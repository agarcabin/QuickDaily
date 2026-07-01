# QuickDaily ProGuard 规则

# ── Kotlin 协程 ──
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── kotlinx.serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Compose ──
-dontwarn androidx.compose.**

# ── 应用自身 ──
-keep class com.quickdaily.** { *; }

# ── 保留反射访问的类（如 SharedPreferences 序列化） ──
-keepclassmembers class * {
    public <init>();
}
