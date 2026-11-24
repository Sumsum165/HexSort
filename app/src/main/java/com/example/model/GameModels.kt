package com.example.model

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class GameMode(val key: String, val label: String) {
  AUTO("auto", "Automatisch"),
  MANUAL("manual", "Selbst sortieren")
}

data class TileStyle(
  val number: Int,
  val name: String,
  val top: Color,
  val mid: Color,
  val side: Color,
  val text: Color
)

object TilePalette {
  val styles: Map<Int, TileStyle> = mapOf(
    1 to TileStyle(1, "Koralle", Color(0xFFF87171), Color(0xFFEF4444), Color(0xFFB91C1C), Color.White),
    2 to TileStyle(2, "Amber", Color(0xFFFCD34D), Color(0xFFF59E0B), Color(0xFFB45309), Color(0xFF1E1605)),
    3 to TileStyle(3, "Smaragd", Color(0xFF34D399), Color(0xFF10B981), Color(0xFF047857), Color.White),
    4 to TileStyle(4, "Himmel", Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF1D4ED8), Color.White),
    5 to TileStyle(5, "Violett", Color(0xFFA78BFA), Color(0xFF8B5CF6), Color(0xFF6D28D9), Color.White),
    6 to TileStyle(6, "Beere", Color(0xFFF472B6), Color(0xFFEC4899), Color(0xFFBE185D), Color.White),
    7 to TileStyle(7, "Türkis", Color(0xFF2DD4BF), Color(0xFF14B8A6), Color(0xFF0F766E), Color.White)
  )

  fun get(number: Int): TileStyle = styles[number] ?: styles.getValue(1)
}

data class HexCell(
  val key: String,
  val q: Int,
  val r: Int,
  val normX: Float,
  val normY: Float,
  val priority: Int,
  val neighbors: List<String>
)

object HexGrid {
  const val RADIUS = 2
  const val DISSOLVE_MIN = 6
  const val HEX_SY = 0.87f
  const val CELL_R = 44f
  const val TILE_R = 40.5f
  const val VIEW_W = 368f
  const val VIEW_H = 376f
  const val VIEW_MIN_X = 0f
  const val VIEW_MIN_Y = -14f
  const val CENTER_X = 184f
  const val CENTER_Y = 182f

  private val DIRECTIONS = listOf(
    0 to -1,
    1 to -1,
    1 to 0,
    0 to 1,
    -1 to 1,
    -1 to 0
  )

  val cells: List<HexCell>
  val byKey: Map<String, HexCell>

  init {
    val raw = mutableListOf<HexCell>()
    for (q in -RADIUS..RADIUS) {
      val r1 = max(-RADIUS, -q - RADIUS)
      val r2 = min(RADIUS, -q + RADIUS)
      for (r in r1..r2) {
        val x = CENTER_X + 1.5f * CELL_R * q
        val y = CENTER_Y + sqrt(3f) * CELL_R * HEX_SY * (r + q / 2f)
        raw.add(
          HexCell(
            key = "$q,$r",
            q = q,
            r = r,
            normX = x,
            normY = y,
            priority = 0,
            neighbors = emptyList()
          )
        )
      }
    }

    // Feste Zugrichtung: immer zuerst nach oben (kleinstes Y), bei gleicher Höhe nach links (kleinstes X)
    raw.sortWith { a, b ->
      if (abs(a.normY - b.normY) > 0.5f) a.normY.compareTo(b.normY)
      else a.normX.compareTo(b.normX)
    }

    val tempMap = raw.associateBy { it.key }
    val built = raw.mapIndexed { idx, c ->
      val nbs = DIRECTIONS.mapNotNull { (dq, dr) ->
        tempMap["${c.q + dq},${c.r + dr}"]?.key
      }
      c.copy(priority = idx, neighbors = nbs)
    }
    cells = built
    byKey = built.associateBy { it.key }
  }
}

data class UndoSnapshot(
  val board: Map<String, List<Int>>,
  val tray: List<List<Int>?>,
  val score: Int,
  val clearedCount: Int
)

data class ModeState(
  val board: Map<String, List<Int>>,
  val tray: List<List<Int>?>,
  val score: Int = 0,
  val clearedCount: Int = 0,
  val undoStack: List<UndoSnapshot> = emptyList(),
  val gameOver: Boolean = false
)

data class FlyingTileAnim(
  val sources: List<HexCell>,
  val target: HexCell,
  val number: Int,
  val progress: Float
)

data class FloatingPopup(
  val id: Long,
  val svgX: Float,
  val svgY: Float,
  val text: String,
  val isChain: Boolean
)

data class GameUiState(
  val activeMode: GameMode = GameMode.AUTO,
  val soundEnabled: Boolean = true,
  val bestAuto: Int = 0,
  val bestManual: Int = 0,
  val board: Map<String, List<Int>> = emptyMap(),
  val tray: List<List<Int>?> = listOf(null, null, null),
  val score: Int = 0,
  val clearedCount: Int = 0,
  val canUndo: Boolean = false,
  val gameOver: Boolean = false,
  val selectedTrayIdx: Int? = null,
  val selectedBoardKey: String? = null,
  val hoverCellKey: String? = null,
  val isAnimating: Boolean = false,
  val statusOverride: String? = null,
  val flyingAnim: FlyingTileAnim? = null,
  val popups: List<FloatingPopup> = emptyList(),
  val showHelpDialog: Boolean = false
) {
  val bestScore: Int
    get() = if (activeMode == GameMode.AUTO) bestAuto else bestManual
}
