<template>
  <div class="chess-board">
    <!-- 棋盘 -->
    <div class="board-container">
      <div class="board-wrapper">
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
        
        <!-- 箭头 SVG 覆盖层 -->
        <svg class="arrows-overlay" viewBox="0 0 360 400">
          <defs>
            <!-- 为每种颜色创建箭头标记 -->
            <marker 
              id="arrowhead-yellow" 
              markerWidth="10" 
              markerHeight="7" 
              refX="9" 
              refY="3.5" 
              orient="auto"
            >
              <polygon points="0 0, 10 3.5, 0 7" fill="#ffeb3b" />
            </marker>
            <marker 
              id="arrowhead-orange" 
              markerWidth="10" 
              markerHeight="7" 
              refX="9" 
              refY="3.5" 
              orient="auto"
            >
              <polygon points="0 0, 10 3.5, 0 7" fill="#ff9800" />
            </marker>
            <marker 
              id="arrowhead-green" 
              markerWidth="10" 
              markerHeight="7" 
              refX="9" 
              refY="3.5" 
              orient="auto"
            >
              <polygon points="0 0, 10 3.5, 0 7" fill="#4caf50" />
            </marker>
          </defs>
          
          <!-- 绘制每个箭头 -->
          <line
            v-for="(arrow, index) in store.arrows"
            :key="index"
            :x1="getArrowX(arrow.from.col)"
            :y1="getArrowY(arrow.from.row)"
            :x2="getArrowX(arrow.to.col)"
            :y2="getArrowY(arrow.to.row)"
            :stroke="arrow.color"
            stroke-width="4"
            :marker-end="getMarkerId(arrow.color)"
            class="arrow-line"
          />
        </svg>
      </div>
      
      <!-- 楚河汉界 -->
      <div class="river">楚河&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;汉界</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useChessStore } from '@/stores/chess';
import { PIECE_NAMES, getPieceColor } from '@/utils/chessLogic';

const store = useChessStore();

// 棋盘尺寸常量
const CELL_SIZE = 40; // 每格 40px（适配手机屏幕）

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

// 计算箭头的 X 坐标（格子中心）
function getArrowX(col: number): number {
  return col * CELL_SIZE + CELL_SIZE / 2;
}

// 计算箭头的 Y 坐标（格子中心）
function getArrowY(row: number): number {
  return row * CELL_SIZE + CELL_SIZE / 2;
}

// 根据颜色获取箭头标记 ID
function getMarkerId(color: string): string {
  const colorMap: Record<string, string> = {
    '#ffeb3b': 'url(#arrowhead-yellow)',
    '#ff9800': 'url(#arrowhead-orange)',
    '#4caf50': 'url(#arrowhead-green)',
  };
  return colorMap[color] || 'url(#arrowhead-yellow)';
}
</script>

<style scoped>
.chess-board {
  width: 100vw;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0;
  margin: 0;
  box-sizing: border-box;
}

.board-container {
  background: #f0d9b5;
  border: 2px solid #8b4513;
  border-radius: 4px;
  padding: 5px;
  box-sizing: border-box;
  max-width: calc(100vw - 10px);
  overflow: hidden;
}

.board-wrapper {
  position: relative;
  width: fit-content;
}

.board {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.board-row {
  display: flex;
  height: 40px;
}

.cell {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  border: 1px solid #d4a574;
  background: #f0d9b5;
  box-sizing: border-box;
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

.red-piece .piece {
  color: #cc0000;
}

.black-piece .piece {
  color: #000000;
}

/* 箭头覆盖层 */
.arrows-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 360px;
  height: 400px;
  pointer-events: none;
  z-index: 10;
}

.arrow-line {
  opacity: 0.8;
  filter: drop-shadow(1px 1px 1px rgba(0, 0, 0, 0.5));
}

.river {
  text-align: center;
  font-size: 18px;
  font-weight: bold;
  color: #8b4513;
  padding: 8px;
  letter-spacing: 15px;
}
</style>
