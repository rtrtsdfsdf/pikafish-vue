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
        <svg class="arrows-overlay" :viewBox="svgViewBox">
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
import { onMounted, ref, computed, onUnmounted } from 'vue';
import { useChessStore } from '@/stores/chess';
import { PIECE_NAMES, getPieceColor } from '@/utils/chessLogic';

const store = useChessStore();

// 动态计算格子大小
const cellSize = ref(40);

function updateCellSize() {
  // 计算可用空间
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  // 预留工具栏和底部面板的空间
  const toolbarHeight = 50; // 工具栏高度
  const bottomPanelHeight = 60; // 底部面板折叠时高度
  const padding = 20; // 边距

  // 计算可用宽度和高度
  const availableWidth = vw - padding;
  const availableHeight = vh - toolbarHeight - bottomPanelHeight - padding;

  // 棋盘是 9 列 10 行
  const cellByWidth = availableWidth / 9;
  const cellByHeight = availableHeight / 10;

  // 取较小值，确保棋盘完整显示
  cellSize.value = Math.floor(Math.min(cellByWidth, cellByHeight));

  // 限制最小和最大值
  cellSize.value = Math.max(30, Math.min(50, cellSize.value));
}

onMounted(async () => {
  updateCellSize();
  window.addEventListener('resize', updateCellSize);
  await store.initGameEngine();
});

onUnmounted(() => {
  window.removeEventListener('resize', updateCellSize);
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
  return col * cellSize.value + cellSize.value / 2;
}

// 计算箭头的 Y 坐标（格子中心）
function getArrowY(row: number): number {
  return row * cellSize.value + cellSize.value / 2;
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

// 计算 SVG viewBox
const svgViewBox = computed(() => {
  const width = 9 * cellSize.value;
  const height = 10 * cellSize.value;
  return `0 0 ${width} ${height}`;
});
</script>

<style scoped>
.chess-board {
  /* 棋盘容器自适应 */
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
}

.board-container {
  background: #f0d9b5;
  border: 2px solid #8b4513;
  border-radius: 4px;
  padding: 4px;
  box-sizing: border-box;
  /* 使用 vmin 确保在小屏幕上也能完整显示 */
  max-width: calc(100vw - 16px);
  max-height: calc(100vh - 16px);
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
  /* 使用 calc 动态计算格子大小 */
  height: calc((100vw - 24px) / 9);
  max-height: calc((100vh - 200px) / 10);
}

.cell {
  /* 使用 calc 动态计算格子大小 */
  width: calc((100vw - 24px) / 9);
  max-width: calc((100vh - 200px) / 10);
  height: 100%;
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
  width: 20%;
  height: 20%;
  min-width: 8px;
  min-height: 8px;
  background: rgba(0, 128, 0, 0.5);
  border-radius: 50%;
}

.piece {
  /* 棋子大小随格子缩放 */
  font-size: clamp(18px, 5.5vw, 32px);
  font-weight: bold;
  text-shadow: 1px 1px 2px rgba(0,0,0,0.3);
}

.red-piece .piece {
  color: #cc0000;
}

.black-piece .piece {
  color: #000000;
}

/* 箭头覆盖层 - 使用百分比 */
.arrows-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 10;
}

.arrow-line {
  opacity: 0.8;
  filter: drop-shadow(1px 1px 1px rgba(0, 0, 0, 0.5));
}

.river {
  text-align: center;
  font-size: clamp(14px, 4vw, 20px);
  font-weight: bold;
  color: #8b4513;
  padding: clamp(4px, 1vw, 10px);
  letter-spacing: clamp(8px, 3vw, 20px);
}
</style>
