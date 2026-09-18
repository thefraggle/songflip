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

