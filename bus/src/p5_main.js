// ================= weather, HUD, menus, company, main loop =================
let NIGHT = false, RAIN = false;
function setNight(on) {
  NIGHT = on; const u = WORLD.sky.material.uniforms;
  u.top.value.setHex(on ? 0x0a1028 : 0x6aa8e0); u.bot.value.setHex(on ? 0x1a2240 : 0xd8ecf4);
  hemi.intensity = on ? 0.18 : 0.85; sun.intensity = on ? 0.06 : 2.1; scene.fog.color.setHex(on ? 0x0e1426 : 0xbfd8e6);
  for (const m of WORLD.winMats) m.emissive.setHex(on && Math.random() < 0.6 ? 0xffc870 : 0x000000);
  M.lamp.color.setHex(on ? 0xffe8a0 : 0x555555); renderer.toneMappingExposure = on ? 1.35 : 1.05; applyRainLook();
}
const rainDrops = new THREE.LineSegments(new THREE.BufferGeometry(), new THREE.LineBasicMaterial({ color: 0xaac4dd, transparent: true, opacity: 0.55 }));
{ const n = 2200, a = new Float32Array(n * 6); for (let i = 0; i < n; i++) { const x = (Math.random() - 0.5) * 80, y = Math.random() * 40, z = (Math.random() - 0.5) * 80; a.set([x, y, z, x, y - 0.6, z], i * 6); } rainDrops.geometry.setAttribute('position', new THREE.BufferAttribute(a, 3)); rainDrops.frustumCulled = false; scene.add(rainDrops); }
function setRain(on) { RAIN = on; rainDrops.visible = on; audio.rain(on ? 1 : 0); applyRainLook(); DROPS.length = 0; }
function applyRainLook() { scene.fog.near = RAIN ? 40 : 120; scene.fog.far = RAIN ? 520 : 1500; if (RAIN && !NIGHT) { scene.fog.color.setHex(0x9aa8b2); WORLD.sky.material.uniforms.top.value.setHex(0x7a8a96); WORLD.sky.material.uniforms.bot.value.setHex(0xa8b4bc); sun.intensity = 0.6; } }
function rainTick(dt) {
  if (!RAIN) return; const a = rainDrops.geometry.attributes.position.array; for (let i = 0; i < a.length; i += 6) { a[i + 1] -= dt * 28; a[i + 4] -= dt * 28; if (a[i + 1] < 0) { a[i + 1] += 40; a[i + 4] += 40; } }
  rainDrops.geometry.attributes.position.needsUpdate = true; rainDrops.position.set(camera.position.x, camera.position.y - 20, camera.position.z);
}
// droplets on the windshield and the wiper that clears them
const DROPS = []; let wiperA = 0, wiperDir = 1;
function windshieldFX(dt) {
  const c = $('#rainfx'), g = c.getContext('2d'); g.clearRect(0, 0, c.width, c.height);
  if (!RAIN || CAM.mode !== 0 || HUDOFF) return;
  const W = c.width, H = c.height; if (DROPS.length < 260 && Math.random() < dt * (BUS.v > 3 ? 60 : 30)) DROPS.push({ x: Math.random() * W, y: Math.random() * H * 0.75, r: 2 + Math.random() * 5 });
  if (BUS.wiper) { wiperA += wiperDir * dt * 2.6; if (wiperA > 1.6) { wiperA = 1.6; wiperDir = -1; } if (wiperA < 0) { wiperA = 0; wiperDir = 1; } }
  const pivots = [[W * 0.28, H * 1.02], [W * 0.68, H * 1.02]], len = H * 0.92;
  for (let i = DROPS.length - 1; i >= 0; i--) { const d = DROPS[i]; if (BUS.wiper) for (const [px, py] of pivots) { const ang = Math.atan2(d.y - py, d.x - px), da = ang - (-Math.PI + 0.25 + wiperA * 1.65); if (Math.abs(da) < 0.06 && Math.hypot(d.x - px, d.y - py) < len) { DROPS.splice(i, 1); break; } } }
  g.fillStyle = 'rgba(200,220,240,.28)'; g.strokeStyle = 'rgba(255,255,255,.35)'; for (const d of DROPS) { g.beginPath(); g.arc(d.x, d.y, d.r, 0, TAU); g.fill(); g.stroke(); }
  if (!BUS.wiper) { g.fillStyle = 'rgba(150,170,190,.18)'; g.fillRect(0, 0, W, H); }
  g.strokeStyle = '#111'; g.lineWidth = 7; g.lineCap = 'round'; for (const [px, py] of pivots) { const ang = -Math.PI + 0.25 + wiperA * 1.65; g.beginPath(); g.moveTo(px, py); g.lineTo(px + Math.cos(ang) * len, py + Math.sin(ang) * len); g.stroke(); }
}

