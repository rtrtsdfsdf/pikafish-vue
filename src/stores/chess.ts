import { defineStore } from 'pinia';
import type { Position, Move, EngineInfo, Arrow } from '@/types/chess';
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
import { initEngine, sendCommand, parseEngineLine, destroyEngine } from '@/utils/engine';
import { logger } from '@/utils/logger';

/*
 * ───────── 状态机 ─────────
 * idle     — 引擎空闲，可以发起新分析
 * thinking — 引擎正在思考，等待 bestmove
 * applying — 已收到 bestmove，正在落子
 *
 * 允许的转换：
 *   idle → thinking     (startAnalysis)
 *   thinking → applying (收到 bestmove)
 *   applying → idle     (落子完成)
 *
 * 任何不在 thinking 状态收到的 bestmove → 丢弃
 * ────────────────────── */
type EnginePhase = 'idle' | 'thinking' | 'applying';

interface ChessState {
  board: string[][];
  currentTurn: 'red' | 'black';
  selectedPos: Position | null;
  validMoves: Position[];
  history: Move[];
  gameOver: boolean;
  winner: 'red' | 'black' | null;
  arrows: Arrow[];
  autoPlayMode: 'none' | 'red' | 'black' | 'both';
  engineDepth: number;
  currentMoveIndex: number;
  /** 引擎思考中（供 UI 绑定） */
  engineThinking: boolean;
  /** 最佳着法 */
  engineInfo: EngineInfo | null;
  /** 引擎状态机 */
  _phase: EnginePhase;
}

