# Add project specific ProGuard rules here.
-keep class com.mathagent.** { *; }
-keepclassmembers class com.mathagent.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep llama.cpp JNI
-keep class com.mathagent.LlamaEngine { *; }
-keep class com.mathagent.TokenCallback { *; }
