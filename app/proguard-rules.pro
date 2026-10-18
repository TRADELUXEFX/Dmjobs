# OkHttp & Okio rules for R8 / ProGuard
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep data models and JSON serialization
-keepclassmembers class * {
    @org.json.* <fields>;
}

# Keep activities
-keep public class * extends android.app.Activity
