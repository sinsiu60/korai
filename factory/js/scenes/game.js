// =============================================================
// scenes/game.js — 메인 게임 씬: 맵·건물·물류·가공·전력·목표·저장을 하나로 묶는다
//   시뮬레이션은 고정 간격(1/60초)으로 돌리고, 화면은 매 프레임 그린다.
//   한 스텝 순서: 전력 계산 → PLC 판단 → 기계 동작 → 컨베이어 이동
// =============================================================

const SIM_DT = 1 / 60;
const AUTOSAVE_MS = 30000;

class GameScene extends Phaser.Scene {
  constructor() { super('Game'); }

  init(data) {
    data = data || {};
    this.saveData = data.save || null;
    this.seed = this.saveData ? this.saveData.seed : (data.seed || 12345);
    this.tool = null;      // 선택된 건물 종류 (null = 보기 모드)
    this.toolDir = this.saveData ? (this.saveData.toolDir | 0) & 3 : DIR.E;
    this.selected = 0;     // 정보 패널에 띄운 건물 id
    this.stroke = null;    // 컨베이어 드래그 설치 상태
    this.linkMode = null;  // PLC 센서/대상 지정 대기
    this.showFps = true;
    this.playTime = this.saveData ? (this.saveData.playTime || 0) : 0;
    this.delivered = 0;
    this.typeCount = {};
    this._acc = 0;
    this._uiTimer = 0;
    this._slowTimer = 0;
    this._fpsTimer = 0;
    this._lastDeliverFx = 0;
  }

