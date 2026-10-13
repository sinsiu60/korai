// =============================================================
// buildings.js — 건물 배치·회전·삭제와 겹침 검사
//   - 건물 데이터(위치/방향)와 화면 스프라이트를 함께 관리한다.
//   - 다른 시스템(컨베이어, 전력 등)은 onChange 콜백으로 변경을 통지받는다.
// =============================================================

class BuildingManager {
  /**
   * @param {Phaser.Scene} scene
   * @param {Grid} grid
   * @param {object} defs data/buildings.json
   */
  constructor(scene, grid, defs) {
    this.scene = scene;
    this.grid = grid;
    this.defs = defs;
    this.list = new Map(); // id -> building
    this.nextId = 1;
    this.listeners = [];
    // 건물 스프라이트를 한 레이어에 모아 그리기 순서를 고정
    this.layer = scene.add.layer().setDepth(10);
  }

  onChange(fn) { this.listeners.push(fn); }
  _emit(kind, b) { for (const fn of this.listeners) fn(kind, b); }

  get(id) { return this.list.get(id) || null; }

  at(x, y) {
    const id = this.grid.buildingIdAt(x, y);
    return id ? this.list.get(id) : null;
  }

  count() { return this.list.size; }

  /**
   * 배치 가능 여부 검사
   * @returns {{ok:boolean, reason?:string}}
   */
  canPlace(type, x, y, dir, ignoreId = 0) {
    const def = this.defs[type];
    if (!def) return { ok: false, reason: '알 수 없는 건물' };
    const fp = Util.footprint(def, dir);
    let oreCount = 0;
    for (let j = 0; j < fp.h; j++) {
      for (let i = 0; i < fp.w; i++) {
        const tx = x + i, ty = y + j;
        if (!this.grid.inBounds(tx, ty)) return { ok: false, reason: '맵 밖에는 지을 수 없어요' };
        const occ = this.grid.occ[this.grid.idx(tx, ty)];
        if (occ && occ !== ignoreId) return { ok: false, reason: '다른 건물과 겹쳐요' };
        if (this.grid.ore[this.grid.idx(tx, ty)]) oreCount++;
      }
    }
    if (def.placeOn === 'ore' && oreCount === 0) {
      return { ok: false, reason: '광석 지대 위에만 설치할 수 있어요' };
    }
    return { ok: true };
  }

  /**
   * 건물 배치. 성공하면 building 객체, 실패하면 null
   * @param {object} opts force=검사 생략, id=지정 id, st=복원할 내부 상태
   */
  place(type, x, y, dir = 0, opts = {}) {
    const def = this.defs[type];
    if (!def) return null;
    if (!def.rotatable) dir = 0;
    if (!opts.force && !this.canPlace(type, x, y, dir).ok) return null;
    const fp = Util.footprint(def, dir);
    // 불러오기일 때는 저장된 id를 그대로 사용 (PLC 연결이 id로 되어 있음)
    let id = opts.id && !this.list.has(opts.id) ? opts.id : this.nextId;
    while (this.list.has(id)) id++;
    this.nextId = Math.max(this.nextId, id + 1);
    const b = {
      id,
      type, x, y, dir,
      w: fp.w, h: fp.h,
      def,
      sprite: null,
      st: opts.st || null,  // 각 시스템이 쓰는 내부 상태 (add 통지 때 채워짐)
      on: true,             // 수동 ON/OFF
    };
    this.list.set(b.id, b);
    this.grid.setOccRect(x, y, fp.w, fp.h, b.id);
    this._createSprite(b);
    this._emit('add', b);
    return b;
  }

  remove(id) {
    const b = this.list.get(id);
    if (!b || b.def.fixed) return false;
    this.grid.setOccRect(b.x, b.y, b.w, b.h, 0);
    this.list.delete(id);
    if (b.sprite) b.sprite.destroy();
    this._emit('remove', b);
    return true;
  }

  /**
   * 회전 (시계 방향 90도). 가로세로가 다른 건물은 자리가 있을 때만.
   * @param {number} [toDir] 지정하면 그 방향으로 설정
   */
  rotate(id, toDir) {
    const b = this.list.get(id);
    if (!b || !b.def.rotatable) return false;
    const nd = (toDir === undefined) ? (b.dir + 1) % 4 : toDir;
    if (nd === b.dir) return true;
    const fp = Util.footprint(b.def, nd);
    if (fp.w !== b.w) {
      // 크기가 바뀌는 회전: 중심을 유지하도록 좌상단 재계산 후 검사
      const nx = b.x + Math.floor((b.w - fp.w) / 2);
      const ny = b.y + Math.floor((b.h - fp.h) / 2);
      if (!this.canPlace(b.type, nx, ny, nd, b.id).ok) return false;
      this.grid.setOccRect(b.x, b.y, b.w, b.h, 0);
      b.x = nx; b.y = ny; b.w = fp.w; b.h = fp.h;
      this.grid.setOccRect(b.x, b.y, b.w, b.h, b.id);
    }
    b.dir = nd;
    this._placeSprite(b);
    this._emit('rotate', b);
    return true;
  }

  // 건물 중심의 월드 좌표
  centerOf(b) {
    return { x: (b.x + b.w / 2) * TILE, y: (b.y + b.h / 2) * TILE };
  }

  _createSprite(b) {
    const s = this.scene.add.image(0, 0, 'b_' + b.type);
    this.layer.add(s);
    b.sprite = s;
    this._placeSprite(b);
  }

  _placeSprite(b) {
    const c = this.centerOf(b);
    b.sprite.setPosition(c.x, c.y);
    b.sprite.setRotation(b.dir * Math.PI / 2);
  }

  // 배치 직후 살짝 튀어오르는 연출
  popEffect(b) {
    if (!b || !b.sprite) return;
    b.sprite.setScale(0.8);
    this.scene.tweens.add({ targets: b.sprite, scale: 1, duration: 140, ease: 'Back.Out' });
  }
}
