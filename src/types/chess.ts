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
  notation?: string;
}

// 引擎分析结果
export interface EngineInfo {
  depth: number;
  score?: number;
  scoreType?: 'cp' | 'mate';  // 分数类型：centipawns 或 mate
  nodes: number;
  nps?: number;  // 每秒节点数（可选）
  pv: string[];
  time: number;
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
  autoPlayMode: 'none' | 'red' | 'black' | 'both';
  pendingAutoMove: boolean;
  engineDepth: number;
  logs: { time: string; message: string; }[];
}
