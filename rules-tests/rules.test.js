// Firestore security rules tests. Run only against the local emulator (project demo-planyourtrip, which never
// reaches a real Firebase project): `npm test` in this folder. See CLAUDE.md, "Security rules tests".
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  arrayUnion,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  documentId,
  setDoc,
  setLogLevel,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const RULES = readFileSync(fileURLToPath(new URL('../firestore.rules', import.meta.url)), 'utf8');

/** The page size of the app's legacy migration query; the rules accept exactly this limit after the marker. */
const LEGACY_PAGE = 450;

let env;

before(async () => {
  // Denied requests are expected here; the SDK would log each one.
  setLogLevel('silent');
  env = await initializeTestEnvironment({
    projectId: 'demo-planyourtrip',
    firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

// ---------- Data ----------

const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

function trip(owner = 'alice', members = [owner], extra = {}) {
  return {
    destination: 'Rome',
    startDate: '2026-11-12',
    endDate: '2026-11-17',
    currencyCode: 'EUR',
    color: 'SKY',
    ownerId: owner,
    memberIds: members,
    updatedAt: 1,
    deleted: false,
    ...extra,
  };
}

const tripTombstone = (owner = 'alice', members = [owner]) => ({ ownerId: owner, memberIds: members, deleted: true, updatedAt: 9 });
const tombstone = { deleted: true, updatedAt: 9 };

const expense = (extra = {}) => ({
  name: 'Dinner',
  amountAgorot: 12050,
  category: 'FOOD',
  date: '2026-11-13',
  originalAmount: null,
  originalCurrency: null,
  exchangeRate: null,
  rateDate: null,
  paidBy: 'alice',
  splitBetween: ['alice', 'bob'],
  updatedAt: 1,
  deleted: false,
  ...extra,
});

const activity = (extra = {}) => ({
  date: '2026-11-13',
  startTime: '09:05',
  title: 'Colosseum',
  note: null,
  type: 'ACTIVITY',
  location: null,
  estimatedCostAgorot: null,
  booked: false,
  updatedAt: 1,
  deleted: false,
  ...extra,
});

const checklistItem = (list, extra = {}) => ({
  list,
  text: 'Passport',
  checked: false,
  dueDate: null,
  createdAt: 1,
  updatedAt: 1,
  deleted: false,
  ...extra,
});

const traveler = (extra = {}) => ({ name: 'Noa', color: 'ROSE', createdAt: 1, updatedAt: 1, deleted: false, ...extra });

const settlement = (extra = {}) => ({
  fromTraveler: 'bob',
  toTraveler: 'alice',
  amountAgorot: 3200,
  date: '2026-11-14',
  createdAt: 1,
  updatedAt: 1,
  deleted: false,
  ...extra,
});

const budget = (extra = {}) => ({ budgetAgorot: 600000, updatedAt: 1, deleted: false, ...extra });

/** Writes [docs] (path → data) without rules. */
async function seed(docs) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [path, data] of Object.entries(docs)) await setDoc(doc(db, path), data);
  });
}

/** Alice's trip t1 shared with Bob, with one item of every kind and both members' personal data. */
async function seedSharedTrip() {
  await seed({
    'trips/t1': trip('alice', ['alice', 'bob']),
    'trips/t1/expenses/e1': expense(),
    'trips/t1/activities/a1': activity(),
    'trips/t1/checklist/c1': checklistItem('BEFORE_TRIP'),
    'trips/t1/travelers/r1': traveler(),
    'trips/t1/settlements/s1': settlement(),
    'trips/t1/memberData/alice': budget(),
    'trips/t1/memberData/alice/packing/p1': checklistItem('PACKING'),
    'trips/t1/memberData/bob': budget({ budgetAgorot: 300000 }),
    'trips/t1/memberData/bob/packing/p2': checklistItem('PACKING', { text: 'Charger' }),
  });
}

const SHARED_CHILDREN = [
  ['expenses/e1', expense()],
  ['activities/a1', activity()],
  ['checklist/c1', checklistItem('BEFORE_TRIP')],
  ['travelers/r1', traveler()],
  ['settlements/s1', settlement()],
];

// ---------- Creating a trip ----------

