// =============================================================
// input.js — 터치 조작 (카메라 이동/줌, 탭, 길게 누르기, 드래그 설치)
//   한 손가락 드래그 : 맵 이동 (컨베이어 도구일 때는 연속 설치)
//   두 손가락       : 핀치 줌 + 이동
//   탭             : 배치 / 정보 패널
//   길게 누르기      : 삭제
//   마우스(PC 테스트용): 드래그 이동, 휠 줌
// =============================================================

const LONG_PRESS_MS = 550;   // 길게 누르기 판정 시간
const LONG_SHOW_MS = 150;    // 이 시간 이후부터 진행 링을 보여줌
const DRAG_THRESHOLD = 12;   // CSS px — 이만큼 움직이면 탭이 아니라 드래그

// 카메라 줌 범위: '한 칸이 화면에서 몇 CSS px로 보이는가'
const ZOOM_MIN_CSS = 10;
const ZOOM_MAX_CSS = 128;
const ZOOM_DEFAULT_CSS = 52;

class CameraController {
  constructor(scene, mapW, mapH) {
    this.scene = scene;
    this.cam = scene.cameras.main;
    this.worldW = mapW * TILE;
    this.worldH = mapH * TILE;
    this.vx = 0; this.vy = 0; // 관성 이동 속도 (월드 px / ms)
  }

  cssToZoom(cssPerTile) { return cssPerTile * DPR / TILE; }
  get minZoom() { return this.cssToZoom(ZOOM_MIN_CSS); }
  get maxZoom() { return this.cssToZoom(ZOOM_MAX_CSS); }

  // 화면(게임 픽셀) → 월드 좌표. Phaser 카메라는 화면 중앙 기준으로 줌한다.
  screenToWorld(sx, sy) {
    const c = this.cam;
    return {
      x: c.scrollX + c.width / 2 + (sx - c.width / 2) / c.zoom,
      y: c.scrollY + c.height / 2 + (sy - c.height / 2) / c.zoom,
    };
  }

  // 월드 점 (wx,wy)가 화면 (sx,sy)에 오도록 스크롤
  pinWorldToScreen(wx, wy, sx, sy) {
    const c = this.cam;
    c.scrollX = wx - c.width / 2 - (sx - c.width / 2) / c.zoom;
    c.scrollY = wy - c.height / 2 - (sy - c.height / 2) / c.zoom;
    this.clamp();
  }

  centerOn(wx, wy) {
    const c = this.cam;
    c.scrollX = wx - c.width / 2;
    c.scrollY = wy - c.height / 2;
    this.clamp();
  }

  setZoom(z) {
    this.cam.zoom = Util.clamp(z, this.minZoom, this.maxZoom);
  }

  // 화면 중심이 맵 밖으로 나가지 않게
  clamp() {
    const c = this.cam;
    const cx = Util.clamp(c.scrollX + c.width / 2, 0, this.worldW);
    const cy = Util.clamp(c.scrollY + c.height / 2, 0, this.worldH);
    c.scrollX = cx - c.width / 2;
    c.scrollY = cy - c.height / 2;
  }

  panBy(dxScreen, dyScreen) {
    const c = this.cam;
    c.scrollX -= dxScreen / c.zoom;
    c.scrollY -= dyScreen / c.zoom;
    this.clamp();
  }

  // 관성 이동 처리
  update(dt) {
    if (this.vx === 0 && this.vy === 0) return;
    this.cam.scrollX -= this.vx * dt;
    this.cam.scrollY -= this.vy * dt;
    this.clamp();
    const decay = Math.pow(0.992, dt);
    this.vx *= decay; this.vy *= decay;
    if (Math.abs(this.vx) < 0.01 && Math.abs(this.vy) < 0.01) { this.vx = 0; this.vy = 0; }
  }

  stopInertia() { this.vx = 0; this.vy = 0; }
}

