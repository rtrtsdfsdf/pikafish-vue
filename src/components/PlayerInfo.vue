<template>
  <div class="player-info" :class="[side, { active: isActive, flipped: flipped }]">
    <div class="player-avatar" :class="side">
      <span v-if="isAI">🤖</span>
      <span v-else>👤</span>
    </div>
    <div class="player-details">
      <div class="player-name">
        <span class="turn-indicator" v-if="isActive">▶</span>
        {{ side === 'red' ? '红方' : '黑方' }}
        <span class="player-type">({{ isAI ? 'AI' : '玩家' }})</span>
      </div>
      <div class="player-status">
        <span v-if="isActive" class="status-active">轮到走棋</span>
        <span v-else-if="isThinking" class="status-thinking">思考中...</span>
      </div>
    </div>
    <div v-if="isThinking && isActive" class="thinking-indicator">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  side: 'red' | 'black'
  isAI: boolean
  isActive: boolean
  isThinking?: boolean
  flipped?: boolean
}>()
</script>

<style scoped>
.player-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: linear-gradient(90deg, rgba(0,0,0,0.3) 0%, rgba(0,0,0,0.1) 50%, rgba(0,0,0,0.3) 100%);
  border-radius: 8px;
  transition: all 0.3s ease;
  min-width: 200px;
}

.player-info.flipped {
  transform: rotate(180deg);
}

.player-info.active {
  background: linear-gradient(90deg, rgba(139, 69, 19, 0.6) 0%, rgba(139, 69, 19, 0.4) 50%, rgba(139, 69, 19, 0.6) 100%);
  box-shadow: 0 0 15px rgba(255, 193, 7, 0.3);
  border: 2px solid rgba(255, 193, 7, 0.5);
}

.player-info.active .player-avatar {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.player-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  border: 2px solid;
  transition: all 0.3s ease;
}

.player-avatar.red {
  background: rgba(198, 40, 40, 0.4);
  border-color: #c62828;
}

.player-avatar.black {
  background: rgba(66, 66, 66, 0.6);
  border-color: #616161;
}

.player-info.active .player-avatar.red {
  border-color: #ffc107;
  box-shadow: 0 0 10px rgba(255, 193, 7, 0.5);
}

.player-info.active .player-avatar.black {
  border-color: #ffc107;
  box-shadow: 0 0 10px rgba(255, 193, 7, 0.5);
}

.player-details {
  flex: 1;
}

.player-name {
  color: #f5deb3;
  font-size: 16px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
}

.turn-indicator {
  color: #ffc107;
  font-size: 14px;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.player-type {
  font-size: 12px;
  color: #aaa;
  font-weight: normal;
}

.player-status {
  margin-top: 2px;
  font-size: 13px;
}

.status-active {
  color: #ffc107;
  font-weight: bold;
}

.status-thinking {
  color: #4caf50;
}

.thinking-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
}

.thinking-indicator .dot {
  width: 6px;
  height: 6px;
  background: #ffc107;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.thinking-indicator .dot:nth-child(1) { animation-delay: -0.32s; }
.thinking-indicator .dot:nth-child(2) { animation-delay: -0.16s; }
.thinking-indicator .dot:nth-child(3) { animation-delay: 0s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
