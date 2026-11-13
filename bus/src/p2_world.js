// ================= world: the county of 구불군 =================
const renderer = new THREE.WebGLRenderer({ canvas: $('#gl'), antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75)); renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.outputColorSpace = THREE.SRGBColorSpace; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05;
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(62, 1, 0.08, 3200);
const mirrorCam = new THREE.PerspectiveCamera(32, 1.2, 0.5, 400);
scene.fog = new THREE.Fog(0xbfd8e6, 120, 1500);
const hemi = new THREE.HemisphereLight(0xdff2ff, 0x5a7a3a, 0.85); scene.add(hemi);
const sun = new THREE.DirectionalLight(0xfff2dc, 2.1); sun.position.set(120, 200, 80); sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048); Object.assign(sun.shadow.camera, { left: -60, right: 60, top: 60, bottom: -60, near: 10, far: 500 }); sun.shadow.bias = -0.0006; sun.shadow.normalBias = 0.04;
scene.add(sun); scene.add(sun.target);
function resize() { const w = innerWidth, h = innerHeight; renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); const rc = $('#rainfx'); rc.width = w; rc.height = h; }
addEventListener('resize', resize);

// ---- canvas textures ----
function canvasTex(w, h, draw, rep) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const g = c.getContext('2d'); draw(g, w, h);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; if (rep) { t.wrapS = t.wrapT = THREE.RepeatWrapping; } return t;
}
function speckle(g, w, h, n, cols, sz = 2) { for (let i = 0; i < n; i++) { g.fillStyle = cols[i % cols.length]; g.fillRect(Math.random() * w, Math.random() * h, sz, sz); } }
const TEX = {
  grass: canvasTex(256, 256, (g, w, h) => { g.fillStyle = '#6aa046'; g.fillRect(0, 0, w, h); speckle(g, w, h, 5000, ['#5e9440', '#77ad52', '#62983f', '#86b85e'], 2); }, true),
  road: canvasTex(256, 512, (g, w, h) => {
    g.fillStyle = '#4a4d50'; g.fillRect(0, 0, w, h); speckle(g, w, h, 9000, ['#3f4245', '#55585b', '#5e6164', '#45484a'], 2);
    g.fillStyle = '#6a6458'; g.fillRect(0, 0, 10, h); g.fillRect(w - 10, 0, 10, h);
    g.fillStyle = '#e8e8e0'; g.fillRect(12, 0, 6, h); g.fillRect(w - 18, 0, 6, h);
    g.fillStyle = '#f2b81e'; g.fillRect(w / 2 - 7, 0, 5, h); g.fillRect(w / 2 + 2, 0, 5, h);
  }, true),
  school: canvasTex(256, 512, (g, w, h) => {
    g.fillStyle = '#9a3a32'; g.fillRect(0, 0, w, h); speckle(g, w, h, 6000, ['#8a322a', '#a8443a'], 2);
    g.fillStyle = '#e8e8e0'; g.fillRect(12, 0, 6, h); g.fillRect(w - 18, 0, 6, h); g.fillStyle = '#f2b81e'; g.fillRect(w / 2 - 7, 0, 5, h); g.fillRect(w / 2 + 2, 0, 5, h);
    g.save(); g.translate(w * 0.75, h / 2); g.rotate(Math.PI); g.fillStyle = '#f4f0e0'; g.font = 'bold 54px sans-serif'; g.textAlign = 'center'; g.fillText('어린이', 0, -20); g.fillText('보호구역', 0, 50); g.restore();
  }, true),
  dirt: canvasTex(128, 128, (g, w, h) => { g.fillStyle = '#9a8460'; g.fillRect(0, 0, w, h); speckle(g, w, h, 1600, ['#8a7452', '#a8926c'], 2); }, true),
  yard: canvasTex(256, 256, (g, w, h) => { g.fillStyle = '#56595c'; g.fillRect(0, 0, w, h); speckle(g, w, h, 6000, ['#4c4f52', '#626568'], 2); g.strokeStyle = '#e8e8e0'; g.lineWidth = 4; for (let i = 0; i < 5; i++) { g.beginPath(); g.moveTo(20 + i * 50, 30); g.lineTo(20 + i * 50, 140); g.stroke(); } }, true),
  paddy: canvasTex(128, 128, (g, w, h) => { g.fillStyle = '#7aa858'; g.fillRect(0, 0, w, h); for (let y = 4; y < h; y += 10) for (let x = 3; x < w; x += 8) { g.fillStyle = '#5c8a3c'; g.fillRect(x, y, 3, 5); } g.fillStyle = 'rgba(120,170,200,.35)'; g.fillRect(0, 0, w, h); }, true),
  rail: canvasTex(64, 256, (g, w, h) => { g.fillStyle = '#7a7268'; g.fillRect(0, 0, w, h); speckle(g, w, h, 800, ['#6a6258', '#8a8278'], 2); for (let y = 0; y < h; y += 16) { g.fillStyle = '#5a4030'; g.fillRect(4, y, w - 8, 7); } g.fillStyle = '#9aa0a4'; g.fillRect(16, 0, 4, h); g.fillRect(w - 20, 0, 4, h); }, true),
};
TEX.grass.repeat.set(300, 300);
function textTex(lines, o = {}) {
  const w = o.w || 512, h = o.h || 256;
  return canvasTex(w, h, (g) => {
    g.fillStyle = o.bg || '#1e5a8a'; g.fillRect(0, 0, w, h); if (o.border) { g.strokeStyle = o.border; g.lineWidth = 12; g.strokeRect(6, 6, w - 12, h - 12); }
    g.fillStyle = o.fg || '#fff'; g.textAlign = 'center'; g.textBaseline = 'middle';
    lines.forEach((l, i) => { g.font = `${i ? 700 : 800} ${i ? (o.s2 || 44) : (o.s1 || 72)}px "Nanum Gothic", sans-serif`; g.fillText(l, w / 2, h / 2 + (i - (lines.length - 1) / 2) * (o.gap || 84)); });
  });
}
const M = {
  grass: new THREE.MeshLambertMaterial({ map: TEX.grass }),
  road: new THREE.MeshLambertMaterial({ map: TEX.road }),
  school: new THREE.MeshLambertMaterial({ map: TEX.school }),
  dirt: new THREE.MeshLambertMaterial({ map: TEX.dirt }),
  yard: new THREE.MeshLambertMaterial({ map: TEX.yard }),
  paddy: new THREE.MeshLambertMaterial({ map: TEX.paddy }),
  rail: new THREE.MeshLambertMaterial({ map: TEX.rail }),
  wood: new THREE.MeshLambertMaterial({ color: 0x7a5a3a }),
  pole: new THREE.MeshLambertMaterial({ color: 0x8a7a68 }),
  metal: new THREE.MeshLambertMaterial({ color: 0x9aa4ac }),
  dark: new THREE.MeshLambertMaterial({ color: 0x24282c }),
  glass: new THREE.MeshLambertMaterial({ color: 0x2a3a48, emissive: 0x000000 }),
  win: new THREE.MeshLambertMaterial({ color: 0x3a4c58, emissive: 0x000000 }),
  lamp: new THREE.MeshBasicMaterial({ color: 0x555555 }),
};

