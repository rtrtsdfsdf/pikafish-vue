import { defineStore } from 'pinia';
import type { GameState, Position, Move, EngineInfo, Arrow } from '@/types/chess';
import { 
  INITIAL_BOARD, 
  getValidMoves, 
  makeMove, 
  getPieceColor,
  boardToFen,
  moveToUci,
  uciToMove,
  generateNotation
} from '@/utils/chessLogic';
import { 
  initEngine, 
  sendCommand, 
  parseEngineLine, 
  startAnalysis,
  stopAnalysis
} from '@/utils/engine';
import { logger } from '@/utils/logger';

export const useChessStore = defineStore('chess', {
  state: (): GameState & { isMoving: boolean; moveSequence: number } => ({
    board: INITIAL_BOARD.map(row => [...row]),
    currentTurn: 'red',
    selectedPos: null,
    validMoves: [],
    history: [],
    engineThinking: false,
    engineInfo: null,
    gameOver: false,
    winner: null,
    arrows: [],
    autoPlayMode: 'none',
    engineDepth: 20,
    currentMoveIndex: -1,
    isMoving: false,
    moveSequence: 0, // 每次走子后递增，用于过滤过时的 bestmove
    searchSequence: 0, // 当前搜索启动时的 moveSequence 值
  }),

  actions: {
    // 初始化引擎
    async initGameEngine() {
      await initEngine((line) => this.handleEngineMessage(line));
    },

    // 处理引擎消息
    handleEngineMessage(line: string) {
      logger.info('[Engine]', line);
      const parsed = parseEngineLine(line);
      
      if (parsed.type === 'info') {
        if (this.isMoving) return;
        
        this.engineInfo = parsed.data as EngineInfo;
        logger.info('[Engine Info]', this.engineInfo);
        
        if (this.engineInfo.pv && this.engineInfo.pv.length > 0) {
          this.updateArrowsFromPV(this.engineInfo.pv);
        }
      } else if (parsed.type === 'bestmove') {
        if (this.isMoving) {
          logger.info('[BestMove] Ignored, move in progress');
          return;
        }
        
        this.engineThinking = false;
        
        // 序列号检查：如果 moveSequence 已经变化（棋盘已更新），说明是 stop 的残留 bestmove
        if (this.searchSequence < this.moveSequence) {
          logger.info('[BestMove] Stale from old search, ignored');
          return;
        }
        
        const bestMove = parsed.data;
        logger.info('[BestMove]', bestMove);
        if (bestMove && this.shouldAutoPlay()) {
          this.executeEngineMove(bestMove);
        }
      }
    },

    // 判断是否应该 AI 自动走子
    shouldAutoPlay(): boolean {
      if (this.gameOver) return false;
      
      const isAITurn = 
        (this.autoPlayMode === 'red' && this.currentTurn === 'red') ||
        (this.autoPlayMode === 'black' && this.currentTurn === 'black') ||
        (this.autoPlayMode === 'both');
      
      return isAITurn;
    },

    // 执行引擎走法
    executeEngineMove(uci: string) {
      const move = uciToMove(uci);
      if (move) {
        this.movePiece(move.from, move.to);
      }
    },

    // 设置自动对弈模式
    setAutoPlayMode(mode: 'none' | 'red' | 'black' | 'both') {
      this.autoPlayMode = mode;
      
      // 如果当前是 AI 回合，立即开始思考
      if (this.shouldAutoPlay() && !this.gameOver) {
        this.stopEngineAnalysis();
        this.startEngineAnalysis();
      }
    },

    // 根据 PV 更新箭头
    updateArrowsFromPV(pv: string[]) {
      const arrows: Arrow[] = [];
      const colors = ['#ffeb3b', '#ff9800', '#4caf50', '#2196f3'];
      
      for (let i = 0; i < Math.min(pv.length, 3); i++) {
        const uci = pv[i];
        if (!uci) continue;
        
        const move = uciToMove(uci);
        if (move) {
          arrows.push({
            from: move.from,
            to: move.to,
            color: colors[i] || colors[colors.length - 1]!
          });
        }
      }
      
      this.arrows = arrows;
    },

    // 选择棋子
    selectPiece(pos: Position) {
      if (this.gameOver) return;
      
      const piece = this.board[pos.row][pos.col];
      
      if (this.selectedPos) {
        const isValidMove = this.validMoves.some(
          m => m.row === pos.row && m.col === pos.col
        );
        
        if (isValidMove) {
          this.movePiece(this.selectedPos, pos);
          return;
        }
      }
      
      if (piece && piece !== ' ' && getPieceColor(piece) === this.currentTurn) {
        this.selectedPos = pos;
        this.validMoves = getValidMoves(this.board, pos.row, pos.col);
      } else {
        this.selectedPos = null;
        this.validMoves = [];
      }
    },

    // 移动棋子
    movePiece(from: Position, to: Position) {
      if (this.isMoving) {
        logger.warn('[movePiece] Already moving, ignoring');
        return;
      }
      
      this.isMoving = true;
      
      const piece = this.board[from.row][from.col];
      const captured = this.board[to.row][to.col];
      
      // 生成走法记号
      const notation = generateNotation(piece, from, to, captured !== ' ' ? captured : undefined);
      
      // 记录走法
      const move: Move = {
        from,
        to,
        piece,
        captured: captured !== ' ' ? captured : undefined,
        notation
      };
      this.history.push(move);
      this.currentMoveIndex = this.history.length - 1;
      
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
      this.arrows = [];
      
      // 递增序列号（在 stopAnalysis 之前，确保后续被丢弃的 bestmove 能检测到过期）
      this.moveSequence++;
      
      // 先停止当前分析（会发送 stop，残留 bestmove 会被 searchSequence 过滤掉）
      this.stopEngineAnalysis();
      
      // 通知引擎新棋盘
      const fen = boardToFen(this.board);
      const turn = this.currentTurn === 'red' ? 'w' : 'b';
      sendCommand(`position fen ${fen} ${turn}`);

      // 解锁
      this.isMoving = false;
      
      // 如果游戏未结束且当前是 AI 回合，则开新引擎
      if (!this.gameOver && this.shouldAutoPlay()) {
        this.startEngineAnalysis();
      }
      // ⚠️ AI 走后自动切到对方回合，如果对方不是 AI，则停下来等人下棋
      // 不调 startSilentAnalysis 也不调任何 go 命令
    },

    // 静默分析（已废弃 — 非 AI 回合不需要给引擎发任何 go 命令）

    // 开始引擎分析
    startEngineAnalysis() {
      this.engineThinking = true;
      this.searchSequence = this.moveSequence; // 记录当前搜索时的序列号
      startAnalysis(20);
    },

    // 停止引擎分析
    stopEngineAnalysis() {
      this.engineInfo = null;
      this.arrows = [];
      this.engineThinking = false;
      stopAnalysis();
    },

    // 悔棋
    undoMove() {
      if (this.history.length === 0) return;
      if (this.isMoving) return;
      
      const lastMove = this.history.pop()!;
      
      this.board[lastMove.from.row][lastMove.from.col] = lastMove.piece;
      this.board[lastMove.to.row][lastMove.to.col] = lastMove.captured || ' ';
      
      this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
      this.gameOver = false;
      this.winner = null;
      this.selectedPos = null;
      this.validMoves = [];
      this.arrows = [];
      
      this.stopEngineAnalysis();

      const fen = boardToFen(this.board);
      const turn = this.currentTurn === 'red' ? 'w' : 'b';
      sendCommand(`position fen ${fen} ${turn}`);
      
      if (!this.gameOver && this.shouldAutoPlay()) {
        this.startEngineAnalysis();
      }
    },

    // 重新开始
    resetGame() {
      this.board = INITIAL_BOARD.map(row => [...row]);
      this.currentTurn = 'red';
      this.selectedPos = null;
      this.validMoves = [];
      this.history = [];
      this.engineThinking = false;
      this.engineInfo = null;
      this.gameOver = false;
      this.winner = null;
      this.arrows = [];
      this.isMoving = false;
      this.moveSequence = 0; // 重置序列号
      this.searchSequence = 0;
      
      this.stopEngineAnalysis();
      sendCommand('ucinewgame');
      sendCommand('position fen rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w');
    },

    // 翻转棋盘
    flipBoard() {
      this.board = this.board.reverse().map(row => [...row].reverse());
      this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
    },

    // 走法导航
    goToStart() {
      this.currentMoveIndex = -1;
      this.rebuildBoardFromHistory();
    },

    goToPrev() {
      if (this.currentMoveIndex >= 0) {
        this.currentMoveIndex--;
        this.rebuildBoardFromHistory();
      }
    },

    goToNext() {
      if (this.currentMoveIndex < this.history.length - 1) {
        this.currentMoveIndex++;
        this.rebuildBoardFromHistory();
      }
    },

    goToEnd() {
      this.currentMoveIndex = this.history.length - 1;
      this.rebuildBoardFromHistory();
    },

    rebuildBoardFromHistory() {
      this.board = INITIAL_BOARD.map(row => [...row]);
      this.currentTurn = 'red';
      
      for (let i = 0; i <= this.currentMoveIndex; i++) {
        const move = this.history[i]!;
        this.board = makeMove(this.board, move.from, move.to);
        this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
      }
      
      this.selectedPos = null;
      this.validMoves = [];
    },

    async getHint() {
      this.startEngineAnalysis();
    },
  }
});
