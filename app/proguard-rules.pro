# ProGuard rules for EPUB Audio Reader

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { <fields>; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# Hilt
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager
-keep class dagger.hilt.** { *; }
-keepclassmembers class * {
    @dagger.hilt.android.* <fields>;
    @dagger.hilt.android.* <methods>;
}
-keepclassmembers class * extends dagger.hilt.android.HiltActivity {
    public void onCreate(android.os.Bundle);
}
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.android.internal.modules.** { *; }

# Sherpa ONNX
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General Android
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
