// =============================================================
// conveyor.js — 컨베이어·분배기·합류기 (아이템 흐름)
//   - 아이템은 물리 객체가 아니라 "칸마다 가진 슬롯 배열"의 숫자로 관리한다.
//     컨베이어 한 칸 = items[] (앞쪽부터 정렬, 각 항목 {t:아이템번호, pos:0~1})
//   - 아이템 사이 최소 간격 ITEM_GAP(칸) → 한 칸에 최대 2~3개.
//   - 앞 칸이 받아주지 않으면 맨 앞 아이템이 pos=1에서 멈추고 뒤도 줄줄이 멈춘다.
//   - 화면 표시는 같은 텍스처의 이미지 풀(pool)을 재사용해서 한 번에 그리고(배치 렌더링),
//     카메라에 보이는 아이템 수만큼만 사용한다.
// =============================================================

const ITEM_GAP = 0.5;
// 합류기 입구 순서 (진행 방향 기준 0=뒤, 1=왼쪽, 3=오른쪽)
const MERGER_SIDES = [0, 1, 3];

class ConveyorSystem {
  constructor(scene) {
    this.scene = scene;
    this.belts = [];        // 컨베이어 (하류부터 상류 순서 = 업데이트 순서)
    this.nodes = [];        // 분배기·합류기
    this.dirty = true;
    this.speed = GameData.buildings.conveyor.speed || 2;
    this._free = [];        // 아이템 객체 재사용 풀 (가비지 줄이기)
    this.itemCount = 0;

    // 아이템 그리기: 64px 아이콘을 0.5배로 (확대해도 선명). 이미지 객체는 풀에서 재사용.
    this.itemLayer = scene.add.layer().setDepth(15);
    this.pool = [];
    this.visibleCount = 0;
  }

  // 건물 변경 통지 → 다음 프레임에 연결 정보 재계산
  onBuilding(kind, b) {
    if (kind === 'add') this._initState(b);
    this.dirty = true;
  }

  _initState(b) {
    if (b.st) return; // 불러오기에서 이미 채운 경우
    const l = b.def.logic;
    if (l === 'conveyor') b.st = { items: [], next: null, shape: 0 };
    else if (l === 'splitter') b.st = { buf: -1, rr: 0 };
    else if (l === 'merger') b.st = { buf: -1, rr: 0, req: 0, prevReq: 0 };
  }

  _newItem(t, pos) {
    const o = this._free.pop() || { t: 0, pos: 0 };
    o.t = t; o.pos = pos;
    return o;
  }

  // -----------------------------------------------------------
  // 연결 정보 재계산: 앞 칸 건물, 곡선 모양, 업데이트 순서
  // -----------------------------------------------------------
  rebuild() {
    const bm = this.scene.buildings;
    const belts = [], nodes = [];
    bm.list.forEach(b => {
      const l = b.def.logic;
      if (l === 'conveyor') belts.push(b);
      else if (l === 'splitter' || l === 'merger') nodes.push(b);
    });

    for (const b of belts) {
      b.st.next = bm.at(b.x + DX[b.dir], b.y + DY[b.dir]);
      // 곡선: 뒤에서 들어오는 컨베이어가 없고 한쪽 옆에서만 들어오면 곡선
      const back = this._feeds(b, (b.dir + 2) % 4);
      const left = this._feeds(b, (b.dir + 3) % 4);
      const right = this._feeds(b, (b.dir + 1) % 4);
      let shape = 0;
      if (!back && left && !right) shape = 1;
      else if (!back && right && !left) shape = 2;
      if (shape !== b.st.shape || !b._shapeSet) {
        b.st.shape = shape;
        b._shapeSet = true;
        b.sprite.setTexture(shape === 0 ? 'b_conveyor' : (shape === 1 ? 'b_conveyor_L' : 'b_conveyor_R'));
      }
    }

    // 하류 우선 순서 (앞 칸을 먼저 처리해야 빈자리가 바로 생겨 흐름이 매끄럽다)
    const order = [];
    const seen = new Set();
    for (const b of belts) {
      // 반복문으로 체인을 따라가며 아직 안 본 것들을 모아서 뒤집어 넣기 (재귀 깊이 문제 방지)
      if (seen.has(b.id)) continue;
      const chain = [];
      let cur = b;
      while (cur && cur.def.logic === 'conveyor' && !seen.has(cur.id)) {
        seen.add(cur.id);
        chain.push(cur);
        cur = cur.st.next;
      }
      for (let i = chain.length - 1; i >= 0; i--) order.push(chain[i]);
    }
    this.belts = order;
    this.nodes = nodes;
    this.dirty = false;
  }