// ---- the road network: one loop around the 읍, a branch up to 꼭대기리 ----
const LOOP_PTS = [[0, 0], [200, 0], [360, 40], [460, 160], [480, 320], [420, 470], [300, 560], [120, 600], [-60, 560], [-200, 470], [-280, 320], [-260, 160], [-160, 40]];
const BRANCH_PTS = [[480, 320], [560, 318], [640, 290], [720, 300], [800, 352], [860, 430], [880, 520], [860, 600]];
const v3 = (x, z) => new THREE.Vector3(x, 0, z);
const ROADS = {};
function mkRoad(name, pts, closed, w = 8) {
  const curve = new THREE.CatmullRomCurve3(pts.map(p => v3(p[0], p[1])), closed, 'centripetal');
  const len = curve.getLength(), n = Math.ceil(len / 2), P = [], T = [], D = [];
  for (let i = 0; i <= n; i++) { const u = i / n; P.push(curve.getPointAt(u)); T.push(curve.getTangentAt(u)); D.push(u * len); }
  return ROADS[name] = { name, curve, len, P, T, D, closed, w };
}
mkRoad('loop', LOOP_PTS, true); mkRoad('branch', BRANCH_PTS, false);
// a small roundabout at the end of the branch
(() => { const e = BRANCH_PTS.at(-1), c = [e[0] - 4, e[1] + 22], r = 22, pts = []; for (let i = 0; i < 16; i++) { const a = -Math.PI / 2 + i / 16 * TAU; pts.push([c[0] + Math.cos(a) * r, c[1] + Math.sin(a) * r]); } ROADS.ring = null; mkRoad('ring', pts, true); ROADS.ring.center = c; })();
const right = t => new THREE.Vector3(-t.z, 0, t.x);
function roadAt(road, d) { // position and tangent at distance d
  const L = road.len; d = road.closed ? ((d % L) + L) % L : clamp(d, 0, L); const i = Math.min(road.P.length - 2, Math.floor(d / L * (road.P.length - 1))), k = (d - road.D[i]) / ((road.D[i + 1] - road.D[i]) || 1);
  return { p: road.P[i].clone().lerp(road.P[i + 1], k), t: road.T[i].clone().lerp(road.T[i + 1], k).normalize() };
}
function nearestOn(road, x, z, hint = -1, span = 60) {
  let best = 1e9, bi = 0; const n = road.P.length; let a = 0, b = n - 1;
  if (hint >= 0) { a = hint - span; b = hint + span; }
  for (let j = a; j <= b; j++) { const i = road.closed ? ((j % n) + n) % n : clamp(j, 0, n - 1); const p = road.P[i], dd = (p.x - x) ** 2 + (p.z - z) ** 2; if (dd < best) { best = dd; bi = i; } }
  const p = road.P[bi], t = road.T[bi], lat = (x - p.x) * (-t.z) + (z - p.z) * t.x;
  return { i: bi, d: road.D[bi], dist: Math.sqrt(best), lat };
}
function onAnyRoad(x, z) { let m = 1e9; for (const r of Object.values(ROADS)) m = Math.min(m, nearestOn(r, x, z).dist - r.w / 2); return m; }
const roadMeshes = [];
function buildRoadMesh(road, y, mat, from = 0, to = road.len) {
  const pos = [], uv = [], idx = []; const step = 2, n = Math.ceil((to - from) / step);
  for (let i = 0; i <= n; i++) { const d = Math.min(to, from + i * step), { p, t } = roadAt(road, d), r = right(t).multiplyScalar(road.w / 2);
    pos.push(p.x - r.x, y, p.z - r.z, p.x + r.x, y, p.z + r.z); uv.push(0, d / 8, 1, d / 8); if (i) { const a = (i - 1) * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); } }
  const geo = new THREE.BufferGeometry(); geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2)); geo.setIndex(idx); geo.computeVertexNormals();
  const m = new THREE.Mesh(geo, mat); m.receiveShadow = true; scene.add(m); roadMeshes.push(m); return m;
}
// ---- speed zones, stops and landmarks on the loop (distances along the road) ----
const L = ROADS.loop.len, BR = ROADS.branch.len;
const SCHOOL = [0.205 * L, 0.29 * L];
const ZONES = [
  { road: 'loop', a: 0, b: 0.13 * L, lim: 50, name: '읍내' }, { road: 'loop', a: SCHOOL[0], b: SCHOOL[1], lim: 30, name: '어린이 보호구역' },
  { road: 'loop', a: 0.93 * L, b: L, lim: 50, name: '읍내' }, { road: 'branch', a: 0, b: BR, lim: 50, name: '산길' }, { road: 'ring', a: 0, b: ROADS.ring.len, lim: 30, name: '회전 교차로' },
];
function limitAt(roadName, d) { for (const z of ZONES) if (z.road === roadName && d >= z.a && d <= z.b) return z.lim; return 60; }
// stops: road, distance, name. Routes pick from these.
const STOPS = {
  term: { road: 'loop', d: 0.012 * L, name: '구불 터미널' },
  market: { road: 'loop', d: 0.1 * L, name: '읍내 시장' },
  school: { road: 'loop', d: 0.245 * L, name: '구불초등학교' },
  lake: { road: 'loop', d: 0.44 * L, name: '저수지 앞' },
  clinic: { road: 'loop', d: 0.6 * L, name: '구불 보건지소' },
  coop: { road: 'loop', d: 0.86 * L, name: '구불 농협' },
  persim: { road: 'branch', d: 0.42 * BR, name: '감나무골' },
  top: { road: 'ring', d: 0.5 * ROADS.ring.len, name: '꼭대기리 종점' },
};
// the junction distance on the loop
const JUNCTION = nearestOn(ROADS.loop, BRANCH_PTS[0][0], BRANCH_PTS[0][1]).d;
// the railway crossing on the loop
const XING = { d: 0.72 * L };