class TouchController {
  /**
   * @param {Phaser.Scene} scene
   * @param {CameraController} camCtl
   * @param {object} h 콜백 모음 (onTap, wantsLongPress, onLongPress, onLongProgress, onPress, onPressEnd,
   *                   wantsStroke, onStrokeStart, onStrokeMove, onStrokeEnd, onStrokeCancel, onHover)
   */
  constructor(scene, camCtl, h) {
    this.scene = scene;
    this.camCtl = camCtl;
    this.h = h;
    this.state = 'idle';
    this.pointers = new Map(); // id -> {x, y}
    this.start = null;
    this.panRef = null;
    this.pinch = null;
    this.lastMoveT = 0;
    this.enabled = true;

    const inp = scene.input;
    inp.addPointer(2); // 터치 포인터 총 3개
    inp.on('pointerdown', p => this._down(p));
    inp.on('pointermove', p => this._move(p));
    inp.on('pointerup', p => this._up(p));
    inp.on('pointerupoutside', p => this._up(p));
    inp.on('wheel', (p, objs, dx, dy) => this._wheel(p, dy));
    inp.mouse && inp.mouse.disableContextMenu();
  }

  _tileAt(sx, sy) {
    const w = this.camCtl.screenToWorld(sx, sy);
    return { x: Math.floor(w.x / TILE), y: Math.floor(w.y / TILE), wx: w.x, wy: w.y };
  }

  _down(p) {
    if (!this.enabled) return;
    if (p.button && p.button !== 0) return; // 마우스 오른쪽 버튼 무시
    Sfx.unlock();
    this.pointers.set(p.id, { x: p.x, y: p.y });
    this.camCtl.stopInertia();

    if (this.pointers.size === 1) {
      this.state = 'pending';
      const tile = this._tileAt(p.x, p.y);
      this.start = { x: p.x, y: p.y, t: performance.now(), tile, id: p.id };
      this.h.onPress && this.h.onPress(tile);
    } else if (this.pointers.size === 2) {
      // 두 번째 손가락: 하던 동작 취소하고 핀치로 전환
      if (this.state === 'stroke') this.h.onStrokeCancel && this.h.onStrokeCancel();
      this.h.onLongProgress && this.h.onLongProgress(null, 0);
      this.h.onPressEnd && this.h.onPressEnd();
      this._beginPinch();
    }
  }

  _beginPinch() {
    const pts = [...this.pointers.values()];
    const a = pts[0], b = pts[1];
    const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
    const world = this.camCtl.screenToWorld(mx, my);
    this.pinch = {
      dist: Math.max(1, Util.dist(a.x, a.y, b.x, b.y)),
      zoom: this.camCtl.cam.zoom,
      wx: world.x, wy: world.y,
    };
    this.state = 'pinch';
  }

  _move(p) {
    if (!this.enabled) return;
    const tracked = this.pointers.get(p.id);
    if (!tracked) {
      // 마우스 호버(버튼 안 누름) — PC에서 미리보기용
      if (!p.isDown && this.h.onHover) this.h.onHover(this._tileAt(p.x, p.y));
      return;
    }
    const prevX = tracked.x, prevY = tracked.y;
    tracked.x = p.x; tracked.y = p.y;

    switch (this.state) {
      case 'pending': {
        const moved = Util.dist(this.start.x, this.start.y, p.x, p.y);
        if (moved > DRAG_THRESHOLD * DPR) {
          this.h.onLongProgress && this.h.onLongProgress(null, 0);
          if (this.h.wantsStroke && this.h.wantsStroke(this.start.tile)) {
            this.state = 'stroke';
            this.h.onStrokeStart(this.start.tile);
            this.h.onStrokeMove(this._tileAt(p.x, p.y));
          } else {
            this.state = 'pan';
            this.h.onPressEnd && this.h.onPressEnd();
            this.camCtl.panBy(p.x - this.start.x, p.y - this.start.y);
            this.lastMoveT = performance.now();
          }
        }
        break;
      }
      case 'pan': {
        const dx = p.x - prevX, dy = p.y - prevY;
        this.camCtl.panBy(dx, dy);
        // 관성용 속도 추정 (월드 px/ms, 지수 평활)
        const now = performance.now();
        const dt = Math.max(1, now - this.lastMoveT);
        this.lastMoveT = now;
        const z = this.camCtl.cam.zoom;
        this.camCtl.vx = this.camCtl.vx * 0.6 + (dx / z / dt) * 0.4;
        this.camCtl.vy = this.camCtl.vy * 0.6 + (dy / z / dt) * 0.4;
        break;
      }
      case 'stroke':
        this.h.onStrokeMove(this._tileAt(p.x, p.y));
        break;
      case 'pinch':
        this._updatePinch();
        break;
    }
  }

