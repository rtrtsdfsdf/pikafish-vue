// Pikafish 引擎封装 - 使用 Capacitor 原生插件

import { Capacitor } from '@capacitor/core';
import PikafishEngine from '../plugins/PikafishEngine';
import type { PluginListenerHandle } from '@capacitor/core';

export interface EngineMessage {
  type: 'info' | 'bestmove' | 'uciok' | 'readyok' | 'id' | 'option' | 'error' | 'other';
  raw: string;
  data?: Record<string, unknown>;
}

export type MessageCallback = (msg: EngineMessage) => void;

let isInitialized = false;
let isReady = false;
let messageCallback: MessageCallback | null = null;
let listenerHandle: PluginListenerHandle | null = null;
let pendingReadyResolve: (() => void) | null = null;

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
      const msg = parseEngineMessage(data.message);
      
      // 处理特殊消息
      if (msg.type === 'readyok' && pendingReadyResolve) {
        pendingReadyResolve();
        pendingReadyResolve = null;
      }
      
      if (messageCallback) {
        messageCallback(msg);
      }
    });
    
    // 初始化引擎
    const result = await PikafishEngine.init();
    
    if (!result.success) {
      console.error('[Engine] Init failed:', result.error);
      return false;
    }
    
    isInitialized = true;
    console.log('[Engine] Initialized successfully');
    
    // 发送 UCI 命令并等待就绪
    await sendCommand('uci');
    
    // 等待 isready 返回 readyok
    await waitForReady();
    
    return true;
  } catch (error) {
    console.error('[Engine] Init error:', error);
    return false;
  }
}

/**
 * 等待引擎就绪
 */
async function waitForReady(): Promise<void> {
  return new Promise((resolve) => {
    pendingReadyResolve = resolve;
    sendCommand('isready');
    
    // 超时保护
    setTimeout(() => {
      if (pendingReadyResolve) {
        console.warn('[Engine] Ready timeout, continuing anyway');
        pendingReadyResolve = null;
        resolve();
      }
    }, 5000);
  });
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
  console.log('[Engine] Analyzing position:', fen);
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
      try {
        await listenerHandle.remove();
      } catch (e) {
        console.warn('[Engine] Failed to remove listener:', e);
      }
      listenerHandle = null;
    }
    
    isInitialized = false;
    isReady = false;
  }
}

/**
 * 解析引擎消息
 */
function parseEngineMessage(raw: string): EngineMessage {
  const trimmed = raw.trim();
  
  // UCI 协议消息解析
  if (trimmed.startsWith('id ')) {
    return { type: 'id', raw: trimmed };
  }
  
  if (trimmed.startsWith('option ')) {
    return { type: 'option', raw: trimmed };
  }
  
  if (trimmed === 'uciok') {
    return { type: 'uciok', raw: trimmed };
  }
  
  if (trimmed === 'readyok') {
    return { type: 'readyok', raw: trimmed };
  }
  
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
        ponder: parts.length > 3 ? parts[3] : undefined
      }
    };
  }
  
  // 错误消息
  if (trimmed.toLowerCase().includes('error')) {
    return { type: 'error', raw: trimmed };
  }
  
  return { type: 'other', raw: trimmed };
}

/**
 * 解析 info 行
 * 示例：info depth 20 seldepth 24 multipv 1 score cp 72 nodes 123456 nps 1234567 time 1000 pv h2e2 h9g7
 */
function parseInfo(info: string): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  
  // 解析 depth
  const depthMatch = info.match(/depth\s+(\d+)/);
  if (depthMatch) result.depth = parseInt(depthMatch[1]);
  
  // 解析 seldepth
  const seldepthMatch = info.match(/seldepth\s+(\d+)/);
  if (seldepthMatch) result.seldepth = parseInt(seldepthMatch[1]);
  
  // 解析 multipv
  const multipvMatch = info.match(/multipv\s+(\d+)/);
  if (multipvMatch) result.multipv = parseInt(multipvMatch[1]);
  
  // 解析 score (cp 或 mate)
  const scoreMatch = info.match(/score\s+(cp|mate)\s+(-?\d+)/);
  if (scoreMatch) {
    result.scoreType = scoreMatch[1];
    result.score = parseInt(scoreMatch[2]);
  }
  
  // 解析 nodes
  const nodesMatch = info.match(/nodes\s+(\d+)/);
  if (nodesMatch) result.nodes = parseInt(nodesMatch[1]);
  
  // 解析 nps
  const npsMatch = info.match(/nps\s+(\d+)/);
  if (npsMatch) result.nps = parseInt(npsMatch[1]);
  
  // 解析 time
  const timeMatch = info.match(/time\s+(\d+)/);
  if (timeMatch) result.time = parseInt(timeMatch[1]);
  
  // 解析 pv (主要变例) - 注意：pv 是最后一个字段，后面全是走法
  const pvMatch = info.match(/\spv\s+(.+)$/);
  if (pvMatch) result.pv = pvMatch[1].trim().split(/\s+/);
  
  // 解析 hashfull
  const hashMatch = info.match(/hashfull\s+(\d+)/);
  if (hashMatch) result.hashfull = parseInt(hashMatch[1]);
  
  // 解析 tbhits
  const tbMatch = info.match(/tbhits\s+(\d+)/);
  if (tbMatch) result.tbhits = parseInt(tbMatch[1]);
  
  return result;
}

/**
 * 检查是否在原生平台
 */
export function isNativePlatform(): boolean {
  return Capacitor.isNativePlatform();
}

/**
 * 检查引擎是否就绪
 */
export function isEngineReady(): boolean {
  return isInitialized && isReady;
}
