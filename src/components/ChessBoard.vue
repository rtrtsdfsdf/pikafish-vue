<template>
  <div class="chess-board-container">
    <!-- 棋盘 -->
    <div class="board-frame">
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
              'highlight-from': isHighlightFrom(rowIndex, colIndex),
              'highlight-to': isHighlightTo(rowIndex, colIndex)
            }"
            @click="handleCellClick(rowIndex, colIndex)"
          >
            <span v-if="piece !== ' '" class="piece" :class="[getPieceColor(piece), { flipped: flipped }]">
              {{ getPieceName(piece) }}
            </span>
          </div>
        </div>
      </div>
      <div class="river" :class="{ flipped: flipped }">楚河&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;汉界</div>
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

const props = defineProps<{
  flipped?: boolean
}>()

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
  // 如果棋盘翻转，需要转换坐标
  let actualRow = row
  let actualCol = col
  if (props.flipped) {
    actualRow = 9 - row
    actualCol = 8 - col
  }
  const result = store.selectPiece({ row: actualRow, col: actualCol })
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
  background: linear-gradient(135deg, #deb887 0%, #d2a679 50%, #c49a6c 100%);
  border: 4px solid #8B4513;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 
    0 4px 12px rgba(0, 0, 0, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.board {
  display: flex;
  flex-direction: column;
  background: #f0d9b5;
  border: 2px solid #8B4513;
}

.board-row {
  display: flex;
}

.cell {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  border: 1px solid #c9a86c;
  background: #f0d9b5;
  transition: background 0.15s ease;
}

/* 响应式尺寸 */
@media (min-width: 380px) {
  .cell { width: 44px; height: 44px; }
}

@media (min-width: 450px) {
  .cell { width: 50px; height: 50px; }
}

@media (min-width: 550px) {
  .cell { width: 56px; height: 56px; }
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
  font-size: 28px;
  font-weight: bold;
  text-shadow: 
    1px 1px 1px rgba(255, 255, 255, 0.5),
    -1px -1px 1px rgba(0, 0, 0, 0.2);
  user-select: none;
}

@media (min-width: 380px) { .piece { font-size: 32px; } }
@media (min-width: 450px) { .piece { font-size: 36px; } }
@media (min-width: 550px) { .piece { font-size: 40px; } }

.piece.red {
  color: #c62828;
}

.piece.black {
  color: #212121;
}

/* 翻转时棋子保持正向 */
.piece.flipped {
  transform: rotate(180deg);
}

.river {
  text-align: center;
  font-size: 20px;
  font-weight: bold;
  color: #5d4037;
  padding: 10px;
  letter-spacing: 20px;
  background: linear-gradient(90deg, #e8d4a8 0%, #f0d9b5 50%, #e8d4a8 100%);
  border-top: 1px solid #c9a86c;
  border-bottom: 1px solid #c9a86c;
}

.river.flipped {
  transform: rotate(180deg);
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
