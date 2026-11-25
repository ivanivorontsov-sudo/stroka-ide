#!/usr/bin/env node
// Прогоняет скрипты AiWebChat (app/build/webchat-scripts, их выгружает WebChatScriptsTest) в Chrome
// так же, как AiChatActivity: inject → send → poll до стабильного ответа. Аргументы: [chrome] [url живого сайта] [провайдер]
const path = require("path"), fs = require("fs");
const puppeteer = require(process.env.PUPPETEER || "puppeteer-core");
const S = path.join(__dirname, "../../app/build/webchat-scripts");
const js = (n) => fs.readFileSync(path.join(S, n), "utf8");
const chrome = process.argv[2] || "/usr/bin/google-chrome";

async function flow(page, provider) {
  const ins = JSON.parse(await page.evaluate(js(`inject-${provider}.js`)));
  if (ins.status !== "OK") return { ok: false, step: "inject", ins };
  const sent = await page.evaluate(js("send.js"));
  if (sent === "NO_COMPOSER") return { ok: false, step: "send" };
  let answer = "", last = "", stable = Date.now(); const t0 = Date.now();
  while (Date.now() - t0 < 30000) {
    await new Promise(r => setTimeout(r, 300));
    const p = JSON.parse(await page.evaluate(js(`poll-${provider}.js`)));
    if (p.login) return { ok: false, step: "login" };
    const cur = p.fresh ? p.text : "";
    if (cur && cur !== last) { last = cur; answer = cur; stable = Date.now(); }
    if (answer && !p.busy && Date.now() - stable > 1200) break;
  }
  return { ok: !!answer, sent, control: ins.control, answer };
}

(async () => {
  const b = await puppeteer.launch({ executablePath: chrome, headless: true, args: ["--no-sandbox"] });
  let fails = 0;
  for (const [file, provider, expect] of [["deepseek.html", "deepseek", "Калькулятор DeepSeek"], ["qwen.html", "qwen", "calc.pon"], ["chatgpt.html", "chatgpt", "Калькулятор ChatGPT"]]) {
    const page = await b.newPage();
    await page.goto("file://" + path.join(__dirname, "webchat-mocks", file));
    const r = await flow(page, provider);
    const good = r.ok && r.answer.includes(expect) && !r.answer.includes("Сделай калькулятор");
    if (!good) fails++;
    console.log((good ? "OK  " : "FAIL") + " " + provider + ": " + JSON.stringify(r).slice(0, 300));
    await page.close();
  }
  const page = await b.newPage();
  await page.goto("file://" + path.join(__dirname, "webchat-mocks", "login.html"));
  const ins = JSON.parse(await page.evaluate(js("inject-deepseek.js")));
  const poll = JSON.parse(await page.evaluate(js("poll-deepseek.js")));
  const loginOk = ins.status === "NO_COMPOSER" && poll.login;
  if (!loginOk) fails++;
  console.log((loginOk ? "OK  " : "FAIL") + " страница входа распознана: " + JSON.stringify({ ins: ins.status, login: poll.login }));
  const cp = await b.newPage();
  await cp.goto("file://" + path.join(__dirname, "webchat-mocks", "captcha.html"));
  const cpoll = JSON.parse(await cp.evaluate(js("poll-duck.js")));
  if (!cpoll.captcha) fails++;
  console.log((cpoll.captcha ? "OK  " : "FAIL") + " проверка «я не робот» распознана: " + cpoll.captcha);
  await b.close();
  process.exit(fails ? 1 : 0);
})();
