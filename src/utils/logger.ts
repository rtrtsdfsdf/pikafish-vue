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
  private originalConsole = {
    log: console.log.bind(console),
    warn: console.warn.bind(console),
    error: console.error.bind(console),
  };

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
    
    // 使用原始 console 输出（避免循环）
    const prefix = `[${time}] [${level.toUpperCase()}]`;
    switch (level) {
      case 'error':
        this.originalConsole.error(prefix, ...args);
        break;
      case 'warn':
        this.originalConsole.warn(prefix, ...args);
        break;
      default:
        this.originalConsole.log(prefix, ...args);
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
