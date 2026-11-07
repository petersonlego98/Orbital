# Orbital Android - R8 rules
#
# R8 must process all classes together: with minification disabled, D8 does not
# generate the default-method bridges for precompiled Arc classes such as
# arc.backend.android.AndroidApplication, which then crashes with
# AbstractMethodError on arc.Application.addListener.
#
# Keep names readable (debug stack traces) and keep everything that is loaded
# dynamically by mods, Rhino or reflection.

-dontobfuscate

-keep class orbital.** { *; }
-keep class arc.** { *; }
-keep class rhino.** { *; }
-keep class com.android.dex.** { *; }
-keep class com.android.dx.** { *; }
-keep class net.jpountz.** { *; }
-keep class at.yawk.lz4.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

-dontwarn javax.naming.**
