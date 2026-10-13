// =============================================================
// scenes/game.js — 메인 게임 씬: 맵, 건물, 조작을 하나로 묶는다
// =============================================================

class GameScene extends Phaser.Scene {
  constructor() { super('Game'); }

  init(data) {
    this.seed = (data && data.seed) || 12345;
    this.tool = null;      // 선택된 건물 종류 (null = 보기 모드)
    this.toolDir = DIR.E;  // 다음에 지을 건물 방향
    this.selected = 0;     // 정보 패널에 띄운 건물 id
    this.stroke = null;    // 컨베이어 드래그 설치 상태
    this.showFps = true;
  }

  create() {
    const mapDef = GameData.map;
    this.cameras.main.setBackgroundColor('#cfd6de');

    // --- 1. 격자 맵 생성 ---
    this.grid = new Grid(mapDef);
    this.grid.generate(this.seed);
    this._createTilemap();

    // --- 2. 건물 관리자 + 중앙 납품소 ---
    this.buildings = new BuildingManager(this, this.grid, GameData.buildings);
    const hubDef = GameData.buildings[mapDef.hub.type];
    const ho = this.grid.hubOrigin(hubDef.size);
    this.hub = this.buildings.place(mapDef.hub.type, ho.x, ho.y, 0, { force: true });

    // --- 3. 미리보기(고스트)·선택 표시·길게 누르기 링 ---
    this.ghost = this.add.image(0, 0, '__WHITE').setDepth(20).setVisible(false).setAlpha(0.6);
    this.ghostRect = this.add.graphics().setDepth(19);
    this.selGfx = this.add.graphics().setDepth(25);
    this.ringGfx = this.add.graphics().setDepth(30);

    // --- 4. 카메라 + 터치 조작 ---
    this.camCtl = new CameraController(this, this.grid.w, this.grid.h);
    this.camCtl.setZoom(this.camCtl.cssToZoom(ZOOM_DEFAULT_CSS));
    const hc = this.buildings.centerOf(this.hub);
    this.camCtl.centerOn(hc.x, hc.y);
    this.touch = new TouchController(this, this.camCtl, this._touchHandlers());

    // --- 5. UI ---
    this.ui = new GameUI(this, GameData.buildings);
    this.ui.setStats({ buildings: this.buildings.count() });
    this.buildings.onChange(() => this.ui.setStats({ buildings: this.buildings.count() }));
    this._fpsTimer = 0;

    this.scale.on('resize', this._onResize, this);
    this.events.once('shutdown', () => {
      this.scale.off('resize', this._onResize, this);
      this.ui.destroy();
    });

    // 성능 테스트: 주소 뒤에 ?stress=2000 처럼 붙이면 건물을 대량 배치
    const q = new URLSearchParams(location.search);
    if (q.has('stress')) Perf.stressBuildings(this, parseInt(q.get('stress'), 10) || 2000);

    this.cameras.main.fadeIn(300, 27, 34, 48);
  }

  _createTilemap() {
    const ts = Art.buildTileset(this, GameData.map);
    const map = this.make.tilemap({ data: this.grid.toTileRows(), tileWidth: TILE, tileHeight: TILE });
    const tileset = map.addTilesetImage('tiles', ts.key, TILE, TILE, ts.margin, ts.spacing);
    this.groundLayer = map.createLayer(0, tileset, 0, 0).setDepth(0);
    this.tilemap = map;
  }

  _onResize() {
    this.camCtl.setZoom(this.cameras.main.zoom);
    this.camCtl.clamp();
  }

  update(time, delta) {
    this.touch.update();
    this.camCtl.update(delta);

    // FPS 표시는 0.5초마다 갱신 (DOM 갱신 비용 줄이기)
    this._fpsTimer += delta;
    if (this._fpsTimer > 500) {
      this._fpsTimer = 0;
      if (this.showFps) this.ui.setFps(this.game.loop.actualFps, `건물 ${this.buildings.count()}`);
    }
  }

