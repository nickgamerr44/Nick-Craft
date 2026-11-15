package com.example.minecraft.multiplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.model.BlockType
import com.example.minecraft.model.GameMode
import com.example.minecraft.model.Player
import com.example.minecraft.model.VoxelWorld
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.random.Random

data class WorldServerDoc(
  @DocumentId val id: String = "",
  val serverCode: String = "",
  val worldName: String = "",
  val hostUid: String = "",
  val hostName: String = "",
  val gameMode: String = "SURVIVAL",
  val seed: Long = 12345L,
  val maxPlayers: Int = MAX_MULTIPLAYER_PLAYERS,
  val memberUids: List<String> = emptyList(),
  val isOpen: Boolean = true,
  val blocksBase64: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

data class PlayerPresenceDoc(
  @DocumentId val id: String = "",
  val uid: String = "",
  val displayName: String = "",
  val x: Double = 24.5,
  val y: Double = 12.0,
  val z: Double = 24.5,
  val yaw: Double = 0.0,
  val pitch: Double = 0.0,
  val slotIndex: Int = 0,
  val health: Double = 20.0,
  val isDead: Boolean = false,
  val isWalking: Boolean = false,
  val swingSeq: Int = 0,
  val heldBlockId: Int = 0,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

data class PlayerHitDoc(
  @DocumentId val id: String = "",
  val attackerUid: String = "",
  val attackerName: String = "",
  val targetUid: String = "",
  val damage: Double = 2.0,
  @ServerTimestamp val createdAt: Timestamp? = null
)

data class BlockEditDoc(
  @DocumentId val id: String = "",
  val x: Int = 0,
  val y: Int = 0,
  val z: Int = 0,
  val blockId: Int = 0,
  val authorUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * Cloud Firestore + CredentialManager Google Sign-In multiplayer gateway shared by
 * both :app (Launcher) and :game (standalone Nick Craft APK), allowing cross-network play
 * between players on different networks with a strict 5-player limit.
 */
class FirestoreMultiplayerGateway(
  context: Context
) : MultiplayerGateway {

  private val appContext = context.applicationContext

  private fun resolveStringResource(name: String): String {
    val resId = appContext.resources.getIdentifier(name, "string", appContext.packageName)
    check(resId != 0) { "Missing Firebase string resource: $name" }
    return appContext.getString(resId)
  }

  private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
    resolveStringResource("firestore_database_id")
  )
  private val auth: FirebaseAuth = Firebase.auth
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override val supportsCloudServers: Boolean = true
  override val requiresSignIn: Boolean = true

  private val _currentUserName = MutableStateFlow<String?>(
    auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: auth.currentUser?.email?.substringBefore('@')
  )
  override val currentUserName: StateFlow<String?> = _currentUserName.asStateFlow()

  private val _currentUserUid = MutableStateFlow<String?>(auth.currentUser?.uid)
  override val currentUserUid: StateFlow<String?> = _currentUserUid.asStateFlow()

  private val _sessionState = MutableStateFlow(MultiplayerSessionState())
  override val sessionState: StateFlow<MultiplayerSessionState> = _sessionState.asStateFlow()

  private val _availableServers = MutableStateFlow<List<ServerSummary>>(emptyList())
  override val availableServers: StateFlow<List<ServerSummary>> = _availableServers.asStateFlow()

  private val _remoteBlockEdits = MutableSharedFlow<BlockEditEvent>(extraBufferCapacity = 64)
  override val remoteBlockEdits: SharedFlow<BlockEditEvent> = _remoteBlockEdits.asSharedFlow()

  private val _remotePlayerHits = MutableSharedFlow<PlayerHitEvent>(extraBufferCapacity = 32)
  override val remotePlayerHits: SharedFlow<PlayerHitEvent> = _remotePlayerHits.asSharedFlow()

  private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
    val user = firebaseAuth.currentUser
    _currentUserUid.value = user?.uid
    _currentUserName.value = user?.displayName?.takeIf { it.isNotBlank() }
      ?: user?.email?.substringBefore('@')
  }

  private var serverDocReg: ListenerRegistration? = null
  private var playersReg: ListenerRegistration? = null
  private var editsReg: ListenerRegistration? = null
  private var hitsReg: ListenerRegistration? = null
  private var hostedWorldRef: WeakReference<VoxelWorld>? = null
  private var hostEditCounter: Int = 0
  private var mySlotIndex: Int = 0
  private var lastPosePublishMs: Long = 0L
  @Volatile private var poseSyncIntervalMs: Long = 120L
  private var sessionJoinedAtMs: Long = 0L
  private val seenEditIds = HashSet<String>()
  private val seenHitIds = HashSet<String>()

  init {
    auth.addAuthStateListener(authListener)
  }

  private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

  override suspend fun trySilentSignIn(context: Context): Boolean {
    if (auth.currentUser != null) return true
    return try {
      val webClientId = resolveStringResource("default_web_client_id")
      val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(true)
        .build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
      val hostContext = context.findActivity() ?: context
      val cm = CredentialManager.create(hostContext)
      val result = cm.getCredential(hostContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCred).await()
        true
      } else {
        false
      }
    } catch (_: NoCredentialException) {
      false
    } catch (_: Exception) {
      false
    }
  }

  override suspend fun signInWithGoogle(activityContext: Context): Result<String> {
    val existing = auth.currentUser
    if (existing != null) {
      return Result.success(_currentUserName.value ?: "Jogador")
    }
    return try {
      val webClientId = resolveStringResource("default_web_client_id")
      val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
      val hostContext = activityContext.findActivity() ?: activityContext
      val cm = CredentialManager.create(hostContext)
      val result = cm.getCredential(hostContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
        val authResult = auth.signInWithCredential(firebaseCred).await()
        val user = authResult.user
        val name = user?.displayName?.takeIf { it.isNotBlank() }
          ?: user?.email?.substringBefore('@')
          ?: "Jogador"
        _currentUserUid.value = user?.uid
        _currentUserName.value = name
        _sessionState.update { it.copy(errorMessage = null) }
        Result.success(name)
      } else {
        val err = IllegalStateException("Credencial Google inesperada")
        _sessionState.update { it.copy(errorMessage = "Credencial Google inválida. Tente novamente.") }
        Result.failure(err)
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("FirestoreMultiplayer", "Google sign-in cancelled or dismissed: ${e.message}", e)
      _sessionState.update {
        it.copy(errorMessage = "Login Google cancelado. Selecione uma conta Google para continuar.")
      }
      Result.failure(e)
    } catch (e: NoCredentialException) {
      Log.w("FirestoreMultiplayer", "No Google credentials available: ${e.message}", e)
      _sessionState.update {
        it.copy(errorMessage = "Nenhuma conta Google encontrada neste aparelho/emulador. Adicione uma conta Google nas Configurações do Android.")
      }
      Result.failure(e)
    } catch (e: Exception) {
      Log.e("FirestoreMultiplayer", "Google sign-in failed", e)
      _sessionState.update {
        it.copy(errorMessage = "Falha ao entrar com Google: ${e.localizedMessage ?: "tente novamente"}")
      }
      Result.failure(e)
    }
  }

  override suspend fun signOut() {
    closeOrLeaveServer()
    _availableServers.value = emptyList()
    withContext(NonCancellable) {
      runCatching {
        CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
      }.onFailure {
        Log.w("FirestoreMultiplayer", "Failed to clear credential state: ${it.message}")
      }
      auth.signOut()
    }
  }

  override suspend fun refreshServers(): Result<Unit> {
    val user = auth.currentUser ?: return Result.failure(IllegalStateException("Faça login com o Google primeiro."))
    return try {
      val snap = db.collection("world_servers")
        .whereEqualTo("isOpen", true)
        .limit(20)
        .get()
        .await()

      val list = snap.documents.mapNotNull { doc ->
        val data = doc.toObject(WorldServerDoc::class.java) ?: return@mapNotNull null
        val mode = runCatching { GameMode.valueOf(data.gameMode) }.getOrDefault(GameMode.SURVIVAL)
        ServerSummary(
          serverId = doc.id,
          serverCode = data.serverCode.ifBlank { doc.id },
          worldName = data.worldName.ifBlank { "Mundo" },
          hostName = data.hostName.ifBlank { "Anfitrião" },
          gameMode = mode,
          seed = data.seed,
          currentPlayers = data.memberUids.size.coerceIn(1, MAX_MULTIPLAYER_PLAYERS),
          maxPlayers = data.maxPlayers.coerceAtMost(MAX_MULTIPLAYER_PLAYERS),
          isOpen = data.isOpen
        )
      }
      _availableServers.value = list
      Result.success(Unit)
    } catch (e: Exception) {
      val msg = handleFirestoreError(e, "list", "world_servers", user.uid)
      _sessionState.update { it.copy(errorMessage = msg) }
      Result.failure(e)
    }
  }

  override suspend fun hostWorldAsServer(
    worldInfo: WorldInfo,
    world: VoxelWorld,
    player: Player
  ): Result<MultiplayerSessionState> {
    val user = auth.currentUser
      ?: return Result.failure(IllegalStateException("Entre com sua conta Google para abrir um servidor."))

    closeOrLeaveServer()
    _sessionState.update { it.copy(isBusy = true, errorMessage = null) }

    val code = generateServerCode()
    val hostDisplayName = (_currentUserName.value ?: "Anfitrião").take(40)
    val encodedBlocks = WorldCodec.compressBlocks(world.blocks)
    hostedWorldRef = WeakReference(world)
    hostEditCounter = 0

    val serverPayload = hashMapOf(
      "serverCode" to code,
      "worldName" to worldInfo.name.take(55).ifBlank { "Meu Mundo" },
      "hostUid" to user.uid,
      "hostName" to hostDisplayName,
      "gameMode" to worldInfo.gameMode.name,
      "seed" to worldInfo.seed,
      "maxPlayers" to MAX_MULTIPLAYER_PLAYERS,
      "memberUids" to listOf(user.uid),
      "isOpen" to true,
      "blocksBase64" to encodedBlocks,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )

    return try {
      db.collection("world_servers").document(code).set(serverPayload).await()
      mySlotIndex = 0
      publishPresenceDoc(code, user.uid, hostDisplayName, player, 0)

      val state = MultiplayerSessionState(
        isActive = true,
        role = MultiplayerRole.HOST,
        serverId = code,
        serverCode = code,
        worldName = worldInfo.name,
        maxPlayers = MAX_MULTIPLAYER_PLAYERS,
        currentPlayers = 1,
        allPlayerNames = listOf(hostDisplayName),
        connectionHint = "Servidor Online • Código: $code (Máx. 5 jogadores)"
      )
      _sessionState.value = state
      attachServerListeners(code, user.uid)
      Result.success(state)
    } catch (e: Exception) {
      hostedWorldRef = null
      val msg = handleFirestoreError(e, "create", "world_servers/$code", user.uid)
      _sessionState.update { it.copy(isBusy = false, errorMessage = msg) }
      Result.failure(e)
    }
  }

  override suspend fun joinServerByCode(
    codeOrAddress: String,
    world: VoxelWorld,
    player: Player
  ): Result<WorldInfo> {
    val user = auth.currentUser
      ?: return Result.failure(IllegalStateException("Entre com sua conta Google para entrar em um servidor."))

    val serverId = codeOrAddress.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "")
    if (serverId.length < 2) {
      return Result.failure(IllegalArgumentException("Código do servidor inválido."))
    }

    closeOrLeaveServer()
    _sessionState.update { it.copy(isBusy = true, errorMessage = null) }

    val serverRef = db.collection("world_servers").document(serverId)
    return try {
      val joinedDoc = db.runTransaction { tx ->
        val snap = tx.get(serverRef)
        val doc = snap.toObject(WorldServerDoc::class.java)
          ?: throw IllegalStateException("Servidor $serverId não encontrado.")
        if (!doc.isOpen) {
          throw IllegalStateException("Este servidor foi encerrado pelo anfitrião.")
        }
        val members = doc.memberUids
        if (user.uid !in members) {
          if (members.size >= MAX_MULTIPLAYER_PLAYERS) {
            throw IllegalStateException("Servidor lotado (limite máximo de $MAX_MULTIPLAYER_PLAYERS jogadores).")
          }
          tx.update(
            serverRef,
            mapOf(
              "memberUids" to (members + user.uid),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
        doc
      }.await()

      val slotIdx = (joinedDoc.memberUids.indexOf(user.uid).takeIf { it >= 0 }
        ?: joinedDoc.memberUids.size).coerceIn(0, MAX_MULTIPLAYER_PLAYERS - 1)
      mySlotIndex = slotIdx

      WorldCodec.decompressBlocks(joinedDoc.blocksBase64, world.blocks)
      world.isDirty = true

      val guestName = (_currentUserName.value ?: "Jogador").take(40)
      publishPresenceDoc(serverId, user.uid, guestName, player, slotIdx)

      val mode = runCatching { GameMode.valueOf(joinedDoc.gameMode) }.getOrDefault(GameMode.SURVIVAL)
      val worldInfo = WorldInfo(
        id = "mp_$serverId",
        name = joinedDoc.worldName.ifBlank { "Servidor $serverId" },
        gameMode = mode,
        seed = joinedDoc.seed,
        lastPlayed = System.currentTimeMillis()
      )

      _sessionState.value = MultiplayerSessionState(
        isActive = true,
        role = MultiplayerRole.CLIENT,
        serverId = serverId,
        serverCode = joinedDoc.serverCode.ifBlank { serverId },
        worldName = worldInfo.name,
        maxPlayers = MAX_MULTIPLAYER_PLAYERS,
        currentPlayers = (joinedDoc.memberUids.size + if (user.uid in joinedDoc.memberUids) 0 else 1)
          .coerceIn(1, MAX_MULTIPLAYER_PLAYERS),
        allPlayerNames = listOf(joinedDoc.hostName, guestName).distinct(),
        connectionHint = "Conectado ao Servidor $serverId"
      )

      attachServerListeners(serverId, user.uid)
      Result.success(worldInfo)
    } catch (e: Exception) {
      val msg = if (e is IllegalStateException) {
        e.message ?: "Não foi possível entrar no servidor."
      } else {
        handleFirestoreError(e, "join", "world_servers/$serverId", user.uid)
      }
      _sessionState.update { it.copy(isBusy = false, errorMessage = msg) }
      Result.failure(e)
    }
  }

  override suspend fun joinServer(
    server: ServerSummary,
    world: VoxelWorld,
    player: Player
  ): Result<WorldInfo> {
    if (server.isFull) {
      val msg = "Servidor lotado (${server.currentPlayers}/${server.maxPlayers} jogadores)."
      _sessionState.update { it.copy(errorMessage = msg) }
      return Result.failure(IllegalStateException(msg))
    }
    return joinServerByCode(server.serverId, world, player)
  }

  private fun attachServerListeners(serverId: String, localUid: String) {
    val user = auth.currentUser ?: return
    val serverRef = db.collection("world_servers").document(serverId)
    sessionJoinedAtMs = System.currentTimeMillis()

    serverDocReg = serverRef.addSnapshotListener { snap, err ->
      if (err != null) {
        handleFirestoreError(err, "listen_server", "world_servers/$serverId", user.uid)
        return@addSnapshotListener
      }
      val doc = snap?.toObject(WorldServerDoc::class.java)
      if (doc == null || !doc.isOpen) {
        _sessionState.update {
          if (it.isActive && it.role == MultiplayerRole.CLIENT) {
            it.copy(isActive = false, errorMessage = "O anfitrião fechou este servidor.")
          } else {
            it
          }
        }
        return@addSnapshotListener
      }
      _sessionState.update {
        it.copy(
          currentPlayers = doc.memberUids.size.coerceIn(1, MAX_MULTIPLAYER_PLAYERS)
        )
      }
    }

    playersReg = serverRef.collection("players").addSnapshotListener { snap, err ->
      if (err != null) {
        handleFirestoreError(err, "listen_players", "world_servers/$serverId/players", user.uid)
        return@addSnapshotListener
      }
      val docs = snap?.documents.orEmpty().mapNotNull { it.toObject(PlayerPresenceDoc::class.java) }
      val remotes = docs.filter { it.uid != localUid && it.uid.isNotBlank() }.map { p ->
        RemotePlayerState(
          playerId = p.uid,
          displayName = p.displayName.ifBlank { "Jogador" },
          x = p.x.toFloat(),
          y = p.y.toFloat(),
          z = p.z.toFloat(),
          yaw = p.yaw.toFloat(),
          pitch = p.pitch.toFloat(),
          slotIndex = p.slotIndex.coerceIn(0, MAX_MULTIPLAYER_PLAYERS - 1),
          health = p.health.toFloat().coerceIn(0f, 20f),
          isDead = p.isDead || p.health <= 0.0,
          isWalking = p.isWalking,
          swingSeq = p.swingSeq.coerceAtLeast(0),
          heldBlockId = p.heldBlockId.coerceIn(0, 127)
        )
      }
      val names = docs.mapNotNull { it.displayName.takeIf { n -> n.isNotBlank() } }.distinct()
      _sessionState.update {
        it.copy(
          remotePlayers = remotes,
          allPlayerNames = if (names.isNotEmpty()) names else it.allPlayerNames
        )
      }
    }

    editsReg = serverRef.collection("block_edits")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .limitToLast(200)
      .addSnapshotListener { snap, err ->
        if (err != null) {
          handleFirestoreError(err, "listen_edits", "world_servers/$serverId/block_edits", user.uid)
          return@addSnapshotListener
        }
        for (doc in snap?.documents.orEmpty()) {
          if (!seenEditIds.add(doc.id)) continue
          val edit = doc.toObject(BlockEditDoc::class.java) ?: continue
          if (edit.authorUid != localUid) {
            _remoteBlockEdits.tryEmit(
              BlockEditEvent(
                x = edit.x,
                y = edit.y,
                z = edit.z,
                blockId = edit.blockId.toByte(),
                authorId = edit.authorUid
              )
            )
          }
        }
      }

    hitsReg = serverRef.collection("player_hits")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .limitToLast(50)
      .addSnapshotListener { snap, err ->
        if (err != null) {
          handleFirestoreError(err, "listen_hits", "world_servers/$serverId/player_hits", user.uid)
          return@addSnapshotListener
        }
        for (doc in snap?.documents.orEmpty()) {
          if (!seenHitIds.add(doc.id)) continue
          val hit = doc.toObject(PlayerHitDoc::class.java) ?: continue
          val createdMs = hit.createdAt?.toDate()?.time
          // Skip historical hits from before this player joined the session
          if (createdMs != null && createdMs < sessionJoinedAtMs - 10_000L) continue
          if (hit.targetUid == localUid && hit.attackerUid != localUid) {
            _remotePlayerHits.tryEmit(
              PlayerHitEvent(
                targetPlayerId = hit.targetUid,
                attackerId = hit.attackerUid,
                attackerName = hit.attackerName.ifBlank { "Jogador" },
                damage = hit.damage.toFloat().coerceIn(0.5f, 20f)
              )
            )
          }
        }
      }
  }

  override fun updatePoseSyncIntervalMs(intervalMs: Long) {
    poseSyncIntervalMs = intervalMs.coerceIn(60L, 500L)
  }

  override fun publishLocalPlayerState(player: Player, forceImmediate: Boolean) {
    val state = _sessionState.value
    val user = auth.currentUser ?: return
    if (!state.isActive || state.serverId.isBlank()) return

    val now = System.currentTimeMillis()
    if (!forceImmediate && now - lastPosePublishMs < poseSyncIntervalMs) return
    lastPosePublishMs = now

    val name = (_currentUserName.value ?: "Jogador").take(40)
    scope.launch {
      runCatching {
        publishPresenceDoc(state.serverId, user.uid, name, player, mySlotIndex)
      }
    }
  }

  private suspend fun publishPresenceDoc(
    serverId: String,
    uid: String,
    displayName: String,
    player: Player,
    slotIndex: Int
  ) {
    val heldBlockId = (player.inventory.selectedItem?.item?.blockType?.id?.toInt() ?: 0).coerceIn(0, 127)
    val payload = hashMapOf(
      "uid" to uid,
      "displayName" to displayName,
      "x" to player.x.toDouble(),
      "y" to player.y.toDouble(),
      "z" to player.z.toDouble(),
      "yaw" to player.yaw.toDouble(),
      "pitch" to player.pitch.toDouble(),
      "slotIndex" to slotIndex.coerceIn(0, MAX_MULTIPLAYER_PLAYERS - 1),
      "health" to player.health.toDouble().coerceIn(0.0, 20.0),
      "isDead" to (player.health <= 0f),
      "isWalking" to player.isWalking,
      "swingSeq" to player.swingSeq.coerceAtLeast(0),
      "heldBlockId" to heldBlockId,
      "updatedAt" to FieldValue.serverTimestamp()
    )
    db.collection("world_servers").document(serverId)
      .collection("players").document(uid)
      .set(payload)
      .await()
  }

  override fun publishBlockEdit(x: Int, y: Int, z: Int, blockType: BlockType) {
    val state = _sessionState.value
    val user = auth.currentUser ?: return
    if (!state.isActive || state.serverId.isBlank()) return

    val editId = "e_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    seenEditIds.add(editId)
    val payload = hashMapOf(
      "x" to x,
      "y" to y,
      "z" to z,
      "blockId" to blockType.id.toInt(),
      "authorUid" to user.uid,
      "createdAt" to FieldValue.serverTimestamp()
    )
    val shouldSnapshot = state.role == MultiplayerRole.HOST && (++hostEditCounter % 8 == 0)
    val worldSnapshotBytes = if (shouldSnapshot) hostedWorldRef?.get()?.blocks?.clone() else null

    scope.launch {
      runCatching {
        db.collection("world_servers").document(state.serverId)
          .collection("block_edits").document(editId)
          .set(payload)
          .await()
        if (worldSnapshotBytes != null) {
          db.collection("world_servers").document(state.serverId)
            .update(
              mapOf(
                "blocksBase64" to WorldCodec.compressBlocks(worldSnapshotBytes),
                "updatedAt" to FieldValue.serverTimestamp()
              )
            )
            .await()
        }
      }.onFailure { err ->
        handleFirestoreError(err, "create", "world_servers/${state.serverId}/block_edits/$editId", user.uid)
      }
    }
  }

  override fun publishPlayerHit(targetPlayerId: String, damage: Float) {
    val state = _sessionState.value
    val user = auth.currentUser ?: return
    if (!state.isActive || state.serverId.isBlank()) return
    if (targetPlayerId.isBlank() || targetPlayerId == user.uid) return

    val hitId = "h_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    val attackerName = (_currentUserName.value ?: "Jogador").take(40).ifBlank { "Jogador" }
    val payload = hashMapOf(
      "attackerUid" to user.uid,
      "attackerName" to attackerName,
      "targetUid" to targetPlayerId,
      "damage" to damage.coerceIn(0.5f, 20.0f).toDouble(),
      "createdAt" to FieldValue.serverTimestamp()
    )

    scope.launch {
      runCatching {
        db.collection("world_servers").document(state.serverId)
          .collection("player_hits").document(hitId)
          .set(payload)
          .await()
      }.onFailure { err ->
        handleFirestoreError(err, "create", "world_servers/${state.serverId}/player_hits/$hitId", user.uid)
      }
    }
  }

  override suspend fun closeOrLeaveServer() {
    val state = _sessionState.value
    val user = auth.currentUser

    serverDocReg?.remove()
    playersReg?.remove()
    editsReg?.remove()
    hitsReg?.remove()
    serverDocReg = null
    playersReg = null
    editsReg = null
    hitsReg = null
    hostedWorldRef = null
    hostEditCounter = 0
    seenEditIds.clear()
    seenHitIds.clear()

    if (state.isActive && state.serverId.isNotBlank() && user != null) {
      val serverRef = db.collection("world_servers").document(state.serverId)
      runCatching {
        serverRef.collection("players").document(user.uid).delete().await()
      }
      if (state.role == MultiplayerRole.HOST) {
        runCatching {
          serverRef.update(
            mapOf(
              "isOpen" to false,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).await()
        }
      } else if (state.role == MultiplayerRole.CLIENT) {
        runCatching {
          db.runTransaction { tx ->
            val snap = tx.get(serverRef)
            val doc = snap.toObject(WorldServerDoc::class.java) ?: return@runTransaction
            if (user.uid in doc.memberUids && doc.hostUid != user.uid) {
              tx.update(
                serverRef,
                mapOf(
                  "memberUids" to doc.memberUids.filter { it != user.uid },
                  "updatedAt" to FieldValue.serverTimestamp()
                )
              )
            }
          }.await()
        }
      }
    }

    _sessionState.value = MultiplayerSessionState()
  }

  override fun clearError() {
    _sessionState.update { it.copy(errorMessage = null) }
  }

  private fun generateServerCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "NC" + (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
  }
}
