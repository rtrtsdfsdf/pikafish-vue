<template>
  <div class="chess-board-container">
    <!-- 棋盘 -->
    <div class="board-frame">
      <div class="board">
        <!-- 上半部分（黑方，第0-4行） -->
        <div 
          v-for="(row, rowIndex) in store.board.slice(0, 5)" 
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
              'highlight-from': isHighlightFrom(rowIndex, colIndex),
              'highlight-to': isHighlightTo(rowIndex, colIndex)
            }"
            @click="handleCellClick(rowIndex, colIndex)"
          >
            <span v-if="piece !== ' '" class="piece" :class="getPieceColor(piece)">
              {{ getPieceName(piece) }}
            </span>
          </div>
        </div>
        
        <!-- 楚河汉界 -->
        <div class="river">
          <span class="river-left">楚 河</span>
          <span class="river-right">汉 界</span>
        </div>
        
        <!-- 下半部分（红方，第5-9行） -->
        <div 
          v-for="(row, rowIndex) in store.board.slice(5)" 
          :key="rowIndex + 5"
          class="board-row"
        >
          <div
            v-for="(piece, colIndex) in row"
            :key="colIndex"
            class="cell"
            :class="{
              'selected': isSelected(rowIndex + 5, colIndex),
              'valid-move': isValidMove(rowIndex + 5, colIndex),
              'highlight-from': isHighlightFrom(rowIndex + 5, colIndex),
              'highlight-to': isHighlightTo(rowIndex + 5, colIndex)
            }"
            @click="handleCellClick(rowIndex + 5, colIndex)"
          >
            <span v-if="piece !== ' '" class="piece" :class="getPieceColor(piece)">
              {{ getPieceName(piece) }}
            </span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 游戏结束提示 -->
    <div v-if="store.gameOver" class="game-over-overlay">
      <div class="game-over-content">
        <div class="winner-text">
          {{ store.winner === 'red' ? '🔴 红方' : '⚫ 黑方' }}获胜!
        </div>
        <button @click="store.resetGame()" class="restart-btn">再来一局</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useChessStore } from '@/stores/chess'
import { PIECE_NAMES, getPieceColor } from '@/utils/chessLogic'
import { Capacitor } from '@capacitor/core'

const store = useChessStore()

// 暴露方法给父组件
defineExpose({
  reset: () => store.resetGame(),
  undo: () => store.undoMove()
})

// 事件
const emit = defineEmits<{
  move: [move: any]
}>()

function getPieceName(piece: string): string {
  return PIECE_NAMES[piece] || ''
}

function isSelected(row: number, col: number): boolean {
  return store.selectedPos?.row === row && store.selectedPos?.col === col
}

function isValidMove(row: number, col: number): boolean {
  return store.validMoves.some(m => m.row === row && m.col === col)
}

function isHighlightFrom(row: number, col: number): boolean {
  return store.lastMove?.from.row === row && store.lastMove?.from.col === col
}

function isHighlightTo(row: number, col: number): boolean {
  return store.lastMove?.to.row === row && store.lastMove?.to.col === col
}

function handleCellClick(row: number, col: number) {
  const result = store.selectPiece({ row, col })
  if (result?.moved) {
    emit('move', result.move)
  }
}

onMounted(async () => {
  console.log('[ChessBoard] Platform:', Capacitor.getPlatform())
  
  if (Capacitor.isNativePlatform()) {
    console.log('[ChessBoard] Initializing engine...')
    try {
      await store.initGameEngine()
      console.log('[ChessBoard] Engine initialized')
    } catch (e) {
      console.error('[ChessBoard] Engine init failed:', e)
    }
  }
})
</script>

<style scoped>
.chess-board-container {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.board-frame {
  background: linear-gradient(135deg, #d4a574 0%, #c49a6c 50%, #b8956a 100%);
  border: 4px solid #5d4037;
  border-radius: 4px;
  padding: 6px;
  box-shadow: 
    0 4px 16px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.board {
  display: flex;
  flex-direction: column;
  background: #f5deb3;
  border: 2px solid #5d4037;
}

.board-row {
  display: flex;
}

.cell {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  border: 1px solid #8b7355;
  background: #f5deb3;
  transition: background 0.15s ease;
}

/* 响应式尺寸 */
@media (min-width: 360px) {
  .cell { width: 38px; height: 38px; }
}

@media (min-width: 400px) {
  .cell { width: 42px; height: 42px; }
}

@media (min-width: 450px) {
  .cell { width: 46px; height: 46px; }
}

.cell:hover {
  background: #e8d4a8;
}

.cell.selected {
  background: #ffeb3b !important;
  box-shadow: inset 0 0 8px rgba(255, 193, 7, 0.8);
}

.cell.valid-move {
  background: rgba(144, 238, 144, 0.5) !important;
}

.cell.valid-move::after {
  content: '';
  position: absolute;
  width: 12px;
  height: 12px;
  background: rgba(76, 175, 80, 0.6);
  border-radius: 50%;
}

.cell.highlight-from {
  background: rgba(255, 193, 7, 0.4) !important;
}

.cell.highlight-to {
  background: rgba(255, 152, 0, 0.4) !important;
}

.piece {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
  user-select: none;
  box-shadow: 
    2px 2px 4px rgba(0, 0, 0, 0.3),
    inset 0 1px 2px rgba(255, 255, 255, 0.3);
  border: 2px solid;
}

@media (min-width: 360px) {
  .piece { width: 34px; height: 34px; font-size: 19px; }
}

@media (min-width: 400px) {
  .piece { width: 38px; height: 38px; font-size: 21px; }
}

@media (min-width: 450px) {
  .piece { width: 42px; height: 42px; font-size: 23px; }
}

@media (min-width: 380px) { .piece { font-size: 32px; } }
@media (min-width: 450px) { .piece { font-size: 36px; } }
@media (min-width: 550px) { .piece { font-size: 40px; } }

.piece.red {
  background: linear-gradient(145deg, #fff5f5, #ffe0e0);
  color: #c62828;
  border-color: #c62828;
}

.piece.black {
  background: linear-gradient(145deg, #f5f5f5, #e0e0e0);
  color: #212121;
  border-color: #212121;
}

/* 翻转时棋子保持正向 */
.river {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 36px;
  background: #f5deb3;
  padding: 0 8px;
}

.river-left, .river-right {
  font-size: 14px;
  font-weight: bold;
  color: #5d4037;
  letter-spacing: 4px;
  font-family: 'KaiTi', 'STKaiti', serif;
}

.river-left {
  /* 楚河在左边 */
}

.river-right {
  /* 汉界在右边 */
}

/* 游戏结束遮罩 */
.game-over-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 8px;
}

.game-over-content {
  text-align: center;
  color: white;
}

.winner-text {
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 20px;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.5);
}

.restart-btn {
  padding: 12px 32px;
  font-size: 18px;
  border: none;
  border-radius: 8px;
  background: #4CAF50;
  color: white;
  cursor: pointer;
  transition: all 0.2s;
}

.restart-btn:hover {
  background: #45a049;
  transform: scale(1.05);
}
</style>
