# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class home.brimley.**$$serializer { *; }
-keepclassmembers class home.brimley.** { *** Companion; }
-keepclasseswithmembers class home.brimley.** { kotlinx.serialization.KSerializer serializer(...); }
