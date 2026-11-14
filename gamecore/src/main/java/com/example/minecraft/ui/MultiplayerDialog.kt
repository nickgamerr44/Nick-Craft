package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .widthIn(max = 720.dp)
        .fillMaxHeight(0.92f)
        .border(2.dp, BedrockColors.CardBorder, RoundedCornerShape(8.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(8.dp))
        .padding(12.dp)
        .testTag("multiplayer_dialog")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header (left column weighted so action buttons never wrap vertically)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .padding(end = 8.dp)
          ) {
            Text(
              text = "Multiplayer (Máx. $MAX_MULTIPLAYER_PLAYERS Jogadores)",
              color = BedrockColors.TextWhite,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (isSignedIn && !userName.isNullOrBlank()) {
              Text(
                text = "Conectado como: $userName",
                color = BedrockColors.TextGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                fontSize = 11,
                singleLine = true,
                onClick = { scope.launch { gateway.signOut() } },
                modifier = Modifier.testTag("multiplayer_signout_button")
              )
            }
            Box(
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "✕",
                color = Color.LightGray,
                fontSize = 18.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (!isSignedIn) {
          // Strict Google Sign-In Auth Gate before any Firestore query/listener
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
              .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              modifier = Modifier.verticalScroll(rememberScrollState()),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
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
                  singleLine = true,
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
              singleLine = true,
              onClick = { activeTab = 0 },
              modifier = Modifier.weight(1f)
            )
            BedrockButton(
              text = "Transformar Mundo em Servidor",
              isGreen = activeTab == 1,
              singleLine = true,
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

          Spacer(modifier = Modifier.height(8.dp))

          if (activeTab == 0) {
            BoxWithConstraints(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            ) {
              val isWideLandscape = maxWidth >= 520.dp

              if (isWideLandscape && gateway.supportsCloudServers) {
                // Two-pane layout in landscape: Join by Code (left) + Open Servers List (right)
                Row(
                  modifier = Modifier.fillMaxSize(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Column(
                    modifier = Modifier
                      .weight(0.38f)
                      .fillMaxHeight()
                      .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
                      .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
                      .padding(10.dp)
                      .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Text(
                      text = "Entrar por Código:",
                      color = BedrockColors.TextYellow,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                      value = codeInput,
                      onValueChange = { codeInput = it },
                      singleLine = true,
                      placeholder = {
                        Text(
                          text = "Ex: NC4829",
                          color = Color.Gray,
                          fontSize = 12.sp
                        )
                      },
                      colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BedrockColors.ButtonGreenTop,
                        unfocusedBorderColor = BedrockColors.CardBorder
                      ),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_code_input")
                    )
                    BedrockButton(
                      text = "Entrar",
                      isGreen = true,
                      singleLine = true,
                      enabled = codeInput.isNotBlank() && !sessionState.isBusy,
                      onClick = { onJoinByCode(codeInput.trim()) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_by_code_button")
                    )
                  }

                  Column(
                    modifier = Modifier
                      .weight(0.62f)
                      .fillMaxHeight()
                  ) {
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
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                          .weight(1f)
                          .padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      BedrockButton(
                        text = if (isRefreshing) "Atualizando..." else "Atualizar",
                        fontSize = 11,
                        singleLine = true,
                        enabled = !isRefreshing,
                        onClick = {
                          isRefreshing = true
                          scope.launch {
                            gateway.refreshServers()
                            isRefreshing = false
                          }
                        },
                        modifier = Modifier.testTag("refresh_servers_button")
                      )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (servers.isEmpty()) {
                      Box(
                        modifier = Modifier
                          .weight(1f)
                          .fillMaxWidth()
                          .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
                          .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
                          .padding(14.dp),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = if (isRefreshing) "Buscando servidores..." else "Nenhum servidor aberto no momento. Crie o seu na aba ao lado!",
                          color = BedrockColors.TextGray,
                          fontSize = 11.sp,
                          fontFamily = FontFamily.Monospace
                        )
                      }
                    } else {
                      LazyColumn(
                        modifier = Modifier
                          .weight(1f)
                          .fillMaxWidth()
                          .testTag("multiplayer_servers_list"),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        items(servers, key = { it.serverId }) { srv ->
                          ServerSummaryRow(
                            srv = srv,
                            isBusy = sessionState.isBusy,
                            onJoin = { onJoinServer(srv) }
                          )
                        }
                      }
                    }
                  }
                }
              } else {
                // Portrait / Compact width: single vertical LazyColumn
                LazyColumn(
                  modifier = Modifier
                    .fillMaxSize()
                    .testTag("multiplayer_servers_list"),
                  contentPadding = PaddingValues(bottom = 8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  item(key = "code_input_row") {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        singleLine = true,
                        placeholder = {
                          Text(
                            text = if (gateway.supportsCloudServers) "Código (ex: NC4829)" else "IP do Host (ex: 192.168.1.5:19132)",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        singleLine = true,
                        enabled = codeInput.isNotBlank() && !sessionState.isBusy,
                        onClick = { onJoinByCode(codeInput.trim()) },
                        modifier = Modifier.testTag("join_by_code_button")
                      )
                    }
                  }

                  if (gateway.supportsCloudServers) {
                    item(key = "open_servers_header") {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = "Servidores Online Abertos:",
                          color = BedrockColors.TextYellow,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.Bold,
                          fontFamily = FontFamily.Monospace,
                          modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        BedrockButton(
                          text = if (isRefreshing) "Atualizando..." else "Atualizar",
                          fontSize = 11,
                          singleLine = true,
                          enabled = !isRefreshing,
                          onClick = {
                            isRefreshing = true
                            scope.launch {
                              gateway.refreshServers()
                              isRefreshing = false
                            }
                          },
                          modifier = Modifier.testTag("refresh_servers_button")
                        )
                      }
                    }

                    if (servers.isEmpty()) {
                      item(key = "empty_servers") {
                        Box(
                          modifier = Modifier
                            .fillMaxWidth()
                            .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
                            .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
                            .padding(18.dp),
                          contentAlignment = Alignment.Center
                        ) {
                          Text(
                            text = if (isRefreshing) "Buscando servidores..." else "Nenhum servidor aberto no momento. Crie o seu na aba ao lado!",
                            color = BedrockColors.TextGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                          )
                        }
                      }
                    } else {
                      items(servers, key = { it.serverId }) { srv ->
                        ServerSummaryRow(
                          srv = srv,
                          isBusy = sessionState.isBusy,
                          onJoin = { onJoinServer(srv) }
                        )
                      }
                    }
                  } else {
                    item(key = "lan_instructions") {
                      Box(
                        modifier = Modifier
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
                  }
                }
              }
            }
          } else {
            // Host a World as Server (max 5 players)
            LazyColumn(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("host_worlds_list"),
              contentPadding = PaddingValues(bottom = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              item(key = "host_header") {
                Text(
                  text = "Escolha um mundo salvo para abrir como Servidor (máx. $MAX_MULTIPLAYER_PLAYERS jogadores):",
                  color = BedrockColors.TextYellow,
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace
                )
              }

              if (worlds.isEmpty()) {
                item(key = "no_saved_worlds") {
                  Box(
                    modifier = Modifier
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
                }
              } else {
                items(worlds, key = { it.id }) { w ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(BedrockColors.CardBg, RoundedCornerShape(4.dp))
                      .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(4.dp))
                      .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(
                      modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                    ) {
                      Text(
                        text = w.name,
                        color = BedrockColors.TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = "${w.gameMode.displayName} • Semente ${w.seed} • Limite: 5 jogadores",
                        color = BedrockColors.TextGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                    BedrockButton(
                      text = "Abrir Servidor",
                      isGreen = true,
                      singleLine = true,
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

@Composable
private fun ServerSummaryRow(
  srv: ServerSummary,
  isBusy: Boolean,
  onJoin: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(BedrockColors.CardBg, RoundedCornerShape(4.dp))
      .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(4.dp))
      .padding(10.dp)
      .testTag("server_item_${srv.serverCode}"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp)
    ) {
      Text(
        text = "${srv.worldName} • [${srv.serverCode}]",
        color = BedrockColors.TextWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = "Anfitrião: ${srv.hostName} • ${srv.gameMode.displayName} • ${srv.currentPlayers}/${srv.maxPlayers} jogadores",
        color = if (srv.isFull) Color(0xFFFF6B6B) else BedrockColors.TextGreen,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
    BedrockButton(
      text = if (srv.isFull) "Lotado (5/5)" else "Conectar",
      isGreen = !srv.isFull,
      singleLine = true,
      enabled = !srv.isFull && !isBusy,
      onClick = onJoin,
      modifier = Modifier.testTag("connect_server_${srv.serverCode}")
    )
  }
}
