// Regenerates app/src/main/assets/names.json (English) and names_<xx>.json from ClashKing's game data.
// Usage:
//   node tools/gen_names_ck.cjs <ClashKingAssets>/assets static_names.json  EN   app/src/main/assets/names.json
//   node tools/gen_names_ck.cjs <ClashKingAssets>/assets static_names.json  RU   app/src/main/assets/names_ru.json
// (the second argument is only a label; the data is <assets>/static_data.json and <assets>/translations/<LOCALE>.json)
// Names come from the game's own text for each item (its TID), so they are the official wording in every language.
// Entries ClashKing does not have (crafted-defense module stats, ids 102xxxxxx) are kept from the existing file.
const fs = require('fs'), path = require('path');
const [assets, , locale, outFile] = process.argv.slice(2);
const data = JSON.parse(fs.readFileSync(path.join(assets, 'static_data.json'), 'utf8'));
const tr = locale === 'EN' ? null : JSON.parse(fs.readFileSync(path.join(assets, 'translations', `${locale}.json`), 'utf8'));
let previous = {};
try { previous = JSON.parse(fs.readFileSync(outFile, 'utf8')).names || {}; } catch (e) { /* first run */ }

const SKIP = new Set(['locales', 'achievements', 'skins', 'sceneries', 'obstacles', 'decorations', 'emotes', 'capital_house_parts',
  'chests', 'magic_snacks', 'player_labels', 'clan_labels', 'league_tiers', 'war_leagues', 'star_bonus', 'builder_leagues',
  'capital_leagues', 'battle_modifiers', 'resources']);
// Our own wording where the game's building name is confusing next to the hero of the same name.
const KEEP = { 1000053: 'Battle Machine Altar', 1000080: 'Battle Copter Altar' };
const names = {};
const text = o => {
  const tid = o.TID && o.TID.name;
  return (tr && tid && tr[tid]) || o.name;
};
function scan(o) {
  if (Array.isArray(o)) return o.forEach(scan);
  if (!o || typeof o !== 'object') return;
  if (typeof o._id === 'number' && typeof o.name === 'string' && names[o._id] === undefined) names[o._id] = text(o);
  for (const k in o) if (k !== 'levels') scan(o[k]);
}
for (const g of Object.keys(data)) if (!SKIP.has(g)) scan(data[g]);
for (const [id, n] of Object.entries(KEEP)) if (names[id] !== undefined) names[id] = previous[id] || n;
let kept = 0;
for (const [id, n] of Object.entries(previous)) {
  // Module stats (Damage, Hitpoints, ...) are not in ClashKing's data; everything else only if it is missing.
  if (names[id] === undefined || (+id >= 102000000 && +id < 103000000)) { if (names[id] !== n) kept++; names[id] = n; }
}
const sorted = Object.fromEntries(Object.keys(names).map(Number).sort((a, b) => a - b).map(k => [k, names[k]]));
const label = locale === 'EN' ? 'English' : locale;
fs.writeFileSync(outFile, JSON.stringify({ source: `ClashKingAssets static_data.json (${label})`, names: sorted }, null, 1) + '\n');
console.log(`${Object.keys(sorted).length} names (${kept} kept from the old file) -> ${outFile}`);
