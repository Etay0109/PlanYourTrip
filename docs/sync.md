# Cloud sync, shared trips and invites

**Room is the only source for the screens**; the app works offline exactly as before. Firestore holds the **shared
trips** (every member's phone syncs the same trip) and each member's personal data, and carries changes between
devices. The exchange-rate cache, the assistant chat and the trip documents (Prep tab) are never synced.

### Shared trips (stages 1 and 2 of 3 done)
- A trip has an **owner** (who created it) and **members** (uids, ≤ 10, owner first, then join order). Others join
  with the owner's invite code (see "Invites and joining").
- **Shared** with all members: the trip's details (destination, dates, currency, color), expenses, travelers,
  settlements, activities and the "Before the trip" checklist. **Personal** (only that member, not even the owner):
  their budget (`SyncKind.BUDGET`) and their Packing list (`SyncKind.PACKING`); documents stay on the device as before.
- **Traveler keys are uids**: an expense's payer/split and a settlement's sides are member uids or name-only traveler
  uuids. The **list order** (`travelerOrder(me, memberIds, travelers)`) is the members in the cloud's order, then the
  travelers: the same on every device, so the extra agorot of an uneven split go to the same people everywhere. With
  one member it is "You" first, then the travelers, exactly as before. Display order still puts "You" first.
- `Trip.ownerId` / `memberIds` (Room `cloud_owner_id` / `member_ids`) come from the cloud; an upload never changes them.
- Room keeps one copy per signed-in account (`trips(owner_id, uuid)` unique), so two accounts on one phone can both
  hold the same shared trip.

### Firestore layout (field names in `SyncDocuments` (its private `Field` constants), must match `firestore.rules`)
```
trips/{tripUuid}                               destination, startDate, endDate, currencyCode, color,
                                               countryCode, latitude, longitude (null = free text; optional:
                                               documents written before suggestions lack them),
                                               ownerId, memberIds (array of uids)          — no budget here
   (trip tombstone)                            ownerId, memberIds, deleted: true, updatedAt (members still read it)
trips/{tripUuid}/expenses/{uuid}               name, amountAgorot, category, date,
                                               originalAmount, originalCurrency, exchangeRate, rateDate,
                                               paidBy (string key), splitBetween (array of keys)
trips/{tripUuid}/activities/{uuid}             date, startTime, title, note, type, location, estimatedCostAgorot, booked
trips/{tripUuid}/checklist/{uuid}              list ("BEFORE_TRIP" only), text, checked, dueDate, createdAt
trips/{tripUuid}/travelers/{uuid}              name, color, createdAt
trips/{tripUuid}/settlements/{uuid}            fromTraveler, toTraveler, amountAgorot, date, createdAt
trips/{tripUuid}/memberData/{uid}              budgetAgorot                       (personal: only that uid)
trips/{tripUuid}/memberData/{uid}/packing/{uuid}  list ("PACKING"), text, checked, dueDate, createdAt   (personal)
every live document also has                   updatedAt (epoch millis, int), deleted: false
trips/{tripUuid}/members/{uid}                 name, joinedAt (server timestamp), left, inviteCode (null for the owner)
trips/{tripUuid}/private/invite                code (the owner's current invite code or null; owner only)
invites/{code}                                 tripId, inviterName, destination, startDate, endDate,
                                               createdAt (server timestamp), status (open|used|cancelled), usedBy
users/{uid}                                    sharedTripsMigrated: true   (the legacy copy's migration marker)
users/{uid}/trips/**                           the legacy copy (before shared trips): read until migrated, then deleted
```
- **Ids:** a document's id is the item's uuid, except the budget: `memberData/{uid}` is named after the member, while a
  `BUDGET` item always has its trip's uuid. `SyncDocuments.fromDocument(kind, documentId, tripUuid, …)` applies that
  rule (using the document id would key every budget by the member's uid, and the legacy migration could never
  finish). Paths come from `SyncPaths.of`.
- Money is whole agorot (int), dates ISO strings ("2026-11-12"), `startTime` "HH:mm", rates and foreign amounts exact
  decimal strings (`toPlainString()`), enums by name (unknown → same fallback as Room). Every key is written, null
  when empty. A document that can't be read is skipped and logged, never fatal.
- **Deletes are tombstones:** a child document is replaced (no merge) by `{deleted: true, updatedAt}`. A trip is
  deleted with an `update` that removes its data fields and keeps `ownerId`/`memberIds` (so members can still read
  the tombstone); the same write tombstones its shared children and the writer's own budget and Packing items.
- **Writing a trip** (`FirestoreCloudStore.write`): a trip not yet in the cloud (checked with a `get`, allowed for a
  missing document) is created with `ownerId` = the writer and `memberIds = [writer]`; an existing trip is written
  with `set(merge)` of the data fields only — never `ownerId`/`memberIds`, so a phone with a stale member list can't
  undo a membership change. Writes are committed **one trip per batch** (≤ 450 writes): every item's rule looks up its
  trip, and Firestore allows only 20 different document lookups per batch (repeated lookups of one trip count once;
  `rules.test.js` checks 450).
- **Reading:** `fetchAll` = the query `trips where memberIds array-contains <uid>` (the only list the rules allow), then
  each live trip's 5 shared subcollections + `memberData/{uid}` + its `packing`. `fetch` reads trips first; children of
  a trip that doesn't exist are reported missing without reading them.

### Legacy migration (`LegacyMigration.kt`, pure + `SyncManager.migrateLegacy`)
Before shared trips the cloud copy was `users/{uid}/trips/…` (keys `"me"`, budget in the trip document, both
checklists in one subcollection). Once per account (local flag `SyncFlags.isSharedTripsMigrated`, prefs "sync",
key `shared_trips_migrated_<uid>`), **before any other sync work** (full or quick, so a trip not yet in the new layout
can never make the merge delete it here):
1. `fetchLegacy` (paged queries `orderBy(documentId).limit(450)`), `convertLegacy(items, uid)`: `"me"` → uid in payer,
   split and settlements; each trip gets owner = only member = uid; its budget becomes a `BUDGET` item with the trip's
   `updatedAt`; Packing items → `PACKING`; a legacy checklist tombstone → a tombstone of both kinds.
2. `planLegacyCopy(legacy, fetchAll)`: copies only what the new layout lacks or has **older** (never overwrites a newer
   edit from a phone that migrated first), trips first; children of a trip that ends up deleted are skipped. A
   **budget** is copied only when the new layout has no budget document of that trip at all (a legacy budget carries
   the legacy trip's `updatedAt`, so it must never replace an existing one).
3. `fetchAll` again; nothing left to copy (`isLegacyCopyComplete`), or the sync fails as a **sync problem** (logged with
   the kinds still missing) and the next one starts over.
4. `markLegacyMigrated` (sets the marker: old app versions can no longer read the legacy copy), `deleteLegacy` (every
   legacy document, old `places` included, children first), then the local flag.
Every step can be repeated; old cloud data is deleted only after step 3. An account without legacy data just gets the
marker. The old one-time removal of place documents (`deleteLegacyPlaces`, prefs key `places_cleanup_<uid>`) is part
of `deleteLegacy` now.

### Write path
- `RoomTripRepository` / `RoomExpenseRepository` / `RoomActivityRepository` (and the checklist, traveler and settlement
  repositories) take the `Clock` and a `LocalChangeListener` (the `SyncManager`). Every insert/update sets
  `updated_at = clock.millis()` and `pending_sync = 1`; `setBudget` sets `budget_updated_at` / `budget_pending`
  instead (the personal budget never conflicts with shared edits); deletes go through the DAOs' `@Transaction
  deleteAndRecord` (insert a `deletions` row, then delete; a Packing item records `PACKING`). Then `onLocalChange()`.

### SyncManager (app-scoped, `Dispatchers.IO`)
- **Full sync** (legacy migration if needed, download everything with `fetchAll`, `planMerge`, apply in one Room
  transaction, upload trips first, mark synced): for the signed-in user (`collectLatest` on the uid, so switching
  accounts cancels it) **every time the app comes to the foreground** (`foreground`, the process lifecycle from
  `processInForeground` in `ProcessForeground.kt`: an activity started; rotations and quick activity changes don't
  count) — so a sign-in in the app syncs at once, and a process Android starts in the background for boot, an alarm
  or WorkManager (trip reminders) does no full sync until the app is opened; whenever `NetworkMonitor.isOnline` goes
  back to true **while in the foreground** (in the background the next foreground syncs); and on **pull to refresh**
  (`SyncController.refresh()`, false when offline or failed). Tests leave `foreground` always true.
- **Quick sync** ~1 s after a local change (conflated channel): only the pending items and their own cloud
  documents (`fetch`), the same `planMerge`, so a newer edit from another device is never overwritten.
- One sync at a time (`Mutex`); every query uses the current uid only. No network → no cloud call, status OFFLINE.
  No realtime listener: other devices see changes at their next sign-in, return to the app, reconnect or pull to
  refresh. Reminders are scheduled from Room as before; a trip date another member changed reaches this phone (and its
  reminder) at the next app open, not at a background wake-up.
- Uploads go in chunks of 100 items, sorted by `SyncKind` (trips first); each chunk is marked synced (`markSynced`:
  `pending_sync = 0` / `budget_pending = 0` / drop the tombstone **only if the time is unchanged**, so an edit made
  during the upload stays pending). A failure anywhere throws `CloudUnavailableException` and leaves the rest pending.
- Firestore uses a **memory cache only** (Room is the one copy on the device, so sign-out really removes the data);
  reads use `Source.SERVER`; each cloud call times out after 20 s.
- **Documents of deleted trips:** after every **successful full sync** `SyncManager` calls
  `TripCleanup.afterFullSync(uid)` = `RoomDocumentRepository.removeOrphans`. Quick syncs, failed or offline syncs and
  sign-out never run it. At app start `AppContainer.cleanUpDocumentFiles()` sweeps files no row uses.

### Lost trips and members (`TripAccess.kt` pure, `SyncManager`, `SyncAccessTest`, `TripAccessTest`)
A trip is **lost** when I left, was removed, or its owner deleted it. Only a **full** sync decides, after the
`memberIds array-contains me` list query **succeeded** (a failed query fails the whole sync first):
- `tripsToCheck`: local trips the list didn't return, and trips it returned as tombstones that I don't own. They are
  read directly (`CloudStore.tripAccess`: `LIVE`, `DELETED`, `MISSING`, or `NO_ACCESS` = the read was denied).
- `decideAccess`: a trip I **don't** own is lost only when the list didn't return it alive **and** the direct read was
  denied, missing or deleted. A trip I **own** (local `cloud_owner_id` = me, or blank) is **never** lost; its denied
  read is a sync error (`CloudUnavailableException`: nothing is deleted, retried later); `MISSING` keeps the old
  behavior (a pending new trip uploads).
- For each lost trip, in this order: `CloudStore.deletePersonal` (my budget + Packing; a failure fails the sync before
  anything local changes), then `SyncLocalStore.removeTrips` (one transaction: the trip, its items **with pending
  changes**, its tombstones), then `TripCleanup.forgetTrip` (= `DocumentRepository.deleteForTrip`). Lost trips are
  left out of both sides before `planMerge`, so a member's device never revives a trip the owner deleted.
- A **quick sync** never removes anything: pending items of a trip I don't own whose direct read isn't `LIVE` stay
  pending, are left out, and a full sync is requested (`fullSyncRequests` channel).
- After every successful full sync: `CloudStore.fetchMembers` for every live trip → `SyncLocalStore.replaceMembers`.
- `SyncController.leaveTrip(tripUuid)` (online only, not for the owner, under the sync lock): `CloudStore.leaveTrip`
  (one batch: `arrayRemove(me)`, `members/me.left = true`, delete `memberData/me` and the Packing items; items that
  don't fit are deleted in earlier batches), then the same local removal. `SyncController.removeMember(tripUuid, uid)`
  (owner): `arrayRemove(uid)` + `members/{uid}.left = true` in one batch, then a full sync.

### Merge rules (`planMerge`, pure, `SyncMergeTest`)
Items are matched by **kind and uuid** (a trip's `BUDGET` item has the trip's uuid).

| local | cloud | result |
|---|---|---|
| live, pending | missing | upload (created here / first upload of existing data) |
| live, not pending | missing | delete here (removed from the cloud) |
| missing | live | save here (trips before their children) |
| live | live | newer `updatedAt` wins, tie → cloud |
| live | tombstone | newer of edit and delete wins, tie → delete |
| tombstone | live | newer of delete and edit wins, tie → the cloud's edit (the item comes back) |
| tombstone | missing / tombstone | drop the local tombstone |

Children of a trip that ends up deleted are deleted here and never saved or uploaded. Cloud versions are saved in
`SyncKind` order (`TRIP, TRAVELER, EXPENSE, ACTIVITY, CHECKLIST, SETTLEMENT, BUDGET, PACKING`, stored by name so it may
be reordered), trips first; checklist items and travelers in `createdAt` order, ties by uuid. `RoomSyncLocalStore.apply`
re-checks the time so a cloud version never replaces a newer local change made while the sync ran; saving a trip keeps
the local copy's budget columns, and a membership change (which doesn't change `updatedAt`) is picked up as a tie.
Timestamps are device clocks (no server time), so a device with a very wrong clock can win conflicts it shouldn't.

### Status and sign out
- Every full sync's result is recorded per account (`SyncFlags.recordFullSync`: succeeded + time of the last success,
  prefs `full_sync_ok_<uid>` / `full_sync_success_at_<uid>`; the injected `Clock`). A failure is a **connection**
  failure (`CloudUnavailableException.isProblem = false`: offline, timeout, Firestore `UNAVAILABLE` /
  `DEADLINE_EXCEEDED`) or a **sync problem** (`isProblem = true`: anything else, e.g. denied by the rules, a legacy
  copy that can't finish, my own trip unreadable). Every failure is logged (`adb logcat -s CloudSync`), timeouts too.
- `SyncController.status`: `SYNCING` while a sync runs; offline: `SAVED` only if nothing is pending and the last full
  sync succeeded, else `OFFLINE`; `PROBLEM` ("Sync problem – tap to retry", red, with "Last synced 9 Oct, 16:07" or
  "Not synced on this device yet" from `SyncController.lastSyncedAt`; tapping runs `refresh()`) while the last full
  sync failed with a problem, even if a quick sync worked since; `SAVED` **only** when nothing is pending **and** the
  last full sync succeeded; `SYNCING` before the account's first full sync; else `OFFLINE`.
- `signOut(force = false)`: first a full sync (migration, upload of pending changes; skipped offline, then the last
  recorded result counts; it never runs the documents cleanup). The account's rows and tombstones are deleted from
  the device (`NonCancellable`, under the sync lock) **only when the data is safely in the cloud**: the legacy migration
  finished for the account, the last full sync succeeded and nothing is pending. Otherwise nothing changes and it
  returns `NEEDS_CONFIRMATION` (changes pending: "Changes not uploaded yet") or `NOT_CONFIRMED` (nothing pending, but
  not confirmed: "Trips not confirmed in the cloud"); both dialogs say the data stays on this device, hidden from other
  accounts. **Documents are kept** on every path (never uploaded): hidden by `owner_id`, shown again when the same
  account signs in. `signOut(force = true)` ("Sign out anyway") signs out and **keeps** the data on the device, hidden
  by owner scoping; it syncs at that account's next sign-in here. A second account on the same device only ever syncs
  its own rows.
- Testing: `FakeCloudStore` stores **Firestore documents under Firestore's paths** (`SyncPaths.of`, written with
  `toDocument`, read back with `fromDocument` and the document's own id, budgets at `memberData/{uid}`, a deleted
  trip's children not returned by `fetchAll`), so path or id mistakes fail the sync tests (`SyncPathsTest`: every
  kind and its tombstone round-trip through its document); `unreadableKinds` makes documents of a kind unreadable (the
  2026-10-09 bug). `TwoAccountsOneDeviceTest` (two accounts on one phone, joining, the exact 2026-10-09 case),
  `SyncStatusAndSignOutTest` (truthful status, safe sign-out), `LegacyBudgetTest`. The fake also has (shared trips with
  members, personal items per uid, the legacy copy + marker; `offline`,
  `failAfterWrites`, `failLegacyDelete`, `onWrite`, `addMember`, `membersOf`, `memberDocs`, `removeFromTrip`,
  `personalOf`, `failList`, `denyTripReads`, `failDeletePersonal`, `deletedPersonal`; a `fetch` of a trip I'm not a
  member of fails like Firestore; `put(uid, …)`/`docsOf(uid)` = written
  by / visible to that uid, a budget under `"budget:<trip uuid>"`), `FakeSyncLocalStore` (same guards as Room),
  `FakeSyncFlags`, `FakeNetworkMonitor`. **Never test against the real Firestore.** `SyncManagerTest` runs the manager
  in `backgroundScope` and advances virtual time (`advanceUntilIdle` stops when only background work is left).

### Security rules (`firestore.rules`, tests in `rules-tests/rules.test.js`)
- **Trips:** `get` for members (a missing id reads as "no document"); `list` only with `memberIds array-contains <me>`;
  `create` only with `ownerId == me` and `memberIds == [me]`; data updates by members with `ownerId` and `memberIds`
  unchanged; tombstone, hard delete and reviving a deleted trip: owner only; member list (only `memberIds` changing,
  1–10, no duplicates): the owner removes others and stays, a member removes only themselves **and deletes their
  budget in the same write** (`!existsAfter(memberData/me)`, member document `left` if it exists); **only joining adds
  anyone** (below); the trip document may never hold `budgetAgorot`.
- **Joining** (`validJoin`, checked by all three documents of the batch): the trip update only appends my own uid
  (`after.memberIds == before.memberIds.concat([me])`, ≤ 10, trip live, I wasn't a member); my member document names an
  invite that was open, unexpired and **for this trip** before the write and is `used` by me after it; the invite's
  update is only `status` open → used + `usedBy` = me. Nobody can add another uid; re-using, guessing or a code of
  another trip fails; rejoining after removal needs a new code.
- **Invites `invites/{code}`**: `get` only by the exact code while `status == 'open'`, `request.time < createdAt + 7
  days` and the trip is live (missing, used, cancelled, expired look the same); **never listed**, never deleted;
  created only by the trip's owner for a live trip with < 10 members, with exactly the invite fields, the trip's own
  destination and dates, `createdAt == request.time`, open, `usedBy` null, `inviterName` = the owner's member document
  name; an existing code is an update and is denied (the app tries another, up to 5); cancel: owner only, open →
  cancelled. Code format `^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$`.
- **Members `members/{uid}`**: read by members; written by that uid only: the owner (code null) or a joiner
  (`validJoin`); exactly `name` (1–100), `joinedAt == request.time`, `left == false`, `inviteCode` (an email or any
  other key is denied); later only `left` → true, by them or the owner, in the write that takes them out of
  `memberIds`. Never deleted. **`private/invite`**: the owner only, `{code: null | code}`.
- **Lookup limits:** Firestore allows 10 `get`/`exists` per document and 20 per batch (one document counts once). The
  batches use: create invite 2 documents, join 3, leave 3 (packing deletes need none; 446 packing items are tested in
  one batch), remove 1. The emulator enforces the limit (checked once: the join batch fails with 12 extra lookups).
- **Shared children:** read by members; written by a member *after* the write (`getAfter`, so a new trip and its items
  fit in one batch and leaving + writing in one batch fails); live items only while the trip is live, tombstones
  always; hard delete: owner only.
- **memberData/{uid}** and **…/packing**: read and **delete** by that uid only, even after they left or were removed (so
  they can clean up their own data); create/update by that uid while a member. Nobody else, not even the owner.
- **Field validation** as before (`hasOnly` key lists, ISO dates, end ≥ start, amount > 0, budget/cost null or > 0,
  decimal strings, "HH:mm", texts not empty, `createdAt`/`updatedAt` int, settlement keys different, **text
  lengths** = `TextLimits.kt`: destination, expense name, activity title ≤ 200, activity location ≤ 300, note ≤ 2000,
  checklist/packing text ≤ 500, traveler name 1–100, enum names and currency codes ≤ 40) or a tombstone; expense `paidBy`/`splitBetween` are now **required**; shared checklist `list ==
  'BEFORE_TRIP'`, packing `list == 'PACKING'`; trip `memberIds` 1–10 without duplicates and containing the owner;
  trip `validPlace`: `countryCode` missing/null or `^[A-Z]{2}$`, `latitude`/`longitude` both missing/null or both
  numbers in −90..90 / −180..180 (a trip without the keys stays valid; the tombstone rule is unchanged).
- **Legacy copy** `users/{uid}/trips/**`: no create/update; delete by its owner; read by its owner until
  `users/{uid}.sharedTripsMigrated == true`, after that only the list query with `limit == 450` (the app's migration
  query) works, so old app versions are cut off while leftovers can still be deleted. The marker can be set, never
  unset or deleted.
- Change the rules together with `SyncDocuments` and `rules.test.js`, run `npm test` in `rules-tests/`, then publish.

### Known limits
- **Old app versions** (before shared trips): once the new rules are published they can't write at all (their edits
  stay pending on that phone, status "Offline"). Until that account is migrated they still download; after the marker
  their download is denied, so they keep showing their last data and never delete anything. Updating the phone runs
  the Room migration and uploads its pending edits to the new layout (newer wins). Order: publish the rules, then update
  every phone. Until the rules are published, the new app's migration fails safely (the new paths are denied) and
  retries; nothing is deleted.
- **Rate limits:** the rules can't rate-limit requests, so guessing invite codes and using up the free read quota are
  held back only by the size of the code space (8.5·10¹¹), the 7-day single-use codes and App Check. **Enforce App
  Check for Firestore** (Firebase console → App Check → APIs → Cloud Firestore → Enforce, once the metrics show the
  requests as verified).
- **Trust model:** every member can edit and delete the trip's shared expenses, plan, travelers, settlements and
  "Before the trip" checklist. Share a trip only with people you trust. The owner can remove a member but can't undo
  what they changed (no history).
- A removed member's phone keeps its last copy until its next full sync (no realtime listener); from the moment of
  removal the rules deny every read and write.
- Leaving deletes my Packing list in the same batch, but the rules can't list documents, so only the budget's
  deletion is enforced; the Packing deletion is the app's. A removed member deletes both at their next full sync.
- "This trip already has 10 members" is inferred: a denied join while the code is still readable (open, unexpired,
  trip live) can only be a full trip. Invites are never deleted (used, cancelled or expired ones simply stay
  unreadable).
- Timestamps are device clocks (see "Merge rules").
- **The trip's place and app versions before suggestions** (until every phone runs the new app): an old app's edit of
  the destination text keeps the old `countryCode`/coordinates (its upload merges only the fields it knows), and an old
  app **can't delete** a trip that has a place (its tombstone leaves `countryCode`/`latitude`/`longitude`, which
  `validTripTombstone` denies; the delete stays pending on that phone until it is updated). Order: publish the rules
  (they accept everything the old app writes), then update every phone.

### Shared trips: next stage (planned, not built)
- **Stage 3 – personal data and polish:** budget card/percentages/donut/PDF/assistant use my budget and my share
  (wired since stage 1), "Your budget" copy; Prep progress = shared "Before the trip" + my Packing; suggested items
  guarded against two members adding them at once; tests with two members editing the same item (newer wins); a
  member's leftover `memberData` after the owner deletes the trip is already deleted by the lost-trip step.

## Invites and joining (`data/sharing`, `ui/tripdetails/members`, `ui/home/join`)
- **Codes** (`InviteCode`): 8 characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (no 0/O/1/I/L), `SecureRandom`, shown
  "K7M4 XP2R", "K7M4-XP2R" in the shared message (which `extract` finds again when pasted). Work once, expire 7 days
  after the server's `createdAt`.
- **Overview "Traveling together" card** (`MembersCard`, first on the Overview; reference `design/trip_members.png`,
  only this card; the rest of the Overview is unchanged): title + count pill on one line (when "Invite" doesn't fit
  it becomes a round icon button, then the title gets smaller, `minFitTextScale`; never wraps), 56dp avatars ("You" first, then the
  others in join order), "Owner" pill under the owner, tonal "Invite" (owner only). Tapping the card opens the members
  sheet. State: `TripDetailsUiState.Loaded.members` (`membersState(me, trip, members, zone)`).
- **Invite sheet** (`InviteSheet`, `InviteViewModel` (default owner = `TripDetails` entry); `design/trip_invite.png`):
  "Invite" shows the owner's open code (`private/invite` → the invite, if still readable and open) or creates one (one
  batch: owner's member document, invite, pointer); the code on the hero gradient with a copy button, "Expires 16 Oct"
  and "Works once" pills, "Share code" (`ACTION_SEND` chooser, `invite_share_message`), "Cancel this code" (then
  "Create a new code"); offline → "Try again"; a trip with 10 members → no code. Then "Members" with each member's
  row ("Joined 9 Oct", "Owner"; under the name on narrow sheets / large fonts). Opened from the card it has one title,
  "Members" + count; the invite mode says "Invite to London" (`shortDestination`, also in the shared message). The owner's ⋮ → "Remove from trip" → confirmation (`isRemoving` synchronous) →
  `SyncController.removeMember`; a non-owner gets "Leave trip".
- **Leaving:** non-owners see "Leave trip" (`ic_logout`) instead of "Delete trip" in every trip menu and "Leave" on
  the Home swipe (`Trip.ownerId` vs the uid; a blank owner counts as mine). `DeleteTripViewModel.onRemoveTrip(trip)`
  opens "Leave this trip?" (`DeleteDialogState.leave`); Home confirms with `SyncController.leaveTrip`; the trip screen
  hands it to Home (`pendingLeaveTripId`, like a delete). Messages `LEFT` / `LEAVE_FAILED`.
- **Joining** (Home: outlined "Join trip" pill next to "+ Add trip", both in a full-width FAB row; `JoinTripSheet`,
  `JoinTripViewModel`; `design/home_join.png`): 8 boxes over one hidden `BasicTextField` (the text is Compose state;
  `InviteCode.input` keeps a pasted code), "Paste" (clipboard), a complete code is looked up (`CodeLookup`); a valid
  code shows the "YOU'RE JOINING" card (destination, dates, "Invited by Noa") and enables "Join trip" (`isJoining`
  synchronous). Messages: one for wrong / expired / used / cancelled, "already a member" (also when the trip is
  already on this device), "already has 10 members", no connection. After joining: the sheet closes,
  `SyncController.refresh()` brings the trip down, snackbar "You joined Rome, Italy".
- **Member name** (`memberName`): the display name, ≤ 100 characters; without one "Trip member", never the email.
- **Budget tab:** other members are `Person.Member` (after "You", before the travelers, color by their place in
  `memberIds`, not tappable or removable), offered in "Paid by" / "Split between" (a new expense is split between
  everyone); a member who left shows as "Noa (left)" on old expenses. `ExpenseFormUiState.order` keeps the stored
  split in list order while the chips show "You" first.

