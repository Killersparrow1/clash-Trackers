// Regenerates app/src/main/assets/max_data.json from ClashKing's static game data (it follows the game faster than the
// clash-of-clans-data package and already has Town Hall 19). Same output format as gen_max_data.cjs.
// Usage:
//   node tools/gen_max_data_ck.cjs <path-to>/ClashKingAssets/assets/static_data.json app/src/main/assets/max_data.json
// The output file is also read first (if it exists): buildings that ClashKing lists without any levels (Helper Hut and
// Crafting Station at the moment) keep the steps they already have.
// Data: https://github.com/ClashKingInc/ClashKingAssets (GPL-3.0; credited in the app under Support > Credits).
//
// How ClashKing's numbers map to ours:
//   buildings, traps: a level holds the cost / time / Town Hall to reach THAT level (same as our step list).
//   troops, spells, heroes, pets, guardians: a level holds the cost / time to go from it to the NEXT level, so the step
//     to level N + 1 is read from level N, and the Town Hall requirement comes from level N + 1.
//   walls: ClashKing lists Gold only; from level 5 walls can also use Elixir, which we keep as "Gold or Elixir".
//   counts per Town Hall: added up from the "unlocks" of each Town Hall level.
const fs = require('fs');
const [inFile, outFile] = process.argv.slice(2);
const data = JSON.parse(fs.readFileSync(inFile, 'utf8'));
const RES = { Gold: 0, Elixir: 1, 'Dark Elixir': 2, 'Gold or Elixir': 3 };
const SKIP_IDS = new Set([1000001 /* Town Hall */, 1000015 /* Builder's Hut */, 1000064 /* B.O.B's Hut */]);
const BUILDING_CAT = { Defense: 'd', Army: 'a', Resource: 'r', Helper: 'p' };
const warnings = [];
const items = {};
let previous = {};
try { previous = JSON.parse(fs.readFileSync(outFile, 'utf8')).items || {}; } catch (e) { /* first run */ }

const townHall = data.buildings.find(b => b._id === 1000001);
const MAX_TH = Math.max(...townHall.levels.map(l => l.level));

// Counts: cumulative "unlocks" quantity per Town Hall level.
const unlocked = {};
for (const l of townHall.levels) for (const u of l.unlocks || []) (unlocked[u._id] ||= {})[l.level] = u.quantity;
function counts(id) {
  const u = unlocked[id];
  if (!u) return null;
  const n = [];
  let total = 0;
  for (let t = 0; t <= MAX_TH; t++) { total += u[t] || 0; n.push(total); }
  return n;
}

// ClashKing's file names: lower case, spaces to "_", and . ? ' removed (see their README, "Programmatic Paths").
const clean = s => s.toLowerCase().replace(/ /g, '_').replace(/\./g, '').replace(/\?/g, '').replace(/\\q/g, '').replace(/\u2019/g, '');
// Where the picture of an item lives on https://assets.clashk.ing, relative to the site root (null = not known).
function iconPath(group, o, levels) {
  const c = clean(o.name);
  switch (group) {
    case 'buildings': return `buildings/home-village/${c}/level_${levels}.webp`;
    case 'traps': return `traps/home-village/${c}/level_${levels}.webp`;
    case 'spells': return `spells/${c}.webp`;
    default: return `${group}/${c}/icon.webp`; // troops, heroes, pets, guardians
  }
}

function add(o, kind, cat, steps, group) {
  if (!steps.length) return;
  // A step without a Town Hall requirement would break the app's parser: assume the newest Town Hall (and say so).
  for (const st of steps) {
    if (typeof st[3] !== 'number') { warnings.push(`${o.name}: no Town Hall requirement in ClashKing data, assumed Town Hall ${MAX_TH}`); st[3] = MAX_TH; }
  }
  items[o._id] = { k: kind, c: cat, n: counts(o._id), l: steps, nm: o.name, i: iconPath(group, o, steps.length) };
}
const res = (o, name) => {
  const r = RES[o.upgrade_resource];
  if (r === undefined) warnings.push(`${name}: unknown resource ${o.upgrade_resource}`);
  return r ?? 0;
};

// Buildings (home village), including walls.
for (const o of data.buildings) {
  if (o.village !== 'home' || SKIP_IDS.has(o._id) || !Array.isArray(o.levels)) continue;
  const cat = BUILDING_CAT[o.type];
  if (o.type === 'Wall') {
    add(o, 'w', 'p', o.levels.map(l => [l.build_time || 0, l.build_cost || 0, l.level >= 5 ? 3 : 0, l.required_townhall]), 'buildings');
  } else if (cat) {
    add(o, 'b', cat, o.levels.map(l => [l.build_time || 0, l.build_cost || 0, res(o, o.name), l.required_townhall]), 'buildings');
    // Buildings ClashKing has no levels for: keep what we had (no icon path, they ship with the app).
    if (!o.levels.length && previous[o._id]) { items[o._id] = previous[o._id]; warnings.push(`${o.name}: no levels in ClashKing data, kept the old steps`); }
  } // Worker and similar are not upgrades we track
}
for (const o of data.traps) {
  if (o.village !== 'home') continue;
  add(o, 'b', 'p', o.levels.map(l => [l.build_time || 0, l.build_cost || 0, res(o, o.name), l.required_townhall]), 'traps');
}

// Things researched or levelled with "cost to go to the next level" on each level.
function nextLevelSteps(o) {
  const L = o.levels;
  const steps = [[0, 0, res(o, o.name), L[0].required_townhall]]; // level 1 is unlocked, not bought
  for (let i = 0; i < L.length - 1; i++) {
    const time = L[i].upgrade_time || 0, cost = L[i].upgrade_cost || 0;
    if (time === 0 && cost > 0) warnings.push(`${o.name} level ${i + 1} -> ${i + 2}: cost but no time (short times may be rounded down)`);
    steps.push([time, cost, res(o, o.name), L[i + 1].required_townhall]);
  }
  return steps;
}
for (const [group, kind, cat] of [['troops', 'l', 'a'], ['spells', 'l', 'a'], ['heroes', 'h', 'a'], ['pets', 'p', 'a'], ['guardians', 'b', 'p']]) {
  for (const o of data[group]) {
    if (o.village === 'builderBase' || !Array.isArray(o.levels) || !o.levels.length) continue;
    // Only things with an upgrade cost are researched or levelled by the player; super, seasonal and event troops and
    // spells have none.
    if (!o.levels.some(l => (l.upgrade_cost || 0) > 0)) continue;
    add(o, kind, cat, nextLevelSteps(o), group);
  }
}

const sorted = Object.fromEntries(Object.keys(items).map(Number).sort((a, b) => a - b).map(k => [k, items[k]]));
fs.writeFileSync(outFile, JSON.stringify({ source: 'ClashKingAssets static_data.json', maxTh: MAX_TH, items: sorted }) + '\n');
console.log(`${Object.keys(sorted).length} items, max Town Hall ${MAX_TH}`);
warnings.forEach(w => console.warn('warning:', w));
