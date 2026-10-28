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

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: process.env.GCLOUD_PROJECT || 'demo-es-career-guide',
    firestore: { rules: readFileSync(resolve(root, 'firestore.rules'), 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

// ---- Identities ------------------------------------------------------------

const anonymous = () => env.unauthenticatedContext();
const alice = () => env.authenticatedContext('alice-uid', { email: 'alice@example.com' });
const bob = () => env.authenticatedContext('bob-uid', { email: 'bob@example.com' });

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
