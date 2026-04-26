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
        
        <div v-if="store.engineInfo" class="engine-info">
          <div>深度: {{ store.engineInfo.depth }}</div>
          <div v-if="store.engineInfo.score">
            分数: {{ store.engineInfo.score > 0 ? '+' : '' }}{{ (store.engineInfo.score / 100).toFixed(2) }}
          </div>
          <div v-if="store.engineInfo.mate">
            杀棋: {{ store.engineInfo.mate }} 步
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
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useChessStore } from '@/stores/chess';
import { PIECE_NAMES, getPieceColor } from '@/utils/chessLogic';

const store = useChessStore();

onMounted(async () => {
  try {
    await store.initGameEngine();
  } catch (e) {
    console.log('Engine init skipped');
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
  .cell {
    width: 42px;
    height: 42px;
  }
}

@media (min-width: 500px) {
  .cell {
    width: 48px;
    height: 48px;
  }
}

.cell:hover {
  background: #e8c99b;
}

.cell.selected {
  background: #ffff00 !important;
}

.cell.valid-move {
  background: #90ee90 !important;
}

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

@media (min-width: 400px) {
  .piece {
    font-size: 30px;
  }
}

@media (min-width: 500px) {
  .piece {
    font-size: 34px;
  }
}

.red-piece .piece {
  color: #cc0000;
}

.black-piece .piece {
  color: #000000;
}

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

.engine-info {
  padding: 10px;
  background: #e8f5e9;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 12px;
}

.engine-info div {
  margin: 4px 0;
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
}

.controls button {
  flex: 1;
  max-width: 150px;
  padding: 12px 20px;
  font-size: 16px;
  cursor: pointer;
  background: #4caf50;
  color: white;
  border: none;
  border-radius: 8px;
  transition: background 0.3s;
  font-weight: bold;
}

.controls button:hover {
  background: #45a049;
}

.controls button:disabled {
  background: #bbb;
  cursor: not-allowed;
}
</style>
