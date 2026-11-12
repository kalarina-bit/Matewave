# Compose, AndroidX and coroutines ship their own consumer rules, and the app uses no
# reflection. Only keep line numbers so release crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Debug and verbose logging (NetworkLog included) is stripped from release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
