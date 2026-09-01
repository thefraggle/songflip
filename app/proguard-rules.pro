# -----------------------------------------------------------------------------
# SongFlip R8 & ProGuard Optimization Rules
# -----------------------------------------------------------------------------

# Keep Kotlinx Serialization models
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
-keep class de.goork.songflip.core.analytics.** { *; }
-keep class de.goork.songflip.data.** { *; }

# Keep RevenueCat Purchases SDK
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# Keep Google Play In-App Review API
-keep class com.google.android.play.core.review.** { *; }
-keep class com.google.android.play.core.common.** { *; }
-dontwarn com.google.android.play.core.**

# Keep Ktor & OkHttp Client engine
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep Jetpack Compose & Material 3
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Logging & SLF4J (used internally by Ktor/KMP dependencies)
-dontwarn org.slf4j.impl.**

