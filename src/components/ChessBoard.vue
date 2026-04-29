<template>
  <div class="chess-board">
    <div class="board-container" ref="boardContainer">
      <canvas 
        ref="boardCanvas" 
        class="board-canvas"
        @click="handleCanvasClick"
        @touchstart="handleTouchStart"
      ></canvas>
      
      <!-- 箭头 SVG 覆盖层 -->
      <svg class="arrows-overlay" :viewBox="svgViewBox">
        <defs>
          <marker id="arrowhead-yellow" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#ffeb3b" />
          </marker>
          <marker id="arrowhead-orange" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#ff9800" />
          </marker>
          <marker id="arrowhead-green" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#4caf50" />
          </marker>
        </defs>
        
        <line
          v-for="(arrow, index) in store.arrows"
          :key="index"
          :x1="getArrowX(arrow.from.col)"
          :y1="getArrowY(arrow.from.row)"
          :x2="getArrowX(arrow.to.col)"
          :y2="getArrowY(arrow.to.row)"
          :stroke="arrow.color"
          stroke-width="3"
          :marker-end="getMarkerId(arrow.color)"
          class="arrow-line"
        />
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed, onUnmounted, watch, nextTick } from 'vue';
import { useChessStore } from '@/stores/chess';
import { PIECE_NAMES, getPieceColor } from '@/utils/chessLogic';

const store = useChessStore();

const boardCanvas = ref<HTMLCanvasElement | null>(null);
const boardContainer = ref<HTMLDivElement | null>(null);

// 棋盘参数
const cellSize = ref(40);
const padding = ref(20); // 棋盘边距
const canvasWidth = ref(400);
const canvasHeight = ref(440);

// 计算棋盘尺寸
function calculateBoardSize() {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  
  const toolbarHeight = 44;
  const bottomPanelHeight = 50;
  const margin = 16;
  
  const availableWidth = vw - margin;
  const availableHeight = vh - toolbarHeight - bottomPanelHeight - margin;
  
  // 棋盘是 8 条竖线，9 条横线（10 行交叉点）
  // 宽度 = 8 * cellSize, 高度 = 9 * cellSize + 楚河汉界
  const cellByWidth = (availableWidth - 40) / 8; // 减去边距
  const cellByHeight = (availableHeight - 60) / 9.5; // 楚河汉界占半格
  
  cellSize.value = Math.floor(Math.min(cellByWidth, cellByHeight));
  cellSize.value = Math.max(28, Math.min(45, cellSize.value));
  
  padding.value = Math.floor(cellSize.value * 0.5);
  
  // 画布尺寸
  canvasWidth.value = 8 * cellSize.value + 2 * padding.value;
  canvasHeight.value = 9 * cellSize.value + 2 * padding.value + Math.floor(cellSize.value * 0.5);
}

