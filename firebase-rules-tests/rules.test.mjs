// Security-rules tests for firestore.rules and database.rules.json.
//
// Run against the local emulators only, under a demo- project id, so nothing
// here can reach the live project. See README.md in this directory.
//
// Each finding is covered from both sides: the attack is denied, and every
// flow the current app depends on is still allowed.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// The shared contestant login (docs/PRE_RELEASE_EVENT_CHALLENGE.md) and an admin.
const EVENT_EMAIL = 'exam1234@challenge.app';
const ROLL = '160122735116';
const OTHER_ROLL = '160122735117';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: process.env.GCLOUD_PROJECT || 'demo-es-career-guide',
    firestore: { rules: readFileSync(resolve(root, 'firestore.rules'), 'utf8') },
    database: { rules: readFileSync(resolve(root, 'database.rules.json'), 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.clearDatabase();
});

// ---- Identities ------------------------------------------------------------

const anonymous = () => env.unauthenticatedContext();
const alice = () => env.authenticatedContext('alice-uid', { email: 'alice@example.com' });
const bob = () => env.authenticatedContext('bob-uid', { email: 'bob@example.com' });
// Any ordinary app account: a student who registered through RegisterActivity.
const student = () => env.authenticatedContext('student-uid', { email: 'student@example.com' });
const contestant = () => env.authenticatedContext('event-uid', { email: EVENT_EMAIL });
const admin = () => env.authenticatedContext('admin-uid', { email: 'admin@challenge.app', admin: true });

// ---- Seed data ---------------------------------------------------------------

async function seedFirestore() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc('usernames/alice').set({ uid: 'alice-uid', createdAt: 1 });
    await db.doc('usernames/bob').set({ uid: 'bob-uid', createdAt: 1 });
    await db.doc('users/alice').set({
      uid: 'alice-uid', username: 'alice', email: 'alice@example.com', displayName: 'Alice',
    });
    await db.doc('users/bob').set({
      uid: 'bob-uid', username: 'bob', email: 'bob@example.com', displayName: 'Bob',
    });
    await db.doc('users/alice/data/progress').set({ totalXP: 100 });
  });
}

async function seedEvent() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.database();
    for (const roll of [ROLL, OTHER_ROLL]) {
      await db.ref(`preReleaseEvent/participants/${roll}`).set({
        profile: { rollNumber: roll, registeredAt: 1 },
        status: { currentStatus: 'in_progress', warningCount: 0, isTerminated: false },
        challenge2: { generatedQuestions: '[{"q":"secret"}]', savedState: '{"a":"answer"}' },
        universalRanking: { challenge1Score: 40, totalWeightedScore: 40 },
      });
      await db.ref(`preReleaseEvent/rankings/universal/${roll}`).set({ rollNumber: roll, totalScore: 40, rank: 1 });
    }
    await db.ref('preReleaseEvent/config').set({ eventActive: true });
  });
}

// ---- Finding 1: unauthenticated listing of every user's email --------------

describe('Finding 1 - user emails cannot be listed', () => {
  beforeEach(seedFirestore);

  test('attack: a signed-out client cannot list users', async () => {
    await assertFails(anonymous().firestore().collection('users').get());
  });

  test('attack: a signed-out client cannot list usernames (to then get each profile)', async () => {
    await assertFails(anonymous().firestore().collection('usernames').get());
  });

  test('attack: a signed-in user cannot list every profile', async () => {
    await assertFails(alice().firestore().collection('users').get());
  });

  test("attack: a signed-in user cannot query for someone else's email", async () => {
    await assertFails(alice().firestore().collection('users').where('email', '==', 'bob@example.com').get());
  });

  test("attack: a signed-in user cannot query someone else's username mapping", async () => {
    await assertFails(alice().firestore().collection('usernames').where('uid', '==', 'bob-uid').get());
  });

  test('app: RegisterActivity checks a username is free before sign-in', async () => {
    await assertSucceeds(anonymous().firestore().doc('usernames/carol').get());
    await assertSucceeds(anonymous().firestore().doc('usernames/alice').get());
  });

  test('app: LoginActivity resolves a username to its email before sign-in', async () => {
    await assertSucceeds(anonymous().firestore().doc('usernames/alice').get());
    await assertSucceeds(anonymous().firestore().doc('users/alice').get());
  });

  test('app: LoginActivity finds the signed-in user\'s own username by uid', async () => {
    await assertSucceeds(alice().firestore().collection('usernames').where('uid', '==', 'alice-uid').get());
  });

  test('app: LoginActivity recovers the signed-in user\'s own profile by email', async () => {
    await assertSucceeds(alice().firestore().collection('users').where('email', '==', 'alice@example.com').get());
  });

  test('app: registration claims a username and creates the profile', async () => {
    const carol = env.authenticatedContext('carol-uid', { email: 'carol@example.com' }).firestore();
    const batch = carol.batch();
    batch.set(carol.doc('usernames/carol'), { uid: 'carol-uid', createdAt: 1 });
    batch.set(carol.doc('users/carol'), { uid: 'carol-uid', username: 'carol', email: 'carol@example.com' });
    await assertSucceeds(batch.commit());
  });

  test('app: a user updates their own profile and reads their own progress', async () => {
    await assertSucceeds(alice().firestore().doc('users/alice').update({ lastLogin: 2 }));
    await assertSucceeds(alice().firestore().doc('users/alice/data/progress').get());
    await assertFails(bob().firestore().doc('users/alice/data/progress').get());
  });
});

