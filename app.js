(function () {
  "use strict";

  // =========================================================
  // 1. FARBPALETTE & ZAHLENKONFIGURATION (1 - 7)
  // =========================================================
  var TILE_STYLES = {
    1: { name: "Koralle",  top: "#f87171", mid: "#ef4444", side: "#b91c1c", text: "#ffffff" },
    2: { name: "Amber",    top: "#fcd34d", mid: "#f59e0b", side: "#b45309", text: "#1e1605" },
    3: { name: "Smaragd",  top: "#34d399", mid: "#10b981", side: "#047857", text: "#ffffff" },
    4: { name: "Himmel",   top: "#60a5fa", mid: "#3b82f6", side: "#1d4ed8", text: "#ffffff" },
    5: { name: "Violett",  top: "#a78bfa", mid: "#8b5cf6", side: "#6d28d9", text: "#ffffff" },
    6: { name: "Beere",    top: "#f472b6", mid: "#ec4899", side: "#be185d", text: "#ffffff" },
    7: { name: "Türkis",   top: "#2dd4bf", mid: "#14b8a6", side: "#0f766e", text: "#ffffff" }
  };

  var STORAGE_KEY = "wabenzahl_save_v1";
  var HEX_RADIUS = 2; // 19 Waben (3-4-5-4-3)
  var CELL_R = 43;
  var TILE_R = 39.5;
  var HEX_SY = 0.87;
  var BOARD_CX = 180;
  var BOARD_CY = 182;
  var VB_X = 0;
  var VB_Y = -14;
  var VB_W = 360;
  var VB_H = 376;
  var DISSOLVE_MIN = 6;

  // Feste Nachbar-Richtungen (Flat-Top Axial)
  var DIRECTIONS = [
    { dq: 0, dr: -1 },
    { dq: 1, dr: -1 },
    { dq: 1, dr: 0 },
    { dq: 0, dr: 1 },
    { dq: -1, dr: 1 },
    { dq: -1, dr: 0 }
  ];

  // =========================================================
  // 2. WABENFELD-GEOMETRIE & ZUGRICHTUNG
  // =========================================================
  var CELLS = [];
  var CELL_BY_KEY = {};

  function cellKey(q, r) {
    return q + "," + r;
  }

  function initGridGeometry() {
    CELLS = [];
    CELL_BY_KEY = {};
    for (var q = -HEX_RADIUS; q <= HEX_RADIUS; q++) {
      var r1 = Math.max(-HEX_RADIUS, -q - HEX_RADIUS);
      var r2 = Math.min(HEX_RADIUS, -q + HEX_RADIUS);
      for (var r = r1; r <= r2; r++) {
        var x = BOARD_CX + 1.5 * CELL_R * q;
        var y = BOARD_CY + Math.sqrt(3) * CELL_R * HEX_SY * (r + q / 2);
        var cell = {
          key: cellKey(q, r),
          q: q,
          r: r,
          x: x,
          y: y,
          neighbors: []
        };
        CELLS.push(cell);
        CELL_BY_KEY[cell.key] = cell;
      }
    }

    // Feste Sortierreihenfolge: erst nach oben (kleinstes y), dann nach links (kleinstes x)
    CELLS.sort(function (a, b) {
      if (Math.abs(a.y - b.y) > 0.5) return a.y - b.y;
      return a.x - b.x;
    });

    CELLS.forEach(function (cell, idx) {
      cell.priority = idx;
      DIRECTIONS.forEach(function (d) {
        var nb = CELL_BY_KEY[cellKey(cell.q + d.dq, cell.r + d.dr)];
        if (nb) cell.neighbors.push(nb.key);
      });
    });
  }

  function hexPoints(cx, cy, r, sy) {
    var pts = [];
    var halfH = (Math.sqrt(3) / 2) * r * sy;
    pts.push((cx + r).toFixed(2) + "," + cy.toFixed(2));
    pts.push((cx + r * 0.5).toFixed(2) + "," + (cy + halfH).toFixed(2));
    pts.push((cx - r * 0.5).toFixed(2) + "," + (cy + halfH).toFixed(2));
    pts.push((cx - r).toFixed(2) + "," + cy.toFixed(2));
    pts.push((cx - r * 0.5).toFixed(2) + "," + (cy - halfH).toFixed(2));
    pts.push((cx + r * 0.5).toFixed(2) + "," + (cy - halfH).toFixed(2));
    return pts.join(" ");
  }

  function hexFrontSkirtPoints(cx, cy, r, sy, depth) {
    var halfH = (Math.sqrt(3) / 2) * r * sy;
    var p = [
      [cx - r, cy],
      [cx - r * 0.5, cy + halfH],
      [cx + r * 0.5, cy + halfH],
      [cx + r, cy],
      [cx + r, cy + depth],
      [cx + r * 0.5, cy + halfH + depth],
      [cx - r * 0.5, cy + halfH + depth],
      [cx - r, cy + depth]
    ];
    return p.map(function (pt) {
      return pt[0].toFixed(2) + "," + pt[1].toFixed(2);
    }).join(" ");
  }

  // =========================================================
  // 3. SANFTE WEB-AUDIO-EFFEKTE (OHNE EXTERNE DATEIEN)
  // =========================================================
  var audioCtx = null;

  function ensureAudio() {
    if (!appState.soundEnabled) return null;
    if (!audioCtx && (window.AudioContext || window.webkitAudioContext)) {
      var Ctx = window.AudioContext || window.webkitAudioContext;
      audioCtx = new Ctx();
    }
    if (audioCtx && audioCtx.state === "suspended") {
      audioCtx.resume().catch(function () {});
    }
    return audioCtx;
  }

  function playTone(freq, duration, type, gainPeak, delay) {
    var ctx = ensureAudio();
    if (!ctx) return;
    try {
      var now = ctx.currentTime + (delay || 0);
      var osc = ctx.createOscillator();
      var gain = ctx.createGain();
      osc.type = type || "sine";
      osc.frequency.setValueAtTime(freq, now);
      gain.gain.setValueAtTime(0.0001, now);
      gain.gain.exponentialRampToValueAtTime(gainPeak || 0.08, now + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now);
      osc.stop(now + duration + 0.02);
    } catch (e) {
      // Audio-Fehler still ignorieren
    }
  }

  function sfxPlace() {
    playTone(280, 0.08, "triangle", 0.08, 0);
    playTone(360, 0.07, "sine", 0.05, 0.03);
  }

  function sfxMerge(chainStep) {
    var base = 360 + Math.min(chainStep, 6) * 65;
    playTone(base, 0.11, "sine", 0.075, 0);
    playTone(base * 1.25, 0.12, "triangle", 0.055, 0.04);
  }

  function sfxDissolve(chainStep) {
    var root = 440 + Math.min(chainStep, 5) * 55;
    playTone(root, 0.16, "triangle", 0.09, 0);
    playTone(root * 1.25, 0.18, "sine", 0.08, 0.05);
    playTone(root * 1.5, 0.24, "sine", 0.09, 0.1);
  }

  function sfxSelect() {
    playTone(460, 0.055, "sine", 0.045, 0);
  }

  function sfxError() {
    playTone(190, 0.12, "sawtooth", 0.04, 0);
  }

  // =========================================================
  // 4. SPIELZUSTAND & SPEICHERUNG
  // =========================================================
  function createEmptyBoard() {
    var board = {};
    CELLS.forEach(function (c) {
      board[c.key] = [];
    });
    return board;
  }

  function cloneBoard(board) {
    var copy = {};
    Object.keys(board).forEach(function (k) {
      copy[k] = board[k].slice();
    });
    return copy;
  }

  function cloneTray(tray) {
    return tray.map(function (stack) {
      return stack ? stack.slice() : null;
    });
  }

  function getMaxActiveNumber(score) {
    if (score >= 420) return 7;
    if (score >= 160) return 6;
    return 5;
  }

  function randInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  function pickRandom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
  }

  function generateMixedStack(score, preferMultiLayer) {
    var maxNum = getMaxActiveNumber(score);
    var groupCount = preferMultiLayer ? (Math.random() < 0.72 ? 2 : 3) : (Math.random() < 0.22 ? 1 : (Math.random() < 0.75 ? 2 : 3));
    var usedNums = [];
    while (usedNums.length < groupCount) {
      var candidate = randInt(1, maxNum);
      if (usedNums.length === 0 || usedNums[usedNums.length - 1] !== candidate) {
        if (usedNums.indexOf(candidate) === -1) {
          usedNums.push(candidate);
        }
      }
    }

    var stack = [];
    usedNums.forEach(function (num, idx) {
      var maxCount = groupCount === 1 ? 3 : (idx === usedNums.length - 1 ? 3 : 2);
      var count = randInt(1, maxCount);
      if (stack.length + count > 5) {
        count = Math.max(1, 5 - stack.length);
      }
      for (var i = 0; i < count; i++) {
        stack.push(num);
      }
    });
    return stack;
  }

  function generateTray(score) {
    return [
      generateMixedStack(score, false),
      generateMixedStack(score, true),
      generateMixedStack(score, false)
    ];
  }

  function createSeededBoard(mode) {
    var board = createEmptyBoard();
    var shuffled = CELLS.slice().sort(function () { return Math.random() - 0.5; });

    if (mode === "auto") {
      // 5 verteilte Startstapel ohne direkte Nachbar-Treffer, damit der erste Zug direkt Spaß macht
      var placed = [];
      for (var i = 0; i < shuffled.length && placed.length < 5; i++) {
        var cell = shuffled[i];
        var num = randInt(1, 5);
        var conflict = cell.neighbors.some(function (nKey) {
          var st = board[nKey];
          return st.length > 0 && st[st.length - 1] === num;
        });
        if (!conflict) {
          var stack = [num];
          if (Math.random() < 0.65) {
            var baseNum = randInt(1, 5);
            if (baseNum !== num) {
              stack = [baseNum, num];
            } else {
              stack = [num, num];
            }
          }
          board[cell.key] = stack;
          placed.push(cell.key);
        }
      }
    } else {
      // Modus „Selbst sortieren“: 10 gemischte Startstapel mit garantierten Einstiegs-Paaren
      for (var j = 0; j < 10; j++) {
        board[shuffled[j].key] = generateMixedStack(0, true);
      }
      // Garantiere mindestens 2 passende Nachbar-Paare zum direkten Loslegen
      var center = CELL_BY_KEY[cellKey(0, 0)];
      var nb = CELL_BY_KEY[center.neighbors[0]];
      var pairNum = randInt(1, 4);
      board[center.key] = [randInt(1, 5), pairNum, pairNum];
      board[nb.key] = [randInt(1, 5), pairNum];
    }

    return board;
  }

  function createFreshModeState(mode) {
    return {
      board: createSeededBoard(mode),
      tray: generateTray(0),
      score: 0,
      clearedCount: 0,
      undoStack: [],
      gameOver: false
    };
  }

  var appState = {
    activeMode: "auto",
    soundEnabled: true,
    bestScores: { auto: 0, manual: 0 },
    modes: {
      auto: null,
      manual: null
    },
    selectedTrayIdx: null,
    selectedBoardKey: null,
    isAnimating: false
  };

  function currentModeState() {
    return appState.modes[appState.activeMode];
  }

  function saveState() {
    try {
      var payload = {
        activeMode: appState.activeMode,
        soundEnabled: appState.soundEnabled,
        bestScores: appState.bestScores,
        modes: appState.modes
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    } catch (e) {
      // Speicher voll oder deaktiviert
    }
  }

  function loadState() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      var parsed = JSON.parse(raw);
      if (parsed && parsed.modes && parsed.modes.auto && parsed.modes.manual) {
        appState.activeMode = parsed.activeMode === "manual" ? "manual" : "auto";
        appState.soundEnabled = parsed.soundEnabled !== false;
        appState.bestScores = parsed.bestScores || { auto: 0, manual: 0 };
        appState.modes = parsed.modes;
      }
    } catch (e) {
      // Ungültiger Speicherstand -> Standardzustand behalten
    }
  }

  // =========================================================
  // 5. SPIELLOGIK: HELFER & KETTENREAKTIONEN
  // =========================================================
  function getTopNumber(stack) {
    return stack && stack.length > 0 ? stack[stack.length - 1] : null;
  }

  function getTopRunLength(stack) {
    if (!stack || stack.length === 0) return 0;
    var top = stack[stack.length - 1];
    var count = 0;
    for (var i = stack.length - 1; i >= 0; i--) {
      if (stack[i] === top) count++;
      else break;
    }
    return count;
  }

  function popTopRun(stack) {
    var top = getTopNumber(stack);
    var count = getTopRunLength(stack);
    stack.splice(stack.length - count, count);
    return { num: top, count: count };
  }

  function isBoardFull(board) {
    return CELLS.every(function (c) {
      return board[c.key].length > 0;
    });
  }

  function hasAnyManualPair(board) {
    for (var i = 0; i < CELLS.length; i++) {
      var c = CELLS[i];
      var top = getTopNumber(board[c.key]);
      if (top === null) continue;
      for (var j = 0; j < c.neighbors.length; j++) {
        var nbTop = getTopNumber(board[c.neighbors[j]]);
        if (nbTop === top) return true;
      }
    }
    return false;
  }

  function findConnectedTopGroups(board) {
    var visited = {};
    var groups = [];

    CELLS.forEach(function (startCell) {
      if (visited[startCell.key]) return;
      var top = getTopNumber(board[startCell.key]);
      if (top === null) return;

      var queue = [startCell.key];
      var component = [];
      visited[startCell.key] = true;

      while (queue.length > 0) {
        var curKey = queue.shift();
        var curCell = CELL_BY_KEY[curKey];
        component.push(curCell);

        curCell.neighbors.forEach(function (nbKey) {
          if (visited[nbKey]) return;
          if (getTopNumber(board[nbKey]) === top) {
            visited[nbKey] = true;
            queue.push(nbKey);
          }
        });
      }

      if (component.length >= 2) {
        // Feste Zugrichtung: Ziel ist immer die Zelle mit der kleinsten priority (oben, dann links)
        component.sort(function (a, b) {
          return a.priority - b.priority;
        });
        groups.push({
          num: top,
          target: component[0],
          sources: component.slice(1)
        });
      }
    });

    return groups;
  }

  function findDissolvableCells(board) {
    var list = [];
    CELLS.forEach(function (c) {
      var st = board[c.key];
      if (getTopRunLength(st) >= DISSOLVE_MIN) {
        list.push(c);
      }
    });
    return list;
  }

  function pushUndoSnapshot() {
    var ms = currentModeState();
    ms.undoStack.push({
      board: cloneBoard(ms.board),
      tray: cloneTray(ms.tray),
      score: ms.score,
      clearedCount: ms.clearedCount
    });
    if (ms.undoStack.length > 15) {
      ms.undoStack.shift();
    }
  }

  function addScore(points) {
    var ms = currentModeState();
    ms.score += points;
    if (ms.score > (appState.bestScores[appState.activeMode] || 0)) {
      appState.bestScores[appState.activeMode] = ms.score;
    }
  }

  function wait(ms) {
    return new Promise(function (resolve) {
      setTimeout(resolve, ms);
    });
  }

  // =========================================================
  // 6. SVG-STAPEL-RENDERER (VERSATZ & FREIGELEGTE ZAHLEN)
  // =========================================================
  var SVG_NS = "http://www.w3.org/2000/svg";

  function svgEl(tag, attrs) {
    var el = document.createElementNS(SVG_NS, tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        el.setAttribute(k, attrs[k]);
      });
    }
    return el;
  }

  function buildStackGroup(stack, cx, cy, tileR, sy, options) {
    var opts = options || {};
    var g = svgEl("g", { class: "stack-group" });
    if (!stack || stack.length === 0) return g;

    // Berechne Versatz nach oben für jeden Stein, sodass Übergänge zur nächsten Zahl klar lesbar bleiben
    var n = stack.length;
    var rawSteps = [];
    var transitionCount = 0;
    for (var i = 0; i < n - 1; i++) {
      var isTransition = stack[i] !== stack[i + 1];
      if (isTransition) transitionCount++;
      rawSteps.push(isTransition ? 11.2 : 5.2);
    }

    var totalLift = rawSteps.reduce(function (acc, v) { return acc + v; }, 0);
    var maxLift = opts.maxLift || 60;
    var scaleLift = totalLift > maxLift ? maxLift / totalLift : 1;

    // Finde den obersten Farbwechsel (nächste freiliegende Zahl unter dem oberen Block)
    var topTransitionIdx = -1;
    for (var t = n - 2; t >= 0; t--) {
      if (stack[t] !== stack[t + 1]) {
        topTransitionIdx = t;
        break;
      }
    }

    var yOffsets = [0];
    var acc = 0;
    for (var j = 0; j < rawSteps.length; j++) {
      var step = rawSteps[j] * scaleLift;
      if (j === topTransitionIdx && step < 9.2) {
        step = 9.2;
      } else if (stack[j] !== stack[j + 1] && step < 7.8) {
        step = 7.8;
      }
      acc += step;
      yOffsets.push(acc);
    }

    var halfH = (Math.sqrt(3) / 2) * tileR * sy;
    var baseDepth = 8.5;

    // Weicher Vektor-Schatten unter dem Stapel (ohne teures SVG-Filter-Offscreen-Rendering)
    g.appendChild(svgEl("polygon", {
      points: hexPoints(cx, cy + 4.5, tileR * 1.02, sy),
      fill: "rgba(2, 6, 23, 0.42)"
    }));

    for (var idx = 0; idx < n; idx++) {
      var num = stack[idx];
      var style = TILE_STYLES[num] || TILE_STYLES[1];
      var tileCy = cy - yOffsets[idx];
      var isTop = idx === n - 1;
      var nextDiffers = idx < n - 1 && stack[idx] !== stack[idx + 1];
      var stepBelowUpper = idx < n - 1 ? (yOffsets[idx + 1] - yOffsets[idx]) : baseDepth;
      var skirtDepth = isTop ? baseDepth : Math.max(stepBelowUpper + 1.5, 6.5);

      // 3D-Seitenkante (Vorderseite)
      g.appendChild(svgEl("polygon", {
        points: hexFrontSkirtPoints(cx, tileCy, tileR, sy, skirtDepth),
        fill: style.side,
        stroke: "rgba(2, 6, 23, 0.35)",
        "stroke-width": "0.8"
      }));

      // Oberseite des Steins
      g.appendChild(svgEl("polygon", {
        points: hexPoints(cx, tileCy, tileR, sy),
        fill: isTop ? style.top : style.mid,
        stroke: isTop ? "rgba(255,255,255,0.45)" : "rgba(255,255,255,0.18)",
        "stroke-width": isTop ? "1.3" : "0.8"
      }));

      // Wenn unter einem anderen Farbblock: Zahl direkt auf der freigelegten Frontlippe anzeigen!
      if (!isTop && nextDiffers && stepBelowUpper >= 7.5) {
        var lipY = tileCy + halfH + Math.min(stepBelowUpper * 0.52, 6.0);
        var lipText = svgEl("text", {
          x: cx,
          y: lipY,
          fill: "#ffffff",
          class: "stack-lip-text",
          style: opts.small ? "font-size: 8.5px;" : ""
        });
        lipText.textContent = String(num);
        g.appendChild(lipText);
      }

      // Oberster Stein: große Zahl + Stapelzähler für zusammenhängende gleiche Steine oben
      if (isTop) {
        var topText = svgEl("text", {
          x: cx,
          y: tileCy - 0.5,
          fill: style.text,
          class: "stack-top-text",
          style: opts.small ? "font-size: 17px;" : ""
        });
        topText.textContent = String(num);
        g.appendChild(topText);

        var runLen = getTopRunLength(stack);
        if (runLen >= 2) {
          var badgeX = cx + tileR * 0.46;
          var badgeY = tileCy - halfH * 0.56;
          var pillBg = runLen >= DISSOLVE_MIN ? "#10b981" : "rgba(15, 23, 42, 0.78)";
          g.appendChild(svgEl("rect", {
            x: badgeX - 11,
            y: badgeY - 7.5,
            width: 22,
            height: 15,
            rx: 7.5,
            fill: pillBg,
            stroke: "rgba(255,255,255,0.4)",
            "stroke-width": "0.9",
            class: "stack-count-pill"
          }));
          var badgeText = svgEl("text", {
            x: badgeX,
            y: badgeY + 0.3,
            class: "stack-count-text"
          });
          badgeText.textContent = "×" + runLen;
          g.appendChild(badgeText);
        }
      }
    }

    return g;
  }

  // =========================================================
  // 7. DOM-REFERENZEN & RENDERING
  // =========================================================
  var dom = {
    boardSvg: document.getElementById("hex-board"),
    boardSlots: document.getElementById("board-slots"),
    boardStacks: document.getElementById("board-stacks"),
    boardOverlays: document.getElementById("board-overlays"),
    floatingEffects: document.getElementById("floating-effects"),
    traySlots: document.getElementById("tray-slots"),
    dragGhost: document.getElementById("drag-ghost"),
    scoreValue: document.getElementById("score-value"),
    bestValue: document.getElementById("best-value"),
    clearedValue: document.getElementById("cleared-value"),
    modeAutoBtn: document.getElementById("mode-auto"),
    modeManualBtn: document.getElementById("mode-manual"),
    directionBadge: document.getElementById("direction-badge"),
    statusText: document.getElementById("status-text"),
    btnUndo: document.getElementById("btn-undo"),
    btnSound: document.getElementById("btn-sound"),
    iconSoundOn: document.getElementById("icon-sound-on"),
    iconSoundOff: document.getElementById("icon-sound-off"),
    btnHelp: document.getElementById("btn-help"),
    btnRestart: document.getElementById("btn-restart"),
    modalHelp: document.getElementById("modal-help"),
    btnCloseHelp: document.getElementById("btn-close-help"),
    btnHelpOk: document.getElementById("btn-help-ok"),
    colorLegend: document.getElementById("color-legend"),
    modalGameOver: document.getElementById("modal-gameover"),
    gameoverSubtitle: document.getElementById("gameover-subtitle"),
    goScore: document.getElementById("go-score"),
    goBest: document.getElementById("go-best"),
    goCleared: document.getElementById("go-cleared"),
    btnGameOverRestart: document.getElementById("btn-gameover-restart")
  };

  function getBoardTransform() {
    var rect = dom.boardSvg.getBoundingClientRect();
    if (!rect.width || !rect.height) return null;
    var scale = Math.min(rect.width / VB_W, rect.height / VB_H);
    var offsetX = rect.left + (rect.width - VB_W * scale) / 2;
    var offsetY = rect.top + (rect.height - VB_H * scale) / 2;
    return { rect: rect, scale: scale, offsetX: offsetX, offsetY: offsetY };
  }

  function showFloatText(svgX, svgY, text, isChain) {
    var tf = getBoardTransform();
    if (!tf) return;
    var px = (tf.offsetX - tf.rect.left) + (svgX - VB_X) * tf.scale;
    var py = (tf.offsetY - tf.rect.top) + (svgY - VB_Y) * tf.scale;
    var el = document.createElement("div");
    el.className = "float-pop" + (isChain ? " chain" : "");
    el.style.left = px + "px";
    el.style.top = py + "px";
    el.textContent = text;
    dom.floatingEffects.appendChild(el);
    setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 950);
  }

  function buildBoardSlotsOnce() {
    dom.boardSlots.innerHTML = "";
    CELLS.forEach(function (cell) {
      var g = svgEl("g", {
        class: "hex-slot-group hex-cell-interactive",
        "data-cell-key": cell.key
      });
      var poly = svgEl("polygon", {
        points: hexPoints(cell.x, cell.y, CELL_R - 1.5, HEX_SY),
        class: "hex-slot-poly"
      });
      g.appendChild(poly);
      g.addEventListener("click", function () {
        handleBoardCellTap(cell.key);
      });
      dom.boardSlots.appendChild(g);
    });
  }

  function updateBoardHighlights(hoverKey) {
    var ms = currentModeState();
    var selectedTop = appState.selectedBoardKey ? getTopNumber(ms.board[appState.selectedBoardKey]) : null;
    var selectedCell = appState.selectedBoardKey ? CELL_BY_KEY[appState.selectedBoardKey] : null;

    var slotGroups = dom.boardSlots.querySelectorAll(".hex-slot-group");
    slotGroups.forEach(function (g) {
      var k = g.getAttribute("data-cell-key");
      var stack = ms.board[k];
      var isEmpty = stack.length === 0;

      g.classList.toggle("can-drop", appState.selectedTrayIdx !== null && isEmpty);
      g.classList.toggle("drop-hover", hoverKey === k && isEmpty);
      g.classList.toggle("source-selected", appState.selectedBoardKey === k);

      var isValidManualNeighbor = false;
      if (
        appState.activeMode === "manual" &&
        selectedCell &&
        selectedTop !== null &&
        k !== selectedCell.key &&
        selectedCell.neighbors.indexOf(k) !== -1 &&
        getTopNumber(stack) === selectedTop
      ) {
        isValidManualNeighbor = true;
      }
      g.classList.toggle("neighbor-valid", isValidManualNeighbor);
    });
  }

  function renderBoardStacks() {
    var ms = currentModeState();
    dom.boardStacks.innerHTML = "";

    // Zeichne von oben nach unten (CELLS ist bereits nach y, x sortiert!),
    // damit vordere/untere Stapel perspektivisch sauber über hinteren liegen
    CELLS.forEach(function (cell) {
      var stack = ms.board[cell.key];
      if (!stack || stack.length === 0) return;

      var wrap = svgEl("g", {
        class: "hex-cell-interactive"
      });
      wrap.appendChild(buildStackGroup(stack, cell.x, cell.y, TILE_R, HEX_SY, { maxLift: 58 }));
      wrap.addEventListener("click", function () {
        handleBoardCellTap(cell.key);
      });
      dom.boardStacks.appendChild(wrap);
    });

    updateBoardHighlights(null);
  }

  function renderTray() {
    var ms = currentModeState();
    dom.traySlots.innerHTML = "";

    ms.tray.forEach(function (stack, idx) {
      var slotBtn = document.createElement("div");
      slotBtn.className = "tray-slot";
      slotBtn.setAttribute("role", "button");
      slotBtn.setAttribute("tabindex", stack ? "0" : "-1");
      if (!stack) {
        slotBtn.classList.add("empty");
        slotBtn.setAttribute("aria-label", "Leeres Ablagefeld");
      } else {
        var topNum = getTopNumber(stack);
        slotBtn.setAttribute("aria-label", "Stapel " + (idx + 1) + " mit Zahl " + topNum + " oben");
        if (appState.selectedTrayIdx === idx) {
          slotBtn.classList.add("selected");
        }
        var svg = svgEl("svg", { viewBox: "0 0 110 110" });
        svg.appendChild(buildStackGroup(stack, 55, 72, 29, HEX_SY, { small: true, maxLift: 46 }));
        slotBtn.appendChild(svg);
        attachTrayPointerHandlers(slotBtn, idx);
      }
      dom.traySlots.appendChild(slotBtn);
    });
  }

  function renderHUD() {
    var ms = currentModeState();
    dom.scoreValue.textContent = String(ms.score);
    dom.bestValue.textContent = String(appState.bestScores[appState.activeMode] || 0);
    dom.clearedValue.textContent = String(ms.clearedCount);

    var isAuto = appState.activeMode === "auto";
    dom.modeAutoBtn.classList.toggle("active", isAuto);
    dom.modeAutoBtn.setAttribute("aria-selected", String(isAuto));
    dom.modeManualBtn.classList.toggle("active", !isAuto);
    dom.modeManualBtn.setAttribute("aria-selected", String(!isAuto));

    dom.directionBadge.classList.toggle("hidden", !isAuto);
    dom.btnUndo.disabled = appState.isAnimating || ms.undoStack.length === 0;

    dom.iconSoundOn.classList.toggle("hidden", !appState.soundEnabled);
    dom.iconSoundOff.classList.toggle("hidden", appState.soundEnabled);
    dom.btnSound.setAttribute("aria-pressed", String(appState.soundEnabled));

    updateStatusText();
  }

  function updateStatusText(customMsg) {
    if (customMsg) {
      dom.statusText.textContent = customMsg;
      return;
    }
    var ms = currentModeState();
    if (appState.activeMode === "auto") {
      if (appState.selectedTrayIdx !== null) {
        dom.statusText.textContent = "Freie Wabe antippen, um den Stapel zu setzen";
      } else {
        dom.statusText.textContent = "Stapel auf eine freie Wabe ziehen oder antippen";
      }
    } else {
      if (appState.selectedBoardKey) {
        var topNum = getTopNumber(ms.board[appState.selectedBoardKey]);
        dom.statusText.textContent = "Grün markierten Nachbarstapel mit " + topNum + " antippen";
      } else if (appState.selectedTrayIdx !== null) {
        dom.statusText.textContent = "Freie Wabe antippen, um neuen Stapel abzulegen";
      } else {
        dom.statusText.textContent = "Ausgangsstapel antippen oder Stapel aus Ablage setzen";
      }
    }
  }

  function renderAll() {
    renderBoardStacks();
    renderTray();
    renderHUD();
  }

  // =========================================================
  // 8. FLUG-ANIMATIONEN BEI ZUSAMMENFÜHRUNG
  // =========================================================
  function animateMergeFlight(sources, targetCell, num) {
    return new Promise(function (resolve) {
      dom.boardOverlays.innerHTML = "";
      var style = TILE_STYLES[num] || TILE_STYLES[1];
      var flyingItems = [];

      sources.forEach(function (srcCell) {
        // Dezente Richtungslinie (zeigt die feste Zugrichtung visuell)
        var line = svgEl("line", {
          x1: srcCell.x,
          y1: srcCell.y,
          x2: targetCell.x,
          y2: targetCell.y,
          stroke: style.top,
          "stroke-width": "2.5",
          "stroke-dasharray": "5 4",
          "stroke-opacity": "0.6"
        });
        dom.boardOverlays.appendChild(line);

        var flyer = svgEl("g", {});
        flyer.appendChild(svgEl("polygon", {
          points: hexPoints(0, 0, TILE_R * 0.88, HEX_SY),
          fill: style.top,
          stroke: "#ffffff",
          "stroke-width": "1.6"
        }));
        var txt = svgEl("text", {
          x: 0,
          y: 0,
          fill: style.text,
          class: "stack-top-text",
          style: "font-size: 16px;"
        });
        txt.textContent = String(num);
        flyer.appendChild(txt);
        flyer.setAttribute("transform", "translate(" + srcCell.x + "," + srcCell.y + ")");
        dom.boardOverlays.appendChild(flyer);
        flyingItems.push({ el: flyer, sx: srcCell.x, sy: srcCell.y });
      });

      var start = performance.now();
      var duration = 240;

      function frame(now) {
        var t = Math.min(1, (now - start) / duration);
        var ease = 1 - Math.pow(1 - t, 3);
        flyingItems.forEach(function (item) {
          var cx = item.sx + (targetCell.x - item.sx) * ease;
          var cy = item.sy + (targetCell.y - item.sy) * ease - Math.sin(t * Math.PI) * 16;
          item.el.setAttribute("transform", "translate(" + cx.toFixed(1) + "," + cy.toFixed(1) + ")");
        });
        if (t < 1) {
          requestAnimationFrame(frame);
        } else {
          dom.boardOverlays.innerHTML = "";
          resolve();
        }
      }

      requestAnimationFrame(frame);
    });
  }

  // =========================================================
  // 9. SPIELZÜGE & AUFLÖSUNG
  // =========================================================
  async function dissolveCellIfReady(cell, chainStep) {
    var ms = currentModeState();
    var stack = ms.board[cell.key];
    var runLen = getTopRunLength(stack);
    if (runLen < DISSOLVE_MIN) return false;

    var removed = popTopRun(stack);
    var bonus = chainStep > 1 ? chainStep * 10 : 0;
    var pts = removed.count * 10 + bonus;
    ms.clearedCount += removed.count;
    addScore(pts);

    sfxDissolve(chainStep);
    var label = "+" + pts + (chainStep > 1 ? " (Kette ×" + chainStep + ")" : "");
    showFloatText(cell.x, cell.y - 18, label, chainStep > 1);
    renderBoardStacks();
    renderHUD();
    await wait(240);
    return true;
  }

  async function resolveBoardAuto() {
    var ms = currentModeState();
    appState.isAnimating = true;
    renderHUD();

    var chainStep = 0;

    while (true) {
      var groups = findConnectedTopGroups(ms.board);
      if (groups.length > 0) {
        chainStep++;
        // Wähle eindeutige Gruppe nach fester Priorität (kleinste target.priority)
        groups.sort(function (a, b) {
          return a.target.priority - b.target.priority;
        });
        var grp = groups[0];

        if (chainStep > 1) {
          updateStatusText("Kettenreaktion ×" + chainStep + "!");
        }

        // Ziehe alle zusammenhängenden obersten Steine dieser Zahl aus den Quellwaben ab
        var movedTiles = [];
        grp.sources.forEach(function (src) {
          var popped = popTopRun(ms.board[src.key]);
          for (var i = 0; i < popped.count; i++) {
            movedTiles.push(popped.num);
          }
        });

        renderBoardStacks();
        sfxMerge(chainStep);
        await animateMergeFlight(grp.sources, grp.target, grp.num);

        // Steine auf Zielwabe legen
        movedTiles.forEach(function (n) {
          ms.board[grp.target.key].push(n);
        });
        addScore(movedTiles.length * 2);
        renderBoardStacks();
        renderHUD();
        await wait(120);

        // Prüfe sofort auf >= 6 Steine
        await dissolveCellIfReady(grp.target, chainStep);
        continue;
      }

      // Falls ein einzelner Stapel bereits >= 6 Steine oben hat
      var ready = findDissolvableCells(ms.board);
      if (ready.length > 0) {
        chainStep++;
        for (var i = 0; i < ready.length; i++) {
          await dissolveCellIfReady(ready[i], chainStep);
        }
        continue;
      }

      break;
    }

    appState.isAnimating = false;
    checkGameOver();
    saveState();
    renderAll();
  }

  async function placeTrayStackOnCell(trayIdx, cellKeyTarget) {
    if (appState.isAnimating) return;
    var ms = currentModeState();
    var trayStack = ms.tray[trayIdx];
    if (!trayStack || ms.board[cellKeyTarget].length > 0) return;

    pushUndoSnapshot();
    ms.board[cellKeyTarget] = trayStack.slice();
    ms.tray[trayIdx] = null;
    appState.selectedTrayIdx = null;
    appState.selectedBoardKey = null;

    if (ms.tray.every(function (s) { return s === null; })) {
      ms.tray = generateTray(ms.score);
    }

    sfxPlace();
    renderAll();

    if (appState.activeMode === "auto") {
      await resolveBoardAuto();
    } else {
      var placedCell = CELL_BY_KEY[cellKeyTarget];
      if (getTopRunLength(ms.board[cellKeyTarget]) >= DISSOLVE_MIN) {
        appState.isAnimating = true;
        await dissolveCellIfReady(placedCell, 1);
        appState.isAnimating = false;
      }
      checkGameOver();
      saveState();
      renderAll();
    }
  }

  async function executeManualSort(srcKey, dstKey) {
    if (appState.isAnimating) return;
    var ms = currentModeState();
    var srcStack = ms.board[srcKey];
    var dstStack = ms.board[dstKey];
    var srcCell = CELL_BY_KEY[srcKey];
    var dstCell = CELL_BY_KEY[dstKey];

    if (!srcCell || !dstCell) return;
    if (srcCell.neighbors.indexOf(dstKey) === -1) return;

    var srcTop = getTopNumber(srcStack);
    var dstTop = getTopNumber(dstStack);
    if (srcTop === null || srcTop !== dstTop) return;

    pushUndoSnapshot();
    appState.selectedBoardKey = null;
    appState.selectedTrayIdx = null;
    appState.isAnimating = true;

    var popped = popTopRun(srcStack);
    renderBoardStacks();
    renderHUD();
    sfxMerge(1);
    await animateMergeFlight([srcCell], dstCell, popped.num);

    for (var i = 0; i < popped.count; i++) {
      dstStack.push(popped.num);
    }
    addScore(popped.count * 2);
    renderBoardStacks();
    renderHUD();
    await wait(100);

    await dissolveCellIfReady(dstCell, 1);

    appState.isAnimating = false;
    checkGameOver();
    saveState();
    renderAll();
  }

  function handleBoardCellTap(cellKeyTapped) {
    if (appState.isAnimating) return;
    var ms = currentModeState();
    if (ms.gameOver) return;

    var tappedStack = ms.board[cellKeyTapped];
    var isEmpty = tappedStack.length === 0;

    // Fall 1: Ein Stapel aus der Ablage ist ausgewählt -> auf freie Wabe setzen
    if (appState.selectedTrayIdx !== null) {
      if (isEmpty) {
        placeTrayStackOnCell(appState.selectedTrayIdx, cellKeyTapped);
      } else if (appState.activeMode === "manual") {
        // Wechsel zu manueller Stapelauswahl
        appState.selectedTrayIdx = null;
        appState.selectedBoardKey = cellKeyTapped;
        sfxSelect();
        renderTray();
        updateBoardHighlights(null);
        updateStatusText();
      } else {
        sfxError();
      }
      return;
    }

    // Fall 2: Im Modus „Selbst sortieren“ Ausgangsstapel und passenden Nachbarstapel antippen
    if (appState.activeMode === "manual") {
      if (!appState.selectedBoardKey) {
        if (!isEmpty) {
          appState.selectedBoardKey = cellKeyTapped;
          sfxSelect();
          updateBoardHighlights(null);
          updateStatusText();
        }
        return;
      }

      if (appState.selectedBoardKey === cellKeyTapped) {
        appState.selectedBoardKey = null;
        updateBoardHighlights(null);
        updateStatusText();
        return;
      }

      var srcCell = CELL_BY_KEY[appState.selectedBoardKey];
      var srcTop = getTopNumber(ms.board[appState.selectedBoardKey]);
      var dstTop = getTopNumber(tappedStack);

      if (srcCell.neighbors.indexOf(cellKeyTapped) !== -1 && srcTop !== null && dstTop === srcTop) {
        executeManualSort(appState.selectedBoardKey, cellKeyTapped);
      } else if (!isEmpty) {
        // Neuen Ausgangsstapel wählen
        appState.selectedBoardKey = cellKeyTapped;
        sfxSelect();
        updateBoardHighlights(null);
        updateStatusText();
      } else {
        appState.selectedBoardKey = null;
        updateBoardHighlights(null);
        updateStatusText();
      }
    }
  }

  function checkGameOver() {
    var ms = currentModeState();
    if (!isBoardFull(ms.board)) {
      ms.gameOver = false;
      return;
    }
    if (appState.activeMode === "auto") {
      ms.gameOver = true;
    } else {
      ms.gameOver = !hasAnyManualPair(ms.board);
    }

    if (ms.gameOver) {
      showGameOverModal();
    }
  }

  // =========================================================
  // 10. DRAG & DROP + TAP FÜR ABLAGE-STAPEL
  // =========================================================
  var dragSession = null;

  function findDropCellAtClientPoint(clientX, clientY) {
    var tf = getBoardTransform();
    if (!tf || tf.scale <= 0) return null;
    // Leichter Versatz nach oben analog zum Drag-Geist, damit der Finger die Wabe nicht verdeckt
    var adjustedY = clientY - 24;
    var svgX = VB_X + (clientX - tf.offsetX) / tf.scale;
    var svgY = VB_Y + (adjustedY - tf.offsetY) / tf.scale;

    var ms = currentModeState();
    var bestKey = null;
    var bestDist = 48;
    CELLS.forEach(function (c) {
      if (ms.board[c.key].length > 0) return;
      var dx = c.x - svgX;
      var dy = c.y - svgY;
      var dist = Math.sqrt(dx * dx + dy * dy);
      if (dist < bestDist) {
        bestDist = dist;
        bestKey = c.key;
      }
    });
    return bestKey;
  }

  function attachTrayPointerHandlers(slotEl, idx) {
    slotEl.addEventListener("pointerdown", function (ev) {
      if (appState.isAnimating) return;
      var ms = currentModeState();
      if (ms.gameOver || !ms.tray[idx]) return;
      ensureAudio();

      ev.preventDefault();
      if (slotEl.setPointerCapture) {
        try { slotEl.setPointerCapture(ev.pointerId); } catch (e) {}
      }

      dragSession = {
        idx: idx,
        pointerId: ev.pointerId,
        startX: ev.clientX,
        startY: ev.clientY,
        activeDrag: false,
        slotEl: slotEl,
        hoverKey: null
      };
    });
  }

  window.addEventListener("pointermove", function (ev) {
    if (!dragSession || ev.pointerId !== dragSession.pointerId) return;
    var dx = ev.clientX - dragSession.startX;
    var dy = ev.clientY - dragSession.startY;
    var dist = Math.sqrt(dx * dx + dy * dy);

    if (!dragSession.activeDrag && dist > 8) {
      dragSession.activeDrag = true;
      appState.selectedTrayIdx = dragSession.idx;
      appState.selectedBoardKey = null;
      dragSession.slotEl.classList.add("dragging-source");

      var stack = currentModeState().tray[dragSession.idx];
      dom.dragGhost.innerHTML = "";
      var svg = svgEl("svg", { viewBox: "0 0 110 110" });
      svg.appendChild(buildStackGroup(stack, 55, 72, 30, HEX_SY, { maxLift: 48 }));
      dom.dragGhost.appendChild(svg);
      dom.dragGhost.classList.remove("hidden");
    }

    if (dragSession.activeDrag) {
      dom.dragGhost.style.left = ev.clientX + "px";
      dom.dragGhost.style.top = ev.clientY + "px";
      var hitKey = findDropCellAtClientPoint(ev.clientX, ev.clientY);
      if (hitKey !== dragSession.hoverKey) {
        dragSession.hoverKey = hitKey;
        updateBoardHighlights(hitKey);
      }
    }
  });

  function endDragSession(ev) {
    if (!dragSession || ev.pointerId !== dragSession.pointerId) return;
    var session = dragSession;
    dragSession = null;

    dom.dragGhost.classList.add("hidden");
    dom.dragGhost.innerHTML = "";
    session.slotEl.classList.remove("dragging-source");

    if (session.activeDrag) {
      var dropKey = findDropCellAtClientPoint(ev.clientX, ev.clientY);
      updateBoardHighlights(null);
      if (dropKey) {
        placeTrayStackOnCell(session.idx, dropKey);
      } else {
        appState.selectedTrayIdx = null;
        renderTray();
        updateBoardHighlights(null);
        updateStatusText();
      }
    } else {
      // Einfaches Antippen: Stapel auswählen / abwählen
      appState.selectedBoardKey = null;
      appState.selectedTrayIdx = appState.selectedTrayIdx === session.idx ? null : session.idx;
      sfxSelect();
      renderTray();
      updateBoardHighlights(null);
      updateStatusText();
    }
  }

  window.addEventListener("pointerup", endDragSession);
  window.addEventListener("pointercancel", endDragSession);

  // =========================================================
  // 11. STEUERUNG, MODALE & INITIALISIERUNG
  // =========================================================
  function switchMode(mode) {
    if (appState.isAnimating || appState.activeMode === mode) return;
    appState.activeMode = mode;
    appState.selectedTrayIdx = null;
    appState.selectedBoardKey = null;
    saveState();
    renderAll();
    if (currentModeState().gameOver) {
      showGameOverModal();
    } else {
      dom.modalGameOver.classList.add("hidden");
    }
  }

  function restartCurrentMode() {
    if (appState.isAnimating) return;
    appState.modes[appState.activeMode] = createFreshModeState(appState.activeMode);
    appState.selectedTrayIdx = null;
    appState.selectedBoardKey = null;
    dom.modalGameOver.classList.add("hidden");
    saveState();
    renderAll();
  }

  function performUndo() {
    if (appState.isAnimating) return;
    var ms = currentModeState();
    if (ms.undoStack.length === 0) return;

    var prev = ms.undoStack.pop();
    ms.board = prev.board;
    ms.tray = prev.tray;
    ms.score = prev.score;
    ms.clearedCount = prev.clearedCount;
    ms.gameOver = false;

    appState.selectedTrayIdx = null;
    appState.selectedBoardKey = null;
    dom.modalGameOver.classList.add("hidden");
    sfxSelect();
    saveState();
    renderAll();
  }

  function showGameOverModal() {
    var ms = currentModeState();
    dom.gameoverSubtitle.textContent =
      appState.activeMode === "auto"
        ? "Alle 19 Waben sind belegt."
        : "Keine freien Waben und keine passenden Nachbarn mehr.";
    dom.goScore.textContent = String(ms.score);
    dom.goBest.textContent = String(appState.bestScores[appState.activeMode] || 0);
    dom.goCleared.textContent = String(ms.clearedCount);
    dom.modalGameOver.classList.remove("hidden");
  }

  function buildColorLegend() {
    dom.colorLegend.innerHTML = "";
    for (var n = 1; n <= 7; n++) {
      var st = TILE_STYLES[n];
      var chip = document.createElement("div");
      chip.className = "legend-chip";
      chip.style.background = st.mid;
      chip.style.color = st.text;
      chip.textContent = String(n);
      dom.colorLegend.appendChild(chip);
    }
  }

  function bindUIEvents() {
    dom.modeAutoBtn.addEventListener("click", function () { switchMode("auto"); });
    dom.modeManualBtn.addEventListener("click", function () { switchMode("manual"); });
    dom.btnUndo.addEventListener("click", performUndo);
    dom.btnRestart.addEventListener("click", restartCurrentMode);
    dom.btnGameOverRestart.addEventListener("click", restartCurrentMode);

    dom.btnSound.addEventListener("click", function () {
      appState.soundEnabled = !appState.soundEnabled;
      if (appState.soundEnabled) sfxSelect();
      saveState();
      renderHUD();
    });

    dom.btnHelp.addEventListener("click", function () {
      dom.modalHelp.classList.remove("hidden");
    });
    dom.btnCloseHelp.addEventListener("click", function () {
      dom.modalHelp.classList.add("hidden");
    });
    dom.btnHelpOk.addEventListener("click", function () {
      dom.modalHelp.classList.add("hidden");
    });
    dom.modalHelp.addEventListener("click", function (ev) {
      if (ev.target === dom.modalHelp) {
        dom.modalHelp.classList.add("hidden");
      }
    });
  }

  // Für Android Back-Button Unterstützung
  window.wabenzahlHandleBack = function () {
    if (!dom.modalHelp.classList.contains("hidden")) {
      dom.modalHelp.classList.add("hidden");
      return true;
    }
    if (!dom.modalGameOver.classList.contains("hidden")) {
      dom.modalGameOver.classList.add("hidden");
      return true;
    }
    if (appState.selectedBoardKey || appState.selectedTrayIdx !== null) {
      appState.selectedBoardKey = null;
      appState.selectedTrayIdx = null;
      renderTray();
      updateBoardHighlights(null);
      updateStatusText();
      return true;
    }
    return false;
  };

  function bootstrap() {
    initGridGeometry();
    appState.modes.auto = createFreshModeState("auto");
    appState.modes.manual = createFreshModeState("manual");
    loadState();
    buildBoardSlotsOnce();
    buildColorLegend();
    bindUIEvents();
    renderAll();
    if (currentModeState().gameOver) {
      showGameOverModal();
    }
  }

  bootstrap();
})();
