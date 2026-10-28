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
- Pick a date in the future and it becomes a countdown. Set **Alert when the date arrives** to *Notification* or *Ringtone* and the app tells you the moment the countdown reaches zero (same alert system as the Event Countdown, see below).
- Dark app, English text, no ads, no extra libraries. The internet permission is used for one thing only: looking for a newer version of the app on GitHub (see *Updating inside the app*).

## More widgets (version 2.9)

The app adds five widgets to the launcher's widget list. The app's home screen has an **Add** button for each one and lists every widget you have placed, whatever its type. Each widget keeps its own settings, and tapping it (on the home screen or in the list) reopens them.

- **Time Since**: the widget described above.
- **Clock and Date**: a big live clock with today's date. Choose 12 or 24 hour, show seconds, and pick the colors and transparency.
- **World Clock**: a frosted-glass widget made to match a reference widget measured pixel by pixel: a solid yellow pill with your phone's own time (`01 : 43`, bold), the date in a light font (`Wed, 2026, Oct 28,PM`), and dark translucent city pills (`DUB  12:43 PM`, regular weight). The glass container is white at 15% with a thin outline and rounded corners. You choose how many cities: **1 to 6**, with *Add a city* and a remove button on every row. Tap a city to open a list with a **search box**: type a city or a country (for example `Seoul` or `South Korea`) from about 80 cities. Choose short names (`LON`) or full names (`London`), 12 or 24 hour, **text size** (small, normal, large) and colors. There are **two transparency sliders**: one for the glass container and one for the city pills. The yellow pill always stays solid. Text sizes depend on the number of cities, not on the size the launcher reports, so the widget looks the same on every phone; make the widget taller when you use more than three cities.
- **Event Countdown**: up to four named events in one widget. Each has a date and, if you like, a **time**. Without a time the widget shows the days left (`Today`, `Tomorrow`, `48 days`, or `3 days ago` once it has passed). With a time it counts down to the minute: far away it shows whole hours (`4d 19h`), and from a day before it a live ticking countdown (`05:29:41`) that the launcher updates every second. At the moment of the event it says `Now`, and an hour later it becomes a past event. Tick *Every year* for birthdays and anniversaries and the widget counts to the next one. Every event also has an **Alert** button: *Off*, *Notification* or *Ringtone*. An event with a time alerts at that time; an event without one alerts on its date at the **all-day alert time** you choose (9:00 AM by default). The app shows a notification; with *Ringtone* it also rings with your phone's ringtone until you tap or swipe the notification away (at most one minute). *Send a test alert* in the settings lets you hear it first. Android 13 and newer ask once for permission to show notifications. If a phone was off at the alert time, the alert is still shown when it comes back, as long as it is less than a day late. Removing the widget stops its alerts.
- **Month Calendar**: the month at a glance with today circled in the accent color. The arrows on the widget move to the previous or next month, tapping the month name jumps back to this month, and tapping anywhere else opens the settings. Choose whether the week starts on Monday or Sunday.

The clock widgets use `TextClock`, which the launcher keeps ticking by itself, so they need no alarms or background work. The Event Countdown only changes at midnight, so it uses one alarm shortly after midnight (and also redraws on boot and when the date or time zone changes). Event alerts use one more alarm, set for the next alert (or the next change of the countdown text) of all your Event widgets; when it fires the app sends what is due, redraws the widgets and sets the alarm for the one after. On some phones (for example with aggressive battery managers) you must also allow the app to auto-start or run in the background, or Android may drop the alarm.

## Updating inside the app

Open the app and, when a newer release exists on this repository's Releases page, a card at the top says **Update available**. Tap **Update now**: the app downloads `TimeSince.apk` and Android asks whether to install it over the old version. Your widgets and settings stay, because every build is signed with the same key.

- The app looks for updates when it opens (at most once an hour) and whenever you tap **Check for updates** at the bottom of the home screen. It reads `releases/latest` of this repository and compares the release tag (`v2.7`) with its own version.
- The first time, Android sends you to a screen to allow **Install unknown apps** for Time Since. Allow it, go back and tap **Update now** again.
- The APK is streamed straight into Android's installer, so nothing is saved on the phone and no file provider is needed.
- Only version 2.6 and newer have this. Install 2.6 once from the Releases page; every release after it shows up in the app.

## "Harmful app" warnings when installing

Chrome warns about every APK it downloads ("This type of file can harm your device"), and Play Protect may say the app is unknown or "harmful" because it is not on Google Play and Google has not seen this exact file before (every new version is a new file). Neither means the app does anything bad: it has no ads, no analytics and uses the internet only to ask GitHub for a newer release.

- In Chrome tap **Download anyway** or **Keep**.
- In the Play Protect box tap **More details**, then **Install anyway** (or **Scan app** and then install).
- Updating from inside the app avoids the Chrome warning, because the app downloads the file itself.
- Since version 3.0 the APK is no longer marked *debuggable*, which makes it look less like a developer test build. The only complete fix is publishing through Google Play, which needs a developer account and a private release key.

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
3. When it finishes, the APK is published under **Releases**, named `TimeSince.apk`. This link always gives the newest build:
   `https://github.com/<owner>/<repo>/releases/latest/download/TimeSince.apk`
   (The run page also keeps the `time-since-debug-apk` artifact, a zip with `app-debug.apk`.)

Each release is named after `versionName` in `app/build.gradle.kts` (for example `v2.3`). Pushing the same version again replaces that release, so its APK always matches the newest commit.

### Android Studio

Open this folder, let it sync (it installs SDK 35 and Gradle 8.9 if needed), then press Run, or use Build > Build APK(s). The file lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Command line

With JDK 17 and the Android SDK (platform 35) installed, run `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). Set `ANDROID_HOME`, or create a `local.properties` file containing `sdk.dir=/path/to/Android/Sdk`.

## Install on your phone

1. Copy `app-debug.apk` to the phone and open it. Allow installs from that source if asked.
2. Open **Time Since** and tap **Add** next to the widget you want, or long-press the home screen, choose Widgets and drag one of the five out.
3. Choose its settings, colors and transparency, then tap **Save widget**.

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
  Cities.kt           the city list (with countries) and their time zones
  CityPicker.kt       the city list dialog with a search box
  ExtraStore.kt       saves the settings of the clock widgets, one set per widget
  ClockWidget.kt, ClockConfigActivity.kt              Clock and Date widget and its settings
  WorldClockWidget.kt, WorldClockConfigActivity.kt    World Clock widget and its settings
  EventsWidget.kt, EventsConfigActivity.kt            Event Countdown widget and its settings
  EventMath.kt        days left, yearly repeat, ordering of events, alert times
  EventAlerts.kt      notifications and the alarm for Event Countdown alerts
  EventAlarmReceiver.kt  receives that alarm
  CalendarWidget.kt, CalendarConfigActivity.kt        Month Calendar widget and its settings
  CalendarMath.kt     the 6x7 month grid; CalendarIds.kt holds the generated view ids
  DailyTick.kt        the after-midnight alarm used by date-based widgets
  Updater.kt          asks GitHub for the newest release, downloads it into an install session
  UpdateInstallReceiver.kt  shows Android's install prompt, or the reason an update failed
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
