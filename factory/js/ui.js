// =============================================================
// ui.js — 화면 위 HTML UI (상단 바, 하단 건물 툴바, 회전 버튼, 정보 패널, 토스트, 메뉴)
//   - 글자가 선명하고 반응형 배치가 쉬워서 UI는 DOM으로 만든다.
//   - 게임 월드(Phaser 캔버스) 터치와 겹치지 않도록 UI 요소만 터치를 받는다.
// =============================================================

const $ = (sel) => document.querySelector(sel);

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
}

class GameUI {
  /**
   * @param {object} game 게임 씬이 제공하는 동작 모음
   *   selectTool(type|null), rotatePressed(), deleteSelected(), closeInfo(), openMenu()
   */
  constructor(game, defs) {
    this.game = game;
    this.defs = defs;
    this.root = $('#hud');
    this.root.classList.remove('hidden');
    this.toolButtons = new Map();
    this._toastTimer = 0;

    this.toolbar = $('#toolbar');
    this.rotateBtn = $('#btn-rotate');
    this.cancelBtn = $('#btn-cancel');
    this.info = $('#info-panel');
    this.toastEl = $('#toast');
    this.fpsEl = $('#fps');
    this.statEl = $('#stat-buildings');

    this._buildToolbar();
    this._bind();
  }

  _bind() {
    this.rotateBtn.onclick = () => { Sfx.click(); this.game.rotatePressed(); };
    this.cancelBtn.onclick = () => { Sfx.click(); this.game.selectTool(null); };
    $('#btn-menu').onclick = () => { Sfx.click(); this.game.openMenu(); };
    $('#info-close').onclick = () => { Sfx.click(); this.game.closeInfo(); };
    $('#info-rotate').onclick = () => { Sfx.click(); this.game.rotatePressed(); };
    $('#info-delete').onclick = () => { this.game.deleteSelected(); };
  }

  // 하단 툴바: 첫 칸은 '보기'(선택 해제), 이후 buildings.json의 toolbar 건물
  _buildToolbar() {
    this.toolbar.innerHTML = '';
    const viewBtn = this._toolButton(null, '보기', this._handIcon(40));
    this.toolbar.appendChild(viewBtn);

    Object.keys(this.defs)
      .filter(k => !k.startsWith('_') && this.defs[k].toolbar)
      .sort((a, b) => (this.defs[a].order || 0) - (this.defs[b].order || 0))
      .forEach(type => {
        const def = this.defs[type];
        const btn = this._toolButton(type, def.name, Art.iconCanvas(type, def, 40));
        if (def.powerKW) {
          const kw = el('span', 'tool-kw', def.powerKW + 'kW');
          btn.appendChild(kw);
        }
        this.toolbar.appendChild(btn);
      });
    this.setTool(null, 0);
  }

  _toolButton(type, label, iconEl) {
    const btn = el('button', 'tool-btn');
    btn.appendChild(iconEl);
    btn.appendChild(el('span', 'tool-label', label));
    btn.onclick = () => {
      Sfx.unlock(); Sfx.click();
      // 이미 선택된 도구를 다시 누르면 해제
      const cur = this.game.tool;
      this.game.selectTool(cur === type ? null : type);
    };
    this.toolButtons.set(type, btn);
    return btn;
  }

  // '보기' 도구 아이콘 (손가락 포인터를 도형으로)
  _handIcon(css) {
    const px = Math.round(css * DPR);
    const c = Art.makeCanvas(px, px);
    c.style.width = css + 'px'; c.style.height = css + 'px';
    const ctx = c.getContext('2d');
    const s = px / 40;
    ctx.scale(s, s);
    ctx.fillStyle = '#ffffff';
    ctx.strokeStyle = '#2b3240';
    ctx.lineWidth = 2.2;
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(12, 6); ctx.lineTo(12, 30); ctx.lineTo(18, 24);
    ctx.lineTo(22, 33); ctx.lineTo(26, 31); ctx.lineTo(22, 22);
    ctx.lineTo(30, 22); ctx.closePath();
    ctx.fill(); ctx.stroke();
    return c;
  }

  // 도구 선택 상태 표시
  setTool(type, dir) {
    this.toolButtons.forEach((btn, t) => btn.classList.toggle('active', t === type));
    this.cancelBtn.classList.toggle('hidden', !type);
    this.updateRotate();
  }

  // 회전 버튼: 도구 선택 중이면 도구 방향, 건물 선택 중이면 그 건물 회전
  updateRotate() {
    const g = this.game;
    const sel = g.selected ? g.buildings.get(g.selected) : null;
    let enabled = false, dir = g.toolDir;
    if (g.tool) {
      enabled = !!this.defs[g.tool].rotatable;
    } else if (sel) {
      enabled = !!sel.def.rotatable;
      dir = sel.dir;
    }
    this.rotateBtn.disabled = !enabled;
    $('#rotate-arrow').style.transform = `rotate(${dir * 90}deg)`;
  }

