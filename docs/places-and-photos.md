# Destination suggestions, flags and photos

## Destination suggestions and flags (`data/place`, `ui/tripform`, `ui/common/Flags.kt`)
- **Suggestions** (Add trip and Edit trip), from 2 characters (trimmed), in two steps (`DestinationSuggestions`):
  **instant** rows from the built-in table on every keystroke, no debounce, offline too (`instant`: Hebrew text →
  `HebrewPlaces`, anything else → `EnglishPlaces`: the English name or the label "Paris, France", start of the name or of
  a later word, accents/case/dashes ignored, exact first, then table order, ≤ 5); then, 300 ms after the last keystroke
  (`TripFormViewModel.LOOKUP_DELAY_MILLIS`), Photon (`remote`, null on failure). `merge` keeps the instant rows where
  they are and adds Photon's below, minus repeats (`isSamePlaceAs`: same name, country and kind within 50 km, or a
  country), ≤ 6 rows. A new keystroke cancels the running lookup **and disconnects its request**
  (`PhotonPlaceSearch`), and an answer for older text is never shown. While Photon runs, `TripFormUiState.isSearching`
  shows a 3dp `primary` `LinearProgressIndicator` in the gap under the field (a live region: TalkBack says "Searching");
  it ends on the answer, a failure, text below 2 characters, a pick or focus loss (a keystroke during a search keeps
  it). The list (`DestinationSuggestionList`): flag (or a pin), name, "England, United Kingdom", then "© OpenStreetMap
  contributors". `DestinationFieldWithSuggestions` draws the bar and the list **over** the fields below (a `Layout`
  that reports only the field's height, `zIndex`), so typing never moves them; a trailing spacer after Save gives room
  to scroll the list's last rows into view. It closes when the field loses focus. No network, a failed or bad answer
  → only the instant rows, **never an error**.
- **Photon** (`https://photon.komoot.io/api/?q=…&lang=en&limit=10&osm_tag=place`, no key, `User-Agent:
  PlanYourTrip/<versionName> (Android; com.example.planyourtrip)`, 10 s timeouts). `PhotonJson` keeps `osm_key` place
  with `osm_value` country, state, region, province, county, city, town, village, municipality, island, archipelago
  (streets, shops, houses, neighbourhoods are dropped). A suggestion is a repeat only when name, region **and**
  country are the same (London, England and London, Ontario both show).
- **Hebrew:** Photon has no Hebrew names for places abroad ("לונדון" finds only streets in Israel). Text with Hebrew
  letters is matched against `HEBREW_PLACE_TABLE` (`HebrewPlaces.search`: no niqqud, geresh/quotes ignored, maqaf/dash
  = space, final letters = regular ones; the start of a name or of a later word; exact first, then table order; ≤ 5),
  offline too, then Photon's results for the raw text are added (places in Israel). The table is **generated data**:
  a hand-written list (Hebrew name, spellings, English query, **expected country code**) was looked up once on Photon;
  the generator failed on any entry whose top result had another country or a type outside the allowlist, and those
  were reviewed by hand. Countries' Hebrew names come from the JDK (CLDR). `HebrewPlacesTest` checks every entry and
  that no Hebrew name or alias is in two entries. Add places by regenerating (`tools/hebrew-places/README.md`), not by
  guessing coordinates.
- **Picking** fills "Name, Country" (`PlaceSuggestion.label`; a country is just its name), closes the keyboard, and
  remembers `TripPlace(countryCode, latitude, longitude)`; **any edit of the text forgets it** until a suggestion is
  picked again (also when the same text is typed back). Free text saves `TripPlace.NONE`. Edit trip keeps the stored
  place while the text is unchanged. Invites still copy the destination text only.
- **Region rows** (`RegionKind`, `PlaceSuggestion.regionKind`): a province, state, region or county never fills the
  same text as the city with its name. Photon's `state` → "State" (US, AU, BR, MX, IN, DE, AT, MY, NG, VE, SS, SD),
  "Province" (AR, CA, NL, CN, … `PROVINCE_COUNTRIES`) or "Region" (anywhere else: Tuscany, Catalonia); `province` →
  Province; `region` → Region; `county` → County; the UK's England/Scotland/Wales/Northern Ireland get no word. The
  label adds the word ("Buenos Aires Province, Argentina", once if the name already ends with it); the row shows the
  plain name + a small gray label ("Province"). Repeats (`sameness`) also compare the kind.
