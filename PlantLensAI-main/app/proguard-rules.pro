# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\punit\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# Keep Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *

# Keep Firebase models
-keep class com.plantlens.ai.models.** { *; }

# Keep TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
