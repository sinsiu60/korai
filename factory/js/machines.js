// =============================================================
// machines.js — 아이템 주고받기(Logistics) + 채굴기·가공기·창고·납품소 동작
// =============================================================

// 가공기 출력 버퍼 최대치 (이만큼 쌓이면 '출력 막힘'으로 멈춤)
const OUT_CAP = 4;

// 상태별 램프 색
const STATUS_COLOR = {
  work: 0x3ddc84,     // 가동 중
  idle: 0xffcf5c,     // 재료 대기
  blocked: 0xff9f43,  // 출력 막힘
  nopower: 0xff5a5a,  // 전력 없음/트립
  off: 0x8a94a6,      // 꺼짐 (수동/PLC)
};
const STATUS_TEXT = {
  work: '가동 중', idle: '재료 대기', blocked: '출력 막힘', nopower: '전력 없음', off: '꺼짐',
};

// -------------------------------------------------------------
// Logistics: 건물 사이 아이템 전달 규칙
// -------------------------------------------------------------
class Logistics {
  constructor(scene) { this.scene = scene; }

  // 수동 OFF 또는 PLC OFF가 아니면 켜짐
  static isOn(b) { return b.on !== false && !b.plcOff; }

  /**
   * 건물 tgt가 아이템 t를 받을 수 있으면 받고 true
   * @param moveDir 아이템이 움직이는 방향 (tgt의 어느 면으로 들어오는지 판단)
   */
  accept(tgt, t, moveDir) {
    switch (tgt.def.logic) {
      case 'conveyor':
      case 'splitter':
      case 'merger':
        return this.scene.conveyors.accept(tgt, t, moveDir);
      case 'crafter':
        return this.scene.machines.acceptCrafter(tgt, t, moveDir);
      case 'storage':
        return this.scene.machines.acceptStorage(tgt, t, moveDir);
      case 'generator':
        return this.scene.machines.acceptGenerator(tgt, t);
      case 'hub':
        this.scene.deliver(tgt, t);
        return true;
      default:
        return false;
    }
  }

  /**
   * 여러 칸짜리 건물의 앞면(출력 방향)에 닿은 칸들로 번갈아 내보내기
   */
  pushOut(b, t) {
    const d = b.dir;
    const cnt = (d === DIR.E || d === DIR.W) ? b.h : b.w;
    const st = b.st;
    for (let k = 0; k < cnt; k++) {
      const idx = ((st.outRR || 0) + k) % cnt;
      let tx, ty;
      if (d === DIR.E) { tx = b.x + b.w; ty = b.y + idx; }
      else if (d === DIR.W) { tx = b.x - 1; ty = b.y + idx; }
      else if (d === DIR.S) { tx = b.x + idx; ty = b.y + b.h; }
      else { tx = b.x + idx; ty = b.y - 1; }
      const tgt = this.scene.buildings.at(tx, ty);
      if (tgt && tgt !== b && this.accept(tgt, t, d)) {
        st.outRR = idx + 1;
        return true;
      }
    }
    return false;
  }
}

// -------------------------------------------------------------
// MachineSystem
// -------------------------------------------------------------
class MachineSystem {
  constructor(scene) {
    this.scene = scene;
    this.list = [];         // 채굴기·가공기·창고·발전기 등 (컨베이어 제외)
    this.dirty = true;
    this.nItems = Registry.itemIds.length;
    this.lampLayer = scene.add.layer().setDepth(16);
  }

  onBuilding(kind, b) {
    const l = b.def.logic;
    if (l === 'conveyor' || l === 'splitter' || l === 'merger') return;
    if (kind === 'add') {
      this._initState(b);
      if (this._hasLamp(b)) {
        b.lamp = this.scene.add.image(0, 0, 'lamp').setScale(0.55);
        this.lampLayer.add(b.lamp);
        b._lampStatus = null;
      }
    } else if (kind === 'remove') {
      if (b.lamp) b.lamp.destroy();
      if (b.noPowerIcon) b.noPowerIcon.destroy();
    }
    if (b.lamp) b.lamp.setPosition((b.x + b.w) * TILE - 13, b.y * TILE + 13);
    if (b.noPowerIcon) b.noPowerIcon.setPosition((b.x + b.w / 2) * TILE, (b.y + b.h / 2) * TILE);
    if (l === 'miner' && kind !== 'remove') this._computeOre(b);
    this.dirty = true;
  }

