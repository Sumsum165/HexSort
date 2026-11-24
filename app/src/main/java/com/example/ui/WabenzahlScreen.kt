package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GameMode
import com.example.model.GameUiState
import com.example.model.TilePalette
import kotlin.math.roundToInt

@Composable
fun WabenzahlScreen(
  state: GameUiState,
  onSelectTray: (Int) -> Unit,
  onPlaceTrayOnCell: (Int, String) -> Unit,
  onHoverCellChanged: (String?) -> Unit,
  onBoardCellTap: (String) -> Unit,
  onSwitchMode: (GameMode) -> Unit,
  onUndo: () -> Unit,
  onToggleSound: () -> Unit,
  onShowHelp: (Boolean) -> Unit,
  onRestart: () -> Unit
) {
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current
  var boardMetrics by remember { mutableStateOf(BoardMetrics()) }
  var draggingTrayIdx by remember { mutableStateOf<Int?>(null) }
  var dragRootOffset by remember { mutableStateOf(Offset.Zero) }

  val fingerLiftPx = with(density) { 28.dp.toPx() }

  fun findBoardDropTarget(rootPointer: Offset): String? {
    val localX = rootPointer.x - boardMetrics.rootPosition.x
    val localY = (rootPointer.y - fingerLiftPx) - boardMetrics.rootPosition.y
    if (localX < -40f || localY < -40f ||
      localX > boardMetrics.size.width + 40f || localY > boardMetrics.size.height + 40f
    ) {
      return null
    }
    return boardMetrics.findCellAtLocalPoint(localX, localY, emptyOnly = true, board = state.board)
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(Color(0xFF0F172A), Color(0xFF0B1120))
        )
      )
      .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 540.dp)
        .align(Alignment.Center)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      // 1. Kompakte Kopfzeile
      TopHudBar(
        state = state,
        onUndo = onUndo,
        onToggleSound = onToggleSound,
        onShowHelp = { onShowHelp(true) },
        onRestart = onRestart,
        onSwitchMode = onSwitchMode
      )

      // 2. Großes Waben-Spielfeld
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        HexBoardCanvas(
          state = state,
          textMeasurer = textMeasurer,
          onMetricsChanged = { boardMetrics = it },
          onCellTapped = onBoardCellTap
        )
      }

      // 3. Kompakte Status- & Zugrichtungszeile
      StatusStrip(state = state)

      // 4. Untere Stapel-Ablage
      TrayDock(
        tray = state.tray,
        selectedTrayIdx = state.selectedTrayIdx,
        draggingTrayIdx = draggingTrayIdx,
        textMeasurer = textMeasurer,
        onSelectTray = onSelectTray,
        onDragStart = { idx, rootPos ->
          if (!state.isAnimating && !state.gameOver) {
            draggingTrayIdx = idx
            dragRootOffset = rootPos
            onHoverCellChanged(findBoardDropTarget(rootPos))
          }
        },
        onDragMove = { rootPos ->
          if (draggingTrayIdx != null) {
            dragRootOffset = rootPos
            onHoverCellChanged(findBoardDropTarget(rootPos))
          }
        },
        onDragEnd = { idx, rootPos ->
          val dropCell = findBoardDropTarget(rootPos)
          draggingTrayIdx = null
          onHoverCellChanged(null)
          if (dropCell != null) {
            onPlaceTrayOnCell(idx, dropCell)
          }
        },
        onDragCancel = {
          draggingTrayIdx = null
          onHoverCellChanged(null)
        }
      )
    }

    // Schwebender Drag-Geist über allem
    val dragIdx = draggingTrayIdx
    val dragStack = dragIdx?.let { state.tray.getOrNull(it) }
    if (dragIdx != null && dragStack != null) {
      val ghostSizePx = with(density) { 108.dp.toPx() }
      Canvas(
        modifier = Modifier
          .size(108.dp)
          .offset {
            IntOffset(
              (dragRootOffset.x - ghostSizePx / 2f).roundToInt(),
              (dragRootOffset.y - fingerLiftPx - ghostSizePx * 0.62f).roundToInt()
            )
          }
      ) {
        val scale = size.width / 108f
        drawHexStack(
          stack = dragStack,
          cx = size.width / 2f,
          cy = size.height * 0.65f,
          tileR = 34f,
          scaleFactor = scale,
          textMeasurer = textMeasurer,
          maxLift = 52f
        )
      }
    }

    if (state.showHelpDialog) {
      HelpRulesDialog(onDismiss = { onShowHelp(false) })
    }

    if (state.gameOver) {
      GameOverDialog(state = state, onRestart = onRestart)
    }
  }
}

