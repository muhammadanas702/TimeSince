# Time Since

An Android home-screen widget that shows how much time has passed since a date you choose (or how much is left until a future date), live.

## Features (version 2.0)

- **Three styles per widget**
  - *Detailed*: years, months, days, hours, plus live minutes and seconds.
  - *Total days*: one big number such as `230 days`, a short breakdown (`7 mo · 8 d · 10 h`), the live clock and the next milestone (the next 100-day mark or anniversary).
  - *Compact*: a single row for a small widget.
- **Color themes**: nine presets (Midnight, Amoled, Paper, Ocean, Forest, Sunset, Rose, Violet, Mint) or pick your own background, text and accent colors.
- **Transparency**: a 0 to 100% slider for the widget background. At 0% only the text floats over your wallpaper.
- **Multiple widgets**: add as many as you like. Each has its own name, date, style, colors and transparency. The app home screen lists them, and tapping a widget (or its row) opens its settings.
- **Live preview** in the editor, drawn by the same code as the widget, so what you see is what you get.
- Pick a date in the future and it becomes a countdown.
- Dark app, English text, no internet permission, no ads, no extra libraries.

## More widgets (version 2.2)

The app now adds five widgets to the launcher's widget list. Each one keeps its own settings, and tapping a widget reopens them.

- **Time Since**: the widget described above.
- **Clock and Date**: a big live clock with today's date. Choose 12 or 24 hour, show seconds, and pick the colors and transparency.
- **World Clock**: two or three cities in one widget, each with its own live time and weekday. Pick the cities from a list of about 45 (or "Local time"), choose 12 or 24 hour, colors and transparency.

- **Event Countdown**: up to four named dates in one widget, each with the days left (`Today`, `Tomorrow`, `48 days`, or `3 days ago` once it has passed). Tick *Every year* for birthdays and anniversaries and the widget counts to the next one.

- **Month Calendar**: the month at a glance with today circled in the accent color. The arrows on the widget move to the previous or next month, tapping the month name jumps back to this month, and tapping anywhere else opens the settings. Choose whether the week starts on Monday or Sunday.

The clock widgets use `TextClock`, which the launcher keeps ticking by itself, so they need no alarms or background work. The Event Countdown only changes at midnight, so it uses one alarm shortly after midnight (and also redraws on boot and when the date or time zone changes).

## How the widget stays live

Android does not let a widget redraw itself every second. The widget is split in two:

| Part | How it updates |
| --- | --- |
| Years, months, days, hours, total days | Text, redrawn once an hour (and on boot, time change, or when you edit the widget) |
| Minutes : seconds | A Chronometer. The launcher ticks it by itself, no background work needed |

If the phone is asleep when the hourly refresh is due, the refresh happens when it wakes. Until then the clock keeps counting past 59:59 and shows hours too, so the total stays correct.

## Build it

### GitHub Actions (no Android Studio needed)

1. Put this folder in a GitHub repository (upload the files, or push).
2. Open the repository's Actions tab. The `Build APK` workflow runs on every change (or press "Run workflow").
3. When it finishes, open the run and download the `time-since-debug-apk` artifact. Unzip it to get `app-debug.apk`.

### Android Studio

Open this folder, let it sync (it installs SDK 35 and Gradle 8.9 if needed), then press Run, or use Build > Build APK(s). The file lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Command line

With JDK 17 and the Android SDK (platform 35) installed, run `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). Set `ANDROID_HOME`, or create a `local.properties` file containing `sdk.dir=/path/to/Android/Sdk`.

## Install on your phone

1. Copy `app-debug.apk` to the phone and open it. Allow installs from that source if asked.
2. Open **Time Since** and tap **Add widget to home screen**, or long-press the home screen, choose Widgets and drag Time Since out.
3. Choose the date, style, colors and transparency, then tap **Save widget**.

### Updating from version 1

Version 1 was signed with a random debug key, so Android will not install version 2 over it. Uninstall the old app once (its widgets disappear and you set the dates again). From version 2 on, every build is signed with the key in `app/debug.keystore`, so later updates install straight over the top and keep your widgets.

## Project layout

```
app/src/main/java/com/timesince/app/
  TimeMath.kt         calendar-accurate difference, total days, milestones
  Themes.kt           display styles and the color presets
  Store.kt            saves one look per widget
  TimeSinceWidget.kt  draws the widget and schedules the hourly refresh
  ConfigActivity.kt   settings screen for one widget, with live preview
  MainActivity.kt     app home: preview, list of widgets, add button
  Look.kt             colors and transparency shared by the clock widgets
  LookPanel.kt        theme picker, color dialog and opacity slider for settings screens
  Cities.kt           the city list and their time zones
  ExtraStore.kt       saves the settings of the clock widgets, one set per widget
  ClockWidget.kt, ClockConfigActivity.kt              Clock and Date widget and its settings
  WorldClockWidget.kt, WorldClockConfigActivity.kt    World Clock widget and its settings
  EventsWidget.kt, EventsConfigActivity.kt            Event Countdown widget and its settings
  EventMath.kt        days left, yearly repeat, ordering of events
  CalendarWidget.kt, CalendarConfigActivity.kt        Month Calendar widget and its settings
  CalendarMath.kt     the 6x7 month grid; CalendarIds.kt holds the generated view ids
  DailyTick.kt        the after-midnight alarm used by date-based widgets
app/src/main/res/
  layout/widget_time_since.xml   Detailed style
  layout/widget_days.xml         Total days style
  layout/widget_compact.xml      Compact style
  layout/activity_config.xml     settings screen
  layout/activity_main.xml       app home
  xml/time_since_widget_info.xml widget size, preview and reconfigure support
app/debug.keystore               fixed debug signing key (public, see below)
```

## Notes

- Months are counted by calendar, so 31 Jan to 1 Mar is 1 month and 1 day. Total days are whole 24-hour days, so 22 Feb 2026 11:00 shows 230 days on 10 Oct 2026 from 11:00 onwards.
- `app/debug.keystore` is committed on purpose so updates install over each other. It is a public key. For a Play Store release, create your own private signing key instead.
- `targetSdk` is 34 so the app keeps the classic window layout. Raise it to 35 if you want to handle edge-to-edge insets.
