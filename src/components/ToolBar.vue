<template>
  <div class="toolbar">
    <!-- 左侧：菜单和新局 -->
    <div class="toolbar-left">
      <button class="toolbar-btn" @click="$emit('menu')" title="菜单">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path fill="currentColor" d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/>
        </svg>
      </button>
      <button class="toolbar-btn" @click="$emit('newGame')" title="新对局">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
        </svg>
      </button>
    </div>
    
    <!-- 中间：双方信息 + 回合指示 -->
    <div class="toolbar-center">
      <!-- 黑方 -->
      <div 
        class="player-badge black"
        :class="{ active: currentTurn === 'black', ai: blackAI }"
        @click="$emit('toggleBlackAI')"
      >
        <span class="badge-icon">{{ blackAI ? '🤖' : '👤' }}</span>
        <span class="badge-text">黑</span>
        <span v-if="currentTurn === 'black'" class="turn-arrow">◀</span>
      </div>
      
      <!-- VS -->
      <div class="vs-divider">
        <span>VS</span>
      </div>
      
      <!-- 红方 -->
      <div 
        class="player-badge red"
        :class="{ active: currentTurn === 'red', ai: redAI }"
        @click="$emit('toggleRedAI')"
      >
        <span v-if="currentTurn === 'red'" class="turn-arrow">▶</span>
        <span class="badge-icon">{{ redAI ? '🤖' : '👤' }}</span>
        <span class="badge-text">红</span>
      </div>
    </div>
    
    <!-- 右侧：翻转和设置 -->
    <div class="toolbar-right">
      <button class="toolbar-btn" @click="$emit('flipBoard')" title="翻转棋盘">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path fill="currentColor" d="M7.34 6.41L.86 12.9l6.49 6.48 6.49-6.48-6.5-6.49zM3.69 12.9l3.66-3.66L11 12.9l-3.66 3.66-3.65-3.66zm15.67-6.26C18.48 5.86 17.5 5 16.25 5c-1.25 0-2.23.86-3.11 1.66l-.87.87.87.87c.88.8 1.86 1.66 3.11 1.66s2.23-.86 3.11-1.66l.87-.87-.87-.87zM16.25 8c-.48 0-.86-.36-1.36-.86.5-.5.88-.86 1.36-.86s.86.36 1.36.86c-.5.5-.88.86-1.36.86z"/>
        </svg>
      </button>
      <button class="toolbar-btn" @click="$emit('settings')" title="设置">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path fill="currentColor" d="M19.14 12.94c.04-.31.06-.63.06-.94 0-.31-.02-.63-.06-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  redAI: boolean
  blackAI: boolean
  currentTurn: 'red' | 'black'
}>()

defineEmits<{
  menu: []
  newGame: []
  hint: []
  toggleRedAI: []
  toggleBlackAI: []
  flipBoard: []
  settings: []
}>()
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: linear-gradient(180deg, #8B4513 0%, #654321 100%);
  border-bottom: 2px solid #3d2914;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  gap: 4px;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 8px;
}

.player-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: all 0.2s ease;
  border: 2px solid transparent;
}

.player-badge:hover {
  background: rgba(255, 255, 255, 0.2);
}

.player-badge.black {
  color: #bdbdbd;
}

.player-badge.red {
  color: #ef5350;
}

.player-badge.active {
  background: rgba(255, 193, 7, 0.2);
  border-color: #ffc107;
  box-shadow: 0 0 8px rgba(255, 193, 7, 0.4);
}

.player-badge.ai {
  opacity: 1;
}

.player-badge:not(.ai) {
  opacity: 0.8;
}

.badge-icon {
  font-size: 16px;
}

.badge-text {
  font-size: 14px;
  font-weight: bold;
}

.turn-arrow {
  color: #ffc107;
  font-size: 12px;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.vs-divider {
  color: #888;
  font-size: 12px;
  font-weight: bold;
  padding: 0 4px;
}

.toolbar-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  color: #f5deb3;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.toolbar-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.05);
}

.toolbar-btn:active {
  transform: scale(0.95);
}

.toolbar-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.hint-btn:not(:disabled) {
  background: rgba(255, 193, 7, 0.2);
}
</style>
