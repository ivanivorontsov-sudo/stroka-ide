#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const COMPILER_PATH = path.resolve(HERE, "../app/src/main/assets/ponyatny/yadro.js");
const STD_PATH = path.resolve(HERE, "../app/src/main/assets/ponyatny/std.json");
const compiler = await import("file://" + COMPILER_PATH);
const Pony = compiler.default || compiler.PonyatnyYadro || compiler;
const std = JSON.parse(fs.readFileSync(STD_PATH, "utf8"));

function parseArgs(argv) {
  const o = { source: null, target: "all", out: "dist", name: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--target") o.target = argv[++i];
    else if (a.startsWith("--target=")) o.target = a.slice(9);
    else if (a === "--out") o.out = argv[++i];
    else if (a.startsWith("--out=")) o.out = a.slice(6);
    else if (a === "--name") o.name = argv[++i];
    else if (a.startsWith("--name=")) o.name = a.slice(7);
    else if (a === "--help" || a === "-h") o.help = true;
    else if (!o.source) o.source = a;
    else throw new Error("Лишний аргумент: " + a);
  }
  return o;
}
function safeName(s) {
  return (s || "Программа").replace(/\.pon$/i, "").replace(/[^a-zA-Z0-9_-]+/g, "-").replace(/^-+|-+$/g, "") || "PonyatnyApp";
}
function resolveModule(root, request, importer) {
  const raw = String(request).replace(/\\/g, "/");
  const key = raw.replace(/^стд\//i, "").replace(/\.pon$/i, "");
  if (Object.prototype.hasOwnProperty.call(std, key)) return { id: "std:" + key, name: "стд/" + key + ".pon", source: std[key] };
  const base = raw.startsWith("./") || raw.startsWith("../") ? path.dirname(path.resolve(root, importer || "main.pon")) : root;
  for (const candidate of [path.resolve(base, raw), path.resolve(base, raw.endsWith(".pon") ? raw : raw + ".pon")]) {
    const rel = path.relative(root, candidate);
    if (rel.startsWith("..") || path.isAbsolute(rel)) continue;
    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) return { id: candidate, name: rel.replace(/\\/g, "/"), source: fs.readFileSync(candidate, "utf8") };
  }
  return null;
}
function compileSource(file) {
  const absolute = path.resolve(file);
  const root = path.dirname(absolute);
  const source = fs.readFileSync(absolute, "utf8");
  const js = Pony.compile(source, { file: path.basename(absolute), guard: true, resolve: (request, importer) => resolveModule(root, request, importer) });
  return { js, source };
}
function lines(s) { return s.split("\n"); }
function writeWeb(outDir, source, name) {
  const dir = path.join(outDir, "web");
  fs.mkdirSync(dir, { recursive: true });
  fs.copyFileSync(COMPILER_PATH, path.join(dir, "ponyatny.js"));
  fs.writeFileSync(path.join(dir, "std.json"), JSON.stringify(std), "utf8");
  fs.writeFileSync(path.join(dir, "program.pon"), source, "utf8");
  const app = [
    "(() => {",
    "const out=document.getElementById('output'), src=document.getElementById('source'), status=document.getElementById('status');",
    "const modules=" + JSON.stringify(std) + ";",
    "const write=v=>{out.textContent+=String(v)+'\\n';};",
    "document.getElementById('run').addEventListener('click',()=>{out.textContent='';try{PonyatnyYadro.run(src.value,{file:'main.pon',write,writeErr:v=>write('Ошибка: '+v),ask:q=>window.prompt(q||'Введите значение:')??'',resolve:req=>{const k=String(req).replace(/^стд\\//,'').replace(/\\.pon$/i,'');return Object.prototype.hasOwnProperty.call(modules,k)?{id:'std:'+k,name:'стд/'+k+'.pon',source:modules[k]}:null;}});status.textContent='Выполнено';}catch(e){status.textContent='Ошибка';write(e&&e.message?e.message:e);}});",
    "document.getElementById('save').addEventListener('click',()=>{const a=document.createElement('a'),u=URL.createObjectURL(new Blob([src.value],{type:'text/plain;charset=utf-8'}));a.href=u;a.download='main.pon';a.click();URL.revokeObjectURL(u);});",
    "})();"
  ].join("\n");
  fs.writeFileSync(path.join(dir, "app.js"), app, "utf8");
  const html = [
    "<!doctype html><html lang=\"ru\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">",
    "<title>" + name.replace(/[<>&"]/g, "") + " — Понятный</title>",
    "<style>:root{color-scheme:dark;font:16px system-ui;background:#111827;color:#f9fafb}body{max-width:960px;margin:auto;padding:20px}textarea{box-sizing:border-box;width:100%;min-height:48vh;background:#0b1220;color:#e5e7eb;border:1px solid #475569;border-radius:12px;padding:14px;font:15px/1.6 monospace}button{border:0;border-radius:10px;padding:12px 18px;margin:10px 8px 10px 0;font-weight:700;background:#60a5fa;color:#08111f}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#030712;border:1px solid #374151;border-radius:12px;padding:14px;min-height:5em}#status{color:#93c5fd}</style>",
    "<body><h1>" + name.replace(/[<>&"]/g, "") + "</h1><p>Программа на языке «Понятный». Исходный код можно изменить и запустить прямо здесь.</p>",
    "<textarea id=\"source\" spellcheck=\"false\"></textarea><br><button id=\"run\">Запустить</button><button id=\"save\">Сохранить .pon</button><span id=\"status\">Готово</span><h2>Результат</h2><pre id=\"output\"></pre>",
    "<script src=\"ponyatny.js\"></script><script src=\"app.js\"></script></body></html>"
  ].join("\n");
  fs.writeFileSync(path.join(dir, "index.html"), html, "utf8");
  return dir;
}
function writeAndroid(outDir, webDir, name) {
  const root = path.join(outDir, "android-project"), app = path.join(root, "app");
  const java = path.join(app, "src/main/java/dev/ponyatny/app"), assets = path.join(app, "src/main/assets");
  fs.mkdirSync(java, { recursive: true }); fs.mkdirSync(assets, { recursive: true });
  fs.cpSync(webDir, assets, { recursive: true });
  fs.writeFileSync(path.join(root, "settings.gradle"), 'pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name = "PonyatnyProgram"\ninclude(":app")\n');
  fs.writeFileSync(path.join(root, "build.gradle"), 'plugins { id "com.android.application" version "8.7.3" apply false }\n');
  fs.writeFileSync(path.join(root, "gradle.properties"), "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n");
  fs.writeFileSync(path.join(app, "build.gradle"), 'plugins { id "com.android.application" }\nandroid { namespace "dev.ponyatny.app"; compileSdk 35; defaultConfig { applicationId "dev.ponyatny.app"; minSdk 23; targetSdk 35; versionCode 1; versionName "1.0" } }\n');
  fs.writeFileSync(path.join(app, "src/main/AndroidManifest.xml"), '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="' + name.replace(/[<>&"]/g, "") + '" android:theme="@android:style/Theme.Material.Light.NoActionBar"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>\n');
  fs.writeFileSync(path.join(java, "MainActivity.java"), 'package dev.ponyatny.app;\nimport android.app.Activity;\nimport android.os.Bundle;\nimport android.webkit.WebChromeClient;\nimport android.webkit.WebView;\nimport android.webkit.WebSettings;\npublic final class MainActivity extends Activity { @Override public void onCreate(Bundle state) { super.onCreate(state); WebView web = new WebView(this); WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); web.setWebChromeClient(new WebChromeClient()); setContentView(web); web.loadUrl("file:///android_asset/index.html"); } }\n');
  return root;
}
function run(command, args) {
  const r = spawnSync(command, args, { stdio: "inherit" });
  if (r.error) throw r.error;
  if (r.status !== 0) throw new Error(command + " завершился с кодом " + r.status);
}
function main() {
  const o = parseArgs(process.argv.slice(2));
  if (o.help || !o.source) {
    console.log("Использование: node tools/build-ponyatny.mjs <main.pon> [--target all|node|web|android|windows-x64|linux-x64|linux-arm64|macos-x64|macos-arm64] [--out dist]");
    process.exit(o.help ? 0 : 2);
  }
  if (!/\.pon$/i.test(o.source)) throw new Error("Ожидается исходный файл .pon");
  const { js, source } = compileSource(o.source);
  const out = path.resolve(o.out); fs.mkdirSync(out, { recursive: true });
  const name = o.name || safeName(path.basename(o.source));
  const standalone = Pony.standalone(js);
  if (o.target === "all" || o.target === "node") {
    const dir = path.join(out, "node"); fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, name + ".js"), standalone, "utf8");
    fs.writeFileSync(path.join(dir, "README.txt"), "Запуск: node " + name + ".js\nТребуется Node.js 18 или новее.\n", "utf8");
  }
  if (o.target === "all" || o.target === "web") writeWeb(out, source, name);
  if (o.target === "all" || o.target === "android") {
    const webDir = path.join(out, "web");
    if (!fs.existsSync(path.join(webDir, "index.html"))) writeWeb(out, source, name);
    writeAndroid(out, webDir, name);
  }
  const targets = { "windows-x64": ["bun-windows-x64", ".exe"], "linux-x64": ["bun-linux-x64", ""], "linux-arm64": ["bun-linux-arm64", ""], "macos-x64": ["bun-darwin-x64", ""], "macos-arm64": ["bun-darwin-arm64", ""] };
  if (targets[o.target]) {
    const dir = path.join(out, o.target); fs.mkdirSync(dir, { recursive: true });
    const tmp = path.join(out, "ponyatny-standalone.js"); fs.writeFileSync(tmp, standalone, "utf8");
    try { run("bun", ["build", "--compile", "--target=" + targets[o.target][0], tmp, "--outfile", path.join(dir, name + targets[o.target][1])]); }
    finally { fs.rmSync(tmp, { force: true }); }
  } else if (!["all", "node", "web", "android"].includes(o.target)) throw new Error("Неизвестная цель: " + o.target);
  if (o.target === "all") console.log("Подготовлены Node.js, Web и Android-проект. Нативные Windows/Linux/macOS файлы собираются Bun-целями отдельно.");
  console.log("Готово: " + out);
}
try { main(); } catch (e) { console.error("Ошибка сборки Понятного: " + e.message); process.exit(1); }
