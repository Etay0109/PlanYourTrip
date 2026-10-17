# Trip reminders

- **What:** for every trip of the signed-in account that starts after today, a notification at **18:00 device time the
  day before** (`reminderTime`: `atZone` keeps 18:00 across a daylight-saving change). Title "Tomorrow: 🇬🇧 London"
  (`reminderDestination` = `countryFlag` + `shortDestination`); text from the "Before the trip" list **counted when it
  fires** (`ChecklistDao.countBeforeTrip`): "N things left on your Before the trip list" (plurals), "Everything is
  ready…" when all are checked, "Your trip starts tomorrow." without items. Channel `trip_reminders` "Trip reminders"
  (default importance), small icon `ic_notification` (the launcher's monochrome pin), color `NotificationColors.brand`.
- **Only the evening before, at most once:** from 18:00 (or late, when it was delayed) **until 23:59**, never after
  midnight (`reminderDeadline`), no morning-of reminder. Shown reminders are remembered (`shownKey` = uuid|uid|start
  epoch day in prefs `"reminders"` key `shown`, written with `commit()`; pruned once the trip has started), so edited
  dates get a new one. **No reminder** for a trip starting today or already started, or one first seen after 18:00 the
  day before (created or synced that evening). Nothing is synced: each member's phone schedules its own.
- **Owed until midnight** (`planReminders(…, scheduled, shown)`): a reminder stays wanted after its 18:00 while the
  saved map has it for that exact time, so nothing owed is cancelled (an app start
  after 18:00 must never cancel the reminder that is running).
- **Scheduling** (`TripReminderScheduler`, started in `PlanYourTripApplication`): follows `currentUser` →
  `observeTrips(uid)` + the switch, `planReminders` → `reminderChanges` against the saved map (prefs `"reminders"`,
  uuid → uid + time), so Room emissions don't re-enqueue. Created, edited and synced trips are (re)scheduled; deleted,
  left and removed trips (gone from Room) cancelled; sign-out (`uid == null`) cancels everything. Owed reminders whose
  time has come (`isDue`) are delivered **right away** (the catch-up: every app start, a cold start at 18:00, after a
  force-stop). **The first pass after each app start schedules every future reminder again** (alarms don't survive a
  restart or a force-stop; `REPLACE` is cheap) and cancels WorkManager requests nothing remembers.
  `awaitFirstPass()` lets `ReminderRestoreReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, not exported) keep the
  process alive until that is done.
- **Two triggers, one notification** (`AndroidReminderWork`): an alarm (`TripReminderAlarmReceiver`, data uri
  `planyourtrip://trip-reminder/<uuid>`, immutable): `setExactAndAllowWhileIdle(RTC_WAKEUP)` when
  `canUseExactAlarms()` (before Android 12 always; `SCHEDULE_EXACT_ALARM` = "Alarms & reminders", granted by default on
  12 and 13, off by default for new installs from 14), else `setAndAllowWhileIdle`, which Android may deliver up to an
  hour late (`window=+1h` on the Samsung). Never `USE_EXACT_ALARM` (Play: alarm/calendar apps only) or `setAlarmClock`
  (status bar icon). Allowing exact alarms sends `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to
  `ReminderRestoreReceiver`, which calls `TripReminderScheduler.armAgain()` (every future reminder armed again, now
  exact, also in a running process). Revoking it stops the app like a force-stop; the next start arms inexact alarms.
  And the WorkManager backup (2.10.5, Kotlin 1.8 metadata; 2.11+ needs
  Kotlin 2.1: one unique `OneTimeWorkRequest` per trip, `trip-reminder-<uuid>`, `REPLACE`, tags `trip-reminder` +
  `trip-reminder:<uuid>`, initial delay; survives restarts and updates). Both, and the scheduler's catch-up, call
  `ReminderDelivery.deliver` (one `Mutex`): posts only when `shouldShowReminder` (same uid signed in, the trip exists
  and starts tomorrow, switch on, notifications allowed) and not shown yet; whoever comes second gets `ALREADY_SHOWN`.
  A reminder not wanted at that moment (e.g. notifications refused) isn't remembered, so it can still show that
  evening. `TripReminderNotifier` only builds and posts (strings from a `withAppLocale()` context, English); one
  notification id per trip (`uuid.hashCode()`) + `setOnlyAlertOnce`, so even a second post (the app dying between
  posting and saving the shown key) replaces the first silently. Logs:
  `adb logcat -s TripReminder` (POSTED with minutes late, ALREADY_SHOWN, TOO_LATE, NOT_WANTED; trip uuids only).
- **Known limit:** a force-stopped app (Settings → Force stop, `am force-stop`, **Android Studio's Run and Stop**, or
  Samsung's "Deep sleeping apps") can't be woken by anything, not even a push; the reminder comes when the app is next
  opened, if that is before midnight. "Never sleeping apps" doesn't protect against a force-stop. Every app start logs
  how the previous process ended (`readLastExit`, Android 11+: `ApplicationExitInfo`; Android reports a force-stop, a
  swipe from recents and `am kill` all as "user requested", so `exitKind` reads the description: "stop <pkg> due to …"
  / "[FORCE STOP] …" = force-stop, "remove task…" = swiped away; only a running process leaves a record). The zone is the one the `Clock` had at app
  start (a time-zone change takes effect at the next app start).
- **Tap:** an immutable `PendingIntent` to `MainActivity` (`launchMode="singleTop"`, `onNewIntent`) with the trip uuid +
  uid (`ReminderLink`); `AppNavHost` opens the trip on the Prep tab (`TripDetails.initialTab`).
- **Permission** (`POST_NOTIFICATIONS`, Android 13+): asked **once**, right after creating (`TripFormViewModel`) or
  joining (`JoinTripViewModel`) a trip that starts after today (`ReminderPermissionPrompt`, `permissionAsked`), shown by
  `NotificationPermissionHost` above the `NavHost`; never at app start. Refused → reminders aren't posted.
- **Battery screen** (`BatteryTipDialog`, `ui/common/BatteryTipHost.kt`; when exact alarms aren't allowed (Android 12+)
  it adds a line + "Allow alarms & reminders" → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`): "Get trip reminders on time",
  shown **once ever** (`batteryTipShown`) after that permission step (or right away when the permission isn't needed) and when the
  switch is turned on, only on `LIMITING_BRANDS` (Samsung, Xiaomi/Redmi/POCO, Huawei, Honor, OnePlus, Oppo, Realme,
  Vivo) while the app isn't exempt from battery optimization (`isIgnoringBatteryOptimizations`). Samsung gets its steps
  ("Never sleeping apps"); "Open settings" → `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (fallback: the app's
  details). Never `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play policy).
