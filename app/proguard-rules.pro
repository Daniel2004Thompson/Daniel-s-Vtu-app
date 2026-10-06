# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Obfuscate and strip debug/verbose logs in release builds to harden against JADX / APKTool / Frida
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Hide original source file names in stack traces
-renamesourcefileattribute SourceFile

# Keep serialization and networking data models intact during R8 minification
-keepattributes *Annotation*,InnerClasses,Signature, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.example.data.model.** { *; }
-keep class com.example.data.remote.** { *; }
-keep class com.vtu.app.wallet.** { *; }
