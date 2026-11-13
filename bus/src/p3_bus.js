// ================= the bus: model, physics, cameras =================
const BUSES = {
  old: { name: '현태 1호', kind: '1998년식 시골버스', len: 9.2, wid: 2.4, h: 3.0, wb: 4.6, floor: 0.95, maxV: 72, acc: 1.15, brake: 5.2, doors: ['F'], cap: 32, fuel: 420, price: 0, gears: [14, 28, 44, 60, 76], color: 0x2fa86a, stripe: 0xf2f2e8, desc: '할머니가 타던 그 버스. 문이 느리고 덜컹거린다. 앞문 하나.' },
  mini: { name: '꼬마 2호', kind: '미니버스', len: 7.4, wid: 2.1, h: 2.75, wb: 3.9, floor: 0.75, maxV: 88, acc: 1.55, brake: 6, doors: ['F'], cap: 22, fuel: 280, price: 4000000, gears: [16, 32, 50, 68, 90], color: 0xf2c14e, stripe: 0x2a5aa8, desc: '작고 날쌔다. 산길에 강하지만 정원이 적다.' },
  low: { name: '저상 3호', kind: '저상버스', len: 11, wid: 2.5, h: 3.15, wb: 5.9, floor: 0.38, maxV: 86, acc: 1.35, brake: 6, doors: ['F', 'R'], cap: 56, fuel: 470, price: 10000000, gears: [16, 32, 50, 68, 88], color: 0x2a6ad0, stripe: 0xf2f2f2, kneel: true, desc: '앞문·뒷문, 무릎 꿇기(N). 장날 손님을 다 태운다.' },
  ev: { name: '번개 4호', kind: '전기 저상버스', len: 11.6, wid: 2.5, h: 3.35, wb: 6.0, floor: 0.38, maxV: 92, acc: 1.85, brake: 6.4, doors: ['F', 'R'], cap: 60, fuel: 130, price: 25000000, gears: [92], color: 0xf2f2f2, stripe: 0x2fa86a, electric: true, kneel: true, desc: '조용하고 힘세다. 연료비가 거의 안 든다. 군수님의 꿈.' },
};
const PAINTS = [0x2fa86a, 0x2a6ad0, 0xf2c14e, 0xe8452f, 0xf2f2f2, 0x7a4ad8, 0xff8ab0, 0x1e1e24, 0x8a5a34, 0x5ad0e0];
const BUS = { spec: null, root: null, body: null, pos: new THREE.Vector3(), yaw: 0, v: 0, a: 0, alat: 0, steer: 0, wheel: 0, gear: 'N', g: 1, shiftT: 0, rpm: 0, engine: false, park: true,
  doors: { F: 0, R: 0 }, doorT: { F: 0, R: 0 }, ind: 0, hazard: false, lights: false, wiper: false, kneel: 0, damage: 0, odo: 0, offroad: false, mats: {}, wheels: [], doorMeshes: {}, sw: null, sign: null };
const CAM = { mode: 0, yaw: 0, pitch: -0.05, cyaw: 0, cpitch: 0.32, dist: 18, mirror: true };

