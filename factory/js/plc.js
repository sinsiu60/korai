// =============================================================
// plc.js — PLC 블록: "센서 조건 → 대상 기계 ON/OFF" 간단 논리 제어
//   예) 창고 가득 참 → 채굴기 OFF / 전력 부족 → 조립기 OFF (부하 차단)
//   - PLC 자체도 전기가 있어야 동작한다. 꺼지면 대상은 원래대로 켜진다.
//   - 여러 PLC가 같은 대상을 제어하면 하나라도 OFF면 OFF.
// =============================================================

const PLC_CONDS = [
  ['full', '가득 참'],
  ['empty', '비어 있음'],
  ['power', '전력 부족'],
  ['working', '가동 중'],
];
const PLC_ACTIONS = [
  ['off', '참이면 대상 끄기'],
  ['on', '참일 때만 대상 켜기'],
];

class PLCSystem {
  constructor(scene) {
    this.scene = scene;
    this.controlled = new Set(); // 지난 스텝에 PLC가 끈 건물 id
  }

  step() {
    const bm = this.scene.buildings;
    // 지난 스텝 제어 해제 후 다시 판단
    this.controlled.forEach(id => { const b = bm.get(id); if (b) b.plcOff = false; });
    this.controlled.clear();

    const list = this.scene.machines.list;
    for (let k = 0; k < list.length; k++) {
      const plc = list[k];
      if (plc.def.logic !== 'plc') continue;
      const st = plc.st;
      const on = plc.on !== false;
      plc.wants = on;
      if (!on) { plc.status = 'off'; st.state = false; continue; }
      if (plc.pf <= 0) { plc.status = 'nopower'; st.state = false; continue; }
      const sensor = st.sensor ? bm.get(st.sensor) : null;
      const target = st.target ? bm.get(st.target) : null;
      if (!sensor || !target) { plc.status = 'idle'; st.state = false; continue; }
      plc.status = 'work';
      const cond = PLCSystem.evaluate(sensor, st.cond);
      st.state = cond;
      const off = st.action === 'off' ? cond : !cond;
      if (off) { target.plcOff = true; this.controlled.add(target.id); }
    }
  }

  // 센서 건물의 조건 판단
  static evaluate(s, cond) {
    const st = s.st || {};
    const l = s.def.logic;
    switch (cond) {
      case 'full':
        if (l === 'storage') return st.total >= s.def.capacity;
        if (l === 'crafter') {
          const r = st.recipe ? Registry.recipes[st.recipe] : null;
          return st.outTotal + (r ? r.outSum : 1) > OUT_CAP;
        }
        if (l === 'conveyor') return st.items.length >= 2 && st.items[0].pos >= 1;
        if (l === 'miner') return st.out >= 0;
        if (l === 'generator') return st.coal >= s.def.fuelCap;
        if (l === 'splitter' || l === 'merger') return st.buf >= 0;
        return false;
      case 'empty':
        if (l === 'storage') return st.total === 0;
        if (l === 'crafter') return !st.crafting && !st.inv.some(v => v > 0);
        if (l === 'conveyor') return st.items.length === 0;
        if (l === 'generator') return st.coal === 0 && st.energy <= 0;
        if (l === 'splitter' || l === 'merger') return st.buf < 0;
        return false;
      case 'power': {
        const net = s.net;
        return !net || net.tripped || net.sat < 1 || net.overloadT > 0;
      }
      case 'working':
        return s.status === 'work';
    }
    return false;
  }
}