- **Switch:** "Trip reminders" in the profile menu (`AccountState.remindersEnabled`, on by default, this device). Off
  cancels all; on schedules again, asks for the permission if Android still needs it and offers the battery screen.
- Tests: `TripRemindersTest` (time incl. both DST changes, today/started/past, 17:59 vs 18:30 the evening before,
  owed until 23:59:59 and not at 00:00, shown keys, edited dates, deleted trips, switch, messages, shouldShow),
  `TripReminderSchedulerTest` (`FakeReminderWork`, `FakeReminderSettings`, `FakeReminderTrips`, `FakeReminderPoster`,
  `MutableClock`: once per trip, rescheduling, sign-out, account switch, switch, the start-up pass, `armAgain` (all
  again, never shown twice), a cold start at
  18:00, a start at 18:01 after a force-stop, late at 23:30, nothing after midnight, first seen at 20:00, notifications
  refused then allowed), `ReminderDeliveryTest` (at most once with three calls at the same time, until 23:59:59,
  not wanted isn't remembered, deleted/moved trip, message, force), `LastExitTest` (Samsung and Android 16 texts),
  `BatteryTipTest`, `ReminderPermissionPromptTest`,
  `AccountViewModelTest`, `TripFormTest`, `JoinTripViewModelTest`. The emulator's clock can be moved for
  such checks with `settings put global auto_time 0` + `cmd alarm set-time <epoch millis>` (and `set-timezone`); put
  `auto_time` / `auto_time_zone` back to 1 afterwards. Emulator only, never the phone.

