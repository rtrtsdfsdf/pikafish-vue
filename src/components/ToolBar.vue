<template>
  <div class="toolbar">
    <div class="toolbar-left">
      <button class="toolbar-btn" @click="$emit('menu')" title="菜单">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <path fill="currentColor" d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/>
        </svg>
      </button>
      <button class="toolbar-btn" @click="$emit('newGame')" title="新对局">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
        </svg>
      </button>
    </div>
    
    <div class="toolbar-center">
      <!-- AI 控制按钮 -->
      <div class="ai-controls">
        <button 
          class="ai-btn red" 
          :class="{ active: redAI }"
          @click="$emit('toggleRedAI')"
          title="红方AI"
        >
          <span class="dot red-dot"></span>
          <span>红方</span>
        </button>
        <button 
          class="ai-btn black" 
          :class="{ active: blackAI }"
          @click="$emit('toggleBlackAI')"
          title="黑方AI"
        >
          <span class="dot black-dot"></span>
          <span>黑方</span>
        </button>
      </div>
    </div>
    
    <div class="toolbar-right">
      <button class="toolbar-btn" @click="$emit('flipBoard')" title="翻转棋盘">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <path fill="currentColor" d="M7.34 6.41L.86 12.9l6.49 6.48 6.49-6.48-6.5-6.49zM3.69 12.9l3.66-3.66L11 12.9l-3.66 3.66-3.65-3.66zm15.67-6.26C18.48 5.86 17.5 5 16.25 5c-1.25 0-2.23.86-3.11 1.66l-.87.87.87.87c.88.8 1.86 1.66 3.11 1.66s2.23-.86 3.11-1.66l.87-.87-.87-.87zM16.25 8c-.48 0-.86-.36-1.36-.86.5-.5.88-.86 1.36-.86s.86.36 1.36.86c-.5.5-.88.86-1.36.86z"/>
        </svg>
      </button>
      <button class="toolbar-btn" @click="$emit('settings')" title="设置">
        <svg viewBox="0 0 24 24" width="22" height="22">
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
}>()

defineEmits<{
  menu: []
  newGame: []
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
  padding: 8px 12px;
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
  flex: 1;
  display: flex;
  justify-content: center;
}

.ai-controls {
  display: flex;
  gap: 8px;
}

.ai-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 2px solid transparent;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.1);
  color: #f5deb3;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ai-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.ai-btn.red.active {
  background: rgba(198, 40, 40, 0.4);
  border-color: #c62828;
  color: #fff;
}

.ai-btn.black.active {
  background: rgba(66, 66, 66, 0.6);
  border-color: #fff;
  color: #fff;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.5);
}

.red-dot {
  background: #c62828;
}

.black-dot {
  background: #212121;
}

.toolbar-btn {
  width: 40px;
  height: 40px;
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
</style>