// ---- Finding 2: any signed-in account rewriting scores, status, rankings ----

describe('Finding 2 - only the event account and admins write event data', () => {
  beforeEach(seedEvent);

  test("attack: an ordinary student account cannot rewrite a participant's score", async () => {
    await assertFails(
      student().database().ref(`preReleaseEvent/participants/${ROLL}/universalRanking/challenge1Score`).set(100),
    );
  });

  test("attack: an ordinary student account cannot change a participant's status", async () => {
    await assertFails(
      student().database().ref(`preReleaseEvent/participants/${ROLL}/status`).update({ isTerminated: true }),
    );
  });

  test('attack: an ordinary student account cannot rewrite the rankings', async () => {
    await assertFails(
      student().database().ref(`preReleaseEvent/rankings/universal/${ROLL}`).update({ totalScore: 999, rank: 1 }),
    );
  });

  test('attack: a signed-out client cannot write event data', async () => {
    await assertFails(anonymous().database().ref(`preReleaseEvent/participants/${ROLL}/status/warningCount`).set(0));
    await assertFails(anonymous().database().ref('preReleaseEvent/rankings/universal').set(null));
  });

  test('attack: an ordinary student account cannot open the event', async () => {
    await assertFails(student().database().ref('preReleaseEvent/config/eventActive').set(true));
  });

  test('app: the event account registers, starts, submits and saves state', async () => {
    const db = contestant().database();
    await assertSucceeds(db.ref('preReleaseEvent/participants/160122735999').update({
      'profile/rollNumber': '160122735999',
      'status/currentStatus': 'waiting',
      'universalRanking/totalWeightedScore': 0,
    }));
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}`).update({
      'status/currentStatus': 'in_progress',
      'challenge1/status': 'in_progress',
      'challenge1/startTime': 5,
    }));
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}/challenge2`).update({
      savedState: '{}', savedTimeRemainingMs: 1000, savedProblemIndex: 1,
    }));
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}/status/warningCount`).set(1));
  });

  test('app: the event account recomputes and writes the rankings after a submit', async () => {
    const db = contestant().database();
    // PreReleaseEventService.updateUniversalRankings: read every participant,
    // then one multi-path update from the event root.
    await assertSucceeds(db.ref('preReleaseEvent/participants').get());
    await assertSucceeds(db.ref('preReleaseEvent').update({
      [`rankings/universal/${ROLL}`]: { rollNumber: ROLL, totalScore: 40, rank: 1 },
      [`participants/${ROLL}/universalRanking/rank`]: 1,
      [`rankings/universal/${OTHER_ROLL}`]: { rollNumber: OTHER_ROLL, totalScore: 40, rank: 2 },
      [`participants/${OTHER_ROLL}/universalRanking/rank`]: 2,
    }));
    // RankingDashboardActivity observes the rankings.
    await assertSucceeds(db.ref('preReleaseEvent/rankings/universal').get());
  });

  test('app: an admin manages participants and the event', async () => {
    const db = admin().database();
    await assertSucceeds(db.ref('preReleaseEvent/participants').get());
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}/status/canAccessChallenge2`).set(true));
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}/challenge2/extraTimeGrantedMs`).set(60000));
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${OTHER_ROLL}`).remove());
    await assertSucceeds(db.ref(`preReleaseEvent/rankings/universal/${OTHER_ROLL}`).remove());
    await assertSucceeds(db.ref('preReleaseEvent/config/eventActive').set(false));
  });

  test('app: any signed-in account can still read whether the event is active', async () => {
    await assertSucceeds(contestant().database().ref('preReleaseEvent/config').get());
    await assertSucceeds(student().database().ref('preReleaseEvent/config').get());
  });
});

// ---- Finding 3: reading other contestants' questions and answers ------------

describe("Finding 3 - event data is not readable outside the event", () => {
  beforeEach(seedEvent);

  test("attack: an ordinary student account cannot read a contestant's questions or answers", async () => {
    await assertFails(student().database().ref(`preReleaseEvent/participants/${ROLL}/challenge2`).get());
    await assertFails(student().database().ref('preReleaseEvent/participants').get());
  });

  test('attack: an ordinary student account cannot read roll numbers off the rankings', async () => {
    await assertFails(student().database().ref('preReleaseEvent/rankings/universal').get());
  });

  test('attack: a signed-out client cannot read event data', async () => {
    await assertFails(anonymous().database().ref('preReleaseEvent/participants').get());
    await assertFails(anonymous().database().ref('preReleaseEvent/rankings/universal').get());
  });

  test('app: the event account loads its saved questions and state on resume', async () => {
    const db = contestant().database();
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}/challenge2/generatedQuestions`).get());
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}/challenge2`).get());
    await assertSucceeds(db.ref(`preReleaseEvent/participants/${ROLL}/status`).get());
  });

  // Every contestant signs in as the same Firebase account and identifies
  // themselves only by typing a roll number, so no rule can tell one contestant
  // from another. Closing this needs per-contestant sign-in (see the PR). This
  // test pins the gap so it is visible, and must flip to assertFails when that
  // lands.
  test("KNOWN GAP: one contestant can still read another contestant's answers", async () => {
    await assertSucceeds(
      contestant().database().ref(`preReleaseEvent/participants/${OTHER_ROLL}/challenge2/savedState`).get(),
    );
  });
});
