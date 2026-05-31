# ProGuard Rules for LiteRT-LM & TFLite (Google AI Edge)
# ---------------------------------------------------
# Since these libraries make extensive use of JNI (Java Native Interface) to execute
# optimized C++ kernels on mobile GPUs and NPUs, we must prevent the optimizer
# from stripping, renaming, or obfuscating native entrypoints.

# Keep LiteRT-LM Java classes and members intact
-keep class com.google.ai.edge.litertlm.** { *; }

# Keep TensorFlow Lite native wrappers intact
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep Ktor content serialization JNI/reflection hooks if obfuscated
-keepattributes Signature, *Annotation*, InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