@Composable
private fun TopHudBar(
  state: GameUiState,
  onUndo: () -> Unit,
  onToggleSound: () -> Unit,
  onShowHelp: () -> Unit,
  onRestart: () -> Unit,
  onSwitchMode: (GameMode) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFEF4444)))),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "⬡", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(
          text = "Wabenzahl",
          color = Color(0xFFF8FAFC),
          fontSize = 18.sp,
          fontWeight = FontWeight.ExtraBold
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SmallActionButton(
          enabled = state.canUndo,
          tag = "undo_button",
          onClick = onUndo
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Undo,
            contentDescription = "Rückgängig",
            tint = if (state.canUndo) Color(0xFFF8FAFC) else Color(0x55F8FAFC),
            modifier = Modifier.size(19.dp)
          )
        }
        SmallActionButton(
          enabled = true,
          tag = "sound_button",
          onClick = onToggleSound
        ) {
          Icon(
            imageVector = if (state.soundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = "Ton umschalten",
            tint = Color(0xFFF8FAFC),
            modifier = Modifier.size(19.dp)
          )
        }
        SmallActionButton(
          enabled = true,
          tag = "help_button",
          onClick = onShowHelp
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = "Spielregeln",
            tint = Color(0xFFF8FAFC),
            modifier = Modifier.size(19.dp)
          )
        }
        SmallActionButton(
          enabled = !state.isAnimating,
          tag = "restart_button",
          onClick = onRestart
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Neues Spiel",
            tint = Color(0xFFF8FAFC),
            modifier = Modifier.size(19.dp)
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        StatPill(label = "PUNKTE", value = state.score.toString())
        StatPill(label = "REKORD", value = state.bestScore.toString())
        StatPill(label = "GELÖST", value = state.clearedCount.toString())
      }

      Surface(
        shape = RoundedCornerShape(11.dp),
        color = Color(0xF0131C31),
        border = BorderStroke(1.dp, Color(0x2E94A3B8))
      ) {
        Row(
          modifier = Modifier.padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          GameMode.entries.forEach { mode ->
            val selected = state.activeMode == mode
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                  if (selected) {
                    Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF2563EB)))
                  } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                  }
                )
                .clickable(enabled = !state.isAnimating) { onSwitchMode(mode) }
                .padding(horizontal = 9.dp, vertical = 6.dp)
                .testTag("mode_${mode.key}"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = mode.label,
                color = if (selected) Color.White else Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SmallActionButton(
  enabled: Boolean,
  tag: String,
  onClick: () -> Unit,
  content: @Composable () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color(0xD91C2842),
    border = BorderStroke(1.dp, Color(0x2E94A3B8)),
    modifier = Modifier
      .size(36.dp)
      .testTag(tag)
  ) {
    IconButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxSize()
    ) {
      content()
    }
  }
}

@Composable
private fun StatPill(label: String, value: String) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color(0xE6131C31),
    border = BorderStroke(1.dp, Color(0x2E94A3B8))
  ) {
    Column(
      modifier = Modifier
        .widthIn(min = 48.dp)
        .padding(horizontal = 7.dp, vertical = 3.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = label,
        color = Color(0xFF94A3B8),
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 10.sp
      )
      Text(
        text = value,
        color = Color(0xFFF8FAFC),
        fontSize = 13.5.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 16.sp
      )
    }
  }
}

