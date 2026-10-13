// =============================================================
// ui.js — 화면 위 HTML UI
//   상단: 메뉴 / 전력 게이지 / 납품 수 / 목표 / FPS
//   하단: 건물 툴바 (해금된 것만)   오른쪽: 회전·해제 버튼
//   왼쪽: 정보 패널 (상태·재료·진행도·레시피 선택·PLC 설정)
//   - 글자가 선명하고 반응형 배치가 쉬워서 UI는 DOM으로 만든다.
//   - 0.25초마다 바뀐 글자만 갱신해서 버튼 탭이 끊기지 않게 한다.
// =============================================================

const $ = (sel) => document.querySelector(sel);

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
}

// 버튼 공통: 효과음 + 동작
function onTap(btn, fn) {
  btn.onclick = (ev) => { Sfx.unlock(); Sfx.click(); fn(ev); };
  return btn;
}

const STATUS_CSS = {
  work: '#3ddc84', idle: '#ffcf5c', blocked: '#ff9f43', nopower: '#ff5a5a', off: '#8a94a6',
};

class GameUI {
  /**
   * @param {GameScene} game 게임 씬 (동작 함수 제공)
   */
  constructor(game) {
    this.game = game;
    this.defs = GameData.buildings;
    this.root = $('#hud');
    this.root.classList.remove('hidden');
    this.toolButtons = new Map();
    this.newBadges = new Set();
    this._toastTimer = 0;
    this._infoKey = '';
    this._goalsHTML = '';

    this.toolbar = $('#toolbar');
    this.rotateBtn = $('#btn-rotate');
    this.cancelBtn = $('#btn-cancel');
    this.info = $('#info-panel');
    this.toastEl = $('#toast');
    this.fpsEl = $('#fps');

    this.buildToolbar();
    this._bind();
  }

  _bind() {
    onTap(this.rotateBtn, () => this.game.rotatePressed());
    onTap(this.cancelBtn, () => this.game.selectTool(null));
    onTap($('#btn-menu'), () => this.game.openMenu());
    onTap($('#info-close'), () => this.game.closeInfo());
    onTap($('#info-rotate'), () => this.game.rotatePressed());
    $('#info-delete').onclick = () => this.game.deleteSelected();
    onTap($('#power-gauge'), () => this.game.resetBreakers());
    onTap($('#goals'), () => this.showTech());
    onTap($('#modal-close'), () => this.closeModal());
    $('#modal').onclick = (e) => { if (e.target.id === 'modal') this.closeModal(); };
  }

  // ---------------------------------------------------------
  // 하단 툴바: '보기' + 해금된 건물 (buildings.json order 순)
  // ---------------------------------------------------------
  buildToolbar() {
    this.toolbar.innerHTML = '';
    this.toolButtons.clear();
    this.toolbar.appendChild(this._toolButton(null, '보기', this._handIcon(40)));
    Object.keys(this.defs)
      .filter(k => !k.startsWith('_') && !this.defs[k].fixed && Registry.isBuildingUnlocked(k))
      .sort((a, b) => (this.defs[a].order || 0) - (this.defs[b].order || 0))
      .forEach(type => {
        const def = this.defs[type];
        const btn = this._toolButton(type, def.name, Art.iconCanvas(type, def, 40));
        if (def.powerKW) btn.appendChild(el('span', 'tool-kw', def.powerKW + 'kW'));
        if (def.supplyKW) btn.appendChild(el('span', 'tool-kw supply', '+' + def.supplyKW + 'kW'));
        if (this.newBadges.has(type)) btn.appendChild(el('span', 'tool-new', 'NEW'));
        this.toolbar.appendChild(btn);
      });
    this.setTool(this.game.tool);
  }

  markNew(types) { types.forEach(t => this.newBadges.add(t)); }

