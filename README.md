# Ghost Run — Android (standalone, no Python/Pygame)

Spooky landscape endless runner. Native Kotlin, `SurfaceView` 60fps game loop,
Material3 menus, procedural audio (no assets), `SharedPreferences` high score.

- Package: `com.ghostrun.game` · Label: **Ghost Run**
- Orientation: landscape · minSdk 26 · target/compile 34
- Zero permissions. Works fully offline.

## Controls

- Tap anywhere to jump (also 48dp+ HUD buttons for pause/mute)
- Avoid trees, rocks, bats. Grab orbs (+50). Dodging gives +10.
- Difficulty scales with score. Pause on back press / backgrounding.

## Project structure (maintainable core)

- `app/src/main/java/com/ghostrun/game/MainActivity.kt` — overlays, nav, high score, licenses
- `game/GameConfig.kt` — **all tuning lives here**
- `game/GameState.kt` — MENU / PLAYING / PAUSED / GAME_OVER
- `game/GameEngine.kt` — pure update logic (spawn, physics step, collisions, scoring)
- `game/entities/` — `Ghost`, `Obstacle` (sealed Tree/Rock/Bat + weighted factory), `Orb`, `Particle`
- `game/GameView.kt` — `SurfaceView` thread + Canvas renderer (logical 1280×720, letterboxed)
- `audio/SoundManager.kt` — fail-safe procedural SFX + music loop (never crashes without audio)
- `data/HighScoreRepository.kt` — `SharedPreferences` best-score store
- `res/` — Material3 theme, `activity_main.xml` overlays, ghost-emblem vectors,
  adaptive launcher icons (`mipmap-anydpi-v26`) + monochrome for themed icons

To add content (e.g. a new obstacle): extend the `Obstacle` sealed class,
add its spawn weight + draw branch. No loop rewrite needed.

## Build the debug APK (Android Studio CLI only)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# one-time SDK provisioning
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

~/gradle/gradle-8.7/bin/gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

# install:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Licenses

- App code + ghost emblem: Apache-2.0 — see `LICENSE`, `NOTICE`
- Dependencies: see `THIRD_PARTY_LICENSES.md` + in-app Menu → Licenses
