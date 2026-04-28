<template>
  <div class="player-info" :class="[side, { active: isActive, flipped: flipped }]">
    <div class="player-avatar" :class="side">
      <span v-if="isAI">🤖</span>
      <span v-else>👤</span>
    </div>
    <div class="player-details">
      <div class="player-name">
        {{ side === 'red' ? '红方' : '黑方' }}
        <span class="player-type">({{ isAI ? 'AI' : '玩家' }})</span>
      </div>
      <div class="player-time" v-if="showTimer">
        ⏱ {{ formatTime(time) }}
      </div>
    </div>
    <div v-if="isThinking" class="thinking-indicator">
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
  time?: number
  showTimer?: boolean
  flipped?: boolean
}>()

function formatTime(seconds: number = 0): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}
</script>

<style scoped>
.player-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: linear-gradient(90deg, rgba(0,0,0,0.3) 0%, rgba(0,0,0,0.1) 50%, rgba(0,0,0,0.3) 100%);
  border-radius: 8px;
  transition: all 0.3s ease;
}

.player-info.flipped {
  transform: rotate(180deg);
}

.player-info.active {
  background: linear-gradient(90deg, rgba(139, 69, 19, 0.5) 0%, rgba(139, 69, 19, 0.3) 50%, rgba(139, 69, 19, 0.5) 100%);
  box-shadow: 0 0 10px rgba(139, 69, 19, 0.5);
}

.player-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  border: 2px solid;
}

.player-avatar.red {
  background: rgba(198, 40, 40, 0.3);
  border-color: #c62828;
}

.player-avatar.black {
  background: rgba(66, 66, 66, 0.5);
  border-color: #424242;
}

.player-details {
  flex: 1;
}

.player-name {
  color: #f5deb3;
  font-size: 16px;
  font-weight: bold;
}

.player-type {
  font-size: 12px;
  color: #aaa;
  font-weight: normal;
}

.player-time {
  color: #f5deb3;
  font-size: 14px;
  margin-top: 2px;
}

.thinking-indicator {
  display: flex;
  gap: 4px;
}

.thinking-indicator .dot {
  width: 6px;
  height: 6px;
  background: #f5deb3;
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
