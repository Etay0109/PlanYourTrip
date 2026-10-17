# Data model (Room)

Database `plan_your_trip.db`, `AppDatabase` version 17, eleven tables:

Nothing on the device is backed up by Android or copied to a new phone (`allowBackup="false"`, and
`data_extraction_rules.xml` excludes everything for cloud backup and device transfer): Firestore is the backup, and
travel documents stay on this device only.

| `trips` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `owner_id` | TEXT, indexed | uid of the **signed-in account this local copy belongs to** (default `""`; the name predates shared trips). Every query scopes by it |
| `destination` | TEXT | trimmed before insert, never blank |
| `start_date` | INTEGER | `LocalDate` stored as epoch day |
| `end_date` | INTEGER | epoch day, `end_date >= start_date` (validated in the form) |
| `color` | TEXT | `TripColor` enum **name** |
| `budget_agorot` | INTEGER, nullable | **my own** budget in agorot (personal, `memberData/{uid}`); `null` = no budget (a new trip has none) |
| `currency_code` | TEXT, nullable | ISO 4217 code of the destination currency ("EUR"); `null` = not chosen yet |
| `uuid` | TEXT, unique with `owner_id` | stable id, the same in Room and Firestore (the document id); see sync columns below |
| `updated_at` | INTEGER | epoch millis of the last change on any device |
| `pending_sync` | INTEGER | Boolean, 1 = changed here and not uploaded yet |
| `cloud_owner_id` | TEXT, default `''` | uid of the trip's owner in the cloud (who created it; only they can delete it) |
| `member_ids` | TEXT, default `''` | the members' uids, joined by `,`, in the cloud's order (owner first, then join order) |
| `budget_updated_at` | INTEGER, default 0 | epoch millis of the last budget change; 0 = never set (no `BUDGET` item) |
| `budget_pending` | INTEGER, default 0 | Boolean, the budget still has to be uploaded (`setBudget` never touches `updated_at`/`pending_sync`) |
| `country_code` | TEXT, nullable | ISO 3166 code ("GB") of the picked destination's country; `null` = free text (shared, synced) |
| `latitude`, `longitude` | REAL, nullable | the picked place's coordinates, both or neither (`TripPlace.normalized()`); not used by the map links |

| `expenses` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** (deleting a trip deletes its expenses) |
| `name` | TEXT | trimmed, never blank |
| `amount_agorot` | INTEGER | > 0; the **shekel** amount the budget uses, also for foreign-currency expenses |
| `category` | TEXT | `ExpenseCategory` enum **name**; unknown names fall back to `OTHER` |
| `date` | INTEGER | epoch day |
| `original_amount` | TEXT, nullable | amount as typed in a foreign currency, exact `BigDecimal` text ("10.00", "1500") |
| `original_currency` | TEXT, nullable | ISO 4217 code of that amount |
| `exchange_rate` | TEXT, nullable | shekels per 1 unit used for the conversion, exact `BigDecimal` text |
| `rate_date` | INTEGER, nullable | epoch day of that rate (from the rates service) |
| `paid_by` | TEXT, default `'me'` | traveler key of the payer: a **member's uid** (the signed-in one is "You") or a traveler `uuid`. The SQL default is the legacy `'me'` (SQLite can't change it); every insert passes the key |
| `split_between` | TEXT, default `'me'` | traveler keys sharing it equally, joined by `,` (`Converters`), never empty; `[payer]` = "not shared" |

The four `original_*`/`exchange_rate`/`rate_date` columns are all set or all null (null = entered in shekels);
they map to `Expense.original: ForeignAmount?`. `paid_by`/`split_between` map to `Expense.paidBy`/`splitBetween`
(an empty split reads as the payer only); the repositories drop duplicates and store an empty split as `[paidBy]`.

