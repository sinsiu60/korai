// =============================================================
// sound.js — Web Audio로 효과음을 즉석 생성 (외부 파일 없음)
//   - iOS는 사용자 터치 이후에만 소리가 나므로 첫 터치에 unlock()을 호출한다.
// =============================================================

const Sfx = {
  ctx: null,
  master: null,
  enabled: true,
  _lastTick: 0,

  unlock() {
    try {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        this.ctx = new AC();
        this.master = this.ctx.createGain();
        this.master.gain.value = 0.35;
        this.master.connect(this.ctx.destination);
      }
      if (this.ctx.state === 'suspended') this.ctx.resume();
    } catch (e) {
      // 소리가 안 나도 게임은 계속
      this.ctx = null;
    }
  },

  // 기본 음 하나: 주파수 f에서 f2로 미끄러지며 dur초 동안
  _tone(type, f, f2, dur, vol = 0.5, delay = 0) {
    if (!this.enabled || !this.ctx) return;
    const t = this.ctx.currentTime + delay;
    const o = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    o.type = type;
    o.frequency.setValueAtTime(f, t);
    if (f2 !== f) o.frequency.exponentialRampToValueAtTime(f2, t + dur);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(this.master);
    o.start(t);
    o.stop(t + dur + 0.02);
  },

  // 짧은 잡음 (철컥 소리용)
  _noise(dur, vol = 0.3, freq = 1800) {
    if (!this.enabled || !this.ctx) return;
    const t = this.ctx.currentTime;
    const len = Math.floor(this.ctx.sampleRate * dur);
    const buf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const src = this.ctx.createBufferSource();
    src.buffer = buf;
    const f = this.ctx.createBiquadFilter();
    f.type = 'bandpass'; f.frequency.value = freq; f.Q.value = 1.2;
    const g = this.ctx.createGain();
    g.gain.value = vol;
    src.connect(f); f.connect(g); g.connect(this.master);
    src.start(t);
  },

  // 건물 배치: 묵직한 '퉁' + 철컥
  place() {
    this._tone('triangle', 220, 140, 0.12, 0.5);
    this._noise(0.05, 0.25, 2500);
  },

  // 컨베이어 연속 설치: 짧은 틱 (너무 자주 울리지 않게 제한)
  tick() {
    const now = performance.now();
    if (now - this._lastTick < 45) return;
    this._lastTick = now;
    this._tone('square', 900, 700, 0.03, 0.12);
  },

  remove() {
    this._tone('sawtooth', 300, 90, 0.18, 0.25);
    this._noise(0.12, 0.2, 900);
  },

  rotate() {
    this._tone('triangle', 520, 660, 0.06, 0.25);
  },

  error() {
    this._tone('square', 180, 160, 0.09, 0.2);
    this._tone('square', 140, 120, 0.12, 0.2, 0.09);
  },

  click() {
    this._tone('sine', 700, 900, 0.04, 0.2);
  },

  // 납품 (2단계에서 사용)
  deliver() {
    this._tone('sine', 880, 880, 0.08, 0.25);
    this._tone('sine', 1320, 1320, 0.1, 0.2, 0.06);
  },

  // 차단기 트립 (4단계에서 사용)
  trip() {
    this._noise(0.25, 0.5, 600);
    this._tone('sawtooth', 120, 40, 0.5, 0.4);
  },
};
