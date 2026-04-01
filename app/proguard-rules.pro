# Add project specific ProGuard rules here.
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

# Firebase & Room Data Models
-keep class com.amshu.expensesense.Account { *; }
-keep class com.amshu.expensesense.Transaction { *; }
-keep class com.amshu.expensesense.Budget { *; }
-keep class com.amshu.expensesense.CreditCard { *; }
-keep class com.amshu.expensesense.DebitCard { *; }
-keep class com.amshu.expensesense.CardUIModel** { *; }

-keepclassmembers class com.amshu.expensesense.** {
    public <init>();
    public <fields>;
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ML Kit
-keep class com.google.mlkit.** { *; }

# PDFBox Android Optional Dependencies
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn com.tom_roush.pdfbox.**