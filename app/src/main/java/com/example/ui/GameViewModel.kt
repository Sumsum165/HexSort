package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.FloatingPopup
import com.example.model.FlyingTileAnim
import com.example.model.GameMode
import com.example.model.GameUiState
import com.example.model.HexCell
import com.example.model.HexGrid
import com.example.model.ModeState
import com.example.model.UndoSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {

  private val prefs = application.getSharedPreferences("wabenzahl_prefs", Context.MODE_PRIVATE)
  private var toneGen: ToneGenerator? = null

  private val modeStates = mutableMapOf<GameMode, ModeState>()
  private var bestAuto = 0
  private var bestManual = 0
  private var popupCounter = 0L

  private val _uiState = MutableStateFlow(GameUiState())
  val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

  init {
    loadOrInit()
  }

  override fun onCleared() {
    super.onCleared()
    try {
      toneGen?.release()
    } catch (_: Exception) {
    }
    toneGen = null
  }

  private fun loadOrInit() {
    val saved = prefs.getString("save_v1", null)
    var activeMode = GameMode.AUTO
    var soundEnabled = true

    if (saved != null) {
      try {
        val root = JSONObject(saved)
        activeMode = if (root.optString("activeMode") == GameMode.MANUAL.key) GameMode.MANUAL else GameMode.AUTO
        soundEnabled = root.optBoolean("soundEnabled", true)
        bestAuto = root.optInt("bestAuto", 0)
        bestManual = root.optInt("bestManual", 0)

        modeStates[GameMode.AUTO] = decodeModeState(root.getJSONObject("auto"), GameMode.AUTO)
        modeStates[GameMode.MANUAL] = decodeModeState(root.getJSONObject("manual"), GameMode.MANUAL)
      } catch (_: Exception) {
        modeStates.clear()
      }
    }

    if (!modeStates.containsKey(GameMode.AUTO)) {
      modeStates[GameMode.AUTO] = createFreshModeState(GameMode.AUTO)
    }
    if (!modeStates.containsKey(GameMode.MANUAL)) {
      modeStates[GameMode.MANUAL] = createFreshModeState(GameMode.MANUAL)
    }

    publishState(activeMode = activeMode, soundEnabled = soundEnabled)
  }

  private fun saveToPrefs() {
    try {
      val s = _uiState.value
      val root = JSONObject().apply {
        put("activeMode", s.activeMode.key)
        put("soundEnabled", s.soundEnabled)
        put("bestAuto", bestAuto)
        put("bestManual", bestManual)
        put("auto", encodeModeState(modeStates.getValue(GameMode.AUTO)))
        put("manual", encodeModeState(modeStates.getValue(GameMode.MANUAL)))
      }
      prefs.edit().putString("save_v1", root.toString()).apply()
    } catch (_: Exception) {
    }
  }

  private fun encodeModeState(ms: ModeState): JSONObject {
    val boardObj = JSONObject()
    ms.board.forEach { (k, list) ->
      boardObj.put(k, JSONArray(list))
    }
    val trayArr = JSONArray()
    ms.tray.forEach { stack ->
      if (stack == null) trayArr.put(JSONObject.NULL)
      else trayArr.put(JSONArray(stack))
    }
    return JSONObject().apply {
      put("board", boardObj)
      put("tray", trayArr)
      put("score", ms.score)
      put("clearedCount", ms.clearedCount)
      put("gameOver", ms.gameOver)
    }
  }

  private fun decodeModeState(obj: JSONObject, mode: GameMode): ModeState {
    val boardObj = obj.getJSONObject("board")
    val board = mutableMapOf<String, List<Int>>()
    HexGrid.cells.forEach { cell ->
      val arr = boardObj.optJSONArray(cell.key)
      val list = mutableListOf<Int>()
      if (arr != null) {
        for (i in 0 until arr.length()) list.add(arr.getInt(i))
      }
      board[cell.key] = list
    }
    val trayArr = obj.getJSONArray("tray")
    val tray = mutableListOf<List<Int>?>()
    for (i in 0 until 3) {
      val item = trayArr.optJSONArray(i)
      if (item == null) {
        tray.add(null)
      } else {
        val list = mutableListOf<Int>()
        for (j in 0 until item.length()) list.add(item.getInt(j))
        tray.add(list)
      }
    }
    return ModeState(
      board = board,
      tray = tray,
      score = obj.optInt("score", 0),
      clearedCount = obj.optInt("clearedCount", 0),
      undoStack = emptyList(),
      gameOver = obj.optBoolean("gameOver", false)
    )
  }

  private fun publishState(
    activeMode: GameMode = _uiState.value.activeMode,
    soundEnabled: Boolean = _uiState.value.soundEnabled,
    selectedTrayIdx: Int? = _uiState.value.selectedTrayIdx,
    selectedBoardKey: String? = _uiState.value.selectedBoardKey,
    hoverCellKey: String? = _uiState.value.hoverCellKey,
    isAnimating: Boolean = _uiState.value.isAnimating,
    statusOverride: String? = _uiState.value.statusOverride,
    flyingAnim: FlyingTileAnim? = _uiState.value.flyingAnim
  ) {
    val ms = modeStates.getValue(activeMode)
    _uiState.update { cur ->
      cur.copy(
        activeMode = activeMode,
        soundEnabled = soundEnabled,
        bestAuto = bestAuto,
        bestManual = bestManual,
        board = ms.board,
        tray = ms.tray,
        score = ms.score,
        clearedCount = ms.clearedCount,
        canUndo = !isAnimating && ms.undoStack.isNotEmpty(),
        gameOver = ms.gameOver,
        selectedTrayIdx = selectedTrayIdx,
        selectedBoardKey = selectedBoardKey,
        hoverCellKey = hoverCellKey,
        isAnimating = isAnimating,
        statusOverride = statusOverride,
        flyingAnim = flyingAnim
      )
    }
  }

  private fun maxActiveNumber(score: Int): Int = when {
    score >= 420 -> 7
    score >= 160 -> 6
    else -> 5
  }

  private fun generateMixedStack(score: Int, preferMultiLayer: Boolean): List<Int> {
    val maxNum = maxActiveNumber(score)
    val groupCount = if (preferMultiLayer) {
      if (Random.nextFloat() < 0.72f) 2 else 3
    } else {
      val r = Random.nextFloat()
      when {
        r < 0.22f -> 1
        r < 0.75f -> 2
        else -> 3
      }
    }

    val usedNums = mutableListOf<Int>()
    while (usedNums.size < groupCount) {
      val candidate = Random.nextInt(1, maxNum + 1)
      if (!usedNums.contains(candidate)) {
        usedNums.add(candidate)
      }
    }

    val stack = mutableListOf<Int>()
    usedNums.forEachIndexed { idx, num ->
      val maxCount = when {
        groupCount == 1 -> 3
        idx == usedNums.lastIndex -> 3
        else -> 2
      }
      var count = Random.nextInt(1, maxCount + 1)
      if (stack.size + count > 5) count = maxOf(1, 5 - stack.size)
      repeat(count) { stack.add(num) }
    }
    return stack
  }

  private fun generateTray(score: Int): List<List<Int>?> = listOf(
    generateMixedStack(score, false),
    generateMixedStack(score, true),
    generateMixedStack(score, false)
  )

  private fun createSeededBoard(mode: GameMode): Map<String, List<Int>> {
    val board = HexGrid.cells.associate { it.key to emptyList<Int>() }.toMutableMap()
    val shuffled = HexGrid.cells.shuffled()

    if (mode == GameMode.AUTO) {
      var placed = 0
      for (cell in shuffled) {
        if (placed >= 5) break
        val num = Random.nextInt(1, 6)
        val hasMatchingNeighbor = cell.neighbors.any { nKey ->
          board[nKey]?.lastOrNull() == num
        }
        if (!hasMatchingNeighbor) {
          val stack = if (Random.nextFloat() < 0.65f) {
            listOf(Random.nextInt(1, 6), num)
          } else {
            listOf(num)
          }
          board[cell.key] = stack
          placed++
        }
      }
    } else {
      for (i in 0 until 10) {
        board[shuffled[i].key] = generateMixedStack(0, true)
      }
      val center = HexGrid.byKey.getValue("0,0")
      val nb = HexGrid.byKey.getValue(center.neighbors.first())
      val pairNum = Random.nextInt(1, 5)
      board[center.key] = listOf(Random.nextInt(1, 6), pairNum, pairNum)
      board[nb.key] = listOf(Random.nextInt(1, 6), pairNum)
    }

    return board
  }

  private fun createFreshModeState(mode: GameMode): ModeState = ModeState(
    board = createSeededBoard(mode),
    tray = generateTray(0)
  )

  private fun topRunLength(stack: List<Int>): Int {
    val top = stack.lastOrNull() ?: return 0
    var count = 0
    for (i in stack.indices.reversed()) {
      if (stack[i] == top) count++ else break
    }
    return count
  }

  private fun popTopRun(stack: List<Int>): Pair<List<Int>, Int> {
    val count = topRunLength(stack)
    return stack.dropLast(count) to count
  }

  private fun addScore(points: Int) {
    val mode = _uiState.value.activeMode
    val ms = modeStates.getValue(mode)
    val newScore = ms.score + points
    modeStates[mode] = ms.copy(score = newScore)
    if (mode == GameMode.AUTO && newScore > bestAuto) bestAuto = newScore
    if (mode == GameMode.MANUAL && newScore > bestManual) bestManual = newScore
  }

  private fun pushUndoSnapshot() {
    val mode = _uiState.value.activeMode
    val ms = modeStates.getValue(mode)
    val snap = UndoSnapshot(
      board = ms.board.mapValues { it.value.toList() },
      tray = ms.tray.map { it?.toList() },
      score = ms.score,
      clearedCount = ms.clearedCount
    )
    val nextUndo = (ms.undoStack + snap).takeLast(15)
    modeStates[mode] = ms.copy(undoStack = nextUndo)
  }

  private data class MergeGroup(
    val number: Int,
    val target: HexCell,
    val sources: List<HexCell>
  )

  private fun findConnectedTopGroups(board: Map<String, List<Int>>): List<MergeGroup> {
    val visited = mutableSetOf<String>()
    val groups = mutableListOf<MergeGroup>()

    for (startCell in HexGrid.cells) {
      if (visited.contains(startCell.key)) continue
      val top = board[startCell.key]?.lastOrNull() ?: continue

      val queue = ArrayDeque<String>()
      val component = mutableListOf<HexCell>()
      visited.add(startCell.key)
      queue.add(startCell.key)

      while (queue.isNotEmpty()) {
        val curKey = queue.removeFirst()
        val curCell = HexGrid.byKey.getValue(curKey)
        component.add(curCell)

        for (nbKey in curCell.neighbors) {
          if (!visited.contains(nbKey) && board[nbKey]?.lastOrNull() == top) {
            visited.add(nbKey)
            queue.add(nbKey)
          }
        }
      }

      if (component.size >= 2) {
        // Feste Zugrichtung: kleinste priority = am weitesten oben (bei Gleichstand links)
        component.sortBy { it.priority }
        groups.add(
          MergeGroup(
            number = top,
            target = component.first(),
            sources = component.drop(1)
          )
        )
      }
    }

    return groups
  }

  private fun isBoardFull(board: Map<String, List<Int>>): Boolean =
    HexGrid.cells.all { !board[it.key].isNullOrEmpty() }

  private fun hasAnyManualPair(board: Map<String, List<Int>>): Boolean {
    for (cell in HexGrid.cells) {
      val top = board[cell.key]?.lastOrNull() ?: continue
      for (nbKey in cell.neighbors) {
        if (board[nbKey]?.lastOrNull() == top) return true
      }
    }
    return false
  }

  private fun playBeep(tone: Int, durationMs: Int) {
    if (!_uiState.value.soundEnabled) return
    try {
      if (toneGen == null) {
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 38)
      }
      toneGen?.startTone(tone, durationMs)
    } catch (_: Exception) {
    }
  }

  private fun emitPopup(svgX: Float, svgY: Float, text: String, isChain: Boolean) {
    val id = ++popupCounter
    val popup = FloatingPopup(id, svgX, svgY, text, isChain)
    _uiState.update { it.copy(popups = it.popups + popup) }
    viewModelScope.launch {
      delay(950)
      _uiState.update { cur -> cur.copy(popups = cur.popups.filterNot { it.id == id }) }
    }
  }

  fun selectTrayStack(idx: Int) {
    val s = _uiState.value
    if (s.isAnimating || s.gameOver) return
    if (s.tray.getOrNull(idx) == null) return
    playBeep(ToneGenerator.TONE_PROP_BEEP, 40)
    val nextIdx = if (s.selectedTrayIdx == idx) null else idx
    publishState(selectedTrayIdx = nextIdx, selectedBoardKey = null, hoverCellKey = null)
  }

  fun updateHoverCell(cellKey: String?) {
    val s = _uiState.value
    if (s.hoverCellKey != cellKey) {
      publishState(hoverCellKey = cellKey)
    }
  }

  fun placeTrayStackOnCell(trayIdx: Int, cellKeyTarget: String) {
    val s = _uiState.value
    if (s.isAnimating || s.gameOver) return
    val mode = s.activeMode
    val ms = modeStates.getValue(mode)
    val trayStack = ms.tray.getOrNull(trayIdx) ?: return
    if (!ms.board[cellKeyTarget].isNullOrEmpty()) return

    pushUndoSnapshot()
    val updatedMs = modeStates.getValue(mode)
    val newBoard = updatedMs.board.toMutableMap()
    newBoard[cellKeyTarget] = trayStack.toList()
    val newTray = updatedMs.tray.toMutableList()
    newTray[trayIdx] = null
    val finalTray = if (newTray.all { it == null }) generateTray(updatedMs.score) else newTray

    modeStates[mode] = updatedMs.copy(board = newBoard, tray = finalTray)
    playBeep(ToneGenerator.TONE_PROP_ACK, 55)
    publishState(selectedTrayIdx = null, selectedBoardKey = null, hoverCellKey = null)

    viewModelScope.launch {
      if (mode == GameMode.AUTO) {
        resolveBoardAuto()
      } else {
        val placedCell = HexGrid.byKey.getValue(cellKeyTarget)
        if (topRunLength(modeStates.getValue(mode).board.getValue(cellKeyTarget)) >= HexGrid.DISSOLVE_MIN) {
          publishState(isAnimating = true)
          dissolveCellIfReady(placedCell, 1)
          publishState(isAnimating = false)
        }
        checkGameOver()
        saveToPrefs()
        publishState()
      }
    }
  }

  fun onBoardCellTap(cellKeyTapped: String) {
    val s = _uiState.value
    if (s.isAnimating || s.gameOver) return
    val mode = s.activeMode
    val ms = modeStates.getValue(mode)
    val tappedStack = ms.board[cellKeyTapped].orEmpty()
    val isEmpty = tappedStack.isEmpty()

    if (s.selectedTrayIdx != null) {
      if (isEmpty) {
        placeTrayStackOnCell(s.selectedTrayIdx, cellKeyTapped)
      } else if (mode == GameMode.MANUAL) {
        playBeep(ToneGenerator.TONE_PROP_BEEP, 40)
        publishState(selectedTrayIdx = null, selectedBoardKey = cellKeyTapped)
      }
      return
    }

    if (mode == GameMode.MANUAL) {
      val selectedKey = s.selectedBoardKey
      if (selectedKey == null) {
        if (!isEmpty) {
          playBeep(ToneGenerator.TONE_PROP_BEEP, 40)
          publishState(selectedBoardKey = cellKeyTapped)
        }
        return
      }

      if (selectedKey == cellKeyTapped) {
        publishState(selectedBoardKey = null)
        return
      }

      val srcCell = HexGrid.byKey.getValue(selectedKey)
      val srcTop = ms.board[selectedKey]?.lastOrNull()
      val dstTop = tappedStack.lastOrNull()

      if (srcCell.neighbors.contains(cellKeyTapped) && srcTop != null && dstTop == srcTop) {
        viewModelScope.launch {
          executeManualSort(selectedKey, cellKeyTapped)
        }
      } else if (!isEmpty) {
        playBeep(ToneGenerator.TONE_PROP_BEEP, 40)
        publishState(selectedBoardKey = cellKeyTapped)
      } else {
        publishState(selectedBoardKey = null)
      }
    }
  }

  private suspend fun animateFlight(sources: List<HexCell>, target: HexCell, num: Int) {
    val steps = 12
    for (i in 1..steps) {
      val progress = i / steps.toFloat()
      publishState(
        isAnimating = true,
        flyingAnim = FlyingTileAnim(sources, target, num, progress)
      )
      delay(18)
    }
    publishState(isAnimating = true, flyingAnim = null)
  }

  private suspend fun dissolveCellIfReady(cell: HexCell, chainStep: Int): Boolean {
    val mode = _uiState.value.activeMode
    val ms = modeStates.getValue(mode)
    val stack = ms.board[cell.key].orEmpty()
    val runLen = topRunLength(stack)
    if (runLen < HexGrid.DISSOLVE_MIN) return false

    val (remaining, removedCount) = popTopRun(stack)
    val bonus = if (chainStep > 1) chainStep * 10 else 0
    val pts = removedCount * 10 + bonus
    val newBoard = ms.board.toMutableMap()
    newBoard[cell.key] = remaining
    modeStates[mode] = ms.copy(
      board = newBoard,
      clearedCount = ms.clearedCount + removedCount
    )
    addScore(pts)
    playBeep(ToneGenerator.TONE_PROP_BEEP2, 120)

    val label = "+$pts" + if (chainStep > 1) " (Kette ×$chainStep)" else ""
    emitPopup(cell.normX, cell.normY - 18f, label, chainStep > 1)
    publishState(isAnimating = true)
    delay(220)
    return true
  }

  private suspend fun resolveBoardAuto() {
    val mode = GameMode.AUTO
    publishState(isAnimating = true)
    var chainStep = 0

    while (true) {
      val currentBoard = modeStates.getValue(mode).board
      val groups = findConnectedTopGroups(currentBoard)
      if (groups.isNotEmpty()) {
        chainStep++
        val sortedGroups = groups.sortedBy { it.target.priority }
        val grp = sortedGroups.first()

        val status = if (chainStep > 1) "Kettenreaktion ×$chainStep!" else null
        val newBoard = currentBoard.toMutableMap()
        val movedTiles = mutableListOf<Int>()

        for (src in grp.sources) {
          val (rem, cnt) = popTopRun(newBoard.getValue(src.key))
          newBoard[src.key] = rem
          repeat(cnt) { movedTiles.add(grp.number) }
        }

        modeStates[mode] = modeStates.getValue(mode).copy(board = newBoard)
        playBeep(ToneGenerator.TONE_PROP_ACK, 70)
        publishState(isAnimating = true, statusOverride = status)
        animateFlight(grp.sources, grp.target, grp.number)

        val afterFlightBoard = modeStates.getValue(mode).board.toMutableMap()
        afterFlightBoard[grp.target.key] = afterFlightBoard.getValue(grp.target.key) + movedTiles
        modeStates[mode] = modeStates.getValue(mode).copy(board = afterFlightBoard)
        addScore(movedTiles.size * 2)
        publishState(isAnimating = true, statusOverride = status)
        delay(100)

        dissolveCellIfReady(grp.target, chainStep)
        continue
      }

      val ready = HexGrid.cells.filter {
        topRunLength(currentBoard[it.key].orEmpty()) >= HexGrid.DISSOLVE_MIN
      }
      if (ready.isNotEmpty()) {
        chainStep++
        for (cell in ready) {
          dissolveCellIfReady(cell, chainStep)
        }
        continue
      }

      break
    }

    checkGameOver()
    saveToPrefs()
    publishState(isAnimating = false, statusOverride = null)
  }

  private suspend fun executeManualSort(srcKey: String, dstKey: String) {
    val mode = GameMode.MANUAL
    val ms = modeStates.getValue(mode)
    val srcCell = HexGrid.byKey[srcKey] ?: return
    val dstCell = HexGrid.byKey[dstKey] ?: return
    val srcStack = ms.board[srcKey].orEmpty()
    val dstStack = ms.board[dstKey].orEmpty()
    val num = srcStack.lastOrNull() ?: return
    if (dstStack.lastOrNull() != num) return

    pushUndoSnapshot()
    val updatedMs = modeStates.getValue(mode)
    val newBoard = updatedMs.board.toMutableMap()
    val (remSrc, movedCount) = popTopRun(srcStack)
    newBoard[srcKey] = remSrc
    modeStates[mode] = updatedMs.copy(board = newBoard)

    playBeep(ToneGenerator.TONE_PROP_ACK, 65)
    publishState(selectedTrayIdx = null, selectedBoardKey = null, isAnimating = true)
    animateFlight(listOf(srcCell), dstCell, num)

    val afterBoard = modeStates.getValue(mode).board.toMutableMap()
    afterBoard[dstKey] = afterBoard.getValue(dstKey) + List(movedCount) { num }
    modeStates[mode] = modeStates.getValue(mode).copy(board = afterBoard)
    addScore(movedCount * 2)
    publishState(isAnimating = true)
    delay(90)

    dissolveCellIfReady(dstCell, 1)

    checkGameOver()
    saveToPrefs()
    publishState(isAnimating = false)
  }

  private fun checkGameOver() {
    val mode = _uiState.value.activeMode
    val ms = modeStates.getValue(mode)
    if (!isBoardFull(ms.board)) {
      if (ms.gameOver) modeStates[mode] = ms.copy(gameOver = false)
      return
    }
    val isOver = if (mode == GameMode.AUTO) true else !hasAnyManualPair(ms.board)
    modeStates[mode] = ms.copy(gameOver = isOver)
  }

  fun switchMode(mode: GameMode) {
    val s = _uiState.value
    if (s.isAnimating || s.activeMode == mode) return
    publishState(
      activeMode = mode,
      selectedTrayIdx = null,
      selectedBoardKey = null,
      hoverCellKey = null,
      statusOverride = null
    )
    saveToPrefs()
  }

  fun restartCurrentMode() {
    if (_uiState.value.isAnimating) return
    val mode = _uiState.value.activeMode
    modeStates[mode] = createFreshModeState(mode)
    publishState(
      selectedTrayIdx = null,
      selectedBoardKey = null,
      hoverCellKey = null,
      statusOverride = null
    )
    saveToPrefs()
  }

  fun undo() {
    if (_uiState.value.isAnimating) return
    val mode = _uiState.value.activeMode
    val ms = modeStates.getValue(mode)
    val snap = ms.undoStack.lastOrNull() ?: return
    modeStates[mode] = ms.copy(
      board = snap.board,
      tray = snap.tray,
      score = snap.score,
      clearedCount = snap.clearedCount,
      undoStack = ms.undoStack.dropLast(1),
      gameOver = false
    )
    playBeep(ToneGenerator.TONE_PROP_BEEP, 40)
    publishState(selectedTrayIdx = null, selectedBoardKey = null, hoverCellKey = null)
    saveToPrefs()
  }

  fun toggleSound() {
    val next = !_uiState.value.soundEnabled
    publishState(soundEnabled = next)
    if (next) playBeep(ToneGenerator.TONE_PROP_BEEP, 45)
    saveToPrefs()
  }

  fun setHelpVisible(visible: Boolean) {
    _uiState.update { it.copy(showHelpDialog = visible) }
  }

  fun handleBackPress(): Boolean {
    val s = _uiState.value
    if (s.showHelpDialog) {
      setHelpVisible(false)
      return true
    }
    if (s.selectedBoardKey != null || s.selectedTrayIdx != null) {
      publishState(selectedBoardKey = null, selectedTrayIdx = null, hoverCellKey = null)
      return true
    }
    return false
  }
}
