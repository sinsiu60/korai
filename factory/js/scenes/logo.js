// =============================================================
// scenes/logo.js — 개발사 "시우 공방" 로고를 짧게 보여준다 (탭하면 건너뜀)
// =============================================================

class LogoScene extends Phaser.Scene {
  constructor() { super('Logo'); }

  create() {
    const cam = this.cameras.main;
    cam.setBackgroundColor('#1b2230');
    const cx = cam.width / 2, cy = cam.height / 2;
    const unit = Math.min(cam.width, cam.height);

    const logo = this.add.image(cx, cy - unit * 0.08, 'logo');
    logo.setDisplaySize(unit * 0.34, unit * 0.34);

    const title = this.add.text(cx, cy + unit * 0.17, '시우 공방', {
      fontFamily: '-apple-system, "Apple SD Gothic Neo", "Noto Sans KR", "Malgun Gothic", sans-serif',
      fontSize: Math.round(unit * 0.075) + 'px',
      fontStyle: 'bold',
      color: '#ffffff',
    }).setOrigin(0.5);

    const sub = this.add.text(cx, cy + unit * 0.25, 'SIWOO WORKSHOP', {
      fontFamily: '-apple-system, "Helvetica Neue", Arial, sans-serif',
      fontSize: Math.round(unit * 0.028) + 'px',
      color: '#f2c14e',
      letterSpacing: Math.round(unit * 0.008),
    }).setOrigin(0.5);

    const parts = [logo, title, sub];
    parts.forEach(p => p.setAlpha(0));
    logo.setScale(logo.scaleX * 0.85);

    // 등장 → 잠시 유지 → 사라짐
    this.tweens.add({ targets: parts, alpha: 1, duration: 450, ease: 'Sine.Out' });
    this.tweens.add({ targets: logo, scale: logo.scaleX / 0.85, duration: 600, ease: 'Back.Out' });
    this.tweens.add({ targets: logo, angle: 30, duration: 1800, ease: 'Sine.InOut' });

    this._done = false;
    this.time.delayedCall(1500, () => this._finish(parts));
    this.input.once('pointerdown', () => this._finish(parts, true));
  }

  _finish(parts, fast) {
    if (this._done) return;
    this._done = true;
    this.tweens.add({
      targets: parts, alpha: 0, duration: fast ? 150 : 350,
      onComplete: () => this.scene.start('Title'),
    });
  }
}

// 타이틀(시작 메뉴) — 배경만 그리고 메뉴는 HTML로 띄운다
class TitleScene extends Phaser.Scene {
  constructor() { super('Title'); }

  create() {
    this.cameras.main.setBackgroundColor('#1b2230');
    Menu.showMain(() => {
      this.scene.start('Game', { seed: (Date.now() ^ (Math.random() * 1e9)) >>> 0 });
    });
  }
}
