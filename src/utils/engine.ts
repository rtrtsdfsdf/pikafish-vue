// Pikafish 引擎封装 - 使用 Capacitor 原生插件

import { Capacitor } from '@capacitor/core';
import PikafishEngine from '../plugins/PikafishEngine';
import type { PluginListenerHandle } from '@capacitor/core';

export interface EngineMessage {
  type: 'info' | 'bestmove' | 'uciok' | 'readyok' | 'error' | 'other';
  raw: string;
  data?: Record<string, unknown>;
}

export type MessageCallback = (msg: EngineMessage) => void;

let isInitialized = false;
let messageCallback: MessageCallback | null = null;
let listenerHandle: PluginListenerHandle | null = null;

/**
 * 初始化引擎
 */
export async function initEngine(onMessage: MessageCallback): Promise<boolean> {
  if (isInitialized) {
    return true;
  }

  messageCallback = onMessage;

  try {
    // 监听引擎输出事件
    listenerHandle = await PikafishEngine.addListener('engineMessage', (data: { message: string }) => {
      if (messageCallback) {
        const msg = parseEngineMessage(data.message);
        messageCallback(msg);
      }
    });
    
    // 初始化引擎
    const result = await PikafishEngine.init();
    
    if (result.success) {
      isInitialized = true;
      console.log('[Engine] Initialized successfully');
      return true;
    } else {
      console.error('[Engine] Init failed:', result.error);
      return false;
    }
  } catch (error) {
    console.error('[Engine] Init error:', error);
    return false;
  }
}

/**
 * 发送 UCI 命令
 */
export async function sendCommand(command: string): Promise<boolean> {
  if (!isInitialized) {
    console.error('[Engine] Not initialized');
    return false;
  }

  try {
    const result = await PikafishEngine.sendCommand({ command });
    return result.success;
  } catch (error) {
    console.error('[Engine] Send command error:', error);
    return false;
  }
}

/**
 * 开始分析局面
 */
export async function analyzePosition(fen: string, depth: number = 20): Promise<void> {
  await sendCommand('stop');
  await sendCommand(`position fen ${fen}`);
  await sendCommand(`go depth ${depth}`);
}

/**
 * 停止分析
 */
export async function stopAnalysis(): Promise<void> {
  await sendCommand('stop');
}

/**
 * 关闭引擎
 */
export async function quitEngine(): Promise<void> {
  if (isInitialized) {
    await sendCommand('quit');
    await PikafishEngine.quit();
    
    // 移除监听器
    if (listenerHandle) {
      await listenerHandle.remove();
      listenerHandle = null;
    }
    
    isInitialized = false;
  }
}

/**
 * 解析引擎消息
 */
function parseEngineMessage(raw: string): EngineMessage {
  const trimmed = raw.trim();
  
  if (trimmed.startsWith('info')) {
    return {
      type: 'info',
      raw: trimmed,
      data: parseInfo(trimmed)
    };
  }
  
  if (trimmed.startsWith('bestmove')) {
    const parts = trimmed.split(/\s+/);
    return {
      type: 'bestmove',
      raw: trimmed,
      data: {
        move: parts[1],
        ponder: parts[3] // 可选的 ponder 走法
      }
    };
  }
  
  if (trimmed === 'uciok') {
    return { type: 'uciok', raw: trimmed };
  }
  
  if (trimmed === 'readyok') {
    return { type: 'readyok', raw: trimmed };
  }
  
  return { type: 'other', raw: trimmed };
}

/**
 * 解析 info 行
 */
function parseInfo(info: string): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  
  // 解析 depth
  const depthMatch = info.match(/depth\s+(\d+)/);
  if (depthMatch) result.depth = parseInt(depthMatch[1]);
  
  // 解析 score
  const scoreMatch = info.match(/score\s+(cp|mate)\s+(-?\d+)/);
  if (scoreMatch) {
    result.scoreType = scoreMatch[1];
    result.score = parseInt(scoreMatch[2]);
  }
  
  // 解析 nodes
  const nodesMatch = info.match(/nodes\s+(\d+)/);
  if (nodesMatch) result.nodes = parseInt(nodesMatch[1]);
  
  // 解析 time
  const timeMatch = info.match(/time\s+(\d+)/);
  if (timeMatch) result.time = parseInt(timeMatch[1]);
  
  // 解析 pv (主要变例)
  const pvMatch = info.match(/pv\s+(.+)/);
  if (pvMatch) result.pv = pvMatch[1].split(/\s+/);
  
  return result;
}

/**
 * 检查是否在原生平台
 */
export function isNativePlatform(): boolean {
  return Capacitor.isNativePlatform();
}
