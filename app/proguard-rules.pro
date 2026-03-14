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
# Keep app-side Xposed / YukiHook entry points.
# com.zhufucdev.me dependencies already provide consumer-rules.pro transitively,
# so only project-local entry classes need to be protected here.
-keep class * extends com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
-keep class * implements com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
-keep class com.zhufucdev.ws_plugin.hook.HookEntry_YukiHookXposedInit
-keep class com.zhufucdev.ws_plugin.hook.HookEntry
-keep class **_*YukiHookXposedInit
-keep class com.zhufucdev.ws_plugin.ControllerReceiver

# Preserve annotations used by generated / reflective Xposed bootstrap code.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,EnclosingMethod,InnerClasses,Signature

# Some transitive dependencies reference the optional SLF4J Android/JVM binder.
# It is not packaged for this app, so suppress the R8 missing-class warning.
-dontwarn org.slf4j.impl.StaticLoggerBinder
