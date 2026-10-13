// =============================================================
// registry.js — JSON 데이터를 게임에서 쓰기 좋은 형태로 정리
//   - 아이템은 시뮬레이션에서 숫자(인덱스)로 다룬다 (문자열 비교/객체 생성 줄이기)
// =============================================================

const Registry = {
  itemIds: [],        // 인덱스 → 아이템 id
  itemIndex: {},      // 아이템 id → 인덱스
  recipes: {},        // 레시피 id → 레시피
  recipeList: [],
  unlockedBuildings: new Set(),
  unlockedRecipes: new Set(),

  init() {
    this.itemIds = Object.keys(GameData.items).filter(k => !k.startsWith('_'));
    this.itemIndex = {};
    this.itemIds.forEach((id, i) => { this.itemIndex[id] = i; });

    this.recipeList = GameData.recipes.recipes;
    this.recipes = {};
    this.recipeList.forEach(r => {
      this.recipes[r.id] = r;
      // 시뮬레이션용: [아이템번호, 수량] 배열
      r.inIdx = Object.keys(r.in).map(k => [this.itemIndex[k], r.in[k]]);
      r.outIdx = Object.keys(r.out).map(k => [this.itemIndex[k], r.out[k]]);
      r.outSum = r.outIdx.reduce((a, x) => a + x[1], 0);
    });
    this.resetUnlocks();
  },

  resetUnlocks() {
    const s = GameData.tech.start;
    this.unlockedBuildings = new Set(s.buildings);
    this.unlockedRecipes = new Set(s.recipes);
  },

  item(idx) { return GameData.items[this.itemIds[idx]]; },
  itemName(idOrIdx) {
    const id = typeof idOrIdx === 'number' ? this.itemIds[idOrIdx] : idOrIdx;
    return (GameData.items[id] && GameData.items[id].name) || id;
  },

  // 해당 기계로 만들 수 있는 (해금된) 레시피 목록
  recipesFor(machine) {
    return this.recipeList.filter(r => r.machine === machine && this.unlockedRecipes.has(r.id));
  },

  // 입력 아이템으로 레시피 찾기 (제련소 자동 선택용)
  recipeByInput(machine, itemId) {
    return this.recipesFor(machine).find(r => r.in[itemId] !== undefined) || null;
  },

  isBuildingUnlocked(type) { return this.unlockedBuildings.has(type); },
};
