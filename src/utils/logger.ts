// 全局日志系统

interface LogEntry {
  time: string;
  level: 'info' | 'warn' | 'error' | 'debug';
  message: string;
}

class Logger {
  private logs: LogEntry[] = [];
  private maxLogs = 500;
  private listeners: Set<(logs: LogEntry[]) => void> = new Set();

  log(level: LogEntry['level'], ...args: any[]) {
    const now = new Date();
    const time = now.toLocaleTimeString('zh-CN', { hour12: false });
    const message = args.map(a => 
      typeof a === 'object' ? JSON.stringify(a, null, 2) : String(a)
    ).join(' ');
    
    const entry: LogEntry = { time, level, message };
    this.logs.push(entry);
    
    // 限制日志数量
    if (this.logs.length > this.maxLogs) {
      this.logs.shift();
    }
    
    // 通知监听者
    this.notifyListeners();
    
    // 同时输出到控制台
    const prefix = `[${time}] [${level.toUpperCase()}]`;
    switch (level) {
      case 'error':
        console.error(prefix, ...args);
        break;
      case 'warn':
        console.warn(prefix, ...args);
        break;
      default:
        console.log(prefix, ...args);
    }
  }

  info(...args: any[]) {
    this.log('info', ...args);
  }

  warn(...args: any[]) {
    this.log('warn', ...args);
  }

  error(...args: any[]) {
    this.log('error', ...args);
  }

  debug(...args: any[]) {
    this.log('debug', ...args);
  }

  getLogs(): LogEntry[] {
    return [...this.logs];
  }

  clearLogs() {
    this.logs = [];
    this.notifyListeners();
  }

  subscribe(callback: (logs: LogEntry[]) => void) {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  private notifyListeners() {
    this.listeners.forEach(cb => cb([...this.logs]));
  }

  // 获取纯文本格式的日志（用于复制）
  getText(): string {
    return this.logs.map(l => `[${l.time}] [${l.level.toUpperCase()}] ${l.message}`).join('\n');
  }
}

export const logger = new Logger();

// 替换 console.log 等（可选）
export function setupGlobalLogging() {
  // 保存原始方法
  const originalLog = console.log;
  const originalWarn = console.warn;
  const originalError = console.error;

  // 替换
  console.log = (...args) => {
    logger.info(...args);
    originalLog.apply(console, args);
  };

  console.warn = (...args) => {
    logger.warn(...args);
    originalWarn.apply(console, args);
  };

  console.error = (...args) => {
    logger.error(...args);
    originalError.apply(console, args);
  };
}