export const useChessStore = defineStore('chess', {
  state: (): ChessState => ({
    board: INITIAL_BOARD.map(r => [...r]),
    currentTurn: 'red',
    selectedPos: null,
    validMoves: [],
    history: [],
    gameOver: false,
    winner: null,
    arrows: [],
    autoPlayMode: 'none',
    engineDepth: 20,
    currentMoveIndex: -1,
    engineThinking: false,
    engineInfo: null,
    _phase: 'idle',
  }),

  getters: {
    /** 当前方是否是 AI 走 */
    isAITurn(state): boolean {
      if (state.gameOver) return false;
      if (state.autoPlayMode === 'both') return true;
      return state.autoPlayMode === state.currentTurn;
    },
  },

  actions: {
    /* ============ 引擎生命周期 ============ */

    async initGameEngine() {
      await initEngine((line) => this._onEngineMessage(line));
    },

    /* ============ 引擎消息处理（状态机核心） ============ */

    _onEngineMessage(line: string) {
      const parsed = parseEngineLine(line);

      if (parsed.type === 'info') {
        if (this._phase !== 'thinking') return;        // 过时信息
        this.engineInfo = parsed.data as EngineInfo;
        if (this.engineInfo?.pv?.length) {
          this._updateArrowsFromPV(this.engineInfo.pv);
        }
        return;
      }

      if (parsed.type === 'bestmove') {
        // ★ 只接受 thinking 阶段的 bestmove
        if (this._phase !== 'thinking') {
          logger.warn('[Engine] stale bestmove ignored (phase=%s)', this._phase);
          return;
        }

        this.engineThinking = false;
        const uci = parsed.data as string;
        logger.info('[Engine] bestmove %s', uci);
        this._phase = 'applying';          // → applying

        if (uci && uci !== '(none)') {
          this._applyEngineMove(uci);
        } else {
          // 无着法（认输/杀棋），回到 idle
          this._phase = 'idle';
        }
        return;
      }

      // 其他类型忽略
    },

    /* ============ 启动/停止分析 ============ */

    /** 发起新分析：先设 position fen 再发 go，保证顺序 */
    async _startAnalysis() {
      if (this._phase !== 'idle') {
        logger.warn('[Engine] startAnalysis skipped (phase=%s)', this._phase);
        return;
      }
      this._phase = 'thinking';
      this.engineThinking = true;
      this.engineInfo = null;
      this.arrows = [];

      const fen = boardToFen(this.board);
      try {
        // await position fen 完成后才发 go，杜绝乱序
        const turn = this.currentTurn === 'red' ? 'w' : 'b';
        await sendCommand('position fen ' + fen + ' ' + turn);
        await sendCommand('go depth ' + this.engineDepth);
      } catch (err: any) {
        // 引擎挂了 → 回退到 idle
        logger.error('[Engine] analysis start failed: %s', err?.message || err);
        this._phase = 'idle';
        this.engineThinking = false;
      }
    },

    /** 停止当前分析（fire-and-forget，不能 await——避免与 _startAnalysis 时序竞态） */
    _stopAnalysis() {
      // 发 stop 命令，不等待
      sendCommand('stop').catch(() => {});
      this.engineThinking = false;
      this.engineInfo = null;
      this.arrows = [];
      // 如果引擎在 thinking 状态，重置为 idle
      if (this._phase === 'thinking') {
        this._phase = 'idle';
      }
    },

    /* ============ 落子与导航 ============ */

    /** 应用引擎的走法（applying 阶段） */
    _applyEngineMove(uci: string) {
      const move = uciToMove(uci);
      if (!move) {
        logger.error('[Engine] invalid uci: %s', uci);
        this._phase = 'idle';               // 回到 idle，避免死锁
        return;
      }
      this._executeMove(move.from, move.to);
      // _executeMove 末尾会检查 isAITurn 并再次 startAnalysis
    },

    /** 点击落子（用户操作） */
    clickCell(pos: Position) {
      if (this.gameOver) return;
      if (this.isAITurn) return;            // AI 走时不允许点击
      if (this._phase !== 'idle') return;   // 引擎工作中不允许点击

      // 选中 → 走子 或 切换选中
      if (this.selectedPos && this._isValidTarget(pos)) {
        this._executeMove(this.selectedPos, pos);
        this.selectedPos = null;
        this.validMoves = [];
      } else {
        this._selectPiece(pos);
      }
    },

    /** 选择棋子：显示合法走法 */
    _selectPiece(pos: Position) {
      const piece = this.board[pos.row][pos.col];
      if (!piece || getPieceColor(piece) !== this.currentTurn) {
        this.selectedPos = null;
        this.validMoves = [];
        return;
      }
      this.selectedPos = pos;
      this.validMoves = getValidMoves(this.board, pos.row, pos.col);
    },

    /** 检查目标位置是否合法 */
    _isValidTarget(pos: Position): boolean {
      return this.validMoves.some(m => m.row === pos.row && m.col === pos.col);
    },

    /** 执行走法（状态机核心转换） */
    _executeMove(from: Position, to: Position) {
      try {
        const piece = this.board[from.row][from.col];
        const captured = this.board[to.row][to.col];
        const notation = generateNotation(piece, from, to, captured);

        // 走一步
        this.board = makeMove(this.board, from, to);
        const move: Move = { from, to, piece, captured: captured || undefined, notation };
        
        // 更新历史（截断当前位置之后的记录）
        if (this.currentMoveIndex < this.history.length - 1) {
          this.history = this.history.slice(0, this.currentMoveIndex + 1);
        }
        this.history.push(move);
        this.currentMoveIndex = this.history.length - 1;

        // 换手
        this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';

        // 检查将杀/困毙（简单检查：无合法走法即结束）
        this._checkGameOver();

        // 清空选中状态
        this.selectedPos = null;
        this.validMoves = [];
      } finally {
        // ★ 确保状态机回到 idle
        if (this._phase === 'applying') {
          this._phase = 'idle';
        }
      }

      // ★ 检查是否需要AI继续走
      if (!this.gameOver && this.isAITurn) {
        this._startAnalysis();
      }
    },

    /** 检查游戏是否结束 */
    _checkGameOver() {
      // 遍历当前方所有棋子，检查是否还有合法走法
      for (let r = 0; r < 10; r++) {
        for (let c = 0; c < 9; c++) {
          const p = this.board[r][c];
          if (p && getPieceColor(p) === this.currentTurn) {
            const moves = getValidMoves(this.board, r, c);
            if (moves.length > 0) return;  // 还有走法，游戏继续
          }
        }
      }
      // 无合法走法 → 对面获胜
      this.gameOver = true;
      this.winner = this.currentTurn === 'red' ? 'black' : 'red';
    },

    /** 初始化引擎并启动 AI（如果设置为 auto） */
    async startGame() {
      await this.initGameEngine();
      if (this.isAITurn) {
        this._startAnalysis();
      }
    },

    /* ============ 模式切换 ============ */

    setAutoPlayMode(mode: 'none' | 'red' | 'black' | 'both') {
      // 停止当前分析
      if (this._phase === 'thinking') {
        this._stopAnalysis();
      }
      this.autoPlayMode = mode;
      this._phase = 'idle';                 // 强制回到 idle
      this.engineThinking = false;
      this.engineInfo = null;

      // 如果切换到 AI，立即启动
      if (!this.gameOver && this.isAITurn) {
        this._startAnalysis();
      }
    },

    /* ============ 提示（获取引擎建议） ============ */

    async getHint() {
      if (this.gameOver || this._phase !== 'idle') return;
      this._stopAnalysis();                 // 清理
      this._startAnalysis();
      // bestmove 到达后会自动显示箭头
    },

    /* ============ 走法导航 ============ */

    goToStart() { this._navigateTo(-1); },
    goToPrev()  { this._navigateTo(this.currentMoveIndex - 1); },
    goToNext()  { this._navigateTo(this.currentMoveIndex + 1); },
    goToEnd()   { this._navigateTo(this.history.length - 1); },

    _navigateTo(index: number) {
      if (index < -1 || index >= this.history.length) return;
      this.currentMoveIndex = index;
      this._rebuildBoard();
    },

    _rebuildBoard() {
      this.board = INITIAL_BOARD.map(r => [...r]);
      this.currentTurn = 'red';
      for (let i = 0; i <= this.currentMoveIndex; i++) {
        const m = this.history[i];
        if (!m) continue;
        this.board = makeMove(this.board, m.from, m.to);
        this.currentTurn = this.currentTurn === 'red' ? 'black' : 'red';
      }
      this.selectedPos = null;
      this.validMoves = [];
    },

    /* ============ 重置 ============ */

    resetGame() {
      this._stopAnalysis();
      this.board = INITIAL_BOARD.map(r => [...r]);
      this.currentTurn = 'red';
      this.selectedPos = null;
      this.validMoves = [];
      this.history = [];
      this.gameOver = false;
      this.winner = null;
      this.arrows = [];
      this.currentMoveIndex = -1;
      this.engineThinking = false;
      this.engineInfo = null;
      this._phase = 'idle';
    },

    /* ============ 工具方法 ============ */

    _updateArrowsFromPV(pv: string[]) {
      this.arrows = [];
      // 只显示前两步，第一步橙红醒目，第二步半透淡灰，易于区分
      const colors = ['#FF6B35', 'rgba(200, 200, 200, 0.6)'];
      for (let i = 0; i < Math.min(pv.length, 2); i++) {
        const uci = pv[i];
        if (!uci) break;
        const move = uciToMove(uci);
        if (move) {
          this.arrows.push({ from: move.from, to: move.to, color: colors[i] || colors[colors.length - 1]! });
        }
      }
    },
  },
});
