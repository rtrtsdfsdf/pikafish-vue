// Pikafish WASM 引擎封装

let engineWorker: Worker | null = null;
let messageCallback: ((msg: string) => void) | null = null;
let isReady = false;

// 初始化引擎
export async function initEngine(onMessage: (msg: string) => void): Promise<void> {
  if (engineWorker) {
    console.log('[Engine] Already initialized');
    return;
  }

  messageCallback = onMessage;
  console.log('[Engine] Starting initialization...');

  // 创建 Worker 来加载 WASM
  const workerCode = `
    let pikafish = null;
    
    console.log('[Worker] Starting...');
    
    // 监听主线程消息
    self.onmessage = async (e) => {
      const { type, data } = e.data;
      console.log('[Worker] Received:', type, data);
      
      if (type === 'init') {
        try {
          // 动态导入 pikafish.js
          importScripts('/engine/pikafish.js');
          console.log('[Worker] pikafish.js loaded');
          
          const PikafishModule = await Pikafish({
            locateFile: (path) => '/engine/' + path
          });
          
          pikafish = PikafishModule;
          console.log('[Worker] Pikafish module ready');
          
          // 设置消息监听
          pikafish.addMessageListener((line) => {
            console.log('[Worker] Engine output:', line);
            self.postMessage({ type: 'message', data: line });
          });
          
          self.postMessage({ type: 'ready' });
        } catch (err) {
          console.error('[Worker] Error:', err);
          self.postMessage({ type: 'error', data: String(err) });
        }
      } else if (type === 'command') {
        if (pikafish) {
          console.log('[Worker] Sending command:', data);
          pikafish.postMessage(data);
        } else {
          console.warn('[Worker] Engine not ready, ignoring command:', data);
        }
      }
    };
  `;
  
  const blob = new Blob([workerCode], { type: 'application/javascript' });
  const workerUrl = URL.createObjectURL(blob);
  
  engineWorker = new Worker(workerUrl);
  console.log('[Engine] Worker created');
  
  return new Promise((resolve, reject) => {
    if (!engineWorker) {
      reject(new Error('Failed to create worker'));
      return;
    }
    
    const timeout = setTimeout(() => {
      reject(new Error('Engine initialization timeout'));
    }, 30000);
    
    engineWorker.onmessage = (e) => {
      const { type, data } = e.data;
      console.log('[Engine] Worker message:', type, data);
      
      if (type === 'ready') {
        clearTimeout(timeout);
        isReady = true;
        // 发送 UCI 初始化命令
        sendCommand('uci');
        setTimeout(() => {
          sendCommand('isready');
          console.log('[Engine] Initialization complete');
          resolve();
        }, 500);
      } else if (type === 'message') {
        if (messageCallback) {
          messageCallback(data);
        }
      } else if (type === 'error') {
        clearTimeout(timeout);
        reject(new Error(data));
      }
    };
    
    engineWorker.onerror = (err) => {
      clearTimeout(timeout);
      console.error('[Engine] Worker error:', err);
      reject(err);
    };
    
    // 初始化引擎
    engineWorker.postMessage({ type: 'init' });
  });
}

// 发送命令到引擎
export function sendCommand(cmd: string): void {
  if (engineWorker && isReady) {
    console.log('[Engine] Sending command:', cmd);
    engineWorker.postMessage({ type: 'command', data: cmd });
  } else if (!isReady) {
    console.warn('[Engine] Engine not ready, queuing command:', cmd);
    // 引擎未就绪时也发送，让队列处理
    if (engineWorker) {
      engineWorker.postMessage({ type: 'command', data: cmd });
    }
  }
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
    result.scoreType = scoreMatch[1]; // 'cp' or 'mate'
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
  if (engineWorker) {
    sendCommand('quit');
    engineWorker.terminate();
    engineWorker = null;
  }
}