  _toolButton(type, label, iconEl) {
    const btn = el('button', 'tool-btn');
    btn.appendChild(iconEl);
    btn.appendChild(el('span', 'tool-label', label));
    onTap(btn, () => {
      if (type && this.newBadges.delete(type)) { const b = btn.querySelector('.tool-new'); if (b) b.remove(); }
      this.game.selectTool(this.game.tool === type ? null : type);
    });
    this.toolButtons.set(type, btn);
    return btn;
  }

  // '보기' 도구 아이콘 (화살표 포인터)
  _handIcon(css) {
    const px = Math.round(css * DPR);
    const c = Art.makeCanvas(px, px);
    c.style.width = css + 'px'; c.style.height = css + 'px';
    const ctx = c.getContext('2d');
    const s = px / 40;
    ctx.scale(s, s);
    ctx.fillStyle = '#ffffff'; ctx.strokeStyle = '#2b3240';
    ctx.lineWidth = 2.2; ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(12, 6); ctx.lineTo(12, 30); ctx.lineTo(18, 24);
    ctx.lineTo(22, 33); ctx.lineTo(26, 31); ctx.lineTo(22, 22);
    ctx.lineTo(30, 22); ctx.closePath();
    ctx.fill(); ctx.stroke();
    return c;
  }

  setTool(type) {
    this.toolButtons.forEach((btn, t) => btn.classList.toggle('active', t === type));
    this.cancelBtn.classList.toggle('hidden', !type);
    const btn = this.toolButtons.get(type);
    if (btn && btn.scrollIntoView) btn.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    this.updateRotate();
  }

  // 회전 버튼: 도구 선택 중이면 도구 방향, 건물 선택 중이면 그 건물 회전
  updateRotate() {
    const g = this.game;
    const sel = g.selected ? g.buildings.get(g.selected) : null;
    let enabled = false, dir = g.toolDir;
    if (g.tool) enabled = !!this.defs[g.tool].rotatable;
    else if (sel) { enabled = !!sel.def.rotatable; dir = sel.dir; }
    this.rotateBtn.disabled = !enabled;
    $('#rotate-arrow').style.transform = `rotate(${dir * 90}deg)`;
  }

  // ---------------------------------------------------------
  // 정보 패널
  // ---------------------------------------------------------
  showBuilding(b) {
    const def = b.def;
    this._infoKey = 'b' + b.id;
    $('#info-icon').innerHTML = '';
    $('#info-icon').appendChild(Art.iconCanvas(b.type, def, 44));
    $('#info-title').textContent = def.name;
    $('#info-desc').textContent = def.desc || '';
    $('#info-rotate').classList.toggle('hidden', !def.rotatable);
    $('#info-delete').classList.toggle('hidden', !!def.fixed);
    $('#info-hint').textContent = def.fixed ? '고정 건물이라 옮기거나 지울 수 없어요' : '길게 누르면 바로 삭제돼요';
    // 이전 건물 내용이 남지 않도록 캐시를 비움 (빈 목록도 다시 그리게 null)
    this._rowsKey = null; this._barsKey = null; this._invHTML = null;
    this.buildControls(b);
    this.refreshInfo();
    this.info.classList.remove('hidden');
    this.updateRotate();
  }

  showTile(title, desc, rows) {
    this._infoKey = 'tile';
    $('#info-icon').innerHTML = '';
    $('#info-title').textContent = title;
    $('#info-desc').textContent = desc || '';
    $('#info-status').classList.add('hidden');
    this._rowsKey = null;
    this._renderRows(rows || []);
    $('#info-bars').innerHTML = ''; this._barsKey = '';
    $('#info-inv').innerHTML = ''; this._invHTML = '';
    $('#info-controls').innerHTML = '';
    $('#info-rotate').classList.add('hidden');
    $('#info-delete').classList.add('hidden');
    $('#info-hint').textContent = '';
    this.info.classList.remove('hidden');
  }

  hideInfo() {
    this._infoKey = '';
    this.info.classList.add('hidden');
    this.updateRotate();
  }

