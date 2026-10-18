# Screens and features

## Navigation
Type-safe Navigation Compose routes (`@Serializable`) in `ui/navigation/Routes.kt`:

```
(app start) ──▶ Home if a session is saved, otherwise Login
Login ──(sign in / sign up succeeded)──▶ Home      popUpTo<Login> inclusive → Back on Home exits the app
Home ──(FAB / empty-state button)──▶ TripForm()                  Save / back arrow → pop (to Home)
Home ──(tap any trip card)─────────▶ TripDetails(tripId: Long)   back arrow → pop
TripDetails ──(header edit icon)───▶ TripForm(tripId)            Save / back arrow → pop (to the trip)
TripDetails ──("+ Add expense")────▶ ExpenseForm(tripId)         Save / back arrow → pop
TripDetails ──(tap an expense)─────▶ ExpenseForm(tripId, expenseId)  Save / Delete / back → pop
TripDetails ──("Add activity")─────▶ ActivityForm(tripId, dateEpochDay = selected day)  Save / back → pop
TripDetails ──(tap an activity)────▶ ActivityForm(tripId, activityId)  Save / Delete / back → pop
TripDetails ──(sparkle FAB)────────▶ TripAssistant(tripId)       back arrow → pop (conversation is kept)
TripDetails ──("Delete trip" confirmed)──▶ pop to Home with Home's savedStateHandle["pendingDeleteTripId"] = tripId;
                                   Home deletes it (DeleteTripViewModel.deleteConfirmed) and clears the key
(trip reminder tapped) ──▶ Home → TripDetails(tripId, initialTab = "PREP") when the trip is still the signed-in
                                   account's (SessionViewModel.tripIdFor); otherwise the app opens as usual
TripDetails ──("Leave trip" confirmed)──▶ the same with "pendingLeaveTripId"; Home leaves it (leaveConfirmed)
(session becomes null) ──▶ Login                   popUpTo(graph) inclusive → whole back stack cleared
```
- `SessionViewModel.startsSignedIn` picks the start destination once. Firebase restores a saved session
  synchronously, so there is no splash screen.
- `AppNavHost` collects `SessionViewModel.currentUser`; when it becomes null on any screen other than Login (avatar
  menu "Sign out", or Firebase signing the user out), it navigates to Login and clears the back stack.
- Login → Home is driven by `AuthUiState.isAuthenticated`, not by the session flow, so Home opens only after sign up
  has saved the display name.
- `TripDetailsViewModel`, `TripFormViewModel`, `ExpenseFormViewModel` and `ActivityFormViewModel` read their ids via
  `SavedStateHandle.toRoute<...>()`.
- `AssistantViewModel` is created with the **`TripDetails` back stack entry** as its owner
  (`viewModel(navController.getBackStackEntry<TripDetails>(), factory = ...)`), so it reads `TripDetails` from the
  `SavedStateHandle` and the conversation lives exactly as long as the trip screen.
- The trip screen's 4 tabs are **not** destinations: the selected `TripTab` is Compose state (`rememberSaveable`).
- Navigation calls live only in `AppNavHost`; screens receive callbacks (`onAddTrip`, `onTripClick`, `onBack`, ...).

## Home screen rules (`groupTrips` in `ui/home/TripGrouping.kt`)
- **Past:** `end < today`, most recent first. Card shows destination + "MMM yyyy" of the start date, no countdown.
- **Active:** current (`start ≤ today ≤ end`) and future trips, earliest start first (ties by id).
  - The first active trip is the **Next trip** (gradient hero card). The rest are **Upcoming** (white cards).
  - A current trip shows **"Now"** instead of a countdown, in the hero card and as a badge if it lands in Upcoming.
  - Countdown = days from today to the start date (always ≥ 1 for future trips).
- Trip length is inclusive: 12–17 Nov is 6 days.
- Date ranges (`TripFormatting.dateRange`): `12–17 Nov 2026`, `28 Nov – 3 Dec 2026`, `28 Dec 2026 – 3 Jan 2027`,
  and `12 Nov 2026` for a single day. With `currentYear` (Home cards and the trip header: `HomeUiState.Content.currentYear`
  / `TripDetailsUiState.Loaded.currentYear`, from the injected `Clock`) a trip that starts and ends this year leaves the
  year out (`19–24 Oct`); the PDF and other places keep it.
- **Destination titles never break inside a word** (`DestinationTitle`, Next trip / Upcoming / Past cards and the trip
  header; `fitDestinationTitle` in `ui/common/DestinationFit.kt`, pure + `DestinationFitTest`): the name and its flag
  first shrink to stay on one line, down to `destinationMinScale()` (80% of the normal-font size); on the Next trip card
  the countdown box then gets narrower (no 92dp minimum, smaller "Now"/number, 8dp padding) when even that isn't
  enough; only then the name wraps between words (largest size at which every word fits), and a single word too long
  even at the minimum stays on one line with "…". `DestinationTitleLayoutKey` (a semantics property TalkBack ignores)
  reports the lines for `LayoutPolishTest`.
- **The dates line never wraps** (`TripDatesRow(start, end, currentYear, …)` on the Next trip and Upcoming cards and the
  trip header): "19–24 Oct · 6 days" when it fits on one line, else the length is dropped ("19–24 Oct"), else the dates
  get smaller (down to `minFitTextScale`: 75% of the normal-font size), "…" only beyond that. TalkBack always reads the
  whole line. Past cards keep "Aug 2026".
- The Upcoming title shows the count pill ("2 trips"); the Past title shows "N archived".
- **Empty state:** when there are no trips, show the friendly message and an "Add your first trip" button. The FAB
  stays visible. While Room loads for the first time, the state is `Loading` and nothing is drawn, so the empty
  state never flashes.
- The header greeting and initials come from the signed-in user's `greetingName()`: the display name, or the part
  of the email before "@" when there is no name.
- **Trip card menu:** the cards have **no chevron**. Every card (Next trip, Upcoming, Past) ends with a three-dots
  `TripMoreButton` ("Share trip", see "Share trip (PDF)"; "Delete trip", see "Deleting a trip") at the right edge, vertically centered, no background:
  `onHeroMuted` on the Next trip card, the `chevron` gray on the list cards. The countdown box / badge sits to its
  left; the Next trip card's left side stays label, destination, dates. The button is a 48dp touch target with a
  20dp icon (`Dimens.CardMenuIconSize`); the card rows end with `Dimens.CardMenuEndPadding` (2dp) so the icon is
  `CardMenuIconInset` (16dp) from the card edge on all three cards, and the button's 14dp inset is the gap to the
  countdown. The button consumes its taps (never opens the trip); the menu is right-aligned to the button and
  shifted by the end padding, so it lines up with the card's edge.
- **Card keys** are `"trip-<id>"` in all three sections and every item has `Modifier.animateItem()`, so a deleted card
  fades out and the trip that becomes the Next trip moves up from Upcoming. **Pull to refresh** (`RefreshBox` +
  `RefreshViewModel`) runs a full sync; a failure shows "Couldn't refresh. Check your connection." (the flag is cleared only after the snackbar: clearing it
  first changed the `LaunchedEffect` key and cancelled the snackbar at once). Every card sits in a `SwipeToDeleteBox`
  (see "Deleting a trip"); the Scaffold has a `SnackbarHost` for "Trip deleted".
- **Avatar menu** (`AccountButton` + `AccountViewModel` in `ui/common`): tapping the
  avatar opens a `DropdownMenu` with the user's name and email, the sync status (small cloud icon + "All changes
  saved" / "Syncing…" / "Offline – changes will sync later", from `AccountState.syncStatus`; "Sync problem – tap to
  retry" with the last sync time as a menu row that runs a full sync, see "Status and sign out") and "Sign out". Sign out
  goes through `SyncController.signOut`; when changes can't be uploaded, an `AlertDialog` warns ("Sign out anyway" /
  Cancel, `AccountState.showSignOutWarning`); it also says the trip documents stay on the device either way. `HomeViewModel` only provides the greeting and the trips.

