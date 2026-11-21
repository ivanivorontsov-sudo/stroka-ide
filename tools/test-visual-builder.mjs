import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const html = fs.readFileSync('app/src/main/assets/visual_builder.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/gi)].map(m => m[1]);
assert.ok(scripts.length, 'visual builder must contain its inline script');
for (const source of scripts) new vm.Script(source, { filename: 'visual_builder.html' });

const start = html.indexOf('function normalizeVoiceCommand');
const end = html.indexOf('function voiceCommand', start);
assert.ok(start >= 0 && end > start, 'voice command normalizer and style parser must exist');
const parserSource = html.slice(start, end);
let activeTheme = '';
const grid = { style: {} };
const sandbox = {
  setTheme: name => { activeTheme = name; },
  selected: null,
  stage: { querySelectorAll: () => [grid] },
  update: () => {}
};
vm.createContext(sandbox);
vm.runInContext(parserSource + '\nthis.normalize = normalizeVoiceCommand; this.apply = applyStyleHints;', sandbox);

const cases = [
  ['сделай тёмную тему', 'dark'],
  ['сделай темную темку', 'dark'],
  ['премиум дизайн', 'luxury'],
  ['как windows 11', 'fluent'],
  ['покажи стиль shadсн', 'shadcn']
];
for (const [phrase, expected] of cases) {
  activeTheme = '';
  sandbox.apply(phrase);
  assert.equal(activeTheme, expected, 'voice theme: ' + phrase);
}

for (const [phrase, expected] of [
  ['сделай две колонки', 'repeat(2,minmax(0,1fr))'],
  ['два столбца', 'repeat(2,minmax(0,1fr))'],
  ['two columns', 'repeat(2,minmax(0,1fr))'],
  ['покажи две колонке', 'repeat(2,minmax(0,1fr))'],
  ['сделай три столбца', 'repeat(3,minmax(0,1fr))'],
  ['make grid of 3', 'repeat(3,minmax(0,1fr))'],
  ['сделай 2x2 сетку', 'repeat(2,minmax(0,1fr))']
]) {
  grid.style.gridTemplateColumns = '';
  sandbox.apply(phrase);
  assert.equal(grid.style.gridTemplateColumns, expected, 'voice grid: ' + phrase);
}

for (const theme of ['light','dark','luxury','ocean','aurora','minimal','material','fluent','shadcn']) {
  assert.ok(html.includes('data-theme="' + theme + '"'), 'theme button missing: ' + theme);
}
for (const required of ['function explainCode()', 'Объяснятор кода', 'data-template="card"', 'data-template="landing"', 'data-template="portfolio"', 'data-template="shop"']) {
  assert.ok(html.includes(required), 'builder feature missing: ' + required);
}
console.log('Visual builder: syntax OK; bilingual voice/theme/grid regression cases passed; 9 themes and explainer/templates present.');