  _hasLamp(b) {
    const l = b.def.logic;
    return l === 'miner' || l === 'crafter' || l === 'generator' || l === 'plc';
  }

  _zeros() { return new Array(this.nItems).fill(0); }

  _initState(b) {
    if (b.pf === undefined) b.pf = 1;
    b.wants = false;
    b.status = 'idle';
    if (b.st) return; // 불러오기
    switch (b.def.logic) {
      case 'miner': b.st = { progress: 0, out: -1, ore: -1, frac: 0, outRR: 0 }; break;
      case 'crafter': {
        const first = b.def.selectRecipe ? Registry.recipesFor(b.def.machine)[0] : null;
        b.st = { recipe: first ? first.id : null, inv: this._zeros(), out: this._zeros(), outTotal: 0, progress: 0, crafting: false, outRR: 0 };
        break;
      }
      case 'storage': b.st = { inv: this._zeros(), total: 0, outRR: 0, rr: 0 }; break;
      case 'generator': b.st = { coal: 0, energy: 0, output: 0 }; break;
      case 'pole': b.st = { tripped: false }; break;
      case 'plc': b.st = { sensor: 0, cond: 'full', target: 0, action: 'off', state: false }; break;
      default: b.st = {};
    }
  }

  // 채굴기 아래 광석: 가장 많은 종류, 속도 = 광석 칸 비율
  _computeOre(b) {
    const counts = {};
    let total = 0;
    for (let j = 0; j < b.h; j++) for (let i = 0; i < b.w; i++) {
      total++;
      const o = this.scene.grid.oreAt(b.x + i, b.y + j);
      if (o) counts[o.item] = (counts[o.item] || 0) + 1;
    }
    let best = null;
    for (const k in counts) if (!best || counts[k] > counts[best]) best = k;
    b.st.ore = best ? Registry.itemIndex[best] : -1;
    b.st.frac = best ? counts[best] / total : 0;
  }

  rebuild() {
    this.list = [];
    this.scene.buildings.list.forEach(b => {
      const l = b.def.logic;
      if (l !== 'conveyor' && l !== 'splitter' && l !== 'merger') this.list.push(b);
    });
    this.dirty = false;
  }

  // ---------------------------------------------------------
  // 받기
  // ---------------------------------------------------------
  acceptCrafter(b, t, moveDir) {
    if (moveDir === (b.dir + 2) % 4) return false; // 출력면으로는 못 들어옴
    const st = b.st;
    const id = Registry.itemIds[t];
    let r = st.recipe ? Registry.recipes[st.recipe] : null;
    if (!r || r.in[id] === undefined) {
      if (b.def.selectRecipe) return false;
      // 제련소: 들어온 재료에 맞는 레시피로 자동 전환 (진행 중인 게 없을 때만)
      const nr = Registry.recipeByInput(b.def.machine, id);
      if (!nr) return false;
      if (r && (st.crafting || st.inv.some(v => v > 0))) return false;
      st.recipe = nr.id;
      r = nr;
    }
    const need = r.in[id];
    if (st.inv[t] >= Math.max(2, need * 2)) return false;
    st.inv[t]++;
    return true;
  }

  acceptStorage(b, t, moveDir) {
    if (moveDir === (b.dir + 2) % 4) return false;
    const st = b.st;
    if (st.total >= b.def.capacity) return false;
    st.inv[t]++;
    st.total++;
    return true;
  }

  acceptGenerator(b, t) {
    if (Registry.itemIds[t] !== b.def.fuel) return false;
    if (b.st.coal >= b.def.fuelCap) return false;
    b.st.coal++;
    return true;
  }

