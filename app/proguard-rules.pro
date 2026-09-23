-dontwarn org.json.**

# Keep service/receivers referenced by manifest
-keep class com.ftvrcm.** { *; }

-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**
