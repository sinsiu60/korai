// =============================================================
// save.js — localStorage 저장/불러오기
//   - 모든 읽기/쓰기를 try/catch로 감싸 실패해도 게임이 멈추지 않는다.
//     (시크릿 모드, 저장 공간 부족, 깨진 데이터 등)
//   - 지형은 시드로 다시 만들고, 건물과 내부 상태(벨트 위 아이템, 재료 버퍼 등)만 저장한다.
// =============================================================

const SaveSystem = {
  KEY: 'wattfactory.save.v1',
  META_KEY: 'wattfactory.meta.v1',
  VERSION: 1,

  // ---------------------------------------------------------
  // 저장소 접근 (실패하면 false/null)
  // ---------------------------------------------------------
  write(data) {
    try {
      localStorage.setItem(this.KEY, JSON.stringify(data));
      localStorage.setItem(this.META_KEY, JSON.stringify({
        savedAt: data.savedAt,
        delivered: Object.values(data.tech.totals || {}).reduce((a, b) => a + b, 0),
        done: (data.tech.done || []).length,
        buildings: data.buildings.length,
      }));
      return true;
    } catch (e) {
      console.warn('[저장 실패]', e);
      return false;
    }
  },

  read() {
    try {
      const s = localStorage.getItem(this.KEY);
      if (!s) return null;
      const d = JSON.parse(s);
      if (!d || d.v !== this.VERSION || !Array.isArray(d.buildings)) return null;
      return d;
    } catch (e) {
      console.warn('[불러오기 실패]', e);
      return null;
    }
  },

  // 메뉴에 보여줄 요약 (전체 저장 데이터를 파싱하지 않음)
  meta() {
    try {
      const s = localStorage.getItem(this.META_KEY);
      return s ? JSON.parse(s) : null;
    } catch (e) {
      return null;
    }
  },

  clear() {
    try {
      localStorage.removeItem(this.KEY);
      localStorage.removeItem(this.META_KEY);
    } catch (e) { /* 무시 */ }
  },

  // ---------------------------------------------------------
  // 게임 → 저장 데이터
  // ---------------------------------------------------------
  serialize(scene) {
    const ids = Registry.itemIds;
    const enc = arr => {
      const o = {};
      arr.forEach((v, i) => { if (v > 0) o[ids[i]] = v; });
      return o;
    };
    const buildings = [];
    scene.buildings.list.forEach(b => {
      const e = { id: b.id, t: b.type, x: b.x, y: b.y, d: b.dir };
      if (b.on === false) e.off = 1;
      const st = b.st || {};
      let s = null;
      switch (b.def.logic) {
        case 'conveyor':
          if (st.items.length) s = { i: st.items.map(it => [ids[it.t], Math.round(it.pos * 1000) / 1000]) };
          break;
        case 'splitter':
        case 'merger':
          if (st.buf >= 0) s = { b: ids[st.buf] };
          break;
        case 'miner': s = { p: st.progress, o: st.out >= 0 ? ids[st.out] : null }; break;
        case 'crafter': s = { r: st.recipe, inv: enc(st.inv), out: enc(st.out), p: st.progress, c: st.crafting ? 1 : 0 }; break;
        case 'storage': s = { inv: enc(st.inv) }; break;
        case 'generator': s = { coal: st.coal, e: st.energy }; break;
        case 'pole': if (st.tripped) s = { tr: 1 }; break;
        case 'plc': s = { se: st.sensor, co: st.cond, ta: st.target, ac: st.action }; break;
      }
      if (s) e.s = s;
      buildings.push(e);
    });
    const cam = scene.cameras.main;
    return {
      v: this.VERSION,
      savedAt: Date.now(),
      seed: scene.seed,
      nextId: scene.buildings.nextId,
      toolDir: scene.toolDir,
      playTime: Math.round(scene.playTime),
      cam: { x: cam.scrollX + cam.width / 2, y: cam.scrollY + cam.height / 2, css: cam.zoom * TILE / DPR },
      tech: scene.tech.serialize(),
      buildings,
    };
  },

  // ---------------------------------------------------------
  // 저장 데이터 → 건물 상태 객체 (알 수 없는 아이템은 버림)
  // ---------------------------------------------------------
  decodeState(e, def) {
    const n = Registry.itemIds.length;
    const idx = id => (id in Registry.itemIndex ? Registry.itemIndex[id] : -1);
    const dec = o => {
      const arr = new Array(n).fill(0);
      if (o) for (const k in o) { const i = idx(k); if (i >= 0) arr[i] = Math.max(0, o[k] | 0); }
      return arr;
    };
    const s = e.s || {};
    switch (def.logic) {
      case 'conveyor': {
        const items = (s.i || [])
          .map(([id, pos]) => ({ t: idx(id), pos: Util.clamp(+pos || 0, 0, 1) }))
          .filter(it => it.t >= 0)
          .sort((a, b) => b.pos - a.pos);
        return { items, next: null, shape: 0 };
      }
      case 'splitter': return { buf: s.b ? idx(s.b) : -1, rr: 0 };
      case 'merger': return { buf: s.b ? idx(s.b) : -1, rr: 0, req: 0, prevReq: 0 };
      case 'miner': return { progress: +s.p || 0, out: s.o ? idx(s.o) : -1, ore: -1, frac: 0, outRR: 0 };
      case 'crafter': {
        const out = dec(s.out);
        const recipe = s.r && Registry.recipes[s.r] ? s.r : null;
        return {
          recipe, inv: dec(s.inv), out, outTotal: out.reduce((a, b) => a + b, 0),
          progress: +s.p || 0, crafting: !!(s.c && recipe), outRR: 0,
        };
      }
      case 'storage': {
        const inv = dec(s.inv);
        return { inv, total: inv.reduce((a, b) => a + b, 0), outRR: 0, rr: 0 };
      }
      case 'generator': return { coal: s.coal | 0, energy: +s.e || 0, output: 0 };
      case 'pole': return { tripped: !!s.tr };
      case 'plc': return {
        sensor: s.se | 0, cond: s.co || 'full', target: s.ta | 0, action: s.ac === 'on' ? 'on' : 'off', state: false,
      };
    }
    return {};
  },

  // 건물 복원 (하나가 깨져도 나머지는 계속)
  restoreBuildings(scene, data) {
    const bm = scene.buildings;
    let maxId = 0, skipped = 0;
    for (const e of data.buildings) {
      try {
        const def = GameData.buildings[e.t];
        if (!def || e.t.startsWith('_')) { skipped++; continue; }
        const dir = (e.d | 0) & 3;
        if (!def.fixed && !bm.canPlace(e.t, e.x | 0, e.y | 0, dir).ok) { skipped++; continue; }
        const b = bm.place(e.t, e.x | 0, e.y | 0, dir, {
          force: !!def.fixed, id: e.id | 0, st: this.decodeState(e, def),
        });
        if (!b) { skipped++; continue; }
        if (e.off) b.on = false;
        maxId = Math.max(maxId, b.id);
      } catch (err) {
        console.warn('[건물 복원 실패]', e, err);
        skipped++;
      }
    }
    bm.nextId = Math.max(bm.nextId, maxId + 1, data.nextId | 0);
    return skipped;
  },
};
