#include <jni.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <algorithm>
#include "llama.h"
#include "ggml-backend.h"
#include <cstdlib>
#include <dlfcn.h>

static llama_model * model = nullptr;
static llama_context * ctx = nullptr;
static std::mutex engine_mutex;
static std::atomic<bool> cancelled{false};
static void fail(JNIEnv * env, const char * msg) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), msg);
}
static void unload() {
    if (ctx) llama_free(ctx);
    if (model) llama_model_free(model);
    ctx = nullptr; model = nullptr;
}
static std::string utf8(JNIEnv *env, jstring value) {
    jclass cls = env->FindClass("java/lang/String");
    jmethodID get = env->GetMethodID(cls,"getBytes","(Ljava/lang/String;)[B");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto bytes = (jbyteArray)env->CallObjectMethod(value,get,encoding);
    std::string out(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes,0,out.size(),reinterpret_cast<jbyte*>(out.data()));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(encoding); env->DeleteLocalRef(cls);
    return out;
}
extern "C" JNIEXPORT jstring JNICALL Java_cc_wckd_walkietalkie_NativeLlm_load(JNIEnv *env,jclass,jstring path,jint threads,jint mode,jstring libDir,jstring cacheDir) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    unload();
    std::string libs=utf8(env,libDir);
    std::string cache=utf8(env,cacheDir)+"/opencl";
    setenv("GGML_OPENCL_KERNEL_CACHE_DIR",cache.c_str(),1);
    setenv("ADSP_LIBRARY_PATH",(libs+";/vendor/lib/rfsa/adsp;/vendor/dsp/cdsp;/vendor/dsp").c_str(),1);
    setenv("GGML_HEXAGON_POWER_MODE","sustained_high_performance",1);
    if (!ggml_backend_reg_by_name("CPU")) ggml_backend_load((libs+"/libggml-cpu.so").c_str());
    std::vector<ggml_backend_dev_t> devices;
    std::string backend;
    if (mode==1 || mode==3) {
        auto reg=ggml_backend_reg_by_name("OpenCL");
        if (!reg) reg=ggml_backend_load((libs+"/libggml-opencl.so").c_str());
        if (!reg || !ggml_backend_reg_dev_count(reg)) {fail(env,"Adreno OpenCL is unavailable to this app. Try CPU in Engine settings.");return nullptr;}
        auto dev=ggml_backend_reg_dev_get(reg,0);
        devices.push_back(dev); backend=std::string("GPU: ")+ggml_backend_dev_description(dev);
    }
    if (mode==2 || mode==3) {
        auto reg=ggml_backend_reg_by_name("HTP");
        if (!reg) reg=ggml_backend_load((libs+"/libggml-hexagon.so").c_str());
        if (!reg || !ggml_backend_reg_dev_count(reg)) {fail(env,"Hexagon NPU is unavailable. This build requires Snapdragon 8 Elite Gen 5 / v81 and OEM FastRPC access. Try Adreno GPU.");return nullptr;}
        auto dev=ggml_backend_reg_dev_get(reg,0);devices.push_back(dev);
        if (!backend.empty()) backend+=" + ";
        backend+=std::string("NPU: ")+ggml_backend_dev_description(dev);
    }
    if(mode==0)backend="CPU · 4 threads";
    devices.push_back(nullptr);
    llama_backend_init();
    auto params = llama_model_default_params();
    params.devices=devices.data();
    params.n_gpu_layers = mode==0 ? 0 : 99;
    params.load_mode = mode==2 || mode==3 ? LLAMA_LOAD_MODE_NONE : LLAMA_LOAD_MODE_MMAP;
    std::vector<float> split(llama_max_devices(),0.0f);split[0]=0.5f;split[1]=0.5f;
    if(mode==3){params.split_mode=LLAMA_SPLIT_MODE_LAYER;params.tensor_split=split.data();}
    model = llama_model_load_from_file(utf8(env,path).c_str(),params);
    if (!model) { fail(env,"Could not load Qwen with this backend. Try a different engine or close other apps."); return nullptr; }
    auto cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 256;
    cp.n_ubatch = 128;
    cp.n_threads = std::clamp((int)threads,1,8);
    cp.n_threads_batch = cp.n_threads;
    cp.abort_callback = [](void*) { return cancelled.load(); };
    cancelled = false;
    ctx = llama_init_from_model(model,cp);
    if (!ctx) { unload(); fail(env,"Could not create inference context. Try GPU or CPU, or close other apps."); return nullptr; }
    return env->NewStringUTF(backend.c_str());
}
extern "C" JNIEXPORT void JNICALL Java_cc_wckd_walkietalkie_NativeLlm_cancel(JNIEnv*,jclass) { cancelled = true; }
extern "C" JNIEXPORT void JNICALL Java_cc_wckd_walkietalkie_NativeLlm_close(JNIEnv*,jclass) {
    std::lock_guard<std::mutex> lock(engine_mutex); unload();
}
extern "C" JNIEXPORT void JNICALL Java_cc_wckd_walkietalkie_NativeLlm_generate(JNIEnv *env,jclass,jstring input,jint max_tokens,jobject callback) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (!ctx) { fail(env,"Model is not loaded."); return; }
    cancelled = false;
    auto vocab = llama_model_get_vocab(model);
    std::string prompt = utf8(env,input);
    int count = -llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
    if (count <= 0 || count > 1792) { fail(env,"This conversation is too long. Start a new chat or send a shorter message."); return; }
    std::vector<llama_token> tokens(count);
    llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),count,true,true);
    llama_memory_clear(llama_get_memory(ctx),true);
    for (int off=0; off<count && !cancelled; off+=256) {
        int n=std::min(256,count-off);
        if (llama_decode(ctx,llama_batch_get_one(tokens.data()+off,n)) != 0) {
            if (!cancelled) fail(env,"Qwen prompt processing failed.");
            return;
        }
    }
    auto sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler,llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,1.1f,0.0f,0.0f));
    llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.9f,1));
    llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.65f));
    llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    jclass cb = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb,"onToken","([B)Z");
    int limit=std::min((int)max_tokens,2048-count-1);
    for (int i=0;i<limit && !cancelled;i++) {
        llama_token token=llama_sampler_sample(sampler,ctx,-1);
        if (llama_vocab_is_eog(vocab,token)) break;
        std::vector<char> piece(256);
        int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);
        if (n<0) { piece.resize(-n); n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false); }
        if (n>0) {
            jbyteArray bytes=env->NewByteArray(n);
            env->SetByteArrayRegion(bytes,0,n,reinterpret_cast<const jbyte*>(piece.data()));
            bool keep=env->CallBooleanMethod(callback,on_token,bytes);
            env->DeleteLocalRef(bytes);
            if (env->ExceptionCheck() || !keep) break;
        }
        if (cancelled) break;
        if (llama_decode(ctx,llama_batch_get_one(&token,1)) != 0) {
            if (!cancelled) fail(env,"Qwen generation failed.");
            break;
        }
    }
    env->DeleteLocalRef(cb);
    llama_sampler_free(sampler);
}
