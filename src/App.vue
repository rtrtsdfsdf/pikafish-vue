<template>
  <div id="app">
    <!-- 顶部工具栏 -->
    <ToolBar 
      :red-a-i="redAI"
      :black-a-i="blackAI"
      @menu="showMenu = true"
      @new-game="handleNewGame"
      @toggle-red-a-i="toggleRedAI"
      @toggle-black-a-i="toggleBlackAI"
      @flip-board="flipBoard"
      @settings="showSettings = true"
    />
    
    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 棋盘 -->
      <div class="board-wrapper" :class="{ flipped: isFlipped }">
        <ChessBoard 
          ref="chessBoardRef"
          @move="onMove"
        />
      </div>
    </main>
    
    <!-- 底部面板 -->
    <BottomPanel
      :engine-info="engineInfo"
      :is-thinking="isThinking"
      :move-history="moveHistory"
      :current-move-index="currentMoveIndex"
      :can-undo="canUndo"
      @undo="handleUndo"
      @reset="handleReset"
      @go-start="goToStart"
      @go-prev="goToPrev"
      @go-next="goToNext"
      @go-end="goToEnd"
    />
    
    <!-- 菜单弹窗 -->
    <div v-if="showMenu" class="modal-overlay" @click="showMenu = false">
      <div class="modal-content" @click.stop>
        <h3>菜单</h3>
        <button @click="handleNewGame(); showMenu = false">新对局</button>
        <button @click="showMenu = false">关闭</button>
      </div>
    </div>
    
    <!-- 设置弹窗 -->
    <div v-if="showSettings" class="modal-overlay" @click="showSettings = false">
      <div class="modal-content" @click.stop>
        <h3>设置</h3>
        <div class="setting-item">
          <label>引擎深度:</label>
          <input type="range" v-model="engineDepth" min="1" max="30" />
          <span>{{ engineDepth }}</span>
        </div>
        <button @click="showSettings = false">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import ToolBar from './components/ToolBar.vue'
import BottomPanel from './components/BottomPanel.vue'
import ChessBoard from './components/ChessBoard.vue'
import { useChessStore } from '@/stores/chess'

const store = useChessStore()

// Refs
const chessBoardRef = ref<InstanceType<typeof ChessBoard> | null>(null)
const showMenu = ref(false)
const showSettings = ref(false)
const isFlipped = ref(false)
const engineDepth = ref(20)

// AI 状态
const redAI = ref(false)
const blackAI = ref(false)

// Computed
const engineInfo = computed(() => store.engineInfo)
const isThinking = computed(() => store.engineThinking)
const moveHistory = computed(() => store.history.map(h => h.notation || ''))
const currentMoveIndex = computed(() => store.currentMoveIndex)
const canUndo = computed(() => store.history.length > 0)

// 当前回合
const currentTurn = computed(() => store.currentTurn)

// Methods
function handleNewGame() {
  store.resetGame()
}

function flipBoard() {
  isFlipped.value = !isFlipped.value
}

function handleUndo() {
  store.undoMove()
}

function handleReset() {
  store.resetGame()
}

function goToStart() {
  store.goToStart?.()
}

function goToPrev() {
  store.goToPrev?.()
}

function goToNext() {
  store.goToNext?.()
}

function goToEnd() {
  store.goToEnd?.()
}

function onMove(move: any) {
  console.log('Move:', move)
}

// AI 控制
function toggleRedAI() {
  redAI.value = !redAI.value
  console.log('Red AI:', redAI.value)
}

function toggleBlackAI() {
  blackAI.value = !blackAI.value
  console.log('Black AI:', blackAI.value)
}

// 监听 AI 状态，自动走棋
watch([redAI, blackAI, currentTurn, isThinking], async ([red, black, turn, thinking]) => {
  if (thinking) return // 引擎正在思考，等待
  
  // 检查是否需要 AI 走棋
  const needAI = (turn === 'red' && red) || (turn === 'black' && black)
  
  if (needAI && !store.gameOver) {
    console.log('[AI] Auto move for', turn)
    // 延迟一小段时间再走，让界面有时间更新
    setTimeout(() => {
      store.makeAIMove()
    }, 300)
  }
}, { immediate: true })

onMounted(() => {
  console.log('App mounted')
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  background: #2d1810;
  min-height: 100vh;
  overflow: hidden;
}

#app {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px;
  overflow: hidden;
  background: linear-gradient(135deg, #3d2914 0%, #2d1810 100%);
}

.board-wrapper {
  transition: transform 0.3s ease;
}

.board-wrapper.flipped {
  transform: rotate(180deg);
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: #3d2914;
  border: 2px solid #8B4513;
  border-radius: 12px;
  padding: 20px;
  min-width: 280px;
  max-width: 90%;
}

.modal-content h3 {
  color: #f5deb3;
  margin-bottom: 16px;
  text-align: center;
}

.modal-content button {
  width: 100%;
  padding: 12px;
  margin-bottom: 8px;
  border: none;
  border-radius: 8px;
  background: #8B4513;
  color: #f5deb3;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.modal-content button:hover {
  background: #a0522d;
}

.modal-content button:last-child {
  margin-bottom: 0;
  background: #5d4037;
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  color: #f5deb3;
}

.setting-item label {
  min-width: 80px;
}

.setting-item input[type="range"] {
  flex: 1;
}

.setting-item span {
  min-width: 30px;
  text-align: right;
}
</style>