describe('creating a trip', () => {
  it('a signed-in user creates a trip they own, as its only member', async () => {
    await assertSucceeds(setDoc(doc(as('alice'), 'trips/t1'), trip('alice')));
  });

  it('nobody signed out can create or read anything', async () => {
    await assertFails(setDoc(doc(anonymous(), 'trips/t1'), trip('alice')));
    await seedSharedTrip();
    await assertFails(getDoc(doc(anonymous(), 'trips/t1')));
  });

  it('can not be created for someone else, or with other members', async () => {
    await assertFails(setDoc(doc(as('mallory'), 'trips/t1'), trip('alice')));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['alice', 'bob'])));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['bob', 'alice'])));
  });

  it('never holds a budget (each member\'s budget is personal)', async () => {
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['alice'], { budgetAgorot: 600000 })));
  });

  it('validates its fields', async () => {
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['alice'], { destination: '' })));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['alice'], { endDate: '2026-11-01' })));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['alice'], { startDate: '12/11/2026' })));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['alice'], { email: 'alice@example.com' })));
  });

  it('a new trip and its first items can be written in one batch', async () => {
    const db = as('alice');
    const batch = writeBatch(db);
    batch.set(doc(db, 'trips/t1'), trip('alice'));
    batch.set(doc(db, 'trips/t1/expenses/e1'), expense({ splitBetween: ['alice'] }));
    batch.set(doc(db, 'trips/t1/memberData/alice'), budget());
    await assertSucceeds(batch.commit());
  });

  it('a batch of 450 items of one trip passes (the app writes one trip per batch)', async () => {
    // Every item's rule looks up the same trip document; repeated lookups of one document count once toward
    // Firestore's limit of 20 lookups per batch.
    const db = as('alice');
    await assertSucceeds(setDoc(doc(db, 'trips/t1'), trip('alice')));
    const batch = writeBatch(db);
    for (let i = 0; i < 450; i++) {
      batch.set(doc(db, `trips/t1/expenses/e${i}`), expense({ splitBetween: ['alice'] }));
    }
    await assertSucceeds(batch.commit());
  });

  it('can be created as a tombstone (a trip deleted before the migration)', async () => {
    await assertSucceeds(setDoc(doc(as('alice'), 'trips/t1'), tripTombstone('alice')));
  });
});

// ---------- Guessing a trip id / listing trips ----------

describe('someone who is not a member', () => {
  beforeEach(seedSharedTrip);

  it('can not read a trip by guessing its id', async () => {
    await assertFails(getDoc(doc(as('mallory'), 'trips/t1')));
  });

  it('sees only "no document" for an id that does not exist', async () => {
    const snap = await assertSucceeds(getDoc(doc(as('mallory'), 'trips/nope')));
    if (snap.exists()) throw new Error('expected no document');
  });

  it('can not list trips, except their own', async () => {
    const db = as('mallory');
    await assertFails(getDocs(collection(db, 'trips')));
    await assertFails(getDocs(query(collection(db, 'trips'), where('memberIds', 'array-contains', 'alice'))));
    await assertFails(getDocs(query(collection(db, 'trips'), where('ownerId', '==', 'alice'))));
    const mine = await assertSucceeds(getDocs(query(collection(db, 'trips'), where('memberIds', 'array-contains', 'mallory'))));
    if (!mine.empty) throw new Error('expected no trips');
  });

  it('can not read or write any shared item of the trip', async () => {
    const db = as('mallory');
    for (const [path, data] of SHARED_CHILDREN) {
      await assertFails(getDoc(doc(db, `trips/t1/${path}`)));
      await assertFails(setDoc(doc(db, `trips/t1/${path}`), data));
      await assertFails(setDoc(doc(db, `trips/t1/${path}`), tombstone));
      await assertFails(deleteDoc(doc(db, `trips/t1/${path}`)));
    }
    await assertFails(getDocs(collection(db, 'trips/t1/expenses')));
  });

  it('can not overwrite the trip, change it, delete it or make themselves its owner', async () => {
    const db = as('mallory');
    await assertFails(setDoc(doc(db, 'trips/t1'), trip('mallory')));
    await assertFails(setDoc(doc(db, 'trips/t1'), trip('alice', ['alice', 'bob', 'mallory'])));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { destination: 'Hacked' }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { ownerId: 'mallory' }));
    await assertFails(deleteDoc(doc(db, 'trips/t1')));
  });

  it('can not add themselves as a member (joining needs an invite, Stage 2)', async () => {
    await assertFails(updateDoc(doc(as('mallory'), 'trips/t1'), { memberIds: arrayUnion('mallory') }));
  });
});

