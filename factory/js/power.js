// =============================================================
// power.js — 전력 시스템 (이 게임의 핵심)
//   - 전신주끼리 거리(reach) 안이면 자동 연결 → 연결된 전신주 묶음 = 하나의 전력망
//   - 전신주 주변 공급 영역(supplyRadius) 안에 한 칸이라도 걸친 기계/발전기/납품소가 그 망에 속함
//   - 공급: 납품소 기본 전력 + 연료가 있는 발전기
//   - 수요 > 공급이면 망 전체 효율(=공급/수요) 저하 → 기계가 느려짐
//   - 과부하가 tripSeconds 동안 이어지면 차단기 트립 → 망 전체 정지, 탭해서 재투입
// =============================================================

class PowerSystem {
  constructor(scene) {
    this.scene = scene;
    const cfg = GameData.buildings._power || {};
    this.tripSeconds = cfg.tripSeconds || 6;
    this.nets = [];
    this.consumers = [];   // 전기가 필요한 건물
    this.dirty = true;
    this.wireGfx = scene.add.graphics().setDepth(26);
    this.areaGfx = scene.add.graphics().setDepth(5);
    this.showArea = false;
    this._wirePairs = [];
    this._nextNetId = 1;
  }

  onBuilding() { this.dirty = true; }

  // 전력과 관계있는 건물인가
  static usesPower(b) {
    const l = b.def.logic;
    return b.def.powerKW > 0 || l === 'hub' || l === 'generator' || l === 'pole';
  }

  // ---------------------------------------------------------
  // 전력망 다시 계산 (건물이 바뀔 때만)
  // ---------------------------------------------------------
  rebuild() {
    const bm = this.scene.buildings;
    const grid = this.scene.grid;
    const poles = [];
    this.consumers = [];
    bm.list.forEach(b => {
      if (b.def.logic === 'pole') poles.push(b);
      if (b.def.powerKW > 0) this.consumers.push(b);
      if (PowerSystem.usesPower(b)) b.net = null;
    });

    // 유니온-파인드
    const parent = poles.map((_, i) => i);
    const find = i => { while (parent[i] !== i) { parent[i] = parent[parent[i]]; i = parent[i]; } return i; };
    const union = (a, b) => { a = find(a); b = find(b); if (a !== b) parent[a] = b; };

    // 1) 전신주끼리 연결
    this._wirePairs = [];
    for (let i = 0; i < poles.length; i++) {
      const a = poles[i];
      for (let j = i + 1; j < poles.length; j++) {
        const b = poles[j];
        const reach = Math.min(a.def.reach, b.def.reach);
        const dx = a.x - b.x, dy = a.y - b.y;
        if (dx * dx + dy * dy <= reach * reach) {
          union(i, j);
          this._wirePairs.push([a, b]);
        }
      }
    }

    // 2) 공급 영역에 걸친 건물 → 전신주 묶음에 연결 (두 망에 걸치면 두 망을 합침)
    const owner = new Map(); // 건물 id → 전신주 인덱스
    poles.forEach((p, i) => {
      const r = p.def.supplyRadius;
      for (let ty = p.y - r; ty <= p.y + r; ty++) {
        for (let tx = p.x - r; tx <= p.x + r; tx++) {
          const id = grid.buildingIdAt(tx, ty);
          if (!id || id === p.id) continue;
          const b = bm.get(id);
          if (!b || !PowerSystem.usesPower(b) || b.def.logic === 'pole') continue;
          if (owner.has(id)) union(owner.get(id), i);
          else owner.set(id, i);
        }
      }
    });

    // 3) 묶음별 전력망 객체 만들기 (트립 상태는 전신주에 기록된 값으로 이어받음)
    const byRoot = new Map();
    const netOf = (i) => {
      const r = find(i);
      let net = byRoot.get(r);
      if (!net) {
        net = { id: this._nextNetId++, poles: [], members: [], gens: [], hubs: [], cap: 0, demand: 0, sat: 1, overloadT: 0, tripped: false, warned: false };
        byRoot.set(r, net);
      }
      return net;
    };
    poles.forEach((p, i) => {
      const net = netOf(i);
      net.poles.push(p);
      p.net = net;
      if (p.st.tripped) net.tripped = true;
      if (p.st.overT) net.overloadT = Math.max(net.overloadT, p.st.overT);
    });
    owner.forEach((pi, id) => {
      const b = bm.get(id);
      const net = netOf(pi);
      b.net = net;
      const l = b.def.logic;
      if (l === 'generator') net.gens.push(b);
      else if (l === 'hub') net.hubs.push(b);
      if (b.def.powerKW > 0) net.members.push(b);
    });
    this.nets = [...byRoot.values()];

    // 전기가 안 들어오는 기계 위에 경고 아이콘
    for (const b of this.consumers) {
      if (!b.net) {
        b.pf = 0;
        if (!b.noPowerIcon) {
          b.noPowerIcon = this.scene.add.image((b.x + b.w / 2) * TILE, (b.y + b.h / 2) * TILE, 'nopower')
            .setDepth(17).setScale(0.6).setAlpha(0.9);
        }
      } else if (b.noPowerIcon) {
        b.noPowerIcon.destroy();
        b.noPowerIcon = null;
      }
    }
    this.dirty = false;
    this.drawWires();
    this.drawArea();
  }

