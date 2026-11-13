package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.ui.GameMainMenuScreen
import com.example.minecraft.ui.GameScreen
import com.example.minecraft.ui.TitleScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
  LAUNCHER,
  GAME_MENU,
  GAME
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          NickCraftLauncherApp()
        }
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      WindowCompat.getInsetsController(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    }
  }
}

@Composable
fun NickCraftLauncherApp() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val storage = remember { WorldStorage(context) }

  var currentScreen by remember { mutableStateOf(AppScreen.LAUNCHER) }
  var activeWorldInfo by remember { mutableStateOf<WorldInfo?>(null) }
  var activeVersionTag by remember { mutableStateOf("0.01") }

  when (currentScreen) {
    AppScreen.LAUNCHER -> {
      TitleScreen(
        onLaunchGame = { versionTag ->
          activeVersionTag = versionTag
          currentScreen = AppScreen.GAME_MENU
        }
      )
    }

    AppScreen.GAME_MENU -> {
      GameMainMenuScreen(
        storage = storage,
        versionTag = activeVersionTag,
        onStartGame = { worldName, gameMode, seed, worldId ->
          val id = worldId ?: "world_${System.currentTimeMillis()}"
          activeWorldInfo = WorldInfo(
            id = id,
            name = worldName,
            gameMode = gameMode,
            seed = seed,
            lastPlayed = System.currentTimeMillis()
          )
          currentScreen = AppScreen.GAME
        },
        onExitToLauncher = {
          currentScreen = AppScreen.LAUNCHER
        }
      )
    }

    AppScreen.GAME -> {
      activeWorldInfo?.let { worldInfo ->
        GameScreen(
          worldInfo = worldInfo,
          versionTag = activeVersionTag,
          storage = storage,
          onExitToTitle = {
            currentScreen = AppScreen.GAME_MENU
            activeWorldInfo = null
          }
        )
      }
    }
  }
}
