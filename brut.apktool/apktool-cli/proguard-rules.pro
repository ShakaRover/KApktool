-keep class brut.apktool.Main {
    public static void main(java.lang.String[]);
}
-keepclassmembers enum * {
    static **[] values();
    static ** valueOf(java.lang.String);
}

# The bundled smali/baksmali CLIs (`apktool smali` / `apktool baksmali`) are driven by
# JCommander through reflection: command classes carry @ExtendedParameters and their option
# fields carry @Parameter/@ExtendedParameter, all of which are read at runtime. Without these
# rules R8 drops the annotations and the subcommands fail with
# "All extended commands should have an ExtendedParameters annotation".
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class com.beust.jcommander.** { *; }
-keep class com.android.tools.smali.util.jcommander.** { *; }
-keep @com.android.tools.smali.util.jcommander.ExtendedParameters class * { *; }
-keep class * extends com.android.tools.smali.util.jcommander.Command { *; }
-keep class com.android.tools.smali.smali.Main { *; }
-keep class com.android.tools.smali.baksmali.Main { *; }

# https://github.com/iBotPeaches/Apktool/pull/3670#issuecomment-2296326878
-dontwarn com.google.j2objc.annotations.Weak
-dontwarn com.google.j2objc.annotations.RetainedWith
