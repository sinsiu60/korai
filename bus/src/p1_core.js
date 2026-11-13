/* ========= 현태의 시골버스 · 시우 공방 ========= */
import * as THREE from 'three';
const $ = s => document.querySelector(s);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, k) => a + (b - a) * k;
const TAU = Math.PI * 2;
const TEST = location.hash.includes('test');
const AA = (() => { try { return JSON.parse(decodeURIComponent(location.hash.split('aa=')[1] || '{}')); } catch (e) { return {}; } })();
const won = n => (n < 0 ? '-' : '') + '₩' + Math.abs(Math.round(n)).toLocaleString();
const fmtT = s => { const neg = s < 0; s = Math.abs(Math.round(s)); return (neg ? '-' : '') + Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
const rngOf = s => () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };

// ---- audio: everything is synthesized ----
const audio = (() => {
  let ac = null, master, eng = null, rainG = null, muted = false;
  const init = () => {
    if (ac || TEST) return; try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { return; }
    master = ac.createGain(); master.gain.value = 0.6; master.connect(ac.destination);
    // engine: two detuned saws through a lowpass, plus a rumble
    const g = ac.createGain(); g.gain.value = 0; const f = ac.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 500; f.Q.value = 4;
    const o1 = ac.createOscillator(), o2 = ac.createOscillator(), o3 = ac.createOscillator(); o1.type = 'sawtooth'; o2.type = 'sawtooth'; o3.type = 'square';
    o1.connect(f); o2.connect(f); const g3 = ac.createGain(); g3.gain.value = 0.25; o3.connect(g3); g3.connect(f); f.connect(g); g.connect(master);
    o1.start(); o2.start(); o3.start(); eng = { g, f, o1, o2, o3 };
    // rain bed
    const nb = ac.createBuffer(1, ac.sampleRate * 2, ac.sampleRate), d = nb.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    const ns = ac.createBufferSource(); ns.buffer = nb; ns.loop = true; const nf = ac.createBiquadFilter(); nf.type = 'highpass'; nf.frequency.value = 1200; rainG = ac.createGain(); rainG.gain.value = 0; ns.connect(nf); nf.connect(rainG); rainG.connect(master); ns.start();
  };
  const tone = (fr, dur, vol = 0.2, type = 'sine', delay = 0, slide = 0) => {
    if (!ac || muted) return; const t = ac.currentTime + delay, o = ac.createOscillator(), g = ac.createGain(); o.type = type; o.frequency.setValueAtTime(fr, t); if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, fr + slide), t + dur);
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(vol, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0008, t + dur); o.connect(g); g.connect(master); o.start(t); o.stop(t + dur + 0.05);
  };
  const noise = (dur, vol = 0.2, f0 = 2000, delay = 0, type = 'bandpass') => {
    if (!ac || muted) return; const t = ac.currentTime + delay, b = ac.createBuffer(1, Math.ceil(ac.sampleRate * dur), ac.sampleRate), d = b.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / d.length);
    const s = ac.createBufferSource(); s.buffer = b; const f = ac.createBiquadFilter(); f.type = type; f.frequency.value = f0; const g = ac.createGain(); g.gain.value = vol; s.connect(f); f.connect(g); g.connect(master); s.start(t);
  };
  return {
    init, tone, noise,
    get ok() { return !!ac; },
    toggle() { muted = !muted; if (master) master.gain.value = muted ? 0 : 0.6; return muted; },
    engine(on, rpm, load, electric) {
      if (!eng) return; const t = ac.currentTime, base = electric ? rpm / 18 : rpm / 30;
      eng.o1.frequency.setTargetAtTime(base, t, 0.05); eng.o2.frequency.setTargetAtTime(base * 1.007 + 0.6, t, 0.05); eng.o3.frequency.setTargetAtTime(electric ? base * 4 : base / 2, t, 0.05);
      eng.f.frequency.setTargetAtTime(electric ? 900 + rpm / 3 : 260 + rpm * 0.35 + load * 500, t, 0.08);
      eng.g.gain.setTargetAtTime(on && !muted ? (electric ? 0.03 + load * 0.04 : 0.06 + load * 0.08) : 0, t, 0.12);
    },
    rain(v) { if (rainG) rainG.gain.setTargetAtTime(muted ? 0 : v * 0.05, ac.currentTime, 0.5); },
    door() { noise(0.5, 0.25, 900, 0, 'lowpass'); tone(160, 0.25, 0.06, 'square', 0.05, -60); },
    tick() { tone(1500, 0.03, 0.05, 'square'); },
    tock() { tone(1100, 0.03, 0.05, 'square'); },
    bell() { tone(1320, 0.5, 0.18, 'sine'); tone(1760, 0.6, 0.1, 'sine', 0.05); },
    chime() { [784, 988, 1175, 1568].forEach((f, i) => tone(f, 0.5, 0.1, 'triangle', i * 0.14)); },
    horn(on) { if (on) { tone(392, 0.35, 0.16, 'square'); tone(494, 0.35, 0.13, 'square'); } },
    crash() { noise(0.6, 0.6, 300, 0, 'lowpass'); tone(80, 0.4, 0.3, 'sawtooth', 0, -40); },
    start() { tone(60, 1.0, 0.2, 'sawtooth', 0, 50); noise(0.8, 0.15, 400, 0, 'lowpass'); },
    reverse() { tone(1000, 0.25, 0.08, 'square'); },
    select() { tone(660, 0.05, 0.08, 'triangle'); },
    coin() { tone(1400, 0.06, 0.08, 'square'); tone(1900, 0.1, 0.06, 'square', 0.06); },
    card() { tone(1800, 0.08, 0.07, 'sine'); },
    bad() { tone(220, 0.25, 0.1, 'square', 0, -60); },
    good() { tone(880, 0.1, 0.08, 'triangle'); tone(1320, 0.15, 0.08, 'triangle', 0.08); },
    xbell(i) { tone(i % 2 ? 900 : 760, 0.18, 0.08, 'square'); },
    train() { tone(330, 1.2, 0.12, 'sawtooth'); tone(415, 1.2, 0.1, 'sawtooth'); },
    kneel() { noise(0.7, 0.18, 600, 0, 'lowpass'); },
  };
})();
addEventListener('pointerdown', () => audio.init());
addEventListener('keydown', () => audio.init());

