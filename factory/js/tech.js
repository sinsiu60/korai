// =============================================================
// tech.js — 납품 목표와 해금 트리 (data/tech.json)
//   - 납품소에 누적 납품한 수량으로 목표 달성 여부를 판단한다.
//   - 선행 목표(requires)가 모두 끝난 목표만 진행 중으로 보인다.
// =============================================================

class TechSystem {
  constructor(onComplete) {
    this.onComplete = onComplete;
    this.totals = {};       // 아이템 id → 누적 납품 수
    this.done = new Set();  // 달성한 목표 id
    this.dirty = true;
    this.nodes = GameData.tech.nodes;
  }

  deliver(itemId) {
    this.totals[itemId] = (this.totals[itemId] || 0) + 1;
    this.dirty = true;
  }

  isAvailable(n) {
    return !this.done.has(n.id) && n.requires.every(r => this.done.has(r));
  }

  available() { return this.nodes.filter(n => this.isAvailable(n)); }

  // 목표별 진행 [{item, have, need}]
  progress(n) {
    return Object.keys(n.goal).map(item => ({
      item, need: n.goal[item], have: Math.min(this.totals[item] || 0, n.goal[item]),
    }));
  }

  ratio(n) {
    const p = this.progress(n);
    return p.reduce((a, x) => a + x.have / x.need, 0) / p.length;
  }

  // 달성 확인 (연쇄 달성도 처리)
  check() {
    if (!this.dirty) return;
    this.dirty = false;
    let changed = true;
    while (changed) {
      changed = false;
      for (const n of this.available()) {
        if (this.progress(n).every(p => p.have >= p.need)) {
          this.done.add(n.id);
          this._applyNode(n);
          this.onComplete(n);
          changed = true;
        }
      }
    }
  }

  _applyNode(n) {
    (n.unlocks.buildings || []).forEach(b => Registry.unlockedBuildings.add(b));
    (n.unlocks.recipes || []).forEach(r => Registry.unlockedRecipes.add(r));
  }

  // 불러온 뒤 해금 상태 다시 적용
  applyAll() {
    Registry.resetUnlocks();
    this.nodes.forEach(n => { if (this.done.has(n.id)) this._applyNode(n); });
  }

  isFinished() { return this.nodes.every(n => this.done.has(n.id)); }

  serialize() { return { totals: this.totals, done: [...this.done] }; }

  load(d) {
    if (!d) return;
    this.totals = Object.assign({}, d.totals || {});
    this.done = new Set((d.done || []).filter(id => this.nodes.some(n => n.id === id)));
    this.applyAll();
    this.dirty = true;
  }
}
