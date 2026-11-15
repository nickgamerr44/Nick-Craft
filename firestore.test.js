const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');

const PROJECT_ID = 'gen-lang-client-0533985160';
const DATABASE_ID = 'ai-studio-android-craftjav-6e425379-0e74-4e3e-8426-ff0352412cbe';

let testEnv;

function tsNow() {
  return new Date(Date.now() - 1000);
}

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
    createdAt: tsNow(),
    updatedAt: tsNow(),
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
  await assertFails(anonDb.collection('world_servers').doc('NC4829').get());
  await assertFails(anonDb.collection('world_servers').doc('NC4829').set(makeServerPayload('u1')));
  await assertFails(anonDb.collection('world_servers').where('isOpen', '==', true).get());
});

test('host creates server with maxPlayers=5 and up to 5 players can join; 6th player is rejected', async () => {
  await testEnv.clearFirestore();
  const hostDb = testEnv.authenticatedContext('p1').firestore(DATABASE_ID);
  const serverRef = hostDb.collection('world_servers').doc('NC4829');

  // Cannot create server with maxPlayers != 5
  await assertFails(serverRef.set(makeServerPayload('p1', { maxPlayers: 10 })));

  // Valid host creation
  await assertSucceeds(serverRef.set(makeServerPayload('p1')));

  // Players p2, p3, p4, p5 join sequentially
  for (const [idx, uid] of ['p2', 'p3', 'p4', 'p5'].entries()) {
    const playerDb = testEnv.authenticatedContext(uid).firestore(DATABASE_ID);
    const snap = await playerDb.collection('world_servers').doc('NC4829').get();
    const currentMembers = snap.data().memberUids;
    assert.equal(currentMembers.length, idx + 1);
    await assertSucceeds(playerDb.collection('world_servers').doc('NC4829').update({
      memberUids: [...currentMembers, uid],
      updatedAt: tsNow()
    }));
  }

  // 6th player (p6) MUST be rejected by Firestore rules
  const p6Db = testEnv.authenticatedContext('p6').firestore(DATABASE_ID);
  const snapFull = await p6Db.collection('world_servers').doc('NC4829').get();
  await assertFails(p6Db.collection('world_servers').doc('NC4829').update({
    memberUids: [...snapFull.data().memberUids, 'p6'],
    updatedAt: tsNow()
  }));

  // Non-member cannot write player pose or block edit
  await assertFails(p6Db.collection('world_servers').doc('NC4829').collection('players').doc('p6').set({
    uid: 'p6', displayName: 'Intruder', x: 24, y: 12, z: 24, yaw: 0, pitch: 0, slotIndex: 4, updatedAt: tsNow()
  }));
  await assertFails(p6Db.collection('world_servers').doc('NC4829').collection('block_edits').doc('edit1').set({
    x: 10, y: 10, z: 10, blockId: 1, authorUid: 'p6', createdAt: tsNow()
  }));

  // Valid member p2 can update own player pose & create block edit, but cannot spoof p3
  const p2Db = testEnv.authenticatedContext('p2').firestore(DATABASE_ID);
  await assertSucceeds(p2Db.collection('world_servers').doc('NC4829').collection('players').doc('p2').set({
    uid: 'p2', displayName: 'Player 2', x: 24.5, y: 12.0, z: 24.5, yaw: 45.0, pitch: 0.0, slotIndex: 1, updatedAt: tsNow()
  }));
  await assertFails(p2Db.collection('world_servers').doc('NC4829').collection('players').doc('p3').set({
    uid: 'p2', displayName: 'Spoof', x: 24.5, y: 12.0, z: 24.5, yaw: 0, pitch: 0, slotIndex: 2, updatedAt: tsNow()
  }));
  await assertSucceeds(p2Db.collection('world_servers').doc('NC4829').collection('block_edits').doc('edit_ok').set({
    x: 10, y: 10, z: 10, blockId: 2, authorUid: 'p2', createdAt: tsNow()
  }));

  // p5 leaves cleanly, opening 5th slot again
  const p5Db = testEnv.authenticatedContext('p5').firestore(DATABASE_ID);
  await assertSucceeds(p5Db.collection('world_servers').doc('NC4829').update({
    memberUids: ['p1', 'p2', 'p3', 'p4'],
    updatedAt: tsNow()
  }));
});

test('non-host cannot delete or close server', async () => {
  await testEnv.clearFirestore();
  const hostDb = testEnv.authenticatedContext('p1').firestore(DATABASE_ID);
  await assertSucceeds(hostDb.collection('world_servers').doc('NC4829').set(makeServerPayload('p1')));

  const p2Db = testEnv.authenticatedContext('p2').firestore(DATABASE_ID);
  await assertFails(p2Db.collection('world_servers').doc('NC4829').update({
    isOpen: false,
    updatedAt: tsNow()
  }));
  await assertFails(p2Db.collection('world_servers').doc('NC4829').delete());
  await assertSucceeds(hostDb.collection('world_servers').doc('NC4829').delete());
});