  // 0.25초마다: 선택된 건물의 상태 글자만 갱신
  refreshInfo() {
    const g = this.game;
    if (!this._infoKey || this._infoKey === 'tile') return;
    const b = g.selected ? g.buildings.get(g.selected) : null;
    if (!b) return;
    const d = g.describe(b);
    const stEl = $('#info-status');
    if (d.status) {
      stEl.classList.remove('hidden');
      stEl.textContent = d.status.text;
      stEl.style.setProperty('--c', d.status.color);
    } else stEl.classList.add('hidden');
    this._renderRows(d.rows);
    this._renderBars(d.bars || []);
    this._renderInv(d.invs || []);
    if (this._refreshControls) this._refreshControls();
  }

  _renderRows(rows) {
    const box = $('#info-rows');
    const key = rows.map(r => r[0]).join('|');
    if (key !== this._rowsKey) {
      this._rowsKey = key;
      box.innerHTML = '';
      rows.forEach(([k]) => {
        const tr = el('div', 'info-row');
        tr.appendChild(el('span', 'k', k));
        tr.appendChild(el('span', 'v'));
        box.appendChild(tr);
      });
    }
    const vs = box.querySelectorAll('.v');
    rows.forEach((r, i) => { if (vs[i].textContent !== String(r[1])) vs[i].textContent = r[1]; });
  }

  _renderBars(bars) {
    const box = $('#info-bars');
    const key = bars.map(b => b.label).join('|');
    if (key !== this._barsKey) {
      this._barsKey = key;
      box.innerHTML = bars.map(b => `<div class="bar-row"><span class="bar-label"></span><span class="bar"><i></i></span></div>`).join('');
    }
    const rows = box.querySelectorAll('.bar-row');
    bars.forEach((b, i) => {
      rows[i].querySelector('.bar-label').textContent = b.label;
      const fill = rows[i].querySelector('i');
      fill.style.width = Math.round(Util.clamp(b.frac, 0, 1) * 100) + '%';
      fill.style.background = b.color || '';
    });
  }

  _renderInv(invs) {
    const html = invs.map(g => `<div class="inv-group"><span class="inv-title">${g.title}</span>` +
      (g.items.length ? g.items.map(([id, n]) => `<span class="inv-item">${Art.itemImgHTML(id, 22)}<b>${n}</b></span>`).join('')
        : '<span class="inv-empty">비어 있음</span>') + '</div>').join('');
    if (html !== this._invHTML) { this._invHTML = html; $('#info-inv').innerHTML = html; }
  }

  // 건물 종류별 조작 버튼 (선택할 때 한 번 만들고, 눌렀을 때 다시 만든다)
  buildControls(b) {
    const g = this.game;
    const box = $('#info-controls');
    box.innerHTML = '';
    this._refreshControls = null;
    const l = b.def.logic;

    // 레시피 선택 (조립기)
    if (l === 'crafter' && b.def.selectRecipe) {
      box.appendChild(el('div', 'ctl-title', '레시피 선택'));
      const wrap = el('div', 'recipe-list');
      Registry.recipesFor(b.def.machine).forEach(r => {
        const btn = el('button', 'recipe-btn' + (b.st.recipe === r.id ? ' active' : ''));
        const ins = Object.keys(r.in).map(k => `${Art.itemImgHTML(k, 18)}<small>×${r.in[k]}</small>`).join(' ');
        const outs = Object.keys(r.out).map(k => `${Art.itemImgHTML(k, 24)}<small>×${r.out[k]}</small>`).join(' ');
        btn.innerHTML = `<span class="r-out">${outs}</span><span class="r-name">${r.name}</span><span class="r-in">${ins} · ${r.time}초</span>`;
        onTap(btn, () => { g.setRecipe(b, r.id); this.buildControls(b); this.refreshInfo(); });
        wrap.appendChild(btn);
      });
      box.appendChild(wrap);
    }

    // PLC 설정
    if (l === 'plc') this._plcControls(b, box);

    // 차단기 재투입 (전력 관련 건물)
    if (l === 'pole' || l === 'generator' || l === 'hub') {
      const btn = el('button', 'pill-btn wide', '⚡ 차단기 재투입');
      onTap(btn, () => g.resetNetOf(b));
      box.appendChild(btn);
      this._refreshControls = () => {
        const tripped = !!(b.net && b.net.tripped);
        btn.classList.toggle('alert', tripped);
        btn.disabled = !tripped;
      };
    }

    // 수동 ON/OFF (납품소·전신주 제외)
    if (!b.def.fixed && l !== 'pole') {
      const btn = el('button', 'pill-btn wide');
      const sync = () => {
        btn.textContent = b.on === false ? '▶ 다시 켜기' : '⏸ 정지시키기';
        btn.classList.toggle('alert', b.on === false);
      };
      sync();
      onTap(btn, () => { g.toggleOn(b); sync(); this.refreshInfo(); });
      box.appendChild(btn);
    }
  }

