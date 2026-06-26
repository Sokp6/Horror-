# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep Gson serialization classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.horrorgame.awakening.game.** { *; }
-dontwarn com.google.gson.**