// ---- input: keyboard + gamepad, one state ----
const IN = { thr: 0, brk: 0, steer: 0, lookX: 0, lookY: 0, pressed: new Set(), pad: false, padName: '' };
const KEYS = new Set();
const KEYMAP = {
  // edge-triggered actions
  f: 'doorF', g: 'doorR', q: 'indL', e: 'indR', h: 'hazard', i: 'engine', l: 'lights', k: 'wiper', b: 'horn', c: 'cam', ' ': 'park', '1': 'gearD', '2': 'gearN', '3': 'gearR',
  m: 'mirror', tab: 'info', escape: 'pause', p: 'pause', enter: 'ok', n: 'kneel', j: 'mute', r: 'reset',
};
addEventListener('keydown', e => {
  const k = e.key.toLowerCase(); if (['arrowup', 'arrowdown', 'arrowleft', 'arrowright', ' ', 'tab'].includes(k)) e.preventDefault();
  if (!KEYS.has(k) && KEYMAP[k]) IN.pressed.add(KEYMAP[k]); KEYS.add(k);
});
addEventListener('keyup', e => KEYS.delete(e.key.toLowerCase()));
addEventListener('blur', () => KEYS.clear());
let kSteer = 0;
const PADMAP = { 0: 'doorF', 1: 'doorR', 2: 'park', 3: 'cam', 4: 'indL', 5: 'indR', 8: 'engine', 9: 'pause', 10: 'horn', 11: 'lights', 12: 'gearD', 13: 'gearR', 14: 'gearN', 15: 'hazard' };
const padPrev = {};
function pollInput(dt) {
  const up = KEYS.has('w') || KEYS.has('arrowup'), dn = KEYS.has('s') || KEYS.has('arrowdown');
  const lf = KEYS.has('a') || KEYS.has('arrowleft'), rt = KEYS.has('d') || KEYS.has('arrowright');
  // keyboard steering eases in and self-centres, like holding a real wheel
  const target = (rt ? 1 : 0) - (lf ? 1 : 0);
  if (target) kSteer = clamp(kSteer + target * dt * 1.6, -1, 1); else kSteer *= Math.max(0, 1 - dt * 3);
  let thr = up ? 1 : 0, brk = dn ? 1 : 0, steer = kSteer, lx = 0, ly = 0;
  const pads = navigator.getGamepads ? navigator.getGamepads() : [];
  for (const p of pads) {
    if (!p) continue; IN.pad = true; IN.padName = p.id;
    const dz = v => Math.abs(v) < 0.12 ? 0 : (v - Math.sign(v) * 0.12) / 0.88;
    const sx = dz(p.axes[0] || 0); if (sx) steer = Math.sign(sx) * Math.pow(Math.abs(sx), 1.6);
    lx = dz(p.axes[2] || 0); ly = dz(p.axes[3] || 0);
    const rtv = p.buttons[7] ? p.buttons[7].value : 0, ltv = p.buttons[6] ? p.buttons[6].value : 0;
    thr = Math.max(thr, rtv); brk = Math.max(brk, ltv);
    for (const [bi, act] of Object.entries(PADMAP)) { const b = p.buttons[bi], now = !!(b && b.pressed), key = p.index + ':' + bi; if (now && !padPrev[key]) IN.pressed.add(act); padPrev[key] = now; }
    break;
  }
  IN.thr = thr; IN.brk = brk; IN.steer = steer; IN.lookX = lx; IN.lookY = ly;
  if (window.__auto) { window.__auto(); IN.thr = window.__thr || 0; IN.brk = window.__brk || 0; IN.steer = window.__steer || 0; }
}
addEventListener('gamepadconnected', e => { IN.pad = true; IN.padName = e.gamepad.id; toast('🎮 게임패드 연결됨', 'good'); });
const took = a => { if (IN.pressed.has(a)) { IN.pressed.delete(a); return true; } return false; };

// ---- little DOM helpers ----
function toast(t, kind = '') { const box = $('#toasts'); if (!box) return; const d = document.createElement('div'); d.className = 'toast ' + kind; d.textContent = t; box.prepend(d); while (box.children.length > 5) box.lastChild.remove(); setTimeout(() => d.remove(), 3800); }
function chat(name, t) { const box = $('#chat'); const p = document.createElement('p'); const b = document.createElement('b'); b.textContent = name; p.appendChild(b); p.appendChild(document.createTextNode(t)); box.appendChild(p); while (box.children.length > 4) box.firstChild.remove(); setTimeout(() => p.remove(), 9000); }
let bannerT = 0; function banner(t, sec = 5) { const b = $('#banner'); b.textContent = t; b.hidden = false; bannerT = sec; }
let centerT = 0; function center(t, sec = 1.6, col = '#fff') { const c = $('#center'); c.textContent = t; c.style.color = col; c.hidden = false; centerT = sec; }
