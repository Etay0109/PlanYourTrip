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
  Timestamp,
  arrayRemove,
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
  serverTimestamp,
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

  it('can not add themselves as a member without an invite', async () => {
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

  it('can remove only themselves, deleting their own budget in the same write', async () => {
    const db = as('bob');
    await assertFails(updateDoc(doc(db, 'trips/t1'), { memberIds: ['bob'] }));
    // Leaving without deleting the budget fails.
    await assertFails(updateDoc(doc(db, 'trips/t1'), { memberIds: ['alice'] }));
    const batch = writeBatch(db);
    batch.update(doc(db, 'trips/t1'), { memberIds: ['alice'] });
    batch.delete(doc(db, 'trips/t1/memberData/bob'));
    await assertSucceeds(batch.commit());
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

// ---------- The trip's place (destination suggestions) ----------

describe('the trip\'s place (country code and coordinates)', () => {
  const london = { countryCode: 'GB', latitude: 51.5074456, longitude: -0.1277653 };
  /** What an upload of an existing trip writes: the data fields with set(merge), never owner or members. */
  const oldAppUpdate = { destination: 'Rome, Italy', startDate: '2026-11-12', endDate: '2026-11-18', currencyCode: 'EUR', color: 'SKY', updatedAt: 5, deleted: false };
  const newAppUpdate = { ...oldAppUpdate, countryCode: 'IT', latitude: 41.8933203, longitude: 12.4829321 };
  const create = (data) => setDoc(doc(as('carol'), 'trips/t9'), data);

  it('the installed app keeps working: a trip without the keys is created and updated as before', async () => {
    await assertSucceeds(create(trip('carol')));
    await assertSucceeds(setDoc(doc(as('carol'), 'trips/t9'), oldAppUpdate, { merge: true }));
  });

  it('the installed app may update a trip the new app gave a place (the place stays)', async () => {
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob'], london) });
    await assertSucceeds(setDoc(doc(as('bob'), 'trips/t1'), oldAppUpdate, { merge: true }));
    let after;
    await env.withSecurityRulesDisabled(async (ctx) => { after = (await getDoc(doc(ctx.firestore(), 'trips/t1'))).data(); });
    if (after.countryCode !== 'GB' || after.latitude !== london.latitude) throw new Error('the place was lost');
  });

  it('accepts a full place, no place (nulls) and whole-number coordinates', async () => {
    await assertSucceeds(create(trip('carol', ['carol'], london)));
    await assertSucceeds(create(trip('carol', ['carol'], { countryCode: null, latitude: null, longitude: null })));
    await assertSucceeds(create(trip('carol', ['carol'], { countryCode: 'GB', latitude: null, longitude: null })));
    await assertSucceeds(create(trip('carol', ['carol'], { countryCode: null, latitude: 0, longitude: 0 })));
    await assertSucceeds(create(trip('carol', ['carol'], { countryCode: 'NZ', latitude: -90, longitude: 180 })));
  });

  it('a member writes the place with the trip\'s data, the way the new app uploads it', async () => {
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob']) });
    await assertSucceeds(setDoc(doc(as('bob'), 'trips/t1'), newAppUpdate, { merge: true }));
    await assertSucceeds(setDoc(doc(as('bob'), 'trips/t1'), { ...newAppUpdate, countryCode: null, latitude: null, longitude: null }, { merge: true }));
  });

  it('rejects a bad country code', async () => {
    for (const countryCode of ['gb', 'GBR', 'G', '', 'G1', 44, true, ['GB']]) {
      await assertFails(create(trip('carol', ['carol'], { ...london, countryCode })));
    }
  });

  it('rejects coordinates out of range, of the wrong type or only one of them', async () => {
    const bad = [
      { latitude: 91, longitude: 0 }, { latitude: -90.0001, longitude: 0 },
      { latitude: 0, longitude: 180.5 }, { latitude: 0, longitude: -181 },
      { latitude: '51.5', longitude: -0.12 }, { latitude: 51.5, longitude: '-0.12' },
      { latitude: 51.5, longitude: null }, { latitude: null, longitude: -0.12 },
      { latitude: 51.5 }, { longitude: -0.12 },
      { latitude: [51.5], longitude: -0.12 }, { latitude: true, longitude: false },
    ];
    for (const coordinates of bad) {
      await assertFails(create(trip('carol', ['carol'], { countryCode: 'GB', ...coordinates })));
    }
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob'], london) });
    await assertFails(setDoc(doc(as('bob'), 'trips/t1'), { ...newAppUpdate, latitude: 100 }, { merge: true }));
    await assertFails(setDoc(doc(as('bob'), 'trips/t1'), { ...oldAppUpdate, longitude: null }, { merge: true }));
  });

  it('the new app deletes a trip with a place: every data field goes, the tombstone is unchanged', async () => {
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob'], london) });
    const fields = Object.fromEntries(
      ['destination', 'startDate', 'endDate', 'currencyCode', 'color', 'countryCode', 'latitude', 'longitude']
        .map((f) => [f, deleteField()]),
    );
    await assertSucceeds(updateDoc(doc(as('alice'), 'trips/t1'), { ...fields, deleted: true, updatedAt: 9 }));
  });

  it('known limit: the installed app can not delete a trip that has a place (its tombstone leaves the place)', async () => {
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob'], london) });
    const fields = Object.fromEntries(['destination', 'startDate', 'endDate', 'currencyCode', 'color'].map((f) => [f, deleteField()]));
    await assertFails(updateDoc(doc(as('alice'), 'trips/t1'), { ...fields, deleted: true, updatedAt: 9 }));
  });

  it('a tombstone still holds nothing but owner, members, deleted and updatedAt', async () => {
    await assertFails(setDoc(doc(as('carol'), 'trips/t9'), { ...tripTombstone('carol'), countryCode: 'GB' }));
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

  // The limits match app/src/main/java/com/example/planyourtrip/data/trip/TextLimits.kt.
  it('text lengths: the limit passes, one more character fails', async () => {
    const x = (n) => 'x'.repeat(n);
    const tripAs = (extra) => setDoc(doc(as('alice'), 'trips/t1'), trip('alice', ['alice', 'bob'], extra));
    await assertSucceeds(tripAs({ destination: x(200) }));
    await assertFails(tripAs({ destination: x(201) }));
    await assertFails(tripAs({ color: x(41) }));
    await assertFails(tripAs({ currencyCode: x(41) }));

    await assertSucceeds(write('expenses/e2', expense({ name: x(200) })));
    await assertFails(write('expenses/e2', expense({ name: x(201) })));
    await assertFails(write('expenses/e2', expense({ category: x(41) })));
    await assertFails(write('expenses/e2', expense({ originalAmount: '10.00', originalCurrency: x(41), exchangeRate: '4.02', rateDate: '2026-11-13' })));

    await assertSucceeds(write('activities/a2', activity({ title: x(200), location: x(300), note: x(2000) })));
    await assertFails(write('activities/a2', activity({ title: x(201) })));
    await assertFails(write('activities/a2', activity({ location: x(301) })));
    await assertFails(write('activities/a2', activity({ note: x(2001) })));
    await assertFails(write('activities/a2', activity({ type: x(41) })));

    await assertSucceeds(write('checklist/c2', checklistItem('BEFORE_TRIP', { text: x(500) })));
    await assertFails(write('checklist/c2', checklistItem('BEFORE_TRIP', { text: x(501) })));
    await assertSucceeds(write('memberData/alice/packing/p3', checklistItem('PACKING', { text: x(500) })));
    await assertFails(write('memberData/alice/packing/p3', checklistItem('PACKING', { text: x(501) })));

    await assertSucceeds(write('travelers/r2', traveler({ name: x(100) })));
    await assertFails(write('travelers/r2', traveler({ name: x(101) })));
    await assertFails(write('travelers/r2', traveler({ color: x(41) })));
  });

  it('tombstones have nothing but deleted and updatedAt', async () => {
    await assertSucceeds(write('expenses/e1', tombstone));
    await assertFails(write('expenses/e1', { ...tombstone, name: 'Dinner' }));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1'), { ...tripTombstone('alice', ['alice', 'bob']), destination: 'Rome' }));
  });
});

// ---------- Invites, joining, leaving, removing ----------

const CODE = 'K7M4XP2R';
const CODE2 = 'ABCD2345';
const DAY = 24 * 60 * 60 * 1000;

const invite = (tripId = 't1', extra = {}) => ({
  tripId,
  inviterName: 'Alice',
  destination: 'Rome',
  startDate: '2026-11-12',
  endDate: '2026-11-17',
  createdAt: serverTimestamp(),
  status: 'open',
  usedBy: null,
  ...extra,
});

const member = (name, inviteCode = null, extra = {}) => ({ name, joinedAt: serverTimestamp(), left: false, inviteCode, ...extra });

/** The app's create-invite batch: the owner's member document, the invite and the owner's pointer to it. */
function createInvite(owner = 'alice', code = CODE, tripId = 't1', inviteExtra = {}) {
  const db = as(owner);
  const batch = writeBatch(db);
  batch.set(doc(db, `trips/${tripId}/members/${owner}`), member('Alice'));
  batch.set(doc(db, `invites/${code}`), invite(tripId, inviteExtra));
  batch.set(doc(db, `trips/${tripId}/private/invite`), { code });
  return batch.commit();
}

/** The app's join batch; [skip] leaves one of its three writes out. */
function join(uid, code = CODE, tripId = 't1', { skip = null, memberTrip = tripId, memberCode = code } = {}) {
  const db = as(uid);
  const batch = writeBatch(db);
  if (skip !== 'invite') batch.update(doc(db, `invites/${code}`), { status: 'used', usedBy: uid });
  if (skip !== 'trip') batch.update(doc(db, `trips/${tripId}`), { memberIds: arrayUnion(uid) });
  if (skip !== 'member') batch.set(doc(db, `trips/${memberTrip}/members/${uid}`), member(uid, memberCode));
  return batch.commit();
}

/** The app's leave batch: out of memberIds, member document left, budget and every Packing item deleted. */
function leave(uid, tripId = 't1', packingIds = []) {
  const db = as(uid);
  const batch = writeBatch(db);
  batch.update(doc(db, `trips/${tripId}`), { memberIds: arrayRemove(uid) });
  batch.update(doc(db, `trips/${tripId}/members/${uid}`), { left: true });
  batch.delete(doc(db, `trips/${tripId}/memberData/${uid}`));
  for (const id of packingIds) batch.delete(doc(db, `trips/${tripId}/memberData/${uid}/packing/${id}`));
  return batch.commit();
}

/** The app's remove-member batch, by the owner. */
function removeMember(owner, uid, tripId = 't1') {
  const db = as(owner);
  const batch = writeBatch(db);
  batch.update(doc(db, `trips/${tripId}`), { memberIds: arrayRemove(uid) });
  batch.update(doc(db, `trips/${tripId}/members/${uid}`), { left: true });
  return batch.commit();
}

async function expectMembers(tripId, members) {
  let actual;
  await env.withSecurityRulesDisabled(async (ctx) => {
    actual = (await getDoc(doc(ctx.firestore(), `trips/${tripId}`))).data().memberIds;
  });
  if (JSON.stringify(actual) !== JSON.stringify(members)) {
    throw new Error(`expected members ${JSON.stringify(members)}, got ${JSON.stringify(actual)}`);
  }
}

/** Makes [code]'s invite [days] old, without rules (the server time can't be moved). */
async function ageInvite(code, days) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), `invites/${code}`), { createdAt: Timestamp.fromMillis(Date.now() - days * DAY) });
  });
}

