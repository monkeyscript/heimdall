# Proguard / R8 rules for Heimdall

# Keep Android components
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Compose
-keep class androidx.compose.** { *; }