// 绘制棋盘
function drawBoard() {
  const canvas = boardCanvas.value;
  if (!canvas) return;
  
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  
  // 设置画布尺寸
  canvas.width = canvasWidth.value;
  canvas.height = canvasHeight.value;
  
  const cs = cellSize.value;
  const p = padding.value;
  
  // 背景
  ctx.fillStyle = '#f0d9b5';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  
  // 绘制网格线
  ctx.strokeStyle = '#8b4513';
  ctx.lineWidth = 1;
  
  // 横线（10条）
  for (let i = 0; i < 10; i++) {
    ctx.beginPath();
    ctx.moveTo(p, p + i * cs);
    ctx.lineTo(p + 8 * cs, p + i * cs);
    ctx.stroke();
  }
  
  // 竖线（9条，中间楚河汉界断开）
  for (let i = 0; i < 9; i++) {
    // 上半部分
    ctx.beginPath();
    ctx.moveTo(p + i * cs, p);
    ctx.lineTo(p + i * cs, p + 4 * cs);
    ctx.stroke();
    
    // 下半部分
    ctx.beginPath();
    ctx.moveTo(p + i * cs, p + 5 * cs);
    ctx.lineTo(p + i * cs, p + 9 * cs);
    ctx.stroke();
  }
  
  // 边框竖线（贯穿楚河汉界）
  ctx.beginPath();
  ctx.moveTo(p, p);
  ctx.lineTo(p, p + 9 * cs);
  ctx.stroke();
  
  ctx.beginPath();
  ctx.moveTo(p + 8 * cs, p);
  ctx.lineTo(p + 8 * cs, p + 9 * cs);
  ctx.stroke();
  
  // 九宫格斜线
  // 上方九宫
  ctx.beginPath();
  ctx.moveTo(p + 3 * cs, p);
  ctx.lineTo(p + 5 * cs, p + 2 * cs);
  ctx.stroke();
  
  ctx.beginPath();
  ctx.moveTo(p + 5 * cs, p);
  ctx.lineTo(p + 3 * cs, p + 2 * cs);
  ctx.stroke();
  
  // 下方九宫
  ctx.beginPath();
  ctx.moveTo(p + 3 * cs, p + 7 * cs);
  ctx.lineTo(p + 5 * cs, p + 9 * cs);
  ctx.stroke();
  
  ctx.beginPath();
  ctx.moveTo(p + 5 * cs, p + 7 * cs);
  ctx.lineTo(p + 3 * cs, p + 9 * cs);
  ctx.stroke();
  
  // 绘制炮和兵的位置标记
  drawPositionMarks(ctx, cs, p);
  
  // 楚河汉界
  ctx.font = `bold ${Math.floor(cs * 0.5)}px serif`;
  ctx.fillStyle = '#8b4513';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  
  const riverY = p + 4.5 * cs;
  ctx.fillText('楚 河', p + 2 * cs, riverY);
  ctx.fillText('汉 界', p + 6 * cs, riverY);
  
  // 绘制棋子
  drawPieces(ctx, cs, p);
  
  // 绘制选中状态和有效走法
  drawSelection(ctx, cs, p);
}

// 绘制位置标记（炮和兵的位置）
function drawPositionMarks(ctx: CanvasRenderingContext2D, cs: number, p: number) {
  const markSize = cs * 0.15;
  const offset = cs * 0.08;
  
  // 需要标记的位置
  const positions = [
    // 炮的位置
    [1, 2], [7, 2], [1, 7], [7, 7],
    // 兵/卒的位置
    [0, 3], [2, 3], [4, 3], [6, 3], [8, 3],
    [0, 6], [2, 6], [4, 6], [6, 6], [8, 6]
  ];
  
  positions.forEach(([col, row]) => {
    const x = p + col * cs;
    const y = p + row * cs;
    
    // 四个角的标记
    const corners = [
      { dx: -offset, dy: -offset, sx: -1, sy: -1 }, // 左上
      { dx: offset, dy: -offset, sx: 1, sy: -1 },   // 右上
      { dx: -offset, dy: offset, sx: -1, sy: 1 },   // 左下
      { dx: offset, dy: offset, sx: 1, sy: 1 }      // 右下
    ];
    
    corners.forEach(({ dx, dy, sx, sy }) => {
      // 检查是否在棋盘边缘
      if ((col === 0 && sx === -1) || (col === 8 && sx === 1)) return;
      
      ctx.beginPath();
      ctx.moveTo(x + dx, y + dy);
      ctx.lineTo(x + dx, y + dy + sy * markSize);
      ctx.moveTo(x + dx, y + dy);
      ctx.lineTo(x + dx + sx * markSize, y + dy);
      ctx.stroke();
    });
  });
}

// 绘制棋子
function drawPieces(ctx: CanvasRenderingContext2D, cs: number, p: number) {
  const radius = cs * 0.42;
  
  for (let row = 0; row < 10; row++) {
    for (let col = 0; col < 9; col++) {
      const piece = store.board[row][col];
      if (piece === ' ') continue;
      
      const x = p + col * cs;
      const y = p + row * cs;
      const color = getPieceColor(piece);
      const isRed = color === 'red';
      
      // 棋子底色
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fillStyle = '#fff8dc';
      ctx.fill();
      
      // 棋子边框
      ctx.strokeStyle = isRed ? '#cc0000' : '#000000';
      ctx.lineWidth = 2;
      ctx.stroke();
      
      // 内圈
      ctx.beginPath();
      ctx.arc(x, y, radius * 0.85, 0, Math.PI * 2);
      ctx.strokeStyle = isRed ? '#cc0000' : '#000000';
      ctx.lineWidth = 1;
      ctx.stroke();
      
      // 棋子文字
      ctx.font = `bold ${Math.floor(cs * 0.5)}px "KaiTi", "STKaiti", serif`;
      ctx.fillStyle = isRed ? '#cc0000' : '#000000';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(PIECE_NAMES[piece] || '', x, y);
    }
  }
}