describe('creating and cancelling an invite', () => {
  beforeEach(async () => {
    await seed({ 'trips/t1': trip('alice'), 'trips/t2': trip('carol') });
  });

  it('the owner creates an invite with their member document and the pointer, and reads it back', async () => {
    await assertSucceeds(createInvite());
    await assertSucceeds(getDoc(doc(as('alice'), `invites/${CODE}`)));
    await assertSucceeds(getDoc(doc(as('alice'), 'trips/t1/private/invite')));
  });

  it('a member who is not the owner, or anyone else, can not create one', async () => {
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob']) });
    const db = as('bob');
    const batch = writeBatch(db);
    batch.set(doc(db, `invites/${CODE}`), invite('t1', { inviterName: 'Bob' }));
    await assertFails(batch.commit());
    await assertFails(setDoc(doc(as('mallory'), `invites/${CODE}`), invite('t1')));
    await assertFails(createInvite('bob'));
  });

  it('the owner can not create one for someone else\'s trip', async () => {
    await assertFails(createInvite('alice', CODE, 't2'));
  });

  it('copies the trip\'s destination and dates and the inviter\'s own name, and starts open now', async () => {
    await assertFails(createInvite('alice', CODE, 't1', { destination: 'Paris' }));
    await assertFails(createInvite('alice', CODE, 't1', { endDate: '2026-12-01' }));
    await assertFails(createInvite('alice', CODE, 't1', { inviterName: 'Someone else' }));
    await assertFails(createInvite('alice', CODE, 't1', { createdAt: Timestamp.fromMillis(Date.now() + 30 * DAY) }));
    await assertFails(createInvite('alice', CODE, 't1', { status: 'used' }));
    await assertFails(createInvite('alice', CODE, 't1', { usedBy: 'bob' }));
    await assertFails(createInvite('alice', CODE, 't1', { expiresAt: Timestamp.fromMillis(Date.now() + 365 * DAY) }));
    await assertFails(createInvite('alice', CODE, 't1', { inviterEmail: 'alice@example.com' }));
  });

  it('needs a well-formed code', async () => {
    await assertFails(createInvite('alice', 'K7M4XP2'));
    await assertFails(createInvite('alice', 'K7M4XP20'));
    await assertFails(createInvite('alice', 'k7m4xp2r'));
    await assertFails(createInvite('alice', 'K7M4XP2RR'));
  });

  it('can not overwrite an existing code (the app then tries another)', async () => {
    await assertSucceeds(createInvite());
    await assertFails(createInvite());
    await seed({ 'trips/t3': trip('dave') });
    await assertFails(createInvite('dave', CODE, 't3'));
  });

  it('can not be created for a deleted trip or a trip with 10 members', async () => {
    await seed({ 'trips/t1': tripTombstone('alice') });
    await assertFails(createInvite());
    const ten = ['alice', ...Array.from({ length: 9 }, (_, i) => `m${i}`)];
    await seed({ 'trips/t1': trip('alice', ten) });
    await assertFails(createInvite());
  });

  it('only the owner cancels it, and a cancelled code can not be read or used', async () => {
    await assertSucceeds(createInvite());
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob']) });
    await assertFails(updateDoc(doc(as('bob'), `invites/${CODE}`), { status: 'cancelled' }));
    await assertFails(updateDoc(doc(as('mallory'), `invites/${CODE}`), { status: 'cancelled' }));
    await assertFails(updateDoc(doc(as('alice'), `invites/${CODE}`), { status: 'cancelled', destination: 'Oslo' }));
    await assertSucceeds(updateDoc(doc(as('alice'), `invites/${CODE}`), { status: 'cancelled' }));
    await assertFails(getDoc(doc(as('carol'), `invites/${CODE}`)));
    await assertFails(join('carol'));
    await assertFails(updateDoc(doc(as('alice'), `invites/${CODE}`), { status: 'open' }));
  });

  it('nobody can delete an invite', async () => {
    await assertSucceeds(createInvite());
    await assertFails(deleteDoc(doc(as('alice'), `invites/${CODE}`)));
  });

  it('the pointer to the current code is the owner\'s only', async () => {
    await assertSucceeds(createInvite());
    await seed({ 'trips/t1': trip('alice', ['alice', 'bob']) });
    await assertFails(getDoc(doc(as('bob'), 'trips/t1/private/invite')));
    await assertFails(setDoc(doc(as('bob'), 'trips/t1/private/invite'), { code: CODE2 }));
    await assertSucceeds(setDoc(doc(as('alice'), 'trips/t1/private/invite'), { code: null }));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1/private/invite'), { code: 'nope' }));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1/private/other'), { code: null }));
  });
});