  // 방향 d 쪽 이웃 컨베이어가 이 칸으로 들어오는가
  _feeds(b, d) {
    const n = this.scene.buildings.at(b.x + DX[d], b.y + DY[d]);
    return !!(n && n.def.logic === 'conveyor' && n.dir === (d + 2) % 4);
  }

  // -----------------------------------------------------------
  // 아이템 받기 (moveDir = 아이템이 움직이는 방향)
  // -----------------------------------------------------------
  accept(b, t, moveDir) {
    const st = b.st;
    const rel = (moveDir - b.dir + 4) % 4; // 0=뒤에서, 1=왼쪽에서, 3=오른쪽에서, 2=앞에서
    if (rel === 2) return false;
    const l = b.def.logic;

    if (l === 'conveyor') {
      const items = st.items;
      const fromCurveSide = (rel === 1 && st.shape === 1) || (rel === 3 && st.shape === 2);
      if ((rel === 0 && st.shape === 0) || fromCurveSide) {
        // 뒤(또는 곡선 입구)로 들어옴: pos 0
        if (items.length && items[items.length - 1].pos < ITEM_GAP) return false;
        items.push(this._newItem(t, 0));
        return true;
      }
      // 옆에서 끼어들기: 가운데(0.5)에 빈자리가 있어야
      let at = items.length;
      for (let i = 0; i < items.length; i++) {
        if (Math.abs(items[i].pos - 0.5) < ITEM_GAP * 0.9) return false;
        if (items[i].pos < 0.5 && at === items.length) at = i;
      }
      items.splice(at, 0, this._newItem(t, 0.5));
      return true;
    }

    if (l === 'splitter') {
      if (rel !== 0 || st.buf >= 0) return false;
      st.buf = t;
      return true;
    }

    if (l === 'merger') {
      const bit = 1 << rel;
      st.req |= bit;
      if (st.buf >= 0) return false;
      // 공정하게 돌아가며: 지금 기다리는 입구들 중 차례(rr)가 가장 앞선 입구만 받는다
      const cand = st.prevReq | bit;
      let chosen = rel;
      for (let k = 0; k < 3; k++) {
        const side = MERGER_SIDES[(st.rr + k) % 3];
        if (cand & (1 << side)) { chosen = side; break; }
      }
      if (chosen !== rel) return false;
      st.buf = t;
      st.rr = (MERGER_SIDES.indexOf(rel) + 1) % 3;
      return true;
    }
    return false;
  }

