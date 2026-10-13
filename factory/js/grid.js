// =============================================================
// grid.js — 격자 맵: 지형(광석 지대)과 건물 점유 정보
//   - 아이템/건물 객체를 칸마다 만들지 않고 타입 배열로 관리해서 가볍게 유지한다.
// =============================================================

class Grid {
  /**
   * @param {object} mapDef data/map.json 내용
   */
  constructor(mapDef) {
    this.def = mapDef;
    this.w = mapDef.width;
    this.h = mapDef.height;
    // 광석 종류: 0=없음, 1..N = mapDef.ores[i-1]
    this.ore = new Uint8Array(this.w * this.h);
    // 장식용 무늬 변형값(0~3)
    this.variant = new Uint8Array(this.w * this.h);
    // 건물 점유: 0=비어 있음, 그 외=건물 id
    this.occ = new Int32Array(this.w * this.h);
  }

  idx(x, y) { return y * this.w + x; }

  inBounds(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h; }

  // 해당 칸의 광석 정의(없으면 null)
  oreAt(x, y) {
    if (!this.inBounds(x, y)) return null;
    const o = this.ore[this.idx(x, y)];
    return o ? this.def.ores[o - 1] : null;
  }

  buildingIdAt(x, y) {
    if (!this.inBounds(x, y)) return 0;
    return this.occ[this.idx(x, y)];
  }

  // 사각 영역 점유 설정 (id=0이면 비움)
  setOccRect(x, y, w, h, id) {
    for (let j = 0; j < h; j++) {
      for (let i = 0; i < w; i++) {
        this.occ[this.idx(x + i, y + j)] = id;
      }
    }
  }

  // 시작 지점(납품소) 기준 좌상단 좌표
  hubOrigin(size) {
    return {
      x: Math.floor(this.w / 2) - Math.floor(size[0] / 2),
      y: Math.floor(this.h / 2) - Math.floor(size[1] / 2),
    };
  }

  /**
   * 광석 지대를 랜덤 생성한다. 시드가 같으면 결과도 같다.
   */
  generate(seed) {
    const rand = Util.rng(seed);
    const cx = this.w / 2, cy = this.h / 2;
    const clearR = this.def.startClearRadius || 5;

    for (let i = 0; i < this.variant.length; i++) {
      this.variant[i] = Math.floor(rand() * 4);
    }

    this.def.ores.forEach((oreDef, oi) => {
      const oreIndex = oi + 1;
      const total = oreDef.patches;
      const near = oreDef.nearStart || 0;
      for (let p = 0; p < total; p++) {
        const r = Util.randInt(rand, oreDef.radius[0], oreDef.radius[1]);
        let px, py, tries = 0;
        // 위치 고르기: 앞쪽 몇 개는 시작 지점 근처(8~14칸)에 보장
        do {
          if (p < near) {
            // 광석 종류마다 다른 각도에서 시작해 서로 겹치지 않게
            const ang = (oi / this.def.ores.length) * Math.PI * 2 + rand() * 1.2;
            const d = 9 + rand() * 5;
            px = Math.round(cx + Math.cos(ang) * d);
            py = Math.round(cy + Math.sin(ang) * d);
          } else {
            px = Util.randInt(rand, r + 1, this.w - r - 2);
            py = Util.randInt(rand, r + 1, this.h - r - 2);
          }
          tries++;
        } while (tries < 40 && (Util.dist(px, py, cx, cy) < clearR + r + 1 || this._crowded(px, py, r)));
        this._stampPatch(rand, px, py, r, oreIndex, cx, cy, clearR);
      }
    });
  }

  // 이미 다른 광석이 많이 있는 자리인지 검사 (지대끼리 너무 붙지 않게)
  _crowded(px, py, r) {
    let n = 0;
    for (let y = py - r; y <= py + r; y++) {
      for (let x = px - r; x <= px + r; x++) {
        if (this.inBounds(x, y) && this.ore[this.idx(x, y)]) n++;
      }
    }
    return n > 2;
  }

  // 울퉁불퉁한 원 모양으로 광석 칸을 찍는다
  _stampPatch(rand, px, py, r, oreIndex, cx, cy, clearR) {
    // 각도별 반지름 흔들림(8방향 보간)으로 자연스러운 모양
    const wobble = [];
    for (let k = 0; k < 8; k++) wobble.push(0.75 + rand() * 0.5);
    for (let y = py - r - 1; y <= py + r + 1; y++) {
      for (let x = px - r - 1; x <= px + r + 1; x++) {
        if (!this.inBounds(x, y)) continue;
        if (Util.dist(x + 0.5, y + 0.5, cx, cy) < clearR) continue;
        const dx = x - px, dy = y - py;
        const ang = (Math.atan2(dy, dx) + Math.PI) / (Math.PI * 2) * 8;
        const k0 = Math.floor(ang) % 8, k1 = (k0 + 1) % 8, f = ang - Math.floor(ang);
        const rr = r * (wobble[k0] * (1 - f) + wobble[k1] * f) + 0.3;
        if (Math.sqrt(dx * dx + dy * dy) <= rr) {
          const i = this.idx(x, y);
          if (!this.ore[i]) this.ore[i] = oreIndex;
        }
      }
    }
  }

  // 타일맵용 2차원 인덱스 배열 (Art.TILESET 순서와 맞춘다)
  toTileRows() {
    const rows = [];
    for (let y = 0; y < this.h; y++) {
      const row = [];
      for (let x = 0; x < this.w; x++) {
        const i = this.idx(x, y);
        const o = this.ore[i];
        const v = this.variant[i] & 1; // 변형 2종
        row.push(o ? (2 + (o - 1) * 2 + v) : ((x + y) & 1 ? 1 : 0));
      }
      rows.push(row);
    }
    return rows;
  }
}
