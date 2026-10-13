// =============================================================
// main.js — Phaser 게임 생성과 화면 크기 대응
//   - 레티나 화면에서 선명하도록 캔버스를 (CSS 크기 × DPR)로 만들고 CSS로 축소한다.
// =============================================================

(function () {
  // iOS 사파리의 두 손가락 확대/더블탭 확대 막기 (게임이 직접 처리)
  ['gesturestart', 'gesturechange', 'gestureend'].forEach(ev =>
    document.addEventListener(ev, e => e.preventDefault(), { passive: false }));
  document.addEventListener('dblclick', e => e.preventDefault(), { passive: false });
  document.addEventListener('contextmenu', e => e.preventDefault());

  const cssW = () => window.innerWidth;
  const cssH = () => window.innerHeight;

  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: 'game',
    backgroundColor: '#1b2230',
    width: Math.round(cssW() * DPR),
    height: Math.round(cssH() * DPR),
    scale: { mode: Phaser.Scale.NONE, zoom: 1 / DPR },
    render: { antialias: true, powerPreference: 'high-performance' },
    fps: { target: 60 },
    input: { activePointers: 3 },
    banner: false,
    scene: [BootScene, LogoScene, TitleScene, GameScene],
  });
  window.__game = game; // 디버그용

  // 화면 회전/크기 변경 대응
  let resizeTimer = 0;
  function onResize() {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      game.scale.resize(Math.round(cssW() * DPR), Math.round(cssH() * DPR));
      updatePortraitHint();
    }, 60);
  }
  window.addEventListener('resize', onResize);
  window.addEventListener('orientationchange', onResize);

  // 세로 모드 안내 (닫으면 그 세션 동안 다시 안 띄움)
  let hintDismissed = false;
  function updatePortraitHint() {
    const portrait = cssH() > cssW();
    document.getElementById('portrait-hint').classList.toggle('hidden', !portrait || hintDismissed);
  }
  document.getElementById('portrait-dismiss').onclick = () => { hintDismissed = true; updatePortraitHint(); };
  updatePortraitHint();
})();
