package cc.wckd.walkietalkie;
final class NativeLlm {
 private static boolean linked;
 static synchronized void ensureLinked(){if(!linked){System.loadLibrary("walkietalkie");linked=true;}}
 interface Tokens { boolean onToken(byte[] utf8); }
 static native String load(String path, int threads, int mode, String libDir, String cacheDir);
 static native void generate(String prompt, int maxTokens, Tokens callback);
 static native void cancel();
 static native void close();
}
