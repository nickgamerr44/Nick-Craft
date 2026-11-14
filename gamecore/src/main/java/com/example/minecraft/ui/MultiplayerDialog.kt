package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.model.GameMode
import com.example.minecraft.multiplayer.MAX_MULTIPLAYER_PLAYERS
import com.example.minecraft.multiplayer.MultiplayerGateway
import com.example.minecraft.multiplayer.ServerSummary
import kotlinx.coroutines.launch

@Composable
fun MultiplayerDialog(
  gateway: MultiplayerGateway,
  storage: WorldStorage,
  onHostWorld: (WorldInfo) -> Unit,
  onJoinByCode: (codeOrAddress: String) -> Unit,
  onJoinServer: (ServerSummary) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val userUid by gateway.currentUserUid.collectAsStateWithLifecycle()
  val userName by gateway.currentUserName.collectAsStateWithLifecycle()
  val sessionState by gateway.sessionState.collectAsStateWithLifecycle()
  val servers by gateway.availableServers.collectAsStateWithLifecycle()

  var activeTab by remember { mutableIntStateOf(0) } // 0 = Entrar, 1 = Abrir Servidor
  var codeInput by remember { mutableStateOf("") }
  var isSigningIn by remember { mutableStateOf(false) }
  var isRefreshing by remember { mutableStateOf(false) }
  val worlds = remember { storage.listWorlds() }

  val isSignedIn = !gateway.requiresSignIn || userUid != null

  LaunchedEffect(isSignedIn) {
    if (gateway.requiresSignIn && userUid == null) {
      gateway.trySilentSignIn(context)
    } else if (isSignedIn && gateway.supportsCloudServers) {
      isRefreshing = true
      gateway.refreshServers()
      isRefreshing = false
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.90f)
        .fillMaxHeight(0.88f)
        .border(2.dp, BedrockColors.CardBorder, RoundedCornerShape(8.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(8.dp))
        .padding(16.dp)
        .testTag("multiplayer_dialog")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Multiplayer (Máx. $MAX_MULTIPLAYER_PLAYERS Jogadores)",
              color = BedrockColors.TextWhite,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            if (isSignedIn && !userName.isNullOrBlank()) {
              Text(
                text = "Conectado como: $userName",
                color = BedrockColors.TextGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (gateway.requiresSignIn && userUid != null) {
              BedrockButton(
                text = "Sair",
                fontSize = 10,
                onClick = { scope.launch { gateway.signOut() } }
              )
            }
            Text(
              text = "✕",
              color = Color.LightGray,
              fontSize = 18.sp,
              modifier = Modifier
                .clickable { onDismiss() }
                .padding(4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!isSignedIn) {
          // Strict Google Sign-In Auth Gate before any Firestore query/listener
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
              .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
              .padding(20.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                text = "Multiplayer Online (Qualquer Rede • Máx. 5 Jogadores)",
                color = BedrockColors.TextYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Entre com sua conta Google para abrir um servidor ou entrar pelo código com amigos de qualquer rede ou internet.",
                color = BedrockColors.TextGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
              if (sessionState.errorMessage != null) {
                Text(
                  text = sessionState.errorMessage ?: "",
                  color = Color(0xFFFF6B6B),
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
              if (isSigningIn) {
                CircularProgressIndicator(color = BedrockColors.ButtonGreenTop)
              } else {
                BedrockButton(
                  text = "Entrar com Google",
                  isGreen = true,
                  onClick = {
                    isSigningIn = true
                    gateway.clearError()
                    scope.launch {
                      gateway.signInWithGoogle(context)
                      isSigningIn = false
                    }
                  },
                  modifier = Modifier.testTag("google_signin_button")
                )
              }
            }
          }
        } else {
          // Tabs: Entrar / Abrir Servidor
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            BedrockButton(
              text = "Entrar em Servidor",
              isGreen = activeTab == 0,
              onClick = { activeTab = 0 },
              modifier = Modifier.weight(1f)
            )
            BedrockButton(
              text = "Transformar Mundo em Servidor",
              isGreen = activeTab == 1,
              onClick = { activeTab = 1 },
              modifier = Modifier.weight(1f)
            )
          }

          if (sessionState.errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = sessionState.errorMessage ?: "",
              color = Color(0xFFFF6B6B),
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (activeTab == 0) {
            // Join by Code / IP + Open Server List
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                singleLine = true,
                label = {
                  Text(
                    text = if (gateway.supportsCloudServers) "Código do Servidor (ex: NC4829)" else "IP do Host (ex: 192.168.1.5:19132)",
                    color = Color.Gray,
                    fontSize = 11.sp
                  )
                },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedBorderColor = BedrockColors.ButtonGreenTop,
                  unfocusedBorderColor = BedrockColors.CardBorder
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("server_code_input")
              )
              BedrockButton(
                text = "Entrar",
                isGreen = true,
                enabled = codeInput.isNotBlank() && !sessionState.isBusy,
                onClick = { onJoinByCode(codeInput.trim()) },
                modifier = Modifier.testTag("join_by_code_button")
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (gateway.supportsCloudServers) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Servidores Online Abertos:",
                  color = BedrockColors.TextYellow,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
                BedrockButton(
                  text = if (isRefreshing) "Atualizando..." else "Atualizar",
                  fontSize = 10,
                  enabled = !isRefreshing,
                  onClick = {
                    isRefreshing = true
                    scope.launch {
                      gateway.refreshServers()
                      isRefreshing = false
                    }
                  }
                )
              }

              Spacer(modifier = Modifier.height(6.dp))

              if (servers.isEmpty()) {
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
                    .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = if (isRefreshing) "Buscando servidores..." else "Nenhum servidor aberto no momento. Crie o seu na aba ao lado!",
                    color = BedrockColors.TextGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(16.dp)
                  )
                }
              } else {
                LazyColumn(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  items(servers) { srv ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockColors.CardBg, RoundedCornerShape(4.dp))
                        .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "${srv.worldName} • [${srv.serverCode}]",
                          color = BedrockColors.TextWhite,
                          fontWeight = FontWeight.Bold,
                          fontSize = 13.sp,
                          fontFamily = FontFamily.Monospace
                        )
                        Text(
                          text = "Anfitrião: ${srv.hostName} • ${srv.gameMode.displayName} • ${srv.currentPlayers}/${srv.maxPlayers} jogadores",
                          color = if (srv.isFull) Color(0xFFFF6B6B) else BedrockColors.TextGreen,
                          fontSize = 10.sp,
                          fontFamily = FontFamily.Monospace
                        )
                      }
                      Spacer(modifier = Modifier.width(8.dp))
                      BedrockButton(
                        text = if (srv.isFull) "Lotado (5/5)" else "Conectar",
                        isGreen = !srv.isFull,
                        enabled = !srv.isFull && !sessionState.isBusy,
                        onClick = { onJoinServer(srv) }
                      )
                    }
                  }
                }
              }
            } else {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
                  .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
                  .padding(16.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "Modo Wi-Fi Local (LAN):\nDigite o IP e porta exibidos no menu do anfitrião (ex: 192.168.0.15:19132) e toque em Entrar.\nLimite máximo: 5 jogadores por servidor.",
                  color = BedrockColors.TextGray,
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          } else {
            // Host a World as Server (max 5 players)
            Text(
              text = "Escolha um mundo salvo para abrir como Servidor (máx. $MAX_MULTIPLAYER_PLAYERS jogadores):",
              color = BedrockColors.TextYellow,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (worlds.isEmpty()) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
                  .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
                  .padding(16.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = "Você ainda não tem mundos salvos. Crie um novo mundo já como Servidor!",
                    color = BedrockColors.TextGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                  )
                  Spacer(modifier = Modifier.height(10.dp))
                  BedrockButton(
                    text = "Criar Novo Mundo como Servidor (Máx. 5)",
                    isGreen = true,
                    onClick = {
                      onHostWorld(
                        WorldInfo(
                          id = "world_${System.currentTimeMillis()}",
                          name = "Servidor de ${userName ?: "Nick"}",
                          gameMode = GameMode.SURVIVAL,
                          seed = kotlin.random.Random.nextLong(10000, 99999),
                          lastPlayed = System.currentTimeMillis()
                        )
                      )
                    }
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                items(worlds) { w ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(BedrockColors.CardBg, RoundedCornerShape(4.dp))
                      .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(4.dp))
                      .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = w.name,
                        color = BedrockColors.TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                      )
                      Text(
                        text = "${w.gameMode.displayName} • Semente ${w.seed} • Limite: 5 jogadores",
                        color = BedrockColors.TextGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                      )
                    }
                    BedrockButton(
                      text = "Abrir Servidor",
                      isGreen = true,
                      onClick = { onHostWorld(w) },
                      modifier = Modifier.testTag("host_world_${w.id}")
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
