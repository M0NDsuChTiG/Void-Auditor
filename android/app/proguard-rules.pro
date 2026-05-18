# Shizuku Protection
-keep class rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep public class * extends rikka.shizuku.ShizukuProvider

# Capacitor / Cordova Protection
-keep class com.getcapacitor.** { *; }
-keep class com.kuzyamond.adbstudio.ShizukuExecutor { *; }

# Keep reflection targets
-keepclassmembernames class rikka.shizuku.Shizuku {
    java.lang.Process newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