// ---- HUD ----
const LAMPS = [['indL', '◀', 'g'], ['hazard', '비상', 'r'], ['indR', '▶', 'g'], ['lights', '전조', 'b'], ['wiper', '와이퍼', 'g'], ['doorF', '앞문', 'a'], ['doorR', '뒷문', 'a'], ['park', 'P', 'r'], ['stop', 'STOP', 'r'], ['kneel', '무릎', 'a']];
$('#lamps').innerHTML = LAMPS.map(([k, t, c]) => `<div class="lamp ${c}" data-k="${k}">${t}</div>`).join('');
const lampEls = Object.fromEntries([...document.querySelectorAll('.lamp')].map(e => [e.dataset.k, e]));
let HUDOFF = false;
function hudTick(t) {
  const blink = Math.floor(t * 2.6) % 2 === 0, on = { indL: (BUS.ind === -1 || BUS.hazard) && blink, indR: (BUS.ind === 1 || BUS.hazard) && blink, hazard: BUS.hazard && blink, lights: BUS.lights, wiper: BUS.wiper, doorF: BUS.doors.F > 0.05, doorR: BUS.doors.R > 0.05, park: BUS.park, stop: TRIP.bell, kneel: BUS.kneel > 0.5 };
  for (const [k, e] of Object.entries(lampEls)) e.classList.toggle('on', !!on[k]);
  lampEls.doorR.style.visibility = BUS.spec.doors.includes('R') ? '' : 'hidden'; lampEls.kneel.style.visibility = BUS.spec.kneel ? '' : 'hidden';
  $('#gear').textContent = !BUS.engine ? 'OFF' : BUS.park ? 'P' : BUS.gear === 'D' ? 'D' + (BUS.spec.electric ? '' : BUS.g) : BUS.gear;
  $('#limit').textContent = TRIP.lim || 50;
  const s = nextStop(); $('#rNo').textContent = TRIP.route.no; $('#rName').textContent = TRIP.route.name;
  if (s) { $('#rNext').textContent = (RT.k === RT.stops.length - 1 ? '종점 · ' : '') + s.name; const dist = s.rd - RT.rd; $('#rDist').textContent = dist > 0 ? `${dist > 1000 ? (dist / 1000).toFixed(1) + 'km' : Math.round(dist) + 'm'}` : '도착'; const late = TRIP.t - s.sched; $('#rTime').innerHTML = s.arrived && !s.served && late < -30 ? `<span class="late">출발까지 ${fmtT(-late - 30)}</span>` : late > 0 ? `<span class="late">+${fmtT(late)} 늦음</span>` : `<span class="ok">${fmtT(-late)} 여유</span>`; }
  $('#rClock').textContent = `운행 ${fmtT(TRIP.t)}`;
  $('#pax').textContent = `승객 ${TRIP.onboard.length}/${BUS.spec.cap} · 운임 ${won(TRIP.fares)}`;
  drawGauge(); drawMini();
}
const gctx = $('#gauge').getContext('2d');
function drawGauge() {
  const g = gctx, kmh = Math.abs(BUS.v) * 3.6; g.clearRect(0, 0, 300, 300); g.save(); g.translate(150, 150);
  g.lineWidth = 6; g.strokeStyle = '#2a3631'; g.beginPath(); g.arc(0, 0, 130, Math.PI * 0.75, Math.PI * 2.25); g.stroke();
  const a0 = Math.PI * 0.75, a1 = Math.PI * 2.25, A = v => a0 + (a1 - a0) * v / 100;
  g.strokeStyle = '#ff4b3a'; g.beginPath(); g.arc(0, 0, 130, A(TRIP.lim || 50), a1); g.stroke();
  g.fillStyle = '#cfe0d6'; g.font = 'bold 18px ui-monospace,monospace'; g.textAlign = 'center'; g.textBaseline = 'middle';
  for (let v = 0; v <= 100; v += 10) { const a = A(v); g.strokeStyle = '#cfe0d6'; g.lineWidth = v % 20 ? 2 : 4; g.beginPath(); g.moveTo(Math.cos(a) * 118, Math.sin(a) * 118); g.lineTo(Math.cos(a) * 130, Math.sin(a) * 130); g.stroke(); if (v % 20 === 0) g.fillText(v, Math.cos(a) * 96, Math.sin(a) * 96); }
  // rpm arc
  const rp = clamp(BUS.rpm / 2600, 0, 1); g.lineWidth = 8; g.strokeStyle = BUS.rpm > 2200 ? '#ff4b3a' : '#ffb627'; g.beginPath(); g.arc(0, 0, 70, Math.PI * 0.8, Math.PI * 0.8 + rp * Math.PI * 1.4); g.stroke();
  const a = A(Math.min(105, kmh)); g.strokeStyle = '#fff'; g.lineWidth = 5; g.beginPath(); g.moveTo(-Math.cos(a) * 14, -Math.sin(a) * 14); g.lineTo(Math.cos(a) * 112, Math.sin(a) * 112); g.stroke();
  g.fillStyle = '#ffb627'; g.beginPath(); g.arc(0, 0, 9, 0, TAU); g.fill();
  g.fillStyle = '#fff'; g.font = 'bold 40px ui-monospace,monospace'; g.fillText(Math.round(kmh), 0, 58); g.font = 'bold 15px sans-serif'; g.fillStyle = '#8ea89a'; g.fillText('km/h', 0, 86);
  g.restore();
}
const mctx = $('#mini').getContext('2d');
function drawMini() {
  const g = mctx, W = 440, S = 0.42; g.clearRect(0, 0, W, W); g.save(); g.beginPath(); g.arc(W / 2, W / 2, W / 2 - 4, 0, TAU); g.clip(); g.fillStyle = '#1b2a22'; g.fillRect(0, 0, W, W);
  g.translate(W / 2, W / 2 + 60); g.rotate(BUS.yaw + Math.PI); g.scale(S * 2, S * 2); g.translate(-BUS.pos.x, -BUS.pos.z);
  g.lineCap = g.lineJoin = 'round';
  for (const r of Object.values(ROADS)) { g.strokeStyle = '#5a6a62'; g.lineWidth = 10; g.beginPath(); r.P.forEach((p, i) => i ? g.lineTo(p.x, p.z) : g.moveTo(p.x, p.z)); if (r.closed) g.closePath(); g.stroke(); }
  if (RT.pts.length) { g.strokeStyle = '#3ad07a'; g.lineWidth = 5; g.beginPath(); for (let i = RT.idx; i < RT.pts.length; i += 2) { const p = RT.pts[i].p; i === RT.idx ? g.moveTo(p.x, p.z) : g.lineTo(p.x, p.z); } g.stroke(); }
  RT.stops.forEach((s, i) => { if (i < RT.k) return; const p = RT.pts[s.i].lane; g.fillStyle = i === RT.k ? '#ffb627' : '#cfe0d6'; g.beginPath(); g.arc(p.x, p.z, i === RT.k ? 9 : 6, 0, TAU); g.fill(); });
  g.fillStyle = '#ff4b3a'; g.fillRect(XRAIL.x0 - 5, XRAIL.z0 - 5, 10, 10);
  for (const c of CARS) { g.fillStyle = '#9aa4ac'; g.fillRect(c.pos.x - 2, c.pos.z - 2, 4, 4); }
  g.restore();
  g.save(); g.translate(W / 2, W / 2 + 60); g.fillStyle = '#ffb627'; g.beginPath(); g.moveTo(0, -18); g.lineTo(11, 12); g.lineTo(0, 6); g.lineTo(-11, 12); g.closePath(); g.fill(); g.restore();
  g.strokeStyle = '#2c3a34'; g.lineWidth = 6; g.beginPath(); g.arc(W / 2, W / 2, W / 2 - 4, 0, TAU); g.stroke();
}

