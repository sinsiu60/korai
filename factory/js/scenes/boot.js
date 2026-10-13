// =============================================================
// scenes/boot.js — data/ 폴더의 JSON을 읽고 텍스처를 만든다
// =============================================================

// 읽어 온 게임 데이터 (모든 시스템이 참조)
const GameData = {
  buildings: null,
  items: null,
  map: null,
};

class BootScene extends Phaser.Scene {
  constructor() { super('Boot'); }

  preload() {
    this.load.setPath('data/');
    this.load.json('buildings', 'buildings.json');
    this.load.json('items', 'items.json');
    this.load.json('map', 'map.json');

    this.load.on('loaderror', (file) => {
      this._failed = file.key;
    });
  }

  create() {
    GameData.buildings = this.cache.json.get('buildings');
    GameData.items = this.cache.json.get('items');
    GameData.map = this.cache.json.get('map');

    if (this._failed || !GameData.buildings || !GameData.map) {
      // file:// 로 열면 브라우저 보안 때문에 JSON을 못 읽는다 → 안내 표시
      document.getElementById('boot-error').classList.remove('hidden');
      return;
    }

    Art.buildTileset(this, GameData.map);
    Art.buildBuildingTextures(this, GameData.buildings);
    Art.buildLogo(this);

    // ?skiplogo=1 이면 로고 생략 (개발/테스트용)
    const q = new URLSearchParams(location.search);
    this.scene.start(q.has('skiplogo') ? 'Title' : 'Logo');
  }
}
