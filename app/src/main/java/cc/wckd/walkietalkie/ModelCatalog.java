package cc.wckd.walkietalkie;

/** Compatible, public Hugging Face GGUFs. Q4_0 also suits the experimental NPU backend. */
final class ModelCatalog {
 record Model(String label,String repo,String file,long bytes,String sha,String description,String license) {
  String url(){return "https://huggingface.co/"+repo+"/resolve/main/"+file;}
 }
 static final Model[] MODELS = {
  new Model("Qwen 2.5 · 0.5B","Qwen/Qwen2.5-0.5B-Instruct-GGUF","qwen2.5-0.5b-instruct-q4_0.gguf",428730208L,"7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed","Lightweight · 429 MB","Apache 2.0"),
  new Model("Qwen 2.5 · 1.5B","Qwen/Qwen2.5-1.5B-Instruct-GGUF","qwen2.5-1.5b-instruct-q4_0.gguf",1066227232L,"dcd819ff094852c38faba6873d8ff0c9d51eadb2844539e52042ae5d647bbfdb","Balanced · 1.07 GB","Apache 2.0"),
  new Model("Qwen 2.5 · 3B","Qwen/Qwen2.5-3B-Instruct-GGUF","qwen2.5-3b-instruct-q4_0.gguf",1997879712L,"670d82d0fbee6289b661eb323612c65bd2e97ec8c65cdbd9131aa44d30e9816a","More capable · 2.00 GB","Qwen Research License")
 };
}