// ---- save ----
const SAVEKEY = 'hy-bus-v1';
let SAVE = null;
const freshSave = () => ({ v: 1, money: 1500000, rep: 0, day: 1, trips: 0, pax: 0, km: 0, owned: { old: { paint: null } }, active: 'old', routes: [1], best: {}, sel: 1, seenIntro: false });
function loadSave() { try { const s = JSON.parse(localStorage.getItem(SAVEKEY) || 'null'); if (s && s.v === 1) return s; } catch (e) {} return null; }
function persist() { try { localStorage.setItem(SAVEKEY, JSON.stringify(SAVE)); } catch (e) {} }

// ---- menus ----
const menu = $('#menu');
let MODE = 'boot', PAUSED = false;
function show(html, clear = false) { menu.className = 'screen' + (clear ? ' clear' : ''); menu.innerHTML = html; menu.hidden = false; const b = btns(), d = menu.querySelector('#bGo, #bNext, #bRes, #bHQ, #bCont, #bNew, #bBack, #bB'); FOCUS = Math.max(0, b.indexOf(d)); focusBtn(); }
function hideMenu() { menu.hidden = true; }
let FOCUS = 0;
function btns() { return [...menu.querySelectorAll('.btn:not(:disabled), .sw')]; }
let KBNAV = false;
function focusBtn() { const b = btns(); b.forEach((x, i) => x.classList.toggle('focus', i === FOCUS && (IN.pad || KBNAV))); }
addEventListener('keydown', e => {
  if (menu.hidden) return; const k = e.key, b = btns(); if (!b.length) return;
  const prev = ['ArrowLeft', 'ArrowUp'].includes(k), next = ['ArrowRight', 'ArrowDown', 'Tab'].includes(k);
  if (prev || next) { e.preventDefault(); KBNAV = true; FOCUS = (FOCUS + (next ? 1 : -1) + b.length) % b.length; focusBtn(); b[FOCUS].scrollIntoView({ block: 'nearest' }); audio.select(); }
  else if (k === 'Enter' || k === ' ') { e.preventDefault(); const x = b[FOCUS]; if (x) { audio.select(); x.click(); } }
});
function menuPad() {
  if (menu.hidden) return; const b = btns(); if (!b.length) return;
  if (took('gearD') || took('indL')) { FOCUS = (FOCUS - 1 + b.length) % b.length; focusBtn(); audio.select(); }
  if (took('gearR') || took('indR')) { FOCUS = (FOCUS + 1) % b.length; focusBtn(); audio.select(); }
  if (took('gearN')) { FOCUS = (FOCUS - 1 + b.length) % b.length; focusBtn(); }
  if (took('hazard')) { FOCUS = (FOCUS + 1) % b.length; focusBtn(); }
  if (took('doorF')) { const x = b[FOCUS]; if (x) x.click(); }
  took('ok'); IN.pressed.clear();
}
const KEYHELP = [['가속 / 브레이크', 'W · S (↑ ↓)', 'RT · LT'], ['핸들', 'A · D (← →)', '왼쪽 스틱'], ['시동', 'I', 'View(뒤로)'], ['기어 D · N · R', '1 · 2 · 3', '십자 ↑ · ← · ↓'], ['주차 브레이크', 'Space', 'X'], ['앞문 / 뒷문', 'F / G', 'A / B'], ['깜빡이 왼 / 오', 'Q / E', 'LB / RB'], ['비상등', 'H', '십자 →'], ['전조등', 'L', 'R3'], ['와이퍼', 'K', '—'], ['경적', 'B', 'L3'], ['무릎 꿇기(저상)', 'N', '—'], ['카메라 전환', 'C', 'Y'], ['둘러보기', '마우스 드래그', '오른쪽 스틱'], ['후방 카메라 켜기/끄기', 'M', '—'], ['일시정지', 'Esc / P', 'Start'], ['소리 끄기', 'J', '—']];
const keysHtml = () => `<div class="keys">${KEYHELP.map(([a, b, c]) => `<div><span>${a}</span><span><kbd>${b}</kbd> · <kbd>${c}</kbd></span></div>`).join('')}</div>`;
async function studio() { MODE = 'menu'; show(`<div style="text-align:center"><div style="font-family:var(--f-shout);font-size:64px">시우 공방</div><div style="font-family:var(--f-led);color:var(--amber);letter-spacing:4px">SIWOO WORKSHOP</div></div>`); await sleep(TEST ? 10 : 1600); titleScreen(); }
const sleep = ms => new Promise(r => setTimeout(r, ms));
function titleScreen() {
  MODE = 'menu'; const has = !!loadSave();
  show(`<div class="card" style="text-align:center"><h1>현태의 시골버스</h1><p class="sub">구불군 유일의 버스 회사 「현태운수」 · 운행하고, 돈 벌고, 버스를 늘려라. 예.</p>
  <div class="row" style="justify-content:center">${has ? '<button class="btn a" id="bCont">이어하기</button>' : ''}<button class="btn ${has ? 'd' : 'a'}" id="bNew">새로 시작</button><button class="btn d" id="bKeys">조작법</button></div>
  <p class="sub" style="margin-top:16px">키보드 · 게임패드(엑스박스 배치) 지원 · 크롬/엣지 권장</p></div>`);
  if ($('#bCont')) $('#bCont').onclick = () => { SAVE = loadSave(); hq(); };
  $('#bNew').onclick = () => { SAVE = freshSave(); persist(); intro(); };
  $('#bKeys').onclick = () => { show(`<div class="card"><h2>조작법</h2>${keysHtml()}<div class="row" style="margin-top:14px"><button class="btn" id="bBack">돌아가기</button></div></div>`); $('#bBack').onclick = titleScreen; };
}
async function intro() {
  const lines = [['', '구불군. 인구 3,412명. 길이 구불구불해서 구불군.'], ['', '어느 날, 하나뿐인 버스 회사가 망했다.'], ['군수', '버스가 없으면 할머니들이 장에 못 가요! 누가 노선을 맡아 주시오!'], ['현태', '대형면허 있씁니다. 예.'], ['군수', '…자네, 볶음밥집 하던 사람 아닌가?'], ['현태', '볶음밥은 2인분부터. 버스는 1대부터 시작하겠씁니다. 예.'], ['', '그렇게 「현태운수」가 생겼다. 버스는 할머니가 타던 1998년식 한 대.']];
  for (const [n, t] of lines) { await new Promise(r => { show(`<div class="card" style="max-width:720px"><p style="font-size:22px;line-height:1.6;margin:0 0 14px">${n ? `<b style="color:var(--amber);font-family:var(--f-shout);font-weight:400">${n}</b><br>` : ''}${t}</p><button class="btn" id="bNext">다음 ▶</button></div>`); $('#bNext').onclick = r; if (TEST) r(); }); }
  SAVE.seenIntro = true; persist(); hq();
}
let TAB = 'run';
function hq() {
  MODE = 'menu'; $('#hud').hidden = true; setRain(false); audio.engine(false, 0, 0); const S = SAVE, route = ROUTES.find(r => r.id === S.sel) || ROUTES[0], bus = BUSES[S.active];
  const stars = n => '★'.repeat(n) + '☆'.repeat(5 - n);
  let body = '';
  if (TAB === 'run') body = `<div class="list">${ROUTES.map(r => { const own = S.routes.includes(r.id), canBuy = S.money >= r.price && S.rep >= r.rep;
      return `<div class="item ${S.sel === r.id ? 'sel' : ''}"><h3><span class="no" style="background:var(--green);color:#05170e;border-radius:6px;padding:0 8px;margin-right:6px;font-size:16px">${r.no}</span>${r.name}</h3><div class="d">${r.desc}</div><div class="d">요금 ${won(r.fare)} · 군 보조금 ${won(r.subsidy)}${S.best[r.id] ? ` · 최고 ${stars(S.best[r.id])}` : ''}</div>
      ${own ? `<button class="btn ${S.sel === r.id ? 'a' : 'd'}" data-sel="${r.id}">${S.sel === r.id ? '선택됨' : '선택'}</button>` : `<button class="btn d" data-buy="${r.id}" ${canBuy ? '' : 'disabled'}>노선 인수 ${won(r.price)} · 평판 ${r.rep}+</button>`}</div>`; }).join('')}</div>
    <div class="row" style="margin-top:16px"><button class="btn a" id="bGo">🚌 ${route.no}번 ${route.name} 운행 시작 (${bus.name})</button></div>`;
  else if (TAB === 'garage') body = `<div class="list">${Object.entries(BUSES).map(([id, b]) => { const own = S.owned[id];
      return `<div class="item ${S.active === id ? 'sel' : ''}"><h3>${b.name}</h3><div class="d">${b.kind} · ${b.len}m · 정원 ${b.cap} · 최고 ${b.maxV}km/h · 문 ${b.doors.length}개 · 연료비 ${won(b.fuel)}/km</div><div class="d">${b.desc}</div>
      ${own ? `<div class="swatches">${PAINTS.map((c, i) => `<button class="sw ${((own.paint ?? b.color) === c) ? 'on' : ''}" style="background:#${c.toString(16).padStart(6, '0')}" data-paint="${id}:${i}" title="도색 ${won(300000)}"></button>`).join('')}</div><button class="btn ${S.active === id ? 'a' : 'd'}" data-act="${id}">${S.active === id ? '운행 중인 버스' : '이 버스로 운행'}</button>` : `<button class="btn d" data-bus="${id}" ${S.money >= b.price ? '' : 'disabled'}>구입 ${won(b.price)}</button>`}</div>`; }).join('')}</div><p class="sub" style="margin-top:10px">도색 변경 ${won(300000)}</p>`;
  else if (TAB === 'rec') body = `<div class="stats"><div class="stat"><b>${S.trips}</b><span>운행 횟수</span></div><div class="stat"><b>${S.pax.toLocaleString()}</b><span>태운 승객</span></div><div class="stat"><b>${(S.km / 1000).toFixed(1)}km</b><span>달린 거리</span></div></div><table class="rep">${ROUTES.map(r => `<tr><td>${r.no} ${r.name}</td><td>${S.best[r.id] ? stars(S.best[r.id]) : '—'}</td></tr>`).join('')}</table>`;
  else body = keysHtml() + `<p class="sub" style="margin-top:12px">정류장 노란 칸에 버스를 대고, 완전히 멈춘 뒤 문을 연다. 하차벨이 울리면 꼭 서야 한다. 철길 건널목 앞에서는 반드시 일시정지.</p>`;
  show(`<div class="card"><h1 style="font-size:34px">현태운수 <span style="font-size:18px;color:var(--muted)">구불군 공영버스 · ${S.day}일차</span></h1>
    <div class="stats"><div class="stat"><b>${won(S.money)}</b><span>회사 자금</span></div><div class="stat"><b>${S.rep}</b><span>평판</span></div><div class="stat"><b>${Object.keys(S.owned).length}대</b><span>보유 버스</span></div><div class="stat"><b>${S.routes.length}/${ROUTES.length}</b><span>노선</span></div></div>
    <div class="tabs">${[['run', '운행'], ['garage', '차고'], ['rec', '기록'], ['keys', '조작법']].map(([k, n]) => `<button class="btn d ${TAB === k ? 'on' : ''}" data-tab="${k}">${n}</button>`).join('')}<button class="btn d" id="bTitle" style="margin-left:auto">타이틀</button></div>${body}</div>`);
  menu.querySelectorAll('[data-tab]').forEach(b => b.onclick = () => { TAB = b.dataset.tab; audio.select(); hq(); });
  menu.querySelectorAll('[data-sel]').forEach(b => b.onclick = () => { S.sel = +b.dataset.sel; persist(); hq(); });
  menu.querySelectorAll('[data-buy]').forEach(b => b.onclick = () => { const r = ROUTES.find(x => x.id === +b.dataset.buy); S.money -= r.price; S.routes.push(r.id); S.sel = r.id; persist(); audio.good(); hq(); });
  menu.querySelectorAll('[data-bus]').forEach(b => b.onclick = () => { const id = b.dataset.bus; S.money -= BUSES[id].price; S.owned[id] = { paint: null }; S.active = id; persist(); audio.good(); hq(); });
  menu.querySelectorAll('[data-act]').forEach(b => b.onclick = () => { S.active = b.dataset.act; persist(); hq(); });
  menu.querySelectorAll('[data-paint]').forEach(b => b.onclick = () => { const [id, i] = b.dataset.paint.split(':'); if (S.money < 300000) { audio.bad(); return; } S.money -= 300000; S.owned[id].paint = PAINTS[+i]; persist(); audio.good(); hq(); });
  if ($('#bGo')) $('#bGo').onclick = () => drive(route);
  $('#bTitle').onclick = titleScreen;
}
function drive(route) {
  hideMenu(); $('#hud').hidden = false; MODE = 'drive'; PAUSED = false;
  makeBus(SAVE.active, SAVE.owned[SAVE.active].paint); startTrip(route); SV.docked = false; SV.busy = 0;
  CAM.mode = 0; CAM.yaw = 0; CAM.pitch = -0.05;
  banner(`${route.no}번 ${route.name} · 첫 정류장 ${RT.stops[0].name}`, 6);
  $('#hint').innerHTML = `시동 <kbd>I</kbd> → 주차 브레이크 해제 <kbd>Space</kbd> → 기어 D <kbd>1</kbd> → 가속 <kbd>W</kbd><br>정류장에서 멈추고 앞문 <kbd>F</kbd> · 뒷문 <kbd>G</kbd> · 카메라 <kbd>C</kbd> · 일시정지 <kbd>Esc</kbd>`;
  if (TRIP.rain) toast('비가 온다. 와이퍼(K)를 켜자', ''); if (route.night) toast('밤길이다. 전조등(L)을 켜고 가자', '');
}
function pauseMenu() {
  PAUSED = true; show(`<div class="card" style="max-width:560px;text-align:center"><h2>일시정지</h2><div class="row" style="justify-content:center"><button class="btn a" id="bRes">계속 운행</button><button class="btn d" id="bK">조작법</button><button class="btn d" id="bQuit">운행 포기</button></div></div>`, true);
  $('#bRes').onclick = () => { hideMenu(); PAUSED = false; };
  $('#bK').onclick = () => { show(`<div class="card"><h2>조작법</h2>${keysHtml()}<div class="row" style="margin-top:14px"><button class="btn" id="bB">돌아가기</button></div></div>`, true); $('#bB').onclick = pauseMenu; };
  $('#bQuit').onclick = () => { RT.state = 'idle'; SAVE.money -= 50000; toast('운행 포기: 위약금 ₩50,000', 'bad'); persist(); for (const c of CARS) scene.remove(c.mesh); CARS.length = 0; hq(); };
}
function showReport() {
  const T = TRIP, sc = tripScore(), S = SAVE, km = BUS.odo / 1000;
  const subsidy = Math.round(T.route.subsidy * (0.5 + sc.punct / 200)), fuel = Math.round(km * BUS.spec.fuel), repair = Math.round(BUS.damage), fines = T.fines.reduce((a, f) => a + f.amt, 0);
  const bonus = sc.stars >= 4 ? Math.round(T.route.subsidy * 0.25) : 0, vip = T.vip > 0 ? 200000 : T.vip < 0 ? -100000 : 0;
  const net = T.fares + subsidy + bonus + vip - fuel - repair - fines;
  S.money += net; S.trips++; S.pax += T.pcount; S.km += BUS.odo; S.rep = Math.max(0, S.rep + (sc.stars - 2)); S.best[T.route.id] = Math.max(S.best[T.route.id] || 0, sc.stars); if (S.trips % 2 === 0) S.day++; persist();
  const row = (a, b, cls = '') => `<tr><td>${a}</td><td class="${cls}">${b}</td></tr>`;
  const ev = T.events, issues = [ev.speed && `과속 ${ev.speed}`, ev.crash && `충돌 ${ev.crash}`, ev.brake && `급정거 ${ev.brake}`, ev.curve && `급커브 ${ev.curve}`, ev.skip && `무정차 ${ev.skip}`, ev.xing && `건널목 ${ev.xing}`, ev.gate && `차단기 ${ev.gate}`, ev.early && `조기출발 ${ev.early}`].filter(Boolean).join(' · ') || '없음. 완벽한 운행이었다. 예.';
  MODE = 'menu'; $('#hud').hidden = true; window.__report = { net, stars: sc.stars, sc };
  show(`<div class="card" style="max-width:720px"><h2>${T.route.no}번 ${T.route.name} 운행 결과 <span style="color:var(--amber)">${'★'.repeat(sc.stars)}${'☆'.repeat(5 - sc.stars)}</span></h2>
    <div class="stats"><div class="stat"><b>${Math.round(sc.punct)}</b><span>정시성</span></div><div class="stat"><b>${Math.round(sc.safety)}</b><span>안전</span></div><div class="stat"><b>${Math.round(sc.service)}</b><span>서비스</span></div><div class="stat"><b>${Math.round(sc.comfort)}</b><span>승차감</span></div></div>
    <table class="rep">${row(`운임 수입 (${T.pcount}명${T.freeRides ? `, 닭 ${T.freeRides * 3}마리 무료` : ''})`, won(T.fares), 'pos')}${row('군 보조금 (정시성 반영)', won(subsidy), 'pos')}${bonus ? row('우수 운행 보너스', won(bonus), 'pos') : ''}${vip ? row(vip > 0 ? '군수님 만족' : '군수님 불만', won(vip), vip > 0 ? 'pos' : 'neg') : ''}${row(`연료비 (${km.toFixed(1)}km)`, won(-fuel), 'neg')}${repair ? row('수리비', won(-repair), 'neg') : ''}${fines ? row(`과태료·벌점 (${T.fines.length}건)`, won(-fines), 'neg') : ''}${row('<b>순이익</b>', won(net), net >= 0 ? 'pos' : 'neg')}</table>
    <p class="sub" style="margin-top:10px">평균 지연 ${fmtT(sc.avgLate)} · 지적 사항: ${issues}</p><div class="row"><button class="btn a" id="bHQ">차고로</button></div></div>`);
  $('#bHQ').onclick = hq; for (const c of CARS) scene.remove(c.mesh); CARS.length = 0;
}