test('server members can record PvP player_hits and update own health/death/respawn securely', async () => {
  await testEnv.clearFirestore();
  const p1Db = testEnv.authenticatedContext('p1').firestore(DATABASE_ID);
  await assertSucceeds(p1Db.collection('world_servers').doc('NC4829').set(makeServerPayload('p1')));

  const p2Db = testEnv.authenticatedContext('p2').firestore(DATABASE_ID);
  await assertSucceeds(p2Db.collection('world_servers').doc('NC4829').update({
    memberUids: ['p1', 'p2'],
    updatedAt: tsNow()
  }));

  // Valid PvP hit from p1 against p2
  await assertSucceeds(
    p1Db.collection('world_servers').doc('NC4829').collection('player_hits').doc('hit_1').set({
      attackerUid: 'p1',
      attackerName: 'Nick',
      targetUid: 'p2',
      damage: 7.0,
      createdAt: tsNow()
    })
  );

  // p2 reads player_hits and updates own health to 0 (dead), then respawns with animation fields
  await assertSucceeds(
    p2Db.collection('world_servers').doc('NC4829').collection('player_hits').get()
  );
  await assertSucceeds(
    p2Db.collection('world_servers').doc('NC4829').collection('players').doc('p2').set({
      uid: 'p2', displayName: 'Jogador2', x: 25.0, y: 12.0, z: 25.0, yaw: 0.0, pitch: 0.0,
      slotIndex: 1, health: 0.0, isDead: true, isWalking: false, swingSeq: 3, heldBlockId: 0, updatedAt: tsNow()
    })
  );
  await assertSucceeds(
    p2Db.collection('world_servers').doc('NC4829').collection('players').doc('p2').set({
      uid: 'p2', displayName: 'Jogador2', x: 24.5, y: 14.0, z: 24.5, yaw: 0.0, pitch: 0.0,
      slotIndex: 1, health: 20.0, isDead: false, isWalking: true, swingSeq: 4, heldBlockId: 5, updatedAt: tsNow()
    })
  );

  // Reject negative swingSeq, non-boolean isWalking, and invalid heldBlockId
  await assertFails(
    p2Db.collection('world_servers').doc('NC4829').collection('players').doc('p2').set({
      uid: 'p2', displayName: 'Jogador2', x: 24.5, y: 14.0, z: 24.5, yaw: 0.0, pitch: 0.0,
      slotIndex: 1, isWalking: true, swingSeq: -1, heldBlockId: 5, updatedAt: tsNow()
    })
  );
  await assertFails(
    p2Db.collection('world_servers').doc('NC4829').collection('players').doc('p2').set({
      uid: 'p2', displayName: 'Jogador2', x: 24.5, y: 14.0, z: 24.5, yaw: 0.0, pitch: 0.0,
      slotIndex: 1, isWalking: 'yes', swingSeq: 4, heldBlockId: 5, updatedAt: tsNow()
    })
  );
  await assertFails(
    p2Db.collection('world_servers').doc('NC4829').collection('players').doc('p2').set({
      uid: 'p2', displayName: 'Jogador2', x: 24.5, y: 14.0, z: 24.5, yaw: 0.0, pitch: 0.0,
      slotIndex: 1, isWalking: true, swingSeq: 4, heldBlockId: 999, updatedAt: tsNow()
    })
  );

  // Reject self-hit, spoofed attackerUid, invalid damage, mutation/deletion, and outsider hit
  await assertFails(
    p1Db.collection('world_servers').doc('NC4829').collection('player_hits').doc('self_hit').set({
      attackerUid: 'p1', attackerName: 'Nick', targetUid: 'p1', damage: 2.0, createdAt: tsNow()
    })
  );
  await assertFails(
    p2Db.collection('world_servers').doc('NC4829').collection('player_hits').doc('spoof_hit').set({
      attackerUid: 'p1', attackerName: 'Spoof', targetUid: 'p2', damage: 5.0, createdAt: tsNow()
    })
  );
  await assertFails(
    p1Db.collection('world_servers').doc('NC4829').collection('player_hits').doc('bad_dmg').set({
      attackerUid: 'p1', attackerName: 'Nick', targetUid: 'p2', damage: 99.0, createdAt: tsNow()
    })
  );
  await assertFails(
    p1Db.collection('world_servers').doc('NC4829').collection('player_hits').doc('hit_1').delete()
  );

  const outsiderDb = testEnv.authenticatedContext('p9').firestore(DATABASE_ID);
  await assertFails(
    outsiderDb.collection('world_servers').doc('NC4829').collection('player_hits').doc('outsider_hit').set({
      attackerUid: 'p9', attackerName: 'Intruder', targetUid: 'p2', damage: 4.0, createdAt: tsNow()
    })
  );
});