  _plcControls(b, box) {
    const g = this.game, st = b.st;
    const linkRow = (label, field) => {
      const row = el('div', 'plc-row');
      row.appendChild(el('span', 'k', label));
      const tgt = st[field] ? g.buildings.get(st[field]) : null;
      row.appendChild(el('span', 'v', tgt ? `${tgt.def.name} (${tgt.x},${tgt.y})` : '미지정'));
      const btn = el('button', 'pill-btn small', g.linkMode && g.linkMode.field === field ? '탭 대기…' : '지정');
      onTap(btn, () => g.startLink(b, field));
      row.appendChild(btn);
      box.appendChild(row);
    };
    const chips = (label, options, key) => {
      box.appendChild(el('div', 'ctl-title', label));
      const wrap = el('div', 'chips');
      options.forEach(([v, text]) => {
        const c = el('button', 'chip-btn' + (st[key] === v ? ' active' : ''), text);
        onTap(c, () => { g.plcSet(b, key, v); this.buildControls(b); });
        wrap.appendChild(c);
      });
      box.appendChild(wrap);
    };
    linkRow('센서', 'sensor');
    chips('조건', PLC_CONDS, 'cond');
    linkRow('대상', 'target');
    chips('동작', PLC_ACTIONS, 'action');
  }

  // ---------------------------------------------------------
  // 상단: 전력 게이지 / 목표 / 통계
  // ---------------------------------------------------------
  updatePower(s) {
    const gauge = $('#power-gauge');
    const fill = $('#g-fill');
    const text = $('#g-text');
    let cls = 'ok', msg;
    if (s.tripped > 0) {
      cls = 'trip';
      msg = '차단기 트립! 탭해서 재투입';
    } else if (s.overload > 0) {
      cls = 'warn';
      msg = `과부하 · ${Math.max(0, Math.ceil(s.left))}초 후 트립`;
    } else if (s.cap <= 0 && s.demand <= 0) {
      cls = 'none';
      msg = s.unpowered ? `전기 없는 기계 ${s.unpowered}대` : '전력망 없음';
    } else {
      msg = `${Math.round(s.demand)} / ${Math.round(s.cap)} kW`;
    }
    if (cls === 'ok' && s.unpowered) msg += ` · 미연결 ${s.unpowered}`;
    gauge.className = 'gauge ' + cls;
    const frac = s.cap > 0 ? s.demand / s.cap : (s.demand > 0 ? 1 : 0);
    fill.style.width = Math.round(Util.clamp(frac, 0, 1) * 100) + '%';
    if (text.textContent !== msg) text.textContent = msg;
  }

  updateGoals(tech, hint) {
    const avail = tech.available();
    let html;
    if (!avail.length) {
      html = tech.isFinished() ? '<div class="goal done">🏆 모든 목표 달성!</div>' : '';
    } else {
      html = avail.slice(0, 2).map(n => {
        const rows = tech.progress(n).map(p =>
          `<div class="goal-item">${Art.itemImgHTML(p.item, 18)}<span class="goal-num">${p.have}/${p.need}</span>` +
          `<span class="bar"><i style="width:${Math.round(p.have / p.need * 100)}%"></i></span></div>`).join('');
        return `<div class="goal"><div class="goal-name">${n.name}</div>${rows}</div>`;
      }).join('') + (avail.length > 2 ? `<div class="goal-more">+${avail.length - 2}개 더 · 탭해서 보기</div>` : '');
    }
    if (hint) html += `<div class="goal-hint">${hint}</div>`;
    if (html !== this._goalsHTML) { this._goalsHTML = html; $('#goals').innerHTML = html; }
  }

