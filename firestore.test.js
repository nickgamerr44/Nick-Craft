const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
  serverTimestamp
} = require('firebase/firestore');

const PROJECT_ID = 'gen-lang-client-0533985160';
const DATABASE_ID = 'ai-studio-android-craftjav-6e425379-0e74-4e3e-8426-ff0352412cbe';

let testEnv;

function makeServerPayload(hostUid, overrides = {}) {
  return {
    serverCode: 'NC4829',
    worldName: 'Mundo Sobrevivencia',
    hostUid,
    hostName: 'Nick',
    gameMode: 'SURVIVAL',
    seed: 12345,
    maxPlayers: 5,
    memberUids: [hostUid],
    isOpen: true,
    blocksBase64: 'H4sIAAAAAAAA/w==',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides
  };
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: '127.0.0.1',
      port: 8085,
      rules: fs.readFileSync('firestore.rules', 'utf8'),
      databaseId: DATABASE_ID
    }
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

test('unauthenticated caller cannot read or write world_servers', async () => {
  await testEnv.clearFirestore();
  const anonDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(getDoc(doc(anonDb, 'world_servers', 'NC4829')));
  await assertFails(setDoc(doc(anonDb, 'world_servers', 'NC4829'), makeServerPayload('u1')));
  await assertFails(getDocs(query(collection(anonDb, 'world_servers'), where('isOpen', '==', true))));
});

test('host creates server with maxPlayers=5 and up to 5 players can join; 6th player is rejected', async () => {
  await testEnv.clearFirestore();
  const hostDb = testEnv.authenticatedContext('p1').firestore(DATABASE_ID);
  const serverRef = doc(hostDb, 'world_servers', 'NC4829');

  // Cannot create server with maxPlayers != 5
  await assertFails(setDoc(serverRef, makeServerPayload('p1', { maxPlayers: 10 })));

  // Valid host creation
  await assertSucceeds(setDoc(serverRef, makeServerPayload('p1')));

  // Players p2, p3, p4, p5 join sequentially
  for (const [idx, uid] of ['p2', 'p3', 'p4', 'p5'].entries()) {
    const playerDb = testEnv.authenticatedContext(uid).firestore(DATABASE_ID);
    const snap = await getDoc(doc(playerDb, 'world_servers', 'NC4829'));
    const currentMembers = snap.data().memberUids;
    assert.equal(currentMembers.length, idx + 1);
    await assertSucceeds(updateDoc(doc(playerDb, 'world_servers', 'NC4829'), {
      memberUids: [...currentMembers, uid],
      updatedAt: serverTimestamp()
    }));
  }

  // 6th player (p6) MUST be rejected by Firestore rules
  const p6Db = testEnv.authenticatedContext('p6').firestore(DATABASE_ID);
  const snapFull = await getDoc(doc(p6Db, 'world_servers', 'NC4829'));
  await assertFails(updateDoc(doc(p6Db, 'world_servers', 'NC4829'), {
    memberUids: [...snapFull.data().memberUids, 'p6'],
    updatedAt: serverTimestamp()
  }));

  // Non-member cannot write player pose or block edit
  await assertFails(setDoc(doc(p6Db, 'world_servers', 'NC4829', 'players', 'p6'), {
    uid: 'p6', displayName: 'Intruder', x: 24, y: 12, z: 24, yaw: 0, pitch: 0, slotIndex: 4, updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(p6Db, 'world_servers', 'NC4829', 'block_edits', 'edit1'), {
    x: 10, y: 10, z: 10, blockId: 1, authorUid: 'p6', createdAt: serverTimestamp()
  }));

  // Valid member p2 can update own player pose & create block edit, but cannot spoof p3
  const p2Db = testEnv.authenticatedContext('p2').firestore(DATABASE_ID);
  await assertSucceeds(setDoc(doc(p2Db, 'world_servers', 'NC4829', 'players', 'p2'), {
    uid: 'p2', displayName: 'Player 2', x: 24.5, y: 12.0, z: 24.5, yaw: 45.0, pitch: 0.0, slotIndex: 1, updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(p2Db, 'world_servers', 'NC4829', 'players', 'p3'), {
    uid: 'p2', displayName: 'Spoof', x: 24.5, y: 12.0, z: 24.5, yaw: 0, pitch: 0, slotIndex: 2, updatedAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(p2Db, 'world_servers', 'NC4829', 'block_edits', 'edit_ok'), {
    x: 10, y: 10, z: 10, blockId: 2, authorUid: 'p2', createdAt: serverTimestamp()
  }));

  // p5 leaves cleanly, opening 5th slot again
  const p5Db = testEnv.authenticatedContext('p5').firestore(DATABASE_ID);
  await assertSucceeds(updateDoc(doc(p5Db, 'world_servers', 'NC4829'), {
    memberUids: ['p1', 'p2', 'p3', 'p4'],
    updatedAt: serverTimestamp()
  }));
});

test('non-host cannot delete or close server', async () => {
  await testEnv.clearFirestore();
  const hostDb = testEnv.authenticatedContext('p1').firestore(DATABASE_ID);
  await assertSucceeds(setDoc(doc(hostDb, 'world_servers', 'NC4829'), makeServerPayload('p1')));

  const p2Db = testEnv.authenticatedContext('p2').firestore(DATABASE_ID);
  await assertFails(updateDoc(doc(p2Db, 'world_servers', 'NC4829'), {
    isOpen: false,
    updatedAt: serverTimestamp()
  }));
  await assertFails(deleteDoc(doc(p2Db, 'world_servers', 'NC4829')));
  await assertSucceeds(deleteDoc(doc(hostDb, 'world_servers', 'NC4829')));
});