  // ===========================================================
  // 도구 / 선택
  // ===========================================================
  selectTool(type) {
    this.tool = type;
    if (type) this._setSelected(0);
    this.ui.setTool(type, this.toolDir);
    this._hideGhost();
  }

  rotatePressed() {
    if (this.tool) {
      if (!GameData.buildings[this.tool].rotatable) return;
      this.toolDir = (this.toolDir + 1) % 4;
      Sfx.rotate();
      this.ui.updateRotate();
      if (this.ghost.visible && this._ghostTile) this._showGhost(this._ghostTile);
      this.ui.toast(`방향: ${DIR_ARROW[this.toolDir]} ${DIR_NAME[this.toolDir]}`);
    } else if (this.selected) {
      if (this.buildings.rotate(this.selected)) {
        Sfx.rotate();
        const b = this.buildings.get(this.selected);
        this._setSelected(b.id);
      } else {
        Sfx.error();
        this.ui.toast('회전할 공간이 없어요', 'bad');
      }
    }
  }

  deleteSelected() {
    if (!this.selected) return;
    this._deleteBuilding(this.buildings.get(this.selected));
  }

  closeInfo() { this._setSelected(0); this.ui.hideInfo(); }

  openMenu() {
    this.touch.reset();
    Menu.showPause({
      onResume: () => {},
      onNew: () => this.scene.restart({ seed: (Date.now() ^ (Math.random() * 1e9)) >>> 0 }),
      fpsVisible: () => this.showFps,
      toggleFps: () => {
        this.showFps = !this.showFps;
        document.getElementById('fps').classList.toggle('hidden', !this.showFps);
      },
    });
  }

  _setSelected(id) {
    this.selected = id;
    this.selGfx.clear();
    const b = id ? this.buildings.get(id) : null;
    if (!b) { this.ui.updateRotate(); return; }
    // 파란 테두리로 선택 표시
    this.selGfx.lineStyle(6, Art.COLORS.select, 1);
    this.selGfx.strokeRoundedRect(b.x * TILE - 3, b.y * TILE - 3, b.w * TILE + 6, b.h * TILE + 6, 10);
    this.ui.showBuilding(b, this._extraInfo(b));
  }

  // 건물별 추가 정보 (채굴기: 광석 칸 수 등)
  _extraInfo(b) {
    const rows = [];
    if (b.type === 'miner') {
      const counts = {};
      for (let j = 0; j < b.h; j++) for (let i = 0; i < b.w; i++) {
        const o = this.grid.oreAt(b.x + i, b.y + j);
        if (o) counts[o.item] = (counts[o.item] || 0) + 1;
      }
      const parts = Object.keys(counts).map(k => `${GameData.items[k].name} ${counts[k]}칸`);
      rows.push(['채굴 대상', parts.join(', ') || '없음']);
    }
    return rows;
  }

  _deleteBuilding(b) {
    if (!b) return;
    if (b.def.fixed) {
      Sfx.error();
      this.ui.toast('납품소는 지울 수 없어요', 'bad');
      return;
    }
    const c = this.buildings.centerOf(b);
    this._puff(c.x, c.y, Math.max(b.w, b.h));
    this.buildings.remove(b.id);
    if (this.selected === b.id) this.closeInfo();
    Sfx.remove();
  }

  // 삭제 시 먼지 효과 (도형 몇 개를 퍼뜨림)
  _puff(x, y, size) {
    for (let k = 0; k < 6; k++) {
      const a = (k / 6) * Math.PI * 2;
      const c = this.add.circle(x, y, TILE * 0.18 * size, 0x9aa5b5, 0.8).setDepth(28);
      this.tweens.add({
        targets: c,
        x: x + Math.cos(a) * TILE * 0.6 * size,
        y: y + Math.sin(a) * TILE * 0.6 * size,
        alpha: 0, scale: 0.3, duration: 260,
        onComplete: () => c.destroy(),
      });
    }
  }