  // ---------------------------------------------------------
  // 시뮬레이션 한 스텝 (기계의 지난 스텝 '가동 희망' 상태로 수요 계산)
  // ---------------------------------------------------------
  step(dt) {
    if (this.dirty) this.rebuild();
    for (const net of this.nets) {
      let hubCap = 0, genCap = 0, demand = 0;
      for (const h of net.hubs) hubCap += h.def.supplyKW;
      for (const g of net.gens) if (g.st.coal > 0 || g.st.energy > 0) genCap += g.def.supplyKW;
      for (const m of net.members) if (m.wants) demand += m.def.powerKW;
      const cap = hubCap + genCap;
      net.cap = cap;
      net.demand = demand;

      if (net.tripped) {
        net.sat = 0;
        for (const g of net.gens) { g.st.output = 0; g.status = 'nopower'; }
      } else {
        net.sat = demand > 0 ? Math.min(1, cap / demand) : 1;
        // 납품소 전력을 먼저 쓰고 모자란 만큼만 발전기가 연료를 태운다
        const supplied = Math.min(demand, cap);
        const genNeed = Math.max(0, supplied - hubCap);
        for (const g of net.gens) {
          const st = g.st;
          const has = st.coal > 0 || st.energy > 0;
          const out = (has && genCap > 0) ? genNeed * g.def.supplyKW / genCap : 0;
          st.output = out;
          st.energy -= out * dt;
          while (st.energy < 0 && st.coal > 0) { st.coal--; st.energy += g.def.fuelKJ; }
          if (st.energy < 0) st.energy = 0;
          g.status = !has ? 'idle' : (out > 0 ? 'work' : 'idle');
          g.wants = false;
        }
        // 과부하 감시
        if (demand > cap + 0.01) {
          if (!net.warned) { net.warned = true; this.scene.onOverloadStart(net); }
          net.overloadT += dt;
          if (net.overloadT >= this.tripSeconds) this.trip(net);
        } else {
          net.overloadT = Math.max(0, net.overloadT - dt * 0.5);
          if (net.overloadT === 0) net.warned = false;
        }
      }
      for (const m of net.members) m.pf = net.sat;
      for (const p of net.poles) p.st.overT = net.overloadT;
    }
  }

  trip(net) {
    net.tripped = true;
    net.overloadT = 0;
    net.warned = false;
    for (const p of net.poles) { p.st.tripped = true; p.st.overT = 0; }
    this.drawWires();
    this.scene.onTrip(net);
  }

  // 차단기 재투입
  reset(net) {
    if (!net || !net.tripped) return false;
    net.tripped = false;
    net.overloadT = 0;
    for (const p of net.poles) { p.st.tripped = false; p.st.overT = 0; }
    this.drawWires();
    return true;
  }

  resetAll() {
    let n = 0;
    for (const net of this.nets) if (this.reset(net)) n++;
    return n;
  }

  // 상단 게이지용 요약
  summary() {
    let cap = 0, demand = 0, tripped = 0, overload = 0, left = Infinity;
    for (const net of this.nets) {
      if (!net.members.length && !net.hubs.length && !net.gens.length) continue;
      cap += net.cap; demand += net.demand;
      if (net.tripped) tripped++;
      else if (net.overloadT > 0 && net.demand > net.cap) {
        overload++;
        left = Math.min(left, this.tripSeconds - net.overloadT);
      }
    }
    const unpowered = this.consumers.filter(b => !b.net).length;
    return { nets: this.nets.length, cap, demand, tripped, overload, left, unpowered };
  }

  // ---------------------------------------------------------
  // 그리기: 전선, 공급 영역
  // ---------------------------------------------------------
  drawWires() {
    const g = this.wireGfx;
    g.clear();
    for (const [a, b] of this._wirePairs) {
      const tripped = a.net && a.net.tripped;
      const ax = (a.x + 0.5) * TILE, ay = (a.y + 0.5) * TILE;
      const bx = (b.x + 0.5) * TILE, by = (b.y + 0.5) * TILE;
      g.lineStyle(7, 0x000000, 0.18);
      g.lineBetween(ax + 3, ay + 5, bx + 3, by + 5);
      g.lineStyle(4, tripped ? 0xff5a5a : 0x2b3240, 0.9);
      g.lineBetween(ax, ay, bx, by);
    }
  }

  setShowArea(v) {
    if (this.showArea === v) return;
    this.showArea = v;
    this.drawArea();
  }

  drawArea() {
    const g = this.areaGfx;
    g.clear();
    if (!this.showArea) return;
    this.scene.buildings.list.forEach(p => {
      if (p.def.logic !== 'pole') return;
      const r = p.def.supplyRadius;
      g.fillStyle(0x2f80ff, 0.12);
      g.fillRect((p.x - r) * TILE, (p.y - r) * TILE, (r * 2 + 1) * TILE, (r * 2 + 1) * TILE);
      g.lineStyle(3, 0x2f80ff, 0.5);
      g.strokeRect((p.x - r) * TILE, (p.y - r) * TILE, (r * 2 + 1) * TILE, (r * 2 + 1) * TILE);
    });
  }
}