  // ---------------------------------------------------------
  // 정보 패널
  // ---------------------------------------------------------
  showBuilding(b, extraRows) {
    const def = b.def;
    $('#info-icon').innerHTML = '';
    $('#info-icon').appendChild(Art.iconCanvas(b.type, def, 44));
    $('#info-title').textContent = def.name;
    $('#info-desc').textContent = def.desc || '';
    const rows = [
      ['위치', `${b.x}, ${b.y}`],
      ['크기', `${b.w} × ${b.h} 칸`],
    ];
    if (def.rotatable) rows.push(['방향', `${DIR_ARROW[b.dir]} ${DIR_NAME[b.dir]}`]);
    if (def.powerKW) rows.push(['소비 전력', `${def.powerKW} kW`]);
    (extraRows || []).forEach(r => rows.push(r));
    this._rows(rows);
    $('#info-rotate').classList.toggle('hidden', !def.rotatable);
    $('#info-delete').classList.toggle('hidden', !!def.fixed);
    $('#info-hint').textContent = def.fixed ? '고정 건물이라 옮기거나 지울 수 없어요' : '길게 누르면 바로 삭제돼요';
    this.info.classList.remove('hidden');
    this.updateRotate();
  }

  showTile(title, desc, rows) {
    $('#info-icon').innerHTML = '';
    $('#info-title').textContent = title;
    $('#info-desc').textContent = desc || '';
    this._rows(rows || []);
    $('#info-rotate').classList.add('hidden');
    $('#info-delete').classList.add('hidden');
    $('#info-hint').textContent = '';
    this.info.classList.remove('hidden');
  }

  _rows(rows) {
    const tbody = $('#info-rows');
    tbody.innerHTML = '';
    rows.forEach(([k, v]) => {
      const tr = el('div', 'info-row');
      tr.appendChild(el('span', 'k', k));
      tr.appendChild(el('span', 'v', v));
      tbody.appendChild(tr);
    });
  }

  hideInfo() {
    this.info.classList.add('hidden');
    this.updateRotate();
  }

  // ---------------------------------------------------------
  // 기타
  // ---------------------------------------------------------
  toast(msg, kind = 'info') {
    const t = this.toastEl;
    t.textContent = msg;
    t.className = 'toast show ' + kind;
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => { t.className = 'toast ' + kind; }, 1600);
  }

  setStats(s) {
    this.statEl.textContent = `건물 ${s.buildings}`;
  }

  setFps(fps, extra) {
    this.fpsEl.textContent = `${Math.round(fps)} FPS${extra ? ' · ' + extra : ''}`;
    this.fpsEl.classList.toggle('low', fps < 50);
  }

  destroy() {
    this.root.classList.add('hidden');
    this.info.classList.add('hidden');
  }
}

// =============================================================
// 메뉴 화면 (시작 메뉴 / 일시정지 메뉴)
// =============================================================
const Menu = {
  // 시작 메뉴
  showMain(onNew) {
    const m = $('#menu');
    m.classList.remove('hidden');
    $('#menu-title-sub').textContent = '시우 공방';
    const box = $('#menu-buttons');
    box.innerHTML = '';
    const b1 = el('button', 'menu-btn primary', '새 게임');
    b1.onclick = () => { Sfx.unlock(); Sfx.click(); m.classList.add('hidden'); onNew(); };
    box.appendChild(b1);
    // 이어하기는 5단계(저장)에서 추가
    box.appendChild(el('p', 'menu-note', '이어하기는 저장 기능(5단계)과 함께 추가됩니다'));
  },

  // 게임 중 메뉴
  showPause(opts) {
    const m = $('#menu');
    m.classList.remove('hidden');
    const box = $('#menu-buttons');
    box.innerHTML = '';
    const close = () => m.classList.add('hidden');
    const add = (label, cls, fn) => {
      const b = el('button', 'menu-btn ' + cls, label);
      b.onclick = () => { Sfx.click(); fn(b); };
      box.appendChild(b);
      return b;
    };
    add('계속하기', 'primary', () => { close(); opts.onResume(); });
    add(Sfx.enabled ? '효과음: 켬' : '효과음: 끔', '', b => {
      Sfx.enabled = !Sfx.enabled;
      b.textContent = Sfx.enabled ? '효과음: 켬' : '효과음: 끔';
    });
    add(opts.fpsVisible() ? 'FPS 표시: 켬' : 'FPS 표시: 끔', '', b => {
      opts.toggleFps();
      b.textContent = opts.fpsVisible() ? 'FPS 표시: 켬' : 'FPS 표시: 끔';
    });
    add('새 게임', 'danger', b => {
      if (b.dataset.confirm) { close(); opts.onNew(); return; }
      b.dataset.confirm = '1';
      b.textContent = '정말 새로 시작할까요? (한 번 더 누르기)';
    });
  },

  hide() { $('#menu').classList.add('hidden'); },
};
