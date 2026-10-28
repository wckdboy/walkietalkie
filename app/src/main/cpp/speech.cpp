#include <jni.h>
#include <dlfcn.h>
#include <cstdint>
#include <vector>
#include <exception>

// Moonshine 0.1.5 C ABI. Separate from the OpenCL-linked LLM library.
// Upstream's add-audio JNI path does not release GetFloatArrayElements.
using AddAudio = int32_t (*)(int32_t,int32_t,const float*,uint64_t,int32_t,uint32_t);
extern "C" JNIEXPORT jint JNICALL
Java_cc_wckd_walkietalkie_SpeechSession_addAudio(JNIEnv* env,jclass,jint handle,jint stream,jfloatArray audio) {
 try {
  if(!audio)return -3;
  static void* library=dlopen("libmoonshine.so",RTLD_NOW|RTLD_LOCAL);
  static auto add=library?reinterpret_cast<AddAudio>(dlsym(library,"moonshine_transcribe_add_audio_to_stream")):nullptr;
  if(!add) {
   env->ThrowNew(env->FindClass("java/lang/UnsatisfiedLinkError"),"Moonshine audio entry point is unavailable.");
   return -1;
  }
  jsize count=env->GetArrayLength(audio);
  std::vector<float> samples(count);
  env->GetFloatArrayRegion(audio,0,count,samples.data());
  if(env->ExceptionCheck())return -1;
  return add(handle,stream,samples.data(),static_cast<uint64_t>(count),16000,0);
 }catch(const std::exception& e){
  env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());return -1;
 }
}
