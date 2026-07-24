# HyperCeiler (yu4032 fork)

Fork of [ReChronoRain/HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) with personal modifications.

## Changes

### 1. Fix: workspace horizontal layout bug (#193)

`mWorkspaceCellSide` calculation used `mCellWidth` instead of `maxGridWidth`, causing the entire icon grid to shift left on wide-screen devices (e.g. Xiaomi 17 Ultra).

### 2. Feature: Dock independent horizontal spacing

Workspace and Dock icon padding can now be set independently.

- **Workspace horizontal margin** — controls desktop icon side spacing
- **Dock independent spacing** — controls dock bar icon side spacing (separate from workspace)

Settings location: **System Desktop → Layout → Horizontal margin**

### How it works

Dock icons are shifted via `MarginLayoutParams` adjustment on child views inside HotSeats. The dock background (from HyperLight or built-in) retains its original margins.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64  # JDK 17–21 required
export ANDROID_HOME=~/Android
./gradlew assembleRelease
```