  create() {
    const mapDef = GameData.map;
    this.cameras.main.setBackgroundColor('#cfd6de');

    // --- 1. 해금/목표 ---
    Registry.resetUnlocks();
    this.tech = new TechSystem(n => this._onTechComplete(n));
    if (this.saveData) this.tech.load(this.saveData.tech);
    this.delivered = Object.values(this.tech.totals).reduce((a, b) => a + b, 0);

    // --- 2. 격자 맵 (시드로 생성 → 저장 시 지형은 시드만 기록) ---
    this.grid = new Grid(mapDef);
    this.grid.generate(this.seed);
    this._createTilemap();

    // --- 3. 건물과 시스템들 ---
    this.buildings = new BuildingManager(this, this.grid, GameData.buildings);
    this.logistics = new Logistics(this);
    this.conveyors = new ConveyorSystem(this);
    this.machines = new MachineSystem(this);
    this.power = new PowerSystem(this);
    this.plc = new PLCSystem(this);
    this.buildings.onChange((kind, b) => {
      if (kind === 'add') this.typeCount[b.type] = (this.typeCount[b.type] || 0) + 1;
      if (kind === 'remove') {
        this.typeCount[b.type]--;
        this.conveyors.release(b);
      }
      this.conveyors.onBuilding(kind, b);
      this.machines.onBuilding(kind, b);
      this.power.onBuilding(kind, b);
    });

    let skipped = 0;
    if (this.saveData) skipped = SaveSystem.restoreBuildings(this, this.saveData);
    this.buildings.list.forEach(b => { if (b.type === mapDef.hub.type) this.hub = b; });
    if (!this.hub) {
      const hubDef = GameData.buildings[mapDef.hub.type];
      const ho = this.grid.hubOrigin(hubDef.size);
      this.hub = this.buildings.place(mapDef.hub.type, ho.x, ho.y, 0, { force: true });
    }

    // --- 4. 미리보기(고스트)·선택 표시·길게 누르기 링 ---
    this.ghost = this.add.image(0, 0, '__WHITE').setDepth(20).setVisible(false).setAlpha(0.6);
    this.ghostRect = this.add.graphics().setDepth(19);
    this.selGfx = this.add.graphics().setDepth(25);
    this.ringGfx = this.add.graphics().setDepth(30);

    // --- 5. 카메라 + 터치 조작 ---
    this.camCtl = new CameraController(this, this.grid.w, this.grid.h);
    const sc = this.saveData && this.saveData.cam;
    if (sc && isFinite(sc.x) && isFinite(sc.y) && isFinite(sc.css)) {
      this.camCtl.setZoom(this.camCtl.cssToZoom(sc.css));
      this.camCtl.centerOn(sc.x, sc.y);
    } else {
      this.camCtl.setZoom(this.camCtl.cssToZoom(ZOOM_DEFAULT_CSS));
      const hc = this.buildings.centerOf(this.hub);
      this.camCtl.centerOn(hc.x, hc.y);
    }
    this.touch = new TouchController(this, this.camCtl, this._touchHandlers());

    // --- 6. UI ---
    this.ui = new GameUI(this);
    this._updateSlowUI();

    // --- 7. 자동 저장: 30초마다 + 앱이 백그라운드로 갈 때 ---
    this.time.addEvent({ delay: AUTOSAVE_MS, loop: true, callback: () => this.saveGame(true) });
    this._onVisibility = () => { if (document.visibilityState === 'hidden') this.saveGame(true); };
    this._onPageHide = () => this.saveGame(true);
    document.addEventListener('visibilitychange', this._onVisibility);
    window.addEventListener('pagehide', this._onPageHide);

    this.scale.on('resize', this._onResize, this);
    this.events.once('shutdown', () => {
      this.scale.off('resize', this._onResize, this);
      document.removeEventListener('visibilitychange', this._onVisibility);
      window.removeEventListener('pagehide', this._onPageHide);
      this.ui.destroy();
    });

    // 성능 테스트 (?stress=items 또는 ?stress=2000)
    const q = new URLSearchParams(location.search);
    if (q.has('stress')) {
      const v = q.get('stress');
      if (v === 'items') Perf.stressItems(this);
      else Perf.stressBuildings(this, parseInt(v, 10) || 2000);
    }

    if (this.saveData) {
      this.ui.toast(skipped ? `이어하기 (손상된 건물 ${skipped}개 제외)` : '이어하기 — 저장된 공장을 불러왔어요');
    } else {
      this.time.delayedCall(600, () => this.ui.toast('목표: 납품소에 아이템을 보내 새 기술을 여세요', 'info', 3200));
    }
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

  // ===========================================================
  // 매 프레임
  // ===========================================================
  update(time, delta) {
    this.touch.update();
    this.camCtl.update(delta);

    // 고정 간격 시뮬레이션 (느린 기기에서 한 프레임에 최대 4스텝, 그 이상은 버림)
    // 일시정지 메뉴가 열려 있으면 시뮬레이션 멈춤
    if (!Menu.isOpen) this._acc += Math.min(delta, 100) / 1000;
    let steps = 0;
    while (this._acc >= SIM_DT && steps < 4) {
      this.simStep(SIM_DT);
      this._acc -= SIM_DT;
      steps++;
    }
    if (steps === 4) this._acc = 0;
    if (!Menu.isOpen) this.playTime += delta / 1000;

    this.conveyors.render();
    this.machines.updateLamps();

    this._uiTimer += delta;
    if (this._uiTimer > 250) {
      this._uiTimer = 0;
      this.tech.check();
      this.ui.updatePower(this.power.summary());
      this.ui.refreshInfo();
      if (this.selected && this.buildings.get(this.selected) && this.buildings.get(this.selected).def.logic === 'plc') this._drawSelection();
    }
    this._slowTimer += delta;
    if (this._slowTimer > 500) { this._slowTimer = 0; this._updateSlowUI(); }
    this._fpsTimer += delta;
    if (this._fpsTimer > 500) {
      this._fpsTimer = 0;
      if (this.showFps) this.ui.setFps(this.game.loop.actualFps, `아이템 ${this.conveyors.itemCount}`);
    }
  }

  simStep(dt) {
    this.power.step(dt);
    this.plc.step();
    this.machines.step(dt);
    this.conveyors.step(dt);
  }

  _updateSlowUI() {
    this.ui.updateGoals(this.tech, this._hint());
    this.ui.setStats({ delivered: this.delivered });
  }

  // 초반 안내 문구
  _hint() {
    const c = this.typeCount;
    if (!c.pole) return '💡 납품소 바로 옆에 전신주를 세우면 전기가 들어와요';
    if (!c.miner) return '💡 광석 지대에 채굴기를 놓고, 전신주 파란 영역 안에 두세요';
    if (!c.conveyor) return '💡 채굴기 화살표 앞에서 납품소까지 컨베이어를 드래그하세요';
    if (this.tech.done.size === 0 && this.delivered === 0) return '💡 컨베이어 끝이 납품소에 닿아야 납품돼요';
    return '';
  }

  // ===========================================================
  // 납품 / 전력 이벤트 / 목표 달성
  // ===========================================================
  deliver(hub, t) {
    this.tech.deliver(Registry.itemIds[t]);
    this.delivered++;
    const now = performance.now();
    if (now - this._lastDeliverFx > 140) {
      this._lastDeliverFx = now;
      Sfx.deliver();
      const s = hub.sprite;
      if (!s._pulsing) {
        s._pulsing = true;
        this.tweens.add({ targets: s, scale: 1.04, duration: 70, yoyo: true, onComplete: () => { s._pulsing = false; s.setScale(1); } });
      }
    }
  }

  onOverloadStart() {
    Sfx.warn();
    this.ui.toast(`⚠ 과부하! ${this.power.tripSeconds}초 안에 줄이지 않으면 차단기가 내려가요`, 'warn', 2600);
  }

  onTrip() {
    Sfx.trip();
    this.cameras.main.shake(220, 0.004);
    this.ui.toast('⚡ 차단기 트립! 라인 정지 — 위 게이지를 탭해 재투입', 'bad', 3200);
  }

  resetBreakers() {
    const n = this.power.resetAll();
    if (n) {
      Sfx.powerOn();
      this.ui.toast(`차단기 재투입 (${n}개 전력망)`);
    } else {
      const s = this.power.summary();
      if (s.overload) this.ui.toast('과부하 중이에요 — 기계를 끄거나 발전기를 늘리세요', 'warn');
      else if (!s.nets) this.ui.toast('전력망이 없어요 — 납품소 옆에 전신주를 세우세요');
      else this.ui.toast(`전력 정상 · 사용 ${Math.round(s.demand)} / 공급 ${Math.round(s.cap)} kW`);
    }
  }

  resetNetOf(b) {
    if (b.net && this.power.reset(b.net)) { Sfx.powerOn(); this.ui.toast('차단기 재투입'); }
  }

  _onTechComplete(n) {
    Sfx.fanfare();
    const unlocked = n.unlocks.buildings || [];
    this.ui.markNew(unlocked);
    this.ui.buildToolbar();
    if (this.tech.isFinished()) this.ui.showVictory({ delivered: this.delivered, playTime: this.playTime });
    else this.ui.showUnlock(n);
    this.saveGame(true);
  }

  // ===========================================================
  // 저장
  // ===========================================================
  saveGame(auto) {
    let ok = false;
    try {
      ok = SaveSystem.write(SaveSystem.serialize(this));
    } catch (e) {
      console.warn('[저장 직렬화 실패]', e);
    }
    if (!ok && !this._saveWarned) {
      this._saveWarned = true;
      this.ui.toast('저장할 수 없어요 (저장 공간·시크릿 모드 확인). 게임은 계속돼요', 'bad', 3200);
    } else if (ok && auto) {
      const e = document.getElementById('save-dot');
      if (e) { e.classList.remove('flash'); void e.offsetWidth; e.classList.add('flash'); }
    }
    return ok;
  }

  // ===========================================================
  // 도구 / 선택
  // ===========================================================
  selectTool(type) {
    this.tool = type;
    this.linkMode = null;
    if (type) this._setSelected(0);
    this.ui.setTool(type);
    this._hideGhost();
    this._updateAreaOverlay();
  }

  _updateAreaOverlay() {
    const t = this.tool ? GameData.buildings[this.tool] : null;
    const sel = this.selected ? this.buildings.get(this.selected) : null;
    const show = !!(t && PowerSystem.usesPower({ def: t })) || !!(sel && sel.def.logic === 'pole');
    this.power.setShowArea(show);
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
        this._setSelected(this.selected);
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

  closeInfo() { this.linkMode = null; this._setSelected(0); this.ui.hideInfo(); }

  openMenu() {
    this.touch.reset();
    Menu.showPause({
      onResume: () => {},
      onSave: () => this.saveGame(false),
      onTech: () => this.ui.showTech(),
      onNew: () => { SaveSystem.clear(); this.scene.restart({ seed: (Date.now() ^ (Math.random() * 1e9)) >>> 0 }); },
      fpsVisible: () => this.showFps,
      toggleFps: () => {
        this.showFps = !this.showFps;
        document.getElementById('fps').classList.toggle('hidden', !this.showFps);
      },
    });
  }

  toggleOn(b) {
    b.on = b.on === false;
    this.ui.toast(b.on ? `${b.def.name} 켬` : `${b.def.name} 정지`);
  }

  setRecipe(b, id) {
    this.machines.setRecipe(b, id);
    this.ui.toast(`레시피: ${Registry.recipes[id].name}`);
  }

  // PLC: 센서/대상 지정 모드 시작
  startLink(plc, field) {
    this.linkMode = { plcId: plc.id, field };
    this.ui.buildControls(plc);
    this.ui.toast(field === 'sensor' ? '센서로 쓸 건물을 탭하세요 (빈 곳: 취소)' : '켜고 끌 건물을 탭하세요 (빈 곳: 취소)', 'info', 2600);
  }

  plcSet(b, key, v) { b.st[key] = v; }

  _setSelected(id) {
    this.selected = id;
    this._drawSelection();
    const b = id ? this.buildings.get(id) : null;
    this._updateAreaOverlay();
    if (!b) { this.ui.updateRotate(); return; }
    this.ui.showBuilding(b);
  }

  // 선택 테두리 (+ PLC는 센서/대상 연결선)
  _drawSelection() {
    const g = this.selGfx;
    g.clear();
    const b = this.selected ? this.buildings.get(this.selected) : null;
    if (!b) return;
    g.lineStyle(6, Art.COLORS.select, 1);
    g.strokeRoundedRect(b.x * TILE - 3, b.y * TILE - 3, b.w * TILE + 6, b.h * TILE + 6, 10);
    if (b.def.logic === 'plc') {
      const c = this.buildings.centerOf(b);
      [['sensor', 0x2f80ff], ['target', 0xff9f43]].forEach(([f, col]) => {
        const t = b.st[f] ? this.buildings.get(b.st[f]) : null;
        if (!t) return;
        const tc = this.buildings.centerOf(t);
        g.lineStyle(5, col, 0.9);
        g.lineBetween(c.x, c.y, tc.x, tc.y);
        g.strokeRoundedRect(t.x * TILE - 2, t.y * TILE - 2, t.w * TILE + 4, t.h * TILE + 4, 8);
      });
    }
  }

  // 정보 패널 내용 (UI가 0.25초마다 호출)
  describe(b) {
    const def = b.def, st = b.st || {}, l = def.logic;
    const rows = [['위치', `${b.x}, ${b.y}`]];
    if (def.rotatable) rows.push(['방향', `${DIR_ARROW[b.dir]} ${DIR_NAME[b.dir]}`]);
    const bars = [], invs = [];
    let status = null;

    if (b.lamp) {
      let key = b.status || 'idle';
      let text = STATUS_TEXT[key];
      if (def.powerKW > 0 && !b.net) { key = 'nopower'; text = '전력 없음 — 전신주 범위 밖'; }
      else if (b.net && b.net.tripped) { key = 'nopower'; text = '차단기 트립 — 정지'; }
      else if (key === 'work' && b.pf < 0.999 && def.powerKW > 0) text = `가동 중 (전력 부족, 효율 ${Math.round(b.pf * 100)}%)`;
      else if (key === 'off' && b.plcOff) text = '꺼짐 (PLC 제어)';
      if (l === 'generator' && key === 'idle') text = st.coal > 0 || st.energy > 0 ? '대기 (수요 없음)' : '연료 없음';
      status = { text, color: STATUS_CSS[key] };
    }

    switch (l) {
      case 'miner':
        rows.push(['채굴 대상', st.ore >= 0 ? `${Registry.itemName(st.ore)} (속도 ${Math.round(st.frac * 100)}%)` : '없음']);
        bars.push({ label: '채굴', frac: st.progress });
        break;
      case 'crafter': {
        const r = st.recipe ? Registry.recipes[st.recipe] : null;
        rows.push(['레시피', r ? `${r.name} (${r.time}초)` : (def.selectRecipe ? '선택 필요' : '재료를 넣으면 자동')]);
        bars.push({ label: '가공', frac: st.crafting ? st.progress : 0 });
        invs.push({ title: '재료', items: MachineSystem.invList(st.inv) });
        invs.push({ title: '완성품', items: MachineSystem.invList(st.out) });
        break;
      }
      case 'storage':
        rows.push(['저장량', `${st.total} / ${def.capacity}`]);
        bars.push({ label: '채움', frac: st.total / def.capacity, color: st.total >= def.capacity ? '#ff9f43' : '' });
        invs.push({ title: '내용물', items: MachineSystem.invList(st.inv) });
        break;
      case 'generator':
        rows.push(['출력', `${Math.round(st.output)} / ${def.supplyKW} kW`]);
        rows.push(['연료', `${Registry.itemName(def.fuel)} ${st.coal} / ${def.fuelCap}`]);
        bars.push({ label: '연소', frac: st.energy / def.fuelKJ, color: '#ff9f43' });
        break;
      case 'hub':
        rows.push(['기본 전력', `${def.supplyKW} kW`]);
        rows.push(['총 납품', `${this.delivered}개`]);
        invs.push({ title: '누적', items: Object.keys(this.tech.totals).map(k => [k, this.tech.totals[k]]) });
        break;
      case 'conveyor':
        rows.push(['아이템', `${st.items.length}개`]);
        if (b.on === false || b.plcOff) status = { text: b.plcOff ? '꺼짐 (PLC 제어)' : '꺼짐', color: STATUS_CSS.off };
        break;
      case 'splitter':
      case 'merger':
        rows.push(['대기 중', st.buf >= 0 ? Registry.itemName(st.buf) : '없음']);
        break;
      case 'plc': {
        const sensor = st.sensor ? this.buildings.get(st.sensor) : null;
        const target = st.target ? this.buildings.get(st.target) : null;
        rows.push(['현재 조건', sensor ? (st.state ? '참 ✔' : '거짓') : '-']);
        if (sensor && target) {
          const offNow = target.plcOff;
          rows.push(['대상 상태', offNow ? '꺼짐' : '켜짐']);
        }
        break;
      }
    }

    // 전력망 정보
    if (def.powerKW > 0) rows.push(['소비 전력', `${def.powerKW} kW`]);
    if (l === 'pole' || l === 'generator' || l === 'hub' || def.powerKW > 0) {
      const net = b.net;
      if (net) {
        rows.push(['전력망', `사용 ${Math.round(net.demand)} / 공급 ${Math.round(net.cap)} kW`]);
        rows.push(['망 효율', net.tripped ? '트립 (정지)' : `${Math.round(net.sat * 100)}%`]);
        if (l === 'pole') rows.push(['연결', `기계 ${net.members.length} · 발전기 ${net.gens.length} · 납품소 ${net.hubs.length}`]);
        if (net.overloadT > 0 && !net.tripped) rows.push(['과부하', `${Math.max(0, Math.ceil(this.power.tripSeconds - net.overloadT))}초 후 트립`]);
      } else if (l !== 'hub') {
        rows.push(['전력망', '연결 안 됨']);
      }
    }
    return { status, rows, bars, invs };
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
    const wasSelected = this.selected === b.id;
    this.buildings.remove(b.id);
    if (wasSelected) this.closeInfo();
    else if (this.selected) this._drawSelection();
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
      // 전기가 필요한데 전신주 범위 밖이면 알려주기
      if (b.def.powerKW > 0) {
        this.power.rebuild();
        if (!b.net) this.ui.toast('⚡ 전신주 범위(파란 영역) 밖이라 전기가 없어요', 'warn');
      }
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
    const g = this.ghostRect;
    g.clear();
    // 전신주는 공급 영역과 연결 거리도 미리 보여줌
    if (def.logic === 'pole') {
      const r = def.supplyRadius;
      g.fillStyle(0x2f80ff, 0.16);
      g.fillRect((o.x - r) * TILE, (o.y - r) * TILE, (r * 2 + 1) * TILE, (r * 2 + 1) * TILE);
      g.lineStyle(3, 0x2f80ff, 0.35);
      g.strokeCircle((o.x + 0.5) * TILE, (o.y + 0.5) * TILE, def.reach * TILE);
    }
    g.fillStyle(ok ? Art.COLORS.ok : Art.COLORS.bad, 0.28);
    g.lineStyle(4, ok ? Art.COLORS.ok : Art.COLORS.bad, 0.9);
    g.fillRect(o.x * TILE, o.y * TILE, o.fp.w * TILE, o.fp.h * TILE);
    g.strokeRect(o.x * TILE, o.y * TILE, o.fp.w * TILE, o.fp.h * TILE);
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

  // PLC 연결 모드에서의 탭
  _linkTap(tile) {
    const lm = this.linkMode;
    this.linkMode = null;
    const plc = this.buildings.get(lm.plcId);
    const b = this.buildings.at(tile.x, tile.y);
    if (!plc) return;
    if (!b || b === plc) {
      this.ui.toast('지정을 취소했어요');
    } else if (lm.field === 'target' && (b.def.fixed || b.def.logic === 'pole' || b.def.logic === 'plc')) {
      Sfx.error();
      this.ui.toast('이 건물은 켜고 끌 수 없어요', 'bad');
    } else {
      plc.st[lm.field] = b.id;
      Sfx.place();
      this.ui.toast(`${lm.field === 'sensor' ? '센서' : '대상'}: ${b.def.name} (${b.x},${b.y})`);
    }
    this._setSelected(plc.id);
  }

  _touchHandlers() {
    return {
      onPress: (tile) => {
        if (this.tool) this._showGhost(tile);
      },
      onPressEnd: () => this._hideGhost(),
      onHover: (tile) => { if (this.tool) this._showGhost(tile); },

      onTap: (tile) => {
        if (this.linkMode) { this._linkTap(tile); return; }
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
          this.ui.showTile(ore.name, `${Registry.itemName(ore.item)}을(를) 캘 수 있는 곳이에요. 채굴기를 올려 보세요.`,
            [['위치', `${tile.x}, ${tile.y}`]]);
        } else {
          this.ui.hideInfo();
        }
      },

      wantsLongPress: (tile) => {
        if (this.linkMode) return false;
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
