// =============================================================
// util.js — 공용 상수와 작은 도우미 함수
// =============================================================

// 한 칸의 월드 크기(px). 텍스처도 이 크기로 그린다.
const TILE = 64;

// 방향: 0=오른쪽(동), 1=아래(남), 2=왼쪽(서), 3=위(북)
const DIR = { E: 0, S: 1, W: 2, N: 3 };
const DX = [1, 0, -1, 0];
const DY = [0, 1, 0, -1];
const DIR_NAME = ['오른쪽', '아래', '왼쪽', '위'];
const DIR_ARROW = ['→', '↓', '←', '↑'];

// 기기 픽셀 비율(레티나 대응). 너무 크면 성능이 떨어지므로 2로 제한.
const DPR = Math.min(window.devicePixelRatio || 1, 2);

const Util = {
  // 시드 고정 난수 (mulberry32) — 같은 시드면 같은 맵이 나온다(저장/불러오기 대비)
  rng(seed) {
    let a = seed >>> 0;
    return function () {
      a = (a + 0x6D2B79F5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  },

  randInt(rand, min, max) {
    return min + Math.floor(rand() * (max - min + 1));
  },

  clamp(v, min, max) {
    return v < min ? min : (v > max ? max : v);
  },

  // 회전 방향을 반영한 건물 점유 크기 (세로 방향이면 가로/세로가 바뀐다)
  footprint(def, dir) {
    const w = def.size[0], h = def.size[1];
    return (dir === DIR.S || dir === DIR.N) ? { w: h, h: w } : { w, h };
  },

  // 두 점 사이 거리
  dist(x1, y1, x2, y2) {
    const dx = x2 - x1, dy = y2 - y1;
    return Math.sqrt(dx * dx + dy * dy);
  },
};