## Trip screen (`ui/tripdetails`)
Reference: `design/trip_overview.png` (Overview) and `design/trip_budget.png` (Budget tab, see "Budget tab and expense
splitting").
- `Scaffold` with a Material 3 `NavigationBar`: **Overview, Budget, Prep, Plan** (Prep: `ic_checklist`, see "Prep tab").
  **Pull to refresh** on every tab (`RefreshBox`, spinner below the status bar; own `RefreshViewModel`) runs a full
  sync, same message as Home on failure.
  A round `secondary` FAB with a sparkle (`AssistantFab`) sits above the navigation bar on all four tabs and opens
  the assistant. On the Budget tab the FAB slot holds a full-width row (`FabPosition.Center`): the primary pill
  "+ Add expense" (`AddExpenseFab`, `ExtendedFloatingActionButton`) at the bottom left and the sparkle at the right,
  never overlapping (see "Floating buttons" below).
- **Prep tab:** see "Prep tab" below. `TripTab.PREP` replaced `TripTab.MAP` in the same (third) position.
- **Floating buttons** float over the scrolling list (content passes under them while scrolling). Every tab's list
  ends with `FabClearance` = button height 56dp + the Scaffold's 16dp gap + 24dp, so the last card can always be
  scrolled fully above them.
- **Header** (`TripHeader`, on every tab, scrolls with the list): hero gradient + `HeroWaves`, only bottom corners
  rounded (`extraLarge`), drawn behind the status bar (`contentWindowInsets = WindowInsets(0)` +
  `statusBarsPadding()`); back circle, status pill ("In 38 days" / "Now", **hidden after the trip ended**), edit
  circle (→ `TripForm(tripId)`), three-dots circle (`TripMoreMenu` → "Share trip", "Delete trip"), destination (`headlineLarge`,
  ≤ 3 lines, the country's flag before it when the trip has a `countryCode`: `DestinationTitle`), dates with length. Below 300dp content width or
  above font scale 1.2 the status pill moves to its own line under the buttons.
- **Status bar shade** (`StatusBarShade` in `TripDetailsChrome`, every tab): fixed behind the status bar, the
  `background` color fading out `Dimens.TripStatusBarFade` (16dp) below it. Invisible while the list is at the top (the
  header looks as before); it fades in over the first 16dp of scrolling (the header item's offset), so the header's
  buttons and title and the cards never run into the clock. The status bar icons follow the system theme, so they read
  on it in both modes.
- **Overview:** `BudgetCard` (tap → Budget tab; "Set budget" button opens the budget dialog), then `CurrencyCard`,
  then the **Map card** and Daily plan (→ Plan tab) side by side (equal height: `IntrinsicSize.Min` row), then the day
  section (`DayPreviewCard`, see "Plan tab").
- **Map card** (`FeatureCard` with `ic_map`, "Map", the destination in gray (≤ 2 lines), then
  `OverviewMapIllustration`): a rounded (`shapes.large`) **drawn** map filling the card's width and the height the Daily
  plan card leaves (`weight(1f)`, at least `Dimens.MapPreviewMinHeight` 88dp). A Compose `Canvas` from theme tokens only
  (land `surfaceContainerHighest`, streets `surfaceContainerLowest`, a river `primaryContainer`, a park
  `secondaryContainer`), a teal `ic_location` pin in the middle and under it a white "Open in Google Maps" pill (teal
  `ic_map` icon; the icon is dropped below 360dp screen width or above font scale 1.2, measured with
  `LocalConfiguration`/`LocalDensity` because the row's intrinsic measurement can't run `BoxWithConstraints`). No
  network, no tiles, the same offline. **Tapping anywhere** opens `googleMapsSearchUrl(destination)` =
  `https://www.google.com/maps/search/?api=1&query=` + the trimmed destination percent-encoded as UTF-8 (spaces `%20`)
  with `ACTION_VIEW`: the Google Maps app when installed, else the browser; `ActivityNotFoundException` → snackbar
  "No maps app found" (`openGoogleMaps` in `TripDetailsEffects`, shared with the Plan cards' "Open in Maps").
- **Budget:** see "Budget tab and expense splitting" below.
- `TripDetailsUiState`: `Loading` (nothing drawn) / `NotFound` (not the user's trip or deleted) / `Loaded`.
- **Budget dialog** state lives in the ViewModel (`budgetDialog: BudgetDialogState?`): prefilled with the current
  budget; empty → `EMPTY`, unparseable or ≤ 0 → `INVALID`; Save stores it with `setBudget`.
- **Expense form:** name, amount (currency symbol prefix, decimal keyboard) with a currency selector, date
  (`DateField` with `maxDate` = the trip's last day, **no** `minDate`: flights and hotels are paid before the trip),
  category (`FilterChip`s in the category colors, default `OTHER`). A new expense defaults to today, or to the trip's
  last day once the trip has ended (`ExpenseValidator.defaultDate`). Validation on Save: name not blank; amount see
  "Expense currency"; date not after the trip's end (`DATE_AFTER_TRIP`, "The date can't be after the last day of
  the trip", under the date field). An existing expense dated after the trip (dates shortened later) is kept as it
  is in the list; opening it shows that error right away, and it can't be saved until the date is fixed. Editing a
  field clears only its error. In edit mode the top bar has a delete icon → confirmation dialog → delete. With other
  travelers, "Paid by" and "Split between" sit under the amount (see "Budget tab and expense splitting").
- **Expense row** (`ExpenseRow`): round category icon, name, then the date and the category label (`TagPill` in the
  category colors) on one `FlowRow`; right side the shekel amount in bold and, for a foreign expense, the original
  ("€10.00", `ExpenseCurrency.formatOriginal`) in small gray text. With other travelers (`ExpenseSplitInfo`) a gray
  line with the payer's 18dp avatar ("Paid by Noa · split 3 ways", "Paid by you · not shared", "Paid by you · for
  Noa") and "Your share ₪60" in `primary` under the amounts. With only me neither is shown.

### Budget tab and expense splitting (`ui/common/split`, `ui/tripdetails/split`)
Reference: `design/trip_budget.png`, **with these deliberate differences**: the usual hero `TripHeader`, bottom bar and
sparkle FAB stay as on every tab; "Newest first" is a plain gray label (there is only one order, so no dropdown arrow);
the By category legend uses round dots, ₪0 rows (expenses I'm not part of) come **after** the others, not first.
- **Layout** (top → bottom): `TripHeader`; `BudgetCard` (my share; on this tab without the wallet chip, edit icon next
  to the total); **Travelers** (title + count `Pill`, a sideways-scrolling row of 56dp round avatars with the name under
  each, "You" first and not tappable, then the dashed "+ Add" circle); **Who owes whom** (only with other travelers);
  **By category** (`CategoryChartCard`: donut left with "TOTAL ₪X" inside (whole shekels, rounded half up: `Money.formatWholeShekels`; always one line, shrinks down to `minFitTextScale`; the legend and rows keep their agorot), legend right with dots; stacked below
  300dp or above font scale 1.2; categories with spending of mine largest first, then the ones with only ₪0 for me as
  "₪0 0%" without a slice; nothing at all of mine → an empty `budgetTrack` ring); "Expenses" + "Newest first"; rows (or
  the empty state). The "+ Add expense" pill is in the FAB row (see above).
- **Travelers are names only**, not accounts. "You" is the signed-in member's uid, never a row (see the `travelers`
  table and "Shared trips"). List order: the trip's members (owner first; today only "You"), then travelers by
  `created_at, id` (`travelerOrder(me, memberIds, travelers)`; `Person.You(key)`). Avatars (`TravelerAvatar`): "YOU" on
  `primary`/`onPrimary`, otherwise the first letter or digit of the name on the traveler's `ExtendedColors.travelers`
  fill; initials sized by `travelerAvatarTextStyle` (fits the circle at any font scale; decorative, the name is always
  next to it and in the a11y text). A key no traveler has (removed on another device while used here) shows as
  "Removed traveler" with a gray "?" (`Person.Removed`), and the numbers stay exact.
- **Add / rename / remove** (`TravelerDialog`, state in `SplitViewModel`): "+ Add" → name field; tapping a traveler →
  same dialog prefilled with "Remove". Names: trimmed, not blank (`EMPTY`), not another traveler's name ignoring case
  (`DUPLICATE`), not "You" (`RESERVED`) — `validateTravelerName`. A traveler who paid for or shares an expense or
  appears in a settlement (`isTravelerUsed`, checked when the dialog opens and again by the DAO in the delete
  transaction) can't be removed: "Remove" is disabled and the dialog says "Noa paid for or shares an expense, or
  appears in a settlement, so they can't be removed." `isSaving` is set synchronously (double taps save once).
- **Shares** (`shares`, pure): equal split of the shekel `amountAgorot` in whole agorot; the remainder goes one agora
  each to the first travelers in **list order** (keys sorted by list order, unknown keys last by key), so the shares
  always add up exactly to the amount. `myShare` = my entry (0 when I'm not in the split).
- **Budget counts my share only:** `TripDetailsViewModel` computes `myShares` (expense id → my share) and feeds
  `budgetSummary(spentAgorot = sum)` (left, spent + percent, per-day line) and `categoryTotals(amountOf = my share,
  includeZeroCategories = true)`. The Overview `BudgetCard`, the PDF cost section (`buildTripPdfContent(…, travelers)`,
  categories > 0 only) and the assistant's "spent" (`myTotal`) use the same numbers. An expense I'm not part of counts
  0 but is still listed. Settlements never touch the budget.
- **Balances** (`balances`): per key, paid − shares, + settlements paid, − settlements received; always sum to 0.
  **Transfers** (`simplifyTransfers`): the smallest number of payments: the non-zero balances are split into as many
  zero-sum groups as possible (bitmask DP over subsets, ≤ 16 people with a balance; above that one greedy group), each
  group of k settles with k − 1 transfers (largest debtor pays largest creditor). Sorted: rows with "You" first, then
  the larger amount, then list order.
- **Who owes whom** (`WhoOwesWhomCard`): under the title my net balance (`owesSummary`, pure, `OwesSummaryTest`; display
  only, received − paid over the open transfers): "You're owed ₪820.49" (`primary`), "You owe ₪150" (`budgetWarning`),
  "You're all square" (others still owe each other), no line when all is settled; then "Based on N shared expenses"
  (`sharedExpenseCount`: split ≠ `[payer]`);
  rows on `surfaceContainerHighest`: 32dp avatar → arrow → avatar, "Noa owes you" / "You owe Amit" / "Amit owes Noa",
  the amount (teal when owed to me, natural width, never wrapped). The sentence sits between them when it fits on one
  line there (measured), else under the avatars + amount on the row's full width, and when even avatars + amount don't
  fit side by side (320dp / font 1.5) the amount goes under the sentence; "owes\u00A0you" / "You\u00A0owe" keep the fixed
  words together, so only a long name wraps (`LayoutPolishTest.owesCard_*`); tonal full-width "Settle up" (`primaryContainer`). No open debt → check icon +
  "All settled up", plus a "History" link when payments exist. Hidden when the trip has only me.
- **Settle up** (`SettleUpSheet`, `ModalBottomSheet`, state `SettleSheetState`): radio rows of the open transfers (a
  single one is picked already); the amount field starts at the full amount (`Money.toInput`) and may be lowered for a
  partial payment (`validateSettleAmount`: `EMPTY`, `INVALID` (≤ 0 / unparseable), `TOO_MUCH` (> the open amount));
  "Mark as paid" saves `Settlement(from, to, amount, today)` and closes the sheet; `isSaving` is synchronous. "History"
  switches the sheet to the payments, newest first (like expense rows: "Noa paid you" with the date under it, the
  amount bold on the right, never wrapped), each with a delete icon →
  confirmation → delete (the debt reopens).
- **Expense form:** with other travelers, under the amount: "Paid by" (`FilterChip`s with 18dp avatars, single) and
  "Split between" (a "Not shared" chip = `[payer]`, then one chip per person, multi). New expense: paid by me, split
  between everyone. Changing the payer while "Not shared" keeps it not shared (the split moves to the new payer); an
  empty split → `SPLIT_EMPTY` ("Choose at least one person to split with") on Save. Editing keeps the stored payer and
  split (unknown keys stay, shown as "Removed traveler"). With only me both fields are hidden and the expense saves
  as `me` / `[me]`.
- **A traveler who joined as a member** (`ui/common/split/TravelerMerging.kt`, pure + `TravelerMergingTest`): a
  name-only traveler whose name is a current member's display name or its first word ("Noa" / "Noa Levi", ignoring
  case; matching exactly one member, who hasn't left) is the same person twice. Until merged it is labeled "name
  only" (`Person.Named.nameOnly`: a small label under the Travelers avatar, "Noa (name only)" everywhere `personName`
  is used: expense form chips, Who owes whom, settle sheet, history, expense rows). The Budget tab shows one card at a
  time above Travelers: "Noa Levi joined this trip. Merge with the traveler 'Noa'?" (or "You joined this trip…"),
  "Keep separate" / "Merge" (`SplitViewModel.onMergeSuggestion` / `onDismissSuggestion`; `isMerging` set synchronously).
  "Keep separate" is remembered on this device (`MergeSuggestionStore`, prefs "merge_suggestions", per account, trip,
  traveler and member) and never asked again. **Merging** (`planTravelerMerge`, then
  `TravelerDao.mergeIntoMember`, one Room transaction that plans again against the trip's current rows): the
  traveler's key becomes the member's uid in every payer, split and settlement; a settlement between the two is
  deleted (money to oneself); the traveler is deleted; edits are pending and deletes recorded, so the next sync
  uploads it all. Offered only when **every balance stays exactly the same** (everyone else's, and the member's =
  theirs + the traveler's): an expense split between both (`SHARED_TOGETHER`) or an extra agora that would move to
  someone else in the new list order (`BALANCES_WOULD_CHANGE`) shows why instead, with "OK". Merging into "You" makes
  the traveler's shares count in my budget (said on the card). No schema or rules change. Room side:
  `TravelerMergeRoomTest` (androidTest).
- **One traveler only:** the Travelers section shows "You" and "+ Add"; no Who owes whom card; no split line or
  "Your share" on rows; no split fields in the form. Every number is the same as before splitting existed.

### Expense currency (`expense/ExpenseCurrency.kt`, pure + unit-tested)
- The selector (`SingleChoiceSegmentedButtonRow`) offers **ILS** and one foreign currency: the trip currency (none
  when the trip has no currency or it is ILS → no selector), or, when editing a foreign expense, **that expense's
  own currency** (even if the trip currency changed since). A new expense defaults to the trip currency; an
  edited one to its stored currency. The selector moves under the field below 360dp or above font scale 1.2.
- The form follows `CurrencyRepository.observeRate(code)` for its whole life and calls `refreshRate` once on open
  when `isStale` (no cache or ≥ 12 hours). No cached rate and the fetch fails → `ForeignRateState.Unavailable`:
  the foreign option is disabled (`foreignEnabled = false`) with a short message and the selection moves to ILS —
  **except** for an edited foreign expense, which keeps its currency and stored rate. While the first fetch runs,
  saving a foreign amount reports `NO_RATE`.
- **Save rule** (`resolveAmount`): editing with the same currency and the same amount ("10" = "10.00") keeps the
  stored `amountAgorot` and `ForeignAmount` (rate fixed at the first save). Otherwise ILS → `Money.parseAgorot`, no
  original; foreign → `parseAmount` with the currency's decimals (`java.util.Currency.defaultFractionDigits`:
  JPY 0, EUR 2, KWD 3; unknown → 2), shekels = amount × latest rate, `BigDecimal`, **HALF_UP to whole agorot**, no
  fees, no manual adjustment; ≤ 0 agorot or above `Money.MAX_AGOROT` → `INVALID_AMOUNT`.
- Live line under a valid foreign amount: "≈ ₪40.20 · 1 EUR = ₪4.02 · rate of 2 Oct" (`conversionPreview`,
  `CurrencyConversion.formatRate`, `TripFormatting.dayMonth` of the **service's** rate date); it shows the stored
  rate while an edited amount is unchanged.
- Budget totals, the donut and the Overview budget card only ever sum `amountAgorot`, so they need no conversion.

### Plan tab and itinerary (`Itinerary.kt` pure + unit-tested, `components/ItineraryComponents.kt`)
Reference: `design/trip_plan.png`, **with these deliberate differences**: no top bar ("Plan" title, logo,
profile), no share/map icons, no walking distance / "On Track" / "22 min walk", no "Not scheduled yet" section and
no assistant button inside the tab (the trip screen's sparkle FAB opens the assistant), and **no orange** — each type has its own hue
(`activityTypes`), used for the round type icon, the type label, the timeline dot, the Overview day section
(label, note icon, dot) and the type chips in the activity form. "Booked" is a neutral pill with a check, so it
never looks like a second type label.
- A day is **planned** when it has ≥ 1 activity. Activities outside the trip dates never count.
- **Default day:** today while the trip is happening (`start ≤ today ≤ end`), otherwise (before *or* after the
  trip) the first day. The selected days live in `TripDetailsViewModel` (`planDay`, `overviewDay`, null = default);
  a selection that is no longer inside the trip dates (dates edited) falls back to the default
  (`validDayOrDefault`). The tab itself stays Compose state.
- **Ordering** (`sortActivities`, `ui/common/TripDays.kt`): by date, then start time with **untimed activities last**, then id.
- **Plan tab** (below the usual `TripHeader`, no title of its own: the header shows destination and dates): `DayChipsRow`
  ("Day 1 · Thu 12 Nov", selected = filled teal + check icon, opens scrolled to the selected chip);
  `DaySummaryCard` ("DAILY RHYTHM", "N activities", "~₪210 planned" = `dailyCostAgorot`, hidden when no activity
  of the day has a cost); the timeline (`PlanTimeline` → `TimelineRow` + `ActivityCard`: 24-hour time, title,
  round type icon, note box, type pill, "Booked" pill, then the location with an **"Open in Maps"** button) as **one**
  lazy item so the rail line is unbroken; or the empty state; then the full-width `AddActivityButton` (opens the form
  on the selected day).
- **"Open in Maps"** (`ActivityCard(onOpenInMaps)`, Plan tab only, also on "Outside trip dates" cards; not on the
  Overview's compact cards): shown only when the activity's location text isn't blank; a 48dp `IconButton` with
  `ic_map` in `primary`, a11y "Open in Maps", centered under the round type icon. It opens
  `googleMapsActivityUrl(location, destination)` = `googleMapsSearchUrl("<location>, <destination>")` (the destination
  is left out when blank or already contained in the location, case-insensitive) through the same `openGoogleMaps` +
  "No maps app found" snackbar as the Overview Map card. Nothing is looked up in the app.
- **Outside trip dates:** activities whose date is outside the (edited) trip dates are never deleted; they are
  listed last under "Outside trip dates", with their date shown on the card.
- **Daily plan card:** with activities, "5 of 6 days planned" + `DailyPlanProgress`: one segment per day
  (filled = planned) for trips of ≤ 14 days (`MAX_PROGRESS_SEGMENTS`), one continuous bar for longer trips; "5 days
  set" / "1 open" under it. Without any activity it keeps "No plan yet".
- **Overview day section** (`DayPreviewCard`, hidden while the trip has no activities at all): title from
  `daySectionTitle` — "First day" (day 1, before the trip), "Today" (the current day during the trip), else
  "Day N"; date with previous/next arrows (disabled at the ends); up to 3 compact activities, then "+N more"; an
  empty day shows "Nothing planned for this day" + "Add activity". "See full plan" calls `onSeeFullPlan()` (copies
  the day into `planDay`) and switches to the Plan tab.
- **Activity form** (`ActivityForm(tripId, activityId?, dateEpochDay?)`): title, type (`FilterChip`s, default
  `ACTIVITY`), date (`DateField` limited to the trip with `minDate`/`maxDate`; default = the passed day, else the
  default day), optional start time (read-only field → `AlertDialog` with a 24-hour M3 `TimePicker`, written
  when material3 1.3 had no `TimePickerDialog` (1.4 adds one); × clears it; shared `TimeField`), location (free
  text, optional for every type), estimated cost (₪, optional), "Booked" switch row, note.
  The ViewModel loads the trip first (`isLoading`); a trip that isn't the user's sets `done`. Validation on Save:
  title not blank; cost empty or `Money.parseAgorot` > 0. Edit mode: delete icon → confirmation → delete; the form
  hands the `DeletedActivity` to the app-scoped
  `DeletedActivityStore`, and the trip screen (`TripDetailsViewModel.deletedActivity`, this trip only) shows the
  snackbar "Activity deleted" with "Undo" (`SnackbarDuration.Long`, on every tab): Undo → `restoreActivity`, a dismiss
  keeps the delete. In memory only. An existing date outside the trip is kept unless the user picks another one.

### Prep tab (`ui/tripdetails/prep`, `data/document`, checklist in `data/trip`)
Reference: `design/trip_prep.png`, **with these deliberate differences**: the usual hero `TripHeader` stays on top
(back/edit/share on every tab), then the title; the subtitle reads "19–24 Oct 2026 · Rome, Italy" (dates first, see
"Language and layout direction"); the FAB is the sparkle `AssistantFab`, not an arrow; the bottom bar is the app's.
- **Own ViewModel:** `PrepViewModel` (default owner = the `TripDetails` entry, reads `TripDetails` from the
  `SavedStateHandle`; `Clock` + `computeDispatcher` like the others). `TripDetailsScreen` collects it and passes
  `prep: PrepUiState` + `PrepActions` to `TripDetailsContent` (`prepTab(...)` items); `rememberPrepActions` /
  `PrepDialogs` (`PrepHost.kt`) own the file picker, the viewer intent, the dialogs and the snackbars.
- **Layout:** no title of its own (the header shows destination and dates); `PrepProgressCard` ("12 of 20 done", percent `Pill` in
  `primaryContainer`, bar `primary` on `budgetTrack`, "8 things left before your trip" before the trip / "N things left"
  from its first day / "All done…"; both lists empty → "Nothing on your lists yet" + an invitation, no bar);
  Documents; Checklist.
- **Progress** (`PrepProgress.kt`, pure): counts **both** lists; percent rounded **down** (19/20 → 95%, only all done is
  100%). `dueState`: `OVERDUE` = not checked and due date **before** today (due today is not overdue, checked never is),
  shown in `budgetWarning` orange (a11y "Overdue, due 18 Oct"); otherwise gray.
- **Checklist:** chips "Before the trip (5/8)" / "Packing (7/12)" (selected filled `primary`, the other
  `surfaceContainerLowest` + `outlineVariant` border; `FlowRow`, the selected list is Compose `rememberSaveable`
  state). One card: rows in `created_at` order, tap toggles (`Role.Checkbox`, state "Done"/"Not done", the whole row
  ≥ 56dp is the target), unchecked = empty circle with a 2dp `onSurfaceVariant` ring, checked = filled teal check +
  gray line-through text; **long press** opens a `DropdownMenu`: Edit text (dialog, VM `editText`), Set /
  Change due date (`DatePickerPopup`, "Before the trip" only, no min/max), Clear due date, Delete → snackbar "Item
  deleted" + Undo (`deletedItem` in the VM, `SnackbarDuration.Long`). Last row "Add item…": a `BasicTextField` whose
  text is Compose state; IME Done → `onAddItem` (false for blank text, the field keeps it), cleared on success, the
  custom `onDone` keeps the keyboard open. An empty list shows a line + "Add suggested items" (`string-array`s
  `prep_suggested_before_trip` / `prep_suggested_packing`, added through `addItems` only while that list is empty;
  a double tap adds once). Nothing is ever added automatically.
- **Documents:** title + count pill + "+ Add" (`TextButton`); a `horizontalScroll` `Row` with `IntrinsicSize.Min`
  (all cards as tall as the tallest) of `DocumentCard`s (`Dimens.DocumentCardWidth`, `shapes.large`): type icon in a
  `primaryContainer` square (`ic_flight`, `ic_hotel`, `ic_shield`, `ic_ticket`, `ic_description`), "PDF"/"Photo"
  badge, three-dots menu (Edit → the same form prefilled: name, type, detail line; Delete → confirmation dialog →
  row and file deleted), name, detail, "View file". Tapping the card opens the file. The last card is the dashed "Add
  document" card. Under the row: "Stored on this device only".
- **Adding:** `OpenDocument` with `application/pdf` + `image/*` → `onFilePicked(uri)` → `DocumentFileStore.inspect`
  → `checkPickedFile`: not a PDF/image → "Only PDF files and images can be added."; size > 20 MB → "This file is larger
  than 20 MB…" (no form); unknown size passes and `copyIn` stops at the limit (`DocumentTooLargeException`, same
  message, the partial copy is deleted). Otherwise `DocumentFormDialog` (name prefilled with the file name without its
  extension, type `OTHER`, detail). Save: blank name → error; `isSaving` set synchronously (double taps save once,
  dismiss ignored); the file is copied **on Save**, then the row inserted (a failed insert deletes the copy). A copy
  error closes the form with a snackbar.
- **Viewing:** `fileToView` → `FileProvider` content uri + mime → `ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION`;
  missing file → "This document's file is no longer on this device."; no app → "No app found to open this file".

### Currency card (`CurrencyCard`, `CurrencyConversion.kt`, `data/currency`)
- `TripDetailsViewModel.currencyCard` (`CurrencyCardState?`, null = nothing drawn until the trip loads):
  `NoCurrency` (message + "Choose currency") / `Shekel` (trip currency is ILS: code chip + one-line message, no
  converter, no fetch) / `Loading` (no cached rate, fetching) / `Unavailable` (no cached rate, fetch failed →
  "Try again") / `Rate`.
- `Rate` shows "1 [EUR ▾] = ₪3.44" and an "Updated 2 Oct" pill with the **service's** date
  (`TripFormatting.dayMonth`), never "today". Rates ≥ ₪1 show 2 decimals, smaller ones 4 significant digits.
  A currency worth **less than ₪0.10** (`CurrencyConversion.rateLine` / `PER_SHEKEL_BELOW`) is shown the other way
  round, "₪1 = 495.52 [🇦🇷 ARS ▾]" (`formatUnitsPerShekel`: 1 ÷ rate, half-up, 2 decimals, whole with grouping from
  1,000: "8,446", "570,097"); display only, the converter, stored rates and expenses keep shekels per unit. The line
  never wraps: when it doesn't fit, its text shrinks down to `minFitTextScale` (the chip keeps its size).
- The currency code is a **small outlined chip with a dropdown arrow** (labelLarge, 48dp touch target, a11y
  "Change currency, now EUR") on every state that has a code; tapping it (or "Choose currency") opens the shared
  `CurrencyPickerSheet`, a `ModalBottomSheet` with a search field (matches code or name, codes starting with the
  query first) over `/v1/currencies`. The list is kept in memory after the first success; offline → message +
  "Try again". Selecting saves with `TripRepository.setCurrency`.
- **The currency is only ever chosen by the user** (card or trip form) — never guessed from the destination text. The
  one exception is the trip form of a **new** trip: picking a suggested place preselects its country's currency (see
  "Destination suggestions and flags"); an existing trip's currency never changes by itself.
- **Rate source:** Frankfurter **v2** (no API key), `OkHttpGet` + `kotlinx-serialization-json`:
  `GET https://api.frankfurter.dev/v2/rates?base=ILS&quotes=XXX` → `[{"date","base":"ILS","quote":"XXX","rate":…}]`.
  Asked with base ILS (the service rounds to ~5 digits, so tiny currencies stay exact this way) and **inverted**:
  shekels per unit = 1 ÷ rate, `MathContext(12, HALF_UP)` (`FrankfurterJson.INVERSION_PRECISION`; IRR ≈
  0.00000175408746231). The rate is read from the JSON text into `BigDecimal` (also "2.0e-06"). `FrankfurterJson.parseRates`
  rejects anything that isn't a list (the service's `{"status":422,…}`), no entry for base ILS + the asked quote, a
  rate that isn't a number > 0; `parseCurrencies` (`/v2/currencies`, `[{iso_code, name, …}]`) rejects an empty list
  (all count as failures). **Travel currencies only** (`TravelCurrencies.isOffered`): metals, XDR, CMD, CNH, GGP, IMP,
  JEP and every other `X..` code except the multi-country XAF, XOF, XCD, XPF, XCG are left out (155 of 165 on
  2026-10-12). Rates cached before v2 stay until stale (12 h); expenses keep their stored rate and shekels. The
  manifest declares `INTERNET`.
- **Refresh:** `TripDetailsScreen` calls `onOverviewShown()` in a `LaunchedEffect(selectedTab, loaded,
  currencyCode)` once the trip is loaded. The ViewModel remembers the code it last showed: a **different currency**
  (picked on the card or in the trip form) is fetched right away (`force`), even with a fresh cache, while the
  cached rate stays visible; the same currency is fetched only when `isStale(fetchedAt, now)` (no cache, or fetched
  ≥ 12 hours ago). `onCurrencySelected` on the card starts the forced fetch itself. With no internet the cached
  rate and its date stay on screen; failures never throw out of `CurrencyRepository` (it returns false/null).
- **Converter:** two fields side by side (trip currency, ILS) with a swap button between them; swap only moves the
  fields (both keep their amounts). Input: digits, one `.`/`,`, ≤ 2 decimals, ≤ 12 integer digits (up to
  999,999,999,999.99, for currencies like IRR) — any other keystroke is ignored and the field keeps its value. Math is `BigDecimal`:
  ILS = amount × rate, foreign = amount ÷ rate, both HALF_UP to 2 decimals, shown without trailing zeros or
  grouping ("201", "24.88").
- **Converter text is Compose state, not ViewModel state.** `CurrencyCard` keeps a `ConverterInput` (last edited
  side + its exact text + swap) in `rememberSaveable(code)` and updates it synchronously with the pure
  `CurrencyConversion.edit()` / `swap()`; only the *other* field is derived (`converterState(input, rate)`), so
  the field being typed in is never rewritten and a refreshed rate updates the other field. Keyed by the code, so
  a currency change clears both fields. Do not route the text through a `StateFlow`: `BasicTextField(String)`
  resets its cursor when it recomposes with a value that lags behind what was typed — this caused the
  "can't type 3+ digits" bug, together with boxes that were too narrow.
- **The same rule for every text field** (trip, expense and activity forms, Auth, Prep / Split / budget dialogs,
  currency search): the field shows its own `rememberSaveable` text, starting from the ViewModel's loaded value
  (edit forms compose their fields only after loading), and sends each change to the ViewModel, which keeps its copy
  for validation and saving but never feeds it back while typing. Text the ViewModel sets (a picked destination, a
  newly picked settle-up transfer's full amount) is set in the field by the screen too. Auth sends the values only
  on submit (`AuthInput`), and its passwords use plain `remember`, so they are never saved.
- **Process death:** the trip, expense and activity forms keep a draft (text + dates, category, currency, payer and
  split, picked place, booked, time) in `SavedStateHandle` via `ui/common/SavedFormState`; when editing, a restored
  draft wins over the stored item. Open dialogs come back too: Prep "Edit text" and the document form, Split's
  traveler dialog and settle-up sheet (once the trip loads, if the traveler or transfer still exists), the budget
  dialog, and the Auth mode. Search sheets (currency picker) are not restored.
- **Amounts are always fully visible:** the code label sits above the amount, and the amount font steps down
  from 24sp to 10sp (`ConverterAmountStyles` in `Type.kt`, picked with `CurrencyConversion.largestFittingSize` +
  `rememberTextMeasurer`) until the whole number fits its box; the line height stays fixed.
- The converter is independent of expenses; expenses in the trip currency are described in "Expense currency".

### AI trip assistant (`ui/tripdetails/assistant`, `data/assistant`)
Reference: `design/trip_assistant.png`, **with these deliberate differences**: one header only (`BackTopAppBar`, back
arrow + "Trip assistant"); no profile icon, "AI PRO" badge, "Online" status, map or refresh icons; no walking
distances, weather, plan photo, microphone or like/dislike buttons; **no orange** (timeline dots use `activityTypes`).
- **Model:** Firebase AI Logic, Gemini Developer API backend (`Firebase.ai(backend = GenerativeBackend.googleAI())`),
  model names only in `AssistantModel` (`AssistantPrompt.kt`): `PRIMARY` = `gemini-3.5-flash-lite` (default),
  `FALLBACK` = `gemini-3.8-flash`. Both are stable and support structured output (Firebase AI Logic models page,
  2026-10-01). Flash-Lite was chosen as the default for its free daily limit: free tier ~500 requests/day for
  3.5 Flash-Lite and 3.1 Flash-Lite vs ~20 for the Flash models (3.5–3.8). Google's docs don't publish these numbers
  (they point to AI Studio → Rate limits); 20/day for `gemini-3.8-flash` was confirmed by a real quota error, the
  rest comes from third-party summaries, so check AI Studio when choosing again. Each model has its own quota.
  `AssistantRequest.model` picks the model; no API key in the app or repo. `FirebaseAssistantRepository` builds the
  model per request, because the system instruction (`assistantInstructions(tripContext)`) carries the current trip.
- **Never test against the real model** (no emulator chat requests, no direct calls): the free quota is tiny. Use
  `FakeAssistantRepository` in unit tests; the developer tests on the device.
- **Trip context** (`buildTripContext`, English + ISO dates whatever the device language): destination, today, dates +
  length + whether the trip is before/now/after, every day's activities in timeline order ("nothing planned" for empty
  days), activities outside the dates, trip currency (or "not chosen"), budget total/spent/remaining in ₪ (spent = my
  share of each expense, like the Budget tab; or "no budget set", or "over budget by"). Built from the latest Room data for **every** request.
- **Rules in the instructions:** answer in the language of the user's message; respect the remaining budget; never
  repeat planned activities; say when unsure about opening hours, prices etc.; plain text, no markdown.
- **Structured answer** (`responseMimeType = "application/json"` + `responseSchema`):
  `{reply, plan?: {date: yyyy-MM-dd, title, activities: [{time: HH:mm, title, type: ActivityType name, note,
  place?, costShekels?}]}}` (`place` = the one venue's name, saved as the activity's location text). `AssistantJson.parseReply` is lenient: not JSON / neither reply nor plan → null (`UNREADABLE`);
  a ```json fence is stripped; unknown type → `OTHER`; a time that isn't a valid 24-hour H:mm → null (untimed);
  cost → agorot HALF_UP, ≤ 0 or above ₪1,000,000,000 → null; blank titles dropped; invalid date or no activities →
  no plan (the text stays).
- **History:** each request sends the earlier turns (`chatHistory`: answered user messages + the model's raw JSON),
  so a failed message is never sent twice. Only the last `MAX_HISTORY_TURNS` (20) turns go, always starting with a user
  message, so a long chat doesn't use up the small quota. A message is at most `MAX_MESSAGE_CHARS` (1000) characters:
  the field stops taking text there and shows "n / 1000" from 900 on; `onSend` rejects anything longer. The
  conversation is **in memory only** (never in Room).
- **Chips** (only while the conversation is empty, `suggestionChips`): "Plan day N for me" (first trip day without
  activities; left out when all days are planned), "Dinner ideas for day N" (the default day: today during the
  trip, otherwise day 1), "Am I within budget?". A chip sends its text as a message.
- **Chat UI:** user bubbles right (`primary`), answers left (`surfaceContainerLowest`, sparkle avatar) with "AI
  generated · check details before booking" under each; three pulsing dots while `isSending`; input text is Compose
  `rememberSaveable` state (not ViewModel state, see the converter cursor bug); send is disabled while a request runs,
  for blank text and before the trip loads (`onSend` also returns false then, and the field is only cleared on true).
  The input bar pads for the navigation bar and the keyboard (`MainActivity` uses `adjustResize`).
- **Plan card** (`SuggestedPlanCard`): "SUGGESTED PLAN", "Day N · Fri 13 Nov", title, `TimelineRow` per suggestion
  (time, title, note, type `TagPill`, "~₪90" pill, checkbox checked by default; the whole row toggles). "Add to Day N"
  adds the checked ones via `suggestionInputs` (not booked; the venue the model named, `place`, becomes the location
  text and is never looked up) with `ActivityRepository.addActivity`; status
  NEW → ADDING → ADDED ("Added", disabled, toggles locked). Plan tab and Overview update through their Room flows.
  `withinTrip`/`dayNumber` are recomputed from the **current** trip dates; outside the trip there is no button,
  only a note.
- **Errors** (`AssistantError`, card after the conversation with "Try again" = resend the last message without a
  second bubble): `QuotaExceededException` or a message with quota / rate limit / RESOURCE_EXHAUSTED / 429 →
  `RATE_LIMIT` (the free tier allows only ~20 requests a day for this model); a 503 "high demand" / overloaded /
  unavailable message → `BUSY`. After `BUSY` or `RATE_LIMIT` the ViewModel sends the same request **once** to
  `model.other` (still "typing", no delay) and only then shows the last error; "Try again" starts again with
  `PRIMARY`. Other errors are shown right away. A message mentioning App Check / attestation → `APP_CHECK` (`classifyServerMessage`);
  `RequestTimeoutException` or an `IOException` cause → `NO_CONNECTION`; stopped/blocked/unparseable responses →
  `UNREADABLE`; everything else → `UNKNOWN`. Every failure is logged with its exception
  (`adb logcat -s TripAssistant`), and an answer that isn't readable JSON is logged with its raw text.

### App Check
- Firebase enforces App Check for Firebase AI Logic. `PlanYourTripApplication.onCreate()` calls `installAppCheck()`
  before anything else: `src/debug` installs `DebugAppCheckProviderFactory` (`firebase-appcheck-debug`, debug only),
  `src/release` installs `PlayIntegrityAppCheckProviderFactory` (`firebase-appcheck-playintegrity`, release only; the
  app must be linked in Play Console / have its SHA-256 in Firebase).
- **Debug token:** with `firebase-appcheck-debug` 19.0.2 (BoM 34.12.0) it is logged **only when a new token is created**
  (first launch after install or after clearing app data), Logcat level **Debug**, tag
  `com.google.firebase.appcheck.debug.internal.DebugAppCheckProvider`, text "Enter this debug secret into the allow
  list in the Firebase Console for your project: <token>". Register it in Firebase console → App Check → Apps →
  ⋮ → Manage debug tokens. **Never commit the token.**

### Budget rules (`BudgetCalculations.kt`, pure + unit-tested)
- `budgetSummary(...)` is `null` when there is no budget → card shows a short message and "Set budget".
- left = total − spent (negative = over budget). Over budget shows "₪X over budget" (error color) instead of "left".
- `spentPercent` = spent / total × 100 (one decimal, "57.5%"); the bar is clamped to 0..1 and turns
  `budgetWarning` orange when **more than 80%** is spent (exactly 80% stays teal).
- "About ₪X per day for N days": before the trip N = trip length; during it N = days left **including today**;
  after the trip the line is hidden. Also hidden when nothing is left (≤ 0). X = left / N, rounded **down** to whole
  shekels (exact agorot below ₪1).
- spent = **my share** of every expense (`myTotal`, see "Budget tab and expense splitting"), never the full amounts.
- `categoryTotals` (`ui/common/CategoryTotals.kt`, shared with the PDF) = per-category sums of `amountOf` (my share) > 0,
  largest first (ties by enum order), percent of the total; with `includeZeroCategories` (Budget tab) the categories
  whose expenses count 0 follow as ₪0 / 0%.

## Share trip (PDF) (`ui/common/share`)
- **Entry points:** the three-dots menu of every Home trip card and of the trip header → `ShareTripViewModel.onShareTrip
  (tripId)`. Each screen has its own `ShareTripViewModel` (default owner) and renders `ShareTripHost`.
- **Dialog** (`ShareDialogState` in the ViewModel): "Include costs" switch (on by default; the whole row toggles),
  Cancel and "Create PDF" (`PrimaryButton` with its spinner while `isCreating`; switch, Cancel and dismiss are
  ignored then, and `isCreating` is set synchronously so a double tap creates one PDF). A failure keeps the dialog
  open with "Couldn't create the PDF." and the button reads "Try again". On success the dialog closes and
  `pendingShare` is set; `ShareTripHost` starts `ACTION_SEND` in a chooser (`EXTRA_STREAM` + `ClipData` +
  `FLAG_GRANT_READ_URI_PERMISSION`) and calls `onShareLaunched()`.
- **Data:** the ViewModel reads the trip, its expenses and travelers (only when costs are included) and activities once with
  `.first()` for the current uid; another account's trip or no user → failure, nothing exported.
- **Content** (`buildTripPdfContent`, pure, `TripPdfContent`): header (destination, dates, inclusive length); cost
  section only when costs are included **and** there are expenses: total spent (my share), budget and `BudgetDifference`
  (Under / Over / OnBudget) only when there is a budget, `categoryTotals` (largest first, only > 0, amount +
  percent); then one `PdfDay` per trip day ("Day 1 · Thu 12 Nov") with `activitiesOn` order (untimed last) and
  time, title, type, location, note (blank → null). An empty day says "Nothing planned". When no activity falls
  inside the trip, `days = null` and the PDF has one line instead of the section. The model has **no** email,
  expense list, activity costs/booked flag or activities outside the dates, so they can't leak.
- **File name** (`pdfFileName`): "Rome-Italy-12-17-Nov-2026.pdf" — destination (≤ 60 chars) + `dateRange` in
  English, every run of non-letter/non-digit characters → "-" (Unicode letters, so Hebrew stays; combining marks
  only after a letter, so emoji variation selectors go), nothing left → "Trip".
- **Drawing** (`AndroidTripPdfExporter`, `Dispatchers.IO`): A4 595×842 pt, 48 pt margins, footer band. The body is
  a list of measured blocks; `paginate` (pure) never splits a block, keeps a `keepWithNext` block (section title,
  day heading, "By category") on the page of the block after it, and gives a block taller than a page a page of its
  own where it is clipped (only possible with a giant note). Footer on every page: "Created with Plan Your Trip" +
  "Page N of M". Text is `StaticLayout` with `TextDirectionHeuristics.FIRSTSTRONG_LTR` + `ALIGN_NORMAL` (Hebrew and
  Arabic paragraphs lay out right to left, bidi for mixed text). Inter from `res/font`; API 29+ uses
  `Typeface.CustomFallbackBuilder(...).setSystemFallback("sans-serif")`, older versions get the system fallback
  automatically, so characters Inter lacks still render. Colors come from `PdfColors` (hero gradient header,
  activity type hues, error red for over budget).
- **Files:** `cacheDir/shared_pdfs/` is emptied before each new PDF; shared through
  `androidx.core.content.FileProvider` (authority `${applicationId}.fileprovider`, `res/xml/file_paths.xml`, not
  exported). Failures are logged (`adb logcat -s TripPdf`) and rethrown to the ViewModel.

## Deleting a trip (`ui/common/delete`, `ui/home/components/SwipeToDeleteBox.kt`)
(For a shared trip someone else owns, every entry point below leaves instead: see "Invites and joining".)
- **Entry points:** swipe a Home card (Next trip, Upcoming, Past) to the left; "Delete trip" (red, `ic_delete`) in
  the three-dots menu of every Home card and of the trip header. Each screen has its own `DeleteTripViewModel`
  (default owner) and renders `DeleteTripHost`.
- **Swipe** (`SwipeToDeleteBox`, foundation `AnchoredDraggableState`, end-to-start only): anchors `Closed` 0,
  `Revealed` −`Dimens.SwipeRevealWidth` (96dp), `Dismissed` −card width; 50% positional threshold. A short swipe
  springs back; past the reveal width the card stays open on a red (`ExtendedTheme.colors.swipeDelete`, `ErrorRed` in both
  modes: the dark scheme's `error` is a pale pink) area with `ic_delete` + "Delete" (`onSwipeDelete`, white) that can be tapped; a full swipe or a fast fling opens the dialog right away. While the dialog
  is open for that trip (`deletePending`) the card stays; when it closes without deleting, the card animates back.
  The drag takes only horizontal gestures, so a swipe never opens the trip and a tap still does. TalkBack gets a
  "Delete trip" custom action.
- **Dialog** (`DeleteDialogState(tripId, destination, isDeleting)`): "Delete this trip?", the body names the trip and
  what goes with it (`trip_delete_body` starts with U+200E LRM, so an RTL destination at the start doesn't turn the
  English sentence right to left), "Cancel" and a red "Delete". `isDeleting` is set synchronously (double taps delete
  once; Cancel and dismiss are ignored, a spinner replaces "Delete").
- **Home** confirms with `onConfirm()`: delete with the current uid, then the documents; the dialog closes and
  `message` = `DELETED` ("Trip deleted" snackbar) or `FAILED` (no user, another account's trip, an exception;
  "Couldn't delete the trip. Try again.", nothing deleted). A failure in the documents step still counts as deleted.
  Room's flow re-groups the trips (the next upcoming trip becomes the Next trip, the empty state after the last one).
- **Trip screen** confirms with `takeConfirmed()` (nothing deleted there, so it never flashes "not found") and calls
  `onTripDeleted(tripId)`: `AppNavHost` pops back to Home with `pendingDeleteTripId`, and Home runs
  `deleteConfirmed(tripId)` with the usual animation and snackbar.
- **Sync:** the recorded `TRIP` tombstone uploads with the quick sync, or at the next full sync after the connection
  returns; `FirestoreCloudStore.write` tombstones the trip (keeping `ownerId`/`memberIds`), its shared children and my
  own budget and Packing items, and `planMerge` drops the children's own local tombstones. `firestore.rules` allow it
  only for the trip's **owner** (today every trip's only member).


## Auth screen behavior
Reference: `design/sign_in.png` and `design/sign_up.png`, **with these deliberate differences**: the icon is the real
launcher icon (not the placeholder pin with a star), no "Terms and Privacy Policy" line (the app has no terms page),
no check mark inside a filled field.
- Two modes, `SIGN_IN` / `SIGN_UP`, **without a toggle**: the bottom line "New here? **Create an account**" /
  "Already have an account? **Sign in**" switches (only the bold words are a button, `onModeToggle`).
- Switching modes **keeps** typed email/password and **clears** all errors (and the focus).
- **Hero** (`AuthHero`): at least `Dimens.AuthHeroHeightFraction` (42%) of the screen, taller when its text needs it
  (320dp / font 1.5), edge to edge behind the status bar with **white status bar icons** (`WhiteStatusBarIcons`, the
  previous setting comes back when the screen leaves). The photo (`AuthHeroPhoto`) is a **fixed** layer; the gradient,
  icon and text (`AuthHeroContent`) and the sheet scroll over it, and the scrolling column is **clipped below the status
  bar** (content never draws under the clock), with a fixed `authStatusBarScrim` (dark teal 40%, fading out
  `Dimens.AuthStatusBarFade` below the bar, `StatusBarScrim`) on top. Photos `res/drawable-nodpi/auth_hero_sign_in.webp` /
  `auth_hero_sign_up.webp` (1080 × 1080, cropped from `design/hero_sign_*.jpg` around the harbor / the peak and tree
  line, `ContentScale.Crop`), a vertical gradient transparent → `authHeroScrim` (dark hero teal at 85%), the launcher
  icon (`ic_launcher_background` color + `ic_launcher_foreground` drawn at 108/72 so it looks like the home-screen
  icon, 64dp, shadow), headline (`AuthHeadlineStyle`) and subtitle in `onAuthHero` / `onAuthHeroMuted`. The
  `authHero…`/`authStatusBarScrim`/`onAuthHero…` tokens are the same in light and dark mode: the hero never changes; the sheet follows the theme.
- **Sheet**: `surfaceContainerLowest` with 32dp top corners (`Dimens.AuthSheetCorner`), overlapping the hero by 32dp;
  the screen behind it has the same color, so it always reaches the bottom edge.
- **Fields** (`AuthTextField`: `BasicTextField` + `TextFieldDefaults.DecorationBox` with a custom container): ≥ 56dp,
  `shapes.medium` (16dp), `surfaceContainerHighest` fill and no border at rest; focused: `surfaceContainerLowest` + 1.5dp
  `primary` border; error: `error` border and the message under it. Leading outlined icons (`ic_person_outline`,
  `ic_mail_outline`, `ic_lock_outline`). TalkBack reads the label and the error (the field's `error` semantics; the
  line under it is hidden from TalkBack so it isn't read twice).
- Sign In: email, password (show/hide), "Sign In". There is no "Forgot password?" option (removed on purpose).
- Sign Up: full name, email, password (+ strength bar + "At least 8 characters"), confirm password (each password
  with show/hide), "Create account".
- **Strength bar** (Sign Up, `AuthValidator.passwordStrength`, display only, never changes validation): 4 segments; 1
  when anything is typed, 2 at 8+ characters, 3 at 8+ with letters and digits, 4 at 12+ with letters, digits and a
  symbol (not a space). An error replaces the helper text; the bar stays.
- **Button** (`AuthButton`): full width, ≥ 56dp, pill, `primary`, bold label + arrow, shadow. Always enabled; a tap
  while `isLoading` (spinner) is ignored. A submit stopped by validation increments `AuthUiState.failedSubmits`, and the
  screen moves focus to the first field with an error.
- **Keyboard:** the column has `imePadding()` and scrolls (the hero scrolls away); whenever a field gets focus or the
  keyboard's height changes, the screen scrolls so the focused field **and** the button are above the keyboard
  (`AuthLayout` + `BringIntoViewRequester`, after `KEYBOARD_SETTLE_MILLIS`). IME "Next" moves to the next field, "Done"
  (last field) submits.
- A valid form calls `AuthRepository` (Firebase). Sign up creates the account and saves the full name as the display
  name. On success the ViewModel sets `isAuthenticated`, which navigates to Home.
- **Requests and double taps:** while sign in/up runs, the main button shows a spinner (`isLoading`). `onSubmit`
  returns early while `isLoading`, and the flag is set synchronously, so double taps send one request.
- UI test `ui/auth/AuthScreenTest` (androidTest, real `AuthViewModel` + a stub repository, never Firebase): no toggle,
  the bottom links, empty-field messages and focus on both screens, field + button above the keyboard, 320dp / 1.5;
  it saves full-screen pictures (status bar and keyboard included) to the app's `files/auth-shots/`.

### Backend errors (`AuthError`, mapped in `FirebaseAuthRepository`)
| Firebase exception | `AuthError` | Shown |
|---|---|---|
| `FirebaseAuthInvalidCredentialsException`, `FirebaseAuthInvalidUserException` | `INVALID_CREDENTIALS` | under password: "Wrong email or password" |
| `FirebaseAuthUserCollisionException` | `EMAIL_IN_USE` | under email |
| `FirebaseAuthWeakPasswordException` (checked first: it extends InvalidCredentials) | `WEAK_PASSWORD` | under password |
| `FirebaseNetworkException` | `NO_CONNECTION` | snackbar |
| `FirebaseTooManyRequestsException` | `TOO_MANY_REQUESTS` | snackbar |
| anything else | `UNKNOWN` | snackbar |

- Firebase's email-enumeration protection reports a wrong email and a wrong password the same way, so the app shows
  one message for both.
- Snackbar messages live in `AuthUiState.message` (`AuthMessage.Failure`) and are
  cleared with `onMessageShown()` after display.
- If the account is created but saving the display name fails, sign up still succeeds; the greeting falls back to
  the email prefix.

### Validation rules (`AuthValidator`)
| Field | Sign In | Sign Up |
|---|---|---|
| Full name | — | not blank |
| Email | not blank + valid format | not blank + valid format |
| Password | not empty | ≥ 8 characters |
| Confirm | — | equals password; both empty → "Please confirm your password" (`EMPTY_CONFIRM`) |

- Validation runs **when the submit button is tapped** (not while typing). Editing a field clears only that field's error.
- Errors are shown under the field (the `DecorationBox` `supportingText` slot) with an error border; see "Auth screen behavior".
- The 8-char minimum is enforced only on Sign Up: existing accounts may predate the rule, and the backend decides
  whether a sign-in password is correct.
- Email regex is our own `Regex`, not `android.util.Patterns`, because `Patterns` is null in JVM unit tests.
- Email and name are trimmed before being sent to the repository; passwords are sent as-is.

## Trip form (new + edit, `ui/tripform`)
- Fields: destination, start date, end date, trip currency. Edit mode ("Edit Trip", "Save changes") prefills the
  trip and calls `updateTrip`, which stores destination, dates and currency and keeps color and budget. Dates are read-only fields that open a Material 3 `DatePickerDialog`.
- Validation on Save (`TripValidator`): destination not blank; both dates chosen; end not before start (a one-day
  trip is fine). Errors show under each field. Changing either date clears both date errors.
- The end-date picker disables days before the start date, but the ViewModel still validates.
- **DatePicker millis are UTC midnight.** Always convert with `toPickerMillis()` / `pickerMillisToLocalDate()`
  (`ui/common/PickerDates.kt`). Converting through the device time zone shifts dates by a day.
- Past dates are allowed, so finished trips can be logged.
- No budget fields — the budget is set on the trip screen.
- **Trip currency (optional):** read-only field "EUR · Euro" (name from the loaded list, else the platform's
  `java.util.Currency` name) that opens the shared `CurrencyPickerSheet`; a clear (×) icon removes it. A new trip
  starts with **no** currency (never derived from free text); picking a suggested place may preselect one (below).
  `TripFormViewModel` takes `CurrencyRepository` for the list; the code is saved with `addTrip(..., currencyCode,
  place)` / `updateTrip(..., currencyCode, place)`. The field shows the currency's flag (leading icon).