  // ===========================================================
  // 배치
  // ===========================================================
  // 탭한 칸을 건물 중심으로 삼아 좌상단 좌표 계산
  _originFor(type, dir, tile) {
    const fp = Util.footprint(GameData.buildings[type], dir);
    return { x: tile.x - Math.floor((fp.w - 1) / 2), y: tile.y - Math.floor((fp.h - 1) / 2), fp };
  }

  _tryPlace(tile, silent) {
    const type = this.tool;
    const o = this._originFor(type, this.toolDir, tile);
    const chk = this.buildings.canPlace(type, o.x, o.y, this.toolDir);
    if (!chk.ok) {
      if (!silent) { Sfx.error(); this.ui.toast(chk.reason, 'bad'); this._flashGhost(tile); }
      return null;
    }
    const b = this.buildings.place(type, o.x, o.y, this.toolDir);
    if (b) {
      this.buildings.popEffect(b);
      silent ? Sfx.tick() : Sfx.place();
    }
    return b;
  }

  _showGhost(tile) {
    if (!this.tool) return;
    this._ghostTile = tile;
    const def = GameData.buildings[this.tool];
    const o = this._originFor(this.tool, this.toolDir, tile);
    const ok = this.buildings.canPlace(this.tool, o.x, o.y, this.toolDir).ok;
    this.ghost.setTexture('b_' + this.tool)
      .setPosition((o.x + o.fp.w / 2) * TILE, (o.y + o.fp.h / 2) * TILE)
      .setRotation(def.rotatable ? this.toolDir * Math.PI / 2 : 0)
      .setVisible(true);
    this.ghostRect.clear();
    this.ghostRect.fillStyle(ok ? Art.COLORS.ok : Art.COLORS.bad, 0.28);
    this.ghostRect.lineStyle(4, ok ? Art.COLORS.ok : Art.COLORS.bad, 0.9);
    this.ghostRect.fillRect(o.x * TILE, o.y * TILE, o.fp.w * TILE, o.fp.h * TILE);
    this.ghostRect.strokeRect(o.x * TILE, o.y * TILE, o.fp.w * TILE, o.fp.h * TILE);
  }

  _hideGhost() {
    this.ghost.setVisible(false);
    this.ghostRect.clear();
  }

  // 배치 실패 시 빨간 미리보기를 잠깐 보여줌
  _flashGhost(tile) {
    this._showGhost(tile);
    this.time.delayedCall(350, () => { if (!this.touch.pointers.size) this._hideGhost(); });
  }

  // ===========================================================
  // 컨베이어 드래그 설치
  //   손가락이 지나간 칸을 축 방향으로 한 칸씩 이어서 설치하고,
  //   각 컨베이어는 다음 칸을 향하도록 자동 회전한다.
  // ===========================================================
  _strokeSet(x, y, dir) {
    const key = y * this.grid.w + x;
    const rec = this.stroke.touched.get(key);
    const existing = this.buildings.at(x, y);
    if (existing) {
      if (existing.type !== 'conveyor') return; // 다른 건물은 건너뜀
      if (!rec) this.stroke.touched.set(key, { id: existing.id, prevDir: existing.dir, created: false });
      if (existing.dir !== dir) this.buildings.rotate(existing.id, dir);
      return;
    }
    const b = this.buildings.place('conveyor', x, y, dir);
    if (b) {
      this.buildings.popEffect(b);
      Sfx.tick();
      this.stroke.touched.set(key, { id: b.id, created: true });
    }
  }