  setStats(s) {
    const t = `납품 ${s.delivered}`;
    const e = $('#stat-delivered');
    if (e.textContent !== t) e.textContent = t;
  }

  setFps(fps, extra) {
    this.fpsEl.textContent = `${Math.round(fps)} FPS${extra ? ' · ' + extra : ''}`;
    this.fpsEl.classList.toggle('low', fps < 50);
  }

  // ---------------------------------------------------------
  // 모달: 해금 트리 / 해금 알림
  // ---------------------------------------------------------
  openModal(title, bodyEl) {
    $('#modal-title').textContent = title;
    const body = $('#modal-body');
    body.innerHTML = '';
    body.appendChild(bodyEl);
    $('#modal').classList.remove('hidden');
    this.game.touch.reset();
  }

  closeModal() { $('#modal').classList.add('hidden'); }

  _unlockChips(n) {
    const wrap = el('div', 'unlock-list');
    (n.unlocks.buildings || []).forEach(t => {
      const c = el('span', 'unlock-chip');
      c.appendChild(Art.iconCanvas(t, this.defs[t], 22));
      c.appendChild(document.createTextNode(this.defs[t].name));
      wrap.appendChild(c);
    });
    (n.unlocks.recipes || []).forEach(r => {
      const rec = Registry.recipes[r];
      const c = el('span', 'unlock-chip');
      c.innerHTML = Art.itemImgHTML(Object.keys(rec.out)[0], 20) + '레시피: ' + rec.name;
      wrap.appendChild(c);
    });
    return wrap;
  }

  showTech() {
    const tech = this.game.tech;
    const body = el('div', 'tech-list');
    tech.nodes.forEach(n => {
      const done = tech.done.has(n.id);
      const avail = tech.isAvailable(n);
      const card = el('div', 'tech-card ' + (done ? 'done' : avail ? 'avail' : 'locked'));
      const head = el('div', 'tech-head');
      head.appendChild(el('span', 'tech-name', (done ? '✅ ' : avail ? '🎯 ' : '🔒 ') + n.name));
      card.appendChild(head);
      card.appendChild(el('div', 'tech-desc', n.desc));
      const goal = el('div', 'tech-goal');
      goal.innerHTML = tech.progress(n).map(p =>
        `<span class="inv-item">${Art.itemImgHTML(p.item, 18)}${Registry.itemName(p.item)} <b>${done ? p.need : p.have}/${p.need}</b></span>`).join('');
      card.appendChild(goal);
      if (!done && !avail) {
        const req = n.requires.map(r => tech.nodes.find(x => x.id === r).name).join(', ');
        card.appendChild(el('div', 'tech-req', '선행: ' + req));
      }
      card.appendChild(this._unlockChips(n));
      body.appendChild(card);
    });
    this.openModal('목표와 해금 트리', body);
  }

  showUnlock(n) {
    const body = el('div', 'unlock-pop');
    body.appendChild(el('div', 'unlock-big', '🎉 ' + n.name + ' 달성!'));
    const has = (n.unlocks.buildings || []).length + (n.unlocks.recipes || []).length;
    if (has) {
      body.appendChild(el('p', 'unlock-sub', '새로 열렸어요'));
      body.appendChild(this._unlockChips(n));
    }
    const ok = el('button', 'menu-btn primary', '좋아요');
    onTap(ok, () => this.closeModal());
    body.appendChild(ok);
    this.openModal('목표 달성', body);
  }

