// Regenerates app/src/main/assets/max_data.json (the "Road to max" card) from the `clash-of-clans-data` repo.
// Usage (same unpacked repo/tarball as gen_upgrade_times.cjs, no npm install needed):
//   node tools/gen_max_data.cjs <path-to-unpacked>/package app/src/main/assets/max_data.json
//
// Output: { "source": "...", "maxTh": 18, "items": { "<dataId>": { "k": kind, "n": [count per Town Hall] | null,
//           "l": [[seconds, cost, resource, townHallRequired], ...] } } }
//   kind:     b = building/defense/trap/guardian, w = wall, l = lab (troop/spell/siege), h = hero, p = pet
//   resource: 0 Gold, 1 Elixir, 2 Dark Elixir, 3 Gold or Elixir
//   "l"[i] is the step that takes the item TO level i + 1 (level 1 = build / unlock). "n" is indexed by Town Hall level.
// Only home village items. Town Hall itself, Builder's Hut (gems), B.O.B's Hut, crafted-defense modules, equipment and
// Builder Base are left out.
const fs = require('fs'), path = require('path');
const [pkgDir, outFile] = process.argv.slice(2);
const version = JSON.parse(fs.readFileSync(path.join(pkgDir, 'package.json'), 'utf8')).version;
const home = path.join(pkgDir, 'data', 'home');

const KIND = {
  'army-buildings': 'b', defenses: 'b', 'resource-buildings': 'b', traps: 'b', guardians: 'b', other: 'b',
  walls: 'w', troops: 'l', spells: 'l', 'siege-machines': 'l', heroes: 'h', pets: 'p',
};
const SKIP_IDS = new Set([1000001 /* Town Hall */, 1000015 /* Builder's Hut */, 1000064 /* B.O.B's Hut */]);
const RES = { Gold: 0, Elixir: 1, 'Dark Elixir': 2, 'Gold or Elixir': 3 };
const secs = t => (t ? ((t.days * 24 + t.hours) * 60 + t.minutes) * 60 + (t.seconds || 0) : 0);
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const MAX_TH = Math.max(...read(path.join(home, 'town-hall', 'town-hall.json')).levels.map(l => l.level));

// Hero Hall level -> the Town Hall level that first allows it (hero levels are gated by Hero Hall).
const heroHall = read(path.join(home, 'army-buildings', 'hero-hall.json'));
const hallTh = {};
for (const l of heroHall.levels) hallTh[l.level] = l.townHallRequired;
const thForHall = lvl => { const ks = Object.keys(hallTh).map(Number).sort((a, b) => a - b); for (const k of ks) if (k >= lvl) return hallTh[k]; return MAX_TH; };

const items = {};
const warnings = [];
for (const dir of Object.keys(KIND)) {
  const d = path.join(home, dir);
  if (!fs.existsSync(d)) continue;
  const walk = p => fs.readdirSync(p).sort().forEach(f => {
    const fp = path.join(p, f);
    if (fs.statSync(fp).isDirectory()) return f === 'helpers' ? undefined : walk(fp); // helpers are not builder/lab upgrades
    if (!fp.endsWith('.json')) return;
    const o = read(fp);
    if (!o || typeof o.dataId !== 'number' || SKIP_IDS.has(o.dataId) || !Array.isArray(o.levels)) return;
    const kind = KIND[dir];
    const steps = [];
    for (const l of o.levels.filter(l => l.supercharge === undefined).sort((a, b) => a.level - b.level)) {
      if (l.level !== steps.length + 1) { warnings.push(`${o.id}: level ${l.level} out of order`); break; }
      const res = RES[l.buildCostResource || l.upgradeCostResource || l.researchCostResource];
      const cost = l.buildCost ?? l.upgradeCost ?? l.researchCost ?? 0;
      const time = secs(l.buildTime || l.upgradeTime || l.researchTime);
      let th = l.townHallRequired;
      if (kind === 'h') th = thForHall(l.heroHallLevelRequired);
      if (th === undefined) { warnings.push(`${o.id} lvl ${l.level}: no Town Hall requirement`); th = 1; }
      if (res === undefined && cost > 0) warnings.push(`${o.id} lvl ${l.level}: unknown resource`);
      steps.push([time, cost, res ?? 0, th]);
    }
    if (!steps.length) return;
    let n = null;
    if (Array.isArray(o.availablePerTownHall)) {
      n = [];
      let last = 0;
      for (let t = 0; t <= MAX_TH; t++) {
        const e = o.availablePerTownHall.find(x => x.townHallLevel === t);
        if (e) last = e.count;
        n.push(last);
      }
    }
    items[o.dataId] = { k: kind, n, l: steps };
  });
  walk(d);
}
const sorted = Object.fromEntries(Object.keys(items).map(Number).sort((a, b) => a - b).map(k => [k, items[k]]));
fs.writeFileSync(outFile, JSON.stringify({ source: `clash-of-clans-data@${version} (data/home)`, maxTh: MAX_TH, items: sorted }) + '\n');
console.log(`${Object.keys(sorted).length} items, max Town Hall ${MAX_TH}`);
warnings.forEach(w => console.warn('warning:', w));
