# Design rules

Reference: `design/home_screen.png`. Match it closely when adding or changing screens.
- **Use theme tokens, never hard-coded colors in screens.** Material roles come from `MaterialTheme.colorScheme`;
  brand colors without a Material role come from `ExtendedTheme.colors` (hero gradient and its overlay, wave and glow
  colors, the destination photo's tint, shade and chip (`heroPhoto…`, see "Destination photos"), the Sign In / Sign Up hero
  (`authHeroScrim`, `authStatusBarScrim`, `onAuthHero`, `onAuthHeroMuted`), mint dot, badge, past-card colors, pastel letter squares, `budgetWarning` orange (also overdue due dates), `budgetTrack`,
  `iconChip`, `categories`, `activityTypes`, `travelers` and `removedTraveler`). Raw color values live only in `ui/theme/Color.kt` and
  `ExtendedColors.kt` — no exceptions.
- **Activity type and category colors** (`ExtendedColors.activityTypes` / `categories`, both
  `Map<…, TintedColor>`): each entry has a `content` color (icon, label text, chart slice, timeline dot) and a
  light `container` tint, with dark-mode variants. Raw values are the `Hue*` constants in `Color.kt`; a hue means
  the same thing in both maps:

  | Hue | Activity type | Expense category |
  |---|---|---|
  | teal (brand) | Activity | — |
  | rose | Dining | Food |
  | purple | Lodging | Hotels |
  | indigo | Transport | Transport |
  | gray | Other | Other |
  | sky blue | — | Flights |
  | green-teal | — | Attractions |
  | green | — | Shopping |

  **Never orange** (it means a warning: close to or over budget, or an overdue Prep checklist item). Used for: Plan cards (icon, label, dot, note icon),
  Overview day section, activity form chips, donut slices, legend and row icons (`CategoryIcon`, round), row
  category labels, expense form chips. `TintedColorsTest` checks, for both maps in both modes, ≥ 4.5:1
  content-on-tint contrast, ≥ 3:1 on the background, no repeated colors, no `budgetWarning`, and that shared
  meanings share their hue — keep it passing when changing them.
- **Traveler avatar colors** (`ExtendedColors.travelers`, `TintedColor(container = fill, content = initials)`): the
  same `Hue*` constants as above (rose, indigo, purple, sky, green, green-teal), saturated fill + white initials in
  light mode, the light `…Dark` hue + `Night` initials in dark mode; `removedTraveler` = gray. "You" is
  `primary`/`onPrimary`. Never orange; `TintedColorsTest` checks ≥ 4.5:1 initials contrast, no repeats, both modes.
- **Colors:** brand teal (`primary` #0F6E86, darker `secondary` #0B5A6E for the FAB), warm off-white background
  #FBF8FA, white cards (`surfaceContainerLow`: white in light mode, `NightSurface` in dark mode; trip screen, Home's
  Upcoming card, assistant answer bubbles and the Suggested plan card), gray secondary text (`onSurfaceVariant`). A dark
  scheme exists for every token. In dark mode `surfaceContainerLowest` **is the background** (`Night`), so it is only
  for things that should match the screen (the auth sheet, sheets, bordered chips and fields); a card on it would have
  no visible edge (its 1dp shadow doesn't show on a dark background).
- **Dynamic color is disabled** on purpose, so wallpaper colors on Android 12+ never replace the brand teal.
- **Shapes** (`ui/theme/Shape.kt`): `small` 12dp text fields · `medium` 16dp letter squares · `large` 20dp trip
  cards · `extraLarge` 28dp hero card. Buttons and the FAB are pills (`CircleShape`).
- **Typography:** bundled Inter (Regular/Medium/SemiBold/Bold).
  - `headlineLarge` 40 Bold → screen titles ("My Trips", "Plan Your Trip")
  - `headlineMedium` 30 Bold → hero destination
  - `headlineSmall` 24 SemiBold → section titles
  - `titleLarge` 20 SemiBold → card titles
  - `displaySmall` 44 Bold → countdown number
  - `bodyLarge` 17 → dates and body text
  - `labelLarge` → buttons and the "NEXT TRIP" / "TRIP BUDGET" labels (letter-spaced, via `HeroLabelStyle`)
  - Named styles in `Type.kt` for values that are not a Material role: `HeroLabelStyle` (letter-spaced caps on the
    hero card) and `DatePickerHeadlineStyle` (the DatePicker uses `headlineLarge` internally, so the picker gets this
    calmer style through a local `MaterialTheme` override). Never write `fontSize`/`letterSpacing` in screens.
- **Sizes** (`ui/theme/Dimens.kt`): `ScreenPadding` 20dp screen side padding, `AvatarSize` 56dp avatar and letter
  squares, `ButtonHeight` 52dp primary buttons, `IconChipSize` 44dp round icon chips, `CategoryIconSize` 40dp
  category squares, `TouchTarget` 48dp, `CardMenuIconSize` / `CardMenuIconInset` / `CardMenuEndPadding` (home card
  three-dots button), `MapPreviewMinHeight` 88dp (Overview Map card's picture), `SwipeRevealWidth` 96dp (Home card's red "Delete" area),
  `TravelerAvatarSize` 56dp / `TravelerAvatarSmallSize` 32dp / `TravelerAvatarTinySize` 18dp / `TravelerAvatarChipSize` 18dp (traveler avatars).
  Responsive breakpoints: `CompactScreenWidth` 360dp (home badge, expense currency selector, Map card pill icon),
  `NarrowContentWidth` 300dp (header status pill, converter rate pill, category chart) and `LargeFontScale` 1.2
  (`isLargeFontScale()`). Add a value there instead of repeating a number in several files.
- **Spacing:** 16dp between cards, 16–24dp inside cards. The home list has an extra 104dp bottom padding so the FAB
  never covers the last card.
- **Reuse the shared components** before writing new UI: `PrimaryButton` (pill button with optional loading
  spinner, optional leading icon), `BackTopAppBar` (optional actions), `BrandMark` (gradient circle with the plane),
  `DateField` (optional `minDate`/`maxDate`), `HeroWaves`, `LetterSquare`, `Pill`, `TripDatesRow`,
  `TripMoreMenu`/`TripMoreButton`, `EditFormScaffold` (create/edit forms), `ConfirmDeleteDialog`, `TintedFilterChip`.
  Trip-area cards that need a gray fill (note box, day summary, compact activity cards, progress box, unselected
  day chips) use `surfaceContainerHighest`. The upcoming and past cards share one private `TripListCard` skeleton.
- **Long text never gets cut off.** The trip dates line stays on one line by dropping the length, then shrinking (see
  "Home screen rules"); other dates have no `maxLines` and wrap; destinations shrink a little, then wrap between words (never inside one) to at most 2–3 lines
  with an ellipsis. Text columns use `weight(1f)`. Upcoming cards move the countdown badge under the dates when
  the card is narrower than 360dp or the font scale is above 1.2. Trip lengths and badges use non-breaking spaces
  ("6 days", "In 120 days"), so a line can break only before the length. Check new layouts with the
  320dp / fontScale 1.5 preview in `HomeScreen.kt`.
- Icons are small vector drawables in `res/drawable` (Material Symbols paths), not `material-icons-extended`.

