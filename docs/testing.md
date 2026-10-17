# Test coverage

What each test suite covers.

- Keep pure logic in plain Kotlin and unit-test it: expense splitting (`SplitTest`: uneven split with the extra agorot
  in list order, an expense I'm not part of, not shared, paid for someone else, three travelers with crossing debts,
  independent pairs and zero-sum groups (minimum transfers), partial settlement, deleted settlement, everything
  settled, settlements never change my share, single traveler, unknown/removed key, empty split, a random property
  test that shares, category totals and balances always add up, `isTravelerUsed`, name rules, avatar letters;
  `SplitViewModelTest`: add/rename/duplicate/blank/"You", double taps, remove blocked by payer/split/settlement or
  used meanwhile, settle full/partial/single debt/invalid amounts, history delete with confirmation, another account's
  trip; `ExpenseFormTest`: only me, defaults with travelers, not shared + payer change, empty split, editing keeps the
  split, removed traveler; `TripDetailsViewModelTest`: my share in the summary and chart, an existing trip keeps its
  numbers; `TripPdfContentTest`/`ShareTripViewModelTest`/`AssistantViewModelTest`: my share), validators, `groupTrips`, `tripStatus`, `TripFormatting`,
  `PickerDates`, `Money`, `BudgetCalculations`, `AppLocaleTest` (formatters stay English under an RTL default locale), `Itinerary` (`ItineraryTest`: one-day, past and current trips,
  untimed activities, activities outside the dates),
  `CurrencyConversion` (incl. digit-by-digit typing tests), activity type and category color contrast (`TintedColorsTest`), `ExpenseCurrency` (no-decimal currency, no rate, editing with/without changing the amount, switching currency), the expense date limit (`ExpenseFormTest`: default after the trip ended, date after the trip on save, editing an expense already dated after the trip), deleting an activity (`ActivityFormTest`: delete offers Undo, every type saves without a location; `TripDetailsViewModelTest`: Undo restores the activity, only this trip's message, dismiss keeps the delete), `CurrencyPicker`, `FrankfurterJson`, `isStale`, the assistant (`AssistantJsonTest`: malformed answer, unknown type,
  bad times, costs, invalid date; `AssistantPromptTest`; `AssistantContextTest`: context, chips, history (the last 20 turns, never starting with an
  answer); `AssistantViewModelTest`: a long chat sends only the last turns, a message over 1000 characters is not sent;
  `SuggestionsTest`: only some checked, day outside the trip), the shared PDF (`TripPdfContentTest`: costs on/off, no
budget, over/under/on budget, no expenses, no activities, only activities outside the dates, a day with nothing
planned, untimed activities; `PdfFileNameTest`: special characters, emoji, Hebrew, fallback, long names;
`PdfPaginationTest`; `ShareTripViewModelTest`: double tap, failure + try again, costs off, another account's trip),
the sync (`SyncMergeTest`: edited on two devices,
deleted on one and edited on another, first upload, children of a deleted trip, checklist items saved in creation
order, travelers/settlements of a deleted trip, save order with travelers, a trip and its budget merged separately;
`SyncDocumentsTest`: field formats, the trip's owner/members and no budget, the budget and Packing documents, a legacy
trip document and its budget, tombstones, fallbacks, payer/split, an expense document from before splitting,
travelers, settlements; `LegacyMigrationTest`: "me" → uid, owner/members, Packing and budget, checklist tombstones,
the copy plan (empty new layout, repeated run, interrupted copy, newer version never overwritten, trip deleted since,
legacy trip tombstone, child tombstone); `SyncManagerTest`: second account on the same device, offline in the middle
of a sync, reconnect, quick sync, edits during an upload, sign-out paths, the legacy migration (same data, deleted only
after the check, interrupted copy keeps the phone's trips and finishes next time, interrupted after the marker, a newer
edit from an updated phone kept, a quick sync migrates first, each account its own, an account without legacy data),
personal budget/Packing invisible to another member, an upload never changing the members, refresh, checklist items,
travelers + settlements + shared expenses up and down, a deleted trip tombstoning its travelers and settlements, the
documents cleanup (only after a successful full sync); `SyncDocumentsTest` also reads an older activity document with
`placeId`/`placeLookup` and covers checklist fields; `SplitTest.theOwnersUid_givesExactlyTheSameNumbersAsTheOldMeKey`:
a 500-case property test that shares, my share, balances and transfers are identical with "me" and with the uid;
`RefreshViewModelTest`), shared trips stage 2 (`SyncAccessTest`: removed with pending edits, left elsewhere, owner
deleted the trip while I had a newer edit, a foreign trip gone from the cloud, broken rules denying every trip read
remove nothing, my own trip without access fails the sync and keeps everything, a failed list query removes nothing,
a failed personal-data delete keeps everything, my new trip still uploads, a quick sync asks for the full sync that
decides, leaving, offline/owner can't leave, removing a member, member documents downloaded; `TripAccessTest`;
`InviteCodeTest`: alphabet, SecureRandom, normalize/paste, display, expiry, `memberName`; `InviteViewModelTest`:
create, reopen shows the same code, used/expired → new code, cancel, offline, full, non-owner, remove with
confirmation and double tap; `JoinTripViewModelTest`: lookup only at 8 characters, one invalid message, already a
member (also locally), full, failure, double tap, success refreshes, never the email; `LeaveTripViewModelTest`;
`MembersStateTest`: order, owner, full, members as people, former members; `FakeInviteRepository`), the security rules (`rules-tests/rules.test.js` on the emulator, see "Security rules"), the Prep tab (`PrepProgressTest`: no items, all done, 12 of 20, rounding down, overdue / due
today / checked, toggling, insertion order, suggested items; `DocumentFilesTest`: PDF/images, unsupported types, the
20 MB limit, unknown size, badge, extension, default name; `PrepViewModelTest`: toggling, add, suggestions once, edit
text, due dates, delete + Undo, another account's trip, too large / unsupported / unreadable / too large while copying,
double tap, edit, delete with confirmation, view a missing file), text limits (`TextLimitsTest`: cutting, never half an emoji, `ActivityInput.normalized`, the upload cuts every
limited field; `ExpenseFormTest`: the name field stops at the limit), HTTP (`OkHttpGetTest` on a local MockWebServer: body, User-Agent/Accept, error status, connection failure, bad URL,
cancelling ends the call at once; `FrankfurterRatesApiTest`: the request URLs, with `RecordingHttpGet`), `ActivityTypeTest`, `GoogleMapsTest` (spaces, comma, Hebrew, special
characters, trimming, decodes back; the activity URL with/without the destination), destination suggestions and flags (`PhotonJsonTest`:
types kept/dropped, labels, repeats only when name+region+country match; `PhotonPlaceSearchTest`: the encoded URL and
User-Agent; `HebrewPlacesTest`: matching rules and every
table entry, no Hebrew name in two entries; `DestinationSuggestionsTest`; `EnglishPlacesTest`; `TripFormPlaceTest` with `FakePlaceSearch`:
instant rows, merging, the loading bar, pick, free text, edit after pick, offline, failing service, Hebrew, debounce, cancelled lookup, edit trip, currency
preselection; `FlagsTest`: every offered currency has a flag or is left out on purpose; `TripPlaceSyncTest` +
`SyncPathsTest`: the place round-trips; androidTest `TripPlaceRoomTest`), `AccountViewModelTest` (menu state, sign out, warning, sign out anyway), deleting a trip (`DeleteTripViewModelTest`:
confirm, cancel, double tap, the trip's document files removed and other trips' kept, another account's trip, no
user, failure, confirmed on the trip screen and deleted on Home; `HomeViewModelTest`: the next upcoming trip becomes
the Next trip, the last trip → empty; `SyncManagerTest`: deleted offline, uploaded with its children's tombstones when
the connection returns), `firstName`/`initials`/`greetingName`, `TripColor`, and every
  ViewModel (with `FakeTripRepository`, `FakeExpenseRepository`, `FakeActivityRepository`,
  `FakeTravelerRepository` + `FakeSettlementRepository` (`gate`, `added`; removal checks the fake expenses/settlements),
  `FakeCurrencyRepository`, `FakeChecklistRepository`, `FakeDocumentRepository` + `FakeDocumentFileStore` (`picked`,
  `actualSizes`, `failCopy`), `FakeAssistantRepository` (scripted responses, recorded requests, `gate`), `FakeTripRepository` (`failDelete`, `deleteGate`, `deletes`, trip uuid = `uuidOf(id)`), `FakeTripPdfExporter` (`exports`,
  `fail`, `gate`), `FakeSyncController` (`refreshResult`, `refreshGate`), `FakeCloudStore`,
  `FakeSyncLocalStore`, `FakeSyncFlags`, `FakeNetworkMonitor` and `FakeAuthRepository` in
  `app/src/test`; set `nextSignInError`, `FakeCurrencyRepository.offline` etc. to simulate backend failures). Most
  ViewModel tests sign in as `AuthUser("u1", …)`; `ME` (`app/src/test/.../TestUsers.kt`) is that uid, their traveler key.
  Expenses always pass `paidBy`/`splitBetween` (no default key).
  `TripDaoTest` in `app/src/androidTest` covers Room (owner scoping, updates, currency, rate cache, expenses,
  activities, cascades, sync columns, recorded deletes, restore as a new pending row, checklist order/scoping/due
  dates/restore, documents scoped by owner, kept after sign-out and shown again when the trip returns, orphans and the
  file sweep, suggested items keeping their order with a fixed clock (same millisecond, delete + Undo, clock set
  back), checklist items from the cloud stored in creation order, payer/split columns, travelers (order, colors, owner,
  rename), removal blocked by payer/split/settlement and recorded otherwise, settlements (order, validation, recorded
  deletes), travelers/settlements/splits from the cloud, a deleted trip leaving no expenses/activities/checklist
  items/travelers/settlements/documents/files behind and only its `TRIP` tombstone pending); `MigrationTest` covers v6 → v7, v7 → v8, v8 → v9, v9 → v10, v10 → v11 (place names copied into empty
  locations as pending edits, own locations untouched, places and `PLACE` deletions gone, all trips/expenses/activities
  kept, cascade still works), v11 → v12 (all data kept, new tables empty, checklist cascade, documents without FK) and
  v14 → v15 (all data kept, `trip_members` empty, unique per trip and uid, cascade), v12 → v13 (all data kept with the same amounts, existing expenses `paid_by`/`split_between` = `me` and not re-uploaded,
  travelers/settlements empty, cascade) and v13 → v14 ("me" → the owner's uid in payer, split lists and settlements,
  ownerless trips untouched, members and budget sync state, nothing re-uploaded, the same uuid for two accounts but not
  twice for one, cascade). `TripDaoTest` also covers a new trip's owner/members, the budget pending on its own, a cloud
  trip keeping my budget, Packing deletes recorded as `PACKING`, and two accounts holding the same shared trip.
  `SharedTripRoomTest` (androidTest) covers member documents (replaced as a whole, per account, cascade),
  `tripOwners` and `removeTrips` (items, pending changes and tombstones of that trip and account only).
