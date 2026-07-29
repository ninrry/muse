# ProGuard rules for Muse Player
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Media3 (only keep what's necessary for MediaSession)
-keep class androidx.media3.common.MediaItem { *; }
-keep class androidx.media3.common.MediaMetadata { *; }
-keep class androidx.media3.session.MediaSession { *; }

# Jaudiotagger (pure Java audio tag library)
-keep class org.jaudiotagger.** { *; }
-keep class javax.imageio.** { *; }
-keep class java.awt.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn org.jaudiotagger.tag.images.**
-dontwarn org.jaudiotagger.test.**
