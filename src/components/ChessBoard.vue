<template>
  <div class="chess-app">
    <!-- 标题 -->
    <header class="app-header">
      <h1>🎮 皮卡鱼中国象棋</h1>
    </header>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 棋盘 -->
      <div class="board-container">
        <div class="board">
          <div 
            v-for="(row, rowIndex) in store.board" 
            :key="rowIndex"
            class="board-row"
          >
            <div
              v-for="(piece, colIndex) in row"
              :key="colIndex"
              class="cell"
              :class="{
                'selected': isSelected(rowIndex, colIndex),
                'valid-move': isValidMove(rowIndex, colIndex),
                'red-piece': getPieceColor(piece) === 'red',
                'black-piece': getPieceColor(piece) === 'black'
              }"
              @click="handleCellClick(rowIndex, colIndex)"
            >
              <span v-if="piece !== ' '" class="piece">
                {{ getPieceName(piece) }}
              </span>
            </div>
          </div>
        </div>
        <div class="river">楚河&nbsp;&nbsp;&nbsp;&nbsp;汉界</div>
      </div>

      <!-- 信息面板 -->
      <div class="info-panel">
        <div class="turn-indicator">
          {{ store.currentTurn === 'red' ? '🔴 红方' : '⚫ 黑方' }}走棋
        </div>
        
        <!-- 引擎状态 -->
        <div class="engine-status" :class="{ 'error': engineError, 'success': engineReady }">
          <div class="status-label">引擎状态:</div>
          <div class="status-value">{{ engineStatus }}</div>
        </div>
        
        <div v-if="store.engineInfo" class="engine-info">
          <div>深度: {{ store.engineInfo.depth }}</div>
          <div v-if="store.engineInfo.score !== undefined">
            分数: {{ formatScore(store.engineInfo.score, store.engineInfo.scoreType) }}
          </div>
          <div v-if="store.engineInfo.pv && store.engineInfo.pv.length > 0" class="pv-line">
            推荐: {{ store.engineInfo.pv.slice(0, 4).join(' ') }}
          </div>
        </div>
        
        <div v-if="store.engineThinking" class="thinking">
          ⏳ 分析中...
        </div>
        
        <div v-if="store.gameOver" class="game-over">
          🎉 {{ store.winner === 'red' ? '红方' : '黑方' }}获胜!
        </div>
        
        <div class="controls">
          <button @click="store.undoMove()" :disabled="store.history.length === 0">
            ↩️ 悔棋
          </button>
          <button @click="store.resetGame()">
            🔄 重开
          </button>
          <button @click="toggleDebug()" class="debug-btn">
            {{ showDebug ? '隐藏日志' : '显示日志' }}
          </button>
        </div>
        
        <!-- 调试日志 -->
        <div v-if="showDebug" class="debug-panel">
          <div class="debug-header">
            <span>调试日志</span>
            <div class="debug-actions">
              <button @click="copyLogs()" class="copy-btn">📋 复制</button>
              <button @click="clearLogs()" class="clear-btn">清空</button>
            </div>
          </div>
          <div class="debug-logs" ref="debugLogsRef">
            <div v-for="(log, index) in debugLogs" :key="index" 
                 :class="['log-line', log.level]">
              <span class="log-time">{{ log.time }}</span>
              <span class="log-msg">{{ log.message }}</span>
            </div>
          </div>
          <div v-if="copySuccess" class="copy-success">✓ 已复制到剪贴板</div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import { useChessStore } from '@/stores/chess';
import { PIECE_NAMES, getPieceColor } from '@/utils/chessLogic';
import { Capacitor } from '@capacitor/core';
import { Clipboard } from '@capacitor/clipboard';

const store = useChessStore();

// 调试相关
const showDebug = ref(true);  // 默认显示日志
const debugLogs = ref<Array<{ time: string; level: string; message: string }>>([]);
const debugLogsRef = ref<HTMLElement | null>(null);
const copySuccess = ref(false);

// 引擎状态
const engineStatus = ref('未初始化');
const engineError = ref(false);
const engineReady = ref(false);

