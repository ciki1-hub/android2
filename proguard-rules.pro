# ===========================================
# BASIC OPTIMIZATION
# ===========================================
-optimizationpasses 5
-allowaccessmodification
-dontusemixedcaseclassnames
-dontpreverify

# ===========================================
# CORE APPLICATION CLASSES (FIXES ANALYTICS/HISTORY/COMPARE)
# ===========================================
-keep public class com.cifiko.stoperica.** { *; }
-keepclassmembers class com.cifiko.stoperica.** { *; }

# ===========================================
# ACTIVITIES AND SERVICES (ENHANCED PROTECTION)
# ===========================================
-keep class com.cifiko.stoperica.MainActivity { *; }
-keep class com.cifiko.stoperica.MapActivity { *; }
-keep class com.cifiko.stoperica.LiveViewerActivity { *; }
-keep class com.cifiko.stoperica.AnalyticsActivity { *; }
-keep class com.cifiko.stoperica.HistoryActivity { *; }
-keep class com.cifiko.stoperica.CompareActivity { *; }
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# ===========================================
# DATA CLASSES (CRUCIAL FOR ALL FEATURES)
# ===========================================
-keep class com.cifiko.stoperica.Session {
    public <fields>;
    public <methods>;
}
-keep class com.cifiko.stoperica.LiveSession { *; }

# ===========================================
# PARCELABLE SUPPORT (FIXES ACTIVITY TRANSITIONS)
# ===========================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ===========================================
# FIREBASE AND GOOGLE SERVICES
# ===========================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ===========================================
# GOOGLE MAPS AND LOCATION SERVICES
# ===========================================
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.maps.model.** { *; }
-dontwarn com.google.android.gms.maps.**
-dontwarn com.google.android.gms.location.**

# ===========================================
# NETWORKING (OkHttp FOR WEB UPLOAD)
# ===========================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ===========================================
# SERIALIZATION (FIXES DATA TRANSFER)
# ===========================================
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===========================================
# KOTLIN AND COROUTINES
# ===========================================
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.**

# ===========================================
# ANDROID SYSTEM COMPONENTS
# ===========================================
-keep class android.content.Intent { *; }
-keep class android.content.IntentFilter { *; }
-keep class android.os.** { *; }

# ===========================================
# RESOURCES AND METADATA
# ===========================================
-keepclassmembers class **.R$* {
    public static <fields>;
}
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# ===========================================
# LOG REMOVAL (RELEASE ONLY)
# ===========================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}