  showVictory(stats) {
    const body = el('div', 'unlock-pop');
    body.appendChild(el('div', 'unlock-big', '🏆 공장 완성!'));
    body.appendChild(el('p', 'unlock-sub', `모든 목표를 달성했어요. 총 납품 ${stats.delivered}개 · 플레이 ${Math.round(stats.playTime / 60)}분`));
    body.appendChild(el('p', 'unlock-sub', '계속 공장을 키워도 좋아요.'));
    const ok = el('button', 'menu-btn primary', '계속하기');
    onTap(ok, () => this.closeModal());
    body.appendChild(ok);
    this.openModal('축하합니다', body);
  }

  // ---------------------------------------------------------
  toast(msg, kind = 'info', ms = 1800) {
    const t = this.toastEl;
    t.textContent = msg;
    t.className = 'toast show ' + kind;
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => { t.className = 'toast ' + kind; }, ms);
  }

  destroy() {
    this.root.classList.add('hidden');
    this.info.classList.add('hidden');
    this.closeModal();
  }
}

// =============================================================
// 메뉴 화면 (시작 메뉴 / 일시정지 메뉴)
// =============================================================
const Menu = {
  isOpen: false,   // 열려 있으면 게임 시뮬레이션 일시정지

  _render(buttons, note) {
    this.isOpen = true;
    const m = $('#menu');
    m.classList.remove('hidden');
    const box = $('#menu-buttons');
    box.innerHTML = '';
    buttons.forEach(([label, cls, fn]) => {
      const b = el('button', 'menu-btn ' + cls, label);
      b.onclick = () => { Sfx.unlock(); Sfx.click(); fn(b); };
      box.appendChild(b);
    });
    if (note) box.appendChild(el('p', 'menu-note', note));
  },

  // 시작 메뉴: 저장이 있으면 '이어하기'
  showMain(onContinue, onNew) {
    const meta = SaveSystem.meta();
    const close = () => this.hide();
    const buttons = [];
    let note = '';
    if (meta && SaveSystem.read()) {
      buttons.push(['이어하기', 'primary', () => { close(); onContinue(); }]);
      const d = new Date(meta.savedAt);
      note = `저장: ${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` +
        ` · 건물 ${meta.buildings} · 납품 ${meta.delivered} · 목표 ${meta.done}개 달성`;
      buttons.push(['새 게임', 'danger', b => {
        if (!b.dataset.confirm) { b.dataset.confirm = '1'; b.textContent = '저장을 지우고 새로 시작할까요? (한 번 더)'; return; }
        SaveSystem.clear(); close(); onNew();
      }]);
    } else {
      buttons.push(['새 게임', 'primary', () => { close(); onNew(); }]);
    }
    this._render(buttons, note);
  },

  // 게임 중 메뉴
  showPause(opts) {
    const close = () => this.hide();
    this._render([
      ['계속하기', 'primary', () => { close(); opts.onResume(); }],
      ['지금 저장', '', b => { b.textContent = opts.onSave() ? '저장했어요 ✓' : '저장 실패 (저장 공간 확인)'; }],
      ['목표와 해금 트리', '', () => { close(); opts.onTech(); }],
      [Sfx.enabled ? '효과음: 켬' : '효과음: 끔', '', b => {
        Sfx.enabled = !Sfx.enabled;
        b.textContent = Sfx.enabled ? '효과음: 켬' : '효과음: 끔';
      }],
      [opts.fpsVisible() ? 'FPS 표시: 켬' : 'FPS 표시: 끔', '', b => {
        opts.toggleFps();
        b.textContent = opts.fpsVisible() ? 'FPS 표시: 켬' : 'FPS 표시: 끔';
      }],
      ['새 게임', 'danger', b => {
        if (b.dataset.confirm) { close(); opts.onNew(); return; }
        b.dataset.confirm = '1';
        b.textContent = '저장을 지우고 새로 시작할까요? (한 번 더)';
      }],
    ]);
  },

  hide() { this.isOpen = false; $('#menu').classList.add('hidden'); },
};