function makeBus(id, paint) {
  if (BUS.root) scene.remove(BUS.root);
  const S = BUSES[id], g = new THREE.Group(), body = new THREE.Group(); g.add(body); BUS.spec = S; BUS.id = id;
  const col = paint != null ? paint : S.color;
  const paintM = new THREE.MeshLambertMaterial({ color: col, side: THREE.DoubleSide }), stripeM = new THREE.MeshLambertMaterial({ color: S.stripe, side: THREE.DoubleSide });
  const glassM = new THREE.MeshLambertMaterial({ color: 0x223a48, transparent: true, opacity: 0.32, side: THREE.DoubleSide, depthWrite: false });
  const inM = new THREE.MeshLambertMaterial({ color: 0xd8d4c8, side: THREE.DoubleSide }), floorM = new THREE.MeshLambertMaterial({ color: 0x4a4a52 }), seatM = new THREE.MeshLambertMaterial({ color: S.electric ? 0x2a5aa8 : 0x2a6a4a });
  const L2 = S.len / 2, W2 = S.wid / 2, fl = S.floor, wb = fl < 0.5 ? 1.05 : 0.82; // window bottom height above the floor
  const box = (w, h, d, m, x, y, z, cast = true) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m); b.position.set(x, y, z); b.castShadow = cast; body.add(b); return b; };
  // lower body (sides, front, back), skirt stripe, floor, roof
  const lowTop = fl + wb, roofY = S.h;
  for (const s of [-1, 1]) { box(0.06, lowTop - 0.35, S.len, paintM, s * W2, 0.35 + (lowTop - 0.35) / 2, 0); box(0.065, 0.28, S.len, stripeM, s * W2, 0.62, 0); }
  box(S.wid, lowTop - 0.35, 0.06, paintM, 0, 0.35 + (lowTop - 0.35) / 2, L2); box(S.wid, roofY - 0.35, 0.06, paintM, 0, 0.35 + (roofY - 0.35) / 2, -L2 + 0.03).scale.y = 1;
  box(S.wid - 0.1, 0.08, S.len - 0.1, floorM, 0, fl, 0, false);
  box(S.wid + 0.04, 0.16, S.len + 0.02, paintM, 0, roofY, 0); box(S.wid * 0.6, 0.3, S.len * 0.25, paintM, 0, roofY + 0.2, -S.len * 0.15); // roof + aircon
  // window band: glass panes between pillars
  const bandH = roofY - lowTop - 0.08, bandY = lowTop + bandH / 2;
  for (const s of [-1, 1]) { const g2 = new THREE.Mesh(new THREE.PlaneGeometry(S.len - 0.6, bandH), glassM); g2.position.set(s * (W2 + 0.01), bandY, -0.2); g2.rotation.y = s * Math.PI / 2; body.add(g2); for (let z = -L2 + 0.15; z <= L2 - 0.3; z += 1.6) box(0.08, bandH, 0.12, paintM, s * W2, bandY, z); }
  const ws = new THREE.Mesh(new THREE.PlaneGeometry(S.wid - 0.2, roofY - lowTop + 0.35), glassM); ws.position.set(0, (roofY + lowTop - 0.35) / 2 + 0.05, L2 + 0.02); body.add(ws);
  box(0.12, roofY - lowTop + 0.3, 0.12, paintM, -W2 + 0.06, (roofY + lowTop) / 2, L2); box(0.12, roofY - lowTop + 0.3, 0.12, paintM, W2 - 0.06, (roofY + lowTop) / 2, L2);
  // destination sign (LED)
  const signC = document.createElement('canvas'); signC.width = 512; signC.height = 96; const signT = new THREE.CanvasTexture(signC); signT.colorSpace = THREE.SRGBColorSpace;
  const signM = new THREE.Mesh(new THREE.PlaneGeometry(S.wid - 0.5, 0.36), new THREE.MeshBasicMaterial({ map: signT })); signM.position.set(0, roofY - 0.25, L2 + 0.05); body.add(signM); BUS.sign = { c: signC, t: signT };
  // lights
  const mk = (c, x, y, z, w = 0.36, h = 0.2, back = false) => { const m = new THREE.MeshBasicMaterial({ color: c }); const p = new THREE.Mesh(new THREE.PlaneGeometry(w, h), m); p.position.set(x, y, z); if (back) p.rotation.y = Math.PI; body.add(p); return m; };
  BUS.mats = { headL: mk(0x888880, -W2 + 0.35, 0.75, L2 + 0.04), headR: mk(0x888880, W2 - 0.35, 0.75, L2 + 0.04), tailL: mk(0x5a0a08, -W2 + 0.3, 0.9, -L2 - 0.01, 0.3, 0.4, true), tailR: mk(0x5a0a08, W2 - 0.3, 0.9, -L2 - 0.01, 0.3, 0.4, true),
    indFL: mk(0x5a3a00, W2 - 0.2, 1.0, L2 + 0.04, 0.2, 0.12), indFR: mk(0x5a3a00, -W2 + 0.2, 1.0, L2 + 0.04, 0.2, 0.12), indRL: mk(0x5a3a00, W2 - 0.3, 1.25, -L2 - 0.01, 0.3, 0.14, true), indRR: mk(0x5a3a00, -W2 + 0.3, 1.25, -L2 - 0.01, 0.3, 0.14, true) };
  // headlight beams for the night
  const hl = new THREE.SpotLight(0xfff4d8, 0, 120, 0.5, 0.45, 1.2); hl.position.set(0, 1.2, L2); hl.target.position.set(0, 0, L2 + 30); body.add(hl); body.add(hl.target); BUS.beam = hl;
  // doors on the right side: two leaves that fold
  BUS.doorMeshes = {};
  for (const d of S.doors) { const z = d === 'F' ? L2 - 0.95 : -0.4, dw = 1.15, dh = roofY - fl - 0.3, grp = []; for (const k of [-1, 1]) { const leaf = new THREE.Mesh(new THREE.BoxGeometry(0.05, dh, dw / 2), new THREE.MeshLambertMaterial({ color: 0x334a56, transparent: true, opacity: 0.7 })); leaf.position.set(-(W2 + 0.03), fl + dh / 2 + 0.1, z + k * dw / 4); body.add(leaf); grp.push({ m: leaf, k, z }); } BUS.doorMeshes[d] = { grp, z, dw }; }
  // mirrors
  for (const s of [-1, 1]) { const arm = box(0.05, 0.05, 0.7, M.dark, s * (W2 + 0.3), roofY - 0.5, L2 + 0.2); arm.rotation.y = s * 0.5; box(0.08, 0.5, 0.28, M.dark, s * (W2 + 0.55), roofY - 0.85, L2 + 0.45); }
  // wheels
  BUS.wheels = []; const fz = L2 - 2.0, rz = fz - S.wb, wg = new THREE.CylinderGeometry(0.5, 0.5, 0.36, 16); wg.rotateZ(Math.PI / 2);
  for (const z of [fz, rz]) for (const s of [-1, 1]) { const w = new THREE.Group(); const t = new THREE.Mesh(wg, M.dark); t.castShadow = true; w.add(t); const hub = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 0.38, 8).rotateZ(Math.PI / 2), M.metal); w.add(hub); w.position.set(s * (W2 - 0.15), 0.5, z); g.add(w); BUS.wheels.push({ w, front: z === fz }); }
  BUS.axleF = fz; BUS.axleR = rz;
  // interior: seats, poles, dash, steering wheel
  const seatG = new THREE.BoxGeometry(0.9, 0.5, 0.5);
  for (let z = L2 - 2.6; z > -L2 + 0.8; z -= 0.95) for (const s of [-1, 1]) { if (s < 0 && S.doors.includes('R') && Math.abs(z + 0.4) < 0.9) continue; const st = new THREE.Mesh(seatG, seatM); st.position.set(s * (W2 - 0.55), fl + 0.45, z); body.add(st); const bk = new THREE.Mesh(new THREE.BoxGeometry(0.9, 0.7, 0.1), seatM); bk.position.set(s * (W2 - 0.55), fl + 0.9, z - 0.22); body.add(bk); }
  for (let z = L2 - 2.2; z > -L2 + 1; z -= 2.2) { const p = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.03, roofY - fl, 6), new THREE.MeshLambertMaterial({ color: 0xf2c14e })); p.position.set(0.25, (roofY + fl) / 2, z); body.add(p); }
  box(S.wid - 0.2, 0.02, S.len - 0.3, inM, 0, roofY - 0.1, 0, false);
  const dash = box(S.wid * 0.62, 0.32, 0.6, M.dark, W2 * 0.35, lowTop - 0.1, L2 - 0.45, false);
  const sw = new THREE.Group(); const ring = new THREE.Mesh(new THREE.TorusGeometry(0.26, 0.03, 8, 24), M.dark); sw.add(ring); for (let i = 0; i < 3; i++) { const sp = new THREE.Mesh(new THREE.BoxGeometry(0.04, 0.26, 0.02), M.dark); sp.position.y = -0.12; const sg = new THREE.Group(); sg.rotation.z = i * TAU / 3; sg.add(sp); sw.add(sg); }
  sw.position.set(W2 - 0.72, lowTop - 0.02, L2 - 0.9); sw.rotation.x = -0.9; body.add(sw); BUS.sw = sw;
  BUS.eye = new THREE.Vector3(W2 - 0.72, lowTop + 0.62, L2 - 1.42);
  g.traverse(o => { if (o.isMesh) o.receiveShadow = false; });
  BUS.root = g; BUS.body = body; scene.add(g); drawSign('회송', '현태운수');
  return g;
}
function drawSign(no, text) {
  const { c, t } = BUS.sign, g = c.getContext('2d'); g.fillStyle = '#050505'; g.fillRect(0, 0, 512, 96);
  g.fillStyle = '#ffb627'; g.font = 'bold 64px ui-monospace, monospace'; g.textBaseline = 'middle'; g.fillText(no, 18, 50);
  g.font = 'bold 52px "Nanum Gothic", sans-serif'; g.fillText(text, 150, 52);
  for (let x = 0; x < 512; x += 4) { g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(x, 0, 1, 96); } t.needsUpdate = true;
}
const fwd = () => new THREE.Vector3(Math.sin(BUS.yaw), 0, Math.cos(BUS.yaw));
function placeBus(road, d, lane = 1.9) { const { p, t } = roadAt(road, d); BUS.pos.copy(p).addScaledVector(right(t), lane); BUS.yaw = Math.atan2(t.x, t.z); BUS.v = 0; BUS.steer = 0; }

