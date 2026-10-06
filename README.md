# Time Since

An Android home-screen widget that shows how much time has passed since a date you choose (or how much is left until a future date), in years, months, days, hours, minutes and seconds.

- Add as many widgets as you like, each with its own date and optional name.
- Tap a widget to change its date or name.
- Pick a date in the future and it becomes a countdown.
- The app screen shows a live preview with every second ticking.
- Dark design, English text, no internet permission, no ads, no dependencies beyond Android itself.

## How the widget stays live

Android does not let a widget redraw itself every second. The widget is split in two:

| Part | How it updates |
| --- | --- |
| Years, months, days, hours | Text, redrawn once an hour (and on boot, time change, or when you edit the widget) |
| Minutes : seconds | A Chronometer. The launcher ticks it by itself, no background work needed |

If the phone is asleep when the hourly refresh is due, the refresh happens when it wakes. Until then the clock keeps counting past 59:59 and shows hours too, so the total stays correct.

## Build it

### Option A: Android Studio

1. Install Android Studio and open this folder (`File > Open`).
2. Let it sync. It will offer to install Android SDK 35 and download Gradle 8.9.
3. Plug in your phone (USB debugging on) or start an emulator, then press Run.

Or build the APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`. The file lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Option B: GitHub Actions (no Android Studio needed)

1. Create a new GitHub repository and push this folder to it.
2. Open the repository's Actions tab. The `Build APK` workflow runs on every push (or press "Run workflow").
3. When it finishes, open the run and download the `time-since-debug-apk` artifact. Unzip it to get `app-debug.apk`.

### Command line

With JDK 17 and the Android SDK (platform 35) installed, run `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). Set `ANDROID_HOME`, or create a `local.properties` file containing `sdk.dir=/path/to/Android/Sdk`.

## Install on your phone

1. Copy `app-debug.apk` to the phone and open it.
2. Android will ask to allow installs from that source. Allow it for the file manager or browser you used.
3. Open **Time Since** once, or go straight to the next step.

## Put the widget on the home screen

- Open the app and tap **Add widget to home screen** (works on most launchers), or
- Long-press an empty spot on the home screen, tap **Widgets**, find **Time Since**, and drag it out.

Choose the date and time, tap **Save widget**, and it appears. Resize it by long-pressing and dragging the handles.

## Project layout

```
app/src/main/java/com/timesince/app/
  TimeMath.kt        calendar-accurate difference (leap years, month lengths)
  Store.kt           saves one date per widget
  TimeSinceWidget.kt the widget: layout updates and the hourly refresh
  MainActivity.kt    app screen and widget configuration screen
app/src/main/res/
  layout/widget_time_since.xml   the widget
  layout/activity_main.xml       the app screen
  xml/time_since_widget_info.xml widget size, preview and settings
  values/colors.xml              palette (change colours here)
```

## Notes

- Months are counted by calendar, so 31 Jan to 1 Mar is 1 month and 1 day.
- `targetSdk` is 34 so the app keeps the classic window layout. Raise it to 35 if you want to handle edge-to-edge insets.
- This is a debug build signed with the debug key. For a Play Store release you would create your own signing key.
