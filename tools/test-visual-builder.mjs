import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const html = fs.readFileSync('app/src/main/assets/visual_builder.html', 'utf8');
const activity = fs.readFileSync('app/src/main/java/org/ivanivorontsov/stroka/ui/VisualBuilderActivity.kt', 'utf8');
const localBuild = fs.readFileSync('app/src/main/java/org/ivanivorontsov/stroka/ui/LocalBuildEngine.kt', 'utf8');
const projectTemplates = fs.readFileSync('app/src/main/java/org/ivanivorontsov/stroka/core/Templates.kt', 'utf8');
const localBuildSource = fs.readFileSync('app/src/main/java/org/ivanivorontsov/stroka/ui/LocalBuildEngine.kt', 'utf8');
const ponyCompilerSource = fs.readFileSync('app/src/main/assets/ponyatny/yadro.js', 'utf8');
const ponyStandardLibrary = JSON.parse(fs.readFileSync('app/src/main/assets/ponyatny/std.json', 'utf8'));
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

for (const theme of ['light','dark','luxury','ocean','aurora','minimal','material','fluent','shadcn','antd','carbon']) {
  assert.ok(html.includes('data-theme="' + theme + '"'), 'theme button missing: ' + theme);
}
for (const required of ['function explainCode()', 'Объяснятор кода', 'data-template="card"', 'data-template="landing"', 'data-template="portfolio"', 'data-template="shop"']) {
  assert.ok(html.includes(required), 'builder feature missing: ' + required);
}
assert.ok(activity.includes('VoiceInput.Listener'), 'visual builder must reuse resilient voice input');
assert.ok(activity.includes('voice?.start(language, "auto")'), 'visual builder must enable automatic online/offline fallback');
assert.ok(activity.includes('voice?.isActive == true'), 'voice button must be able to stop listening');
assert.ok(activity.includes('voice?.release()'), 'voice engine resources must be released with the activity');
assert.ok(activity.includes('window.onVoiceStatus'), 'voice status must use a non-command callback');
assert.ok(!activity.includes('SpeechRecognizer.createSpeechRecognizer'), 'legacy single-engine recognizer must not return');
assert.ok(html.includes('window.onVoiceStatus='), 'builder must display listening status separately');
assert.ok(html.includes('if(styleOnly&&!contentIntent){update();return}'), 'style-only commands must not create accidental blocks');
assert.ok(html.includes('make (?:a )?website'), 'English website creation command must be supported');
assert.ok(html.includes("if(!stage.children.length){applyTemplate('landing');}"), 'new visual builder must start with a designed landing page');
assert.ok(html.includes('const premiumStyles='), 'exported page must include the premium responsive design system');
assert.ok(html.includes('site-feature-grid') && html.includes('site-final'), 'default site must contain designed content sections');
assert.ok(html.includes('собери красивый сайт'), 'Russian voice command must map to the full website template');
assert.ok(html.includes('class="item site-section" draggable="true"'), 'website sections must be independently selectable and draggable');
assert.ok(html.includes('class="item biz-section" draggable="true"'), 'business card sections must be independently selectable and draggable');
assert.ok(html.includes('class="item business-card" draggable="true"'), 'business card hero must be independently editable');
assert.ok(localBuild.includes('fun buildStaticWebsite') && localBuild.includes('MediaStore.Downloads'), 'static site build must create a real ZIP in Downloads');
assert.ok(localBuild.includes('fun buildPonyatnyWebsite') && localBuild.includes('ponyatny/yadro.js'), 'offline Ponyatny compiler must be embedded in website export');
assert.ok(projectTemplates.includes('адаптивный премиум-шаблон') && projectTemplates.includes('YOURSTUDIO'), 'new web projects must start from the premium template');

const appRunnerMatch = localBuildSource.match(/val appJs = """([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(appRunnerMatch, 'offline Ponyatny browser runner must be generated');
const ponyProgram = [
  'запиши_файл("data.txt", "сохранено")',
  'пиши прочитай_файл("data.txt")',
  'подключи "стд/математика.pon" как мат',
  'пиши "Факториал: " + строка(мат.факториал(5))'
].join('\n');
const appRunner = appRunnerMatch[1]
  .replace('${JSONObject.quote(source)}', JSON.stringify(ponyProgram))
  .replace('${JSONObject.quote(entryName)}', JSON.stringify('main.pon'))
  .replace('${modules}', '{}')
  .replace('$standard', JSON.stringify(ponyStandardLibrary));
new vm.Script(appRunner, { filename: 'LocalBuildEngine.kt app.js' });
const compilerSandbox = { module: { exports: {} } };
vm.createContext(compilerSandbox);
new vm.Script(ponyCompilerSource, { filename: 'ponyatny/yadro.js' }).runInContext(compilerSandbox);
const elements = {
  output: { textContent: '' }, source: { value: '' }, status: { textContent: '' },
  run: { addEventListener(name, callback) { this.callback = callback; } },
  save: { addEventListener() {} }
};
const stored = new Map();
const localStorageMock = { getItem(key) { return stored.get(key) || null; }, setItem(key, value) { stored.set(key, value); } };
const browserContext = {
  window: { PonyatnyYadro: compilerSandbox.module.exports, prompt: () => 'ответ' },
  document: { getElementById(id) { return elements[id]; } },
  localStorage: localStorageMock
};
vm.createContext(browserContext);
new vm.Script(appRunner, { filename: 'generated Ponyatny web app' }).runInContext(browserContext);
elements.run.callback();
assert.equal(elements.output.textContent, 'сохранено\nФакториал: 120\n', 'offline compiler must run standard modules and virtual file I/O');
assert.ok(stored.has('stroka-ponyatny-files-v1'), 'virtual file writes must persist in browser storage');
console.log('Visual builder: syntax OK; bilingual voice/theme/grid regression cases passed; 9 themes and explainer/templates present.');
