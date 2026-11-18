// Regenerates app/src/main/assets/upgrade_times.json (full upgrade durations, for the progress bars) from ClashKing's data.
// Usage: node tools/gen_upgrade_times_ck.cjs <ClashKingAssets>/assets/static_data.json app/src/main/assets/upgrade_times.json
//
// Output: { "source": "...", "times": { "<dataId>": { "<level>": seconds, ... }, ... } }
// The time stored under level N is the time to upgrade TO level N (level 1 = build time). ClashKing keeps the time of a
// troop / spell / hero / pet step on the level it starts from, so those are shifted by one (see gen_max_data_ck.cjs).
// Home village and Builder Base. Walls are instant and equipment has no timer, so both are left out. Anything the old
// file has that ClashKing does not (crafted-defense modules) is kept.
const fs = require('fs');
const [inFile, outFile] = process.argv.slice(2);
const data = JSON.parse(fs.readFileSync(inFile, 'utf8'));
let previous = {};
try { previous = JSON.parse(fs.readFileSync(outFile, 'utf8')).times || {}; } catch (e) { /* first run */ }
const times = {};
for (const group of ['buildings', 'traps']) {
  for (const o of data[group]) {
    if (o.type === 'Wall' || !Array.isArray(o.levels) || !o.levels.length) continue;
    times[o._id] = Object.fromEntries(o.levels.map(l => [l.level, l.build_time || 0]));
  }
}
for (const group of ['troops', 'spells', 'heroes', 'pets', 'guardians']) {
  for (const o of data[group]) {
    const L = o.levels;
    if (!Array.isArray(L) || !L.some(l => (l.upgrade_cost || 0) > 0 || (l.upgrade_time || 0) > 0)) continue;
    const t = { 1: 0 };
    for (let i = 0; i < L.length - 1; i++) t[L[i + 1].level] = L[i].upgrade_time || 0;
    times[o._id] = t;
  }
}
let kept = 0;
for (const [id, t] of Object.entries(previous)) {
  if (!times[id]) { times[id] = t; kept++; continue; }
  for (const [lv, s] of Object.entries(t)) if (!(lv in times[id])) times[id][lv] = s; // levels ClashKing does not list (some Builder Base troops)
}
const sorted = Object.fromEntries(Object.keys(times).map(Number).sort((a, b) => a - b).map(k => [k, times[k]]));
fs.writeFileSync(outFile, JSON.stringify({ source: 'ClashKingAssets static_data.json', times: sorted }) + '\n');
console.log(`upgrade times for ${Object.keys(sorted).length} ids (${kept} kept from the old file) -> ${outFile}`);
