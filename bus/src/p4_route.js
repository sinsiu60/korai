// ================= traffic, routes, passengers, scoring =================
const CARS = [];
const CAR_COLS = [0xf2f2f2, 0x1e1e24, 0xb8bec6, 0xd02a20, 0x2a5aa8, 0x5a6a3a, 0xe8d8b0];
function mkCar(kind) {
  const g = new THREE.Group(), col = CAR_COLS[Math.floor(Math.random() * CAR_COLS.length)];
  if (kind === 'tractor') { // 경운기: the rural boss of slow traffic
    const b = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.9, 2.2), new THREE.MeshLambertMaterial({ color: 0x3a8a3a })); b.position.y = 1.0; g.add(b);
    const cart = new THREE.Mesh(new THREE.BoxGeometry(1.8, 0.7, 2.6), new THREE.MeshLambertMaterial({ color: 0x8a6a4a })); cart.position.set(0, 0.9, -2.6); g.add(cart);
    const hay = new THREE.Mesh(new THREE.BoxGeometry(1.6, 0.8, 2.2), new THREE.MeshLambertMaterial({ color: 0xe8c860 })); hay.position.set(0, 1.6, -2.6); g.add(hay);
    for (const z of [0.4, -2.6]) for (const s of [-1, 1]) { const w = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.5, 0.3, 12).rotateZ(Math.PI / 2), M.dark); w.position.set(s * 0.85, 0.5, z); g.add(w); }
    const man = person(0x3a5a8a); man.position.set(0, 1.2, 0.3); man.scale.setScalar(0.9); g.add(man);
  } else if (kind === 'truck') { // 1-ton truck
    const cab = new THREE.Mesh(new THREE.BoxGeometry(1.8, 1.6, 1.8), new THREE.MeshLambertMaterial({ color: 0x2a5aa8 })); cab.position.set(0, 1.3, 1.4); g.add(cab);
    const bed = new THREE.Mesh(new THREE.BoxGeometry(1.8, 0.5, 2.8), new THREE.MeshLambertMaterial({ color: 0x9aa4ac })); bed.position.set(0, 0.85, -0.9); g.add(bed);
    for (const z of [1.3, -1.4]) for (const s of [-1, 1]) { const w = new THREE.Mesh(new THREE.CylinderGeometry(0.36, 0.36, 0.3, 10).rotateZ(Math.PI / 2), M.dark); w.position.set(s * 0.85, 0.36, z); g.add(w); }
  } else {
    const b = new THREE.Mesh(new THREE.BoxGeometry(1.8, 0.75, 4.3), new THREE.MeshLambertMaterial({ color: col })); b.position.y = 0.7; b.castShadow = true; g.add(b);
    const c = new THREE.Mesh(new THREE.BoxGeometry(1.6, 0.6, 2.2), new THREE.MeshLambertMaterial({ color: 0x2a3a48 })); c.position.set(0, 1.35, -0.2); g.add(c);
    for (const z of [1.35, -1.35]) for (const s of [-1, 1]) { const w = new THREE.Mesh(new THREE.CylinderGeometry(0.34, 0.34, 0.25, 10).rotateZ(Math.PI / 2), M.dark); w.position.set(s * 0.85, 0.34, z); g.add(w); }
    const tl = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 0.15), new THREE.MeshBasicMaterial({ color: 0xa01a10 })); tl.position.set(0, 0.85, -2.16); tl.rotation.y = Math.PI; g.add(tl);
  }
  scene.add(g); return g;
}
function spawnTraffic() {
  for (const c of CARS) scene.remove(c.mesh); CARS.length = 0;
  const add = (road, s, dir, kind = 'car') => { const vmax = kind === 'tractor' ? 5 : kind === 'truck' ? 13 : 14 + Math.random() * 4; CARS.push({ road, s, dir, v: vmax, vmax, kind, mesh: mkCar(kind), pos: new THREE.Vector3(), rad: kind === 'tractor' ? 1.4 : 1.7, stunned: 0 }); };
  for (let i = 0; i < 9; i++) add('loop', (i / 9 + 0.04) * L, i % 2 ? 1 : -1, i === 4 ? 'truck' : 'car');
  add('loop', 0.55 * L, 1, 'tractor');
  for (let i = 0; i < 3; i++) add('branch', (0.2 + i * 0.3) * BR, i % 2 ? 1 : -1, i === 1 ? 'truck' : 'car');
}
function trafficTick(dt) {
  for (const c of CARS) {
    const road = ROADS[c.road], { p, t } = roadAt(road, c.s), lane = right(t).multiplyScalar(c.dir * 1.9);
    c.pos.copy(p).add(lane);
    // who's in front of me?
    let gap = 1e9;
    for (const o of CARS) { if (o === c || o.road !== c.road || o.dir !== c.dir) continue; let ds = (o.s - c.s) * c.dir; if (road.closed) ds = ((ds % road.len) + road.len) % road.len; if (ds > 0 && ds < gap) gap = ds; }
    const bn = BUS.near && BUS.near[c.road]; if (bn && bn.dist < 6) { const inLane = Math.sign(bn.lat) === c.dir || Math.abs(bn.lat) < 1.2; let ds = (bn.d - c.s) * c.dir; if (road.closed) ds = ((ds % road.len) + road.len + road.len / 2) % road.len - road.len / 2; if (inLane && ds > -2 && ds < gap) gap = ds - BUS.spec.len / 2; }
    if (c.road === 'loop' && XRAIL.closed) { let ds = (XING.d - c.s) * c.dir; ds = ((ds % L) + L) % L; if (ds > 0 && ds < 40) gap = Math.min(gap, ds - 8); }
    const want = c.stunned > 0 ? 0 : gap < 9 ? 0 : gap < 30 ? Math.min(c.vmax, (gap - 9) * 0.8) : c.vmax * (limitAt(c.road, c.s) < 40 ? 0.6 : 1);
    c.stunned = Math.max(0, c.stunned - dt);
    c.v += clamp(want - c.v, -6 * dt, 2.5 * dt);
    c.s += c.dir * c.v * dt;
    if (road.closed) c.s = ((c.s % road.len) + road.len) % road.len;
    else if (c.s > road.len - 5 || c.s < 2) { c.dir *= -1; c.s = clamp(c.s, 2, road.len - 5); } // turn around at the ends
    const h = t.clone().multiplyScalar(c.dir); c.mesh.position.copy(c.pos); c.mesh.rotation.y = Math.atan2(h.x, h.z);
  }
}
// ---- people (waiting at stops) ----
const PEOPLE_POOL = [];
function person(col) { const g = new THREE.Group(); const b = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.28, 1.0, 8), new THREE.MeshLambertMaterial({ color: col })); b.position.y = 0.85; g.add(b); const hd = new THREE.Mesh(new THREE.SphereGeometry(0.2, 10, 8), new THREE.MeshLambertMaterial({ color: 0xe8c0a0 })); hd.position.y = 1.55; g.add(hd); const lg = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.15, 0.4, 6), M.dark); lg.position.y = 0.2; g.add(lg); return g; }
// passenger types: weight, fare kind, boarding seconds, colour, lines
const PTYPES = [
  { k: '할머니', w: 30, pay: 'cash', sec: 3.4, col: 0x9a6aa8, on: ['기사 양반, 장에 가유.', '아이고 다리야. 천천히 가유.', '동전이 어딨더라…'], off: ['수고혀요, 기사 양반.'] },
  { k: '학생', w: 22, pay: 'card', sec: 0.9, col: 0x2a3a6a, on: ['카드 찍었어요!', '아 지각이다…'], off: ['감사합니다!'] },
  { k: '아저씨', w: 20, pay: 'card', sec: 1.1, col: 0x5a5a4a, on: ['에헴.', '날씨 좋구먼.'], off: ['수고하쇼.'] },
  { k: '닭 든 할아버지', w: 8, pay: 'cash', sec: 2.6, col: 0x8a5a34, on: ['닭은 요금 안 내도 되지? 꼬꼬댁!', '(닭 세 마리가 같이 탔다)'], off: ['꼬꼬댁! (닭도 내렸다)'], free: 1 },
  { k: '외지인', w: 10, pay: 'card', sec: 1.6, col: 0xd08a3a, on: ['여기가 구불군이에요? 와, 진짜 구불구불하네.', '이 버스 기차역 가요?'], off: ['사진 찍고 갈게요!'] },
  { k: '군수', w: 2, pay: 'card', sec: 1.2, col: 0x1e1e24, on: ['군수일세. 정시 운행 부탁하네.'], off: ['좋은 버스로군. 예산 좀 더 줘야겠어.'], vip: 1 },
];
function pickType(R) { const tot = PTYPES.reduce((a, b) => a + b.w, 0); let x = R() * tot; for (const t of PTYPES) { if ((x -= t.w) < 0) return t; } return PTYPES[0]; }