- **Currency preselection** (new trip only, `preselectCurrency`): after a pick, when no currency is set or the current
  one was preselected and untouched (`currencyPreselected`), the country's currency (`countryCurrency`: platform data,
after `CURRENCY_CHANGES` for recent changes the JVM / older Android ICU may lack: BG and HR → EUR, VE → VES, SL → SLE,
ZW → ZWG, MR → MRU, ST → STN, BY → BYN, CW/SX → XCG; every ISO country resolves to an offered code, tested)
  is set **only if** the rates service offers it (`currencyRepository.currencies()`; unavailable → nothing); a later
  pick replaces or clears a preselected one; choosing or clearing by hand ends it. Edit trip never changes it.
- **Flags** (display only, nothing stored for currencies): `countryFlag(code)` = regional indicators, null unless two
  letters; `currencyFlag(code)` from the one fixed table (149 currencies, every offered one with a single country;
  `EUR` → 🇪🇺); `CURRENCIES_WITHOUT_FLAG` = XAF, XOF, XCD, XPF, XCG, ANG (several countries each). `FlagsTest` +
  `test/.../OfferedCurrencies.kt` pin the service's list and the offered list (2026-10-12). Shown as their
  own decorative `Text` (`FlagEmoji`, no TalkBack text): before the destination on the Next trip, Upcoming and Past
  cards and the trip header (`DestinationTitle`, which shows `shortDestination`: with a country code, "Name, Country" →
  "Name", cut at the last ", "; otherwise the full text; display only, TalkBack and everything else keep the full text), and before currency codes in the currency picker, the trip form's
  currency field, the Overview currency card's code chip and converter labels, and the expense form's ILS/foreign
  buttons (`CurrencyCodeLabel`).
- **Maps:** the Overview map card and "Open in Maps" still search the destination/location **text**, also when
  coordinates exist (a coordinates search shows a bare pin); the coordinates are kept for destination photos and a
  future Map tab.

## Destination photos (`data/photo`, `ui/common/components/DestinationPhoto.kt`)
- **Where:** the Next trip card and the trip header (all four tabs) draw the photo (960 px) behind their content; the
  Upcoming and Past cards show a 56dp thumbnail (250 px) in place of the letter square (`PhotoSquare`, same size and
  shape). Not in the PDF, the assistant or anywhere else.
- **No photo = exactly as before:** no URL, loading or a failed load draws nothing (no placeholder, spinner or error
  icon, no size change). A downloaded photo fades in (400 ms, together with its scrim); one from the memory cache shows
  at once. `HeroPhotoState.visibility` (0..1) also blends the hero's muted texts to full white, the countdown box,
  status chip and round buttons to `heroPhotoChip`, adds a chip behind the Next card's menu, and fades the gradient's
  `HeroWaves` out.
- **Scrim** (`HeroPhotoScrim`, tokens `heroPhotoTint` / `heroPhotoShade` / `heroPhotoChip`): a light tint over the whole
  photo, then the shade only where text sits, so the place stays recognizable. Next card: `TEXT` (0.55) up to where
  the label, destination and dates end (each measured, they wrap their content), then easing to `OPEN` (0.10) at the
  card's right edge (countdown box and menu have chips). Header: `STATUS_BAR` (0.18) at the top easing to `OPEN`
  around the buttons, rising to `LARGE_TEXT` (0.40) at the measured title and `TEXT` at the dates line to the bottom.
  Every change is a smoothstep fade (`HeroPhotoScrim.easedStops`, ≥ `MinFade` 96dp where there is room): a short
  linear drop showed a visible seam on bright photos. Text positions come from `HeroPhotoState.track` (bounds relative to the
  hero); until measured the whole hero gets `TEXT`. `HeroPhotoTest` checks, on a pure-white and a pure-black pixel in
  both themes: small text ≥ 4.5:1, the large bold title ≥ 3:1, chip text ≥ 4.5:1 even in the open area, and the
  scrim ≤ 0.4 opaque where no text sits. Change the levels or tokens only with it passing.