// ---- actions while driving ----
function actions() {
  if (took('pause')) { if (PAUSED) { hideMenu(); PAUSED = false; } else pauseMenu(); return; }
  if (PAUSED) { menuPad(); return; }
  if (took('mute')) toast(audio.toggle() ? '소리 끔' : '소리 켬');
  if (took('engine')) { if (!BUS.engine) { BUS.engine = true; audio.start(); toast(BUS.spec.electric ? '전원 ON 🔋' : '부르릉… 시동 ON', 'good'); } else if (Math.abs(BUS.v) < 0.5) { BUS.engine = false; toast('시동 OFF'); } }
  if (took('gearD')) { if (Math.abs(BUS.v) < 1 || BUS.gear !== 'R') { BUS.gear = 'D'; BUS.g = 1; } else toast('멈춘 다음 바꾸씹시오'); }
  if (took('gearN')) BUS.gear = 'N';
  if (took('gearR')) { if (Math.abs(BUS.v) < 0.5) { BUS.gear = 'R'; audio.reverse(); } else toast('멈춘 다음 후진 기어'); }
  if (took('park')) { BUS.park = !BUS.park; audio.noise(0.25, 0.2, 700, 0, 'lowpass'); }
  if (took('doorF')) { if (Math.abs(BUS.v) < 0.6) { BUS.doorT.F = BUS.doorT.F ? 0 : 1; audio.door(); } else toast('달리는 중엔 문을 못 엽니다'); }
  if (took('doorR')) { if (!BUS.spec.doors.includes('R')) toast('이 버스는 앞문뿐이다'); else if (Math.abs(BUS.v) < 0.6) { BUS.doorT.R = BUS.doorT.R ? 0 : 1; audio.door(); } }
  if (took('indL')) { BUS.ind = BUS.ind === -1 ? 0 : -1; if (BUS.ind === -1) TRIP.indLeftT = TRIP.t; }
  if (took('indR')) BUS.ind = BUS.ind === 1 ? 0 : 1;
  if (took('hazard')) BUS.hazard = !BUS.hazard;
  if (took('lights')) BUS.lights = !BUS.lights;
  if (took('wiper')) BUS.wiper = !BUS.wiper;
  if (took('horn')) { audio.horn(true); for (const c of CARS) if (c.pos.distanceTo(BUS.pos) < 25) c.vmax = Math.min(c.vmax + 1, 18); }
  if (took('kneel')) { if (BUS.spec.kneel) { BUS.kneelOn = !BUS.kneelOn; audio.kneel(); } }
  if (took('cam')) { CAM.mode = (CAM.mode + 1) % 3; toast('카메라: ' + CAM_NAMES[CAM.mode]); }
  if (took('mirror')) { CAM.mirror = !CAM.mirror; }
  if (took('reset')) { if (Math.abs(BUS.v) < 1) { const P = RT.pts[RT.idx]; BUS.pos.copy(P.lane); BUS.yaw = Math.atan2(P.h.x, P.h.z); BUS.v = 0; fine('reset', 30000, '견인차 호출', 1); } }
  took('info'); took('ok');
  // a turn signal cancels itself after the turn, like the real one
  if (BUS.ind && Math.abs(BUS.steer) > 0.25) BUS.indArmed = true; if (BUS.indArmed && Math.abs(BUS.steer) < 0.05) { BUS.ind = 0; BUS.indArmed = false; }
}

