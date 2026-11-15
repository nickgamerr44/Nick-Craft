package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MultiplayerServerRuleTest : FirestoreEmulatorTestBase() {

  private companion object {
    const val OP_TIMEOUT_MS = 8000L
  }

  private suspend fun assertPermissionDenied(block: suspend () -> Unit) {
    try {
      withTimeout(OP_TIMEOUT_MS) { block() }
      fail("Expected PERMISSION_DENIED")
    } catch (e: Throwable) {
      val fsEx = e.unwrapFirestoreException()
        ?: throw AssertionError("Expected FirebaseFirestoreException(PERMISSION_DENIED), got $e", e)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }
  }

  @Test
  fun serverCreationEnforces5PlayerLimitAndMembership(): Unit = runBlocking {
    val serverId = "RULETEST_${System.currentTimeMillis()}"
    val serverRef = db.collection("world_servers").document(serverId)

    // 1. Unauthenticated read must fail
    auth.signOut()
    assertPermissionDenied {
      serverRef.get().await()
    }

    // 2. Host creates server with maxPlayers = 5
    val hostEmail = "host_${System.currentTimeMillis()}@nickcraft.test"
    val hostUid = signInTestUser(hostEmail)
    val payload = hashMapOf(
      "serverCode" to serverId.take(12),
      "worldName" to "Mundo Teste",
      "hostUid" to hostUid,
      "hostName" to "HostNick",
      "gameMode" to "SURVIVAL",
      "seed" to 12345L,
      "maxPlayers" to 5,
      "memberUids" to listOf(hostUid),
      "isOpen" to true,
      "blocksBase64" to "H4sIAAAAAAAA/w==",
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    withTimeout(OP_TIMEOUT_MS) { serverRef.set(payload).await() }

    // 3. Players 2..5 join cleanly
    val joinedUids = mutableListOf(hostUid)
    val guestEmails = mutableListOf<String>()
    for (idx in 2..5) {
      val email = "guest${idx}_${System.currentTimeMillis()}@nickcraft.test"
      guestEmails.add(email)
      val guestUid = signInTestUser(email)
      joinedUids.add(guestUid)
      withTimeout(OP_TIMEOUT_MS) {
        serverRef.update(
          mapOf(
            "memberUids" to joinedUids.toList(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }
    }

    // 4. 6th player MUST be rejected by Firestore rules
    val sixthUid = signInTestUser("guest6_${System.currentTimeMillis()}@nickcraft.test")
    assertPermissionDenied {
      serverRef.update(
        mapOf(
          "memberUids" to (joinedUids + sixthUid),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // 5. Non-member 6th player cannot write player pose or block edit
    assertPermissionDenied {
      serverRef.collection("players").document(sixthUid).set(
        mapOf(
          "uid" to sixthUid,
          "displayName" to "Intruso",
          "x" to 24.5,
          "y" to 12.0,
          "z" to 24.5,
          "yaw" to 0.0,
          "pitch" to 0.0,
          "slotIndex" to 4,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // 6. Member 2 can write own player pose and create block edit
    val member2Uid = signInTestUser(guestEmails[0])
    withTimeout(OP_TIMEOUT_MS) {
      serverRef.collection("players").document(member2Uid).set(
        mapOf(
          "uid" to member2Uid,
          "displayName" to "Jogador2",
          "x" to 24.5,
          "y" to 12.0,
          "z" to 24.5,
          "yaw" to 15.0,
          "pitch" to 0.0,
          "slotIndex" to 1,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      serverRef.collection("block_edits").document("edit_rule_ok").set(
        mapOf(
          "x" to 12,
          "y" to 10,
          "z" to 12,
          "blockId" to 1,
          "authorUid" to member2Uid,
          "createdAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // 7. PvP hit from host to member2 & member2 health=0/respawn=20 updates
    signInTestUser(hostEmail)
    val hitDocId = "hit_${System.currentTimeMillis()}"
    withTimeout(OP_TIMEOUT_MS) {
      serverRef.collection("player_hits").document(hitDocId).set(
        mapOf(
          "attackerUid" to hostUid,
          "attackerName" to "HostNick",
          "targetUid" to member2Uid,
          "damage" to 7.0,
          "createdAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // Self-hit and excessive damage (>20) must be denied by rules
    assertPermissionDenied {
      serverRef.collection("player_hits").document("self_${System.currentTimeMillis()}").set(
        mapOf(
          "attackerUid" to hostUid,
          "attackerName" to "HostNick",
          "targetUid" to hostUid,
          "damage" to 2.0,
          "createdAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }
    assertPermissionDenied {
      serverRef.collection("player_hits").document("excess_${System.currentTimeMillis()}").set(
        mapOf(
          "attackerUid" to hostUid,
          "attackerName" to "HostNick",
          "targetUid" to member2Uid,
          "damage" to 50.0,
          "createdAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // Member2 updates own health to 0 (dead), respawns with animation fields, and invalid swingSeq fails
    signInTestUser(guestEmails[0])
    withTimeout(OP_TIMEOUT_MS) {
      serverRef.collection("players").document(member2Uid).set(
        mapOf(
          "uid" to member2Uid,
          "displayName" to "Jogador2",
          "x" to 24.5,
          "y" to 12.0,
          "z" to 24.5,
          "yaw" to 15.0,
          "pitch" to 0.0,
          "slotIndex" to 1,
          "health" to 0.0,
          "isDead" to true,
          "isWalking" to false,
          "swingSeq" to 2,
          "heldBlockId" to 0,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      serverRef.collection("players").document(member2Uid).set(
        mapOf(
          "uid" to member2Uid,
          "displayName" to "Jogador2",
          "x" to 24.5,
          "y" to 14.0,
          "z" to 24.5,
          "yaw" to 0.0,
          "pitch" to 0.0,
          "slotIndex" to 1,
          "health" to 20.0,
          "isDead" to false,
          "isWalking" to true,
          "swingSeq" to 3,
          "heldBlockId" to 4,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }
    assertPermissionDenied {
      serverRef.collection("players").document(member2Uid).set(
        mapOf(
          "uid" to member2Uid,
          "displayName" to "Jogador2",
          "x" to 24.5,
          "y" to 14.0,
          "z" to 24.5,
          "yaw" to 0.0,
          "pitch" to 0.0,
          "slotIndex" to 1,
          "isWalking" to true,
          "swingSeq" to -1,
          "heldBlockId" to 4,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // 8. Host sees 5/5 players and can delete server
    signInTestUser(hostEmail)
    val hostDoc = withTimeout(OP_TIMEOUT_MS) { serverRef.get().await() }
    assertTrue(hostDoc.exists())
    assertEquals(5, (hostDoc.get("memberUids") as List<*>).size)
    withTimeout(OP_TIMEOUT_MS) { serverRef.delete().await() }
  }
}
