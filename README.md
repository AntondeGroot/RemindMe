# Remind Me

A personal Android reminder app. The whole app is settings; the only thing it ever
does is post notifications at the times you set. No lists, no checking things off,
no accounts, no network. Everything stays in `SharedPreferences` on the phone.

## What it does

Each reminder holds a separate set of times for **Home**, **Office** and **Off** days,
so "clean up 1 thing" can fire three times when you work from home and once on an
office evening. A weekly pattern decides which type each weekday is by default, and
you can override a single day from the main screen without disturbing that pattern.

**Specific days** is the fourth kind of day, under Home, Office and Off in the editor,
and the only one a reminder picks for itself: tick any combination of the seven
weekdays, optionally narrowed to even or odd weeks — the ISO week number printed on
the calendar, shown beside today's date on the main screen so you can see which one
this is. Give that set its own times and they fire on those dates *on top of* whatever
the day type already asks for — a time listed in both only fires once. The bins go out
on Friday in even weeks; the plants get watered Tuesday and Saturday.

Leave the days unticked — as everything ships — and the reminder just follows the
home/office/off times all week. A reminder that should *only* fire on its specific
days is one with those days set and the other three left empty.

The reminders it ships with:

| Reminder | Home | Office | Off |
|---|---|---|---|
| 🐈 Clean out the kitty litter bins | 21:00 | 21:00 | 21:00 |
| 🧺 Clean up 1 thing | 11:00, 15:00, 19:30 | 19:30 | 11:00, 16:00 |
| 📓 Did you register all your meals? | 09:30, 13:30, 16:30, 20:00 | same | 10:00, 14:00, 17:00, 20:30 |
| 💧 Drink a glass of water | 08:00 → 20:30, nine times | same | seven times, spread wider |

Default week: Mon/Wed/Fri home, Tue/Thu office, weekend off.

## Building it

You need [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
and a JDK 17, which Android Studio bundles.

1. **File → Open** and pick this folder.
2. Wait for the Gradle sync. It downloads Gradle 8.9, the Android Gradle Plugin and
   the Compose libraries, so the first sync needs a network connection and a few minutes.
3. Plug in your phone with USB debugging on, or start an emulator.
4. Press **Run**.

There is no `gradle/wrapper/gradle-wrapper.jar` in this archive because it's a binary.
Android Studio regenerates it on first sync. If you'd rather build from the command
line, run `gradle wrapper` once (with any local Gradle), then `./gradlew assembleDebug`
— the APK lands in `app/build/outputs/apk/debug/`.

To get it onto the phone without Android Studio, copy that APK across and open it;
you'll need to allow installing from unknown sources.

## Three permissions to grant

The app shows an amber card for each of these until it's sorted, with a button that
opens the right settings page:

- **Notifications** — without it nothing reaches you at all.
- **Alarms & reminders** — Android 12+. Without it the system batches alarms and your
  21:00 ping might land at 21:07.
- **Battery optimisation exemption** — some manufacturers (Samsung, Xiaomi, OnePlus
  especially) aggressively sleep apps and will swallow overnight alarms otherwise.

Use **Send a test notification** in any reminder's editor to confirm it all works
before trusting it with the cat.

## How the scheduling works

Only one alarm is ever pending: the next moment something is due. When it fires,
`AlarmReceiver` posts every reminder scheduled for that minute and books the next
alarm. Finding that moment means walking forward a day at a time for four weeks —
long enough for "Monday, even weeks", which stretches to a 21-day gap over the new
year of a 53-week ISO year. That sidesteps per-app alarm limits and copes with edits, reboots, clock
changes and time-zone changes — `BootReceiver` rebuilds the chain after each.

Alarms use `setExactAndAllowWhileIdle`, which fires on the minute even in doze. If
you find pings still slipping on a stubborn phone, swap it for `setAlarmClock` in
`Scheduler.kt`: that's the most reliable option Android offers, at the cost of a
permanent alarm icon in the status bar.

Each reminder posts under a stable notification id, so a new water ping replaces the
previous one rather than stacking nine of them up by evening.

## Files worth knowing

```
app/src/main/java/nl/local/remindme/
  Model.kt          reminders, day types, specific days, the starting set
  Store.kt          JSON in SharedPreferences
  Scheduler.kt      works out what's next and sets the alarm
  AlarmReceiver.kt  fires, notifies, books the next one
  BootReceiver.kt   rebuilds alarms after reboot or a clock change
  Notifications.kt  channel and posting
  MainActivity.kt   the settings UI (Compose)
```

Changing the starting reminders means editing `Defaults.config` in `Model.kt`.
Reinstalling resets everything; **Reset everything** under *Your usual week* does
the same without a reinstall.