  // 조립기 레시피 변경 (재료 버퍼는 비움)
  setRecipe(b, recipeId) {
    const st = b.st;
    if (st.recipe === recipeId) return;
    st.recipe = recipeId;
    st.inv.fill(0);
    st.crafting = false;
    st.progress = 0;
  }

  // ---------------------------------------------------------
  // 시뮬레이션
  // ---------------------------------------------------------
  step(dt) {
    if (this.dirty) this.rebuild();
    const lg = this.scene.logistics;
    for (let k = 0; k < this.list.length; k++) {
      const b = this.list[k];
      const st = b.st;
      const on = Logistics.isOn(b);
      switch (b.def.logic) {
        case 'miner': {
          if (st.out >= 0 && lg.pushOut(b, st.out)) st.out = -1;
          if (!on) { b.wants = false; b.status = 'off'; break; }
          if (st.out >= 0 || st.ore < 0) { b.wants = false; b.status = st.ore < 0 ? 'idle' : 'blocked'; break; }
          b.wants = true;
          if (b.pf <= 0) { b.status = 'nopower'; break; }
          st.progress += dt * b.pf * st.frac / b.def.miningTime;
          if (st.progress >= 1) {
            st.progress -= 1;
            st.out = st.ore;
            if (lg.pushOut(b, st.out)) st.out = -1;
          }
          b.status = 'work';
          break;
        }
        case 'crafter': {
          // 완성품 하나씩 내보내기
          if (st.outTotal > 0) {
            for (let i = 0; i < this.nItems; i++) {
              if (st.out[i] > 0) {
                if (lg.pushOut(b, i)) { st.out[i]--; st.outTotal--; }
                break;
              }
            }
          }
          if (!on) { b.wants = false; b.status = 'off'; break; }
          const r = st.recipe ? Registry.recipes[st.recipe] : null;
          if (!r) { b.wants = false; b.status = 'idle'; break; }
          if (!st.crafting) {
            let ok = st.outTotal + r.outSum <= OUT_CAP;
            for (let i = 0; ok && i < r.inIdx.length; i++) if (st.inv[r.inIdx[i][0]] < r.inIdx[i][1]) ok = false;
            if (ok) {
              for (const [i, n] of r.inIdx) st.inv[i] -= n;
              st.crafting = true;
              st.progress = 0;
            }
          }
          if (st.crafting) {
            b.wants = true;
            if (b.pf <= 0) { b.status = 'nopower'; break; }
            st.progress += dt * b.pf / r.time;
            b.status = 'work';
            if (st.progress >= 1) {
              for (const [i, n] of r.outIdx) { st.out[i] += n; st.outTotal += n; }
              st.crafting = false;
              st.progress = 0;
            }
          } else {
            b.wants = false;
            b.status = st.outTotal + r.outSum > OUT_CAP ? 'blocked' : 'idle';
          }
          break;
        }
        case 'storage': {
          if (!on || st.total <= 0) break;
          // 종류를 번갈아 하나씩 내보내기
          for (let j = 0; j < this.nItems; j++) {
            const i = (st.rr + j) % this.nItems;
            if (st.inv[i] > 0) {
              if (lg.pushOut(b, i)) { st.inv[i]--; st.total--; st.rr = (i + 1) % this.nItems; }
              break;
            }
          }
          break;
        }
      }
    }
  }

  // 램프 색 갱신 (상태가 바뀐 경우만)
  updateLamps() {
    for (let k = 0; k < this.list.length; k++) {
      const b = this.list[k];
      if (!b.lamp) continue;
      const s = b.status || 'idle';
      if (s !== b._lampStatus) {
        b._lampStatus = s;
        b.lamp.setTint(STATUS_COLOR[s] || 0xffffff);
      }
    }
  }

  // 창고·가공기 등의 내용물 [[아이템id, 개수]] (정보 패널용)
  static invList(arr) {
    const out = [];
    if (!arr) return out;
    for (let i = 0; i < arr.length; i++) if (arr[i] > 0) out.push([Registry.itemIds[i], arr[i]]);
    return out;
  }
}
