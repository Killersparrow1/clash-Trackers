// Writes app/src/main/assets/icon_paths.json: for every icon the app or a widget can show, where ClashKing keeps its
// picture on https://assets.clashk.ing (relative to the site root). Only paths that exist in the asset repo are written.
// Usage: node tools/gen_icon_paths_ck.cjs <ClashKingAssets>/assets app/src/main/assets/icon_paths.json
// Keys are the game data id ("1000008" = Cannon) or a widget artwork name ("builder_potion").
// ClashKing's file names: lower case, spaces to "_", and . ? ' removed (see their README, "Programmatic Paths").
const fs = require('fs'), path = require('path');
const [root, outFile] = process.argv.slice(2);
const data = JSON.parse(fs.readFileSync(path.join(root, 'static_data.json'), 'utf8'));
const clean = s => s.toLowerCase().replace(/ /g, '_').replace(/\./g, '').replace(/\?/g, '').replace(/\\q/g, '').replace(/’/g, '');
const exists = p => fs.existsSync(path.join(root, p));
const out = {};
const warnings = [];

// The newest level's picture of a building or trap (they change as they level up).
function topLevel(dir, levels) {
  for (let n = levels; n >= 1; n--) if (exists(`${dir}/level_${n}.webp`)) return `${dir}/level_${n}.webp`;
  return null;
}
function put(id, name, candidates) {
  const hit = candidates.find(c => c && exists(c));
  if (hit) out[id] = hit; else warnings.push(`${id} ${name}: no picture found`);
}
for (const o of data.buildings) {
  if (!Array.isArray(o.levels) || !o.levels.length) continue;
  const base = o.village === 'builderBase' ? 'builder-base' : 'home-village';
  const n = o.levels.length;
  put(o._id, o.name, [topLevel(`buildings/${base}/${clean(o.name)}`, n)]);
}
for (const o of data.traps) {
  const base = o.village === 'builderBase' ? 'builder-base' : 'home-village';
  put(o._id, o.name, [topLevel(`traps/${base}/${clean(o.name)}`, o.levels.length)]);
}
for (const g of ['troops', 'heroes', 'pets', 'guardians']) {
  for (const o of data[g]) put(o._id, o.name, [`${g}/${clean(o.name)}/icon.webp`]);
}
for (const o of data.spells) put(o._id, o.name, [`spells/${clean(o.name)}.webp`]);
for (const o of data.helpers) put(o._id, o.name, [`helpers/${clean(o.name)}.webp`, `helpers/${o.name.toLowerCase().replace(/ /g, '_')}.webp`]);
// Widget artwork.
const art = {
  builder_potion: 'magic_items/builder_potion.webp',
  research_potion: 'magic_items/research_potion.webp',
  pet_potion: 'magic_items/pet_potion.webp',
  builder_bite: 'magic_snacks/builder_bite.webp',
  apprentice: "helpers/builder's_apprentice.webp",
  lab_assistant: 'helpers/lab_assistant.webp',
};
for (const [k, p] of Object.entries(art)) put(k, k, [p]);
const sorted = Object.fromEntries(Object.keys(out).sort().map(k => [k, out[k]]));
fs.writeFileSync(outFile, JSON.stringify({ source: 'ClashKingAssets (assets.clashk.ing)', paths: sorted }) + '\n');
console.log(`${Object.keys(sorted).length} icon paths -> ${outFile}`);
warnings.slice(0, 40).forEach(w => console.warn('warning:', w));
if (warnings.length > 40) console.warn(`... and ${warnings.length - 40} more`);
