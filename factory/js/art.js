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
