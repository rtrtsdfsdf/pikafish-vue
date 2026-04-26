// 简化版引擎 - 暂时不使用 WASM，只做基本的走法验证

import type { EngineInfo } from '@/types/chess';

// 引擎状态
let isAnalyzing = false;
let analysisCallback: ((info: EngineInfo) => void) | null = null;

// 初始化引擎（简化版，不加载 WASM）
export async function initEngine(onMessage: (msg: string) => void): Promise<void> {
  console.log('Engine initialized (simplified mode)');
  // 简化版不需要真正的初始化
  return Promise.resolve();
}

// 发送命令（简化版，不做任何事）
export function sendCommand(cmd: string): void {
  console.log('Engine command:', cmd);
}

// 设置位置
export function setPosition(fen: string): void {
  console.log('Position set:', fen);
}

// 开始分析（简化版，模拟分析结果）
export function startAnalysis(depth: number = 20): void {
  isAnalyzing = true;
  
  // 模拟分析延迟后返回结果
  setTimeout(() => {
    if (isAnalyzing && analysisCallback) {
      analysisCallback({
        depth: depth,
        score: Math.floor(Math.random() * 200 - 100), // 随机分数
        nodes: Math.floor(Math.random() * 100000),
        nps: Math.floor(Math.random() * 10000),
        time: Math.floor(Math.random() * 1000),
        pv: []
      });
    }
    isAnalyzing = false;
  }, 500);
}

// 停止分析
export function stopAnalysis(): void {
  isAnalyzing = false;
}

// 设置分析回调
export function setAnalysisCallback(callback: (info: EngineInfo) => void): void {
  analysisCallback = callback;
}

// 设置哈希大小
export function setHashSize(mb: number): void {
  console.log('Hash size set:', mb);
}

// 设置线程数
export function setThreads(n: number): void {
  console.log('Threads set:', n);
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
    if (scoreMatch[1] === 'cp') {
      result.score = parseInt(scoreMatch[2] || '0');
    } else {
      result.mate = parseInt(scoreMatch[2] || '0');
    }
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
  isAnalyzing = false;
  analysisCallback = null;
}
