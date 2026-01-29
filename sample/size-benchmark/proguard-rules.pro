# Size Benchmark ProGuard Rules
# These rules prevent R8 from stripping the libraries we want to measure

# Keep all Datadog SDK classes
-keep class com.datadog.** { *; }
-keep interface com.datadog.** { *; }

# Keep all OkHttp classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }

# Keep all WorkManager classes
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }

# Keep all Kotlin Coroutines classes
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# Prevent obfuscation warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