// ---- main loop ----
let last = performance.now(), T = 0, FRAMES = 0;
const mirrorBox = document.createElement('div'); mirrorBox.style.cssText = 'position:fixed;border:3px solid #111;border-radius:10px;pointer-events:none;box-shadow:0 0 0 2px #2c3a34'; mirrorBox.hidden = true; document.body.appendChild(mirrorBox);
const mirrorLbl = document.createElement('div'); mirrorLbl.textContent = '우측 후방'; mirrorLbl.style.cssText = 'position:absolute;left:6px;top:4px;font-size:11px;font-weight:800;color:#fff;text-shadow:0 1px 2px #000'; mirrorBox.appendChild(mirrorLbl);
function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000) * (AA.speed || 1); last = now; T += dt;
  pollInput(dt);
  if (MODE === 'drive') {
    actions();
    if (!PAUSED) { for (let i = 0; i < 2; i++) busStep(dt / 2); trafficTick(dt); tripTick(dt); }
    railTick(PAUSED ? 0 : dt, BUS.pos); busVisual(dt, T); camStep(dt); rainTick(dt); hudTick(T);
  } else { menuPad(); if (BUS.root) { railTick(dt, null); busVisual(dt, T); BUS.yaw += dt * 0.08; CAM.mode = 1; CAM.cpitch = 0.25; CAM.dist = 20; camStep(dt); } }
  if (bannerT > 0 && (bannerT -= dt) <= 0) $('#banner').hidden = true;
  if (centerT > 0 && (centerT -= dt) <= 0) $('#center').hidden = true;
  windshieldFX(dt);
  FRAMES = (FRAMES || 0) + 1; if (AA.norender && FRAMES % 30) { requestAnimationFrame(frame); return; }
  renderer.setScissorTest(false); renderer.setViewport(0, 0, innerWidth, innerHeight); renderer.render(scene, camera);
  const showMirror = MODE === 'drive' && CAM.mirror && CAM.mode === 0;
  mirrorBox.hidden = !showMirror;
  if (showMirror) { const w = Math.min(280, innerWidth * 0.22), h = w * 0.62, x = innerWidth - w - 18, y = innerHeight * 0.42; mirrorCam.aspect = w / h; mirrorCam.updateProjectionMatrix(); renderer.setScissorTest(true); renderer.setScissor(x, y, w, h); renderer.setViewport(x, y, w, h); rainDrops.visible = false; renderer.render(scene, mirrorCam); rainDrops.visible = RAIN; renderer.setScissorTest(false); Object.assign(mirrorBox.style, { left: x + 'px', bottom: y + 'px', width: w + 'px', height: h + 'px' }); }
  requestAnimationFrame(frame);
}
// ---- boot ----
resize(); buildWorld(); makeBus('old'); placeBus(ROADS.loop, STOPS.term.d - 30); setNight(false);
Object.assign(TRIP, { route: ROUTES[0], onboard: [], fares: 0, t: 0, lim: 50 }); window.__ready = 1;
requestAnimationFrame(frame);
if (AA.go) { SAVE = freshSave(); drive(ROUTES.find(r => r.id === (AA.route || 1))); } else studio();
window.__api = { BUS, TRIP, RT, SV, IN, ROUTES, XRAIL, CARS, STOPS, ROADS, drive, hq, get SAVE() { return SAVE; }, set SAVE(v) { SAVE = v; }, freshSave, took, nextStop, camera, CAM };