// ---- physics ----
const EV = { stopDone: null }; // events for the route logic
function busStep(dt) {
  const S = BUS.spec, kmh = Math.abs(BUS.v) * 3.6, doorsOpen = BUS.doors.F > 0.02 || BUS.doors.R > 0.02;
  // gearbox
  const G = S.gears; if (BUS.gear === 'D') {
    if (BUS.shiftT > 0) BUS.shiftT -= dt;
    else if (BUS.g < G.length && kmh > G[BUS.g - 1] * 0.9 && IN.thr > 0.1) { BUS.g++; BUS.shiftT = S.electric ? 0 : 0.45; }
    else if (BUS.g > 1 && kmh < G[BUS.g - 2] * 0.55) { BUS.g--; BUS.shiftT = S.electric ? 0 : 0.25; }
  } else BUS.g = 1;
  const top = G[BUS.g - 1], lo = BUS.g > 1 ? G[BUS.g - 2] * 0.5 : 0;
  BUS.rpm = BUS.engine ? (BUS.gear === 'N' || BUS.park || doorsOpen ? 700 + IN.thr * 1500 : 650 + clamp((kmh - lo) / (top - lo || 1), 0, 1.15) * 1650) : 0;
  // forces
  let thr = BUS.engine && !doorsOpen && !BUS.park ? IN.thr : 0; if (BUS.shiftT > 0) thr *= 0.25;
  let a = 0; const dir = BUS.gear === 'R' ? -1 : 1;
  if (BUS.gear !== 'N' && thr > 0) { const gearK = S.electric ? 1 : 1.25 - 0.1 * BUS.g; const vmax = BUS.gear === 'R' ? 12 : S.maxV; a += dir * thr * S.acc * gearK * Math.max(0, 1 - Math.pow(kmh / vmax, 3)); }
  const sgn = Math.sign(BUS.v);
  const roll = 0.1 + (BUS.offroad ? 1.0 : 0), drag = 0.00038 * BUS.v * BUS.v + (BUS.engine && BUS.gear !== 'N' && thr === 0 ? 0.25 : 0);
  let brake = IN.brk * S.brake + (BUS.park ? 5 : 0) + (doorsOpen ? 6 : 0);
  const resist = (roll + drag) * sgn;
  let v = BUS.v + (a - resist) * dt;
  if (brake > 0) { const dv = brake * dt; v = Math.abs(v) <= dv ? 0 : v - Math.sign(v) * dv; }
  if (sgn !== 0 && Math.sign(v) !== sgn && a * sgn <= 0) v = 0; // resistances never push backwards
  BUS.a = (v - BUS.v) / dt; BUS.v = v;
  // steering (the wheel turns less at speed)
  const maxS = lerp(0.62, 0.2, clamp(kmh / 70, 0, 1)), target = IN.steer * maxS;
  BUS.steer += clamp(target - BUS.steer, -1.4 * dt, 1.4 * dt);
  const yawRate = BUS.v * Math.tan(BUS.steer) / S.wb; BUS.yaw -= yawRate * dt; BUS.alat = BUS.v * yawRate;
  const f = fwd(); BUS.pos.addScaledVector(f, BUS.v * dt); BUS.odo += Math.abs(BUS.v) * dt;
  // doors and kneeling
  for (const d of ['F', 'R']) { const tgt = BUS.doorT[d]; const sp = S.name === '현태 1호' ? 0.9 : 1.6; BUS.doors[d] += clamp(tgt - BUS.doors[d], -sp * dt, sp * dt); }
  BUS.kneel += clamp((BUS.kneelOn ? 1 : 0) - BUS.kneel, -dt, dt);
  collide();
  // off-road check
  const near = nearestOn(ROADS.loop, BUS.pos.x, BUS.pos.z, -1), nb = nearestOn(ROADS.branch, BUS.pos.x, BUS.pos.z), nr = nearestOn(ROADS.ring, BUS.pos.x, BUS.pos.z);
  const yardP = roadAt(ROADS.loop, STOPS.term.d), inYard = Math.hypot(BUS.pos.x - (yardP.p.x + right(yardP.t).x * 22), BUS.pos.z - (yardP.p.z + right(yardP.t).z * 22)) < 26;
  BUS.offroad = !inYard && Math.min(near.dist - 4.8, nb.dist - 4.8, nr.dist - 4.8) > 0;
  BUS.near = { loop: near, branch: nb, ring: nr };
}
function collide() {
  const S = BUS.spec, f = fwd(), r = new THREE.Vector3(f.z, 0, -f.x), rad = S.wid / 2;
  const n = Math.max(3, Math.round(S.len / 2.2)); let hit = 0;
  for (let i = 0; i < n; i++) {
    const off = -S.len / 2 + rad + (S.len - 2 * rad) * i / (n - 1), cx = BUS.pos.x + f.x * off, cz = BUS.pos.z + f.z * off;
    for (const c of COL) {
      const dx0 = cx - c.x, dz0 = cz - c.z; if (dx0 * dx0 + dz0 * dz0 > (c.r + rad + 2) ** 2) continue;
      let nx, nz, pen;
      if (c.hw == null) { const d = Math.hypot(dx0, dz0); if (d >= c.r + rad) continue; nx = dx0 / (d || 1); nz = dz0 / (d || 1); pen = c.r + rad - d; }
      else { const cs = Math.cos(c.rot), sn = Math.sin(c.rot), lx = dx0 * cs - dz0 * sn, lz = dx0 * sn + dz0 * cs; const qx = clamp(lx, -c.hw, c.hw), qz = clamp(lz, -c.hd, c.hd); const ex = lx - qx, ez = lz - qz, d = Math.hypot(ex, ez); if (d >= rad) continue; const ux = ex / (d || 1), uz = ez / (d || 1); nx = ux * cs + uz * sn; nz = -ux * sn + uz * cs; pen = rad - d; }
      BUS.pos.x += nx * pen; BUS.pos.z += nz * pen; hit = Math.max(hit, Math.abs(BUS.v));
    }
    for (const car of CARS) { const dx = cx - car.pos.x, dz = cz - car.pos.z, d = Math.hypot(dx, dz), rr = rad + car.rad; if (d < rr) { BUS.pos.x += dx / d * (rr - d); BUS.pos.z += dz / d * (rr - d); hit = Math.max(hit, Math.abs(BUS.v - car.v * 0.5)); car.stunned = 3; } }
  }
  if (hit > 0) { if (hit > 1.2) EV.crash = (EV.crash || 0) + hit; BUS.v *= 0.2; }
}
function busVisual(dt, t) {
  const S = BUS.spec, root = BUS.root;
  root.position.copy(BUS.pos); root.rotation.y = BUS.yaw;
  // body pitch and roll from the forces (and the kneel)
  const pitch = clamp(-BUS.a * 0.006, -0.03, 0.03), roll = clamp(BUS.alat * 0.008, -0.04, 0.04), rumble = BUS.engine ? Math.sin(t * 40) * 0.003 * (S.electric ? 0.2 : 1) + (BUS.offroad ? Math.sin(t * 17) * 0.012 * Math.min(1, Math.abs(BUS.v) / 5) : 0) : 0;
  BUS.body.rotation.x = lerp(BUS.body.rotation.x, pitch, dt * 4); BUS.body.rotation.z = lerp(BUS.body.rotation.z, roll + BUS.kneel * 0.035, dt * 4); BUS.body.position.y = rumble - BUS.kneel * 0.08;
  for (const w of BUS.wheels) { w.w.children[0].rotation.x += BUS.v * dt / 0.5; w.w.children[1].rotation.x = w.w.children[0].rotation.x; if (w.front) w.w.rotation.y = -BUS.steer; }
  BUS.sw.rotation.z = BUS.steer * 7;
  for (const [d, dm] of Object.entries(BUS.doorMeshes)) { const o = BUS.doors[d]; for (const l of dm.grp) { l.m.position.z = dm.z + l.k * dm.dw / 4 + l.k * o * dm.dw * 0.28; l.m.position.x = -(S.wid / 2 + 0.03 + o * 0.18); l.m.rotation.y = l.k * o * 1.2; } }
  // lights
  const blink = Math.floor(t * 2.6) % 2 === 0, L = BUS.ind === -1 || BUS.hazard, R = BUS.ind === 1 || BUS.hazard;
  BUS.mats.indFL.color.setHex(L && blink ? 0xffb020 : 0x5a3a00); BUS.mats.indRL.color.setHex(L && blink ? 0xffb020 : 0x5a3a00);
  BUS.mats.indFR.color.setHex(R && blink ? 0xffb020 : 0x5a3a00); BUS.mats.indRR.color.setHex(R && blink ? 0xffb020 : 0x5a3a00);
  if ((L || R) && blink !== BUS.lastBlink) { BUS.lastBlink = blink; blink ? audio.tick() : audio.tock(); }
  const braking = IN.brk > 0.05 || (BUS.v === 0 && BUS.engine);
  BUS.mats.tailL.color.setHex(braking ? 0xff2010 : BUS.lights ? 0xa01a10 : 0x5a0a08); BUS.mats.tailR.color.copy(BUS.mats.tailL.color);
  BUS.mats.headL.color.setHex(BUS.lights ? 0xfffbe8 : 0x888880); BUS.mats.headR.color.copy(BUS.mats.headL.color);
  BUS.beam.intensity = BUS.lights ? 220 : 0;
  audio.engine(BUS.engine, BUS.rpm, IN.thr * (BUS.gear !== 'N' ? 1 : 0.5), S.electric);
}
// ---- cameras ----
let drag = null;
$('#gl').addEventListener('pointerdown', e => { drag = { x: e.clientX, y: e.clientY }; });
addEventListener('pointerup', () => drag = null);
addEventListener('pointermove', e => { if (!drag) return; const dx = e.clientX - drag.x, dy = e.clientY - drag.y; drag = { x: e.clientX, y: e.clientY };
  if (CAM.mode === 0) { CAM.yaw = clamp(CAM.yaw - dx * 0.004, -2.4, 2.4); CAM.pitch = clamp(CAM.pitch - dy * 0.004, -0.8, 0.5); } else { CAM.cyaw -= dx * 0.005; CAM.cpitch = clamp(CAM.cpitch + dy * 0.004, 0.05, 1.2); } });