**Sync columns:** `trips`, `expenses` and `activities` all have `uuid` (TEXT NOT NULL, a random UUID made at insert;
unique per account: `trips(owner_id, uuid)`, children `(trip_id, uuid)`, so two accounts on one phone can each hold a
copy of a shared trip), `updated_at` (INTEGER, epoch millis, set from the injected `Clock` on every insert/update) and
`pending_sync` (INTEGER, default 1). The Long `id` stays the local primary key (routes, ViewModels and repository
interfaces use it); it differs between devices and is never synced.

| `deletions` column | Type | Notes |
|---|---|---|
| `uuid` | TEXT, PK with `owner_id` | the deleted item's uuid |
| `owner_id` | TEXT, indexed, PK with `uuid` | the account that deleted it |
| `kind` | TEXT | `SyncKind` name: `TRIP`, `TRAVELER`, `EXPENSE`, `ACTIVITY`, `CHECKLIST`, `SETTLEMENT`, `PACKING` (`BUDGET` is never deleted on its own) |
| `trip_uuid` | TEXT, nullable | the trip of a child item (the cloud path needs it); null for a trip |
| `deleted_at` | INTEGER | epoch millis of the delete |

A row exists only while the delete waits to upload (local tombstone).

| `activities` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** (deleting a trip deletes its activities) |
| `date` | INTEGER | epoch day; may fall outside the trip after its dates are edited (never deleted for that) |
| `start_time` | INTEGER, nullable | `LocalTime` as minute of the day (0..1439); `null` = no time |
| `title` | TEXT | trimmed, never blank |
| `note` | TEXT, nullable | trimmed; blank is stored as `null` |
| `type` | TEXT | `ActivityType` enum **name**; unknown names fall back to `OTHER` |
| `location` | TEXT, nullable | free text, trimmed; blank is stored as `null`; optional for every type ("Open in Maps" searches it) |
| `estimated_cost_agorot` | INTEGER, nullable | > 0 when set (validated in the form); `null` = no estimate |
| `booked` | INTEGER | Boolean |

- **Deleting an activity** (`ActivityDao.deleteAndRecord`) records an `ACTIVITY` deletion. `deleteActivity` returns a
  `DeletedActivity` (the activity) and `restoreActivity` ("Undo") inserts it again as a **new** row with a new uuid
  (the recorded delete still uploads, so no uuid is reused).

| `checklist_items` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** |
| `list` | TEXT | `ChecklistKind` name: `BEFORE_TRIP`, `PACKING` (unknown → `BEFORE_TRIP`) |
| `text` | TEXT | trimmed, never blank |
| `checked` | INTEGER | Boolean |
| `due_date` | INTEGER, nullable | epoch day; only ever set on `BEFORE_TRIP` items (the DAO's update has `AND list = 'BEFORE_TRIP'`) |
| `created_at` | INTEGER | epoch millis when added (synced); lists are ordered `created_at, id` (creation time, then insertion order), the same on every device. `ChecklistDao.insertInOrder` (one transaction) gives every new item `max(now, latest created_at of the trip + 1)` and suggested items +1 ms each from there, so no two items of a trip tie on a device, even in the same millisecond or with a clock set back; "Undo" keeps the old value, so the item returns to its place |
| + `uuid`, `updated_at`, `pending_sync` | | sync columns as above |

- **Deleting a checklist item** (`ChecklistDao.deleteAndRecord`) records a `CHECKLIST` deletion; `restoreItem` inserts
  a new row with a new uuid (like activities).

| `documents` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `owner_id` | TEXT, indexed | the account that added it |
| `trip_uuid` | TEXT, indexed | the trip's **uuid**, **no foreign key**: a clean sign-out deletes the account's trips but keeps its documents, which belong to the trip again when it comes back from the cloud with the same uuid |
| `uuid` | TEXT, unique | random; also the stored file's name (`<uuid>.pdf`, `.jpg`, …) |
| `name` | TEXT | trimmed, never blank |
| `type` | TEXT | `DocumentType` name: `FLIGHT, LODGING, INSURANCE, TICKET, OTHER` (unknown → `OTHER`) |
| `detail` | TEXT, nullable | optional second line; blank → null |
| `file_name` | TEXT | name inside `filesDir/documents/` |
| `mime_type` | TEXT | `application/pdf` or `image/*` (badge "PDF" / "Photo") |
| `size_bytes` | INTEGER | ≤ 20 MB |
| `created_at` | INTEGER | epoch millis; the cards are ordered `created_at, id` |

Documents are **never synced** (no sync columns). `DocumentDao.observeForTrip` joins `trips` on uuid **and** owner.

| `travelers` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** |
| `name` | TEXT | trimmed, never blank, unique in the trip ignoring case, never "You" (checked in the UI) |
| `color` | TEXT | `TravelerColor` name (`ROSE, INDIGO, PURPLE, SKY, GREEN, GREEN_TEAL`; unknown → first), round-robin by the trip's traveler count |
| `created_at` | INTEGER | epoch millis; the list order (`created_at, id`), always after the trip's last traveler (`max(now, latest + 1)`) |
| + `uuid`, `updated_at`, `pending_sync` | | sync columns; `uuid` is also the traveler's **key** in expenses and settlements |

Trip **members are not rows**: a member's key is their account uid (the trip's `member_ids`); the signed-in member is
shown as "You", never renamed or removed here. Before shared trips the owner's key was the fixed text `"me"`
(`LEGACY_ME_KEY`, `data/trip/Travelers.kt`); the v14 migration and the cloud migration replaced it with the owner's uid. `TravelerDao.removeIfUnused`
(one transaction) returns `IN_USE` when an expense of the trip has `paid_by` = the uuid or the uuid in
`split_between` (`instr` on `,list,`) or a settlement uses it; otherwise records a `TRAVELER` deletion and deletes.