describe('reading an invite', () => {
  beforeEach(async () => {
    await seed({ 'trips/t1': trip('alice') });
    await createInvite();
  });

  it('works only with its exact code while it is open', async () => {
    const snap = await assertSucceeds(getDoc(doc(as('bob'), `invites/${CODE}`)));
    const keys = Object.keys(snap.data()).sort();
    const expected = ['createdAt', 'destination', 'endDate', 'inviterName', 'startDate', 'status', 'tripId', 'usedBy'];
    if (JSON.stringify(keys) !== JSON.stringify(expected)) throw new Error(`unexpected fields ${keys}`);
  });

  it('guessing a code looks the same as any invalid code', async () => {
    await assertFails(getDoc(doc(as('mallory'), `invites/${CODE2}`)));
    await assertFails(getDoc(doc(as('mallory'), 'invites/nonsense')));
  });

  it('invites can never be listed or queried', async () => {
    const db = as('mallory');
    await assertFails(getDocs(collection(db, 'invites')));
    await assertFails(getDocs(query(collection(db, 'invites'), where('status', '==', 'open'))));
    await assertFails(getDocs(query(collection(db, 'invites'), where('tripId', '==', 't1'))));
    await assertFails(getDocs(collection(as('alice'), 'invites')));
  });

  it('signed out can not read it', async () => {
    await assertFails(getDoc(doc(anonymous(), `invites/${CODE}`)));
  });

  it('an expired invite can not be read or used', async () => {
    await ageInvite(CODE, 6.9);
    await assertSucceeds(getDoc(doc(as('bob'), `invites/${CODE}`)));
    await ageInvite(CODE, 7.01);
    await assertFails(getDoc(doc(as('bob'), `invites/${CODE}`)));
    await assertFails(getDoc(doc(as('alice'), `invites/${CODE}`)));
    await assertFails(join('bob'));
  });

  it('a used invite can not be read', async () => {
    await assertSucceeds(join('bob'));
    await assertFails(getDoc(doc(as('carol'), `invites/${CODE}`)));
    await assertFails(getDoc(doc(as('bob'), `invites/${CODE}`)));
  });

  it('the invite of a deleted trip can not be read or used', async () => {
    await seed({ 'trips/t1': tripTombstone('alice') });
    await assertFails(getDoc(doc(as('bob'), `invites/${CODE}`)));
    await assertFails(join('bob'));
  });
});

