# Security-rules tests

Emulator tests for `../firestore.rules` and `../database.rules.json`, written
with `@firebase/rules-unit-testing` and Node's built-in test runner.

They run only against the local Firestore and Realtime Database emulators,
under the `demo-es-career-guide` project id. Firebase never connects a `demo-`
project to a real one, so these tests cannot read, write or deploy anything
live.

## Run

Requires Node 20+ and Java 11+ (for the emulators).

```bash
cd firebase-rules-tests
npm ci
npm test
```

`npm test` is `firebase emulators:exec` with the pinned `firebase-tools` from
`package.json`. The equivalent `npx` command, from the repository root:

```bash
npx --prefix firebase-rules-tests firebase-tools emulators:exec --only firestore,database --project demo-es-career-guide "node --test firebase-rules-tests/rules.test.mjs"
```

Every test loads the rules files from the repository root, so a test run
always checks the rules that would be deployed.

## What is covered

Each finding is tested from both sides: the attack is denied, and every
request the current app makes for that data is still allowed. The app flows
are named after the screen that makes them.
