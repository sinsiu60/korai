// =============================================================
// perf.js — 성능 점검 도우미
//   주소 뒤에 ?stress=2000 을 붙이면 빈 칸에 컨베이어를 대량으로 깔아
//   화면 FPS(오른쪽 위)를 확인할 수 있다. (2단계부터는 아이템 흐름도 추가)
// =============================================================

const Perf = {
  stressBuildings(scene, n) {
    const g = scene.grid;
    let placed = 0;
    // 맵 중앙에서 바깥으로 훑으며 빈 칸에 설치
    const cx = g.w / 2, cy = g.h / 2;
    const cells = [];
    for (let y = 0; y < g.h; y++) for (let x = 0; x < g.w; x++) cells.push([x, y, Util.dist(x, y, cx, cy)]);
    cells.sort((a, b) => a[2] - b[2]);
    for (const [x, y] of cells) {
      if (placed >= n) break;
      if (g.buildingIdAt(x, y)) continue;
      // 줄마다 방향을 바꿔 지그재그 라인처럼
      const dir = (y & 1) ? DIR.W : DIR.E;
      if (scene.buildings.place('conveyor', x, y, dir)) placed++;
    }
    scene.ui.toast(`성능 테스트: 컨베이어 ${placed}개 배치`);
    return placed;
  },
};
