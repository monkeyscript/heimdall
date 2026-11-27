# Proguard / R8 rules for Messagate

# Keep Android components
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Compose
-keep class androidx.compose.** { *; }
