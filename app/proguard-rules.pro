# ProGuard rules for Komiku Reader
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }

# Coil
-dontwarn coil.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**