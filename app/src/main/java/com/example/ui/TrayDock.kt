package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp

@Composable
fun TrayDock(
  tray: List<List<Int>?>,
  selectedTrayIdx: Int?,
  draggingTrayIdx: Int?,
  textMeasurer: TextMeasurer,
  onSelectTray: (Int) -> Unit,
  onDragStart: (Int, Offset) -> Unit,
  onDragMove: (Offset) -> Unit,
  onDragEnd: (Int, Offset) -> Unit,
  onDragCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = Color(0xEB131C31),
    border = BorderStroke(1.dp, Color(0x2E94A3B8))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      tray.forEachIndexed { idx, stack ->
        TraySlotCard(
          stack = stack,
          isSelected = selectedTrayIdx == idx,
          isDraggingSource = draggingTrayIdx == idx,
          textMeasurer = textMeasurer,
          onTap = { onSelectTray(idx) },
          onDragStart = { rootPos -> onDragStart(idx, rootPos) },
          onDragMove = onDragMove,
          onDragEnd = { rootPos -> onDragEnd(idx, rootPos) },
          onDragCancel = onDragCancel,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun TraySlotCard(
  stack: List<Int>?,
  isSelected: Boolean,
  isDraggingSource: Boolean,
  textMeasurer: TextMeasurer,
  onTap: () -> Unit,
  onDragStart: (Offset) -> Unit,
  onDragMove: (Offset) -> Unit,
  onDragEnd: (Offset) -> Unit,
  onDragCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  var slotRootPos by remember { mutableStateOf(Offset.Zero) }
  var currentDragRoot by remember { mutableStateOf(Offset.Zero) }

  val borderColor = when {
    stack == null -> Color(0x2494A3B8)
    isSelected -> Color(0xFF38BDF8)
    else -> Color.Transparent
  }
  val bgColor = when {
    stack == null -> Color(0x590F172A)
    isSelected -> Color(0x591E3A8A)
    else -> Color(0xB80F172A)
  }

  Box(
    modifier = modifier
      .height(78.dp)
      .background(bgColor, RoundedCornerShape(12.dp))
      .then(
        if (borderColor != Color.Transparent) {
          Modifier.padding(0.dp)
        } else Modifier
      )
      .onGloballyPositioned { coords ->
        slotRootPos = coords.positionInRoot()
      }
      .pointerInput(stack) {
        if (stack != null) {
          detectTapGestures(onTap = { onTap() })
        }
      }
      .pointerInput(stack) {
        if (stack != null) {
          detectDragGestures(
            onDragStart = { startOffset ->
              currentDragRoot = slotRootPos + startOffset
              onDragStart(currentDragRoot)
            },
            onDrag = { change, dragAmount ->
              change.consume()
              currentDragRoot += dragAmount
              onDragMove(currentDragRoot)
            },
            onDragEnd = {
              onDragEnd(currentDragRoot)
            },
            onDragCancel = {
              onDragCancel()
            }
          )
        }
      }
      .alpha(if (isDraggingSource) 0.28f else 1f),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = RoundedCornerShape(12.dp),
      color = Color.Transparent,
      border = BorderStroke(
        width = if (isSelected) 2.dp else 1.dp,
        color = borderColor
      )
    ) {
      if (stack != null) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val scale = minOf(size.width / 100f, size.height / 100f)
          drawHexStack(
            stack = stack,
            cx = size.width / 2f,
            cy = size.height * 0.66f,
            tileR = 31f,
            scaleFactor = scale,
            textMeasurer = textMeasurer,
            maxLift = 44f,
            compactText = true
          )
        }
      }
    }
  }
}
