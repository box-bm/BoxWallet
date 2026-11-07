# BoxWallet R8 rules
#
# Keep this file minimal. AndroidX, Compose, Kotlin, coroutines, Hilt/Dagger, Room, WorkManager,
# DataStore, Firebase, Play services and Gson all ship their own consumer rules inside their AARs/JARs.
# Every extra `-keep class x.** { *; }` here stops R8 from shrinking, inlining and optimizing that
# code, which lowers Play Console's "DEX code optimization" score and makes the app bigger and slower.
#
# Only add a rule for code reached by reflection that R8 cannot see, and scope it as narrowly as possible.

# Readable stack traces in Crashlytics / Play vitals (deobfuscated with the uploaded mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========================================
# Backup file format (Gson, reflection)
# ========================================
# BackupRepository serializes these with Gson. Field names are the JSON keys of backups already
# stored in users' Google Drive, so they must never be renamed or removed. Generic signatures
# (List<TransactionEntity>) are kept by Gson's bundled rules.
-keep class com.boxdev.boxwallet.domain.model.BackupData { <fields>; }
-keep class com.boxdev.boxwallet.data.local.entity.CategoryEntity { <fields>; }
-keep class com.boxdev.boxwallet.data.local.entity.LoanEntity { <fields>; }
-keep class com.boxdev.boxwallet.data.local.entity.TransactionEntity { <fields>; }

# ========================================
# Google API client / Drive (reflection)
# ========================================
# The client maps JSON and query parameters to fields annotated with @Key by name, and creates
# response models (File, FileList, ...) through their no-arg constructors.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep @interface com.google.api.client.util.Key
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keepclassmembers class * extends com.google.api.client.util.GenericData {
    <init>();
}
