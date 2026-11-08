// Security rules tests. These need the Firestore emulator, which needs Java 11+.
// Run with: npm run test:rules
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment, assertSucceeds, assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, runTransaction,
  serverTimestamp, Timestamp, GeoPoint, increment,
} = require('firebase/firestore');

const H = 60 * 60 * 1000;
let env;

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-cuacaum',
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') },
  });
});

test.after(async () => {
  await env.cleanup();
});

test.beforeEach(async () => {
  await env.clearFirestore();
});

// Signed in with Google unless guest = true (Firebase anonymous sign-in).
const db = (uid, { guest = false } = {}) => (uid
  ? env.authenticatedContext(uid, { firebase: { sign_in_provider: guest ? 'anonymous' : 'google.com' } }).firestore()
  : env.unauthenticatedContext().firestore());

function newUser(overrides = {}) {
  return {
    displayName: 'Alice',
    email: 'alice@siswa.um.edu.my',
    isGuest: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    usualLocations: ['KK8'],
    health: { asthma: true, heatSensitive: false, uvSensitive: false, mobilityLimited: false },
    settings: { language: 'en', textSize: 'normal' },
    alertPrefs: { rain: true, heat: true, haze: true, rainLeadMinutes: 60 },
    fcmTokens: [],
    ...overrides,
  };
}

function newReport(uid, overrides = {}) {
  return {
    category: 'flood',
    description: 'Ankle-deep water',
    photoPath: null,
    location: new GeoPoint(3.1209, 101.6538),
    nearestEdgeId: 'KK8-BUSSTOP3',
    reportedBy: uid,
    createdAt: serverTimestamp(),
    expiresAt: Timestamp.fromMillis(Date.now() + 2 * H - 60 * 1000),
    confirmCount: 0,
    ...overrides,
  };
}

/** Puts a report straight into the database, skipping the rules. */
async function seedReport(id, data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'hazard_reports', id), data);
  });
}

function existingReport(overrides = {}) {
  const now = Date.now();
  return {
    category: 'flood',
    location: new GeoPoint(3.1209, 101.6538),
    reportedBy: 'alice',
    createdAt: Timestamp.fromMillis(now - 10 * 60 * 1000),
    expiresAt: Timestamp.fromMillis(now + H),
    confirmCount: 0,
    ...overrides,
  };
}

/** What the app does when someone confirms a report. */
function confirm(firestore, reportId, uid, { addHours = 1, skipConfirmationDoc = false } = {}) {
  return runTransaction(firestore, async (tx) => {
    const reportRef = doc(firestore, 'hazard_reports', reportId);
    const snap = await tx.get(reportRef);
    const exp = snap.data().expiresAt.toMillis();
    tx.update(reportRef, {
      confirmCount: increment(1),
      expiresAt: Timestamp.fromMillis(exp + addHours * H),
    });
    if (!skipConfirmationDoc) {
      tx.set(doc(firestore, 'hazard_reports', reportId, 'confirmations', uid), { confirmedAt: serverTimestamp() });
    }
  });
}

// ---------- users ----------

test('users: can create and read your own profile', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), 'users', 'alice'), newUser()));
  await assertSucceeds(getDoc(doc(db('alice'), 'users', 'alice')));
});

test('users: guest profile with null email is allowed', async () => {
  await assertSucceeds(setDoc(doc(db('guest1'), 'users', 'guest1'),
    newUser({ displayName: 'Guest', email: null, isGuest: true })));
});

test("users: can't read or write someone else's profile", async () => {
  await assertFails(setDoc(doc(db('bob'), 'users', 'alice'), newUser()));
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'users', 'alice'), newUser()));
  await assertFails(getDoc(doc(db('bob'), 'users', 'alice')));
});

test('users: signed-out users are blocked', async () => {
  await assertFails(getDoc(doc(db(null), 'users', 'alice')));
});

test('users: rejects unknown fields and bad settings', async () => {
  await assertFails(setDoc(doc(db('alice'), 'users', 'alice'), newUser({ role: 'admin' })));
  await assertFails(setDoc(doc(db('alice'), 'users', 'alice'),
    newUser({ settings: { language: 'fr', textSize: 'normal' } })));
});

// ---------- hazard_reports ----------

test('reports: signed-in user can create a valid report', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), 'hazard_reports', 'r1'), newReport('alice')));
});

test("reports: can't report as someone else", async () => {
  await assertFails(setDoc(doc(db('alice'), 'hazard_reports', 'r1'), newReport('bob')));
});

test("reports: can't start with confirmations or last more than 2 hours", async () => {
  await assertFails(setDoc(doc(db('alice'), 'hazard_reports', 'r1'), newReport('alice', { confirmCount: 3 })));
  await assertFails(setDoc(doc(db('alice'), 'hazard_reports', 'r1'),
    newReport('alice', { expiresAt: Timestamp.fromMillis(Date.now() + 5 * H) })));
});

test('reports: invalid category or long description is rejected', async () => {
  await assertFails(setDoc(doc(db('alice'), 'hazard_reports', 'r1'), newReport('alice', { category: 'tsunami' })));
  await assertFails(setDoc(doc(db('alice'), 'hazard_reports', 'r1'),
    newReport('alice', { description: 'x'.repeat(201) })));
});

test('reports: createdAt must be the server time', async () => {
  await assertFails(setDoc(doc(db('alice'), 'hazard_reports', 'r1'),
    newReport('alice', { createdAt: Timestamp.fromMillis(Date.now() - 3 * H) })));
});

