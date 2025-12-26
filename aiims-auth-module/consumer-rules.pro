# Keep AIIMS Auth Module classes to prevent obfuscation issues with Gson/Retrofit
-keep class org.aiims.odk.auth.api.** { *; }
-keepclassmembers class org.aiims.odk.auth.api.** { *; }

# Keep attributes required for Retrofit/Gson reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses