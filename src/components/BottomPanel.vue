<template>
  <div class="bottom-panel" :class="{ collapsed: isCollapsed }">
    <!-- 折叠/展开按钮 -->
    <div class="panel-toggle" @click="isCollapsed = !isCollapsed">
      <svg v-if="isCollapsed" viewBox="0 0 24 24" width="20" height="20">
        <path fill="currentColor" d="M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z"/>
      </svg>
      <svg v-else viewBox="0 0 24 24" width="20" height="20">
        <path fill="currentColor" d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z"/>
      </svg>
    </div>
    
    <!-- 面板内容 -->
    <div class="panel-content" v-show="!isCollapsed">
      <!-- 标签页 -->
      <div class="tabs">
        <button 
          v-for="tab in tabs" 
          :key="tab.id"
          class="tab-btn"
          :class="{ active: activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>
      
      <!-- 引擎分析 -->
      <div v-if="activeTab === 'engine'" class="tab-content">
        <div class="engine-info">
          <div class="info-row">
            <span class="label">深度:</span>
            <span class="value">{{ engineInfo?.depth || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="label">分数:</span>
            <span class="value" :class="scoreClass">{{ formatScore }}</span>
          </div>
          <div class="info-row full-width">
            <span class="label">最佳走法:</span>
            <span class="value pv">{{ bestMove }}</span>
          </div>
        </div>
        <div v-if="isThinking" class="thinking">
          <span class="spinner"></span>
          <span>思考中...</span>
        </div>
      </div>
      
      <!-- 走法记录 -->
      <div v-if="activeTab === 'moves'" class="tab-content moves-list">
        <div class="moves-grid">
          <template v-for="(round, roundIndex) in rounds" :key="roundIndex">
            <div class="round-row">
              <span class="round-num">{{ roundIndex + 1 }}.</span>
              <span class="red-move" :class="{ current: round.redIndex === currentMoveIndex }">{{ round.red || "..." }}</span>
              <span class="black-move" :class="{ current: round.blackIndex === currentMoveIndex }">{{ round.black || "..." }}</span>
            </div>
          </template>
        </div>
        <div v-if="moveHistory.length === 0" class="empty-hint">
          暂无走法记录
        </div>
      </div>
      
      <!-- 导航控制 -->
      <div v-if="activeTab === 'nav'" class="tab-content nav-controls">
        <button class="nav-btn" @click="$emit('goStart')" :disabled="currentMoveIndex <= 0">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
          </svg>
        </button>
        <button class="nav-btn" @click="$emit('goPrev')" :disabled="currentMoveIndex <= 0">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>
          </svg>
        </button>
        <button class="nav-btn" @click="$emit('goNext')" :disabled="currentMoveIndex >= moveHistory.length">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>
          </svg>
        </button>
        <button class="nav-btn" @click="$emit('goEnd')" :disabled="currentMoveIndex >= moveHistory.length">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
          </svg>
        </button>
      </div>
    </div>
    
    <!-- 底部快捷操作栏 -->
    <div class="quick-actions">
      <button class="action-btn" @click="$emit('undo')" :disabled="!canUndo">
        <svg viewBox="0 0 24 24" width="18" height="18">
          <path fill="currentColor" d="M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z"/>
        </svg>
        <span>悔棋</span>
      </button>
      <button class="action-btn" @click="$emit('reset')">
        <svg viewBox="0 0 24 24" width="18" height="18">
          <path fill="currentColor" d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
        </svg>
        <span>重开</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  engineInfo?: {
    depth?: number
    score?: number
    scoreType?: 'cp' | 'mate'
    pv?: string[]
  }
  isThinking?: boolean
  moveHistory: string[]
  currentMoveIndex: number
  canUndo: boolean
}>()

defineEmits<{
  undo: []
  reset: []
  goStart: []
  goPrev: []
  goNext: []
  goEnd: []
}>()

const isCollapsed = ref(false)
const activeTab = ref('engine')

const tabs = [
  { id: 'engine', label: '引擎' },
  { id: 'moves', label: '招法' },
  { id: 'nav', label: '导航' }
]

const formatScore = computed(() => {
  if (!props.engineInfo?.score) return '-'
  const { score, scoreType } = props.engineInfo
  if (scoreType === 'mate') {
    return score > 0 ? `M${score}` : `-M${Math.abs(score)}`
  }
  return score > 0 ? `+${score}` : String(score)
})

const scoreClass = computed(() => {
  if (!props.engineInfo?.score) return ''
  const score = props.engineInfo.score
  if (props.engineInfo.scoreType === 'mate') {
    return score > 0 ? 'winning' : 'losing'
  }
  if (score > 50) return 'winning'
  if (score < -50) return 'losing'
  return ''
})

const bestMove = computed(() => {
  if (!props.engineInfo?.pv?.length) return '-'
  return props.engineInfo.pv.slice(0, 4).join(' ')
})

// 将走法列表转换为回合格式
const rounds = computed(() => {
  const result = []
  for (let i = 0; i < props.moveHistory.length; i += 2) {
    result.push({
      red: props.moveHistory[i],
      redIndex: i,
      black: props.moveHistory[i + 1] || null,
      blackIndex: i + 1
    })
  }
  return result
})
</script>

<style scoped>
.bottom-panel {
  background: linear-gradient(180deg, #654321 0%, #3d2914 100%);
  border-top: 2px solid #8B4513;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.3);
}

.panel-toggle {
  display: flex;
  justify-content: center;
  padding: 4px;
  color: #f5deb3;
  cursor: pointer;
  background: rgba(0, 0, 0, 0.2);
}

.panel-toggle:hover {
  background: rgba(255, 255, 255, 0.1);
}

.panel-content {
  padding: 8px 12px;
}

.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}

.tab-btn {
  flex: 1;
  padding: 8px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.1);
  color: #f5deb3;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background: #8B4513;
  color: white;
}

.tab-content {
  min-height: 80px;
  max-height: 150px;
  overflow-y: auto;
}

.engine-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 4px;
}

.info-row.full-width {
  grid-column: span 2;
}

.info-row .label {
  color: #aaa;
  font-size: 12px;
}

.info-row .value {
  color: #f5deb3;
  font-weight: bold;
}

.info-row .value.winning {
  color: #4CAF50;
}

.info-row .value.losing {
  color: #f44336;
}

.info-row .value.pv {
  font-size: 12px;
  word-break: break-all;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  color: #f5deb3;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #f5deb3;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.moves-list {
  max-height: 200px;
  overflow-y: auto;
}

.moves-grid {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.round-row {
  display: grid;
  grid-template-columns: 30px 1fr 1fr;
  gap: 8px;
  padding: 6px 8px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 4px;
  font-size: 14px;
}

.round-num {
  color: #888;
  font-size: 12px;
}

.red-move, .black-move {
  color: #f5deb3;
  padding: 2px 6px;
  border-radius: 3px;
}

.red-move {
  color: #ef5350;
}

.black-move {
  color: #bdbdbd;
}

.red-move.current, .black-move.current {
  background: #8B4513;
  color: white;
}

.empty-hint {
  color: #888;
  text-align: center;
  padding: 20px;
}

.nav-controls {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 16px;
}

.nav-btn {
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #f5deb3;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.nav-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
}

.nav-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.quick-actions {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.3);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #f5deb3;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
}

.action-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
</style>
