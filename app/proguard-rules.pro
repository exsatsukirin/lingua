# Keep Room entities/DTOs when minification is enabled.
-keep class com.lingua.app.data.history.** { *; }
-keepclassmembers class com.lingua.app.data.remote.dto.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lingua.app.**$$serializer { *; }
-keepclassmembers class com.lingua.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.lingua.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
