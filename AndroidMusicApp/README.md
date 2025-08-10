# Android Music App - Minimal Project

This is a minimal Android Studio project for the music player (Jetpack Compose + ExoPlayer + Room).
It includes a production-ready MusicService with MediaSession and PlayerNotificationManager.

## How to open
1. Download and unzip this project.
2. Open the folder in Android Studio.
3. Let Gradle sync (Android Studio may ask to update Gradle plugin or wrapper).
4. Build -> Build Bundle(s) / APK(s) -> Build APK(s)

## Notes
- If `gradlew` is not present, run `./gradlew wrapper` in the project or let Android Studio configure Gradle.
- The project requires runtime permission `READ_EXTERNAL_STORAGE` (for Android <13) or `READ_MEDIA_AUDIO` (Android 13+).
- The GitHub Actions workflow `/.github/workflows/android-build.yml` will attempt to run `./gradlew assembleDebug`. If you want CI builds, ensure the Gradle wrapper is present or add wrapper files.