| `settlements` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** |
| `from_traveler` | TEXT | key of who paid (`me` or a traveler uuid) |
| `to_traveler` | TEXT | key of who received it; never equal to `from_traveler` |
| `amount_agorot` | INTEGER | > 0 |
| `date` | INTEGER | epoch day (today when marked as paid) |
| `created_at` | INTEGER | epoch millis; history is `date DESC, created_at DESC, id DESC` |
| + `uuid`, `updated_at`, `pending_sync` | | sync columns |

Deleting a settlement (`SettlementDao.deleteAndRecord`) records a `SETTLEMENT` deletion; the debt it paid reopens.

| `trip_members` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`**; unique with `uid` |
| `uid` | TEXT | the member's account uid |
| `name` | TEXT | their display name from the member document (never an email) |
| `joined_at` | INTEGER, nullable | epoch millis (server time) |
| `left` | INTEGER | Boolean: they left or were removed (their name still shows on old expenses) |

The cloud's member documents as of the last **full** sync (`RoomSyncLocalStore.replaceMembers` replaces a trip's rows
as a whole). Never pending, never uploaded, no tombstones. Who is a member *now* is `trips.member_ids`.

| `destination_photos` column | Type | Notes |
|---|---|---|
| `photo_key` | TEXT PK | `photoKey(destination, lat, lon)`: trimmed, lowercased, single-spaced text + `\|` + coordinates to 3 decimals (empty for free text) |
| `card_url`, `thumb_url` | TEXT, nullable | the 960 px and 250 px images; both null = nothing found |
| `file_page_url` | TEXT, nullable | the image's Commons description page (author, license), kept for a photo credit later |
| `checked_at` | INTEGER | epoch millis of the lookup |

Shared by every account (like `exchange_rates`: no personal data), never synced, kept on sign-out. Old keys (an edited
destination) simply stay; rows are tiny.

| `exchange_rates` column | Type | Notes |
|---|---|---|
| `currency_code` | TEXT PK | one cached rate per currency, shared by all accounts (rates are public) |
| `rate` | TEXT | shekels per 1 unit, exact `BigDecimal` text ("3.4408"), never a float |
| `rate_date` | INTEGER | epoch day of the rate's date **as returned by the service** |
| `fetched_at` | INTEGER | epoch millis of when this device fetched it (drives the 12-hour refresh) |

- **Money is whole agorot in `Long`** (100 agorot = ₪1), never `Float`/`Double`. Text input is parsed with
  `Money.parseAgorot` (digits, one `.` or `,`, ≤ 2 decimals, ≤ ₪1,000,000,000; `parsePositiveAgorot` when it must
  be > 0: budget, activity cost, settle amount); display goes through
  `Money.formatAgorot` / `shekels()` ("₪2,550", "₪12.50"). Doubles are used only for displayed percentages.
- `ExpenseCategory` = `FLIGHTS, HOTELS, FOOD, SHOPPING, TRANSPORT, ATTRACTIONS, OTHER`, same naming rules as `TripColor`.
  Icons/labels live in `ui/common/ExpenseCategoryUi.kt`, colors in `ExtendedColors.categories`.
- `ActivityType` = `ACTIVITY` (label "Attraction"), `DINING, SHOPPING, LODGING, TRANSPORT, OTHER` (Shopping: green,
  `ic_shopping_bag`, same hue as the Shopping category), same naming rules. Icons/labels in
  `ui/common/ActivityTypeUi.kt`, colors in `ExtendedColors.activityTypes` (one hue per type, see Design rules).
  The domain model is `TripActivity` (not `Activity`, which would clash with `android.app.Activity`); the form
  saves an `ActivityInput`, which repositories `normalized()` (trim, blank optionals → null).

- `TripEntity` is the Room row; `Trip` is the domain model used by the UI (mapped with `toTrip()`).
  The UI never sees Room types, so storage can change later (e.g. Firestore sync).
- `TripColor` = `SKY, PEACH, MINT, LAVENDER, ROSE, LEMON`. It is assigned automatically when a trip is created,
  round-robin by the number of the owner's existing trips (`TripColor.forIndex(dao.count(ownerId))`), so each
  account starts at the first color. Stored by name, so entries can be
  reordered or appended, but never renamed or removed without a migration (an unknown name falls back to the first
  color). Every stored enum (`TripColor`, `ExpenseCategory`, `ActivityType`, `ChecklistKind`, `DocumentType`,
  `TravelerColor`) has a `fromName(name)` companion with its fallback; Room `Converters` and `SyncDocuments` both use it.
  The actual colors live in the theme (`ExtendedColors.pastels`), with light and dark variants.
- **Every query is scoped to one owner** (`observeAll(ownerId)`, `observeById(id, ownerId)`, `count(ownerId)`,
  `update`/`setBudget` with `AND owner_id = :ownerId`), so an account never sees or changes another account's trips.
  Expense and activity queries join/sub-select `trips` on `owner_id`; `addExpense` / `addActivity` return null
  for a trip the owner doesn't own. `TripRepository` takes `ownerId` explicitly; ViewModels pass
  `authRepository.currentUser.value?.uid` (New Trip does nothing and Details shows nothing without a user).
- `observeAll` returns trips ordered by `start_date, id`. Grouping into home sections happens in Kotlin.
  Expenses are returned newest first (`date DESC, id DESC`).
- **Migrations:** v1 → v2 is an `AutoMigration` that adds `owner_id`. Trips created before v2 get `""` and are shown
  to no account. v2 → v3 is an `AutoMigration` that adds `trips.budget_agorot` (null) and creates `expenses`;
  existing trips are kept. v3 → v4 is an `AutoMigration` that adds `trips.currency_code` (null) and creates
  `exchange_rates`; existing trips and expenses are kept. v4 → v5 is an `AutoMigration` that creates `activities`;
  all existing data is kept. v5 → v6 is an `AutoMigration` that adds the four nullable foreign-amount columns to
  `expenses`, so every existing expense stays a shekel expense. v6 → v7 is the **manual** `MIGRATION_6_7`
(`Migrations.kt`, SQL copied from `7.json`): adds the three sync columns, gives every existing row its own
`UUID.randomUUID()` (SQLite can't), sets `updated_at` to the migration time and `pending_sync = 1` (so the first sync
uploads everything), then creates the unique indexes and `deletions` (`MigrationTest` in androidTest). v7 → v8,
v8 → v9 and v9 → v10 are `AutoMigration`s from the removed Map tab (`places`, `activities.place_id`,
`places.auto_matched`, `activities.place_lookup`, `places.added_on_map`; `MigrationTest.migrate7To8…/8To9…/9To10…`).
v10 → v11 is the **manual** `MIGRATION_10_11` (SQL from `11.json`): an activity linked to a place whose `location` is
null or blank gets the place's name as its location, with `updated_at` = the migration time and `pending_sync = 1` (so
the text uploads; activities that already had a location are untouched); then `activities` is rebuilt without
`place_id`/`place_lookup` (SQLite can't drop an FK column), `places` is dropped and `PLACE` rows are removed from
`deletions`. Every trip, expense and activity is kept (`MigrationTest.migrate10To11…`). v11 → v12 is an
`AutoMigration` that creates `checklist_items` and `documents` empty; all existing data is kept
(`MigrationTest.migrate11To12…`). v12 → v13 is an `AutoMigration` (expense splitting) that adds
`expenses.paid_by` / `split_between` with the default `'me'` (every existing expense becomes "paid by me, not
shared", so every trip keeps exactly its numbers; rows keep their `updated_at`/`pending_sync`, nothing re-uploads)
and creates `travelers` and `settlements` empty (`MigrationTest.migrate12To13…`). v13 → v14 is the **manual**
`MIGRATION_13_14` (shared trips, SQL from `14.json`): `paid_by`, the `me` token in `split_between` and settlement keys
`me` become the trip's `owner_id` (trips with `owner_id = ''` are left as they are); `trips` gets `cloud_owner_id` =
`member_ids` = `owner_id`, `budget_updated_at` = `updated_at`, `budget_pending` = `pending_sync`; unique indexes
become `trips(owner_id, uuid)` / children `(trip_id, uuid)`; `deletions` is rebuilt with the PK `(owner_id, uuid)`.
`updated_at`/`pending_sync` of existing rows are untouched (the cloud migration rewrites the cloud copy the same way),
and the owner stays first in the list order, so every share, balance and transfer keeps its exact value
(`MigrationTest.migrate13To14…`, `SplitTest.theOwnersUid_givesExactlyTheSameNumbersAsTheOldMeKey`). Room turns on
`PRAGMA foreign_keys` itself (the cascade is covered in `TripDaoTest`). v14 → v15 is an `AutoMigration` (invites and
joining) that creates `trip_members` empty; all existing data is kept (`MigrationTest.migrate14To15…`). v15 → v16 is
an `AutoMigration` (destination suggestions) that adds `trips.country_code` / `latitude` / `longitude`, null for every
existing trip; nothing re-uploads (`MigrationTest.migrate15To16…`). v16 → v17 is an `AutoMigration` (destination
photos) that creates `destination_photos` empty; all data is kept (`MigrationTest.migrate16To17…`).
- **Never seed sample trips into the database.** Sample data belongs only in previews and tests.
- **Changing the schema:** bump `version`, add a `Migration` or `AutoMigration`, build, and commit the new JSON in
  `app/schemas/`. Do not use `fallbackToDestructiveMigration` — users would lose their trips.
- **Deleting a trip** (`TripRepository.deleteTrip` → `TripDao.deleteAndRecord`, one transaction): records a `TRIP`
  row in `deletions`, deletes the trip (the FK cascade removes its expenses, activities, checklist items, travelers
  and settlements) and returns the trip's uuid; then `DocumentRepository.deleteForTrip(tripUuid, ownerId)` deletes its documents and their
  files (no FK). Not `removeOrphans`: right after a sign-in, before the first full sync brings the trips back, it would
  also wipe the documents of trips that aren't back yet. If the app dies between the two steps, the next full sync's
  orphan cleanup removes the rest. `DeleteTripViewModel` runs both steps (see "Deleting a trip").

