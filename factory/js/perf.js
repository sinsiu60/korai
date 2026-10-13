// =============================================================
// perf.js — 성능 점검 도우미 (주소 뒤에 붙여서 사용)
//   ?stress=2000  : 빈 칸에 컨베이어 2000개 배치 (건물 수 부하)
//   ?stress=items : 맵 가장자리에 컨베이어 고리 4개를 깔고 칸마다 아이템을 올려
//                   약 900개가 계속 돌게 만든다 (아이템 흐름 부하)
//   화면 오른쪽 위 FPS 표시로 확인한다.
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

  stressItems(scene, insets = [1, 3, 5, 7]) {
    const g = scene.grid, bm = scene.buildings, cs = scene.conveyors;
    let belts = 0, items = 0;
    const nItems = Registry.itemIds.length;
    insets.forEach((k, ri) => {
      const x0 = k, y0 = k, x1 = g.w - 1 - k, y1 = g.h - 1 - k;
      const path = [];
      for (let x = x0; x < x1; x++) path.push([x, y0, DIR.E]);
      for (let y = y0; y < y1; y++) path.push([x1, y, DIR.S]);
      for (let x = x1; x > x0; x--) path.push([x, y1, DIR.W]);
      for (let y = y1; y > y0; y--) path.push([x0, y, DIR.N]);
      path.forEach(([x, y, d], i) => {
        if (bm.at(x, y)) return;
        const b = bm.place('conveyor', x, y, d);
        if (!b) return;
        belts++;
        // 한 칸에 하나씩 (간격이 넉넉해서 계속 흐름)
        b.st.items.push(cs._newItem((i + ri) % nItems, 0.5));
        items++;
      });
    });
    scene.camCtl.setZoom(scene.camCtl.minZoom);
    scene.camCtl.centerOn(g.w * TILE / 2, g.h * TILE / 2);
    scene.ui.toast(`성능 테스트: 컨베이어 ${belts}개, 아이템 ${items}개`);
    return { belts, items };
  },
};
