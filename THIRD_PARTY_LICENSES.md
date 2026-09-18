# Third-party licenses

This app is built with the following open-source components.
Full license texts ship with the component distributions; summaries below.

## Runtime / UI dependencies (bundled in the APK)

- Kotlin Standard Library (`org.jetbrains.kotlin:kotlin-stdlib`)
  - License: Apache License 2.0 — https://github.com/JetBrains/kotlin/blob/master/LICENSE
- AndroidX Core KTX (`androidx.core:core-ktx` 1.13.1)
  - License: Apache License 2.0 — https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt
- AndroidX AppCompat (`androidx.appcompat:appcompat` 1.7.0)
  - License: Apache License 2.0 — https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt
- Google Material Components for Android (`com.google.android.material:material` 1.12.0)
  - License: Apache License 2.0 — https://github.com/material-components/material-components-android/blob/master/LICENSE

## Build tooling (not bundled in the APK)

- Android Gradle Plugin 8.5.2 — Apache License 2.0
- Gradle 8.7 — Apache License 2.0 — https://github.com/gradle/gradle/blob/master/LICENSE
- OpenJDK 17 — GPLv2 with Classpath Exception

## Notes

- No Python, Pygame, pygame-ce, or numpy code is used or redistributed.
  The original Pygame prototype was a behavioral reference only.
- Launcher ghost emblem (`res/drawable/ic_launcher_foreground.xml`,
  `ic_logo_ghost.xml`) is an original vector licensed under Apache 2.0 with this project.
- A short license summary is also shown in-app (Main menu → Licenses).
