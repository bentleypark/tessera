# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Tessera: Keep public API
-keep public class com.naemomlab.tessera.** { *; }

# Keep Compose runtime
-keepclassmembers class androidx.compose.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
