# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Gson models
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Room entities
-keep class com.boxdev.boxwallet.data.local.entity.** { *; }

# Keep backup data class
-keep class com.boxdev.boxwallet.ui.screens.settings.BackupData { *; }

# Apache POI
-dontwarn org.apache.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# iText PDF
-dontwarn com.itextpdf.**
-keep class com.itextpdf.** { *; }
