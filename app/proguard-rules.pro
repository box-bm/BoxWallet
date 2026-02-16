# BoxWallet ProGuard Rules
# Optimized for production release builds

# ========================================
# General Android & Kotlin
# ========================================

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, Signature, Exception
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Keep sealed classes
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }

# ========================================
# Room Database
# ========================================

# Keep all Room entities
-keep @androidx.room.Entity class * { *; }
-keep class com.boxdev.boxwallet.data.local.entity.** { *; }

# Keep all DAOs
-keep @androidx.room.Dao class * { *; }
-keep class com.boxdev.boxwallet.data.local.dao.** { *; }

# Keep Room database class
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }

# Keep Room runtime
-dontwarn androidx.room.paging.**

# ========================================
# Hilt / Dagger
# ========================================

# Keep Hilt generated classes
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Hilt modules
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Keep injected constructors
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}

# Keep Hilt components
-keep class **_HiltModules { *; }
-keep class **_HiltComponents { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# ========================================
# Domain Models & Data Classes
# ========================================

# Keep all domain models (used with Gson serialization)
-keep class com.boxdev.boxwallet.domain.model.** { *; }

# Keep data classes with serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================================
# Gson
# ========================================

# Gson uses generic type information stored in a class file when working with fields.
-keepattributes Signature

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Prevent obfuscation of models used with Gson
-keep class com.boxdev.boxwallet.domain.model.BackupData { *; }
-keep class com.boxdev.boxwallet.domain.model.Transaction { *; }
-keep class com.boxdev.boxwallet.domain.model.Category { *; }
-keep class com.boxdev.boxwallet.domain.model.Loan { *; }

# ========================================
# Jetpack Compose
# ========================================

# Keep Compose runtime
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose UI
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# ========================================
# WorkManager
# ========================================

# Keep Worker classes
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class com.boxdev.boxwallet.worker.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.impl.**

# ========================================
# DataStore
# ========================================

# Keep DataStore preferences
-keep class androidx.datastore.*.** { *; }

# ========================================
# Biometric
# ========================================

# Keep biometric classes
-keep class androidx.biometric.** { *; }

# ========================================
# iText PDF Library
# ========================================

# Keep iText core classes
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Keep iText layout and kernel
-keep class com.itextpdf.kernel.** { *; }
-keep class com.itextpdf.layout.** { *; }
-keep class com.itextpdf.io.** { *; }

# Ignore missing Java desktop/enterprise classes not available on Android
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn javax.xml.crypto.**
-dontwarn javax.xml.stream.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.framework.**
-dontwarn aQute.bnd.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# Ignore missing Apache dependencies
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.http.**
-dontwarn org.apache.jcp.xml.dsig.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.ctc.wstx.**
-dontwarn com.graphbuilder.**

# ========================================
# Apache POI (Excel export)
# ========================================

# Keep POI classes
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# Keep POI OOXML
-keep class org.apache.poi.xssf.** { *; }
-keep class org.apache.poi.ss.** { *; }

# Keep Apache Commons (used by POI)
-dontwarn org.apache.commons.**
-keep class org.apache.commons.** { *; }

# Keep XML beans (used by POI)
-dontwarn org.apache.xmlbeans.**
-keep class org.apache.xmlbeans.** { *; }

# Ignore missing Java EE and desktop classes used by POI
-dontwarn org.apache.batik.**
-dontwarn org.bouncycastle.**
-dontwarn com.zaxxer.sparsebits.**

# ========================================
# Google Play Services & Drive API
# ========================================

# Keep Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep Google Drive API
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**

# Keep Google HTTP client
-keep class com.google.api.client.http.** { *; }
-dontwarn com.google.api.client.http.**

# Keep Google Auth
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ========================================
# Parcelable
# ========================================

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ========================================
# Serializable
# ========================================

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================================
# Navigation Component
# ========================================

# Keep navigation args
-keepnames class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.navigation.** { *; }

# ========================================
# Remove Logging (Production Only)
# ========================================

# Remove Log calls (optional - comment out if you want logs in production)
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# ========================================
# General Rules
# ========================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom view constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ========================================
# Optimization Settings
# ========================================

# Enable aggressive optimizations
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Optimize aggressively
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*