  // -----------------------------------------------------------
  // 시뮬레이션 한 스텝
  // -----------------------------------------------------------
  step(dt) {
    if (this.dirty) this.rebuild();
    const lg = this.scene.logistics;
    const d = this.speed * dt;
    let count = 0;

    for (let k = 0; k < this.belts.length; k++) {
      const b = this.belts[k];
      const items = b.st.items;
      const n = items.length;
      if (!n) continue;
      count += n;
      if (!Logistics.isOn(b)) continue;
      // 앞에서부터 이동 (앞 아이템과 간격 유지)
      let limit = 1;
      for (let i = 0; i < n; i++) {
        const it = items[i];
        if (it.pos < limit) it.pos = Math.min(it.pos + d, limit);
        limit = it.pos - ITEM_GAP;
      }
      // 맨 앞 아이템이 끝에 닿으면 앞 칸에 넘기기 시도
      const front = items[0];
      if (front.pos >= 1 && b.st.next && lg.accept(b.st.next, front.t, b.dir)) {
        items.shift();
        this._free.push(front);
        count--;
      }
    }

    for (const b of this.nodes) {
      const st = b.st;
      if (b.def.logic === 'merger') { st.prevReq = st.req; st.req = 0; }
      if (st.buf < 0) continue;
      count++;
      if (!Logistics.isOn(b)) continue;
      if (b.def.logic === 'splitter') {
        // 앞 → 왼쪽 → 오른쪽 순서로 번갈아, 막힌 쪽은 건너뜀
        const dirs = [b.dir, (b.dir + 3) % 4, (b.dir + 1) % 4];
        for (let j = 0; j < 3; j++) {
          const idx = (st.rr + j) % 3;
          const dd = dirs[idx];
          const tgt = this.scene.buildings.at(b.x + DX[dd], b.y + DY[dd]);
          if (tgt && lg.accept(tgt, st.buf, dd)) {
            st.buf = -1; st.rr = (idx + 1) % 3; count--;
            break;
          }
        }
      } else {
        const tgt = this.scene.buildings.at(b.x + DX[b.dir], b.y + DY[b.dir]);
        if (tgt && lg.accept(tgt, st.buf, b.dir)) { st.buf = -1; count--; }
      }
    }
    this.itemCount = count;
  }

  // 건물이 사라질 때 아이템 객체 반납
  release(b) {
    if (b.st && b.st.items) { for (const it of b.st.items) this._free.push(it); b.st.items.length = 0; }
  }

  // -----------------------------------------------------------
  // 그리기: 카메라에 보이는 아이템만 bob으로 표시
  // -----------------------------------------------------------
  render() {
    const view = this.scene.cameras.main.worldView;
    const x0 = view.x - TILE, y0 = view.y - TILE, x1 = view.right + TILE, y1 = view.bottom + TILE;
    const H = TILE / 2;
    const frames = Art.itemFrames;
    let used = 0;
    const put = (wx, wy, t) => {
      let img = this.pool[used];
      if (!img) {
        img = this.scene.add.image(0, 0, 'items', frames[t]).setScale(0.5);
        img._t = t;
        this.itemLayer.add(img);
        this.pool.push(img);
      } else {
        if (img._t !== t) { img.setFrame(frames[t]); img._t = t; }
        if (!img.visible) img.visible = true;
      }
      img.x = wx; img.y = wy;
      used++;
    };

    for (let k = 0; k < this.belts.length; k++) {
      const b = this.belts[k];
      const items = b.st.items;
      if (!items.length) continue;
      const cx = b.x * TILE + H, cy = b.y * TILE + H;
      if (cx < x0 || cx > x1 || cy < y0 || cy > y1) continue;
      const vx = DX[b.dir], vy = DY[b.dir];
      const shape = b.st.shape;
      for (let i = 0; i < items.length; i++) {
        const it = items[i];
        if (shape === 0) {
          put(cx + vx * (it.pos - 0.5) * TILE, cy + vy * (it.pos - 0.5) * TILE, it.t);
        } else {
          // 곡선: 옆 모서리 중앙 → 앞 모서리 중앙을 1/4 원호로
          const sd = shape === 1 ? (b.dir + 3) % 4 : (b.dir + 1) % 4;
          const sx = DX[sd], sy = DY[sd];
          const ccx = cx + (sx + vx) * H, ccy = cy + (sy + vy) * H;
          const th = it.pos * Math.PI / 2;
          const c = Math.cos(th), s = Math.sin(th);
          put(ccx + (-vx * c - sx * s) * H, ccy + (-vy * c - sy * s) * H, it.t);
        }
      }
    }
    for (const b of this.nodes) {
      if (b.st.buf < 0) continue;
      const cx = b.x * TILE + H, cy = b.y * TILE + H;
      if (cx < x0 || cx > x1 || cy < y0 || cy > y1) continue;
      put(cx, cy, b.st.buf);
    }
    for (let i = used; i < this.visibleCount; i++) this.pool[i].visible = false;
    this.visibleCount = used;
  }
}
