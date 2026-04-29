// 棋子类型
export type PieceType = 'k' | 'a' | 'b' | 'n' | 'r' | 'c' | 'p' | 
                         'K' | 'A' | 'B' | 'N' | 'R' | 'C' | 'P' | '';

// 棋子颜色
export type PieceColor = 'red' | 'black' | '';

// 棋盘位置
export interface Position {
  row: number;
  col: number;
}

// 走法
export interface Move {
  from: Position;
  to: Position;
  piece: string;
  captured?: string;
}

// 引擎分析结果
export interface EngineInfo {
  depth: number;
  score?: number;
  mate?: number;
  nodes: number;
  nps: number;
  pv: string[];
  time: number;
}

// 箭头（用于显示走法）
export interface Arrow {
  from: Position;
  to: Position;
  color: string; // 箭头颜色
}

// 游戏状态
export interface GameState {
  board: string[][];
  currentTurn: 'red' | 'black';
  selectedPos: Position | null;
  validMoves: Position[];
  history: Move[];
  engineThinking: boolean;
  engineInfo: EngineInfo | null;
  gameOver: boolean;
  winner: 'red' | 'black' | null;
  arrows: Arrow[]; // 显示的箭头列表
}
