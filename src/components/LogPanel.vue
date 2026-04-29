<template>
  <div class="log-panel" v-if="visible">
    <div class="log-header">
      <span class="log-title">📋 调试日志</span>
      <div class="log-actions">
        <button class="log-btn" @click="copyLogs" :title="'复制日志'">
          {{ copied ? '✓ 已复制' : '复制' }}
        </button>
        <button class="log-btn" @click="clearLogs" title="清空日志">清空</button>
        <button class="log-btn close-btn" @click="$emit('close')" title="关闭">✕</button>
      </div>
    </div>
    <div class="log-content" ref="logContainer">
      <div 
        v-for="(log, index) in logs" 
        :key="index"
        class="log-entry"
        :class="log.level"
      >
        <span class="log-time">{{ log.time }}</span>
        <span class="log-level">[{{ log.level.toUpperCase() }}]</span>
        <span class="log-message">{{ log.message }}</span>
      </div>
      <div v-if="logs.length === 0" class="log-empty">
        暂无日志
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { logger } from '@/utils/logger';

defineProps<{
  visible: boolean;
}>();

defineEmits<{
  close: [];
}>();

const logs = ref(logger.getLogs());
const logContainer = ref<HTMLElement | null>(null);
const copied = ref(false);

let unsubscribe: (() => void) | null = null;

onMounted(() => {
  unsubscribe = logger.subscribe((newLogs) => {
    logs.value = newLogs;
    // 自动滚动到底部
    if (logContainer.value) {
      requestAnimationFrame(() => {
        logContainer.value!.scrollTop = logContainer.value!.scrollHeight;
      });
    }
  });
});

onUnmounted(() => {
  if (unsubscribe) {
    unsubscribe();
  }
});

function copyLogs() {
  const text = logger.getText();
  
  // 尝试使用 Clipboard API
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(() => {
      copied.value = true;
      setTimeout(() => {
        copied.value = false;
      }, 2000);
    }).catch(err => {
      console.error('Copy failed:', err);
      fallbackCopy(text);
    });
  } else {
    fallbackCopy(text);
  }
}

function fallbackCopy(text: string) {
  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    const success = document.execCommand('copy');
    document.body.removeChild(textarea);
    
    if (success) {
      copied.value = true;
      setTimeout(() => {
        copied.value = false;
      }, 2000);
    } else {
      alert('复制失败，请手动选择复制');
    }
  } catch (err) {
    console.error('Fallback copy failed:', err);
    alert('复制失败，请手动选择复制');
  }
}

function clearLogs() {
  logger.clearLogs();
}
</script>

<style scoped>
.log-panel {
  position: fixed;
  bottom: 80px;
  left: 10px;
  right: 10px;
  max-height: 50vh;
  background: rgba(20, 20, 20, 0.95);
  border: 1px solid #444;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  z-index: 1000;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #333;
  border-bottom: 1px solid #444;
  border-radius: 8px 8px 0 0;
}

.log-title {
  color: #fff;
  font-weight: bold;
}

.log-actions {
  display: flex;
  gap: 8px;
}

.log-btn {
  padding: 4px 12px;
  background: #555;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.log-btn:hover {
  background: #666;
}

.close-btn {
  background: #c44;
}

.close-btn:hover {
  background: #d55;
}

.log-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  max-height: calc(50vh - 50px);
}

.log-entry {
  padding: 2px 0;
  word-break: break-all;
  line-height: 1.4;
}

.log-time {
  color: #888;
  margin-right: 8px;
}

.log-level {
  margin-right: 8px;
}

.log-message {
  color: #ddd;
}

.log-entry.info .log-level {
  color: #4fc3f7;
}

.log-entry.warn .log-level {
  color: #ffb74d;
}

.log-entry.warn .log-message {
  color: #ffb74d;
}

.log-entry.error .log-level {
  color: #ef5350;
}

.log-entry.error .log-message {
  color: #ef5350;
}

.log-entry.debug .log-level {
  color: #81c784;
}

.log-empty {
  color: #666;
  text-align: center;
  padding: 20px;
}
</style>
