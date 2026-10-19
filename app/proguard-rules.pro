# -----------------------------------------------------------------------------
# SongFlip R8 & ProGuard Optimization Rules
# -----------------------------------------------------------------------------

# Keep Kotlinx Serialization models & serializer mechanics
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class *$$serializer {
    public static final *$$serializer INSTANCE;
}
-keepclasseswithmembers class * {
    public static final *** Companion;
}

# Optional / unused dependency warnings suppression
-dontwarn org.slf4j.impl.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn com.google.android.play.core.**
-dontwarn com.revenuecat.purchases.**
-dontwarn java.sql.**
-dontwarn javax.sql.**
-dontwarn org.sqlite.**
-dontwarn retrofit2.**

# ShazamKit JNI & Gson Model Rules (#78)
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.shazam.sigx.** { *; }
-keep class com.shazam.jni.** { *; }
-keep class com.shazam.sig.** { *; }
-keep class com.shazam.recognition.** { *; }
-keep class com.shazam.shazamkit.** { *; }
-keepclassmembers class com.shazam.shazamkit.MediaItem { *; }


