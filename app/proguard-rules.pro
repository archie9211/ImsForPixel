# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles settings in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep our entry points invoked via am instrument and app_process
-keep class com.svenuks.imsforpixel.BrokerInstrumentation { *; }
-keep class com.svenuks.imsforpixel.ImsQueryTool {
    public static void main(java.lang.String[]);
    *;
}
-keep class com.svenuks.imsforpixel.MainActivity { *; }

# HiddenApiBypass
-keep class org.lsposed.hiddenapibypass.** { *; }

# Kadb and Adblib
-keep class com.flyfishxu.kadb.** { *; }
-keep class com.tananaev.adblib.** { *; }

# Keep internal telephony classes accessed via reflection
-dontwarn com.android.internal.telephony.**
-keep class com.android.internal.telephony.** { *; }
-keep interface com.android.internal.telephony.** { *; }
