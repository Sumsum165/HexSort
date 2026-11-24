package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameMode
import com.example.model.GameUiState
import com.example.model.HexGrid
import com.example.model.TilePalette
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class BoardMetrics(
  val size: IntSize = IntSize.Zero,
  val rootPosition: Offset = Offset.Zero
) {
  val scale: Float
    get() {
      if (size.width <= 0 || size.height <= 0) return 1f
      return min(size.width / HexGrid.VIEW_W, size.height / HexGrid.VIEW_H)
    }

  val offsetX: Float
    get() = (size.width - HexGrid.VIEW_W * scale) / 2f

  val offsetY: Float
    get() = (size.height - HexGrid.VIEW_H * scale) / 2f

  fun toScreenX(svgX: Float): Float = offsetX + (svgX - HexGrid.VIEW_MIN_X) * scale
  fun toScreenY(svgY: Float): Float = offsetY + (svgY - HexGrid.VIEW_MIN_Y) * scale

  fun findCellAtLocalPoint(localX: Float, localY: Float, emptyOnly: Boolean, board: Map<String, List<Int>>): String? {
    if (scale <= 0f) return null
    val svgX = HexGrid.VIEW_MIN_X + (localX - offsetX) / scale
    val svgY = HexGrid.VIEW_MIN_Y + (localY - offsetY) / scale
    var bestKey: String? = null
    var bestDist = 47f
    for (cell in HexGrid.cells) {
      if (emptyOnly && !board[cell.key].isNullOrEmpty()) continue
      val dist = hypot(cell.normX - svgX, cell.normY - svgY)
      if (dist < bestDist) {
        bestDist = dist
        bestKey = cell.key
      }
    }
    return bestKey
  }
}

fun buildHexPath(cx: Float, cy: Float, r: Float, sy: Float): Path {
  val halfH = (sqrt(3f) / 2f) * r * sy
  return Path().apply {
    moveTo(cx + r, cy)
    lineTo(cx + r * 0.5f, cy + halfH)
    lineTo(cx - r * 0.5f, cy + halfH)
    lineTo(cx - r, cy)
    lineTo(cx - r * 0.5f, cy - halfH)
    lineTo(cx + r * 0.5f, cy - halfH)
    close()
  }
}

fun buildFrontSkirtPath(cx: Float, cy: Float, r: Float, sy: Float, depth: Float): Path {
  val halfH = (sqrt(3f) / 2f) * r * sy
  return Path().apply {
    moveTo(cx - r, cy)
    lineTo(cx - r * 0.5f, cy + halfH)
    lineTo(cx + r * 0.5f, cy + halfH)
    lineTo(cx + r, cy)
    lineTo(cx + r, cy + depth)
    lineTo(cx + r * 0.5f, cy + halfH + depth)
    lineTo(cx - r * 0.5f, cy + halfH + depth)
    lineTo(cx - r, cy + depth)
    close()
  }
}

