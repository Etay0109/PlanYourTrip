# Language and layout direction

The app looks and behaves as in English on every phone, also when the phone's language is Hebrew, Arabic or any
other RTL language. There are no translations (`res/values` only).
- **One locale:** `AppLocale.locale` (`ui/common/AppLocale.kt`, `Locale.US`). Every formatter (`TripFormatting`,
  `Money`, `CurrencyConversion.formatRate`/`currencySymbol`, `ExpenseCurrency.formatOriginal`, `currencyName`) takes
  it as its **default** `locale` parameter — never `Locale.getDefault()`. Formats that must not depend on any locale
  (Firestore "HH:mm", `Money.toInput`) use `Locale.ROOT`. `AppLocaleTest` runs every formatter with a he-IL, ar-EG,
  fa-IR and de-DE JVM default and expects the English output; add new formatters there.
- **Resources:** `MainActivity.attachBaseContext` wraps the context with `withAppLocale()` (`AppLocaleSetup.kt`):
  a configuration context with `setLocale(AppLocale.locale)` + `setLayoutDirection(...)`. Compose reads
  `LocalConfiguration`/`LocalLayoutDirection` from it, so every screen, dialog, menu, bottom sheet, snackbar and the
  Material date/time pickers (month and day names, labels) are English and LTR. Works on all API levels (26+),
  no AppCompat or per-app language API needed.
- **Layout direction, twice more:** the manifest has `android:supportsRtl="false"` (the View system, which Compose's
  `onRtlPropertiesChanged` follows, stays LTR), and `androidResources.localeFilters += "en"` in `app/build.gradle.kts`
  drops the libraries' translations from the APK, so built-in labels are English even outside the wrapped context.
- **JVM default:** `applyDefaultAppLocale()` sets `Locale.setDefault(AppLocale.locale)` for library code that reads
  it, in `Application.onCreate`, `onConfigurationChanged` (Android resets the default when the system language
  changes) and every `withAppLocale()`.
- **User text in Hebrew/Arabic** (destinations, titles, notes, expense names, chat messages) keeps Compose's
  default text direction: in an LTR layout it resolves to `ContentOrLtr`, so a paragraph whose first strong
  character is RTL reads right to left (bidi for mixed text) without mirroring the layout. Never set
  `TextDirection.Ltr` on text that can hold user input. String resources that embed user text start with fixed or
  LTR text ("%1$s · %2$s" = dates · destination), so the line order stays as in English.
- The PDF keeps its own RTL paragraph handling (`FIRSTSTRONG_LTR`); its fixed labels are English strings.
- Money stays "₪2,550" (`money_shekels` = "₪%1$s", grouping from `AppLocale`).

