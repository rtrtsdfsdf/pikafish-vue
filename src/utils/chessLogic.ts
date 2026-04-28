// 中国象棋逻辑

import type { Position, Move, PieceColor } from '@/types/chess';

// 初始棋盘布局
export const INITIAL_BOARD: string[][] = [
  ['r', 'n', 'b', 'a', 'k', 'a', 'b', 'n', 'r'],
  [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
  [' ', 'c', ' ', ' ', ' ', ' ', ' ', 'c', ' '],
  ['p', ' ', 'p', ' ', 'p', ' ', 'p', ' ', 'p'],
  [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
  [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
  ['P', ' ', 'P', ' ', 'P', ' ', 'P', ' ', 'P'],
  [' ', 'C', ' ', ' ', ' ', ' ', ' ', 'C', ' '],
  [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
  ['R', 'N', 'B', 'A', 'K', 'A', 'B', 'N', 'R'],
];

// 棋子名称映射
export const PIECE_NAMES: Record<string, string> = {
  'k': '将', 'K': '帅',
  'a': '士', 'A': '仕',
  'b': '象', 'B': '相',
  'n': '马', 'N': '马',
  'r': '车', 'R': '车',
  'c': '炮', 'C': '炮',
  'p': '卒', 'P': '兵',
};

// 获取棋子颜色
export function getPieceColor(piece: string): PieceColor {
  if (!piece || piece === ' ') return '';
  return piece === piece.toUpperCase() ? 'red' : 'black';
}

// 检查位置是否在棋盘内
export function isValidPosition(row: number, col: number): boolean {
  return row >= 0 && row <= 9 && col >= 0 && col <= 8;
}

// 检查是否在九宫内
export function isInPalace(row: number, col: number, color: PieceColor): boolean {
  if (col < 3 || col > 5) return false;
  if (color === 'black') {
    return row >= 0 && row <= 2;
  } else {
    return row >= 7 && row <= 9;
  }
}

// 检查是否过河
export function hasCrossedRiver(row: number, color: PieceColor): boolean {
  if (color === 'red') {
    return row <= 4;
  } else {
    return row >= 5;
  }
}

// 获取合法走法
export function getValidMoves(board: string[][], row: number, col: number): Position[] {
  const piece = board[row][col];
  if (!piece || piece === ' ') return [];
  
  const color = getPieceColor(piece);
  const moves: Position[] = [];
  const type = piece.toLowerCase();
  
  switch (type) {
    case 'k': // 将/帅
      moves.push(...getKingMoves(board, row, col, color));
      break;
    case 'a': // 士/仕
      moves.push(...getAdvisorMoves(board, row, col, color));
      break;
    case 'b': // 象/相
      moves.push(...getBishopMoves(board, row, col, color));
      break;
    case 'n': // 马
      moves.push(...getKnightMoves(board, row, col, color));
      break;
    case 'r': // 车
      moves.push(...getRookMoves(board, row, col, color));
      break;
    case 'c': // 炮
      moves.push(...getCannonMoves(board, row, col, color));
      break;
    case 'p': // 兵/卒
      moves.push(...getPawnMoves(board, row, col, color));
      break;
  }
  
  return moves;
}

// 将/帅走法
function getKingMoves(board: string[][], row: number, col: number, color: PieceColor): Position[] {
  const moves: Position[] = [];
  const directions = [[0, 1], [0, -1], [1, 0], [-1, 0]];
  
  for (const [dr, dc] of directions) {
    const newRow = row + dr;
    const newCol = col + dc;
    if (isInPalace(newRow, newCol, color)) {
      const target = board[newRow][newCol];
      if (target === ' ' || getPieceColor(target) !== color) {
        moves.push({ row: newRow, col: newCol });
      }
    }
  }
  
  // 将帅对面
  const enemyKing = color === 'red' ? 'k' : 'K';
  for (let r = 0; r <= 9; r++) {
    if (board[r][col] === enemyKing) {
      let blocked = false;
      const minR = Math.min(row, r);
      const maxR = Math.max(row, r);
      for (let i = minR + 1; i < maxR; i++) {
        if (board[i][col] !== ' ') {
          blocked = true;
          break;
        }
      }
      if (!blocked) {
        moves.push({ row: r, col });
      }
      break;
    }
  }
  
  return moves;
}

// 士/仕走法
function getAdvisorMoves(board: string[][], row: number, col: number, color: PieceColor): Position[] {
  const moves: Position[] = [];
  const directions = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
  
  for (const [dr, dc] of directions) {
    const newRow = row + dr;
    const newCol = col + dc;
    if (isInPalace(newRow, newCol, color)) {
      const target = board[newRow][newCol];
      if (target === ' ' || getPieceColor(target) !== color) {
        moves.push({ row: newRow, col: newCol });
      }
    }
  }
  
  return moves;
}

// 象/相走法
function getBishopMoves(board: string[][], row: number, col: number, color: PieceColor): Position[] {
  const moves: Position[] = [];
  const directions = [[2, 2], [2, -2], [-2, 2], [-2, -2]];
  const eyes = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
  
  for (let i = 0; i < directions.length; i++) {
    const [dr, dc] = directions[i];
    const [er, ec] = eyes[i];
    const newRow = row + dr;
    const newCol = col + dc;
    const eyeRow = row + er;
    const eyeCol = col + ec;
    
    // 检查象眼
    if (board[eyeRow]?.[eyeCol] !== ' ') continue;
    
    // 检查是否过河
    if (!isValidPosition(newRow, newCol)) continue;
    if (hasCrossedRiver(newRow, color)) continue;
    
    const target = board[newRow][newCol];
    if (target === ' ' || getPieceColor(target) !== color) {
      moves.push({ row: newRow, col: newCol });
    }
  }
  
  return moves;
}

// 马走法
function getKnightMoves(board: string[][], row: number, col: number, color: PieceColor): Position[] {
  const moves: Position[] = [];
  const jumps = [
    { dr: -2, dc: -1, lr: -1, lc: 0 },
    { dr: -2, dc: 1, lr: -1, lc: 0 },
    { dr: 2, dc: -1, lr: 1, lc: 0 },
    { dr: 2, dc: 1, lr: 1, lc: 0 },
    { dr: -1, dc: -2, lr: 0, lc: -1 },
    { dr: -1, dc: 2, lr: 0, lc: 1 },
    { dr: 1, dc: -2, lr: 0, lc: -1 },
    { dr: 1, dc: 2, lr: 0, lc: 1 },
  ];
  
  for (const { dr, dc, lr, lc } of jumps) {
    const newRow = row + dr;
    const newCol = col + dc;
    const legRow = row + lr;
    const legCol = col + lc;
    
    // 检查蹩马腿
    if (board[legRow]?.[legCol] !== ' ') continue;
    
    if (!isValidPosition(newRow, newCol)) continue;
    
    const target = board[newRow][newCol];
    if (target === ' ' || getPieceColor(target) !== color) {
      moves.push({ row: newRow, col: newCol });
    }
  }
  
  return moves;
}

// 车走法
function getRookMoves(board: string[][], row: number, col: number, color: PieceColor): Position[] {
  const moves: Position[] = [];
  const directions = [[0, 1], [0, -1], [1, 0], [-1, 0]];
  
  for (const [dr, dc] of directions) {
    let newRow = row + dr;
    let newCol = col + dc;
    
    while (isValidPosition(newRow, newCol)) {
      const target = board[newRow][newCol];
      if (target === ' ') {
        moves.push({ row: newRow, col: newCol });
      } else {
        if (getPieceColor(target) !== color) {
          moves.push({ row: newRow, col: newCol });
        }
        break;
      }
      newRow += dr;
      newCol += dc;
    }
  }
  
  return moves;
}

// 炮走法
function getCannonMoves(board: string[][], row: number, col: number, color: PieceColor): Position[] {
  const moves: Position[] = [];
  const directions = [[0, 1], [0, -1], [1, 0], [-1, 0]];
  
  for (const [dr, dc] of directions) {
    let newRow = row + dr;
    let newCol = col + dc;
    let jumped = false;
    
    while (isValidPosition(newRow, newCol)) {
      const target = board[newRow][newCol];
      
      if (!jumped) {
        if (target === ' ') {
          moves.push({ row: newRow, col: newCol });
        } else {
          jumped = true;
        }
      } else {
        if (target !== ' ') {
          if (getPieceColor(target) !== color) {
            moves.push({ row: newRow, col: newCol });
          }
          break;
        }
      }
      
      newRow += dr;
      newCol += dc;
    }
  }
  
  return moves;
}

// 兵/卒走法
function getPawnMoves(board: string[][], row: number, col: number, color: PieceColor): Position[] {
  const moves: Position[] = [];
  const forward = color === 'red' ? -1 : 1;
  
  // 前进
  const newRow = row + forward;
  if (isValidPosition(newRow, col)) {
    const target = board[newRow][col];
    if (target === ' ' || getPieceColor(target) !== color) {
      moves.push({ row: newRow, col });
    }
  }
  
  // 过河后可以左右移动
  if (hasCrossedRiver(row, color)) {
    for (const dc of [-1, 1]) {
      const newCol = col + dc;
      if (isValidPosition(row, newCol)) {
        const target = board[row][newCol];
        if (target === ' ' || getPieceColor(target) !== color) {
          moves.push({ row, col: newCol });
        }
      }
    }
  }
  
  return moves;
}

// 执行走法
export function makeMove(board: string[][], from: Position, to: Position): string[][] {
  const newBoard = board.map(row => [...row]);
  newBoard[to.row][to.col] = newBoard[from.row][from.col];
  newBoard[from.row][from.col] = ' ';
  return newBoard;
}

/**
 * 棋盘转 FEN（中国象棋格式）
 * 格式：rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1
 * 
 * @param board 棋盘
 * @param turn 当前回合 ('red' | 'black')
 * @param halfMoves 半回合数（可选）
 * @param fullMoves 完整回合数（可选）
 */
export function boardToFen(
  board: string[][], 
  turn: 'red' | 'black' = 'red',
  halfMoves: number = 0,
  fullMoves: number = 1
): string {
  const fenRows: string[] = [];
  
  for (const row of board) {
    let fenRow = '';
    let empty = 0;
    
    for (const cell of row) {
      if (cell === ' ') {
        empty++;
      } else {
        if (empty > 0) {
          fenRow += empty.toString();
          empty = 0;
        }
        fenRow += cell;
      }
    }
    
    if (empty > 0) {
      fenRow += empty.toString();
    }
    
    fenRows.push(fenRow);
  }
  
  // 中国象棋 FEN：回合标记 w=红方(大写), b=黑方(小写)
  const turnChar = turn === 'red' ? 'w' : 'b';
  
  // 完整 FEN 格式
  return `${fenRows.join('/')} ${turnChar} - - ${halfMoves} ${fullMoves}`;
}
// 列号转中文
const COL_NAMES = ['九', '八', '七', '六', '五', '四', '三', '二', '一']

// 生成中国象棋走法记号
export function moveToString(board: string[][], from: { row: number, col: number }, to: { row: number, col: number }, piece: string): string {
  const pieceName = PIECE_NAMES[piece] || piece
  const isRed = piece === piece.toUpperCase()
  
  // 起始列号（红方从右到左是九到一，黑方从左到右是1到9）
  const fromColName = isRed ? COL_NAMES[from.col] : String(from.col + 1)
  
  // 目标列号
  const toColName = isRed ? COL_NAMES[to.col] : String(to.col + 1)
  
  // 判断走法类型
  const rowDiff = to.row - from.row
  const colDiff = to.col - from.col
  
  let action = ''
  let target = ''
  
  if (colDiff === 0) {
    // 直线走（进/退）
    if ((isRed && rowDiff < 0) || (!isRed && rowDiff > 0)) {
      action = '进'
      target = String(Math.abs(rowDiff))
    } else {
      action = '退'
      target = String(Math.abs(rowDiff))
    }
  } else if (rowDiff === 0) {
    // 平移
    action = '平'
    target = toColName
  } else {
    // 斜走（马、象、士）
    if ((isRed && rowDiff < 0) || (!isRed && rowDiff > 0)) {
      action = '进'
    } else {
      action = '退'
    }
    target = toColName
  }
  
  return `${pieceName}${fromColName}${action}${target}`
}

// 走法转 UCI 格式
export function moveToUci(from: Position, to: Position): string {
  const cols = 'abcdefghi';
  return `${cols[from.col]}${9 - from.row}${cols[to.col]}${9 - to.row}`;
}

// UCI 格式转走法
export function uciToMove(uci: string): { from: Position; to: Position } | null {
  if (uci.length < 4) return null;
  
  const cols = 'abcdefghi';
  const fromCol = cols.indexOf(uci[0] || '');
  const fromRow = 9 - parseInt(uci[1] || '0');
  const toCol = cols.indexOf(uci[2] || '');
  const toRow = 9 - parseInt(uci[3] || '0');
  
  if (fromCol < 0 || toCol < 0) return null;
  
  return {
    from: { row: fromRow, col: fromCol },
    to: { row: toRow, col: toCol }
  };
}