fun DrawScope.drawHexStack(
  stack: List<Int>,
  cx: Float,
  cy: Float,
  tileR: Float,
  scaleFactor: Float,
  textMeasurer: TextMeasurer,
  maxLift: Float = 62f,
  compactText: Boolean = false
) {
  if (stack.isEmpty()) return
  val n = stack.size
  val rawSteps = FloatArray((n - 1).coerceAtLeast(0))
  for (i in 0 until n - 1) {
    rawSteps[i] = if (stack[i] != stack[i + 1]) 11.4f else 5.2f
  }

  val totalLift = rawSteps.sum()
  val liftScale = if (totalLift > maxLift && totalLift > 0f) maxLift / totalLift else 1f
  var topTransitionIdx = -1
  for (t in n - 2 downTo 0) {
    if (stack[t] != stack[t + 1]) {
      topTransitionIdx = t
      break
    }
  }

  val yOffsets = FloatArray(n)
  var acc = 0f
  for (j in rawSteps.indices) {
    var step = rawSteps[j] * liftScale
    if (j == topTransitionIdx && step < 9.4f) {
      step = 9.4f
    } else if (stack[j] != stack[j + 1] && step < 7.8f) {
      step = 7.8f
    }
    acc += step
    yOffsets[j + 1] = acc
  }

  val scaledR = tileR * scaleFactor
  val halfH = (sqrt(3f) / 2f) * scaledR * HexGrid.HEX_SY
  val baseDepth = 8.6f * scaleFactor

  // Weicher Bodenschatten unter dem Stapel
  drawPath(
    path = buildHexPath(cx, cy + 4.5f * scaleFactor, scaledR * 1.02f, HexGrid.HEX_SY),
    color = Color(0x66020617)
  )

  for (idx in 0 until n) {
    val num = stack[idx]
    val style = TilePalette.get(num)
    val tileCy = cy - yOffsets[idx] * scaleFactor
    val isTop = idx == n - 1
    val nextDiffers = idx < n - 1 && stack[idx] != stack[idx + 1]
    val stepBelowUpper = if (idx < n - 1) (yOffsets[idx + 1] - yOffsets[idx]) * scaleFactor else baseDepth
    val skirtDepth = if (isTop) baseDepth else maxOf(stepBelowUpper + 1.4f * scaleFactor, 6.2f * scaleFactor)

    // Vorderkante (3D-Tiefe)
    val skirt = buildFrontSkirtPath(cx, tileCy, scaledR, HexGrid.HEX_SY, skirtDepth)
    drawPath(path = skirt, color = style.side)
    drawPath(path = skirt, color = Color(0x55020617), style = Stroke(width = 0.9f * scaleFactor))

    // Obere Hex-Fläche
    val topFace = buildHexPath(cx, tileCy, scaledR, HexGrid.HEX_SY)
    drawPath(path = topFace, color = if (isTop) style.top else style.mid)
    drawPath(
      path = topFace,
      color = Color.White.copy(alpha = if (isTop) 0.45f else 0.16f),
      style = Stroke(width = (if (isTop) 1.4f else 0.8f) * scaleFactor)
    )

    // Freiliegende Zahl direkt auf der Frontlippe unter Farbwechsel anzeigen
    if (!isTop && nextDiffers && stepBelowUpper >= 7.0f * scaleFactor) {
      val lipFontPx = ((if (compactText) 8.5f else 10.2f) * scaleFactor).coerceAtLeast(10f)
      val layout = textMeasurer.measure(
        text = num.toString(),
        style = TextStyle(
          color = Color.White,
          fontSize = with(density) { lipFontPx.toSp() },
          fontWeight = FontWeight.ExtraBold
        )
      )
      val lipY = tileCy + halfH + minOf(stepBelowUpper * 0.52f, 6.2f * scaleFactor)
      drawText(
        textLayoutResult = layout,
        topLeft = Offset(cx - layout.size.width / 2f, lipY - layout.size.height / 2f)
      )
    }

    // Oberster Stein: große Zahl + Stapelzähler-Pill
    if (isTop) {
      val topFontPx = (if (compactText) 17.5f else 21f) * scaleFactor
      val numLayout = textMeasurer.measure(
        text = num.toString(),
        style = TextStyle(
          color = style.text,
          fontSize = with(density) { topFontPx.toSp() },
          fontWeight = FontWeight.Black
        )
      )
      drawText(
        textLayoutResult = numLayout,
        topLeft = Offset(cx - numLayout.size.width / 2f, tileCy - numLayout.size.height / 2f)
      )

      var runLen = 0
      for (k in stack.indices.reversed()) {
        if (stack[k] == num) runLen++ else break
      }
      if (runLen >= 2) {
        val badgeW = 22f * scaleFactor
        val badgeH = 14.5f * scaleFactor
        val bx = cx + scaledR * 0.44f
        val by = tileCy - halfH * 0.56f
        val pillColor = if (runLen >= HexGrid.DISSOLVE_MIN) Color(0xFF10B981) else Color(0xCC0F172A)

        drawRoundRect(
          color = pillColor,
          topLeft = Offset(bx - badgeW / 2f, by - badgeH / 2f),
          size = Size(badgeW, badgeH),
          cornerRadius = CornerRadius(badgeH / 2f, badgeH / 2f)
        )
        drawRoundRect(
          color = Color.White.copy(alpha = 0.42f),
          topLeft = Offset(bx - badgeW / 2f, by - badgeH / 2f),
          size = Size(badgeW, badgeH),
          cornerRadius = CornerRadius(badgeH / 2f, badgeH / 2f),
          style = Stroke(width = 1f * scaleFactor)
        )

        val badgeFontPx = 9.5f * scaleFactor
        val badgeLayout = textMeasurer.measure(
          text = "×$runLen",
          style = TextStyle(
            color = Color.White,
            fontSize = with(density) { badgeFontPx.toSp() },
            fontWeight = FontWeight.Bold
          )
        )
        drawText(
          textLayoutResult = badgeLayout,
          topLeft = Offset(bx - badgeLayout.size.width / 2f, by - badgeLayout.size.height / 2f)
        )
      }
    }
  }
}

