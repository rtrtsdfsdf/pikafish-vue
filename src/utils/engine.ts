// Pikafish 原生引擎封装（通过 Capacitor 插件）

import { Capacitor } from '@capacitor/core';
import { logger } from './logger';

// 动态导入插件
let PikafishEngine: any = null;

let isReady = false;
let messageCallback: ((msg: string) => void) | null = null;
let engineVersion: string = '';

// 初始化引擎
export async function initEngine(onMessage: (msg: string) => void): Promise<void> {
  if (isReady) {
    logger.info('[Engine] Already initialized');
    return;
  }

  messageCallback = onMessage;
  logger.info('[Engine] Starting initialization...');

  try {
    // 动态导入插件
    const module = await import('../plugins/PikafishEngine');
    PikafishEngine = module.PikafishEngine;
    
    // 注册消息监听
    PikafishEngine.addListener('engineMessage', (data: { message: string }) => {
      const line = data.message;
      if (line.startsWith('[DEBUG]')) {
        logger.info('[Engine]', line);
        return;
      }
      if (messageCallback) {
        messageCallback(line);
      }
    });

    // 初始化引擎
    const result = await PikafishEngine.init();
    
    if (result.success) {
      isReady = true;
      logger.info('[Engine] Initialization complete');
    } else {
      logger.error('[Engine] Init failed:', result.error);
      throw new Error(result.error || 'Init failed');
    }
  } catch (err) {
    logger.error('[Engine] Init error:', err);
    throw err;
  }
}

// 发送命令到引擎（返回 Promise，让调用方能 await）
export async function sendCommand(cmd: string): Promise<void> {
  if (!PikafishEngine || !isReady) {
    const err = new Error(`Engine not ready (isReady=${isReady}, hasPlugin=${!!PikafishEngine})`);
    logger.warn('[Engine]', err.message, 'ignoring command:', cmd);
    throw err;
  }
  
  logger.info('[Engine] Sending command:', cmd);
  try {
    await PikafishEngine.sendCommand({ command: cmd });
  } catch (err: any) {
    logger.error('[Engine] Send error:', err?.message || err);
    // Stream closed — 引擎进程挂了，标记为未就绪，后续调用会发现并重试
    if (err?.message?.includes('Stream closed') || err?.message?.includes('stream closed')) {
      isReady = false;
      logger.error('[Engine] Engine stream closed, will need re-init');
    }
    throw err;
  }
}

// 设置位置 (FEN 格式)
export function setPosition(fen: string): void {
  sendCommand(`position fen ${fen}`).catch(() => {});
}

// 开始分析
export function startAnalysis(depth: number = 20): void {
  sendCommand(`go depth ${depth}`).catch(() => {});
}

// 停止分析
export function stopAnalysis(): void {
  sendCommand('stop').catch(() => {});
}

// 设置哈希大小 (MB)
export function setHashSize(mb: number): void {
  sendCommand(`setoption name Hash value ${mb}`).catch(() => {});
}

// 设置线程数
export function setThreads(n: number): void {
  sendCommand(`setoption name Threads value ${n}`).catch(() => {});
}

// 发送 UCI 命令
export function sendUciCommand(): void {
  sendCommand('uci').catch(() => {});
}

// 发送 isready 命令（用于检查引擎是否就绪）
export function sendIsReady(): void {
  sendCommand('isready').catch(() => {});
}

// 检查引擎是否就绪
export function isEngineReady(): boolean {
  return isReady;
}

// 获取引擎版本
export function getEngineVersion(): string {
  return engineVersion;
}

// 解析引擎输出
export function parseEngineLine(line: string): {
  type: 'info' | 'bestmove' | 'uciok' | 'readyok' | 'other';
  data?: any;
} {
  if (line.startsWith('info')) {
    const info = parseInfoLine(line);
    return { type: 'info', data: info };
  } else if (line.startsWith('bestmove')) {
    const match = line.match(/bestmove\s+(\w+)/);
    if (match) {
      return { type: 'bestmove', data: match[1] };
    }
  } else if (line === 'uciok') {
    return { type: 'uciok' };
  } else if (line === 'readyok') {
    return { type: 'readyok' };
  }
  return { type: 'other' };
}

function parseInfoLine(line: string): any {
  const result: any = {};
  
  const depthMatch = line.match(/depth\s+(\d+)/);
  if (depthMatch) result.depth = parseInt(depthMatch[1] || '0');
  
  const scoreMatch = line.match(/score\s+(cp|mate)\s+(-?\d+)/);
  if (scoreMatch) {
    result.scoreType = scoreMatch[1];
    result.score = parseInt(scoreMatch[2] || '0');
  }
  
  const nodesMatch = line.match(/nodes\s+(\d+)/);
  if (nodesMatch) result.nodes = parseInt(nodesMatch[1] || '0');
  
  const npsMatch = line.match(/nps\s+(\d+)/);
  if (npsMatch) result.nps = parseInt(npsMatch[1] || '0');
  
  const timeMatch = line.match(/time\s+(\d+)/);
  if (timeMatch) result.time = parseInt(timeMatch[1] || '0');
  
  const pvMatch = line.match(/\spv\s+(.+)/);
  if (pvMatch) {
    result.pv = (pvMatch[1] || '').trim().split(/\s+/);
  }
  
  return result;
}

// 销毁引擎
export async function destroyEngine(): Promise<void> {
  if (PikafishEngine) {
    try {
      await sendCommand('quit');
    } catch {
      // 引擎已死，忽略
    }
    try {
      await PikafishEngine.quit();
    } catch {
      // 忽略
    }
    isReady = false;
    engineVersion = '';
  }
}
