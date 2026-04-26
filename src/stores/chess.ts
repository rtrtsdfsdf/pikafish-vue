import { defineStore } from 'pinia';
import type { GameState, Position, Move, EngineInfo } from '@/types/chess';
import { 
  INITIAL_BOARD, 
  getValidMoves, 
  makeMove, 
  getPieceColor,
  boardToFen
} from '@/utils/chessLogic';
import { 
  initEngine, 
  sendCommand, 
  analyzePosition, 
  stopAnalysis,
  quitEngine,
  isNativePlatform,
  isEngineReady,
  type EngineMessage 
} from '@/utils/engine';

export const useChessStore = defineStore('chess', {
  state: (): GameState => ({
    board: INITIAL_BOARD.map(row => [...row]),
    currentTurn: 'red',
    selectedPos: null,
    validMoves: [],
    history: [],
    engineThinking: false,
    engineInfo: null,
    gameOver: false,
    winner: null,
  }),

  actions: {
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
        
        // 开始初始局面分析
        this.startEngineAnalysis();
      } else {
        console.error('[Store] Engine initialization failed');
      }
    },

    /**
     * 处理引擎消息
     */
    handleEngineMessage(msg: EngineMessage) {
      console.log('[Store] Engine message:', msg.type, msg.raw.substring(0, 50));
      
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
          // 可以在这里实现自动走棋功能
        }
      } else if (msg.type === 'uciok') {
        console.log('[Store] UCI OK received');
      } else if (msg.type === 'readyok') {
        console.log('[Store] Engine ready');
      }
    },

    /**
     * 选择棋子
     */
    selectPiece(pos: Position) {
      if (this.gameOver) return;
      
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
        captured: captured !== ' ' ? captured : undefined
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
      
      // 自动开始引擎分析
      if (!this.gameOver && isNativePlatform() && isEngineReady()) {
        this.startEngineAnalysis();
      }
    },

    /**
     * 开始引擎分析
     */
    async startEngineAnalysis() {
      if (!isNativePlatform() || !isEngineReady()) {
        console.log('[Store] Engine not ready, skipping analysis');
        return;
      }

      this.engineThinking = true;
      this.engineInfo = null;
      
      // 生成 FEN
      const fen = boardToFen(this.board, this.currentTurn);
      console.log('[Store] Starting analysis, FEN:', fen);
      
      await analyzePosition(fen, 20);
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
    },

    /**
     * 悔棋
     */
    undoMove() {
      if (this.history.length === 0) return;
      
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
      
      // 重新分析
      if (isNativePlatform() && isEngineReady()) {
        this.startEngineAnalysis();
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
      
      // 重新分析初始局面
      if (isNativePlatform() && isEngineReady()) {
        this.startEngineAnalysis();
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