describe('joining a trip', () => {
  beforeEach(async () => {
    await seed({ 'trips/t1': trip('alice'), 'trips/t2': trip('alice'), 'trips/t9': trip('zoe') });
    await createInvite();
  });

  it('with an open invite, one batch makes me a member and uses the code', async () => {
    await assertSucceeds(join('bob'));
    await expectMembers('t1', ['alice', 'bob']);
    const db = as('bob');
    await assertSucceeds(getDoc(doc(db, 'trips/t1')));
    await assertSucceeds(getDocs(collection(db, 'trips/t1/members')));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/expenses/e2'), expense({ paidBy: 'bob' })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/memberData/bob'), budget()));
  });

  it('re-using a code fails, also for the one who used it', async () => {
    await assertSucceeds(join('bob'));
    await assertFails(join('carol'));
    await expectMembers('t1', ['alice', 'bob']);
  });

  it('joining twice fails', async () => {
    await assertSucceeds(join('bob'));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `invites/${CODE2}`), invite('t1', { createdAt: Timestamp.now() }));
    });
    await assertFails(join('bob', CODE2));
    await expectMembers('t1', ['alice', 'bob']);
  });

  it('can not join a trip other than the invite\'s', async () => {
    await assertFails(join('bob', CODE, 't2'));
    await assertFails(join('bob', CODE, 't9'));
    // Using the code while writing the member document of another trip fails too.
    await assertFails(join('bob', CODE, 't2', { memberTrip: 't2' }));
  });

  it('a join batch missing any of its three writes fails', async () => {
    await assertFails(join('bob', CODE, 't1', { skip: 'invite' }));
    await assertFails(join('bob', CODE, 't1', { skip: 'trip' }));
    await assertFails(join('bob', CODE, 't1', { skip: 'member' }));
    await expectMembers('t1', ['alice']);
  });

  it('a member document must name the code that is used', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `invites/${CODE2}`), invite('t1', { createdAt: Timestamp.now() }));
    });
    await assertFails(join('bob', CODE, 't1', { memberCode: CODE2 }));
  });

  it('nobody can add another person, not even the owner or with a valid code', async () => {
    const db = as('bob');
    const batch = writeBatch(db);
    batch.update(doc(db, `invites/${CODE}`), { status: 'used', usedBy: 'carol' });
    batch.update(doc(db, 'trips/t1'), { memberIds: arrayUnion('carol') });
    batch.set(doc(db, 'trips/t1/members/carol'), member('Carol', CODE));
    await assertFails(batch.commit());
    const owner = as('alice');
    const ownerBatch = writeBatch(owner);
    ownerBatch.update(doc(owner, `invites/${CODE}`), { status: 'used', usedBy: 'carol' });
    ownerBatch.update(doc(owner, 'trips/t1'), { memberIds: arrayUnion('carol') });
    await assertFails(ownerBatch.commit());
    // Adding myself and someone else in one go fails as well.
    const both = writeBatch(db);
    both.update(doc(db, `invites/${CODE}`), { status: 'used', usedBy: 'bob' });
    both.update(doc(db, 'trips/t1'), { memberIds: arrayUnion('bob', 'carol') });
    both.set(doc(db, 'trips/t1/members/bob'), member('Bob', CODE));
    await assertFails(both.commit());
    await expectMembers('t1', ['alice']);
  });

  it('can not change anything else of the trip while joining, or join before the owner', async () => {
    const db = as('bob');
    const batch = writeBatch(db);
    batch.update(doc(db, `invites/${CODE}`), { status: 'used', usedBy: 'bob' });
    batch.update(doc(db, 'trips/t1'), { memberIds: arrayUnion('bob'), destination: 'Hacked' });
    batch.set(doc(db, 'trips/t1/members/bob'), member('Bob', CODE));
    await assertFails(batch.commit());
    const first = writeBatch(db);
    first.update(doc(db, `invites/${CODE}`), { status: 'used', usedBy: 'bob' });
    first.update(doc(db, 'trips/t1'), { memberIds: ['bob', 'alice'] });
    first.set(doc(db, 'trips/t1/members/bob'), member('Bob', CODE));
    await assertFails(first.commit());
  });

  it('a trip never has more than 10 members', async () => {
    // The owner made two codes while the trip had 9 members; the first one fills it.
    const nine = ['alice', ...Array.from({ length: 8 }, (_, i) => `m${i}`)];
    await seed({ 'trips/t1': trip('alice', nine) });
    await assertSucceeds(createInvite('alice', CODE2));
    await assertSucceeds(join('bob')); // the largest join: into a trip of 9 members
    await expectMembers('t1', [...nine, 'bob']);
    // The second code is still open (the app then says "this trip already has 10 members"), but joining fails.
    await assertSucceeds(getDoc(doc(as('carol'), `invites/${CODE2}`)));
    await assertFails(join('carol', CODE2));
  });

  it('a member document with an extra field such as an email is rejected', async () => {
    const db = as('bob');
    const batch = writeBatch(db);
    batch.update(doc(db, `invites/${CODE}`), { status: 'used', usedBy: 'bob' });
    batch.update(doc(db, 'trips/t1'), { memberIds: arrayUnion('bob') });
    batch.set(doc(db, 'trips/t1/members/bob'), member('Bob', CODE, { email: 'bob@example.com' }));
    await assertFails(batch.commit());
    await assertFails(setDoc(doc(as('alice'), 'trips/t1/members/alice'), member('Alice', null, { email: 'alice@example.com' })));
  });

  it('member documents are validated and read only by members', async () => {
    await assertFails(setDoc(doc(as('alice'), 'trips/t1/members/alice'), member('')));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1/members/alice'), member('x'.repeat(101))));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1/members/alice'), member('Alice', null, { joinedAt: Timestamp.fromMillis(1) })));
    await assertFails(setDoc(doc(as('alice'), 'trips/t1/members/bob'), member('Bob')));
    await assertFails(getDocs(collection(as('mallory'), 'trips/t1/members')));
    await assertFails(getDoc(doc(as('mallory'), 'trips/t1/members/alice')));
    await assertFails(deleteDoc(doc(as('alice'), 'trips/t1/members/alice')));
  });
});