  _touchHandlers() {
    return {
      onPress: (tile) => {
        if (this.tool) this._showGhost(tile);
      },
      onPressEnd: () => this._hideGhost(),
      onHover: (tile) => { if (this.tool) this._showGhost(tile); },

      onTap: (tile) => {
        const b = this.buildings.at(tile.x, tile.y);
        if (b) {
          // 건물 탭 → 정보 패널 (도구 선택 중이어도)
          Sfx.click();
          if (this.tool) this.selectTool(null);
          this._setSelected(b.id);
          return;
        }
        if (this.tool) {
          this._tryPlace(tile, false);
          return;
        }
        // 빈 칸 탭 → 광석 지대면 정보, 아니면 패널 닫기
        const ore = this.grid.oreAt(tile.x, tile.y);
        this._setSelected(0);
        if (ore) {
          this.ui.showTile(ore.name, `${GameData.items[ore.item].name}을(를) 캘 수 있는 곳이에요. 채굴기를 올려 보세요.`,
            [['위치', `${tile.x}, ${tile.y}`]]);
        } else {
          this.ui.hideInfo();
        }
      },

      wantsLongPress: (tile) => {
        const b = this.buildings.at(tile.x, tile.y);
        return !!(b && !b.def.fixed);
      },

      onLongProgress: (tile, frac) => {
        this.ringGfx.clear();
        if (!tile || frac <= 0) return;
        const b = this.buildings.at(tile.x, tile.y);
        if (!b) return;
        const c = this.buildings.centerOf(b);
        const r = Math.max(b.w, b.h) * TILE * 0.42;
        this.ringGfx.lineStyle(10, 0x000000, 0.25);
        this.ringGfx.strokeCircle(c.x, c.y, r);
        this.ringGfx.lineStyle(10, Art.COLORS.bad, 1);
        this.ringGfx.beginPath();
        this.ringGfx.arc(c.x, c.y, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.min(1, frac));
        this.ringGfx.strokePath();
      },

      onLongPress: (tile) => {
        this.ringGfx.clear();
        this._deleteBuilding(this.buildings.at(tile.x, tile.y));
      },

      // 드래그 설치는 '드래그 설치 가능 도구'일 때만
      wantsStroke: () => !!(this.tool && GameData.buildings[this.tool].dragPlace),

      onStrokeStart: (tile) => {
        this._hideGhost();
        this.stroke = { last: { x: tile.x, y: tile.y }, dir: this.toolDir, touched: new Map(), started: false };
      },

      onStrokeMove: (tile) => {
        const s = this.stroke;
        if (!s) return;
        // 손가락이 여러 칸을 건너뛰어도 한 칸씩 이어서 처리
        let guard = 0;
        while ((tile.x !== s.last.x || tile.y !== s.last.y) && guard++ < 200) {
          const dx = tile.x - s.last.x, dy = tile.y - s.last.y;
          let dir;
          // 더 많이 움직인 축을 먼저 따라감
          if (Math.abs(dx) >= Math.abs(dy)) dir = dx > 0 ? DIR.E : DIR.W;
          else dir = dy > 0 ? DIR.S : DIR.N;
          const nx = s.last.x + DX[dir], ny = s.last.y + DY[dir];
          if (!this.grid.inBounds(nx, ny)) break;
          // 현재 칸은 다음 칸을 향하게, 다음 칸도 같은 방향으로 놓아 둔다
          this._strokeSet(s.last.x, s.last.y, dir);
          this._strokeSet(nx, ny, dir);
          s.last = { x: nx, y: ny };
          s.dir = dir;
          s.started = true;
        }
      },

      onStrokeEnd: () => {
        if (this.stroke && this.stroke.started) {
          // 다음 탭 설치도 마지막 드래그 방향을 따르도록
          this.toolDir = this.stroke.dir;
          this.ui.updateRotate();
        }
        this.stroke = null;
      },

      // 두 번째 손가락이 닿으면(핀치) 이번 드래그로 설치/회전한 것을 되돌린다
      onStrokeCancel: () => {
        if (!this.stroke) return;
        this.stroke.touched.forEach(rec => {
          if (rec.created) this.buildings.remove(rec.id);
          else this.buildings.rotate(rec.id, rec.prevDir);
        });
        this.stroke = null;
      },
    };
  }
}
