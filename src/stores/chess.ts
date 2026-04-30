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
    arrows: [],
    autoPlayMode: 'none',
    engineDepth: 20,
    currentMoveIndex: -1,
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
        this.engineInfo = parsed.data as EngineInfo;
        logger.info('[Engine Info]', this.engineInfo);
        
        // 根据 PV 更新箭头
        if (this.engineInfo.pv && this.engineInfo.pv.length > 0) {
          this.updateArrowsFromPV(this.engineInfo.pv);
        }
      } else if (parsed.type === 'bestmove') {
        this.engineThinking = false;
        
        // 如果是 AI 模式，自动执行走法
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
        // 延迟一点执行，让用户看到思考过程
        setTimeout(() => {
          this.movePiece(move.from, move.to);
        }, 500);
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
      const colors = ['#ffeb3b', '#ff9800', '#4caf50', '#2196f3']; // 黄、橙、绿、蓝
      
      // 最多显示前 3 个走法的箭头
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

    // 移动棋子
    movePiece(from: Position, to: Position) {
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
      this.arrows = []; // 清空箭头
      
      // 先停止当前分析
      this.stopEngineAnalysis();

      // 通知引擎
      const fen = boardToFen(this.board);
      const turn = this.currentTurn === 'red' ? 'w' : 'b';
      sendCommand(`position fen ${fen} ${turn}`);
      
      // 如果游戏未结束，开始分析
      if (!this.gameOver) {
        // 如果是 AI 回合，让引擎思考并走子
        if (this.shouldAutoPlay()) {
          this.startEngineAnalysis();
        } else {
          // 非 AI 模式下也进行分析（显示箭头提示），但不显示"思考中"
          this.startSilentAnalysis();
        }
      }
    },

    // 静默分析（不显示思考中，只更新箭头）
    startSilentAnalysis() {
      const fen = boardToFen(this.board);
      const turn = this.currentTurn === 'red' ? 'w' : 'b';
      sendCommand(`position fen ${fen} ${turn}`);
      sendCommand(`go depth ${this.engineDepth}`);
      // 注意：这里不设置 engineThinking = true
    },

    // 开始引擎分析
    startEngineAnalysis() {
      this.engineThinking = true;
      startAnalysis(20);
    },

    // 停止引擎分析
    stopEngineAnalysis() {
      // 立即清除旧分析结果（视觉反馈）
      this.engineInfo = null;
      this.arrows = [];
      this.engineThinking = false;
      
      // 通知引擎停止当前搜索
      stopAnalysis();
    },

    // 悔棋
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
      this.arrows = [];
      
      // 先停止当前分析
      this.stopEngineAnalysis();

      // 更新引擎
      const fen = boardToFen(this.board);
      const turn = this.currentTurn === 'red' ? 'w' : 'b';
      sendCommand(`position fen ${fen} ${turn}`);
      
      // 悔棋后重新分析新局面（与走子后一致）
      if (!this.gameOver) {
        if (this.shouldAutoPlay()) {
          this.startEngineAnalysis();
        } else {
          this.startSilentAnalysis();
        }
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
      this.arrows = []; // 清空箭头
      
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

    // 根据历史记录重建棋盘
    rebuildBoardFromHistory() {
      // 重置到初始状态
      this.board = INITIAL_BOARD.map(row => [...row]);
      this.currentTurn = 'red';
      
      // 重放到当前索引
      for (let i = 0; i <= this.currentMoveIndex; i++) {
        const move = this.history[i]!;
        this.board = makeMove(this.board, move.from, move.to);
        this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
      }
      
      this.selectedPos = null;
      this.validMoves = [];
    },

    // 获取提示
    async getHint() {
      this.startEngineAnalysis();
    },
  }
});