// ---------- Members ----------

describe('a member', () => {
  beforeEach(seedSharedTrip);

  it('reads the trip and every shared item, and lists their trips', async () => {
    const db = as('bob');
    await assertSucceeds(getDoc(doc(db, 'trips/t1')));
    for (const [path] of SHARED_CHILDREN) await assertSucceeds(getDoc(doc(db, `trips/t1/${path}`)));
    const trips = await assertSucceeds(getDocs(query(collection(db, 'trips'), where('memberIds', 'array-contains', 'bob'))));
    if (trips.size !== 1) throw new Error('expected one trip');
  });

  it('edits the trip and every shared item', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, 'trips/t1'), { destination: 'Rome, Italy', updatedAt: 2 }, { merge: true }));
    for (const [path, data] of SHARED_CHILDREN) {
      await assertSucceeds(setDoc(doc(db, `trips/t1/${path}`), { ...data, updatedAt: 2 }));
      await assertSucceeds(setDoc(doc(db, `trips/t1/${path}`), tombstone));
    }
  });

  it('can not make themselves the owner', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1'), { ownerId: 'bob' }));
    await assertFails(updateDoc(doc(as('alice'), 'trips/t1'), { ownerId: 'bob' }));
  });

  it('can not add another person, and neither can the owner', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1'), { memberIds: arrayUnion('carol') }));
    await assertFails(updateDoc(doc(as('alice'), 'trips/t1'), { memberIds: arrayUnion('carol') }));
    await assertFails(updateDoc(doc(as('alice'), 'trips/t1'), { memberIds: ['alice', 'bob', 'bob'] }));
  });

  it('can remove only themselves', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1'), { memberIds: ['bob'] }));
    await assertSucceeds(updateDoc(doc(as('bob'), 'trips/t1'), { memberIds: ['alice'] }));
  });

  it('can not change the members together with anything else', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1'), { memberIds: ['alice'], destination: 'Oslo' }));
  });

  it('can not delete the trip, nor bring a deleted trip back', async () => {
    await assertFails(setDoc(doc(as('bob'), 'trips/t1'), tripTombstone('alice', ['alice', 'bob'])));
    await assertFails(deleteDoc(doc(as('bob'), 'trips/t1')));
    await seed({ 'trips/t1': tripTombstone('alice', ['alice', 'bob']) });
    await assertFails(setDoc(doc(as('bob'), 'trips/t1'), trip('alice', ['alice', 'bob'], { updatedAt: 20 })));
  });

  it('can not hard-delete shared items (only tombstones)', async () => {
    await assertFails(deleteDoc(doc(as('bob'), 'trips/t1/expenses/e1')));
  });
});

describe('the owner', () => {
  beforeEach(seedSharedTrip);

  it('removes another member, but never themselves', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'trips/t1'), { memberIds: ['bob'] }));
    await assertSucceeds(updateDoc(doc(as('alice'), 'trips/t1'), { memberIds: ['alice'] }));
  });

  it('deletes the trip (tombstone, keeping who may read it) and may bring it back', async () => {
    const db = as('alice');
    await assertSucceeds(setDoc(doc(db, 'trips/t1'), tripTombstone('alice', ['alice', 'bob'])));
    // Members still read the tombstone, so their phones learn about the delete.
    await assertSucceeds(getDoc(doc(as('bob'), 'trips/t1')));
    // No live item can be added to a deleted trip; tombstones still can.
    await assertFails(setDoc(doc(as('bob'), 'trips/t1/expenses/e2'), expense()));
    await assertSucceeds(setDoc(doc(as('bob'), 'trips/t1/expenses/e1'), tombstone));
    await assertSucceeds(setDoc(doc(db, 'trips/t1'), trip('alice', ['alice', 'bob'], { updatedAt: 20 })));
  });

  it('can not read or write another member\'s personal data', async () => {
    const db = as('alice');
    await assertFails(getDoc(doc(db, 'trips/t1/memberData/bob')));
    await assertFails(getDocs(collection(db, 'trips/t1/memberData/bob/packing')));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/bob'), budget()));
    await assertFails(deleteDoc(doc(db, 'trips/t1/memberData/bob')));
    await assertFails(deleteDoc(doc(db, 'trips/t1/memberData/bob/packing/p2')));
  });

  it('deletes the trip the way the app does: removing the data fields, keeping owner and members', async () => {
    const fields = Object.fromEntries(['destination', 'startDate', 'endDate', 'currencyCode', 'color'].map((f) => [f, deleteField()]));
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1'), { ...fields, deleted: true, updatedAt: 9 }));
    await assertSucceeds(updateDoc(doc(as('alice'), 'trips/t1'), { ...fields, deleted: true, updatedAt: 9 }));
  });

  it('hard-deletes the trip document', async () => {
    await assertSucceeds(deleteDoc(doc(as('alice'), 'trips/t1')));
  });
});

