# Terra Live Wallpapers — Android

Ultra-realistic real-time Earth as a native Android live wallpaper.
Real rotation (sidereal time), real sunlight/terminator, live EUMETSAT
satellite clouds (3-hourly), true-scale Moon on its real orbit, the full
Hipparcos star catalog, Milky Way and planets at their true positions.

## How it works
`EarthWallpaperService` is a system `WallpaperService`. The scene is a
WebGL page (assets/earth.html) rendered by a hardware-accelerated WebView
projected onto the wallpaper surface through a VirtualDisplay +
Presentation. Rendering pauses automatically whenever the wallpaper is
not visible (battery-friendly).

Because the wallpaper lives inside the app process, **uninstalling the
app automatically removes the wallpaper** — Android reverts to the
system default. Nothing is left behind.

## Build
1. Open this folder in Android Studio (Koala or newer, JDK 17).
2. Let Gradle sync (Studio will generate the Gradle wrapper if prompted).
3. Run on a device (min Android 8.0 / API 26).

## Publish as a paid app (Google Play)
1. Create the app in Play Console → set **Paid**, pick your price
   (pricing is set in Console, not in code; you need a payments profile).
2. `Build > Generate Signed App Bundle` → upload the `.aab`.
3. Store listing assets are in the brand pack (icon 512, feature graphic,
   descriptions in BRAND.md).
4. Note: a price can be changed later, but a paid app cannot be switched
   to free and back.

## Internet permission
Used only to fetch NASA-derived Earth imagery from CDNs and the live
cloud layer from clouds.matteason.co.uk. With no connection the
wallpaper falls back to built-in procedural visuals and keeps working.

## Build without Android Studio (GitHub Actions)
1. Create a GitHub repo and push this folder to it.
2. Open the repo's **Actions** tab — the "Build APK" workflow runs
   automatically (or press "Run workflow").
3. When it finishes, download **TerraLive-debug-apk** from the artifacts,
   copy it to your phone and install it (allow "install unknown apps").
4. For the Play Store, sign the release bundle: in Play Console enable
   **Play App Signing**, or sign locally with `jarsigner`/Android Studio.
