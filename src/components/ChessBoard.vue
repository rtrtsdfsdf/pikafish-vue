<template>
  <div class="chess-board">
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
      
      <!-- 楚河汉界 -->
      <div class="river">楚河&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;汉界</div>
    </div>

    <!-- 信息面板 -->
    <div class="info-panel">
      <div class="turn-indicator">
        当前回合: {{ store.currentTurn === 'red' ? '红方' : '黑方' }}
      </div>
      
      <!-- 引擎信息 -->
      <div v-if="store.engineInfo" class="engine-info">
        <div>深度: {{ store.engineInfo.depth }}</div>
        <div v-if="store.engineInfo.score">
          分数: {{ store.engineInfo.score > 0 ? '+' : '' }}{{ store.engineInfo.score / 100 }}
        </div>
        <div v-if="store.engineInfo.mate">
          杀棋: {{ store.engineInfo.mate }} 步
        </div>
        <div v-if="store.engineInfo.pv" class="pv">
          最佳走法: {{ store.engineInfo.pv.slice(0, 3).join(' ') }}
        </div>
      </div>
      
      <div v-if="store.engineThinking" class="thinking">
        引擎分析中...
      </div>
      
      <!-- 游戏结束 -->
      <div v-if="store.gameOver" class="game-over">
        游戏结束! {{ store.winner === 'red' ? '红方' : '黑方' }}获胜!
      </div>
      
      <!-- 控制按钮 -->
      <div class="controls">
        <button @click="store.undoMove()" :disabled="store.history.length === 0">
          悔棋
        </button>
        <button @click="store.resetGame()">重新开始</button>
        <button @click="store.flipBoard()">翻转棋盘</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useChessStore } from '@/stores/chess';
import { PIECE_NAMES, getPieceColor } from '@/utils/chessLogic';

const store = useChessStore();

onMounted(async () => {
  await store.initGameEngine();
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
.chess-board {
  display: flex;
  gap: 20px;
  padding: 20px;
  justify-content: center;
  align-items: flex-start;
}

.board-container {
  background: #f0d9b5;
  border: 3px solid #8b4513;
  border-radius: 8px;
  padding: 10px;
}

.board {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.board-row {
  display: flex;
  height: 50px;
}

.cell {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  border: 1px solid #d4a574;
  background: #f0d9b5;
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
  width: 12px;
  height: 12px;
  background: rgba(0, 128, 0, 0.5);
  border-radius: 50%;
}

.piece {
  font-size: 32px;
  font-weight: bold;
  text-shadow: 1px 1px 2px rgba(0,0,0,0.3);
}

.red-piece .piece {
  color: #cc0000;
}

.black-piece .piece {
  color: #000000;
}

.river {
  text-align: center;
  font-size: 24px;
  font-weight: bold;
  color: #8b4513;
  padding: 10px;
  letter-spacing: 20px;
}

.info-panel {
  min-width: 250px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.turn-indicator {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 15px;
  padding: 10px;
  background: #f5f5f5;
  border-radius: 4px;
}

.engine-info {
  margin-bottom: 15px;
  padding: 10px;
  background: #e8f5e9;
  border-radius: 4px;
  font-size: 14px;
}

.engine-info div {
  margin: 5px 0;
}

.pv {
  font-family: monospace;
  word-break: break-all;
}

.thinking {
  color: #666;
  font-style: italic;
  margin-bottom: 15px;
}

.game-over {
  font-size: 20px;
  font-weight: bold;
  color: #cc0000;
  text-align: center;
  padding: 15px;
  background: #ffebee;
  border-radius: 4px;
  margin-bottom: 15px;
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.controls button {
  padding: 10px 20px;
  font-size: 16px;
  cursor: pointer;
  background: #4caf50;
  color: white;
  border: none;
  border-radius: 4px;
  transition: background 0.3s;
}

.controls button:hover {
  background: #45a049;
}

.controls button:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>