@Composable
private fun StatusStrip(state: GameUiState) {
  val text = state.statusOverride ?: when (state.activeMode) {
    GameMode.AUTO -> {
      if (state.selectedTrayIdx != null) "Freie Wabe antippen, um den Stapel zu setzen"
      else "Stapel auf eine freie Wabe ziehen oder antippen"
    }
    GameMode.MANUAL -> {
      val selectedKey = state.selectedBoardKey
      if (selectedKey != null) {
        val topNum = state.board[selectedKey]?.lastOrNull()
        "Grün markierten Nachbarstapel mit $topNum antippen"
      } else if (state.selectedTrayIdx != null) {
        "Freie Wabe antippen, um neuen Stapel abzulegen"
      } else {
        "Ausgangsstapel antippen oder Stapel aus Ablage setzen"
      }
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (state.activeMode == GameMode.AUTO) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0x470E7490),
        border = BorderStroke(1.dp, Color(0x5938BDF8)),
        modifier = Modifier.padding(end = 6.dp)
      ) {
        Text(
          text = "↑ Zug nach oben-links",
          color = Color(0xFFBAE6FD),
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
      }
    }
    Text(
      text = text,
      color = Color(0xFF94A3B8),
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun HelpRulesDialog(onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = Color(0xFF131C31),
      border = BorderStroke(1.dp, Color(0x5C94A3B8)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "So wird gespielt",
            color = Color(0xFFF8FAFC),
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color(0xFFF8FAFC))
          }
        }

        Column(
          modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          RuleParagraph(
            title = "1. Stapel erkennen",
            body = "Jede Zahl von 1 bis 7 hat eine eigene Farbe. Die Steine sind leicht versetzt gezeichnet, sodass du die nächste freiliegende Zahl direkt darunter ablesen kannst."
          )
          RuleParagraph(
            title = "2. Modus „Automatisch“",
            body = "Setze einen Stapel auf eine freie Wabe (Ziehen oder Antippen). Berühren sich oben gleiche Zahlen, wandern alle zusammenhängenden obersten Steine gemeinsam auf eine Wabe – bei Kettenreaktionen automatisch weiter."
          )
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0x400E7490),
            border = BorderStroke(1.dp, Color(0x4D38BDF8))
          ) {
            Text(
              text = "Feste Zugrichtung (↑↖): Steine wandern immer zur am weitesten oben liegenden Wabe (bei gleicher Höhe weiter links). So springt nie etwas hin und her.",
              color = Color(0xFFE2E8F0),
              fontSize = 12.5.sp,
              lineHeight = 17.sp,
              modifier = Modifier.padding(10.dp)
            )
          }
          RuleParagraph(
            title = "3. Modus „Selbst sortieren“",
            body = "Tippe zuerst deinen Ausgangsstapel an und dann den passenden Nachbarstapel mit derselben Zahl oben. Neue Stapel aus der Ablage kannst du jederzeit auf freie Waben setzen."
          )
          RuleParagraph(
            title = "4. Auflösen ab 6 Steinen",
            body = "Liegen mindestens 6 gleiche Steine oben zusammen auf einem Stapel, lösen sie sich auf und geben Punkte. Darunterliegende Steine bleiben erhalten!"
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            for (n in 1..7) {
              val st = TilePalette.get(n)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(32.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(st.mid),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = n.toString(),
                  color = st.text,
                  fontWeight = FontWeight.Black,
                  fontSize = 14.sp
                )
              }
            }
          }
        }

        Button(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
          Text("Verstanden", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
      }
    }
  }
}

@Composable
private fun RuleParagraph(title: String, body: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(text = title, color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Text(text = body, color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 18.sp)
  }
}

@Composable
private fun GameOverDialog(state: GameUiState, onRestart: () -> Unit) {
  Dialog(onDismissRequest = {}) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = Color(0xFF131C31),
      border = BorderStroke(1.dp, Color(0x5C94A3B8)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text(
          text = "Keine Züge mehr!",
          color = Color(0xFFF8FAFC),
          fontSize = 20.sp,
          fontWeight = FontWeight.ExtraBold
        )
        Text(
          text = if (state.activeMode == GameMode.AUTO) {
            "Alle 19 Waben sind belegt."
          } else {
            "Keine freien Waben und keine passenden Nachbarn mehr."
          },
          color = Color(0xFF94A3B8),
          fontSize = 13.5.sp,
          textAlign = TextAlign.Center
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          GameOverStatBox("Punkte", state.score.toString(), Modifier.weight(1f))
          GameOverStatBox("Rekord", state.bestScore.toString(), Modifier.weight(1f))
          GameOverStatBox("Gelöst", state.clearedCount.toString(), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(2.dp))

        Button(
          onClick = onRestart,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("gameover_restart_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
          Text("Neues Spiel", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
      }
    }
  }
}

@Composable
private fun GameOverStatBox(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFF1C2842)
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(text = label, color = Color(0xFF94A3B8), fontSize = 11.sp)
      Text(text = value, color = Color(0xFFF8FAFC), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
  }
}
