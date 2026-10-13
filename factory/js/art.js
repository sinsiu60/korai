// =============================================================
// art.js — 외부 에셋 없이 Canvas 2D로 타일·건물·아이콘을 그린다
//   - 같은 그리기 함수로 Phaser 텍스처와 툴바 아이콘을 모두 만든다.
//   - 건물은 '오른쪽(동)을 바라보는' 모습으로 그리고, 회전은 스프라이트 회전으로 처리.
// =============================================================

const Art = {
  // 바탕 색상 팔레트 (밝은 플랫 스타일)
  COLORS: {
    ground: '#e6eaef',
    ground2: '#e1e6ec',
    gridLine: 'rgba(120,135,155,0.22)',
    outline: '#2b3240',
    ok: 0x3ddc84,
    bad: 0xff5a5a,
    select: 0x2f80ff,
  },

  makeCanvas(w, h) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    return c;
  },

  roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  },

  // ---------------------------------------------------------
  // 지형 타일
  // ---------------------------------------------------------
  drawGround(ctx, alt) {
    ctx.fillStyle = alt ? this.COLORS.ground2 : this.COLORS.ground;
    ctx.fillRect(0, 0, TILE, TILE);
    // 격자선: 위·왼쪽 테두리만 그려 이웃 타일과 겹치지 않게
    ctx.fillStyle = this.COLORS.gridLine;
    ctx.fillRect(0, 0, TILE, 2);
    ctx.fillRect(0, 0, 2, TILE);
  },

  drawOre(ctx, oreDef, variant) {
    this.drawGround(ctx, false);
    // 광석 지대 바닥 물들이기
    ctx.fillStyle = oreDef.color;
    ctx.globalAlpha = 0.22;
    ctx.fillRect(2, 2, TILE - 2, TILE - 2);
    ctx.globalAlpha = 1;
    // 돌 덩어리 (변형마다 배치가 다름)
    const rand = Util.rng(1234 + variant * 77 + oreDef.id.length * 13);
    const n = 4 + variant;
    for (let k = 0; k < n; k++) {
      const x = 10 + rand() * (TILE - 20);
      const y = 10 + rand() * (TILE - 20);
      const r = 5 + rand() * 6;
      ctx.fillStyle = oreDef.dark;
      this._rock(ctx, x + 1.5, y + 2, r, rand);
      ctx.fillStyle = oreDef.color;
      this._rock(ctx, x, y, r, rand);
    }
  },

  // 다각형 돌 모양
  _rock(ctx, x, y, r, rand) {
    const sides = 6;
    ctx.beginPath();
    for (let i = 0; i < sides; i++) {
      const a = (i / sides) * Math.PI * 2;
      const rr = r * (0.8 + ((i * 37) % 5) * 0.06);
      const px = x + Math.cos(a) * rr, py = y + Math.sin(a) * rr;
      if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.fill();
  },

  /**
   * 타일셋 텍스처를 만든다. 확대/축소 시 이음매가 보이지 않도록 1px 테두리를 늘려(extrude) 둔다.
   * 순서: 0=바닥A, 1=바닥B, 이후 광석마다 변형 2종
   */
  buildTileset(scene, mapDef) {
    const tiles = [];
    const mk = (fn) => { const c = this.makeCanvas(TILE, TILE); fn(c.getContext('2d')); tiles.push(c); };
    mk(ctx => this.drawGround(ctx, false));
    mk(ctx => this.drawGround(ctx, true));
    mapDef.ores.forEach(o => {
      mk(ctx => this.drawOre(ctx, o, 0));
      mk(ctx => this.drawOre(ctx, o, 1));
    });

    const cell = TILE + 2;
    const atlas = this.makeCanvas(cell * tiles.length, cell);
    const a = atlas.getContext('2d');
    tiles.forEach((t, i) => {
      const dx = i * cell + 1, dy = 1;
      a.drawImage(t, dx, dy);
      // 가장자리 1px 복사 (extrude)
      a.drawImage(t, 0, 0, TILE, 1, dx, dy - 1, TILE, 1);
      a.drawImage(t, 0, TILE - 1, TILE, 1, dx, dy + TILE, TILE, 1);
      a.drawImage(t, 0, 0, 1, TILE, dx - 1, dy, 1, TILE);
      a.drawImage(t, TILE - 1, 0, 1, TILE, dx + TILE, dy, 1, TILE);
    });
    if (scene.textures.exists('tileset')) scene.textures.remove('tileset');
    scene.textures.addCanvas('tileset', atlas);
    return { key: 'tileset', margin: 1, spacing: 2, count: tiles.length };
  },

  // ---------------------------------------------------------
  // 건물
  // ---------------------------------------------------------
  // 건물 하나를 ctx에 그린다. w,h는 픽셀 크기(오른쪽 방향 기준).
  drawBuilding(ctx, type, def, w, h) {
    const fn = this.buildingDrawers[type] || this.buildingDrawers._default;
    fn.call(this, ctx, def, w, h);
  },

  buildingDrawers: {
    // 컨베이어: 어두운 벨트 위 노란 화살표
    conveyor(ctx, def, w, h) {
      const c = def.colors;
      const pad = w * 0.06;
      ctx.fillStyle = c.body;
      this.roundRect(ctx, pad * 0.3, pad, w - pad * 0.6, h - pad * 2, w * 0.1);
      ctx.fill();
      // 레일 테두리
      ctx.fillStyle = 'rgba(255,255,255,0.12)';
      ctx.fillRect(pad * 0.3, pad + h * 0.06, w - pad * 0.6, h * 0.05);
      ctx.fillRect(pad * 0.3, h - pad - h * 0.11, w - pad * 0.6, h * 0.05);
      // 진행 방향 화살표(쉐브론)
      ctx.strokeStyle = c.accent;
      ctx.lineWidth = w * 0.09;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      const cy = h / 2, s = h * 0.17;
      [0.36, 0.64].forEach(fx => {
        const x = w * fx;
        ctx.beginPath();
        ctx.moveTo(x - s * 0.7, cy - s);
        ctx.lineTo(x + s * 0.5, cy);
        ctx.lineTo(x - s * 0.7, cy + s);
        ctx.stroke();
      });
    },

    // 채굴기: 주황 몸체 + 회전 드릴 + 출력 화살표
    miner(ctx, def, w, h) {
      const c = def.colors;
      const p = w * 0.05;
      ctx.fillStyle = Art.COLORS.outline;
      Art.roundRect(ctx, p, p, w - p * 2, h - p * 2, w * 0.12);
      ctx.fill();
      ctx.fillStyle = c.body;
      Art.roundRect(ctx, p * 1.8, p * 1.8, w - p * 3.6, h - p * 3.6, w * 0.1);
      ctx.fill();
      // 드릴 원판
      const cx = w * 0.45, cy = h / 2, r = w * 0.24;
      ctx.fillStyle = c.accent;
      ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#d8dde4';
      for (let k = 0; k < 3; k++) {
        const a = k * Math.PI * 2 / 3;
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.arc(cx, cy, r * 0.85, a, a + 0.7);
        ctx.closePath();
        ctx.fill();
      }
      ctx.fillStyle = c.accent;
      ctx.beginPath(); ctx.arc(cx, cy, r * 0.25, 0, Math.PI * 2); ctx.fill();
      // 출력 화살표 (오른쪽)
      Art._arrow(ctx, w * 0.8, cy, w * 0.11, '#ffffff', Art.COLORS.outline);
    },

    // 납품소: 청록 큰 건물 + 중앙 상자 마크
    hub(ctx, def, w, h) {
      const c = def.colors;
      const p = w * 0.03;
      ctx.fillStyle = Art.COLORS.outline;
      Art.roundRect(ctx, p, p, w - p * 2, h - p * 2, w * 0.08);
      ctx.fill();
      ctx.fillStyle = c.body;
      Art.roundRect(ctx, p * 2, p * 2, w - p * 4, h - p * 4, w * 0.07);
      ctx.fill();
      // 사방 입구(밝은 홈)
      ctx.fillStyle = 'rgba(0,0,0,0.18)';
      const slot = w * 0.16;
      for (let k = 0; k < 4; k++) {
        const t = (k + 0.5) / 4;
        ctx.fillRect(w * t - slot / 2, p * 2, slot, w * 0.05);
        ctx.fillRect(w * t - slot / 2, h - p * 2 - w * 0.05, slot, w * 0.05);
        ctx.fillRect(p * 2, h * t - slot / 2, w * 0.05, slot);
        ctx.fillRect(w - p * 2 - w * 0.05, h * t - slot / 2, w * 0.05, slot);
      }
      // 중앙 원 + 상자
      ctx.fillStyle = 'rgba(255,255,255,0.18)';
      ctx.beginPath(); ctx.arc(w / 2, h / 2, w * 0.3, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = c.accent;
      const bs = w * 0.26;
      Art.roundRect(ctx, w / 2 - bs / 2, h / 2 - bs / 2, bs, bs, w * 0.03);
      ctx.fill();
      ctx.fillStyle = c.body;
      ctx.fillRect(w / 2 - bs / 2, h / 2 - w * 0.015, bs, w * 0.03);
      ctx.fillRect(w / 2 - w * 0.015, h / 2 - bs / 2, w * 0.03, bs);
    },

    _default(ctx, def, w, h) {
      ctx.fillStyle = Art.COLORS.outline;
      Art.roundRect(ctx, 2, 2, w - 4, h - 4, 8);
      ctx.fill();
      ctx.fillStyle = (def.colors && def.colors.body) || '#9aa5b5';
      Art.roundRect(ctx, 5, 5, w - 10, h - 10, 6);
      ctx.fill();
      Art._arrow(ctx, w * 0.75, h / 2, Math.min(w, h) * 0.12, '#fff', Art.COLORS.outline);
    },
  },

  // 오른쪽을 가리키는 삼각 화살표
  _arrow(ctx, x, y, s, fill, stroke) {
    ctx.beginPath();
    ctx.moveTo(x + s, y);
    ctx.lineTo(x - s * 0.6, y - s);
    ctx.lineTo(x - s * 0.6, y + s);
    ctx.closePath();
    ctx.fillStyle = fill;
    ctx.fill();
    if (stroke) { ctx.lineWidth = s * 0.3; ctx.strokeStyle = stroke; ctx.stroke(); }
  },

  // 모든 건물 텍스처 등록 (키: b_<type>)
  buildBuildingTextures(scene, buildings) {
    Object.keys(buildings).forEach(type => {
      if (type.startsWith('_')) return;
      const def = buildings[type];
      const w = def.size[0] * TILE, h = def.size[1] * TILE;
      const c = this.makeCanvas(w, h);
      this.drawBuilding(c.getContext('2d'), type, def, w, h);
      const key = 'b_' + type;
      if (scene.textures.exists(key)) scene.textures.remove(key);
      scene.textures.addCanvas(key, c);
    });
  },

  // 툴바용 아이콘 캔버스 (CSS px 크기 × DPR 해상도)
  iconCanvas(type, def, cssSize) {
    const px = Math.round(cssSize * DPR);
    const c = this.makeCanvas(px, px);
    c.style.width = cssSize + 'px';
    c.style.height = cssSize + 'px';
    const ctx = c.getContext('2d');
    const sw = def.size[0], sh = def.size[1];
    const unit = px / Math.max(sw, sh);
    const w = sw * unit, h = sh * unit;
    ctx.save();
    ctx.translate((px - w) / 2, (px - h) / 2);
    this.drawBuilding(ctx, type, def, w, h);
    ctx.restore();
    return c;
  },

  // ---------------------------------------------------------
  // 시우 공방 로고 (캔버스로 그려서 텍스처로 사용)
  // ---------------------------------------------------------
  buildLogo(scene) {
    const s = 512;
    const c = this.makeCanvas(s, s);
    const ctx = c.getContext('2d');
    const cx = s / 2, cy = s / 2;
    // 톱니바퀴
    const teeth = 10, ro = 200, ri = 168;
    ctx.fillStyle = '#f2c14e';
    ctx.beginPath();
    for (let i = 0; i < teeth * 2; i++) {
      const a0 = (i / (teeth * 2)) * Math.PI * 2;
      const a1 = ((i + 1) / (teeth * 2)) * Math.PI * 2;
      const r = i % 2 === 0 ? ro : ri;
      ctx.arc(cx, cy, r, a0, a1);
    }
    ctx.closePath();
    ctx.fill();
    // 안쪽 원
    ctx.fillStyle = '#1b2230';
    ctx.beginPath(); ctx.arc(cx, cy, 140, 0, Math.PI * 2); ctx.fill();
    // 번개 (전력 = 이 게임의 핵심)
    ctx.fillStyle = '#f2c14e';
    ctx.beginPath();
    ctx.moveTo(cx + 22, cy - 110);
    ctx.lineTo(cx - 58, cy + 12);
    ctx.lineTo(cx - 4, cy + 12);
    ctx.lineTo(cx - 26, cy + 110);
    ctx.lineTo(cx + 60, cy - 18);
    ctx.lineTo(cx + 6, cy - 18);
    ctx.closePath();
    ctx.fill();
    if (scene.textures.exists('logo')) scene.textures.remove('logo');
    scene.textures.addCanvas('logo', c);
  },
};

// =============================================================
// 2단계 이후 추가 그래픽: 물류·가공·전력 건물, 곡선 컨베이어, 아이템, 상태 램프
// =============================================================
Object.assign(Art.buildingDrawers, {
  // 분배기: 뒤에서 들어와 세 방향으로 갈라지는 화살표
  splitter(ctx, def, w, h) {
    Art._beltBase(ctx, def, w, h);
    const c = def.colors.accent;
    ctx.strokeStyle = c; ctx.lineWidth = w * 0.09; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    const cx = w * 0.45, cy = h / 2;
    ctx.beginPath();
    ctx.moveTo(w * 0.12, cy); ctx.lineTo(cx, cy);
    ctx.moveTo(cx, cy); ctx.lineTo(w * 0.78, cy);
    ctx.moveTo(cx, cy); ctx.lineTo(cx, h * 0.18);
    ctx.moveTo(cx, cy); ctx.lineTo(cx, h * 0.82);
    ctx.stroke();
    Art._arrow(ctx, w * 0.8, cy, w * 0.1, c);
    Art._arrowDir(ctx, cx, h * 0.16, w * 0.1, -Math.PI / 2, c);
    Art._arrowDir(ctx, cx, h * 0.84, w * 0.1, Math.PI / 2, c);
  },

  // 합류기: 세 방향에서 모여 앞으로 나가는 화살표
  merger(ctx, def, w, h) {
    Art._beltBase(ctx, def, w, h);
    const c = def.colors.accent;
    ctx.strokeStyle = c; ctx.lineWidth = w * 0.09; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    const cx = w * 0.55, cy = h / 2;
    ctx.beginPath();
    ctx.moveTo(w * 0.12, cy); ctx.lineTo(w * 0.74, cy);
    ctx.moveTo(cx, h * 0.14); ctx.lineTo(cx, h * 0.86);
    ctx.stroke();
    Art._arrow(ctx, w * 0.82, cy, w * 0.11, c);
  },

  // 제련소: 붉은 벽돌 화로 + 불꽃 창
  smelter(ctx, def, w, h) {
    Art._machineBase(ctx, def, w, h);
    const cx = w * 0.45, cy = h / 2;
    ctx.fillStyle = '#3a2420';
    Art.roundRect(ctx, cx - w * 0.22, cy - h * 0.2, w * 0.44, h * 0.4, w * 0.08);
    ctx.fill();
    // 불꽃
    ctx.fillStyle = def.colors.accent;
    ctx.beginPath();
    ctx.moveTo(cx, cy - h * 0.14);
    ctx.quadraticCurveTo(cx + w * 0.14, cy + h * 0.02, cx, cy + h * 0.14);
    ctx.quadraticCurveTo(cx - w * 0.14, cy + h * 0.02, cx, cy - h * 0.14);
    ctx.fill();
    ctx.fillStyle = '#ff7a3d';
    ctx.beginPath(); ctx.arc(cx, cy + h * 0.06, w * 0.05, 0, Math.PI * 2); ctx.fill();
    // 벽돌 줄무늬
    ctx.fillStyle = 'rgba(0,0,0,0.15)';
    for (let k = 1; k < 4; k++) ctx.fillRect(w * 0.1, h * k / 4 - 1, w * 0.8, 2);
    Art._arrow(ctx, w * 0.84, cy, w * 0.08, '#ffffff', Art.COLORS.outline);
  },

  // 조립기: 파란 몸체 + 톱니 마크
  assembler(ctx, def, w, h) {
    Art._machineBase(ctx, def, w, h);
    Art._gear(ctx, w * 0.44, h / 2, w * 0.22, 8, def.colors.accent, def.colors.body);
    Art._arrow(ctx, w * 0.84, h / 2, w * 0.08, '#ffffff', Art.COLORS.outline);
  },

  // 창고: 나무 상자
  storage(ctx, def, w, h) {
    Art._machineBase(ctx, def, w, h);
    const p = w * 0.18;
    ctx.strokeStyle = def.colors.accent; ctx.lineWidth = w * 0.05;
    ctx.strokeRect(p, p, w - p * 2.3, h - p * 2);
    ctx.beginPath();
    ctx.moveTo(p, p); ctx.lineTo(w - p * 1.3, h - p);
    ctx.moveTo(w - p * 1.3, p); ctx.lineTo(p, h - p);
    ctx.stroke();
    Art._arrow(ctx, w * 0.86, h / 2, w * 0.07, '#ffffff', Art.COLORS.outline);
  },

  // 전신주: 위에서 본 기둥 + 가로대 + 애자
  pole(ctx, def, w, h) {
    const cx = w / 2, cy = h / 2;
    ctx.fillStyle = 'rgba(0,0,0,0.18)';
    ctx.beginPath(); ctx.arc(cx + 3, cy + 4, w * 0.24, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = def.colors.body;
    ctx.strokeStyle = Art.COLORS.outline; ctx.lineWidth = 3;
    ctx.beginPath(); ctx.arc(cx, cy, w * 0.2, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    ctx.fillStyle = Art.COLORS.outline;
    ctx.fillRect(w * 0.14, cy - h * 0.06, w * 0.72, h * 0.12);
    ctx.fillStyle = def.colors.accent;
    [0.2, 0.8].forEach(f => { ctx.beginPath(); ctx.arc(w * f, cy, w * 0.07, 0, Math.PI * 2); ctx.fill(); });
  },

  // 발전기: 회색 몸체 + 번개 + 굴뚝
  generator(ctx, def, w, h) {
    Art._machineBase(ctx, def, w, h);
    ctx.fillStyle = '#2b3240';
    ctx.beginPath(); ctx.arc(w * 0.74, h * 0.27, w * 0.11, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = '#7d8a93';
    ctx.beginPath(); ctx.arc(w * 0.74, h * 0.27, w * 0.06, 0, Math.PI * 2); ctx.fill();
    Art._bolt(ctx, w * 0.42, h * 0.55, w * 0.42, def.colors.accent);
  },

  // PLC: 작은 제어 상자 + 초록 화면
  plc(ctx, def, w, h) {
    const p = w * 0.08;
    ctx.fillStyle = Art.COLORS.outline;
    Art.roundRect(ctx, p, p, w - p * 2, h - p * 2, w * 0.14); ctx.fill();
    ctx.fillStyle = def.colors.body;
    Art.roundRect(ctx, p * 1.8, p * 1.8, w - p * 3.6, h - p * 3.6, w * 0.1); ctx.fill();
    ctx.fillStyle = '#0d1a12';
    ctx.fillRect(w * 0.24, h * 0.24, w * 0.52, h * 0.3);
    ctx.fillStyle = def.colors.accent;
    ctx.font = `bold ${Math.round(w * 0.2)}px sans-serif`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText('PLC', w / 2, h * 0.4);
    ['#3ddc84', '#ffcf5c', '#ff5a5a'].forEach((c, i) => {
      ctx.fillStyle = c;
      ctx.beginPath(); ctx.arc(w * (0.32 + i * 0.18), h * 0.7, w * 0.055, 0, Math.PI * 2); ctx.fill();
    });
  },
});

Object.assign(Art, {
  // 컨베이어 계열 바닥
  _beltBase(ctx, def, w, h) {
    const pad = w * 0.06;
    ctx.fillStyle = Art.COLORS.outline;
    this.roundRect(ctx, pad * 0.3, pad * 0.3, w - pad * 0.6, h - pad * 0.6, w * 0.14); ctx.fill();
    ctx.fillStyle = def.colors.body;
    this.roundRect(ctx, pad, pad, w - pad * 2, h - pad * 2, w * 0.1); ctx.fill();
  },

  // 기계 공통 몸체 (외곽선 + 색)
  _machineBase(ctx, def, w, h) {
    const p = w * 0.04;
    ctx.fillStyle = this.COLORS.outline;
    this.roundRect(ctx, p, p, w - p * 2, h - p * 2, w * 0.1); ctx.fill();
    ctx.fillStyle = def.colors.body;
    this.roundRect(ctx, p * 2, p * 2, w - p * 4, h - p * 4, w * 0.08); ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,0.10)';
    this.roundRect(ctx, p * 2, p * 2, w - p * 4, h * 0.18, w * 0.08); ctx.fill();
  },

  // 임의 각도 화살표
  _arrowDir(ctx, x, y, s, ang, fill) {
    ctx.save(); ctx.translate(x, y); ctx.rotate(ang);
    this._arrow(ctx, 0, 0, s, fill);
    ctx.restore();
  },

  // 톱니바퀴
  _gear(ctx, cx, cy, r, teeth, fill, hole) {
    ctx.fillStyle = fill;
    ctx.beginPath();
    for (let i = 0; i < teeth * 2; i++) {
      const a0 = (i / (teeth * 2)) * Math.PI * 2, a1 = ((i + 1) / (teeth * 2)) * Math.PI * 2;
      ctx.arc(cx, cy, i % 2 === 0 ? r : r * 0.78, a0, a1);
    }
    ctx.closePath(); ctx.fill();
    if (hole) { ctx.fillStyle = hole; ctx.beginPath(); ctx.arc(cx, cy, r * 0.35, 0, Math.PI * 2); ctx.fill(); }
  },

  // 번개 모양 (전력 표시)
  _bolt(ctx, cx, cy, s, fill) {
    ctx.fillStyle = fill;
    ctx.beginPath();
    ctx.moveTo(cx + s * 0.1, cy - s * 0.5);
    ctx.lineTo(cx - s * 0.26, cy + s * 0.06);
    ctx.lineTo(cx - s * 0.02, cy + s * 0.06);
    ctx.lineTo(cx - s * 0.12, cy + s * 0.5);
    ctx.lineTo(cx + s * 0.27, cy - s * 0.08);
    ctx.lineTo(cx + s * 0.03, cy - s * 0.08);
    ctx.closePath(); ctx.fill();
  },

  // 곡선 컨베이어: 위쪽(진행 방향 기준 왼쪽)에서 들어와 오른쪽으로 나감
  _drawCurve(ctx, def, w, h) {
    const c = def.colors;
    const cx = w, cy = 0; // 오른쪽 위 모서리가 원의 중심
    const ro = w * 0.94, ri = w * 0.06;
    ctx.fillStyle = c.body;
    ctx.beginPath();
    ctx.arc(cx, cy, ro, Math.PI / 2, Math.PI);
    ctx.arc(cx, cy, ri, Math.PI, Math.PI / 2, true);
    ctx.closePath(); ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.12)'; ctx.lineWidth = h * 0.05;
    ctx.beginPath(); ctx.arc(cx, cy, ro - h * 0.08, Math.PI / 2, Math.PI); ctx.stroke();
    // 곡선을 따라 쉐브론
    ctx.strokeStyle = c.accent; ctx.lineWidth = w * 0.09; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    [Math.PI * 0.85, Math.PI * 0.62].forEach(a => {
      const px = cx + Math.cos(a) * w * 0.5, py = cy + Math.sin(a) * w * 0.5;
      const ang = Math.atan2(-Math.cos(a), Math.sin(a)); // 진행 방향 (각도가 줄어드는 쪽)
      const s = h * 0.15;
      ctx.save(); ctx.translate(px, py); ctx.rotate(ang);
      ctx.beginPath(); ctx.moveTo(-s * 0.7, -s); ctx.lineTo(s * 0.5, 0); ctx.lineTo(-s * 0.7, s); ctx.stroke();
      ctx.restore();
    });
  },

  // 곡선 컨베이어 텍스처 2종 (b_conveyor_L: 왼쪽에서 들어옴, b_conveyor_R: 오른쪽에서 들어옴)
  buildConveyorCurves(scene, def) {
    ['L', 'R'].forEach(side => {
      const c = this.makeCanvas(TILE, TILE);
      const ctx = c.getContext('2d');
      if (side === 'R') { ctx.translate(0, TILE); ctx.scale(1, -1); }
      this._drawCurve(ctx, def, TILE, TILE);
      const key = 'b_conveyor_' + side;
      if (scene.textures.exists(key)) scene.textures.remove(key);
      scene.textures.addCanvas(key, c);
    });
  },

  // 아이템 아이콘 하나 그리기 (s = 칸 크기)
  drawItem(ctx, item, s) {
    const cx = s / 2, cy = s / 2, r = s * 0.4;
    const col = item.color;
    ctx.lineJoin = 'round';
    switch (item.shape) {
      case 'rock': {
        ctx.fillStyle = 'rgba(0,0,0,0.35)';
        this._rock(ctx, cx + s * 0.03, cy + s * 0.05, r, null);
        ctx.fillStyle = col;
        this._rock(ctx, cx, cy, r, null);
        ctx.fillStyle = 'rgba(255,255,255,0.25)';
        ctx.beginPath(); ctx.arc(cx - r * 0.3, cy - r * 0.3, r * 0.25, 0, Math.PI * 2); ctx.fill();
        break;
      }
      case 'plate': {
        ctx.fillStyle = this.COLORS.outline;
        this.roundRect(ctx, cx - r, cy - r * 0.8, r * 2, r * 1.6, r * 0.25); ctx.fill();
        ctx.fillStyle = col;
        this.roundRect(ctx, cx - r * 0.82, cy - r * 0.62, r * 1.64, r * 1.24, r * 0.18); ctx.fill();
        ctx.fillStyle = 'rgba(255,255,255,0.35)';
        ctx.fillRect(cx - r * 0.6, cy - r * 0.45, r * 1.2, r * 0.18);
        break;
      }
      case 'gear':
        this._gear(ctx, cx, cy, r * 1.05, 8, this.COLORS.outline);
        this._gear(ctx, cx, cy, r * 0.88, 8, col, this.COLORS.outline);
        break;
      case 'wire': {
        ctx.fillStyle = this.COLORS.outline;
        ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fill();
        ctx.strokeStyle = col; ctx.lineWidth = r * 0.22;
        for (let k = 0; k < 3; k++) {
          ctx.beginPath(); ctx.arc(cx, cy, r * (0.75 - k * 0.24), 0, Math.PI * 2); ctx.stroke();
        }
        break;
      }
      case 'chip': {
        ctx.fillStyle = this.COLORS.outline;
        this.roundRect(ctx, cx - r, cy - r, r * 2, r * 2, r * 0.2); ctx.fill();
        ctx.fillStyle = col;
        this.roundRect(ctx, cx - r * 0.8, cy - r * 0.8, r * 1.6, r * 1.6, r * 0.15); ctx.fill();
        ctx.fillStyle = '#1f2a20';
        ctx.fillRect(cx - r * 0.38, cy - r * 0.38, r * 0.76, r * 0.76);
        ctx.fillStyle = '#ffd23f';
        for (let k = -1; k <= 1; k++) {
          ctx.fillRect(cx + k * r * 0.4 - r * 0.07, cy - r * 0.78, r * 0.14, r * 0.3);
          ctx.fillRect(cx + k * r * 0.4 - r * 0.07, cy + r * 0.48, r * 0.14, r * 0.3);
        }
        break;
      }
      case 'motor': {
        ctx.fillStyle = this.COLORS.outline;
        this.roundRect(ctx, cx - r, cy - r * 0.75, r * 2, r * 1.5, r * 0.4); ctx.fill();
        ctx.fillStyle = col;
        this.roundRect(ctx, cx - r * 0.82, cy - r * 0.58, r * 1.5, r * 1.16, r * 0.3); ctx.fill();
        ctx.fillStyle = '#d8dde4';
        ctx.fillRect(cx + r * 0.62, cy - r * 0.14, r * 0.38, r * 0.28);
        ctx.fillStyle = 'rgba(255,255,255,0.3)';
        for (let k = 0; k < 3; k++) ctx.fillRect(cx - r * 0.6 + k * r * 0.4, cy - r * 0.5, r * 0.12, r);
        break;
      }
      default:
        ctx.fillStyle = col;
        ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fill();
    }
  },

  /**
   * 아이템 아틀라스 텍스처 'items' (프레임 이름 = 'it' + 아이템 인덱스)
   * 64px로 그리고 화면에는 절반 크기(32px)로 그려서 확대해도 선명하게.
   */
  ITEM_CELL: 64,
  buildItemAtlas(scene) {
    const s = this.ITEM_CELL;
    const ids = Registry.itemIds;
    const c = this.makeCanvas(s * ids.length, s);
    const ctx = c.getContext('2d');
    ids.forEach((id, i) => {
      ctx.save(); ctx.translate(i * s, 0);
      this.drawItem(ctx, GameData.items[id], s);
      ctx.restore();
    });
    if (scene.textures.exists('items')) scene.textures.remove('items');
    const tex = scene.textures.addCanvas('items', c);
    // 프레임 이름은 문자열로 (숫자 0은 Phaser에서 '프레임 없음'으로 취급되는 곳이 있음)
    this.itemFrames = ids.map((id, i) => 'it' + i);
    ids.forEach((id, i) => tex.add(this.itemFrames[i], 0, i * s, 0, s, s));
    // DOM 아이콘용 데이터 URL 캐시
    this._itemURL = {};
    ids.forEach((id, i) => {
      const ic = this.makeCanvas(s, s);
      ic.getContext('2d').drawImage(c, i * s, 0, s, s, 0, 0, s, s);
      this._itemURL[id] = ic.toDataURL();
    });
  },

  // DOM에서 쓰는 아이템 아이콘 <img>
  itemImg(id, css = 22) {
    const img = document.createElement('img');
    img.src = this._itemURL[id] || '';
    img.width = css; img.height = css;
    img.className = 'item-ic';
    img.alt = Registry.itemName(id);
    return img;
  },
  itemImgHTML(id, css = 22) {
    return `<img class="item-ic" src="${this._itemURL[id] || ''}" width="${css}" height="${css}" alt="">`;
  },

  // 상태 램프(흰 원 — 색은 tint로) + 꺼짐 표시
  buildMiscTextures(scene) {
    const mk = (key, w, h, fn) => {
      const c = this.makeCanvas(w, h); fn(c.getContext('2d'));
      if (scene.textures.exists(key)) scene.textures.remove(key);
      scene.textures.addCanvas(key, c);
    };
    mk('lamp', 32, 32, ctx => {
      ctx.fillStyle = '#2b3240';
      ctx.beginPath(); ctx.arc(16, 16, 15, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.beginPath(); ctx.arc(16, 16, 11, 0, Math.PI * 2); ctx.fill();
    });
    mk('nopower', 48, 48, ctx => {
      ctx.fillStyle = '#2b3240';
      ctx.beginPath(); ctx.arc(24, 24, 22, 0, Math.PI * 2); ctx.fill();
      this._bolt(ctx, 24, 24, 34, '#ff5a5a');
    });
  },
});