// ---------- Personal data ----------

describe('personal data (memberData)', () => {
  beforeEach(seedSharedTrip);

  it('each member reads and writes only their own budget and Packing list', async () => {
    const db = as('bob');
    await assertSucceeds(getDoc(doc(db, 'trips/t1/memberData/bob')));
    await assertSucceeds(getDocs(collection(db, 'trips/t1/memberData/bob/packing')));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/memberData/bob'), budget({ budgetAgorot: null, updatedAt: 2 })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/memberData/bob/packing/p3'), checklistItem('PACKING')));
    await assertFails(getDoc(doc(db, 'trips/t1/memberData/alice')));
    await assertFails(getDocs(collection(db, 'trips/t1/memberData/alice/packing')));
    await assertFails(getDocs(collection(db, 'trips/t1/memberData')));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/alice'), budget()));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/alice/packing/p9'), checklistItem('PACKING')));
  });

  it('someone who is not a member can not create personal data in the trip', async () => {
    await assertFails(setDoc(doc(as('mallory'), 'trips/t1/memberData/mallory'), budget()));
    await assertFails(setDoc(doc(as('mallory'), 'trips/t1/memberData/mallory/packing/p1'), checklistItem('PACKING')));
  });

  it('validates the budget and keeps each list in its place', async () => {
    const db = as('bob');
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/bob'), budget({ budgetAgorot: 0 })));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/bob'), budget({ email: 'bob@example.com' })));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/bob/packing/p3'), checklistItem('BEFORE_TRIP')));
    await assertFails(setDoc(doc(db, 'trips/t1/checklist/c2'), checklistItem('PACKING')));
  });
});

// ---------- Leaving or being removed ----------

describe('a member who left or was removed', () => {
  beforeEach(async () => {
    await seedSharedTrip();
    await seed({ 'trips/t1': trip('alice', ['alice']) });
  });

  it('loses access to the trip and every shared item at once', async () => {
    const db = as('bob');
    await assertFails(getDoc(doc(db, 'trips/t1')));
    for (const [path, data] of SHARED_CHILDREN) {
      await assertFails(getDoc(doc(db, `trips/t1/${path}`)));
      await assertFails(setDoc(doc(db, `trips/t1/${path}`), data));
    }
  });

  it('can not write an expense into the trip they left', async () => {
    await assertFails(setDoc(doc(as('bob'), 'trips/t1/expenses/e2'), expense({ paidBy: 'bob' })));
  });

  it('can still read and delete (only delete) their own personal data, nobody else\'s', async () => {
    const db = as('bob');
    await assertSucceeds(getDoc(doc(db, 'trips/t1/memberData/bob')));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/bob'), budget({ updatedAt: 3 })));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/bob/packing/p2'), checklistItem('PACKING', { updatedAt: 3 })));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/memberData/bob/packing/p2')));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/memberData/bob')));
    await assertFails(deleteDoc(doc(db, 'trips/t1/memberData/alice')));
    await assertFails(deleteDoc(doc(db, 'trips/t1/memberData/alice/packing/p1')));
  });

  it('can delete their own personal data even after the trip document is gone', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => deleteDoc(doc(ctx.firestore(), 'trips/t1')));
    await assertSucceeds(deleteDoc(doc(as('bob'), 'trips/t1/memberData/bob/packing/p2')));
    await assertSucceeds(deleteDoc(doc(as('bob'), 'trips/t1/memberData/bob')));
  });

  it('leaving and writing in the same batch fails', async () => {
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob']) });
    const db = as('bob');
    const batch = writeBatch(db);
    batch.update(doc(db, 'trips/t1'), { memberIds: ['alice'] });
    batch.set(doc(db, 'trips/t1/expenses/e9'), expense({ paidBy: 'bob' }));
    await assertFails(batch.commit());
  });
});

