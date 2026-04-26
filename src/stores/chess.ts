import { defineStore } from 'pinia';
import type { GameState, Position, Move, EngineInfo } from '@/types/chess';
import { 
  INITIAL_BOARD, 
  getValidMoves, 
  makeMove, 
  getPieceColor,
  boardToFen
} from '@/utils/chessLogic';

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
    // 初始化引擎（简化版）
    async initGameEngine() {
      // 简化版不需要引擎初始化
      console.log('Game initialized');
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
      
      // 简化版：不调用引擎分析
      this.engineThinking = false;
    },

    // 开始引擎分析（简化版：不做任何事）
    startEngineAnalysis() {
      this.engineThinking = false;
    },

    // 停止引擎分析
    stopEngineAnalysis() {
      this.engineThinking = false;
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
    },

    // 翻转棋盘
    flipBoard() {
      this.board = this.board.reverse().map(row => [...row].reverse());
      this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
    }
  }
});