test('reports: any signed-in user can read, signed-out users cannot', async () => {
  await seedReport('r1', existingReport());
  await assertSucceeds(getDoc(doc(db('bob'), 'hazard_reports', 'r1')));
  await assertFails(getDoc(doc(db(null), 'hazard_reports', 'r1')));
});

test('confirm: another user can confirm once (+1 hour)', async () => {
  await seedReport('r1', existingReport());
  await assertSucceeds(confirm(db('bob'), 'r1', 'bob'));
});

test("confirm: the same user can't confirm twice", async () => {
  await seedReport('r1', existingReport());
  await assertSucceeds(confirm(db('bob'), 'r1', 'bob'));
  await assertFails(confirm(db('bob'), 'r1', 'bob'));
});

test("confirm: reporter can't confirm their own report", async () => {
  await seedReport('r1', existingReport());
  await assertFails(confirm(db('alice'), 'r1', 'alice'));
});

test("confirm: can't add more than 1 hour", async () => {
  await seedReport('r1', existingReport());
  await assertFails(confirm(db('bob'), 'r1', 'bob', { addHours: 3 }));
});

test("confirm: can't go past 10 hours after the report was made", async () => {
  const created = Date.now() - 9.5 * H;
  await seedReport('r1', existingReport({
    createdAt: Timestamp.fromMillis(created),
    expiresAt: Timestamp.fromMillis(created + 9.8 * H),
  }));
  await assertFails(confirm(db('bob'), 'r1', 'bob'));
});

test("confirm: can't confirm an expired report", async () => {
  await seedReport('r1', existingReport({ expiresAt: Timestamp.fromMillis(Date.now() - 60 * 1000) }));
  await assertFails(confirm(db('bob'), 'r1', 'bob'));
});

test('confirm: must create the confirmation doc in the same transaction', async () => {
  await seedReport('r1', existingReport());
  await assertFails(confirm(db('bob'), 'r1', 'bob', { skipConfirmationDoc: true }));
});

test('confirm: confirmation doc alone (without +1) is rejected', async () => {
  await seedReport('r1', existingReport());
  await assertFails(setDoc(doc(db('bob'), 'hazard_reports', 'r1', 'confirmations', 'bob'),
    { confirmedAt: serverTimestamp() }));
});

test("reports: only the reporter can delete, and other fields can't be edited", async () => {
  await seedReport('r1', existingReport());
  await assertFails(updateDoc(doc(db('bob'), 'hazard_reports', 'r1'), { category: 'other' }));
  await assertFails(deleteDoc(doc(db('bob'), 'hazard_reports', 'r1')));
  await assertSucceeds(deleteDoc(doc(db('alice'), 'hazard_reports', 'r1')));
});

// ---------- tips_cache ----------

function newTip(overrides = {}) {
  return {
    states: ['rain'],
    timeSlot: 'afternoon',
    sensitivities: [],
    text: 'Bring an umbrella.',
    source: 'ai',
    model: 'gemini (Firebase AI Logic)',
    createdAt: serverTimestamp(),
    expiresAt: Timestamp.fromMillis(Date.now() + H - 60 * 1000),
    ...overrides,
  };
}

test('tips: signed-in user can save and read a tip', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), 'tips_cache', 'rain_afternoon_none'), newTip()));
  await assertSucceeds(getDoc(doc(db('bob'), 'tips_cache', 'rain_afternoon_none')));
});

test('tips: guests can read but not write tips', async () => {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'tips_cache', 'rain_afternoon_none'), {
    ...newTip(), createdAt: Timestamp.now(),
  }));
  await assertSucceeds(getDoc(doc(db('guest1', { guest: true }), 'tips_cache', 'rain_afternoon_none')));
  await assertFails(setDoc(doc(db('guest1', { guest: true }), 'tips_cache', 'rain_afternoon_none'), newTip()));
});

test("tips: the document ID must match the tip's states, time slot and sensitivities", async () => {
  await assertFails(setDoc(doc(db('alice'), 'tips_cache', 'heat_morning_none'), newTip()));
  await assertSucceeds(setDoc(doc(db('alice'), 'tips_cache', 'haze_sensitive+rain_evening_asthma'),
    newTip({ states: ['haze_sensitive', 'rain'], timeSlot: 'evening', sensitivities: ['asthma'] })));
});

test('tips: signed-out users are blocked', async () => {
  await assertFails(setDoc(doc(db(null), 'tips_cache', 'rain_afternoon_none'), newTip()));
});

test('tips: rejects bad source, long text, or cache longer than 1 hour', async () => {
  await assertFails(setDoc(doc(db('alice'), 'tips_cache', 'rain_afternoon_none'), newTip({ source: 'human' })));
  await assertFails(setDoc(doc(db('alice'), 'tips_cache', 'rain_afternoon_none'), newTip({ text: 'x'.repeat(301) })));
  await assertFails(setDoc(doc(db('alice'), 'tips_cache', 'rain_afternoon_none'),
    newTip({ expiresAt: Timestamp.fromMillis(Date.now() + 3 * H) })));
});

test("tips: can't delete", async () => {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'tips_cache', 'k'), {
    ...newTip(), createdAt: Timestamp.now(),
  }));
  await assertFails(deleteDoc(doc(db('alice'), 'tips_cache', 'k')));
});

// ---------- everything else ----------

test('unknown collections are blocked', async () => {
  await assertFails(setDoc(doc(db('alice'), 'secrets', 'x'), { a: 1 }));
});
