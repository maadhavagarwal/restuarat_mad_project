# ProGuard / R8 Rules for Restro Up (kotlin-restaurant-mobile)

# ---------------------------------------------------------------------------
# 1. Google Gson & Reflection Models
# ---------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep all Restro domain, API, SQLite, and Firestore model classes for Gson serialization
-keep class com.restaurant.mobile.delivery.** { *; }
-keep class com.restaurant.mobile.model.** { *; }
-keep class com.restaurant.mobile.api.** { *; }

# ---------------------------------------------------------------------------
# 2. Firebase Firestore, Auth, Messaging & Storage
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep custom Firestore POJOs & mappers
-keepclassmembers class com.restaurant.mobile.delivery.** {
    public <init>();
    public <fields>;
    public <methods>;
}

# ---------------------------------------------------------------------------
# 3. Android WebView & JavaScript Bridges (MapComponents / Razorpay)
# ---------------------------------------------------------------------------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------------------
# 4. Kotlin Coroutines & Compose Runtime
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
