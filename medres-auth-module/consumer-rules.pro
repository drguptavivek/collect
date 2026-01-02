# Keep MEDRES Auth Module classes to prevent obfuscation issues with Gson/Retrofit
-keep class edu.aiims.medresodk.auth.api.** { *; }
-keepclassmembers class edu.aiims.medresodk.auth.api.** { *; }

# Keep attributes required for Retrofit/Gson reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses