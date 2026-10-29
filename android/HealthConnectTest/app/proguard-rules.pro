# Add project specific ProGuard rules here.
# Room instantiates WorkManager's generated database using a no-argument
# constructor through reflection. Keep that constructor explicitly: retaining
# the class name alone is insufficient with the current R8 optimizer.
-keepclassmembers class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}

# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
