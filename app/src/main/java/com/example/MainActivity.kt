package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GameViewModel
import com.example.ui.WabenzahlScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    )
    setContent {
      MyApplicationTheme {
        val viewModel: GameViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        BackHandler {
          if (!viewModel.handleBackPress()) {
            finish()
          }
        }

        WabenzahlScreen(
          state = uiState,
          onSelectTray = viewModel::selectTrayStack,
          onPlaceTrayOnCell = viewModel::placeTrayStackOnCell,
          onHoverCellChanged = viewModel::updateHoverCell,
          onBoardCellTap = viewModel::onBoardCellTap,
          onSwitchMode = viewModel::switchMode,
          onUndo = viewModel::undo,
          onToggleSound = viewModel::toggleSound,
          onShowHelp = viewModel::setHelpVisible,
          onRestart = viewModel::restartCurrentMode
        )
      }
    }
  }
}