  _updatePinch() {
    const pts = [...this.pointers.values()];
    if (pts.length < 2 || !this.pinch) return;
    const a = pts[0], b = pts[1];
    const d = Math.max(1, Util.dist(a.x, a.y, b.x, b.y));
    const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
    this.camCtl.setZoom(this.pinch.zoom * d / this.pinch.dist);
    this.camCtl.pinWorldToScreen(this.pinch.wx, this.pinch.wy, mx, my);
  }

  _up(p) {
    if (!this.pointers.has(p.id)) return;
    this.pointers.delete(p.id);

    if (this.state === 'pinch') {
      if (this.pointers.size === 1) {
        // 한 손가락만 남으면 그대로 이동 모드로 (점프 방지)
        this.state = 'pan';
        this.camCtl.stopInertia();
        this.lastMoveT = performance.now();
      } else if (this.pointers.size >= 2) {
        this._beginPinch();
      } else {
        this.state = 'idle';
      }
      return;
    }

    if (this.pointers.size > 0) return;

    if (this.state === 'pending') {
      this.h.onLongProgress && this.h.onLongProgress(null, 0);
      this.h.onTap && this.h.onTap(this.start.tile);
    } else if (this.state === 'stroke') {
      this.h.onStrokeEnd && this.h.onStrokeEnd();
    } else if (this.state === 'pan') {
      // 손을 뗀 직후가 아니라 멈췄다 뗀 경우엔 관성 없음
      if (performance.now() - this.lastMoveT > 80) this.camCtl.stopInertia();
    }
    this.h.onPressEnd && this.h.onPressEnd();
    this.state = 'idle';
  }

  _wheel(p, dy) {
    if (!this.enabled) return;
    const before = this.camCtl.screenToWorld(p.x, p.y);
    this.camCtl.setZoom(this.camCtl.cam.zoom * Math.pow(0.999, dy));
    this.camCtl.pinWorldToScreen(before.x, before.y, p.x, p.y);
  }

  // 매 프레임: 길게 누르기 판정
  update() {
    if (this.state !== 'pending' || !this.start) return;
    // 지울 건물이 없는 칸이면 길게 눌러도 그냥 탭으로 처리
    if (this.h.wantsLongPress && !this.h.wantsLongPress(this.start.tile)) return;
    const held = performance.now() - this.start.t;
    if (held >= LONG_PRESS_MS) {
      this.state = 'done';
      this.h.onLongProgress && this.h.onLongProgress(null, 0);
      this.h.onPressEnd && this.h.onPressEnd();
      this.h.onLongPress && this.h.onLongPress(this.start.tile);
    } else if (held > LONG_SHOW_MS) {
      this.h.onLongProgress && this.h.onLongProgress(this.start.tile, (held - LONG_SHOW_MS) / (LONG_PRESS_MS - LONG_SHOW_MS));
    }
  }

  // 메뉴 등이 열렸을 때 진행 중 동작 초기화
  reset() {
    this.pointers.clear();
    this.state = 'idle';
    this.pinch = null;
  }
}
