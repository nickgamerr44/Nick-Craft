package com.example.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.ui.GameMainMenuScreen
import com.example.minecraft.ui.GameScreen

private enum class StandaloneScreen {
  TITLE,
  GAME
}

class GameMainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
          StandaloneNickCraftApp()
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
private fun StandaloneNickCraftApp() {
  val context = LocalContext.current
  val storage = remember { WorldStorage(context) }

  var currentScreen by remember { mutableStateOf(StandaloneScreen.TITLE) }
  var activeWorldInfo by remember { mutableStateOf<WorldInfo?>(null) }

  when (currentScreen) {
    StandaloneScreen.TITLE -> {
      GameMainMenuScreen(
        storage = storage,
        versionTag = "0.01",
        onStartGame = { worldName, gameMode, seed, worldId ->
          val id = worldId ?: "world_${System.currentTimeMillis()}"
          activeWorldInfo = WorldInfo(
            id = id,
            name = worldName,
            gameMode = gameMode,
            seed = seed,
            lastPlayed = System.currentTimeMillis()
          )
          currentScreen = StandaloneScreen.GAME
        }
      )
    }

    StandaloneScreen.GAME -> {
      activeWorldInfo?.let { worldInfo ->
        GameScreen(
          worldInfo = worldInfo,
          versionTag = "0.01",
          storage = storage,
          onExitToTitle = {
            currentScreen = StandaloneScreen.TITLE
            activeWorldInfo = null
          }
        )
      }
    }
  }
}