// ---------- Field validation (as before shared trips) ----------

describe('field validation', () => {
  beforeEach(seedSharedTrip);
  const write = (path, data) => setDoc(doc(as('alice'), `trips/t1/${path}`), data);

  it('expenses', async () => {
    await assertSucceeds(write('expenses/e2', expense({ originalAmount: '10.00', originalCurrency: 'EUR', exchangeRate: '4.02', rateDate: '2026-11-13' })));
    await assertFails(write('expenses/e2', expense({ amountAgorot: 0 })));
    await assertFails(write('expenses/e2', expense({ amountAgorot: 1.5 })));
    await assertFails(write('expenses/e2', expense({ name: '' })));
    await assertFails(write('expenses/e2', expense({ exchangeRate: '4,02' })));
    await assertFails(write('expenses/e2', expense({ splitBetween: [] })));
    await assertFails(write('expenses/e2', expense({ paidBy: '' })));
    const { paidBy, ...withoutPayer } = expense();
    await assertFails(write('expenses/e2', withoutPayer));
    await assertFails(write('expenses/e2', expense({ email: 'x@y.z' })));
  });

  it('activities', async () => {
    await assertSucceeds(write('activities/a2', activity({ startTime: null, estimatedCostAgorot: 9000 })));
    await assertFails(write('activities/a2', activity({ startTime: '24:00' })));
    await assertFails(write('activities/a2', activity({ title: '' })));
    await assertFails(write('activities/a2', activity({ estimatedCostAgorot: 0 })));
    await assertFails(write('activities/a2', activity({ booked: 'yes' })));
  });

  it('checklist items', async () => {
    await assertSucceeds(write('checklist/c2', checklistItem('BEFORE_TRIP', { dueDate: '2026-11-01' })));
    await assertFails(write('checklist/c2', checklistItem('BEFORE_TRIP', { text: '' })));
    await assertFails(write('checklist/c2', checklistItem('BEFORE_TRIP', { checked: 1 })));
    await assertFails(write('checklist/c2', checklistItem('BEFORE_TRIP', { dueDate: 'tomorrow' })));
  });

  it('travelers', async () => {
    await assertFails(write('travelers/r2', traveler({ name: '' })));
    await assertFails(write('travelers/r2', traveler({ name: 'x'.repeat(101) })));
    await assertFails(write('travelers/r2', traveler({ email: 'noa@example.com' })));
  });

  it('settlements', async () => {
    await assertFails(write('settlements/s2', settlement({ toTraveler: 'bob' })));
    await assertFails(write('settlements/s2', settlement({ amountAgorot: 0 })));
    await assertFails(write('settlements/s2', settlement({ date: '14.11.2026' })));
  });

  it('tombstones have nothing but deleted and updatedAt', async () => {
    await assertSucceeds(write('expenses/e1', tombstone));
    await assertFails(write('expenses/e1', { ...tombstone, name: 'Dinner' }));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), { ...tripTombstone('alice', ['alice', 'bob']), destination: 'Rome' }));
  });
});

// ---------- Members and invites: Stage 2, nothing allowed yet ----------

describe('members and invites (not in Stage 1)', () => {
  beforeEach(seedSharedTrip);

  it('no member document can be written, with or without extra fields', async () => {
    await assertFails(setDoc(doc(as('bob'), 'trips/t1/members/bob'), { name: 'Bob', joinedAt: 1 }));
    await assertFails(setDoc(doc(as('bob'), 'trips/t1/members/bob'), { name: 'Bob', joinedAt: 1, email: 'bob@example.com' }));
    await assertFails(getDoc(doc(as('bob'), 'trips/t1/members/alice')));
  });

  it('no invite can be created, read or listed', async () => {
    await assertFails(setDoc(doc(as('alice'), 'invites/ABCD2345'), { tripId: 't1' }));
    await seed({ 'invites/ABCD2345': { tripId: 't1' } });
    await assertFails(getDoc(doc(as('mallory'), 'invites/ABCD2345')));
    await assertFails(getDocs(collection(as('mallory'), 'invites')));
  });
});