- **Lookup** (`WikipediaPhotoSource`, no key, `User-Agent: PlanYourTrip/<version>
  (https://github.com/Etay0109/PlanYourTrip; Android; com.example.planyourtrip)`, no email; Coil's downloads send the
  same): search Wikipedia for the destination text (5 results, `generator=search` + coordinates, page image,
  pageprops); text with Hebrew letters searches he.wikipedia first, then en. **Picked place:** the first result (in
  search order, not disambiguation) whose own coordinates are within 50 km (city, landmark, no type), 400 km (`state`,
  `adm1st/2nd/3rd`, `isle`, `region`) or 4,000 km (`country`, `continent`) of the trip's coordinates ("London" with
  Ontario's coordinates skips London, Greater London and Chelsea). **Fallback** (a picked place only, when no page
  passes): search again with `fallbackName` (the text before the first comma without a trailing Province/State/
  Region/County: "Buenos Aires"; the first results are reused when that is the text itself) and take the first page
  whose **title starts with it**, whose coordinate type is a place or missing (never edu, landmark, …) and which is
  within 400 km (`chooseNamedNearby`): the province's Wikipedia point is ~75 km from Photon's. London, England is
  still never taken for London, Ontario. **Free text:** the first result. Only that page is used (a nearby landmark
  never stands in).
- **Image** (`PhotoRules`): JPEG or WebP only (SVG, GIF, TIFF and **PNG** never: places' PNG lead images are maps,
  e.g. Santorini's and Mykonos'), ≥ 400 px wide, aspect 0.6–4.0, and no blocked word in the file name (whole words:
  flag, coat, arms, coa, emblem, seal, logo, map(s), locator, location, locmap, orthographic, montage, collage,
  collection, mosaic, composite, banner, symbol, icon, diagram, wappen, escudo, blason, collagem, montaje, montagem,
  montaggio, and Hebrew דגל, סמל, מפה, מפת, מונטאז, קולאז); collage, collagem, montage, montaje, montagem and montaggio
  also **glued** to other words ("DubaiCollage"). Order: the page's **landscape** lead image → its Wikidata item's
  image (P18, same rules; tried first when the lead is portrait, often a collage) → a portrait lead → the largest
  landscape JPEG among the page's images → `NotFound`. Every rejected image is logged with its reason
  (`PhotoRules.rejection`: "type image/png", "312 px wide", "shape 0.42", "blocked word 'dubaicollage'"). Sizes are Wikimedia's standard thumbnail widths 960 and 250 (a smaller
  original is used as is). Which step and file each destination got is logged (`adb logcat -s DestinationPhoto`).
- **Storage** (`RoomDestinationPhotoRepository`, one consumer coroutine, so a key is never looked up twice at once):
  found → kept for good; `NotFound` or an unreadable answer (`PhotoLookupException`) → stored with null URLs, retried
  after 7 days (**once per `PhotoRetryFlags.CURRENT_VERSION`**, prefs "photos" `not_found_cleared_version`, every
  stored "nothing found" is deleted before the first lookup, so trips retry under new rules right away; version 1 =
  the province fallback and collage rules); a connection failure (`IOException`) is not stored: offline nothing is tried, and it is retried when the
  connection comes back (offline → online only) or a minute later when a screen asks again. Images are cached by Coil
  (`cacheDir/destination_photos`, 50 MB, cache headers ignored), so photos show offline after the first load.
- **Wiring:** `HomeViewModel` asks for every trip's `photoQuery` whenever the trip list emits and puts
  `photos: Map<tripId, DestinationPhoto>` in `HomeUiState.Content`; `TripDetailsViewModel.headerPhoto` (its own
  `StateFlow`) follows the trip's query. An edited destination is a new key, so it is looked up and its photo replaces
  the old one. Nothing about photos goes to Firestore; `firestore.rules` and the sync are untouched.
- **Credit:** not shown yet; `file_page_url` keeps each image's Commons page for when it is.
- Tests: `PhotoRulesTest`, `WikipediaPhotoSourceTest` (recorded real answers in `src/test/resources/wikipedia/` +
  hand-written ones, fake `HttpGet`), `DestinationPhotoRepositoryTest` (`FakePhotoSource`, `FakeDestinationPhotoDao`;
  use `runCurrent()`, the consumer runs in `backgroundScope`), `HeroPhotoTest`, `HomeViewModelTest` /
  `TripDetailsViewModelTest` (`FakeDestinationPhotoRepository`), androidTest `DestinationPhotoRoomTest`. **Never test
  against the real Wikipedia** in automated tests.