// ---- routes ----
const ringLen = () => ROADS.ring.len;
const ROUTES = [
  { id: 1, no: '1', name: '읍내 순환', desc: '터미널 → 시장 → 학교 → 저수지 → 보건지소 → 농협 → 터미널. 첫 노선.', segs: () => [['loop', 0, L + STOPS.term.d + 20]], stops: ['term', 'market', 'school', 'lake', 'clinic', 'coop', 'term'], price: 0, rep: 0, fare: 1450, subsidy: 260000, pax: [1, 4] },
  { id: 2, no: '2', name: '꼭대기리', desc: '읍내를 지나 산길로 꼭대기리 종점까지. 회전 교차로를 한 바퀴 돌아 내려온다.', segs: () => [['loop', 0, JUNCTION], ['branch', 0, BR], ['ring', ringLen(), 0], ['branch', BR, 0], ['loop', JUNCTION, L + STOPS.term.d + 20]], stops: ['term', 'market', 'school', 'persim', 'top', 'lake', 'clinic', 'coop', 'term'], price: 2000000, rep: 3, fare: 1650, subsidy: 380000, pax: [1, 4] },
  { id: 3, no: '장', name: '장날 특별', desc: '5일장 날. 할머니와 닭이 한가득. 현금 손님이 많아 정차가 길어진다.', segs: () => [['loop', 0, L + STOPS.term.d + 20]], stops: ['term', 'market', 'school', 'lake', 'clinic', 'coop', 'term'], price: 4000000, rep: 7, fare: 1450, subsidy: 330000, pax: [3, 8], market: 1 },
  { id: 4, no: '막', name: '막차', desc: '밤 10시 꼭대기리 막차. 전조등 필수. 졸린 승객, 어두운 산길.', segs: () => [['loop', 0, JUNCTION], ['branch', 0, BR], ['ring', ringLen(), 0], ['branch', BR, 0], ['loop', JUNCTION, L + STOPS.term.d + 20]], stops: ['term', 'market', 'school', 'persim', 'top', 'lake', 'clinic', 'coop', 'term'], price: 7000000, rep: 12, fare: 1850, subsidy: 520000, pax: [0, 3], night: 1 },
];
const RT = { route: null, pts: [], idx: 0, rd: 0, stops: [], k: 0, t: 0, state: 'idle' };
function buildRoute(route) {
  const pts = []; let acc = 0;
  for (const [rn, a, b] of route.segs()) {
    const road = ROADS[rn], dir = b >= a ? 1 : -1, n = Math.ceil(Math.abs(b - a) / 2);
    for (let i = 0; i <= n; i++) { const d = a + (b - a) * i / n, { p, t } = roadAt(road, d), h = t.clone().multiplyScalar(dir); if (pts.length) acc += Math.hypot(p.x - pts.at(-1).p.x, p.z - pts.at(-1).p.z); const lane = p.clone().addScaledVector(right(h), 1.9); pts.push({ p, lane, h, road: rn, d: road.closed ? ((d % road.len) + road.len) % road.len : d, rd: acc }); }
  }
  RT.pts = pts; RT.len = acc;
  // each stop: the route distance where it sits
  let from = 0; RT.stops = route.stops.map(key => { const s = STOPS[key]; let bi = -1, bd = 1e9; for (let i = from; i < pts.length; i++) { if (pts[i].road !== s.road) continue; const dd = Math.abs(pts[i].d - s.d); if (dd < bd) { bd = dd; bi = i; } if (bd < 4 && pts[i].rd - pts[bi].rd > 60) break; } from = bi + 5; return { key, name: s.name, rd: pts[bi].rd, i: bi, waiting: [], served: false, missed: false }; });
  // the timetable: about 29 km/h between stops plus dwell time
  let tt = 50; RT.stops.forEach((s, i) => { if (i) tt += (s.rd - RT.stops[i - 1].rd) / 8.0 + 25; s.sched = i ? tt : 50; });
}
function routeProject() {
  const pts = RT.pts; let best = 1e9, bi = RT.idx; const a = Math.max(0, RT.idx - 20), b = Math.min(pts.length - 1, RT.idx + 40);
  for (let i = a; i <= b; i++) { const p = pts[i].p, d = (p.x - BUS.pos.x) ** 2 + (p.z - BUS.pos.z) ** 2; if (d < best) { best = d; bi = i; } }
  RT.idx = bi; const P = pts[bi], h = P.h; RT.lat = (BUS.pos.x - P.p.x) * (-h.z) + (BUS.pos.z - P.p.z) * h.x; RT.off = Math.sqrt(best);
  RT.rd = P.rd + ((BUS.pos.x - P.p.x) * h.x + (BUS.pos.z - P.p.z) * h.z);
}
// ---- the trip state ----
const TRIP = {};
function startTrip(route) {
  const R = rngOf(Date.now() & 0xffff);
  Object.assign(TRIP, { route, t: 0, onboard: [], fares: 0, fines: [], comfort: 100, events: { brake: 0, accel: 0, curve: 0, crash: 0, speed: 0, offroad: 0, xing: 0, gate: 0, door: 0, skip: 0, early: 0, ind: 0, night: 0 }, delays: [], served: 0, vip: 0, done: false, bell: false, speedT: 0, offT: 0, xingMin: 99, crossedX: false, lastFine: {}, rain: route.night ? Math.random() < 0.3 : Math.random() < 0.25, departed: false, R, pcount: 0, freeRides: 0 });
  buildRoute(route); RT.idx = 0; RT.k = 0;
  // passengers appear at every stop but the last
  RT.stops.forEach((s, i) => { if (i === RT.stops.length - 1) return; const [a, b] = route.pax; let n = a + Math.floor(R() * (b - a + 1)); if (i === 0) n += 2; if (route.market && (s.key === 'market' || s.key === 'term')) n += 4;
    for (let j = 0; j < n; j++) { let ty = pickType(R); if (route.market && R() < 0.4) ty = PTYPES[R() < 0.6 ? 0 : 3]; if (route.night && ty.k === '학생') ty = PTYPES[2]; const dest = i + 1 + Math.floor(Math.pow(R(), 0.8) * (RT.stops.length - 1 - i)); s.waiting.push({ ty, dest: Math.min(dest, RT.stops.length - 1), mesh: null }); } });
  placeWaiting();
  const st = RT.stops[0], P = RT.pts[Math.max(0, st.i - 4)]; BUS.pos.copy(P.lane); BUS.yaw = Math.atan2(P.h.x, P.h.z); BUS.v = 0; BUS.steer = 0; BUS.gear = 'N'; BUS.park = true; BUS.engine = false; BUS.doorT.F = BUS.doorT.R = 0; BUS.doors.F = BUS.doors.R = 0; BUS.ind = 0; BUS.hazard = false; BUS.lights = !!route.night; BUS.wiper = false; BUS.damage = 0; BUS.odo = 0;
  drawSign(route.no, route.name);
  setNight(!!route.night); setRain(TRIP.rain);
  spawnTraffic(); RT.state = 'run';
}
function placeWaiting() {
  for (const m of PEOPLE_POOL) scene.remove(m); PEOPLE_POOL.length = 0;
  RT.stops.forEach(s => { const { p, t } = roadAt(ROADS[STOPS[s.key].road], STOPS[s.key].d), side = s.key === 'top' ? -1 : 1, rv = right(t).multiplyScalar(side), tt = t.clone().multiplyScalar(side);
    s.waiting.forEach((w, j) => { const m = person(w.ty.col); m.position.copy(p).addScaledVector(rv, 5.6 + (j % 2) * 0.7).addScaledVector(tt, -2.5 + Math.floor(j / 2) * 0.9); m.rotation.y = Math.atan2(-rv.x, -rv.z); scene.add(m); PEOPLE_POOL.push(m); w.mesh = m; }); });
}
function fine(key, amt, label, cool = 8) {
  const now = TRIP.t; if (TRIP.lastFine[key] && now - TRIP.lastFine[key] < cool) return false; TRIP.lastFine[key] = now;
  TRIP.fines.push({ label, amt }); toast(`${label} ${amt ? won(-amt) : ''}`, 'bad'); audio.bad(); return true;
}
function comfortHit(n, label) { TRIP.comfort = Math.max(0, TRIP.comfort - n); toast(label, 'bad'); if (TRIP.onboard.length && Math.random() < 0.5) { const p = TRIP.onboard[Math.floor(Math.random() * TRIP.onboard.length)]; chat(p.ty.k, ['아이고! 살살 좀 가유!', '으악 내 닭!', '기사님 롤러코스터예요?', '커피 쏟았잖아요!'][Math.floor(Math.random() * 4)]); } }
function tripTick(dt) {
  if (RT.state !== 'run') return; TRIP.t += dt; routeProject();
  const kmh = Math.abs(BUS.v) * 3.6;
  // comfort: hard braking, launching, cornering (only counts with people aboard)
  if (TRIP.onboard.length) {
    if (BUS.a < -3.8 && BUS.v > 2 && !TRIP.lastFine.brk) { TRIP.lastFine.brk = 1; TRIP.events.brake++; comfortHit(4, '급정거!'); setTimeout(() => TRIP.lastFine.brk = 0, 1500); }
    if (BUS.a > 2.2 && !TRIP.lastFine.acc) { TRIP.lastFine.acc = 1; TRIP.events.accel++; comfortHit(2, '급출발!'); setTimeout(() => TRIP.lastFine.acc = 0, 2000); }
    if (Math.abs(BUS.alat) > 3.0 && !TRIP.lastFine.crv) { TRIP.lastFine.crv = 1; TRIP.events.curve++; comfortHit(3, '급커브! 승객이 쏠렸다'); setTimeout(() => TRIP.lastFine.crv = 0, 2000); }
  }
  // speed limits
  const where = currentRoad(), lim = where ? limitAt(where.road, where.d) : 30; TRIP.lim = lim;
  if (kmh > lim + 6) { TRIP.speedT += dt; if (TRIP.speedT > 2 && fine('speed', lim === 30 ? 60000 : 30000, `과속 (${Math.round(kmh)}/${lim})`, 10)) TRIP.events.speed++; } else TRIP.speedT = 0;
  // off the road
  if (BUS.offroad && kmh > 3) { TRIP.offT += dt; if (TRIP.offT > 1.5 && fine('off', 10000, '도로 이탈', 12)) TRIP.events.offroad++; } else TRIP.offT = 0;
  if (EV.crash) { const hit = EV.crash; EV.crash = 0; BUS.damage += hit * 40000; audio.crash(); if (fine('crash', 50000, '충돌!', 3)) { TRIP.events.crash++; TRIP.comfort = Math.max(0, TRIP.comfort - 10); center('쾅!', 1, '#ff4b3a'); } }
  // the level crossing: stop before it, never go under a closed gate
  if (where && where.road === 'loop') { const dx = where.d - XING.d, before = BUS.near.loop.lat * 1 >= -6; const ds = ((XING.d - where.d) % L + L) % L;
    if (ds < 35 && ds > 2) TRIP.xingMin = Math.min(TRIP.xingMin, kmh);
    if (ds < 2 || ds > L - 4) { if (!TRIP.crossedX) { TRIP.crossedX = true; if (XRAIL.closed) { fine('gate', 150000, '차단기 무시!! 큰일 날 뻔했다', 30); TRIP.events.gate++; center('차단기!!', 2, '#ff4b3a'); } else if (TRIP.xingMin > 1.5) { fine('xing', 30000, '철길 건널목 일시정지 위반', 30); TRIP.events.xing++; } } }
    else if (ds > 40 && ds < L - 40) { TRIP.crossedX = false; TRIP.xingMin = 99; } }
  // night: headlights
  if (TRIP.route.night && !BUS.lights && kmh > 5) { if (fine('night', 20000, '전조등 미점등', 15)) TRIP.events.night++; }
  // off route?
  if (RT.off > 25) { if (!TRIP.lostT) { TRIP.lostT = 1; toast('노선을 벗어났다! 미니맵의 초록 선을 따라가자', 'bad'); } } else TRIP.lostT = 0;
  stopLogic(dt);
  if (!nextStop() && RT.state === 'run') finishTrip();
}
function currentRoad() { if (!BUS.near) return null; let best = null; for (const [rn, n] of Object.entries(BUS.near)) if (n.dist < 6 && (!best || n.dist < best.dist)) best = { road: rn, d: n.d, dist: n.dist }; return best; }
// ---- stops: docking, doors, boarding, bells ----
const SV = { phase: 'drive', busy: 0, docked: false, arrive: 0 };
function nextStop() { return RT.stops[RT.k]; }
function stopLogic(dt) {
  const s = nextStop(); if (!s) return;
  const dRel = RT.rd - s.rd, kmh = Math.abs(BUS.v) * 3.6, doorsOpen = BUS.doors.F > 0.5 || BUS.doors.R > 0.5, anyOpen = BUS.doorT.F || BUS.doorT.R;
  const alight = TRIP.onboard.filter(p => p.dest === RT.k), demand = s.waiting.length > 0 || alight.length > 0;
  // the bell rings once on the approach
  if (alight.length && !TRIP.bell && dRel > -260 && dRel < -20) { TRIP.bell = true; audio.bell(); chat(alight[0].ty.k, '삐― (하차벨)'); }
  const docked = Math.abs(dRel) < 9 && RT.lat > 0.3 && RT.lat < 4.2 && kmh < 1;
  if (docked && !SV.docked) { SV.docked = true; SV.arrive = TRIP.t; }
  if (!SV.docked && doorsOpen && kmh < 1 && Math.abs(dRel) < 14 && !TRIP.lastFine.bayHint) { TRIP.lastFine.bayHint = 1; toast(Math.abs(dRel) >= 9 ? '정류장 표지판에 더 가까이 대야 한다' : '노란 칸(길 오른쪽)에 붙여 대야 승객이 탄다', 'bad'); }
  if (!doorsOpen) TRIP.lastFine.bayHint = 0;
  if (!docked && kmh > 3) SV.docked = SV.docked && Math.abs(dRel) < 9;
  // doors opened away from a stop
  if (anyOpen && !(Math.abs(dRel) < 14) && kmh < 1 && !TRIP.lastFine.doorAt) { TRIP.lastFine.doorAt = 1; fine('door', 0, '정류장이 아닌 곳에서 문 열기 (승객 항의)', 20); TRIP.comfort = Math.max(0, TRIP.comfort - 3); TRIP.events.door++; }
  if (!anyOpen) TRIP.lastFine.doorAt = 0;
  if (SV.docked && doorsOpen) {
    if (!s.arrived) { s.arrived = TRIP.t; const late = s.arrived - s.sched; TRIP.delays.push(Math.max(0, late)); }
    SV.busy -= dt;
    if (SV.busy <= 0) {
      // people get off first (through the rear door when there is one)
      const rearOk = BUS.spec.doors.includes('R') ? BUS.doors.R > 0.5 : BUS.doors.F > 0.5;
      if (alight.length && (rearOk || BUS.doors.F > 0.5)) {
        const p = alight[0]; TRIP.onboard.splice(TRIP.onboard.indexOf(p), 1); SV.busy = rearOk ? 0.8 : 1.6; if (!rearOk && !TRIP.lastFine.rear) { TRIP.lastFine.rear = 1; chat(p.ty.k, '기사님, 뒷문 좀 열어 줘요!'); TRIP.comfort -= 1; }
        if (Math.random() < 0.35) chat(p.ty.k, p.ty.off[Math.floor(Math.random() * p.ty.off.length)]);
        if (p.ty.vip) TRIP.vip += TRIP.delays.at(-1) < 60 ? 1 : -1;
      } else if (s.waiting.length && BUS.doors.F > 0.5 && TRIP.onboard.length < BUS.spec.cap && RT.k < RT.stops.length - 1) {
        const p = s.waiting.shift(); if (p.mesh) scene.remove(p.mesh); TRIP.onboard.push(p); SV.busy = p.ty.sec * (BUS.kneel > 0.5 ? 0.7 : 1);
        if (p.ty.free) { TRIP.freeRides++; TRIP.fares += TRIP.route.fare; audio.coin(); } else { TRIP.fares += TRIP.route.fare; p.ty.pay === 'cash' ? audio.coin() : audio.card(); }
        TRIP.pcount++; if (Math.random() < 0.45 || p.ty.vip) chat(p.ty.k, p.ty.on[Math.floor(Math.random() * p.ty.on.length)]);
      } else if (!s.ready) { s.ready = true; if (RT.k === RT.stops.length - 1) finishTrip(); else { toast('승하차 끝! 문 닫고 출발', 'good'); audio.good(); } }
    }
  }
  // leaving: doors closed and rolling away from the stop
  if (!s.served && s.arrived && !anyOpen && dRel > 12) {
    s.served = true; TRIP.served++;
    if (TRIP.t < s.sched - 30 && RT.k > 0) { fine('early', 20000, '시간표보다 일찍 출발', 1); TRIP.events.early++; }
    if (BUS.ind === -1 || TRIP.indLeftT && TRIP.t - TRIP.indLeftT < 6) { toast('출발할 때 깜빡이 👍', 'good'); TRIP.events.ind++; } else { TRIP.comfort = Math.max(0, TRIP.comfort - 1); toast('출발할 때 왼쪽 깜빡이를 켜자 (Q)', ''); }
    advanceStop();
  } else if (!s.arrived && dRel > 18) {
    // drove past
    if (demand) { s.missed = true; fine('skip', 0, `무정차 통과: ${s.name}`, 1); TRIP.events.skip++; TRIP.comfort = Math.max(0, TRIP.comfort - 8); for (const p of alight) { p.dest = Math.min(p.dest + 1, RT.stops.length - 1); chat(p.ty.k, '거기서 내려야 했는데!!'); } for (const w of s.waiting) if (w.mesh) w.mesh.rotation.y += Math.PI; }
    advanceStop();
  }
}
function advanceStop() {
  RT.k++; SV.docked = false; SV.busy = 0; TRIP.bell = false; const s = nextStop(); if (!s) return;
  const after = RT.stops[RT.k + 1]; audio.chime(); banner(`이번 정류장은 ${s.name}입니다.${after ? ` 다음은 ${after.name}.` : ' 종점입니다.'} 예.`, 6);
}
function finishTrip() {
  RT.state = 'done'; TRIP.done = true; BUS.doorT.F = BUS.doorT.R = 1;
  setTimeout(() => showReport(), TEST ? 10 : 1200);
}
function tripScore() {
  const T = TRIP, avgLate = T.delays.length ? T.delays.reduce((a, b) => a + b, 0) / T.delays.length : 0;
  const punct = clamp(100 - Math.max(0, avgLate - 45) * 0.6, 0, 100), safety = clamp(100 - T.events.speed * 8 - T.events.crash * 20 - T.events.gate * 40 - T.events.xing * 15 - T.events.offroad * 6 - T.events.night * 6, 0, 100);
  const service = clamp(100 - T.events.skip * 25 - T.events.door * 8 - T.events.early * 10, 0, 100), comfort = T.comfort;
  const total = punct * 0.3 + safety * 0.3 + service * 0.2 + comfort * 0.2, stars = total >= 90 ? 5 : total >= 78 ? 4 : total >= 62 ? 3 : total >= 45 ? 2 : 1;
  return { avgLate, punct, safety, service, comfort, total, stars };
}