// 绘制选中状态和有效走法
function drawSelection(ctx: CanvasRenderingContext2D, cs: number, p: number) {
  // 选中棋子
  if (store.selectedPos) {
    const x = p + store.selectedPos.col * cs;
    const y = p + store.selectedPos.row * cs;
    const radius = cs * 0.48;
    
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.strokeStyle = '#ffff00';
    ctx.lineWidth = 3;
    ctx.stroke();
  }
  
  // 有效走法
  store.validMoves.forEach(move => {
    const x = p + move.col * cs;
    const y = p + move.row * cs;
    const radius = cs * 0.15;
    
    // 如果目标位置有棋子，画圆圈
    if (store.board[move.row][move.col] !== ' ') {
      ctx.beginPath();
      ctx.arc(x, y, cs * 0.42, 0, Math.PI * 2);
      ctx.strokeStyle = '#00ff00';
      ctx.lineWidth = 3;
      ctx.stroke();
    } else {
      // 否则画小圆点
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(0, 255, 0, 0.5)';
      ctx.fill();
    }
  });
}

// 处理点击
function handleCanvasClick(event: MouseEvent) {
  const pos = getPositionFromEvent(event);
  if (pos) {
    store.selectPiece(pos);
  }
}

function handleTouchStart(event: TouchEvent) {
  event.preventDefault();
  const touch = event.touches[0];
  if (touch) {
    const pos = getPositionFromEvent(touch as any);
    if (pos) {
      store.selectPiece(pos);
    }
  }
}

function getPositionFromEvent(event: { clientX: number; clientY: number }): { row: number; col: number } | null {
  const canvas = boardCanvas.value;
  if (!canvas) return null;
  
  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width / rect.width;
  const scaleY = canvas.height / rect.height;
  
  const x = (event.clientX - rect.left) * scaleX;
  const y = (event.clientY - rect.top) * scaleY;
  
  const cs = cellSize.value;
  const p = padding.value;
  
  const col = Math.round((x - p) / cs);
  const row = Math.round((y - p) / cs);
  
  if (col >= 0 && col < 9 && row >= 0 && row < 10) {
    return { row, col };
  }
  
  return null;
}

// 箭头相关
function getArrowX(col: number): number {
  return padding.value + col * cellSize.value;
}

function getArrowY(row: number): number {
  return padding.value + row * cellSize.value;
}

function getMarkerId(color: string): string {
  const colorMap: Record<string, string> = {
    '#ffeb3b': 'url(#arrowhead-yellow)',
    '#ff9800': 'url(#arrowhead-orange)',
    '#4caf50': 'url(#arrowhead-green)',
  };
  return colorMap[color] || 'url(#arrowhead-yellow)';
}

const svgViewBox = computed(() => {
  return `0 0 ${canvasWidth.value} ${canvasHeight.value}`;
});

// 监听棋盘变化重绘
watch(() => store.board, () => {
  nextTick(drawBoard);
}, { deep: true });

watch(() => store.selectedPos, drawBoard);
watch(() => store.validMoves, drawBoard, { deep: true });
watch(() => store.arrows, drawBoard, { deep: true });

// 窗口大小变化
function handleResize() {
  calculateBoardSize();
  nextTick(drawBoard);
}

onMounted(async () => {
  calculateBoardSize();
  window.addEventListener('resize', handleResize);
  await store.initGameEngine();
  nextTick(drawBoard);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
});
</script>

<style scoped>
.chess-board {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
}

.board-container {
  position: relative;
  background: #f0d9b5;
  border: 2px solid #8b4513;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.board-canvas {
  display: block;
  touch-action: none;
}

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
  opacity: 0.85;
  filter: drop-shadow(1px 1px 2px rgba(0, 0, 0, 0.6));
}
</style>