describe('leaving and being removed', () => {
  beforeEach(async () => {
    await seed({
      'trips/t1': trip('alice'),
      'trips/t1/expenses/e1': expense(),
    });
    await createInvite();
    await assertSucceeds(join('bob'));
    await seed({
      'trips/t1/memberData/bob': budget(),
      'trips/t1/memberData/bob/packing/p1': checklistItem('PACKING'),
    });
  });

  it('a member leaves in one batch with their budget and Packing list', async () => {
    await assertSucceeds(leave('bob', 't1', ['p1']));
    await expectMembers('t1', ['alice']);
    await assertFails(getDoc(doc(as('bob'), 'trips/t1')));
  });

  it('leaving with the largest Packing list fits in one batch', async () => {
    const ids = Array.from({ length: 446 }, (_, i) => `p${i}`);
    await env.withSecurityRulesDisabled(async (ctx) => {
      const batch = writeBatch(ctx.firestore());
      for (const id of ids) batch.set(doc(ctx.firestore(), `trips/t1/memberData/bob/packing/${id}`), checklistItem('PACKING'));
      await batch.commit();
    });
    await assertSucceeds(leave('bob', 't1', ids));
  });

  it('leaving without deleting the budget, or without marking the member document, fails', async () => {
    const db = as('bob');
    const noBudget = writeBatch(db);
    noBudget.update(doc(db, 'trips/t1'), { memberIds: arrayRemove('bob') });
    noBudget.update(doc(db, 'trips/t1/members/bob'), { left: true });
    await assertFails(noBudget.commit());
    const notLeft = writeBatch(db);
    notLeft.update(doc(db, 'trips/t1'), { memberIds: arrayRemove('bob') });
    notLeft.delete(doc(db, 'trips/t1/memberData/bob'));
    await assertFails(notLeft.commit());
  });

  it('a member can not mark someone else as left, nor themselves while staying', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1/members/alice'), { left: true }));
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1/members/bob'), { left: true }));
  });

  it('the owner removes a member and marks them as left', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'trips/t1/members/bob'), { left: true }));
    await assertFails(updateDoc(doc(as('alice'), 'trips/t1/members/bob'), { name: 'Robert' }));
    await assertSucceeds(removeMember('alice', 'bob'));
    await expectMembers('t1', ['alice']);
  });

  it('a removed member can not read or write the trip any more', async () => {
    await assertSucceeds(removeMember('alice', 'bob'));
    const db = as('bob');
    await assertFails(getDoc(doc(db, 'trips/t1')));
    await assertFails(getDoc(doc(db, 'trips/t1/expenses/e1')));
    await assertFails(getDocs(collection(db, 'trips/t1/expenses')));
    await assertFails(getDocs(collection(db, 'trips/t1/members')));
    await assertFails(setDoc(doc(db, 'trips/t1/expenses/e2'), expense({ paidBy: 'bob' })));
    await assertFails(setDoc(doc(db, 'trips/t1/expenses/e1'), tombstone));
    await assertFails(setDoc(doc(db, 'trips/t1'), { destination: 'Oslo', updatedAt: 5 }, { merge: true }));
    await assertFails(setDoc(doc(db, 'trips/t1/memberData/bob'), budget({ updatedAt: 3 })));
    // ...but cleans up their own personal data on their next sync.
    await assertSucceeds(getDocs(collection(db, 'trips/t1/memberData/bob/packing')));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/memberData/bob/packing/p1')));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/memberData/bob')));
  });

  it('a removed member can not rejoin with their old code, or by adding themselves', async () => {
    await assertSucceeds(removeMember('alice', 'bob'));
    await assertFails(join('bob'));
    await assertFails(updateDoc(doc(as('bob'), 'trips/t1'), { memberIds: arrayUnion('bob') }));
    const db = as('bob');
    const batch = writeBatch(db);
    batch.update(doc(db, 'trips/t1'), { memberIds: arrayUnion('bob') });
    batch.set(doc(db, 'trips/t1/members/bob'), member('Bob', CODE));
    await assertFails(batch.commit());
    await expectMembers('t1', ['alice']);
  });

  it('a new invite brings a removed member back', async () => {
    await assertSucceeds(removeMember('alice', 'bob'));
    await assertSucceeds(createInvite('alice', CODE2));
    await assertSucceeds(join('bob', CODE2));
    await expectMembers('t1', ['alice', 'bob']);
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