@Composable
fun HexBoardCanvas(
  state: GameUiState,
  textMeasurer: TextMeasurer,
  onMetricsChanged: (BoardMetrics) -> Unit,
  onCellTapped: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var metrics by remember { mutableStateOf(BoardMetrics()) }
  val density = LocalDensity.current

  val pulseTransition = rememberInfiniteTransition(label = "neighborPulse")
  val pulseAlpha by pulseTransition.animateFloat(
    initialValue = 1f,
    targetValue = 0.4f,
    animationSpec = infiniteRepeatable(
      animation = tween(900),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  val selectedCell = state.selectedBoardKey?.let { HexGrid.byKey[it] }
  val selectedTop = state.selectedBoardKey?.let { state.board[it]?.lastOrNull() }

  Box(
    modifier = modifier
      .fillMaxSize()
      .onGloballyPositioned { coords ->
        val updated = BoardMetrics(
          size = coords.size,
          rootPosition = coords.positionInRoot()
        )
        if (updated != metrics) {
          metrics = updated
          onMetricsChanged(updated)
        }
      }
      .pointerInput(state.board, state.isAnimating, state.selectedTrayIdx, state.selectedBoardKey, state.activeMode) {
        detectTapGestures { offset ->
          val hitKey = metrics.findCellAtLocalPoint(offset.x, offset.y, emptyOnly = false, board = state.board)
          if (hitKey != null) {
            onCellTapped(hitKey)
          }
        }
      }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val s = min(size.width / HexGrid.VIEW_W, size.height / HexGrid.VIEW_H)
      val ox = (size.width - HexGrid.VIEW_W * s) / 2f
      val oy = (size.height - HexGrid.VIEW_H * s) / 2f

      fun sx(v: Float) = ox + (v - HexGrid.VIEW_MIN_X) * s
      fun sy(v: Float) = oy + (v - HexGrid.VIEW_MIN_Y) * s

      // 1. Waben-Slots zeichnen
      for (cell in HexGrid.cells) {
        val cx = sx(cell.normX)
        val cy = sy(cell.normY)
        val stack = state.board[cell.key].orEmpty()
        val isEmpty = stack.isEmpty()

        val isDropHover = state.hoverCellKey == cell.key && isEmpty
        val isCanDrop = state.selectedTrayIdx != null && isEmpty
        val isSourceSelected = state.selectedBoardKey == cell.key
        val isValidManualNeighbor = state.activeMode == GameMode.MANUAL &&
          selectedCell != null &&
          selectedTop != null &&
          cell.key != selectedCell.key &&
          selectedCell.neighbors.contains(cell.key) &&
          stack.lastOrNull() == selectedTop

        val slotFill = when {
          isDropHover -> Color(0xFF27406B)
          isValidManualNeighbor -> Color(0xFF1A3A3D)
          isCanDrop -> Color(0xFF1F2E4E)
          else -> Color(0xFF19243D)
        }
        val strokeColor = when {
          isDropHover -> Color(0xFF38BDF8)
          isSourceSelected -> Color(0xFFFBBF24)
          isValidManualNeighbor -> Color(0xFF34D399).copy(alpha = pulseAlpha)
          isCanDrop -> Color(0x7338BDF8)
          else -> Color(0x3694A3B8)
        }
        val strokeWidth = when {
          isDropHover || isSourceSelected || isValidManualNeighbor -> 3.0f * s
          else -> 1.6f * s
        }

        val slotPath = buildHexPath(cx, cy, (HexGrid.CELL_R - 1.5f) * s, HexGrid.HEX_SY)
        drawPath(path = slotPath, color = slotFill)
        drawPath(path = slotPath, color = strokeColor, style = Stroke(width = strokeWidth))
      }

      // 2. Stapel von oben nach unten zeichnen (CELLS sind bereits nach Y, X sortiert)
      for (cell in HexGrid.cells) {
        val stack = state.board[cell.key].orEmpty()
        if (stack.isEmpty()) continue
        drawHexStack(
          stack = stack,
          cx = sx(cell.normX),
          cy = sy(cell.normY),
          tileR = HexGrid.TILE_R,
          scaleFactor = s,
          textMeasurer = textMeasurer,
          maxLift = 62f
        )
      }

      // 3. Flug-Animation bei Zusammenführung
      val anim = state.flyingAnim
      if (anim != null) {
        val style = TilePalette.get(anim.number)
        val t = anim.progress.coerceIn(0f, 1f)
        val ease = 1f - (1f - t).pow(3)
        val tx = sx(anim.target.normX)
        val ty = sy(anim.target.normY)

        for (src in anim.sources) {
          val srcX = sx(src.normX)
          val srcY = sy(src.normY)
          drawLine(
            color = style.top.copy(alpha = 0.65f),
            start = Offset(srcX, srcY),
            end = Offset(tx, ty),
            strokeWidth = 2.6f * s,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * s, 6f * s))
          )

          val curX = srcX + (tx - srcX) * ease
          val curY = srcY + (ty - srcY) * ease - sin(t * PI.toFloat()) * 18f * s
          val flyPath = buildHexPath(curX, curY, HexGrid.TILE_R * 0.88f * s, HexGrid.HEX_SY)
          drawPath(path = flyPath, color = style.top)
          drawPath(path = flyPath, color = Color.White, style = Stroke(width = 1.6f * s))

          val numLayout = textMeasurer.measure(
            text = anim.number.toString(),
            style = TextStyle(
              color = style.text,
              fontSize = with(density) { (17f * s).toSp() },
              fontWeight = FontWeight.Black
            )
          )
          drawText(
            textLayoutResult = numLayout,
            topLeft = Offset(curX - numLayout.size.width / 2f, curY - numLayout.size.height / 2f)
          )
        }
      }
    }

    // 4. Schwebende Punkte-Einblendungen
    for (popup in state.popups) {
      val px = metrics.toScreenX(popup.svgX)
      val py = metrics.toScreenY(popup.svgY)
      Text(
        text = popup.text,
        color = if (popup.isChain) Color(0xFF67E8F9) else Color(0xFFFEF08A),
        fontWeight = FontWeight.Black,
        fontSize = if (popup.isChain) 16.sp else 15.sp,
        modifier = Modifier.offset {
          IntOffset((px - 42.dp.toPx()).roundToInt(), (py - 12.dp.toPx()).roundToInt())
        }
      )
    }
  }
}
