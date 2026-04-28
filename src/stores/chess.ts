import { defineStore } from 'pinia';
import type { GameState, Position, Move, EngineInfo } from '@/types/chess';
import { 
  INITIAL_BOARD, 
  getValidMoves, 
  makeMove, 
  getPieceColor,
  boardToFen,
  moveToString
} from '@/utils/chessLogic';
import { 
  initEngine, 
  sendCommand, 
  analyzePosition, 
  stopAnalysis,
  quitEngine,
  isNativePlatform,
  isEngineReady,
  whenReady,
  type EngineMessage 
} from '@/utils/engine';

// 自动对弈模式
export type AutoPlayMode = 'none' | 'red' | 'black' | 'both';

// 日志条目
export interface LogEntry {
  time: string;
  message: string;
}

export const useChessStore = defineStore('chess', {
  state: (): GameState & { autoPlayMode: AutoPlayMode; logs: LogEntry[] } => ({
    board: INITIAL_BOARD.map(row => [...row]),
    currentTurn: 'red',
    selectedPos: null,
    validMoves: [],
    history: [],
    engineThinking: false,
    engineInfo: null,
    gameOver: false,
    winner: null,
    autoPlayMode: 'none',  // 自动对弈模式
    logs: [],  // 日志数组
    pendingAutoMove: false,  // 是否等待自动执行走法
    engineDepth: 20,  // 引擎分析深度
    hintMove: null as { from: Position, to: Position } | null,  // 提示走法
  }),

  actions: {
    /**
     * 设置自动对弈模式
     */
    setAutoPlayMode(mode: AutoPlayMode) {
      this.autoPlayMode = mode;
      console.log('[Store] Auto play mode:', mode);
      
      // 如果设置了新模式，等待引擎就绪后开始
      if (mode !== 'none' && !this.gameOver) {
        if (isEngineReady()) {
          this.checkAutoPlay();
        } else {
          // 引擎还没准备好，等待就绪
          whenReady(() => {
            console.log('[Store] Engine ready, starting auto play');
            this.checkAutoPlay();
          });
        }
      }
    },

    /**
     * 检查是否需要自动走棋
     */
    checkAutoPlay() {
      if (this.gameOver || this.engineThinking) return;
      
      const shouldEngineMove = 
        (this.autoPlayMode === 'red' && this.currentTurn === 'red') ||
        (this.autoPlayMode === 'black' && this.currentTurn === 'black') ||
        (this.autoPlayMode === 'both');
      
      if (shouldEngineMove) {
        console.log('[Store] Engine should move for', this.currentTurn);
        this.startEngineAnalysis(true);  // true = 自动执行最佳走法
      }
    },

    /**
     * 初始化引擎
     */
    async initGameEngine() {
      if (!isNativePlatform()) {
        console.log('[Store] Not native platform, engine disabled');
        return;
      }

      console.log('[Store] Initializing engine...');
      
      const success = await initEngine((msg: EngineMessage) => {
        this.handleEngineMessage(msg);
      });

      if (success) {
        console.log('[Store] Engine initialized successfully');
        
        // 设置引擎选项
        await sendCommand('setoption name Threads value 2');
        await sendCommand('setoption name Hash value 64');
        
      } else {
        console.error('[Store] Engine initialization failed');
      }
    },

    /**
     * 添加日志
     */
    addLog(message: string) {
      const now = new Date();
      const time = now.toTimeString().substring(0, 8);
      this.logs.unshift({ time, message });  // 新日志在最上面
      
      // 限制日志数量
      if (this.logs.length > 100) {
        this.logs.pop();
      }
    },

    /**
     * 处理引擎消息
     */
    parseUciMove(uci: string): { from: Position, to: Position } | null {
      if (!uci || uci.length < 4) return null;
      const fromCol = uci.charCodeAt(0) - 97;
      const fromRow = 9 - parseInt(uci[1]);
      const toCol = uci.charCodeAt(2) - 97;
      const toRow = 9 - parseInt(uci[3]);
      return {
        from: { row: fromRow, col: fromCol },
        to: { row: toRow, col: toCol }
      };
    }
  handleEngineMessage(msg: EngineMessage) {
      // 打印所有消息到控制台（会被组件拦截并显示）
      console.log('[Store] Engine message:', msg.type, msg.raw);
      
      if (msg.type === 'info') {
        const data = msg.data;
        if (data && typeof data.depth === 'number') {
          // 更新引擎信息
          this.engineInfo = {
            depth: data.depth as number,
            score: data.score as number | undefined,
            scoreType: data.scoreType as 'cp' | 'mate' | undefined,
            nodes: (data.nodes as number) || 0,
            nps: data.nps as number | undefined,
            pv: (data.pv as string[]) || [],
            time: (data.time as number) || 0,
          };
        }
      } else if (msg.type === 'bestmove') {
        this.engineThinking = false;
        if (msg.data?.move) {
          console.log('[Store] Best move:', msg.data.move);
          
          // 如果是自动对弈模式，执行走法
          if (this.pendingAutoMove) {
            this.pendingAutoMove = false;
            this.executeEngineMove(msg.data.move);
          } else {
            // 这是提示走法
            const move = this.parseUciMove(msg.data.move as string);
            if (move) {
              this.hintMove = move;
            }
          }
          return;
        }
      } else if (msg.type === 'uciok') {
        console.log('[Store] UCI OK received');
      } else if (msg.type === 'readyok') {
        console.log('[Store] Engine ready');
        // 引擎就绪后检查是否需要自动走棋
        this.checkAutoPlay();
      }
    },


    /**
     * 开始引擎分析
     */
    async startEngineAnalysis(autoMove: boolean = false) {
      if (!isNativePlatform() || !isEngineReady()) {
        console.log('[Store] Engine not ready, skipping analysis');
        return;
      }

      this.engineThinking = true;
      this.engineInfo = null;
      this.pendingAutoMove = autoMove;
      
      // 生成 FEN
      const fen = boardToFen(this.board, this.currentTurn);
      console.log('[Store] Starting analysis, FEN:', fen, 'autoMove:', autoMove);
      
      await analyzePosition(fen, this.engineDepth);
    },

    /**
     * 获取提示走法
     */
    async getHint() {
      if (this.engineThinking || this.gameOver) return
      
      this.engineThinking = true
      this.hintMove = null
      
      const fen = boardToFen(this.board, this.currentTurn)
      console.log('[Store] Getting hint for FEN:', fen)
      
      await analyzePosition(fen, Math.min(this.engineDepth, 15))
    },

    /**
     * 清除提示
     */
    clearHint() {
      this.hintMove = null
    },

    /**
     * 执行引擎走法
     */
    executeEngineMove(moveStr: string) {
      // 解析走法字符串 (如 "a0a1")
      if (moveStr.length < 4) {
        console.error('[Store] Invalid move string:', moveStr);
        return;
      }

      const fromCol = moveStr.charCodeAt(0) - 'a'.charCodeAt(0);
      const fromRow = 9 - parseInt(moveStr[1]);
      const toCol = moveStr.charCodeAt(2) - 'a'.charCodeAt(0);
      const toRow = 9 - parseInt(moveStr[3]);

      console.log('[Store] Executing engine move:', fromRow, fromCol, '->', toRow, toCol);

      // 验证走法
      const piece = this.board[fromRow][fromCol];
      if (!piece || piece === ' ') {
        console.error('[Store] No piece at source position');
        return;
      }

      // 执行走法
      this.movePiece(
        { row: fromRow, col: fromCol },
        { row: toRow, col: toCol }
      );
    },

    /**
     * 选择棋子
     */
    selectPiece(pos: Position) {
      if (this.gameOver) return;
      
      // 如果是引擎自动走棋的回合，忽略用户点击
      if (
        (this.autoPlayMode === 'red' && this.currentTurn === 'red') ||
        (this.autoPlayMode === 'black' && this.currentTurn === 'black') ||
        (this.autoPlayMode === 'both')
      ) {
        console.log('[Store] Ignoring user input, engine is playing');
        return;
      }
      
      const piece = this.board[pos.row][pos.col];
      
      // 如果已选中棋子，尝试移动
      if (this.selectedPos) {
        const isValidMove = this.validMoves.some(
          m => m.row === pos.row && m.col === pos.col
        );
        
        if (isValidMove) {
          this.movePiece(this.selectedPos, pos);
          return;
        }
      }
      
      // 选择新棋子
      if (piece && piece !== ' ' && getPieceColor(piece) === this.currentTurn) {
        this.selectedPos = pos;
        this.validMoves = getValidMoves(this.board, pos.row, pos.col);
      } else {
        this.selectedPos = null;
        this.validMoves = [];
      }
    },

    /**
     * 移动棋子
     */
    movePiece(from: Position, to: Position) {
      const piece = this.board[from.row][from.col];
      const captured = this.board[to.row][to.col];
      
      // 记录走法
      const move: Move = {
        from,
        to,
        piece,
        captured: captured !== ' ' ? captured : undefined,
        notation: moveToString(this.board, from, to, piece)
      };
      this.history.push(move);
      
      // 执行走法
      this.board = makeMove(this.board, from, to);
      
      // 检查是否吃掉将/帅
      if (captured === 'k') {
        this.gameOver = true;
        this.winner = 'red';
      } else if (captured === 'K') {
        this.gameOver = true;
        this.winner = 'black';
      }
      
      // 切换回合
      this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
      this.selectedPos = null;
      this.validMoves = [];
      
      // 检查是否需要引擎自动走棋
      if (!this.gameOver) {
        if (isNativePlatform() && isEngineReady() && this.autoPlayMode !== 'none') {
          // 延迟一点，让界面有时间更新
          setTimeout(() => {
            this.checkAutoPlay();
          }, 300);
        }
      }
    },

    /**
     * 停止引擎分析
     */
    async stopEngineAnalysis() {
      if (!isNativePlatform()) {
        return;
      }

      await stopAnalysis();
      this.engineThinking = false;
      this.pendingAutoMove = false;
    },

    /**
     * 悔棋
     */
    undoMove() {
      if (this.history.length === 0) return;
      
      // 如果引擎正在思考，先停止
      if (this.engineThinking) {
        this.stopEngineAnalysis();
      }
      
      const lastMove = this.history.pop()!;
      
      // 恢复棋盘
      this.board[lastMove.from.row][lastMove.from.col] = lastMove.piece;
      this.board[lastMove.to.row][lastMove.to.col] = lastMove.captured || ' ';
      
      // 恢复回合
      this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
      this.gameOver = false;
      this.winner = null;
      this.selectedPos = null;
      this.validMoves = [];
      
      // 检查是否需要引擎自动走棋
      if (isNativePlatform() && isEngineReady() && this.autoPlayMode !== 'none') {
        setTimeout(() => { this.checkAutoPlay(); }, 300);
      }
    },

    /**
     * 重新开始
     */
    async resetGame() {
      await stopAnalysis();
      
      this.board = INITIAL_BOARD.map(row => [...row]);
      this.currentTurn = 'red';
      this.selectedPos = null;
      this.validMoves = [];
      this.history = [];
      this.engineThinking = false;
      this.engineInfo = null;
      this.gameOver = false;
      this.winner = null;
      this.pendingAutoMove = false;
      
      // 检查是否需要引擎自动走棋
      if (isNativePlatform() && isEngineReady() && this.autoPlayMode !== 'none') {
        setTimeout(() => {
          this.checkAutoPlay();
        }, 500);
      }
    },

    /**
     * 翻转棋盘
     */
    flipBoard() {
      this.board = this.board.reverse().map(row => [...row].reverse());
      this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
    },

    /**
     * 清理引擎
     */
    async cleanup() {
      await quitEngine();
      }
  }
});