// ---- static colliders (circles and boxes) and the grid that finds them ----
const COL = []; // {x,z,r} or {x,z,hw,hd,rot}
function addCircle(x, z, r) { COL.push({ x, z, r }); }
function addBox(x, z, hw, hd, rot) { COL.push({ x, z, hw, hd, rot, r: Math.hypot(hw, hd) }); }
function nearRoad(x, z, m) { return onAnyRoad(x, z) < m; }

function buildWorld() {
  const R = rngOf(1234);
  // ground, far hills, sky
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(6000, 6000), M.grass); ground.rotation.x = -Math.PI / 2; ground.receiveShadow = true; scene.add(ground);
  const sky = new THREE.Mesh(new THREE.SphereGeometry(3000, 24, 12), new THREE.ShaderMaterial({ side: THREE.BackSide, depthWrite: false, fog: false, uniforms: { top: { value: new THREE.Color(0x6aa8e0) }, bot: { value: new THREE.Color(0xd8ecf4) } }, vertexShader: 'varying vec3 vp; void main(){ vp = position; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }', fragmentShader: 'uniform vec3 top; uniform vec3 bot; varying vec3 vp; void main(){ float h = clamp(vp.y/1200.0,0.0,1.0); gl_FragColor = vec4(mix(bot, top, pow(h,0.6)),1.0); }' }));
  scene.add(sky); WORLD.sky = sky;
  const hillMat = new THREE.MeshLambertMaterial({ color: 0x4f7a48, flatShading: true });
  for (let i = 0; i < 46; i++) { const a = i / 46 * TAU + R() * 0.1, rr = 1250 + R() * 500, h = 140 + R() * 260; const m = new THREE.Mesh(new THREE.ConeGeometry(220 + R() * 200, h, 7), hillMat); m.position.set(Math.cos(a) * rr + 300, h / 2 - 10, Math.sin(a) * rr + 300); m.rotation.y = R() * 3; scene.add(m); }
  // roads
  buildRoadMesh(ROADS.loop, 0.03, M.road, 0, SCHOOL[0]); buildRoadMesh(ROADS.loop, 0.03, M.school, SCHOOL[0], SCHOOL[1]); buildRoadMesh(ROADS.loop, 0.03, M.road, SCHOOL[1], L);
  buildRoadMesh(ROADS.branch, 0.045, M.road); buildRoadMesh(ROADS.ring, 0.06, M.road);
  const isle = new THREE.Mesh(new THREE.CylinderGeometry(15, 15, 0.4, 32), new THREE.MeshLambertMaterial({ color: 0x5a9a40 })); isle.position.set(ROADS.ring.center[0], 0.2, ROADS.ring.center[1]); scene.add(isle); addCircle(ROADS.ring.center[0], ROADS.ring.center[1], 15.5);
  const tree0 = mkTreeMesh(0x4a8a3a); tree0.position.set(ROADS.ring.center[0], 0, ROADS.ring.center[1]); tree0.scale.setScalar(2.2); scene.add(tree0);
  buildRail();
  // the terminal yard and building
  const st = roadAt(ROADS.loop, STOPS.term.d), rv = right(st.t), yard = new THREE.Mesh(new THREE.PlaneGeometry(60, 34), M.yard); yard.rotation.x = -Math.PI / 2; yard.rotation.z = -Math.atan2(st.t.z, st.t.x);
  yard.position.copy(st.p).addScaledVector(rv, 22); yard.position.y = 0.02; yard.receiveShadow = true; scene.add(yard);
  building(st.p.clone().addScaledVector(rv, 44).addScaledVector(st.t, 4), rv.clone().negate(), 30, 12, 9, 0xe8e0cc, ['구불 버스터미널', '현태운수'], 0x2fa86a);
  // landmarks next to each stop
  const LM = { market: [['읍내 시장', '장날: 매달 5일'], 0xf0c040, 26, 10, 6], school: [['구불초등학교', ''], 0xe8d8b0, 34, 14, 10], lake: null, clinic: [['구불 보건지소', ''], 0xf4f4f0, 18, 10, 7], coop: [['구불 농협', '하나로마트'], 0xf4e8c8, 26, 12, 7] };
  for (const [k, s] of Object.entries(STOPS)) {
    const { p, t } = roadAt(ROADS[s.road], s.d), rv2 = right(t);
    stopProps(p, k === 'top' ? t.clone().negate() : t, s.name);
    const lm = LM[k]; if (lm) building(p.clone().addScaledVector(rv2, 18 + lm[3] / 2).addScaledVector(t, 10), rv2.clone().negate(), lm[2], lm[3], lm[4], lm[1], lm[0], 0x1e5a8a);
  }
  // the reservoir by 저수지 앞
  { const { p, t } = roadAt(ROADS.loop, STOPS.lake.d), rv2 = right(t); const water = new THREE.Mesh(new THREE.CircleGeometry(70, 40), new THREE.MeshLambertMaterial({ color: 0x4a88b0 })); water.rotation.x = -Math.PI / 2; water.position.copy(p).addScaledVector(rv2, 95); water.position.y = 0.05; scene.add(water); WORLD.lake = water; addCircle(water.position.x, water.position.z, 70); }
  // town: shops along the first and last stretch of the loop
  const SHOPS = [['다방', 0xe8b0a0], ['철물점', 0xb8c8d8], ['구불 마트', 0xf0e0a0], ['미용실', 0xe8c8e8], ['중국집 「2인분」', 0xf0a080], ['방앗간', 0xd8c8a8], ['약국', 0xc8e8c8], ['치킨', 0xf0d080], ['문방구', 0xc8d8f0], ['우체국', 0xf0c0a0]];
  let si = 0;
  for (const [a, b] of [[0.035, 0.12], [0.9, 0.995]]) for (let d = a * L; d < b * L; d += 22) for (const side of [-1, 1]) {
    const { p, t } = roadAt(ROADS.loop, d), off = right(t).multiplyScalar(side * (15 + R() * 4)), q = p.clone().add(off);
    if (onAnyRoad(q.x, q.z) < 9 || near(q, 14)) continue; const sh = SHOPS[si++ % SHOPS.length];
    building(q, right(t).multiplyScalar(-side), 14 + R() * 4, 9, 5 + R() * 5, sh[1], [sh[0]], 0x24282c);
  }
  // villages: little houses with tiled roofs
  const VIL = [[0.5 * L, 'loop', 7], [0.64 * L, 'loop', 6], [0.38 * BR, 'branch', 8], [ROADS.branch.len, 'branch', 9]];
  for (const [d0, rn, n0] of VIL) for (let i = 0, n = n0; i < n0 * 3 && n > 0; i++) {
    const { p, t } = roadAt(ROADS[rn], d0 + (R() - 0.5) * 120), side = R() < 0.5 ? -1 : 1, q = p.clone().addScaledVector(right(t), side * (16 + R() * 40)).addScaledVector(t, (R() - 0.5) * 30);
    if (onAnyRoad(q.x, q.z) < 9 || near(q, 13)) continue; house(q, R() * TAU, R); n--;
  }
  // rice paddies in the open stretches
  for (let i = 0; i < 70; i++) {
    const rn = R() < 0.75 ? 'loop' : 'branch', road = ROADS[rn], d = R() * road.len; if (rn === 'loop' && (d < 0.14 * L || d > 0.9 * L)) continue;
    const { p, t } = roadAt(road, d), side = R() < 0.5 ? -1 : 1, q = p.clone().addScaledVector(right(t), side * (24 + R() * 60));
    if (onAnyRoad(q.x, q.z) < 22 || near(q, 22)) continue;
    const w = 26 + R() * 18, h = 18 + R() * 14, m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), M.paddy); m.rotation.x = -Math.PI / 2; m.rotation.z = -Math.atan2(t.z, t.x); m.position.set(q.x, 0.04, q.z); m.receiveShadow = true; scene.add(m); OCC.push({ x: q.x, z: q.z, r: Math.max(w, h) / 2 });
  }
  // utility poles along the loop (and their wires)
  const wirePts = [], poleGeo = new THREE.CylinderGeometry(0.14, 0.18, 9, 6), poles = new THREE.InstancedMesh(poleGeo, M.pole, Math.ceil(L / 40) + Math.ceil(BR / 40) + 4); let pi = 0; const mtx = new THREE.Matrix4();
  for (const [rn, road] of [['loop', ROADS.loop], ['branch', ROADS.branch]]) { let prev = null; for (let d = 6; d < road.len; d += 40) { const { p, t } = roadAt(road, d), q = p.clone().addScaledVector(right(t), -(road.w / 2 + 2.2)); mtx.makeTranslation(q.x, 4.5, q.z); poles.setMatrixAt(pi++, mtx); addCircle(q.x, q.z, 0.3); if (prev) { for (const dy of [8.6, 8.1]) wirePts.push(prev.x, dy, prev.z, q.x, dy, q.z); } prev = q; } }
  poles.count = pi; poles.castShadow = true; scene.add(poles);
  const wg = new THREE.BufferGeometry(); wg.setAttribute('position', new THREE.Float32BufferAttribute(wirePts, 3)); scene.add(new THREE.LineSegments(wg, new THREE.LineBasicMaterial({ color: 0x222222 })));
  // street lamps in town (they glow at night)
  const lampHead = new THREE.SphereGeometry(0.45, 8, 6); WORLD.lamps = [];
  for (const [a, b] of [[0, 0.13], [0.9, 1]]) for (let d = a * L; d < b * L; d += 30) { const { p, t } = roadAt(ROADS.loop, d), q = p.clone().addScaledVector(right(t), road_w() / 2 + 1.2); const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.12, 7, 6), M.metal); pole.position.set(q.x, 3.5, q.z); scene.add(pole); const h = new THREE.Mesh(lampHead, M.lamp); h.position.set(q.x, 7.1, q.z); scene.add(h); addCircle(q.x, q.z, 0.25); }
  // trees, as instances
  const trunks = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.25, 0.35, 3, 6), M.wood, 1600), crowns = new THREE.InstancedMesh(new THREE.IcosahedronGeometry(2.6, 0), new THREE.MeshLambertMaterial({ color: 0x3f7a34, flatShading: true }), 1600);
  crowns.castShadow = true; trunks.castShadow = true; let ti = 0; const col = new THREE.Color();
  for (let i = 0; i < 6000 && ti < 1600; i++) {
    const x = (R() - 0.5) * 2200 + 300, z = (R() - 0.5) * 2200 + 300; const dr = onAnyRoad(x, z); if (dr < 6 || near({ x, z }, 4)) continue;
    if (dr > 160 && R() < 0.6) continue; if (Math.abs((x - XRAIL.x0) * XRAIL.t.x + (z - XRAIL.z0) * XRAIL.t.z) < 7) continue;
    const s = 0.8 + R() * 0.9; mtx.compose(new THREE.Vector3(x, 1.5 * s, z), new THREE.Quaternion(), new THREE.Vector3(s, s, s)); trunks.setMatrixAt(ti, mtx);
    mtx.compose(new THREE.Vector3(x, 4.6 * s, z), new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), R() * 3), new THREE.Vector3(s, s * (1 + R() * 0.5), s)); crowns.setMatrixAt(ti, mtx);
    crowns.setColorAt(ti, col.setHSL(0.26 + R() * 0.08, 0.45, 0.28 + R() * 0.1)); if (dr < 30) addCircle(x, z, 0.5 * s + 0.2); ti++;
  }
  trunks.count = crowns.count = ti; scene.add(trunks); scene.add(crowns); WORLD.crowns = crowns;
  // speed limit and school signs
  for (const z of ZONES) { if (z.road === 'ring') continue; const { p, t } = roadAt(ROADS[z.road], z.a + 4); sign(p.clone().addScaledVector(right(t), ROADS[z.road].w / 2 + 1.5), t, z.lim, z.lim === 30 && z.road === 'loop'); }
}
const road_w = () => 8;
const OCC = []; // occupied spots for placement
function near(q, r) { for (const o of OCC) if (Math.hypot(o.x - q.x, o.z - q.z) < o.r + r) return true; return false; }
const WORLD = { winMats: [], lamps: [] };
function building(pos, face, w, d, h, color, lines, signBg) {
  const g = new THREE.Group(), yaw = Math.atan2(face.x, face.z); // local +z looks at the road
  const body = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color })); body.position.y = h / 2; body.castShadow = body.receiveShadow = true; g.add(body);
  const roof = new THREE.Mesh(new THREE.BoxGeometry(w + 0.6, 0.5, d + 0.6), M.dark); roof.position.y = h + 0.25; g.add(roof);
  const wm = new THREE.MeshLambertMaterial({ color: 0x33424c, emissive: 0x000000 }); WORLD.winMats.push(wm);
  for (let fl = 0; fl < Math.floor(h / 3.2); fl++) for (let x = -w / 2 + 2; x < w / 2 - 1; x += 3) { const win = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 1.4), wm); win.position.set(x + 0.4, 1.9 + fl * 3.2, d / 2 + 0.02); g.add(win); }
  if (lines && lines[0]) { const sg = new THREE.Mesh(new THREE.PlaneGeometry(Math.min(w - 1, 12), 2.2), new THREE.MeshBasicMaterial({ map: textTex(lines.filter(Boolean), { bg: '#' + new THREE.Color(signBg).getHexString(), w: 768, h: 160, s1: 84, s2: 40, gap: 70 }) })); sg.position.set(0, h - 1.3, d / 2 + 0.05); g.add(sg); }
  g.position.set(pos.x, 0, pos.z); g.rotation.y = yaw; scene.add(g);
  addBox(pos.x, pos.z, w / 2, d / 2, yaw); OCC.push({ x: pos.x, z: pos.z, r: Math.max(w, d) / 2 });
}
function house(q, yaw, R) {
  const g = new THREE.Group(), w = 8 + R() * 3, d = 6 + R() * 2;
  const wall = new THREE.Mesh(new THREE.BoxGeometry(w, 3.2, d), new THREE.MeshLambertMaterial({ color: [0xf0e8d8, 0xe8dcc0, 0xf4f0e8, 0xd8c8a8][Math.floor(R() * 4)] })); wall.position.y = 1.6; wall.castShadow = wall.receiveShadow = true; g.add(wall);
  const roof = new THREE.Mesh(new THREE.ConeGeometry(Math.hypot(w, d) / 2 + 0.8, 2.6, 4), new THREE.MeshLambertMaterial({ color: [0x2a5a8a, 0x8a3a2a, 0x3a6a4a, 0x5a5a6a][Math.floor(R() * 4)], flatShading: true })); roof.position.y = 4.5; roof.rotation.y = Math.PI / 4; roof.scale.set(w / Math.hypot(w, d) * 1.4, 1, d / Math.hypot(w, d) * 1.4); roof.castShadow = true; g.add(roof);
  const wm = new THREE.MeshLambertMaterial({ color: 0x33424c, emissive: 0x000000 }); WORLD.winMats.push(wm); const win = new THREE.Mesh(new THREE.PlaneGeometry(1.4, 1.1), wm); win.position.set(-w / 4, 1.8, d / 2 + 0.02); g.add(win);
  g.position.set(q.x, 0, q.z); g.rotation.y = yaw; scene.add(g); addBox(q.x, q.z, w / 2, d / 2, yaw); OCC.push({ x: q.x, z: q.z, r: Math.max(w, d) / 2 + 1 });
}
function mkTreeMesh(c) { const g = new THREE.Group(); const t = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.4, 3, 6), M.wood); t.position.y = 1.5; g.add(t); const cr = new THREE.Mesh(new THREE.IcosahedronGeometry(2.6, 0), new THREE.MeshLambertMaterial({ color: c, flatShading: true })); cr.position.y = 4.6; cr.castShadow = true; g.add(cr); return g; }
function stopProps(p, t, name) {
  const rv = right(t), base = p.clone().addScaledVector(rv, 6.2), yaw = Math.atan2(t.x, t.z);
  const g = new THREE.Group(); g.position.copy(base); g.rotation.y = yaw;
  const roof = new THREE.Mesh(new THREE.BoxGeometry(2.2, 0.15, 6), new THREE.MeshLambertMaterial({ color: 0x2fa86a })); roof.position.y = 2.6; g.add(roof);
  const back = new THREE.Mesh(new THREE.BoxGeometry(0.1, 2.2, 6), new THREE.MeshLambertMaterial({ color: 0x9ad0e0, transparent: true, opacity: 0.5 })); back.position.set(-1.0, 1.4, 0); g.add(back);
  for (const zz of [-2.9, 2.9]) { const post = new THREE.Mesh(new THREE.BoxGeometry(0.12, 2.6, 0.12), M.metal); post.position.set(-0.9, 1.3, zz); g.add(post); }
  const bench = new THREE.Mesh(new THREE.BoxGeometry(0.6, 0.12, 4), M.wood); bench.position.set(-0.5, 0.5, 0); g.add(bench);
  scene.add(g); OCC.push({ x: base.x, z: base.z, r: 4 }); addBox(base.x, base.z, 1.1, 3, yaw);
  // the stop pole with the name plate, at the front of the bay
  const sp = p.clone().addScaledVector(rv, 4.6).addScaledVector(t, 7);
  const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 3, 6), M.metal); pole.position.set(sp.x, 1.5, sp.z); scene.add(pole); addCircle(sp.x, sp.z, 0.2);
  const plate = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 0.8), new THREE.MeshBasicMaterial({ map: textTex(['🚌 ' + name], { bg: '#1f7a4c', w: 512, h: 256, s1: 54 }), side: THREE.DoubleSide })); plate.position.set(sp.x, 2.7, sp.z); plate.rotation.y = yaw + Math.PI / 2; scene.add(plate);
  // the bay marking on the road edge
  const bay = new THREE.Mesh(new THREE.PlaneGeometry(14, 2.6), new THREE.MeshBasicMaterial({ color: 0xf2b81e, transparent: true, opacity: 0.35, depthWrite: false })); bay.rotation.x = -Math.PI / 2; bay.rotation.z = -Math.atan2(t.z, t.x); bay.position.copy(p).addScaledVector(rv, 2.2).addScaledVector(t, 1); bay.position.y = 0.08; scene.add(bay);
}
function sign(q, t, lim, school) {
  const yaw = Math.atan2(t.x, t.z), pole = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 2.6, 6), M.metal); pole.position.set(q.x, 1.3, q.z); scene.add(pole);
  const plate = new THREE.Mesh(new THREE.CircleGeometry(0.45, 24), new THREE.MeshBasicMaterial({ map: canvasTex(128, 128, g => { g.fillStyle = '#fff'; g.beginPath(); g.arc(64, 64, 62, 0, TAU); g.fill(); g.strokeStyle = '#d02a20'; g.lineWidth = 14; g.beginPath(); g.arc(64, 64, 54, 0, TAU); g.stroke(); g.fillStyle = '#000'; g.font = 'bold 54px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(lim, 64, 68); }) }));
  plate.position.set(q.x, 2.7, q.z); plate.rotation.y = yaw + Math.PI; scene.add(plate);
  if (school) { const s2 = new THREE.Mesh(new THREE.PlaneGeometry(1.1, 0.7), new THREE.MeshBasicMaterial({ map: textTex(['어린이', '보호구역'], { bg: '#ffd23a', fg: '#000', w: 256, h: 160, s1: 44, s2: 44, gap: 50 }) })); s2.position.set(q.x, 2.0, q.z); s2.rotation.y = yaw + Math.PI; scene.add(s2); }
}
// ---- the railway: a straight line across the loop with a level crossing ----
const XRAIL = { x0: 0, z0: 0, dir: null, gates: [], lights: [], train: null, state: 'idle', t: 0, next: 50, trainS: -900 };
function buildRail() {
  const { p, t } = roadAt(ROADS.loop, XING.d), dir = right(t); XRAIL.x0 = p.x; XRAIL.z0 = p.z; XRAIL.dir = dir; XRAIL.t = t;
  const len = 2400, rg = new THREE.Group(), m = new THREE.Mesh(new THREE.PlaneGeometry(4, len), M.rail); m.rotation.x = -Math.PI / 2; TEX.rail.repeat.set(1, len / 16); rg.add(m);
  rg.position.set(p.x, 0.07, p.z); rg.rotation.y = Math.atan2(dir.x, dir.z); scene.add(rg);
  // the crossing plank
  const plank = new THREE.Mesh(new THREE.PlaneGeometry(10, 5), new THREE.MeshLambertMaterial({ color: 0x3a3a3a })); plank.rotation.x = -Math.PI / 2; plank.rotation.z = -Math.atan2(t.z, t.x); plank.position.set(p.x, 0.09, p.z); scene.add(plank);
  // gates on both approaches
  for (const s of [-1, 1]) {
    const gp = p.clone().addScaledVector(t, s * 9).addScaledVector(dir, -s * 5), yaw = Math.atan2(t.x, t.z);
    const post = new THREE.Mesh(new THREE.BoxGeometry(0.4, 1.4, 0.4), new THREE.MeshLambertMaterial({ color: 0xf2f2f2 })); post.position.set(gp.x, 0.7, gp.z); scene.add(post); addCircle(gp.x, gp.z, 0.4);
    const arm = new THREE.Group(); arm.position.set(gp.x, 1.2, gp.z); arm.rotation.y = Math.atan2(-s * dir.z, s * dir.x);
    const bar = new THREE.Mesh(new THREE.BoxGeometry(5, 0.15, 0.15), new THREE.MeshLambertMaterial({ map: canvasTex(64, 8, g => { for (let i = 0; i < 8; i++) { g.fillStyle = i % 2 ? '#fff' : '#d02a20'; g.fillRect(i * 8, 0, 8, 8); } }) })); bar.position.x = 2.5; arm.add(bar); scene.add(arm); XRAIL.gates.push(arm);
    const lp = gp.clone().addScaledVector(dir, -s * 0.8); const lpole = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.07, 3.2, 6), M.metal); lpole.position.set(lp.x, 1.6, lp.z); scene.add(lpole);
    const xs = new THREE.Mesh(new THREE.PlaneGeometry(1.4, 1.4), new THREE.MeshBasicMaterial({ map: canvasTex(128, 128, g => { g.translate(64, 64); for (const r of [0.6, -0.6]) { g.save(); g.rotate(r); g.fillStyle = '#fff'; g.fillRect(-60, -10, 120, 20); g.strokeStyle = '#d02a20'; g.lineWidth = 4; g.strokeRect(-60, -10, 120, 20); g.restore(); } }), transparent: true, side: THREE.DoubleSide })); xs.position.set(lp.x, 3.1, lp.z); xs.rotation.y = yaw + (s > 0 ? 0 : Math.PI); scene.add(xs);
    for (const k of [-0.25, 0.25]) { const l = new THREE.Mesh(new THREE.SphereGeometry(0.13, 8, 6), new THREE.MeshBasicMaterial({ color: 0x400000 })); const off = new THREE.Vector3().copy(dir).multiplyScalar(k); l.position.set(lp.x + off.x, 2.3, lp.z + off.z); scene.add(l); XRAIL.lights.push(l); }
  }
  // the train (a diesel and three cars)
  const tr = new THREE.Group(), cols = [0x2a4a8a, 0xe8e8e8, 0xe8e8e8, 0xe8e8e8];
  cols.forEach((c, i) => { const car = new THREE.Mesh(new THREE.BoxGeometry(3, 3.6, 20), new THREE.MeshLambertMaterial({ color: c })); car.position.set(0, 2.2, -i * 21.5); car.castShadow = true; tr.add(car); const stripe = new THREE.Mesh(new THREE.BoxGeometry(3.05, 0.5, 20), new THREE.MeshLambertMaterial({ color: 0xd02a20 })); stripe.position.set(0, 1.6, -i * 21.5); tr.add(stripe); });
  scene.add(tr); XRAIL.train = tr; tr.visible = false;
}
function railTick(dt, busPos) {
  XRAIL.t += dt; const X = XRAIL;
  if (X.state === 'idle' && X.t > X.next) { X.state = 'warn'; X.t = 0; }
  if (X.state === 'warn' || X.state === 'pass') { const on = Math.floor(X.t * 2) % 2; X.lights.forEach((l, i) => l.material.color.setHex((i % 2) === on ? 0xff2a10 : 0x400000)); if (busPos && Math.hypot(busPos.x - X.x0, busPos.z - X.z0) < 120 && Math.floor(X.t * 2.5) !== X.lastBell) { X.lastBell = Math.floor(X.t * 2.5); audio.xbell(X.lastBell); } }
  const down = X.state === 'warn' ? clamp((X.t - 2) / 3, 0, 1) : X.state === 'pass' ? 1 : X.state === 'up' ? 1 - clamp(X.t / 3, 0, 1) : 0;
  X.gates.forEach(g => { g.rotation.z = (1 - down) * 1.35; });
  X.closed = down > 0.05;
  if (X.state === 'warn' && X.t > 7) { X.state = 'pass'; X.t = 0; X.trainS = -700; X.train.visible = true; if (busPos && Math.hypot(busPos.x - X.x0, busPos.z - X.z0) < 300) audio.train(); }
  if (X.state === 'pass') { X.trainS += dt * 28; const p = new THREE.Vector3(X.x0, 0, X.z0).addScaledVector(X.dir, X.trainS); X.train.position.copy(p); X.train.lookAt(p.clone().add(X.dir)); if (X.trainS > 780) { X.state = 'up'; X.t = 0; X.train.visible = false; } }
  if (X.state === 'up' && X.t > 3) { X.state = 'idle'; X.t = 0; X.next = 70 + Math.random() * 60; X.lights.forEach(l => l.material.color.setHex(0x400000)); }
}