// ---------- The legacy copy (users/{uid}/trips) ----------

describe('the legacy copy', () => {
  beforeEach(async () => {
    await seed({
      'users/alice/trips/t1': { destination: 'Rome', updatedAt: 1, deleted: false },
      'users/alice/trips/t1/expenses/e1': { name: 'Dinner', updatedAt: 1, deleted: false },
      'users/alice/trips/t1/places/p1': { name: 'Colosseum', updatedAt: 1, deleted: false },
    });
  });

  const legacyQuery = (db, path) => query(collection(db, path), orderBy(documentId()), limit(LEGACY_PAGE));

  it('can no longer be written, by anyone', async () => {
    await assertFails(setDoc(doc(as('alice'), 'users/alice/trips/t2'), { destination: 'Oslo', updatedAt: 1, deleted: false }));
    await assertFails(setDoc(doc(as('alice'), 'users/alice/trips/t1/expenses/e1'), tombstone));
    await assertFails(updateDoc(doc(as('alice'), 'users/alice/trips/t1'), { destination: 'Oslo' }));
  });

  it('is read by its owner as before until it is migrated', async () => {
    const db = as('alice');
    await assertSucceeds(getDoc(doc(db, 'users/alice/trips/t1')));
    await assertSucceeds(getDocs(collection(db, 'users/alice/trips')));
    await assertSucceeds(getDocs(collection(db, 'users/alice/trips/t1/expenses')));
    await assertSucceeds(getDocs(legacyQuery(db, 'users/alice/trips')));
  });

  it('after the marker, old app versions are cut off; the migration\'s query still finds leftovers', async () => {
    const db = as('alice');
    await assertSucceeds(setDoc(doc(db, 'users/alice'), { sharedTripsMigrated: true }));
    await assertFails(getDoc(doc(db, 'users/alice/trips/t1')));
    await assertFails(getDocs(collection(db, 'users/alice/trips')));
    await assertFails(getDocs(collection(db, 'users/alice/trips/t1/expenses')));
    await assertFails(getDocs(query(collection(db, 'users/alice/trips'), limit(100))));
    await assertSucceeds(getDocs(legacyQuery(db, 'users/alice/trips')));
    await assertSucceeds(getDocs(legacyQuery(db, 'users/alice/trips/t1/places')));
    await assertSucceeds(deleteDoc(doc(db, 'users/alice/trips/t1/places/p1')));
    await assertSucceeds(deleteDoc(doc(db, 'users/alice/trips/t1/expenses/e1')));
    await assertSucceeds(deleteDoc(doc(db, 'users/alice/trips/t1')));
  });

  it('the marker can not be unset, changed or deleted', async () => {
    const db = as('alice');
    await assertFails(setDoc(doc(db, 'users/alice'), { sharedTripsMigrated: false }));
    await assertFails(setDoc(doc(db, 'users/alice'), { sharedTripsMigrated: true, email: 'alice@example.com' }));
    await assertSucceeds(setDoc(doc(db, 'users/alice'), { sharedTripsMigrated: true }));
    await assertFails(setDoc(doc(db, 'users/alice'), { sharedTripsMigrated: false }));
    await assertFails(deleteDoc(doc(db, 'users/alice')));
  });

  it('nobody else can read, delete or mark another account\'s legacy copy', async () => {
    const db = as('mallory');
    await assertFails(getDoc(doc(db, 'users/alice/trips/t1')));
    await assertFails(getDocs(legacyQuery(db, 'users/alice/trips')));
    await assertFails(deleteDoc(doc(db, 'users/alice/trips/t1')));
    await assertFails(setDoc(doc(db, 'users/alice'), { sharedTripsMigrated: true }));
    await assertFails(getDoc(doc(db, 'users/alice')));
  });
});
