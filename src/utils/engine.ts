// Pikafish 原生引擎封装（通过 Capacitor 插件）

import { Capacitor } from '@capacitor/core';
import { logger } from './logger';

// 动态导入插件
let PikafishEngine: any = null;

let isReady = false;
let messageCallback: ((msg: string) => void) | null = null;

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
      
      // 过滤调试消息
      if (line.startsWith('[DEBUG]')) {
        logger.info('[Engine]', line);
        return;
      }
      
      // 引擎输出
      if (messageCallback) {
        messageCallback(line);
      }
    });

    // 初始化引擎
    logger.info('[Engine] Calling init...');
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

// 发送命令到引擎
export function sendCommand(cmd: string): void {
  if (!isReady || !PikafishEngine) {
    logger.warn('[Engine] Engine not ready, ignoring command:', cmd);
    return;
  }
  
  logger.info('[Engine] Sending command:', cmd);
  PikafishEngine.sendCommand({ command: cmd }).catch((err: Error) => {
    logger.error('[Engine] Send error:', err);
  });
}

// 设置位置 (FEN 格式)
export function setPosition(fen: string): void {
  sendCommand(`position fen ${fen}`);
}

// 开始分析
export function startAnalysis(depth: number = 20): void {
  sendCommand(`go depth ${depth}`);
}

// 停止分析
export function stopAnalysis(): void {
  sendCommand('stop');
}

// 设置哈希大小 (MB)
export function setHashSize(mb: number): void {
  sendCommand(`setoption name Hash value ${mb}`);
}

// 设置线程数
export function setThreads(n: number): void {
  sendCommand(`setoption name Threads value ${n}`);
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
export function destroyEngine(): void {
  if (PikafishEngine) {
    sendCommand('quit');
    PikafishEngine.quit().catch(() => {});
    isReady = false;
  }
}
