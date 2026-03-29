# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# --- GSON & DATA CLASSES ---
# Keep all data classes to ensure serialization works
-keep class com.webwrap.app.data.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

## --- JETPACK COMPOSE ---
## Prevents stripping of Compose-related metadata and classes
#-keep class androidx.compose.runtime.** { *; }
#-keep class androidx.compose.ui.** { *; }
#-keep class androidx.compose.material3.** { *; }
#
## --- WEBVIEW & JAVASCRIPT ---
## Essential if you use JavaScript interfaces
#-keepclassmembers class * {
#    @android.webkit.JavascriptInterface <methods>;
#}
#-keep class android.webkit.** { *; }
#
## --- PREVENT CRASHES ON STARTUP ---
## Some libraries use reflection to check for presence of other classes
#-dontwarn androidx.**
#-dontwarn com.google.gson.**
#
## Keep the MainActivity and other components
#-keep class com.webwrap.app.MainActivity { *; }
#-keep class com.webwrap.app.service.BackgroundAudioService { *; }