$('#gl').addEventListener('dblclick', () => { CAM.yaw = 0; CAM.pitch = -0.05; });
addEventListener('wheel', e => { if (CAM.mode === 1) CAM.dist = clamp(CAM.dist + e.deltaY * 0.01, 8, 40); }, { passive: true });
const CAM_NAMES = ['운전석', '외부 추적', '앞문 카메라'];
function camStep(dt) {
  const f = fwd(), body = BUS.body;
  // the right stick looks around too
  if (Math.abs(IN.lookX) + Math.abs(IN.lookY) > 0) { if (CAM.mode === 0) { CAM.yaw = clamp(CAM.yaw - IN.lookX * dt * 2, -2.4, 2.4); CAM.pitch = clamp(CAM.pitch - IN.lookY * dt * 1.5, -0.8, 0.5); } else { CAM.cyaw -= IN.lookX * dt * 2; CAM.cpitch = clamp(CAM.cpitch + IN.lookY * dt, 0.05, 1.2); } }
  else if (CAM.mode === 0 && !drag && CAM.autoCenter) { CAM.yaw *= 1 - dt * 2; }
  if (CAM.mode === 0) {
    body.updateMatrixWorld(); const eye = BUS.eye.clone(); eye.z -= clamp(BUS.a * 0.012, -0.06, 0.06); eye.x += clamp(BUS.alat * 0.01, -0.05, 0.05);
    camera.position.copy(eye.applyMatrix4(body.matrixWorld));
    const yaw = BUS.yaw + CAM.yaw, d = new THREE.Vector3(Math.sin(yaw) * Math.cos(CAM.pitch), Math.sin(CAM.pitch), Math.cos(yaw) * Math.cos(CAM.pitch));
    camera.up.set(Math.sin(BUS.body.rotation.z) * 0.3, 1, 0); camera.lookAt(camera.position.clone().add(d)); camera.fov = 66;
  } else if (CAM.mode === 1) {
    const yaw = BUS.yaw + Math.PI + CAM.cyaw, want = BUS.pos.clone().add(new THREE.Vector3(Math.sin(yaw) * Math.cos(CAM.cpitch), Math.sin(CAM.cpitch), Math.cos(yaw) * Math.cos(CAM.cpitch)).multiplyScalar(CAM.dist)); want.y += 1.5;
    camera.position.lerp(want, 1 - Math.exp(-dt * 6)); camera.up.set(0, 1, 0); camera.lookAt(BUS.pos.clone().add(new THREE.Vector3(0, 2, 0)).addScaledVector(f, 2)); camera.fov = 58;
  } else {
    body.updateMatrixWorld(); const p = new THREE.Vector3(-(BUS.spec.wid / 2 + 1.2), 2.6, BUS.spec.len / 2 + 0.6).applyMatrix4(body.matrixWorld); camera.position.copy(p); camera.up.set(0, 1, 0);
    camera.lookAt(new THREE.Vector3(-(BUS.spec.wid / 2 + 0.2), 0.8, -BUS.spec.len / 2 - 4).applyMatrix4(body.matrixWorld)); camera.fov = 70;
  }
  camera.updateProjectionMatrix();
  // the right mirror sees backwards along the side
  body.updateMatrixWorld(); mirrorCam.position.copy(new THREE.Vector3(-(BUS.spec.wid / 2 + 0.6), BUS.spec.h - 0.85, BUS.spec.len / 2 + 0.4).applyMatrix4(body.matrixWorld));
  mirrorCam.lookAt(new THREE.Vector3(-(BUS.spec.wid / 2 + 2.2), 0.6, -BUS.spec.len / 2 - 14).applyMatrix4(body.matrixWorld));
  // the sun's shadow box follows the bus
  sun.position.set(BUS.pos.x + 120, 200, BUS.pos.z + 80); sun.target.position.copy(BUS.pos);
}