// 拦截 console
function setupLogging() {
  const originalLog = console.log;
  const originalError = console.error;
  const originalWarn = console.warn;
  
  console.log = (...args) => {
    originalLog.apply(console, args);
    addLog('info', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
  };
  
  console.error = (...args) => {
    originalError.apply(console, args);
    addLog('error', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
    engineError.value = true;
  };
  
  console.warn = (...args) => {
    originalWarn.apply(console, args);
    addLog('warn', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
  };
}

function addLog(level: string, message: string) {
  const now = new Date();
  const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
  
  const truncatedMsg = message.length > 300 ? message.substring(0, 300) + '...' : message;
  
  debugLogs.value.push({ time, level, message: truncatedMsg });
  
  if (debugLogs.value.length > 200) {
    debugLogs.value.shift();
  }
  
  nextTick(() => {
    if (debugLogsRef.value) {
      debugLogsRef.value.scrollTop = debugLogsRef.value.scrollHeight;
    }
  });
  
  // 更新引擎状态
  if (message.includes('[Engine]') || message.includes('[Store]')) {
    if (message.includes('Initialized successfully') || message.includes('ready')) {
      engineStatus.value = '✅ 已就绪';
      engineError.value = false;
      engineReady.value = true;
    } else if (message.includes('failed') || message.includes('error') || message.includes('not found')) {
      engineStatus.value = '❌ ' + message.replace(/\[.*?\]/g, '').trim();
      engineError.value = true;
      engineReady.value = false;
    } else if (message.includes('Analyzing')) {
      engineStatus.value = '🔍 分析中...';
    }
  }
}

function toggleDebug() {
  showDebug.value = !showDebug.value;
}

function clearLogs() {
  debugLogs.value = [];
}

async function copyLogs() {
  const logText = debugLogs.value
    .map(log => `[${log.time}] [${log.level.toUpperCase()}] ${log.message}`)
    .join('\n');
  
  try {
    if (Capacitor.isNativePlatform()) {
      await Clipboard.write({ string: logText });
    } else {
      await navigator.clipboard.writeText(logText);
    }
    copySuccess.value = true;
    setTimeout(() => { copySuccess.value = false; }, 2000);
  } catch (e) {
    console.error('Failed to copy logs:', e);
  }
}

function formatScore(score: number, scoreType?: 'cp' | 'mate'): string {
  if (scoreType === 'mate') {
    return `杀棋 ${score > 0 ? '+' : ''}${score} 步`;
  }
  const pawns = (score / 100).toFixed(2);
  return `${score > 0 ? '+' : ''}${pawns}`;
}

onMounted(async () => {
  setupLogging();
  
  console.log('[App] Platform:', Capacitor.getPlatform());
  console.log('[App] Is Native:', Capacitor.isNativePlatform());
  
  if (Capacitor.isNativePlatform()) {
    console.log('[App] Initializing engine...');
    engineStatus.value = '⏳ 初始化中...';
    
    try {
      await store.initGameEngine();
      console.log('[App] Engine init completed');
    } catch (e) {
      console.error('[App] Engine init failed:', e);
      engineStatus.value = '❌ 初始化失败: ' + (e as Error).message;
    }
  } else {
    console.log('[App] Running in browser, engine disabled');
    engineStatus.value = '🌐 浏览器模式';
  }
});

function getPieceName(piece: string): string {
  return PIECE_NAMES[piece] || '';
}

function isSelected(row: number, col: number): boolean {
  return store.selectedPos?.row === row && store.selectedPos?.col === col;
}

function isValidMove(row: number, col: number): boolean {
  return store.validMoves.some(m => m.row === row && m.col === col);
}

function handleCellClick(row: number, col: number) {
  store.selectPiece({ row, col });
}
</script>

<style scoped>
.chess-app {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.app-header {
  text-align: center;
  padding: 15px;
  color: white;
}

.app-header h1 {
  font-size: 24px;
  margin: 0;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px;
  gap: 15px;
}

.board-container {
  background: #f0d9b5;
  border: 3px solid #8b4513;
  border-radius: 8px;
  padding: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
}

.board {
  display: flex;
  flex-direction: column;
}

.board-row {
  display: flex;
}

.cell {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  border: 1px solid #d4a574;
  background: #f0d9b5;
}

@media (min-width: 400px) {
  .cell { width: 42px; height: 42px; }
}

@media (min-width: 500px) {
  .cell { width: 48px; height: 48px; }
}

.cell:hover { background: #e8c99b; }
.cell.selected { background: #ffff00 !important; }
.cell.valid-move { background: #90ee90 !important; }

.cell.valid-move::after {
  content: '';
  position: absolute;
  width: 10px;
  height: 10px;
  background: rgba(0, 128, 0, 0.5);
  border-radius: 50%;
}

.piece {
  font-size: 26px;
  font-weight: bold;
  text-shadow: 1px 1px 2px rgba(0,0,0,0.3);
}

@media (min-width: 400px) { .piece { font-size: 30px; } }
@media (min-width: 500px) { .piece { font-size: 34px; } }

.red-piece .piece { color: #cc0000; }
.black-piece .piece { color: #000000; }

.river {
  text-align: center;
  font-size: 18px;
  font-weight: bold;
  color: #8b4513;
  padding: 8px;
  letter-spacing: 15px;
}

.info-panel {
  width: 100%;
  max-width: 400px;
  padding: 15px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
}

.turn-indicator {
  font-size: 20px;
  font-weight: bold;
  text-align: center;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 8px;
  margin-bottom: 12px;
}

.engine-status {
  padding: 10px;
  background: #e3f2fd;
  border-radius: 8px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.engine-status.error {
  background: #ffebee;
  color: #c62828;
}

.engine-status.success {
  background: #e8f5e9;
  color: #2e7d32;
}

.status-label { font-weight: bold; }
.status-value { font-size: 12px; word-break: break-all; text-align: right; }

.engine-info {
  padding: 10px;
  background: #e8f5e9;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 12px;
}

.engine-info div { margin: 4px 0; }

.pv-line {
  font-family: monospace;
  font-size: 12px;
  color: #1565c0;
}

.thinking {
  text-align: center;
  color: #666;
  padding: 8px;
  margin-bottom: 12px;
}

.game-over {
  font-size: 22px;
  font-weight: bold;
  color: #cc0000;
  text-align: center;
  padding: 15px;
  background: #ffebee;
  border-radius: 8px;
  margin-bottom: 12px;
}

.controls {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}

.controls button {
  flex: 1;
  max-width: 120px;
  padding: 12px 16px;
  font-size: 14px;
  cursor: pointer;
  background: #4caf50;
  color: white;
  border: none;
  border-radius: 8px;
  transition: background 0.3s;
  font-weight: bold;
}

.controls button:hover { background: #45a049; }
.controls button:disabled { background: #bbb; cursor: not-allowed; }
.debug-btn { background: #2196f3 !important; }

.debug-panel {
  margin-top: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
}

.debug-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f5f5;
  font-weight: bold;
  font-size: 14px;
}

.debug-actions {
  display: flex;
  gap: 8px;
}

.copy-btn, .clear-btn {
  padding: 4px 12px;
  font-size: 12px;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.copy-btn { background: #4caf50; }
.clear-btn { background: #ff5722; }

.debug-logs {
  max-height: 250px;
  overflow-y: auto;
  padding: 8px;
  background: #fafafa;
  font-family: monospace;
  font-size: 11px;
}

.log-line {
  padding: 2px 0;
  border-bottom: 1px solid #eee;
}

.log-line.error { color: #c62828; background: #ffebee; }
.log-line.warn { color: #f57c00; background: #fff3e0; }
.log-line.info { color: #1565c0; }

.log-time { color: #999; margin-right: 8px; }
.log-msg { word-break: break-all; }

.copy-success {
  text-align: center;
  padding: 8px;
  background: #e8f5e9;
  color: #2e7d32;
  font-size: 12px;
}
</style>
