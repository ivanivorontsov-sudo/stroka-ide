(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.PonyatnyYadro = factory();
})(typeof self !== "undefined" ? self : this, function () {
  const RESERVED = ["пусть", "константа", "навсегда", "напиши", "если", "то", "иначе", "пока", "повторить", "для", "функция", "верни", "конец", "и", "или", "не", "истина", "ложь", "ничего", "стоп", "дальше", "попробуй", "брось", "выбор", "вариант", "когда", "класс", "структура", "это", "себя", "родитель", "новый", "подключи", "в", "затем"];
  const CONTEXT = ["каждого", "из", "от", "до", "шаг", "раз", "ошибка", "как", "наконец", "наследует"];
  const ALIASES = { "пиши": "напиши", "повтори": "повторить", "пробуй": "попробуй", "конст": "константа" };
  const KW = new Set(RESERVED);
  const normWord = (w) => String(w).toLowerCase().replace(/ё/g, "е");
  const STMT_WORDS_NEW = {
    "переменная": "пусть", "пускай": "пусть", "создай": "пусть",
    "постоянная": "константа", "конста": "константа",
    "напечатай": "напиши", "печатай": "напиши", "печать": "напиши", "напечатать": "напиши", "выведи": "напиши", "выводи": "напиши", "вывести": "напиши", "вывод": "напиши", "написать": "напиши", "покажи": "напиши", "скажи": "напиши",
    "повторяй": "повторить",
    "вернуть": "верни", "возврати": "верни", "возврат": "верни", "возвращай": "верни",
    "бросить": "брось", "выбрось": "брось", "кинь": "брось", "паника": "брось", "караул": "брось", "аларм": "брось", "капец": "брось",
    "ежели": "если", "коли": "если",
    "покуда": "пока",
    "процедура": "функция", "подпрограмма": "функция", "функц": "функция",
    "прервать": "стоп", "прервись": "стоп", "хватит": "стоп", "харэ": "стоп", "баста": "стоп",
    "продолжай": "дальше", "далее": "дальше", "следующий": "дальше", "проехали": "дальше", "пропусти": "дальше",
    "выбери": "выбор",
    "подключить": "подключи", "используй": "подключи", "импорт": "подключи",
    "попробовать": "попробуй", "спасите": "попробуй", "авось": "попробуй", "рискнем": "попробуй", "рискну": "попробуй",
    "предупреди": "предупреди", "кричи": "предупреди", "крикни": "предупреди", "ори": "предупреди", "жалуйся": "предупреди", "ворчи": "предупреди",
    "отладка": "отладка", "шепни": "отладка", "шепчи": "отладка", "шепотом": "отладка", "отладь": "отладка",
    "делай": "делай", "выполняй": "делай", "крути": "делай",
    "перечисление": "перечисление", "перечень": "перечисление",
    "интерфейс": "интерфейс", "договор": "интерфейс",
    "ленивая": "ленивая", "ленивый": "ленивая", "ленивое": "ленивая", "лениво": "ленивая",
    "глобальная": "глобальная", "глобальный": "глобальная", "глобальное": "глобальная",
    "убедись": "убедись", "утверждаю": "убедись", "клянусь": "убедись",
    "аминь": "конец", "всё": "конец", "кончено": "конец",
    "метка": "метка", "ярлык": "метка",
    "иди": "иди", "перейди": "иди", "перейти": "иди", "прыгни": "иди", "goto": "иди",
    "поток": "поток", "нить": "поток", "параллельно": "поток",
    "асинхронная": "асинхронная", "асинхронный": "асинхронная", "асинхронно": "асинхронная",
    "с": "с", "используя": "с", "взяв": "с",
    "из": "из",
  };
  const NO_ARG_WORDS = new Set(["стоп", "дальше", "попробуй", "делай", "конец", "верни"]);
  const PHRASES = [
    ["кричи на систему", ["id:предупреди"]], ["ори на систему", ["id:предупреди"]], ["наори на систему", ["id:предупреди"]], ["пожалуйся системе", ["id:предупреди"]],
    ["шепни системе", ["id:отладка"]], ["скажи по секрету", ["id:отладка"]],
    ["ну всё", ["id:выход", "op:(", "op:)"]], ["ну все", ["id:выход", "op:(", "op:)"]], ["я устал я ухожу", ["id:выход", "op:(", "op:)"]], ["занавес", ["id:выход", "op:(", "op:)"]],
    ["считай до трёх", ["id:пауза", "op:(", "num:3000", "op:)"]], ["барабанная дробь", ["id:пауза", "op:(", "num:1000", "op:)"]], ["дай подумать", ["id:пауза", "op:(", "num:1000", "op:)"]], ["минуточку", ["id:пауза", "op:(", "num:1000", "op:)"]],
    ["зуб даю", ["id:убедись"]], ["мамой клянусь", ["id:убедись"]], ["честное слово", ["id:убедись"]],
    ["на случай беды", ["kw:если", "id:ошибка"]], ["если всё сломалось", ["kw:если", "id:ошибка"]],
    ["в любом случае", ["id:наконец"]], ["и в конце концов", ["id:наконец"]],
    ["до посинения", ["kw:пока", "kw:истина"]], ["пока не надоест", ["kw:пока", "kw:истина"]],
    ["на этом всё", ["kw:конец"]], ["вот и сказочке конец", ["kw:конец"]],
    ["выведи на экран", ["kw:напиши"]], ["напиши на экран", ["kw:напиши"]], ["скажи миру", ["kw:напиши"]],
    ["иди к", ["id:иди"]], ["перейди к", ["id:иди"]], ["перейти к", ["id:иди"]], ["прыгни к", ["id:иди"]], ["беги к", ["id:иди"]], ["телепортируйся к", ["id:иди"]],
    ["в фоне", ["id:поток"]],
  ].map(([w, t]) => [w.split(" ").map(normWord), t]).sort((a, b) => b[0].length - a[0].length);
  const WORD_FORMS = { "раз": ["раза"], "каждого": ["каждый", "каждой", "каждое", "каждую", "каждому"], "до": ["по"], "от": ["с"], "шаг": ["шагом"], "из": ["в", "среди"] };
  const CLASS_MODS = new Map([["общая", "static"], ["общий", "static"], ["общее", "static"], ["статическая", "static"], ["статический", "static"], ["статичная", "static"], ["скрытая", "private"], ["скрытый", "private"], ["скрытое", "private"], ["приватная", "private"], ["приватный", "private"], ["закрытая", "private"], ["закрытый", "private"], ["абстрактная", "abstract"], ["абстрактный", "abstract"]]);
  const STMT_ALIAS = new Map(Object.entries(STMT_WORDS_NEW).map(([k, v]) => [normWord(k), v]));
  const ALL_WORDS = RESERVED.concat(CONTEXT);

  class CompileError extends Error {
    constructor(message, line, col, file) {
      super((file ? "Файл «" + file + "», " : "") + (line ? (file ? "строка " : "Строка ") + line + (col ? ", столбец " + col : "") + ": " : "") + message);
      this.pureMessage = message;
      this.line = line;
      this.col = col;
      this.file = file;
    }
  }

  function levenshtein(a, b) {
    const m = a.length, n = b.length;
    const d = [];
    for (let i = 0; i <= m; i++) d.push([i]);
    for (let j = 1; j <= n; j++) d[0][j] = j;
    for (let i = 1; i <= m; i++) for (let j = 1; j <= n; j++) {
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
    }
    return d[m][n];
  }

  const LATIN_LOOKALIKE = { a: "а", e: "е", o: "о", p: "р", c: "с", x: "х", y: "у", k: "к", m: "м", t: "т", b: "в", h: "н", A: "А", E: "Е", O: "О", P: "Р", C: "С", X: "Х", Y: "У", K: "К", M: "М", T: "Т", B: "В", H: "Н" };
  const MAX_SUGGEST_LEN = 40;
  function nearest(word, candidates, limit) {
    if (word.length > MAX_SUGGEST_LEN) return { list: [], d: Infinity };
    const w = word.toLowerCase().replace(/[a-z]/g, (c) => LATIN_LOOKALIKE[c] || c);
    const lim = limit !== undefined ? limit : word.length <= 3 ? 1 : 2;
    let list = [], bestD = Infinity;
    for (const c of new Set(candidates)) {
      if (c === word || Math.abs(c.length - w.length) > lim) continue;
      const dd = levenshtein(w, c.toLowerCase());
      if (dd > lim) continue;
      if (dd < bestD) { list = [c]; bestD = dd; } else if (dd === bestD) list.push(c);
    }
    return { list, d: bestD };
  }
  function suggest(word, candidates) {
    const { list } = nearest(word, candidates);
    if (!list.length) return "";
    return " Возможно, вы имели в виду " + list.slice(0, 3).map((x) => "«" + x + "»").join(" или ") + "?";
  }
  function autoKeyword(word, candidates) {
    if (word.length < 3) return null;
    const { list, d } = nearest(word, candidates, 1);
    return list.length === 1 && d <= 1 ? list[0] : null;
  }

  const BIDI = /[\u202A-\u202E\u2066-\u2069\u200E\u200F\u061C]/;
  const INVISIBLE = /[\u200B-\u200D\u2060\u00AD\u180E\uFEFF]/;
  const hex4 = (ch) => "U+" + ch.codePointAt(0).toString(16).toUpperCase().padStart(4, "0");
  const LETTER = /[\p{Script=Cyrillic}\p{Script=Latin}]/u;
  const IS_CYR = /\p{Script=Cyrillic}/u;
  const IS_LAT = /\p{Script=Latin}/u;
  const MAX_IDENT = 255;
  const MAX_DEPTH = 200;
  function mixedScript(name) {
    for (const part of name.split("_")) if (IS_CYR.test(part) && IS_LAT.test(part)) return part;
    return null;
  }
  const toCyr = (s) => s.replace(/[A-Za-z]/g, (c) => LATIN_LOOKALIKE[c] || c);

  const TRANSLIT = { а: "a", б: "b", в: "v", г: "g", д: "d", е: "e", ё: "yo", ж: "zh", з: "z", и: "i", й: "y", к: "k", л: "l", м: "m", н: "n", о: "o", п: "p", р: "r", с: "s", т: "t", у: "u", ф: "f", х: "kh", ц: "ts", ч: "ch", ш: "sh", щ: "shch", ъ: "", ы: "y", ь: "", э: "e", ю: "yu", я: "ya", і: "i", ї: "yi", є: "ye", ґ: "g", ў: "u" };
  function transliterate(name) {
    let out = "";
    for (const c of name) {
      const l = c.toLowerCase();
      if (Object.prototype.hasOwnProperty.call(TRANSLIT, l)) { const t = TRANSLIT[l]; out += c !== l && t ? t[0].toUpperCase() + t.slice(1) : t; }
      else if (/[A-Za-z0-9_]/.test(c)) out += c;
      else out += "_u" + c.codePointAt(0).toString(16) + "_";
    }
    if (!out) out = "_";
    if (/^[0-9]/.test(out)) out = "_" + out;
    return out;
  }
  function makeRenamer(reserved, convert, nfkc) {
    const fwd = new Map(), used = new Set();
    const key = (x) => (nfkc && x.normalize ? x.normalize("NFKC") : x);
    const fn = (n) => {
      if (fwd.has(n)) return fwd.get(n);
      const base = convert ? convert(n) : n;
      let c = base, k = 1;
      while (reserved.has(c) || used.has(key(c))) c = k === 1 ? base + "_" : base + "_" + k, k++;
      fwd.set(n, c);
      used.add(key(c));
      return c;
    };
    fn.map = fwd;
    return fn;
  }

  function tokenize(src, file, baseLine, baseCol, warn) {
    if (typeof src !== "string") src = String(src);
    if (src.normalize) src = src.normalize("NFC");
    if (!baseLine) {
      let ln = 1, ls = 0;
      for (let k = 0; k < src.length; k++) {
        const ch = src[k];
        if (ch === "\n") { ln++; ls = k + 1; continue; }
        const bidi = BIDI.test(ch);
        if (bidi || (INVISIBLE.test(ch) && !(ch === "\uFEFF" && k === 0)))
          throw new CompileError((bidi ? "невидимый символ направления текста " + hex4(ch) + ": из-за него код может выглядеть не так, как работает (атака Trojan Source, CVE-2021-42574)" : "невидимый символ " + hex4(ch)) + ". Удалите его; если он действительно нужен внутри строки, напишите \\u{" + ch.codePointAt(0).toString(16).toUpperCase() + "}", ln, k - ls + 1, file);
      }
    }
    const tokens = [];
    let i = 0, line = baseLine || 1, lineStart = 0;
    const firstLine = line;
    const colOf = (k) => (line === firstLine && baseCol ? baseCol - 1 : 0) + k - lineStart + 1;
    const err = (msg, k) => { throw new CompileError(msg, line, colOf(k === undefined ? i : k), file); };
    const isLetter = (c) => c === "_" || LETTER.test(c);
    const isDigit = (c) => c >= "0" && c <= "9";
    const push = (t, v, k, extra) => tokens.push(Object.assign({ t, v, line, col: colOf(k), file }, extra || {}));
    while (i < src.length) {
      const c = src[i];
      const start = i;
      if (BIDI.test(c)) err("в программе есть невидимый символ направления текста " + hex4(c) + ". Из-за него код может выглядеть не так, как работает (атака Trojan Source, CVE-2021-42574). Удалите его; если он нужен внутри строки, напишите \\u{" + c.codePointAt(0).toString(16).toUpperCase() + "}");
      if (INVISIBLE.test(c) && !(c === "\uFEFF" && i === 0)) err("в программе есть невидимый символ " + hex4(c) + ". Удалите его; если он нужен внутри строки, напишите \\u{" + c.codePointAt(0).toString(16).toUpperCase() + "}");
      if (c === "\n") { push("nl", undefined, i); line++; i++; lineStart = i; continue; }
      if (c === " " || c === "\t" || c === "\r" || c === "\uFEFF") { i++; continue; }
      if (c === "#" || (c === "/" && src[i + 1] === "/")) { while (i < src.length && src[i] !== "\n") i++; continue; }
      if (c === '"' || c === "«") {
        const close = c === '"' ? '"' : "»";
        const parts = [];
        let s = "";
        let depthQ = 0;
        i++;
        while (i < src.length && !(src[i] === close && depthQ === 0)) {
          const ch = src[i];
          if (ch === "\n") err("строка не закрыта кавычкой", start);
          if (c === "«" && ch === "«") depthQ++;
          if (c === "«" && ch === "»") depthQ--;
          if (BIDI.test(ch) || INVISIBLE.test(ch)) err("внутри строки невидимый символ " + hex4(ch) + (BIDI.test(ch) ? " (направление текста, атака Trojan Source, CVE-2021-42574)" : "") + ". Если он действительно нужен, напишите \\u{" + ch.codePointAt(0).toString(16).toUpperCase() + "}", i);
          if (ch === "\\" && src[i + 1] === "u") {
            const m = /^\\u\{([0-9A-Fa-f]{1,6})\}/.exec(src.slice(i, i + 10));
            const cp = m ? parseInt(m[1], 16) : -1;
            if (!m || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) err("после «\\u» нужен код символа в фигурных скобках, например \\u{263A}", i);
            s += String.fromCodePoint(cp);
            i += m[0].length;
            continue;
          }
          if (ch === "\\") {
            const e = src[i + 1];
            const map = { n: "\n", t: "\t", r: "\r", '"': '"', "\\": "\\", "{": "{", "}": "}", "«": "«", "»": "»" };
            if (!Object.prototype.hasOwnProperty.call(map, e)) err("непонятная последовательность «\\" + (e || "") + "» в строке", i);
            s += map[e];
            i += 2;
            continue;
          }
          if (ch === "{") {
            let depth = 1, j = i + 1, inner = "";
            while (j < src.length && src[j] !== "\n") {
              if (src[j] === "{") depth++;
              if (src[j] === "}") { depth--; if (depth === 0) break; }
              inner += src[j++];
            }
            if (depth !== 0) err("в строке не закрыта фигурная скобка «{». Если нужна сама скобка, пиши \\{", i);
            if (!inner.trim()) err("пустая вставка «{}» в строке", i);
            if (s) parts.push(s);
            s = "";
            parts.push({ src: inner, line, col: colOf(i + 1) });
            i = j + 1;
            continue;
          }
          s += ch;
          i++;
        }
        if (i >= src.length) err("строка не закрыта кавычкой", start);
        i++;
        if (parts.length === 0) push("str", s, start);
        else { if (s) parts.push(s); push("tmpl", undefined, start, { parts }); }
        continue;
      }
      if (isDigit(c)) {
        let s = "";
        while (i < src.length && (isDigit(src[i]) || src[i] === "_")) { if (src[i] !== "_") s += src[i]; i++; }
        if (src[i] === "." && isDigit(src[i + 1] || "")) { s += "."; i++; while (i < src.length && isDigit(src[i])) s += src[i++]; }
        if (isLetter(src[i] || "")) err("число не может сразу переходить в букву", i);
        push("num", parseFloat(s), start, { raw: s });
        continue;
      }
      if (isLetter(c)) {
        let s = "";
        while (i < src.length && (isLetter(src[i]) || isDigit(src[i]))) s += src[i++];
        const low = s.toLowerCase();
        if (s.length > MAX_IDENT) err("слишком длинное имя (больше " + MAX_IDENT + " символов)", start);
        const isKwForm = (x) => { const l = x.toLowerCase(); return KW.has(l) && (x === l || (x.length > 1 && x === l[0].toUpperCase() + l.slice(1))); };
        if (isKwForm(s)) { push("kw", s.toLowerCase(), start); continue; }
        if (Object.prototype.hasOwnProperty.call(ALIASES, low) && (s === low || s === low[0].toUpperCase() + low.slice(1))) { push("kw", ALIASES[low], start); continue; }
        if (s.normalize && s.normalize("NFKC") !== s) err("в имени «" + s + "» есть особая (совместимая) форма буквы. Возможно, вы имели в виду «" + s.normalize("NFKC") + "»?", start);
        const mixed = mixedScript(s);
        if (mixed) {
          const fixed = toCyr(s);
          if (isKwForm(fixed) && !mixedScript(fixed)) {
            if (warn) warn({ line, col: colOf(start), file, text: "в слове «" + s + "» есть латинские буквы, похожие на русские; понято как «" + fixed.toLowerCase() + "»" });
            push("kw", fixed.toLowerCase(), start);
            continue;
          }
          err("в имени «" + s + "» смешаны русские и латинские буквы («" + mixed + "»). Такие имена выглядят одинаково, но это разные имена. " + (!mixedScript(fixed) ? "Возможно, вы имели в виду «" + fixed + "»?" : "Пишите имя одним алфавитом или разделяйте части знаком «_»."), start);
        }
        push("id", s, start);
        continue;
      }
      if (src.substr(i, 3) === "?.[") { push("op", "?.[", i); i += 3; continue; }
      const two = src.substr(i, 2);
      if (["==", "!=", "<=", ">=", "=>", "+=", "-=", "*=", "/=", "%=", "|>", "?.", "->", ":=", "::"].includes(two)) { push("op", two, i); i += 2; continue; }
      if ("+-*/%^()<>=,[]{}.:&".includes(c)) { push("op", c, i); i++; continue; }
      const uni = { "≠": "!=", "≤": "<=", "≥": ">=", "×": "*", "÷": "/" };
      if (uni[c]) { push("op", uni[c], i); i++; continue; }
      const cp = src.codePointAt(i);
      err("непонятный символ «" + String.fromCodePoint(cp) + "» (" + hex4(String.fromCodePoint(cp)) + ")" + (c === "'" ? ". Строки пишутся в двойных кавычках \"…\" или «ёлочках»" : c === ";" ? ". Точка с запятой не нужна: каждая команда пишется с новой строки" : ""));
    }
    push("nl", undefined, i);
    push("eof", undefined, i);
    return tokens;
  }

  function parse(src, file, warn) {
    return parseTokens(tokenize(src, file, 0, 0, warn), file, false, warn, 0);
  }

  function parseTokens(tokens, file, subExpr, warn, depth0) {
    let p = 0;
    let depth = depth0 || 0;
    const fixW = (tok, word) => { if (warn) warn({ line: tok.line, col: tok.col, file: tok.file || file, text: "опечатка «" + tok.v + "» исправлена на «" + word + "»" }); tok.t = "kw"; tok.v = word; tok.fixed = true; };
    const peek = (o) => tokens[Math.min(p + (o || 0), tokens.length - 1)];
    const next = () => tokens[p++];
    const is = (t, v) => peek().t === t && (v === undefined || peek().v === v);
    const isW = (v, o) => { const k = peek(o); if (k.t !== "kw" && k.t !== "id") return false; return k.v.toLowerCase() === v || (Object.prototype.hasOwnProperty.call(WORD_FORMS, v) && WORD_FORMS[v].includes(normWord(k.v))); };
    const tokNorm = (k) => (k && (k.t === "id" || k.t === "kw") ? normWord(k.v) : null);
    function applyAliases() {
      const st = peek();
      if ((st.t !== "id" && st.t !== "kw") || st.aliased) return;
      for (const [words, repl] of PHRASES) {
        let j = p, ok = true;
        for (let w = 0; w < words.length; w++) {
          if (w > 0) while (tokens[j] && tokens[j].t === "op" && tokens[j].v === ",") j++;
          if (tokNorm(tokens[j]) !== words[w]) { ok = false; break; }
          j++;
        }
        if (!ok) continue;
        const nt = repl.map((r) => { const c = r.indexOf(":"); const t = r.slice(0, c), v = r.slice(c + 1); return { t, v: t === "num" ? Number(v) : v, line: st.line, col: st.col, file: st.file, aliased: true }; });
        tokens.splice(p, j - p, ...nt);
        return;
      }
      if (st.t !== "id") return;
      const canon = STMT_ALIAS.get(normWord(st.v));
      if (!canon) return;
      const nx = peek(1);
      const endOk = nx.t === "nl" || nx.t === "eof";
      if (nx.t === "op" && nx.v !== "-") return;
      if (endOk && !NO_ARG_WORDS.has(canon)) return;
      if (canon === "конец" && !endOk) return;
      if (KW.has(canon)) st.t = "kw";
      st.v = canon;
      st.aliased = true;
    }
    const isNew = (w) => is("id") && peek().aliased && peek().v === w;
    const accept = (t, v) => (is(t, v) ? next() : null);
    const acceptW = (v) => (isW(v) ? next() : null);
    const describe = (tok) => tok.t === "nl" ? "конец строки" : tok.t === "eof" ? "конец программы" : tok.t === "str" || tok.t === "tmpl" ? "строка" : "«" + tok.v + "»";
    const fail = (msg, tok) => { tok = tok || peek(); throw new CompileError(msg, tok.line, tok.col, tok.file || file); };
    const expect = (t, v, what) => {
      if (is(t, v)) return next();
      fail("ожидалось " + (what || "«" + v + "»") + ", а встретилось " + describe(peek()));
    };
    const expectW = (v) => { if (isW(v)) return next(); if (is("id") && autoKeyword(peek().v, [v]) === v) { fixW(peek(), v); return next(); } fail("ожидалось слово «" + v + "», а встретилось " + describe(peek()) + (peek().t === "id" ? suggest(peek().v, [v]) : "")); };
    const expectName = (what) => {
      if (is("id")) return next();
      if (is("kw")) fail("«" + peek().v + "» — служебное слово, его нельзя использовать как " + what);
      fail("ожидалось " + what + ", а встретилось " + describe(peek()));
    };
    const expectBindName = (what) => {
      if (is("id")) return next();
      if (is("kw", "в")) return next();
      return expectName(what);
    };
    const skipNl = () => { while (is("nl")) next(); };
    const endLine = () => { if (!is("eof") && !accept("nl")) fail("ожидался конец строки, а встретилось " + describe(peek()) + ". Каждая команда пишется на отдельной строке"); };
    const pos = (tok) => ({ line: tok.line, col: tok.col, file: tok.file || file });

    function block(enders, opener) {
      const body = [];
      skipNl();
      for (;;) {
        applyAliases();
        if (is("id") && (peek(1).t === "nl" || peek(1).t === "eof") && !peek().aliased) {
          const fx = autoKeyword(peek().v, enders.filter((e) => e !== "если ошибка"));
          if (fx) fixW(peek(), fx);
        }
        if (enders.some((e) => (e === "если ошибка" ? isW("если") && isW("ошибка", 1) : e === "если ошибка тип" ? isW("если") && isW("ошибка", 1) && (peek(2).t === "id" || isW("как", 2)) : isW(e)))) break;
        if (is("eof")) fail("не хватает слова «конец» для «" + opener.v + "» из строки " + opener.line, opener);
        body.push(statement());
        skipNl();
      }
      return body;
    }
    const closeEnd = () => { expectW("конец"); endLine(); };

    function typeName() {
      const t = peek();
      if (is("id") || is("kw", "ничего")) { next(); return t.v; }
      fail("ожидалось название типа, а встретилось " + describe(t));
    }
    function typeAnn() { return accept("op", ":") ? typeName() : null; }

    function params() {
      const ps = [];
      expect("op", "(");
      skipNl();
      let seenDefault = false;
      if (!is("op", ")")) {
        for (;;) {
          const t = expectName("имя параметра");
          const type = typeAnn();
          let def = null;
          if (accept("op", "=")) { def = expr(); seenDefault = true; }
          else if (seenDefault) fail("параметр «" + t.v + "» без значения по умолчанию не может идти после параметров со значением", t);
          if (ps.some((q) => q.name === t.v)) fail("параметр «" + t.v + "» повторяется", t);
          ps.push({ name: t.v, type, def, ...pos(t) });
          skipNl();
          if (!accept("op", ",")) break;
          skipNl();
        }
      }
      expect("op", ")", "«)»");
      return ps;
    }

    function funcRest(tok, name, isMethod) {
      const ps = params();
      let ret = null;
      if (accept("op", "->") || accept("op", ":")) ret = typeName();
      endLine();
      const body = block(["конец"], tok);
      closeEnd();
      return { k: "func", name, params: ps, ret, body, method: !!isMethod, ...pos(tok) };
    }

    function loopBody(tok) { endLine(); const body = block(["конец"], tok); closeEnd(); return body; }

    function statement() {
      if (++depth > MAX_DEPTH) fail("слишком глубокая вложенность (больше " + MAX_DEPTH + " уровней)");
      try { return statementInner(); } finally { depth--; }
    }
    const STMT_WORDS = ["пусть", "константа", "напиши", "если", "пока", "повторить", "для", "функция", "верни", "брось", "выбор", "класс", "структура", "подключи", "метка", "поток"];
    function statementInner() {
      applyAliases();
      if (is("id") && !peek().aliased && !["op", "nl", "eof"].includes(peek(1).t)) {
        const fx = autoKeyword(peek().v, STMT_WORDS);
        if (fx) fixW(peek(), fx);
      }
      const tok = peek();
      const P = pos(tok);
      if (accept("kw", "пусть") || accept("kw", "константа")) {
        let kind = tok.v === "константа" ? "const" : "let";
        if (kind === "let" && accept("kw", "навсегда")) kind = "forever";
        const nameTok = expectName("имя переменной");
        if (is("op", ",")) {
          const names = [nameTok.v];
          while (accept("op", ",")) { const nt2 = expectName("имя переменной"); if (names.includes(nt2.v)) fail("имя «" + nt2.v + "» повторяется", nt2); names.push(nt2.v); }
          expect("op", "=", "«=»");
          const value = valueList();
          endLine();
          return { k: "multilet", kind, names, value, ...P };
        }
        const type = typeAnn();
        if (!is("op", "=")) fail("после имени «" + nameTok.v + "» нужен знак «=» и значение");
        next();
        const value = expr();
        endLine();
        return { k: "let", kind, name: nameTok.v, type, value, ...P };
      }
      if (isNew("предупреди") || isNew("отладка")) {
        const w = next().v;
        const items = [];
        if (!is("nl") && !is("eof")) { items.push(expr()); while (accept("op", ",")) items.push(expr()); }
        endLine();
        return { k: "print", items, err: w === "предупреди" ? "warn" : "debug", ...P };
      }
      if (isNew("делай")) {
        next();
        endLine();
        const body = block(["конец"], tok);
        expectW("конец");
        if (!accept("kw", "пока")) fail("после «конец» у «делай» нужно написать «пока условие» на той же строке: конец пока x < 10");
        const cond = expr();
        endLine();
        return { k: "dowhile", body, cond, ...P };
      }
      if (isNew("перечисление")) {
        next();
        const name = expectName("имя перечисления").v;
        endLine();
        skipNl();
        const items = [];
        while (!isW("конец")) {
          if (is("eof")) fail("не хватает слова «конец» для перечисления «" + name + "»", tok);
          for (;;) {
            const it = expectName("имя значения перечисления");
            if (items.some((x) => x.name === it.v)) fail("значение «" + it.v + "» в перечислении повторяется", it);
            let value = null;
            if (accept("op", "=")) value = expr();
            items.push({ name: it.v, value, ...pos(it) });
            if (!accept("op", ",")) break;
            skipNl();
          }
          endLine();
          skipNl();
        }
        closeEnd();
        if (!items.length) fail("перечисление «" + name + "» пустое", tok);
        return { k: "enum", name, items, ...P };
      }
      if (isNew("интерфейс")) {
        next();
        const name = expectName("имя интерфейса").v;
        endLine();
        skipNl();
        const methods = [];
        while (!isW("конец")) {
          if (is("eof")) fail("не хватает слова «конец» для интерфейса «" + name + "»", tok);
          if (!accept("kw", "функция")) fail("внутри интерфейса пишутся только строки «функция имя(параметры)» без тела, а встретилось " + describe(peek()));
          const mn = expectName("имя функции");
          checkMember(mn);
          const ps = params();
          if (accept("op", "->") || accept("op", ":")) typeName();
          endLine();
          methods.push({ name: mn.v, params: ps, ...pos(mn) });
          skipNl();
        }
        closeEnd();
        return { k: "iface", name, methods, ...P };
      }
      if (isNew("ленивая") || isNew("глобальная")) {
        const w = next().v;
        const nameTok = expectName("имя переменной");
        const type = typeAnn();
        if (!is("op", "=")) fail("после имени «" + nameTok.v + "» нужен знак «=» и значение");
        next();
        const value = expr();
        endLine();
        return { k: "let", kind: w === "ленивая" ? "lazy" : "global", name: nameTok.v, type, value, ...P };
      }
      if (isNew("убедись")) {
        next();
        const cond = expr();
        let msg = null;
        if (accept("op", ",")) msg = expr();
        endLine();
        return { k: "assert", cond, msg, ...P };
      }
      if (accept("kw", "напиши")) {
        const items = [];
        if (!is("nl") && !is("eof")) {
          items.push(expr());
          while (accept("op", ",")) items.push(expr());
        }
        endLine();
        return { k: "print", items, ...P };
      }
      if (accept("kw", "если")) return ifRest(tok);
      if (accept("kw", "пока")) {
        const cond = expr();
        return { k: "while", cond, body: loopBody(tok), ...P };
      }
      if (accept("kw", "повторить")) {
        const count = expr();
        expectW("раз");
        return { k: "repeat", count, body: loopBody(tok), ...P };
      }
      if (accept("kw", "для")) {
        if (acceptW("каждого")) {
          const a = expectBindName("имя переменной").v;
          let b = null;
          if (accept("op", ",")) b = expectBindName("имя переменной").v;
          expectW("из");
          const list = expr();
          return { k: "foreach", name: a, name2: b, list, body: loopBody(tok), ...P };
        }
        const name = expectName("имя переменной").v;
        if (!isW("от")) fail("после «для " + name + "» ожидалось «от … до …» или «для каждого … из …»" + (is("id") ? suggest(peek().v, ["от", "из"]) : ""));
        next();
        const from = expr();
        expectW("до");
        const to = expr();
        let step = null;
        if (acceptW("шаг")) step = expr();
        return { k: "forrange", name, from, to, step, body: loopBody(tok), ...P };
      }
      if (isNew("асинхронная")) {
        next();
        if (!accept("kw", "функция")) fail("после «асинхронная» нужно слово «функция»");
        if (is("op", "(")) fail("безымянную асинхронную функцию пока можно писать только как «асинхронная функция имя(...)»");
        const nameTok = expectName("имя функции");
        const fn = funcRest(tok, nameTok.v, false);
        fn.async = true;
        return fn;
      }
      if (accept("kw", "функция")) {
        if (is("op", "(")) { p--; return exprStatement(tok); }
        const nameTok = expectName("имя функции");
        return funcRest(tok, nameTok.v, false);
      }
      if (accept("kw", "верни")) {
        let value = null;
        if (!is("nl") && !is("eof")) value = expr();
        endLine();
        return { k: "return", value, ...P };
      }
      if (accept("kw", "стоп")) { endLine(); return { k: "break", ...P }; }
      if (accept("kw", "дальше")) { endLine(); return { k: "continue", ...P }; }
      if (accept("kw", "брось")) { const value = expr(); endLine(); return { k: "throw", value, ...P }; }
      if (accept("kw", "попробуй")) {
        endLine();
        const body = block(["если ошибка", "наконец", "конец"], tok);
        let fin = null;
        const handlers = [];
        while (isW("если") && isW("ошибка", 1)) {
          const ht = next();
          next();
          let type = null, errName = null;
          if (is("id") && !isW("как")) type = next().v;
          else if (is("kw", "ничего")) type = next().v;
          if (acceptW("как")) errName = expectName("имя для ошибки").v;
          accept("kw", "то");
          endLine();
          const hb = block(["если ошибка тип", "наконец", "конец"], tok);
          handlers.push({ type, errName, body: hb, ...pos(ht) });
          if (!type) break;
        }
        if (acceptW("наконец")) { endLine(); fin = block(["конец"], tok); }
        if (!handlers.length && !fin) fail("после «попробуй» нужен блок «если ошибка» или «наконец»");
        closeEnd();
        const h0 = handlers[0];
        return { k: "try", body, errName: h0 ? h0.errName : null, handler: h0 ? h0.body : null, handlers, fin, ...P };
      }
      if (accept("kw", "выбор")) {
        const subject = expr();
        endLine();
        skipNl();
        const cases = [];
        let def = null;
        const enders = ["вариант", "когда", "иначе", "конец"];
        while (!isW("конец")) {
          const ct = peek();
          if (is("eof")) fail("не хватает слова «конец» для «выбор» из строки " + tok.line, tok);
          if (def) fail("«иначе» в «выбор» должно быть последним", ct);
          if (accept("kw", "вариант")) {
            const values = [expr()];
            while (accept("op", ",")) values.push(expr());
            let guard = null;
            if (accept("kw", "если")) guard = expr();
            accept("kw", "то");
            endLine();
            cases.push({ values, guard, bind: null, body: block(enders, ct), ...pos(ct) });
          } else if (accept("kw", "когда")) {
            const bind = expectName("имя").v;
            let guard = null;
            if (accept("kw", "если")) guard = expr();
            accept("kw", "то");
            endLine();
            cases.push({ values: null, guard, bind, body: block(enders, ct), ...pos(ct) });
          } else if (accept("kw", "иначе")) {
            endLine();
            def = block(enders, ct);
          } else fail("внутри «выбор» ожидалось «вариант», «когда» или «иначе», а встретилось " + describe(peek()) + (is("id") ? suggest(peek().v, ["вариант", "когда", "иначе"]) : ""));
          skipNl();
        }
        closeEnd();
        return { k: "switch", subject, cases, def, ...P };
      }
      if (accept("kw", "класс")) {
        const name = expectName("имя класса").v;
        let parent = null;
        if (acceptW("наследует")) parent = expectName("имя класса").v;
        const ifaces = [];
        if (is("id") && ["реализует", "выполняет", "умеет"].includes(normWord(peek().v))) {
          next();
          do { ifaces.push(expectName("имя интерфейса").v); } while (accept("op", ","));
        }
        endLine();
        skipNl();
        const methods = [];
        const fields = [];
        while (!isW("конец")) {
          const mt = peek();
          if (is("eof")) fail("не хватает слова «конец» для класса «" + name + "»", tok);
          const mods = new Set();
          while (is("id") && CLASS_MODS.has(normWord(peek().v)) && peek(1).t !== "op" && peek(1).t !== "nl") mods.add(CLASS_MODS.get(normWord(next().v)));
          if (accept("kw", "функция")) {
            const mn = expectName("имя метода");
            checkMember(mn);
            let m;
            if (mods.has("abstract")) {
              const ps = params();
              if (accept("op", "->") || accept("op", ":")) typeName();
              endLine();
              m = { k: "func", name: mn.v, params: ps, ret: null, body: [], method: true, abstract: true, ...pos(mt) };
            } else m = funcRest(mt, mn.v, true);
            m.static = mods.has("static");
            m.priv = mods.has("private");
            methods.push(m);
          } else if (accept("kw", "пусть")) {
            const f = expectName("имя поля");
            checkMember(f);
            typeAnn();
            let value = null;
            if (accept("op", "=")) value = expr();
            endLine();
            fields.push({ name: f.v, value, static: mods.has("static"), priv: mods.has("private"), ...pos(f) });
          } else if (is("id") && normWord(peek().v) === "свойство") {
            next();
            const mn = expectName("имя свойства");
            checkMember(mn);
            if (accept("op", "(")) expect("op", ")", "«)»");
            endLine();
            skipNl();
            const GETW = new Set(["получить", "получи", "взять", "геттер"]);
            const SETW = new Set(["установить", "установи", "поставить", "поставь", "сеттер"]);
            if (is("id") && (GETW.has(normWord(peek().v)) || SETW.has(normWord(peek().v)))) {
              let getter = null, setter = null, setParam = "значение";
              while (!isW("конец")) {
                const at = peek();
                if (is("eof")) fail("не хватает слова «конец» для свойства «" + mn.v + "»", mt);
                if (is("id") && GETW.has(normWord(peek().v))) {
                  next();
                  accept("kw", "то");
                  endLine();
                  getter = block(["конец"], at);
                  closeEnd();
                } else if (is("id") && SETW.has(normWord(peek().v))) {
                  next();
                  if (is("id") && !GETW.has(normWord(peek().v)) && !SETW.has(normWord(peek().v)) && (peek(1).t === "nl" || peek(1).t === "eof" || (peek(1).t === "kw" && peek(1).v === "то"))) setParam = next().v;
                  accept("kw", "то");
                  endLine();
                  setter = block(["конец"], at);
                  closeEnd();
                } else fail("внутри свойства пишите «получить» и «установить значение», а встретилось " + describe(peek()));
                skipNl();
              }
              closeEnd();
              if (!getter && !setter) fail("у свойства «" + mn.v + "» нет ни «получить», ни «установить»", mt);
              if (getter) methods.push({ k: "func", name: mn.v, params: [], ret: null, body: getter, method: true, getter: true, static: mods.has("static"), priv: mods.has("private"), ...pos(mt) });
              if (setter) methods.push({ k: "func", name: mn.v, params: [{ name: setParam, type: null, def: null, ...pos(mt) }], ret: null, body: setter, method: true, setter: true, static: mods.has("static"), priv: mods.has("private"), ...pos(mt) });
            } else {
              const body = block(["конец"], mt);
              closeEnd();
              methods.push({ k: "func", name: mn.v, params: [], ret: null, body, method: true, getter: true, static: mods.has("static"), priv: mods.has("private"), ...pos(mt) });
            }
          } else fail("внутри класса можно писать только «функция», «пусть» и «свойство» (можно с «общая», «скрытая», «абстрактная»), а встретилось " + describe(peek()) + (is("id") ? suggest(peek().v, ["функция", "пусть", "свойство", "общая", "скрытая"]) : ""));
          skipNl();
        }
        closeEnd();
        return { k: "class", name, parent, ifaces, methods, fields, ...P };
      }
      if (accept("kw", "структура")) {
        const name = expectName("имя структуры").v;
        const ps = params();
        endLine();
        return { k: "struct", name, params: ps, ...P };
      }
      if (accept("kw", "подключи")) {
        const f = expect("str", undefined, "имя файла в кавычках");
        let alias = null;
        if (is("id") && normWord(peek().v) === "как") { next(); alias = expectName("имя модуля").v; }
        endLine();
        return { k: "import", path: f.v, alias, names: null, ...P };
      }
      if (isNew("из")) {
        next();
        const f = expect("str", undefined, "имя файла в кавычках");
        if (!(is("id") && ["возьми", "импортируй", "бери", "взять"].includes(normWord(peek().v)))) fail("после пути нужно слово «возьми» (например: из \"мат.pon\" возьми факториал)");
        next();
        const names = [];
        do {
          const nt = expectName("имя из модуля");
          let as = nt.v;
          if (is("id") && normWord(peek().v) === "как") { next(); as = expectName("новое имя").v; }
          if (names.some((x) => x.as === as)) fail("имя «" + as + "» повторяется", nt);
          names.push({ name: nt.v, as });
        } while (accept("op", ","));
        endLine();
        return { k: "import", path: f.v, alias: null, names, ...P };
      }
      if (isNew("метка")) {
        next();
        const name = expectName("имя метки").v;
        endLine();
        return { k: "label", name, ...P };
      }
      if (isNew("иди")) {
        next();
        if (is("id") && normWord(peek().v) === "к") next();
        const name = expectName("имя метки").v;
        endLine();
        return { k: "goto", name, ...P };
      }
      if (isNew("поток")) {
        next();
        endLine();
        const body = block(["конец"], tok);
        closeEnd();
        return { k: "thread", body, ...P };
      }
      if (isNew("с")) {
        next();
        const value = expr();
        let name = null;
        if (is("id") && normWord(peek().v) === "как") { next(); name = expectName("имя переменной").v; }
        else if (acceptW("как")) name = expectName("имя переменной").v;
        if (!name) fail("после «с …» нужно «как имя»: с открой(\"а.txt\") как ф");
        endLine();
        const body = block(["конец"], tok);
        closeEnd();
        return { k: "with", value, name, body, ...P };
      }
      return exprStatement(tok);
    }

    function checkMember(t) {
      if (/^__/.test(t.v) || INTERNAL_NAMES.has(t.v)) fail("имя «" + t.v + "» служебное для JavaScript и не может быть полем или функцией класса", t);
    }
    function valueList() {
      const first = expr();
      if (!is("op", ",")) return first;
      const items = [first];
      while (accept("op", ",")) items.push(expr());
      return { k: "list", items, ...pos(tok0(first)) };
    }
    function tok0(e) { return { line: e.line, col: e.col, file: e.file }; }
    function exprStatement(tok) {
      const P = pos(tok);
      const target = expr();
      if (is("op", ",") && ["name", "index", "member", "deref"].includes(target.k)) {
        const targets = [target];
        while (accept("op", ",")) {
          const t2 = expr();
          if (!["name", "index", "member", "deref"].includes(t2.k)) fail("слева от «=» должны быть имена переменных, элементы списков, поля или разыменованные ссылки", t2);
          targets.push(t2);
        }
        expect("op", "=", "«=» (например: а, б = б, а)");
        const value = valueList();
        endLine();
        const parts = targets.map((t) => ({ k: "assign", target: t, op: "=", value: { k: "raw", js: null }, ...P }));
        return { k: "multiassign", parts, value, ...P };
      }
      const opTok = peek();
      if (is("op") && ["=", "+=", "-=", "*=", "/=", "%="].includes(opTok.v)) {
        next();
        if (!["name", "index", "member", "deref"].includes(target.k)) fail("слева от «" + opTok.v + "» должно быть имя переменной, элемент списка, поле или разыменованная ссылка", opTok);
        const value = expr();
        endLine();
        return { k: "assign", target, op: opTok.v, value, ...P };
      }
      if (!is("nl") && !is("eof")) {
        if (target.k === "name") {
          const hint = suggest(target.name, ALL_WORDS);
          if (hint) fail("непонятная команда «" + target.name + "»." + hint, tok);
        }
        fail("ожидался конец строки, а встретилось " + describe(peek()));
      }
      endLine();
      return { k: "expr", expr: target, ...P };
    }

    function ifRest(tok) {
      const cond = expr();
      if (!accept("kw", "то") && !is("nl")) fail("после условия в «если» ожидалось слово «то», а встретилось " + describe(peek()) + (is("id") ? suggest(peek().v, ["то"]) : ""));
      endLine();
      const then = block(["иначе", "конец"], tok);
      let otherwise = null;
      if (isW("иначе")) {
        const et = next();
        if (accept("kw", "если")) { otherwise = [ifRest(et)]; return { k: "if", cond, then, otherwise, ...pos(tok) }; }
        endLine();
        otherwise = block(["конец"], tok);
      }
      closeEnd();
      return { k: "if", cond, then, otherwise, ...pos(tok) };
    }

    function expr() {
      if (++depth > MAX_DEPTH) fail("слишком глубокая вложенность выражения (больше " + MAX_DEPTH + " уровней)");
      try { return walrus(); } finally { depth--; }
    }
    function walrus() {
      if (is("id") && peek(1).t === "op" && peek(1).v === ":=") {
        const t = next();
        next();
        return { k: "walrus", name: t.v, value: walrus(), ...pos(t) };
      }
      return pipe();
    }

    function pipe() {
      let left = or();
      while (is("kw", "затем") || is("op", "|>")) {
        const t = next();
        skipNl();
        if (is("id") && !(peek(1).t === "op" && [".", "?.", "?.[", "[", "=>"].includes(peek(1).v))) {
          const nt = next();
          const args = is("op", "(") ? callArgs() : [];
          left = { k: "pipe", left, name: nt.v, args, ...pos(nt) };
        } else left = { k: "pipe", left, fn: postfix(), args: [], ...pos(t) };
      }
      return left;
    }
    function or() {
      let l = and();
      while (is("kw", "или")) { const t = next(); skipNl(); l = { k: "bin", op: "или", l, r: and(), ...pos(t) }; }
      return l;
    }
    function and() {
      let l = not();
      while (is("kw", "и")) { const t = next(); skipNl(); l = { k: "bin", op: "и", l, r: not(), ...pos(t) }; }
      return l;
    }
    function not() {
      if (is("kw", "не")) { const t = next(); if (++depth > MAX_DEPTH) fail("слишком глубокая вложенность выражения"); try { return { k: "un", op: "не", e: not(), ...pos(t) }; } finally { depth--; } }
      return cmp();
    }
    function cmp() {
      let l = add();
      let n = 0;
      for (;;) {
        if (n++ === 1 && ((is("op") && ["==", "!=", "<", ">", "<=", ">="].includes(peek().v)) || is("kw", "в"))) fail("сравнения нельзя писать цепочкой: «a < b < c» понимается неоднозначно. Напиши через «и»: a < b и b < c");
        if (is("op") && ["==", "!=", "<", ">", "<=", ">="].includes(peek().v)) { const t = next(); l = { k: "bin", op: t.v, l, r: add(), ...pos(t) }; continue; }
        if (is("kw", "в")) { const t = next(); l = { k: "bin", op: "в", l, r: add(), ...pos(t) }; continue; }
        if (is("kw", "не") && peek(1).t === "kw" && peek(1).v === "в") { const t = next(); next(); l = { k: "un", op: "не", e: { k: "bin", op: "в", l, r: add(), ...pos(t) }, ...pos(t) }; continue; }
        return l;
      }
    }
    function add() {
      let l = mul();
      while (is("op", "+") || is("op", "-")) { const t = next(); skipNl(); l = { k: "bin", op: t.v, l, r: mul(), ...pos(t) }; }
      return l;
    }
    function mul() {
      let l = unary();
      while (is("op", "*") || is("op", "/") || is("op", "%")) { const t = next(); skipNl(); l = { k: "bin", op: t.v, l, r: unary(), ...pos(t) }; }
      return l;
    }
    function unary() {
      if (is("op", "-") || is("op", "+")) {
        const t = next();
        if (++depth > MAX_DEPTH) fail("слишком глубокая вложенность выражения");
        try { const e = unary(); return t.v === "-" ? { k: "un", op: "-", e, ...pos(t) } : e; } finally { depth--; }
      }
      if (is("op", "&")) {
        const t = next();
        return { k: "refof", target: unary(), ...pos(t) };
      }
      if (is("op", "*")) {
        const nx = peek(1);
        if (nx.t === "id" || nx.t === "kw" || (nx.t === "op" && ["(", "*", "&", "-"].includes(nx.v))) {
          const t = next();
          return { k: "deref", e: unary(), ...pos(t) };
        }
      }
      if (is("id") && ["ссылка", "указатель"].includes(normWord(peek().v))) {
        const nx = peek(1);
        if (!(nx.t === "op" && nx.v !== "-" && nx.v !== "&" && nx.v !== "*")) {
          const t = next();
          if (is("id") && normWord(peek().v) === "на") next();
          return { k: "refof", target: unary(), ...pos(t) };
        }
      }
      if (is("id") && ["ждать", "ожидай", "жди"].includes(normWord(peek().v))) {
        const nx = peek(1);
        if (!(nx.t === "op" && !["(", "-", "+", "*", "&"].includes(nx.v))) {
          const t = next();
          return { k: "await", e: unary(), ...pos(t) };
        }
      }
      return power();
    }
    function power() {
      const b = postfix();
      if (is("op", "^")) { const t = next(); return { k: "bin", op: "^", l: b, r: unary(), ...pos(t) }; }
      return b;
    }
    function callArgs() {
      expect("op", "(");
      const args = [];
      skipNl();
      if (!is("op", ")")) {
        for (;;) {
          args.push(expr());
          skipNl();
          if (!accept("op", ",")) break;
          skipNl();
        }
      }
      expect("op", ")", "«)»");
      return args;
    }
    function postfix() {
      let e = primary();
      let safe = false;
      for (;;) {
        if (is("op", "(")) { const t = peek(); e = { k: "call", fn: e, args: callArgs(), safe, ...pos(t) }; continue; }
        if (is("op", "[") || is("op", "?.[")) {
          const t = next();
          if (t.v === "?.[") safe = true;
          skipNl();
          const index = expr();
          skipNl();
          expect("op", "]", "«]»");
          e = { k: "index", obj: e, index, safe, ...pos(t) };
          continue;
        }
        if (is("op", ".") || is("op", "?.") || is("op", "::")) {
          const t = next();
          if (t.v === "?.") safe = true;
          const nt = peek();
          if (nt.t !== "id" && nt.t !== "kw") fail((t.v === "::" ? "после «::»" : "после точки") + " ожидалось имя поля или метода");
          if (nt.t === "id" && (/^__/.test(nt.v) || INTERNAL_NAMES.has(nt.v))) fail("«" + nt.v + "» — внутреннее имя JavaScript, обращаться к нему из программы нельзя", nt);
          next();
          if (t.v === "::") { e = { k: "methodref", obj: e, name: nt.v, ...pos(nt) }; continue; }
          if (is("op", "(")) { e = { k: "mcall", obj: e, name: nt.v, args: callArgs(), safe, ...pos(nt) }; continue; }
          e = { k: "member", obj: e, name: nt.v, safe, ...pos(nt) };
          continue;
        }
        return e;
      }
    }
    function primary() {
      const tok = peek();
      const P = pos(tok);
      if (accept("num")) return { k: "num", v: tok.v, ...P };
      if (accept("str")) return { k: "str", v: tok.v, ...P };
      if (accept("tmpl")) {
        const parts = tok.parts.map((pt) => (typeof pt === "string" ? pt : parseTokens(tokenize(pt.src, tok.file || file, pt.line, pt.col, warn), tok.file || file, true, warn, depth)));
        return { k: "tmpl", parts, ...P };
      }
      if (accept("kw", "истина")) return { k: "bool", v: true, ...P };
      if (accept("kw", "ложь")) return { k: "bool", v: false, ...P };
      if (accept("kw", "ничего")) return { k: "null", ...P };
      if (accept("kw", "это") || accept("kw", "себя")) return { k: "this", ...P };
      if (accept("kw", "родитель")) {
        if (is("op", "(")) return { k: "super", name: "создать", args: callArgs(), ctor: true, ...P };
        expect("op", ".");
        const nt = expectName("имя метода");
        return { k: "super", name: nt.v, args: callArgs(), ...P };
      }
      if (accept("kw", "новый")) {
        let name = null;
        if (is("id")) name = next().v;
        const args = is("op", "(") ? callArgs() : [];
        if (accept("op", ":")) {
          endLine();
          skipNl();
          const methods = [];
          const fields = [];
          while (!isW("конец")) {
            const mt = peek();
            if (is("eof")) fail("не хватает слова «конец» для безымянного класса", tok);
            const mods = new Set();
            while (is("id") && CLASS_MODS.has(normWord(peek().v)) && peek(1).t !== "op" && peek(1).t !== "nl") mods.add(CLASS_MODS.get(normWord(next().v)));
            if (accept("kw", "функция")) {
              const mn = expectName("имя метода");
              checkMember(mn);
              const m = funcRest(mt, mn.v, true);
              m.static = mods.has("static");
              m.priv = mods.has("private");
              methods.push(m);
            } else if (accept("kw", "пусть")) {
              const f = expectName("имя поля");
              checkMember(f);
              typeAnn();
              let value = null;
              if (accept("op", "=")) value = expr();
              endLine();
              fields.push({ name: f.v, value, static: mods.has("static"), priv: mods.has("private"), ...pos(f) });
            } else if (is("id") && normWord(peek().v) === "свойство") {
              next();
              const mn = expectName("имя свойства");
              checkMember(mn);
              endLine();
              const body = block(["конец"], mt);
              closeEnd();
              methods.push({ k: "func", name: mn.v, params: [], ret: null, body, method: true, getter: true, static: mods.has("static"), priv: mods.has("private"), ...pos(mt) });
            } else fail("внутри безымянного класса пишут «функция», «пусть» и «свойство», а встретилось " + describe(peek()));
            skipNl();
          }
          expectW("конец");
          return { k: "anonclass", parent: name, args, methods, fields, ...P };
        }
        if (!name) fail("после «новый» нужно имя класса или «:» для безымянного класса");
        return { k: "new", name, args, ...P };
      }
      if (is("id") && ["поток", "нить"].includes(normWord(peek().v)) && (peek(1).t === "nl" || peek(1).t === "eof")) {
        const t = next();
        endLine();
        const body = block(["конец"], t);
        expectW("конец");
        return { k: "thread", body, ...P };
      }
      if (accept("kw", "выбор")) {
        const subject = expr();
        endLine();
        skipNl();
        const cases = [];
        let def = null;
        const enders = ["вариант", "когда", "иначе", "конец"];
        while (!isW("конец")) {
          const ct = peek();
          if (is("eof")) fail("не хватает слова «конец» для «выбор» из строки " + tok.line, tok);
          if (def) fail("«иначе» в «выбор» должно быть последним", ct);
          if (accept("kw", "вариант")) {
            const values = [expr()];
            while (accept("op", ",")) values.push(expr());
            let guard = null;
            if (accept("kw", "если")) guard = expr();
            accept("kw", "то");
            if (!is("nl") && !is("eof") && !(is("kw") && ["вариант", "когда", "иначе", "конец"].includes(peek().v))) {
              const value = expr();
              endLine();
              cases.push({ values, guard, bind: null, value, body: null, ...pos(ct) });
            } else {
              endLine();
              cases.push({ values, guard, bind: null, value: null, body: block(enders, ct), ...pos(ct) });
            }
          } else if (accept("kw", "когда")) {
            const bind = expectName("имя").v;
            let guard = null;
            if (accept("kw", "если")) guard = expr();
            accept("kw", "то");
            if (!is("nl") && !is("eof") && !(is("kw") && ["вариант", "когда", "иначе", "конец"].includes(peek().v))) {
              const value = expr();
              endLine();
              cases.push({ values: null, guard, bind, value, body: null, ...pos(ct) });
            } else {
              endLine();
              cases.push({ values: null, guard, bind, value: null, body: block(enders, ct), ...pos(ct) });
            }
          } else if (accept("kw", "иначе")) {
            if (!is("nl") && !is("eof") && !isW("конец")) {
              def = [{ k: "return", value: expr(), ...pos(ct) }];
              endLine();
            } else {
              endLine();
              def = block(enders, ct);
            }
          } else fail("внутри «выбор» ожидалось «вариант», «когда» или «иначе», а встретилось " + describe(peek()));
          skipNl();
        }
        expectW("конец");
        return { k: "switchexpr", subject, cases, def, ...P };
      }
      if (accept("id")) {
        if (is("op", "=>")) { next(); return { k: "lambda", params: [{ name: tok.v, def: null, type: null, ...P }], body: null, value: expr(), ...P }; }
        return { k: "name", name: tok.v, ...P };
      }
      if (accept("kw", "функция")) {
        const ps = params();
        if (accept("op", "=>")) return { k: "lambda", params: ps, body: null, value: expr(), ...P };
        endLine();
        const body = block(["конец"], tok);
        expectW("конец");
        return { k: "lambda", params: ps, body, value: null, ...P };
      }
      if (accept("kw", "если")) {
        const cond = expr();
        expect("kw", "то");
        const a = expr();
        expect("kw", "иначе");
        const b = expr();
        return { k: "cond", cond, a, b, ...P };
      }
      if (accept("op", "(")) {
        skipNl();
        const e = expr();
        skipNl();
        expect("op", ")", "«)»");
        return e;
      }
      if (accept("op", "[")) {
        const items = [];
        skipNl();
        if (is("op", "]")) { next(); return { k: "list", items, ...P }; }
        const first = expr();
        skipNl();
        if (accept("kw", "для")) {
          expectW("каждого");
          const a = expectBindName("имя переменной").v;
          let b = null;
          if (accept("op", ",")) b = expectBindName("имя переменной").v;
          expectW("из");
          const list = expr();
          let cond = null;
          if (accept("kw", "если")) cond = expr();
          skipNl();
          expect("op", "]", "«]»");
          return { k: "listcomp", value: first, name: a, name2: b, list, cond, ...P };
        }
        items.push(first);
        if (accept("op", ",")) {
          skipNl();
          while (!is("op", "]")) {
            items.push(expr());
            skipNl();
            if (!accept("op", ",")) break;
            skipNl();
          }
        }
        expect("op", "]", "«]»");
        return { k: "list", items, ...P };
      }
      if (accept("op", "{")) {
        const entries = [];
        skipNl();
        if (is("op", "}")) { next(); return { k: "dict", entries, ...P }; }
        let firstKey = null, firstVal = null, firstExpr = null, firstKeyIdent = false;
        const kt = peek();
        if ((kt.t === "id" || kt.t === "kw") && peek(1).t === "op" && peek(1).v === ":") { next(); firstKey = { k: "str", v: kt.v, ...pos(kt) }; firstKeyIdent = true; expect("op", ":"); skipNl(); firstVal = expr(); }
        else {
          const e0 = expr();
          skipNl();
          if (accept("op", ":")) { firstKey = e0; skipNl(); firstVal = expr(); }
          else firstExpr = e0;
        }
        skipNl();
        if (accept("kw", "для")) {
          expectW("каждого");
          const a = expectBindName("имя переменной").v;
          let b = null;
          if (accept("op", ",")) b = expectBindName("имя переменной").v;
          expectW("из");
          const list = expr();
          let cond = null;
          if (accept("kw", "если")) cond = expr();
          skipNl();
          expect("op", "}", "«}»");
          if (firstKey) return { k: "dictcomp", key: firstKeyIdent ? { k: "name", name: firstKey.v, line: firstKey.line, col: firstKey.col, file: firstKey.file } : firstKey, value: firstVal, name: a, name2: b, list, cond, ...P };
          return { k: "setcomp", value: firstExpr, name: a, name2: b, list, cond, ...P };
        }
        if (firstKey) {
          entries.push({ key: firstKey, value: firstVal });
          if (accept("op", ",")) {
            skipNl();
            while (!is("op", "}")) {
              let key;
              const kt2 = peek();
              if ((kt2.t === "id" || kt2.t === "kw") && peek(1).t === "op" && peek(1).v === ":") { next(); key = { k: "str", v: kt2.v, ...pos(kt2) }; }
              else key = expr();
              expect("op", ":");
              skipNl();
              entries.push({ key, value: expr() });
              skipNl();
              if (!accept("op", ",")) break;
              skipNl();
            }
          }
          expect("op", "}", "«}»");
          return { k: "dict", entries, ...P };
        }
        const items = [firstExpr];
        if (accept("op", ",")) {
          skipNl();
          while (!is("op", "}")) {
            items.push(expr());
            skipNl();
            if (!accept("op", ",")) break;
            skipNl();
          }
        }
        expect("op", "}", "«}»");
        return { k: "setlit", items, ...P };
      }
      if (accept("kw", "в")) return { k: "name", name: "в", ...P };
      if (tok.t === "kw") fail("здесь не может стоять слово «" + tok.v + "»", tok);
      fail("ожидалось значение, а встретилось " + describe(tok));
    }

    if (subExpr) {
      const e = expr();
      skipNl();
      if (!is("eof")) fail("лишнее во вставке «{…}»: " + describe(peek()));
      return e;
    }
    const body = [];
    skipNl();
    while (!is("eof")) {
      if (isW("конец")) fail("лишнее слово «конец»: для него нет открывающей команды");
      if (isW("иначе")) fail("«иначе» без «если»");
      body.push(statement());
      skipNl();
    }
    return { k: "program", body };
  }

  const INTERNAL_NAMES = new Set(["constructor", "prototype", "__proto__", "__defineGetter__", "__defineSetter__", "__lookupGetter__", "__lookupSetter__", "hasOwnProperty", "isPrototypeOf", "propertyIsEnumerable", "toLocaleString", "toString", "valueOf", "caller", "callee", "arguments", "call", "apply", "bind"]);

  const BUILTINS = {
    "спроси": [0, 1], "число": [1, 1], "строка": [1, 1], "длина": [1, 1], "добавь": [2, 2], "убери": [1, 2], "случайное": [0, 2],
    "округли": [1, 2], "корень": [1, 1], "модуль": [1, 1], "большими": [1, 1], "маленькими": [1, 1], "соедини": [1, 2], "от": [2, 3],
    "тип": [1, 1], "сумма": [1, 1], "макс": [1, 99], "мин": [1, 99], "целое": [1, 1], "степень": [2, 2], "время": [0, 0],
    "дата_сейчас": [0, 0], "пауза": [1, 1], "формат_числа": [1, 2], "синус": [1, 1], "косинус": [1, 1], "логарифм": [1, 2], "вниз": [1, 1], "вверх": [1, 1],
    "прочитай_файл": [1, 1], "запиши_файл": [2, 2], "допиши_файл": [2, 2], "файл_есть": [1, 1], "логическое": [1, 1], "ПИ": null,
    "выход": [0, 1], "проверь": [1, 2], "множество": [0, 1], "нацело": [2, 2], "экземпляр": [2, 2], "символ": [1, 1], "код": [1, 1],
    "бит_и": [2, 2], "бит_или": [2, 2], "бит_искл": [2, 2], "бит_не": [1, 1], "сдвиг_влево": [2, 2], "сдвиг_вправо": [2, 2], "сдвиг_вправо_без_знака": [2, 2],
    "в_json": [1, 2], "из_json": [1, 1], "нумеруй": [1, 1], "попарно": [2, 2], "список_файлов": [1, 1], "удали_файл": [1, 1], "создай_папку": [1, 1],
    "тангенс": [1, 1], "арктангенс": [1, 2], "экспонента": [1, 1], "бесконечность": null,
    "аргументы": null, "argv": null, "размер": [1, 1], "sizeof": [1, 1], "формат": [1, 99], "printf": [1, 99], "открой": [1, 2],
  };
  const BUILTIN_CLASSES = ["Ошибка"];
  const TYPES = ["число", "строка", "логическое", "список", "словарь", "функция", "ничего", "любое", "целое", "множество"];

  function argWord(n) { return n % 10 === 1 && n % 100 !== 11 ? "значение" : [2, 3, 4].includes(n % 10) && ![12, 13, 14].includes(n % 100) ? "значения" : "значений"; }

  function analyze(program, opts) {
    opts = opts || {};
    const file = opts.file;
    const err = (msg, n) => { throw new CompileError(msg, n.line, n.col, n.file || file); };
    const scopes = [];
    const ctx = { fn: 0, loop: 0, cls: null, method: false, labels: new Map(), gotos: [], labelDepth: 0, switchExpr: 0 };
    const push = () => scopes.push(new Map());
    const pop = () => scopes.pop();
    const lookup = (name) => { for (let i = scopes.length - 1; i >= 0; i--) if (scopes[i].has(name)) return scopes[i].get(name); return null; };
    const allNames = () => { const s = new Set(); for (const sc of scopes) for (const k of sc.keys()) s.add(k); return [...s]; };
    const classNames = () => allNames().filter((x) => lookup(x).kind === "class" || lookup(x).kind === "iface");
    const builtinUsed = new Set();
    const markBuiltin = (name, info) => { if (info && info.builtinCls) builtinUsed.add(name); };
    const classNodes = new Map();
    const privNames = new Map(), pubNames = new Set();
    (function collect(list) {
      for (const s of list) {
        if (s.k === "class") {
          classNodes.set(s.name, s);
          for (const m of s.methods.concat(s.fields)) { if (m.priv) { if (!privNames.has(m.name)) privNames.set(m.name, new Set()); privNames.get(m.name).add(s.name); } else pubNames.add(m.name); }
        }
        for (const k of ["body", "then", "otherwise", "handler", "fin"]) if (Array.isArray(s[k])) collect(s[k]);
        if (s.handlers) s.handlers.forEach((h) => collect(h.body || []));
        if (s.cases) s.cases.forEach((c) => collect(c.body || []));
        if (s.k === "class" || s.k === "anonclass") s.methods.forEach((m) => collect(m.body || []));
        if (s.k === "module" || s.k === "thread" || s.k === "with") collect(s.body || []);
      }
    })(program.body);
    const privOk = (name) => {
      if (!privNames.has(name) || pubNames.has(name)) return true;
      let c = ctx.cls;
      const owners = privNames.get(name);
      while (c) { if (owners.has(c.name)) return true; c = c.parent ? classNodes.get(c.parent) : null; }
      return false;
    };
    const declare = (name, info, n) => {
      const top = scopes[scopes.length - 1];
      if (top.has(name)) {
        const prev = top.get(name);
        if (prev.node && prev.node === info.node) return;
        err("имя «" + name + "» уже объявлено" + (prev.line ? " в строке " + prev.line : ""), n);
      }
      top.set(name, { ...info, line: n.line });
    };
    const hoist = (body) => {
      for (const s of body) {
        if (s.k === "func") declare(s.name, { kind: "func", node: s, min: s.params.filter((q) => !q.def).length, max: s.params.length }, s);
        if (s.k === "class" || s.k === "struct") declare(s.name, { kind: "class", node: s }, s);
        if (s.k === "iface") declare(s.name, { kind: "iface", node: s }, s);
      }
    };
    const checkType = (t, n) => {
      if (t && lookup(t) && !TYPES.includes(t)) markBuiltin(t, lookup(t));
      if (t && !TYPES.includes(t) && !(lookup(t) && (lookup(t).kind === "class" || lookup(t).kind === "iface"))) err("неизвестный тип «" + t + "»." + suggest(t, TYPES.concat(classNames())), n);
    };
    const litType = (e) => ({ num: "число", str: "строка", tmpl: "строка", bool: "логическое", list: "список", dict: "словарь", lambda: "функция" })[e.k];

    function body(list) { hoist(list); for (const s of list) stmt(s); }
    function blockScope(list) { ctx.labelDepth++; push(); body(list); pop(); ctx.labelDepth--; }
    function loop(f) { ctx.loop++; f(); ctx.loop--; }
    function checkGotos(owner) {
      for (const g of ctx.gotos) if (!ctx.labels.has(g.name)) err("нет метки «" + g.name + "». Поставьте её так: метка " + g.name, g);
      owner.hasGoto = ctx.labels.size > 0 || ctx.gotos.length > 0;
    }
    function withLabels(owner, f) {
      const saved = { labels: ctx.labels, gotos: ctx.gotos, labelDepth: ctx.labelDepth };
      ctx.labels = new Map(); ctx.gotos = []; ctx.labelDepth = 0;
      try { f(); checkGotos(owner); } finally { ctx.labels = saved.labels; ctx.gotos = saved.gotos; ctx.labelDepth = saved.labelDepth; }
    }

    function stmt(s) {
      switch (s.k) {
        case "let": {
          if (s.type) {
            checkType(s.type, s);
            const lt = litType(s.value);
            if (lt && TYPES.includes(s.type) && s.type !== "любое" && lt !== s.type && !(s.type === "целое" && lt === "число")) err("переменная «" + s.name + "» объявлена как «" + s.type + "», а ей дают значение типа «" + lt + "»", s.value);
          }
          expr(s.value);
          if (s.kind === "global") {
            if (scopes[1].has(s.name)) err("имя «" + s.name + "» уже объявлено" + (scopes[1].get(s.name).line ? " в строке " + scopes[1].get(s.name).line : ""), s);
            scopes[1].set(s.name, { kind: "let", type: s.type, line: s.line });
            if (!program.globals.includes(s.name)) program.globals.push(s.name);
            return;
          }
          declare(s.name, { kind: s.kind, type: s.type }, s);
          return;
        }
        case "multilet": expr(s.value); for (const n of s.names) declare(n, { kind: s.kind }, s); return;
        case "multiassign": expr(s.value); s.parts.forEach(stmt); return;
        case "dowhile": loop(() => blockScope(s.body)); expr(s.cond); return;
        case "assert": expr(s.cond); if (s.msg) expr(s.msg); return;
        case "enum": push(); s.items.forEach((it) => it.value && expr(it.value)); pop(); declare(s.name, { kind: "const" }, s); return;
        case "iface": { const names = new Set(); for (const m of s.methods) { if (names.has(m.name)) err("функция «" + m.name + "» в интерфейсе объявлена дважды", m); names.add(m.name); } return; }
        case "assign": {
          if (s.target.k === "name") {
            const info = lookup(s.target.name);
            if (!info) err("переменная «" + s.target.name + "» не объявлена. Объяви её так: пусть " + s.target.name + " = …" + suggest(s.target.name, allNames()), s.target);
            if (info.kind === "const") err("нельзя изменить константу «" + s.target.name + "»", s.target);
            if (info.kind === "forever") err("нельзя изменить «" + s.target.name + "»: она объявлена как «пусть навсегда»", s.target);
            if (info.kind === "lazy") err("нельзя изменить ленивую переменную «" + s.target.name + "»", s.target);
            if (info.kind === "func" || info.kind === "builtin" || info.kind === "class" || info.kind === "iface") err("нельзя присвоить значение «" + s.target.name + "»: это " + (info.kind === "class" ? "класс" : "функция"), s.target);
            s.target.ref = "user";
            if (info.type) s.checkType = info.type;
          } else expr(s.target);
          expr(s.value);
          return;
        }
        case "print": s.items.forEach(expr); return;
        case "expr": expr(s.expr); return;
        case "if": expr(s.cond); blockScope(s.then); if (s.otherwise) blockScope(s.otherwise); return;
        case "while": expr(s.cond); loop(() => blockScope(s.body)); return;
        case "repeat": expr(s.count); loop(() => blockScope(s.body)); return;
        case "foreach":
          expr(s.list);
          if (s.name2 && s.name2 === s.name) err("имена в «для каждого» не должны совпадать", s);
          loop(() => { ctx.labelDepth++; push(); declare(s.name, { kind: "let" }, s); if (s.name2) declare(s.name2, { kind: "let" }, s); body(s.body); pop(); ctx.labelDepth--; });
          return;
        case "forrange":
          expr(s.from); expr(s.to);
          if (s.step) { expr(s.step); if (s.step.k === "num" && s.step.v === 0) err("шаг цикла не может быть нулём", s.step); }
          loop(() => { ctx.labelDepth++; push(); declare(s.name, { kind: "let" }, s); body(s.body); pop(); ctx.labelDepth--; });
          return;
        case "func": func(s, s.method); return;
        case "return": if (!ctx.fn && !ctx.switchExpr) err("«верни» можно писать только внутри функции", s); if (s.value) expr(s.value); return;
        case "label":
          if (ctx.labelDepth) err("метку «" + s.name + "» можно ставить только в теле программы или функции, не внутри «если», «пока» и других блоков", s);
          if (ctx.labels.has(s.name)) err("метка «" + s.name + "» уже есть", s);
          ctx.labels.set(s.name, s);
          return;
        case "goto": ctx.gotos.push(s); return;
        case "thread":
          withLabels(s, () => {
            const savedFn = ctx.fn, savedLoop = ctx.loop;
            ctx.fn = 1; ctx.loop = 0;
            push(); body(s.body); pop();
            ctx.fn = savedFn; ctx.loop = savedLoop;
          });
          return;
        case "with":
          expr(s.value);
          ctx.labelDepth++;
          push(); declare(s.name, { kind: "let" }, s); body(s.body); pop();
          ctx.labelDepth--;
          return;
        case "module": {
          withLabels(s, () => {
            push();
            body(s.body);
            s.exports = [];
            for (const [n, info] of scopes[scopes.length - 1]) if (["func", "class", "let", "const", "forever", "lazy", "iface"].includes(info.kind)) s.exports.push(n);
            pop();
          });
          if (s.alias) declare(s.alias, { kind: "const" }, s);
          if (s.names) {
            const have = new Set(s.exports);
            for (const n of s.names) {
              if (!have.has(n.name)) err("в модуле «" + (s.path || "") + "» нет имени «" + n.name + "»", s);
              declare(n.as, { kind: "let" }, s);
            }
          }
          return;
        }
        case "break": if (!ctx.loop) err("«стоп» можно писать только внутри цикла", s); return;
        case "continue": if (!ctx.loop) err("«дальше» можно писать только внутри цикла", s); return;
        case "throw": expr(s.value); return;
        case "try":
          blockScope(s.body);
          for (const h of s.handlers || []) {
            if (h.type && h.type !== "ничего") {
              const ti = lookup(h.type);
              if (!TYPES.includes(h.type) && !(ti && (ti.kind === "class" || ti.kind === "iface"))) err("неизвестный тип ошибки «" + h.type + "»." + suggest(h.type, TYPES.concat(classNames())), h);
              markBuiltin(h.type, ti);
            }
            ctx.labelDepth++; push(); if (h.errName) declare(h.errName, { kind: "let" }, h); body(h.body); pop(); ctx.labelDepth--;
          }
          if (s.fin) blockScope(s.fin);
          return;
        case "switch":
          expr(s.subject);
          for (const c of s.cases) {
            push();
            if (c.values) c.values.forEach(expr);
            if (c.bind) declare(c.bind, { kind: "let" }, c);
            if (c.guard) expr(c.guard);
            body(c.body);
            pop();
          }
          if (s.def) blockScope(s.def);
          return;
        case "class": {
          if (s.parent) {
            const pi = lookup(s.parent);
            if (!pi || pi.kind !== "class") err("класс-родитель «" + s.parent + "» не найден." + suggest(s.parent, classNames()), s);
            if (s.parent === s.name) err("класс не может наследовать сам себя", s);
            markBuiltin(s.parent, pi);
          }
          for (const i of s.ifaces || []) { const ii = lookup(i); if (!ii || ii.kind !== "iface") err("интерфейс «" + i + "» не найден." + suggest(i, allNames().filter((x) => lookup(x).kind === "iface")), s); }
          const names = new Set();
          for (const m of s.methods) {
            const key = (m.static ? "общая " : "") + m.name + (m.getter ? "#get" : m.setter ? "#set" : m.name === "создать" ? "#c" + m.params.length : "");
            if (names.has(key)) err("метод «" + m.name + "» объявлен дважды", m);
            names.add(key);
          }
          const saved = { ...ctx };
          ctx.cls = s;
          ctx.method = true;
          ctx.fn = 0;
          push();
          for (const f of s.fields) if (f.value) expr(f.value);
          for (const m of s.methods) func(m, true);
          pop();
          Object.assign(ctx, saved);
          return;
        }
        case "struct": push(); for (const q of s.params) { if (q.def) expr(q.def); checkType(q.type, q); } pop(); return;
        case "import": err("«подключи» можно писать только на верхнем уровне программы", s); return;
        default: err("неизвестная команда", s);
      }
    }
    function func(f, isMethod) {
      const saved = { ...ctx };
      ctx.fn = 1; ctx.loop = 0;
      if (!isMethod && f.name) { ctx.cls = null; ctx.method = false; }
      withLabels(f, () => {
        push();
        for (const q of f.params) { checkType(q.type, q); if (q.def) expr(q.def); declare(q.name, { kind: "let", type: q.type }, q); }
        if (f.ret) checkType(f.ret, f);
        body(f.body);
        pop();
      });
      Object.assign(ctx, saved);
    }
    function checkArity(info, n, count, name) {
      let mn, mx;
      if (info && info.kind === "func") { mn = info.min; mx = info.max; }
      else if (info && info.kind === "builtin" && BUILTINS[name]) [mn, mx] = BUILTINS[name];
      else return;
      if (count < mn || count > mx) err("функция «" + name + "» ждёт " + (mn === mx ? mn + " " + argWord(mn) : "от " + mn + " до " + mx + " значений") + ", а получила " + count, n);
    }
    function resolve(n) {
      const info = lookup(n.name);
      if (info) { n.ref = info.kind === "class" ? "class" : info.kind === "builtin" ? "builtin" : "user"; if (info.kind === "lazy") n.lazy = true; markBuiltin(n.name, info); return info; }
      err("не знаю, что такое «" + n.name + "»." + (suggest(n.name, allNames().concat(ALL_WORDS)) || " Если это новая переменная, объяви её: пусть " + n.name + " = …"), n);
    }
    function expr(e) {
      switch (e.k) {
        case "num": case "str": case "bool": case "null": case "raw": return;
        case "tmpl": e.parts.forEach((x) => typeof x !== "string" && expr(x)); return;
        case "this": if (!ctx.cls) err("«это» можно использовать только внутри функций класса", e); return;
        case "super": if (!ctx.cls || !ctx.cls.parent) err("«родитель» можно использовать только в классе, который что-то наследует", e); e.args.forEach(expr); return;
        case "name": resolve(e); return;
        case "list": e.items.forEach(expr); return;
        case "dict": e.entries.forEach((x) => { expr(x.key); expr(x.value); }); return;
        case "bin": expr(e.l); expr(e.r); return;
        case "un": expr(e.e); return;
        case "cond": expr(e.cond); expr(e.a); expr(e.b); return;
        case "index": expr(e.obj); expr(e.index); return;
        case "member": expr(e.obj); if (e.obj.k !== "this" && !privOk(e.name)) err("«" + e.name + "» — скрытое поле класса «" + [...privNames.get(e.name)].join("», «") + "», снаружи класса к нему обращаться нельзя", e); return;
        case "mcall": expr(e.obj); if (e.obj.k !== "this" && !privOk(e.name)) err("«" + e.name + "» — скрытая функция класса «" + [...privNames.get(e.name)].join("», «") + "», снаружи класса её вызывать нельзя", e); e.args.forEach(expr); return;
        case "new": { const info = lookup(e.name); if (!info || info.kind !== "class") err("класс «" + e.name + "» не найден." + suggest(e.name, classNames()), e); markBuiltin(e.name, info); e.args.forEach(expr); return; }
        case "call":
          if (e.fn.k === "this") {
            if (!ctx.cls) err("«это(...)» можно писать только внутри функций класса", e);
            e.thisCtor = true;
            e.args.forEach(expr);
            return;
          }
          if (e.fn.k === "name") { const info = resolve(e.fn); checkArity(info, e, e.args.length, e.fn.name); }
          else expr(e.fn);
          e.args.forEach(expr);
          return;
        case "pipe": {
          expr(e.left);
          e.args.forEach(expr);
          if (e.fn) { expr(e.fn); return; }
          const info = lookup(e.name);
          if (info) { e.mode = info.kind === "class" ? "class" : info.kind === "builtin" ? "builtin" : "user"; if (info.kind === "lazy") e.lazy = true; markBuiltin(e.name, info); checkArity(info, e, e.args.length + 1, e.name); return; }
          e.mode = "method";
          return;
        }
        case "lambda": {
          const saved = { ...ctx };
          ctx.fn = 1; ctx.loop = 0;
          withLabels(e, () => {
            push();
            for (const q of e.params) { checkType(q.type, q); if (q.def) expr(q.def); declare(q.name, { kind: "let", type: q.type }, q); }
            if (e.body) body(e.body); else expr(e.value);
            pop();
          });
          Object.assign(ctx, saved);
          return;
        }
        case "walrus": {
          const info = lookup(e.name);
          if (!info) err("переменная «" + e.name + "» не объявлена. Для «:=» сначала напиши: пусть " + e.name + " = ничего", e);
          if (info.kind === "const") err("нельзя изменить константу «" + e.name + "»", e);
          if (info.kind === "forever") err("нельзя изменить «" + e.name + "»: она объявлена как «пусть навсегда»", e);
          if (info.kind === "lazy") err("нельзя изменить ленивую переменную «" + e.name + "»", e);
          if (info.kind === "func" || info.kind === "builtin" || info.kind === "class" || info.kind === "iface") err("нельзя присвоить значение «" + e.name + "»: это функция или класс", e);
          expr(e.value);
          return;
        }
        case "refof": {
          if (!["name", "index", "member", "deref"].includes(e.target.k)) err("ссылку можно взять только на переменную, элемент списка или поле", e);
          if (e.target.k === "name") {
            const info = lookup(e.target.name);
            if (!info) err("переменная «" + e.target.name + "» не объявлена. Объяви её так: пусть " + e.target.name + " = …", e.target);
            if (info.kind === "const" || info.kind === "forever" || info.kind === "lazy") err("нельзя взять ссылку на константу или неизменяемую переменную «" + e.target.name + "»", e);
            e.target.ref = "user";
          } else expr(e.target);
          return;
        }
        case "deref": expr(e.e); return;
        case "await": expr(e.e); return;
        case "methodref": expr(e.obj); return;
        case "thread": stmt(e); return;
        case "setlit": e.items.forEach(expr); return;
        case "listcomp": case "setcomp": case "dictcomp": {
          expr(e.list);
          push();
          declare(e.name, { kind: "let" }, e);
          if (e.name2) declare(e.name2, { kind: "let" }, e);
          if (e.cond) expr(e.cond);
          if (e.key) expr(e.key);
          expr(e.value);
          pop();
          return;
        }
        case "switchexpr": {
          ctx.switchExpr++;
          expr(e.subject);
          for (const c of e.cases) {
            push();
            if (c.values) c.values.forEach(expr);
            if (c.bind) declare(c.bind, { kind: "let" }, c);
            if (c.guard) expr(c.guard);
            if (c.value) expr(c.value);
            if (c.body) body(c.body);
            pop();
          }
          if (e.def) {
            if (Array.isArray(e.def)) body(e.def);
            else expr(e.def);
          }
          ctx.switchExpr--;
          return;
        }
        case "anonclass": {
          if (e.parent) {
            const pi = lookup(e.parent);
            if (!pi || (pi.kind !== "class" && pi.kind !== "iface")) err("класс или интерфейс «" + e.parent + "» не найден." + suggest(e.parent, classNames()), e);
            markBuiltin(e.parent, pi);
            e.parentKind = pi.kind;
          }
          e.args.forEach(expr);
          const saved = { ...ctx };
          ctx.cls = { name: "анонимный", parent: e.parentKind === "class" ? e.parent : null };
          ctx.method = true;
          ctx.fn = 0;
          push();
          for (const f of e.fields) if (f.value) expr(f.value);
          for (const m of e.methods) func(m, true);
          pop();
          Object.assign(ctx, saved);
          return;
        }
        default: err("непонятное выражение", e);
      }
    }
    push();
    for (const k of Object.keys(BUILTINS)) scopes[0].set(k, { kind: "builtin" });
    for (const k of BUILTIN_CLASSES) scopes[0].set(k, { kind: "class", builtinCls: true });
    push();
    program.globals = [];
    withLabels(program, () => body(program.body));
    program.builtinClasses = [...builtinUsed];
    return program;
  }

  function resolveImports(program, opts, seen, cache) {
    seen = seen || new Set([opts.file || ""]);
    cache = cache || new Map();
    const out = [];
    for (const s of program.body) {
      if (s.k !== "import") { out.push(s); continue; }
      const where = s.file || opts.file;
      if (!opts.resolve) throw new CompileError("подключать файлы здесь нельзя: неизвестно, где их искать", s.line, s.col, where);
      let res;
      try { res = opts.resolve(s.path, where); } catch (e) { throw new CompileError("не удалось подключить файл «" + s.path + "»", s.line, s.col, where); }
      if (!res) throw new CompileError("файл «" + s.path + "» не найден", s.line, s.col, where);
      const name = res.name || s.path;
      let inner;
      if (cache.has(res.id)) inner = cache.get(res.id);
      else if (seen.has(res.id)) inner = { k: "program", body: [] };
      else {
        seen.add(res.id);
        inner = resolveImports(parse(res.source, name, opts.warn), { ...opts, file: name }, seen, cache);
        cache.set(res.id, inner);
      }
      if (s.alias || s.names) out.push({ k: "module", alias: s.alias || null, names: s.names || null, body: inner.body, path: s.path, share: res.id, line: s.line, col: s.col, file: s.file });
      else {
        if (cache.get(res.id + "#flat")) continue;
        cache.set(res.id + "#flat", true);
        out.push(...inner.body);
      }
    }
    return { k: "program", body: out };
  }

  const jsStr = (s) => JSON.stringify(s);
  function jsIdent(n, ascii) {
    if (!ascii) return "$" + n;
    return "$" + n.replace(/[^A-Za-z0-9_]/gu, (c) => "$" + c.codePointAt(0).toString(16).padStart(6, "0"));
  }
  let ASCII_IDS = false;
  const vname = (n) => jsIdent(n, ASCII_IDS);
  const OPS = { "+": "plus", "-": "minus", "*": "mul", "/": "div", "%": "mod", "^": "pow", "==": "eq", "!=": "ne", "<": "lt", ">": "gt", "<=": "le", ">=": "ge", "в": "has" };
  const ASSIGN_OPS = { "+=": "plus", "-=": "minus", "*=": "mul", "/=": "div", "%=": "mod" };

  function generate(program, opts) {
    opts = opts || {};
    ASCII_IDS = !!opts.ascii;
    try { return generateInner(program, opts); } finally { ASCII_IDS = false; }
  }
  function generateInner(program, opts) {
    let out = [];
    let ind = 0;
    let uid = 0;
    const emit = (s) => out.push("  ".repeat(ind) + s);
    const L = (n) => (n.line ? "__R.L = " + n.line + "; " : "");
    const guard = () => { if (opts.guard) emit("__R.g();"); };
    const classRef = (t) => (t && !TYPES.includes(t) ? vname(t) : "null");
    const typed = (v, t, name) => "__R.tp(" + v + ", " + jsStr(t) + ", " + jsStr(name) + ", " + classRef(t) + ")";
    const args = (list) => list.map(expr).join(", ");

    function stmts(list) { for (const s of list) stmt(s); }
    function blockOf(list) { ind++; stmts(list); ind--; }
    function loopBody(list) { ind++; guard(); stmts(list); ind--; }
    let gotoCtx = null;
    const moduleShares = new Map();
    function capture(f) {
      const saved = out, savedInd = ind;
      out = [];
      ind = 0;
      f();
      const lines = out;
      out = saved;
      ind = savedInd;
      return lines;
    }
    function emitGotoBody(list, owner) {
      if (!owner || !owner.hasGoto) { stmts(list); return; }
      const preludeK = new Set(["func", "class", "struct", "enum", "iface", "module"]);
      const prelude = list.filter((s) => preludeK.has(s.k));
      const hoisted = [];
      const seen = new Set();
      const take = (n) => { if (!seen.has(n)) { seen.add(n); hoisted.push(n); } };
      for (const s of list) {
        if (s.k === "let" && s.kind !== "global") take(s.name);
        if (s.k === "multilet") s.names.forEach(take);
      }
      stmts(prelude);
      for (const n of hoisted) emit("let " + vname(n) + " = null;");
      const id = ++uid;
      const lab = "__go" + id, pc = "__pc" + id;
      const labels = new Map();
      let n = 1;
      for (const s of list) if (s.k === "label") { labels.set(s.name, n++); }
      const saved = gotoCtx;
      gotoCtx = { pc, lab, labels };
      emit("let " + pc + " = 0;");
      emit(lab + ": for (;;) { switch (" + pc + ") {");
      emit("case 0:");
      try {
        for (const s of list) {
          if (s.k === "label") { emit("case " + labels.get(s.name) + ":"); continue; }
          if (preludeK.has(s.k)) continue;
          stmt(s);
        }
        emit("break " + lab + ";");
        emit("default: break " + lab + ";");
        emit("} }");
      } finally { gotoCtx = saved; }
    }
    function emitClassLike(s, name) {
      const ctors = (s.methods || []).filter((m) => m.name === "создать" && !m.static && !m.getter && !m.setter && !m.abstract);
      emit("class " + name + (s.parent && s.parentKind !== "iface" ? " extends " + vname(s.parent) : "") + " {");
      ind++;
      if (!(s.parent && s.parentKind !== "iface")) emit("constructor(...a) { this.__init(); if (typeof this[" + jsStr("создать") + "] === \"function\") this[" + jsStr("создать") + "](...a); else if (a.length) __R.fail(\"у класса «\" + this.__cls + \"» нет функции «создать», поэтому ему нельзя передавать значения\"); }");
      emit("__init() {" + (s.parent && s.parentKind !== "iface" ? " super.__init();" : "") + (s.fields || []).filter((f) => !f.static).map((f) => " this[" + jsStr(f.name) + "] = " + (f.value ? expr(f.value) : "null") + ";").join("") + " }");
      emit("get __cls() { return " + jsStr(s.name || "безымянный") + "; }");
      const statics = (s.fields || []).filter((f) => f.static).map((f) => f.name).concat((s.methods || []).filter((m) => m.static).map((m) => m.name));
      if (statics.length) emit("static get __statics() { return (super.__statics || []).concat(" + JSON.stringify(statics) + "); }");
      for (const f of s.fields || []) if (f.static) emit("static [" + jsStr(f.name) + "] = " + (f.value ? expr(f.value) : "null") + ";");
      (s.methods || []).forEach((m) => {
        if (m.abstract) { emit("[" + jsStr(m.name) + "]() { __R.fail(" + jsStr("функция «" + m.name + "» класса «" + (s.name || "безымянный") + "» абстрактная: её нужно написать в классе-наследнике") + "); }"); return; }
        const ctorIdx = ctors.length > 1 ? ctors.indexOf(m) : -1;
        const mname = ctorIdx >= 0 ? "создать#" + ctorIdx : m.name;
        emit((m.static ? "static " : "") + (m.getter ? "get " : m.setter ? "set " : "") + "[" + jsStr(mname) + "](" + paramList(m.params) + ") {");
        ind++; funcBody(m); ind--; emit("}");
      });
      if (ctors.length > 1) {
        emit("[" + jsStr("создать") + "](...a) { return this[__R.pickCtor([" + ctors.map((m, i) => "[" + i + ", " + m.params.filter((q) => !q.def).length + ", " + m.params.length + "]").join(", ") + "], a.length)](...a); }");
      }
      ind--;
      emit("}");
      const ifaces = (s.ifaces || []).slice();
      if (s.parent && s.parentKind === "iface") ifaces.push(s.parent);
      if (ifaces.length) emit("__R.implements(" + name + ", [" + ifaces.map(vname).join(", ") + "]);");
    }
    function stmt(s) {
      switch (s.k) {
        case "let": {
          let v = expr(s.value);
          if (s.type) v = typed(v, s.type, s.name);
          if (s.kind === "forever") v = "__R.forever(" + v + ")";
          if (s.kind === "lazy") {
            emit(L(s) + (gotoCtx ? "" : "const ") + vname(s.name) + " = __R.lazy(() => " + v + ");");
            return;
          }
          if (s.kind === "global" || gotoCtx) { emit(L(s) + vname(s.name) + " = " + v + ";"); return; }
          emit(L(s) + (s.kind === "let" ? "let " : "const ") + vname(s.name) + " = " + v + ";");
          return;
        }
        case "multilet": {
          const t = "__t" + (++uid);
          let v = "__R.unpack(" + expr(s.value) + ", " + s.names.length + ")";
          emit(L(s) + "const " + t + " = " + v + ";");
          const parts = s.names.map((n, i) => vname(n) + " = " + (s.kind === "forever" ? "__R.forever(" + t + "[" + i + "])" : t + "[" + i + "]"));
          emit((gotoCtx ? "" : (s.kind === "let" ? "let " : "const ")) + parts.join(gotoCtx ? "; " : ", ") + ";");
          return;
        }
        case "multiassign": {
          const t = "__t" + (++uid);
          emit(L(s) + "{ const " + t + " = __R.unpack(" + expr(s.value) + ", " + s.parts.length + ");");
          ind++;
          s.parts.forEach((pt, i) => { pt.value.js = t + "[" + i + "]"; stmt(pt); });
          ind--;
          emit("}");
          return;
        }
        case "dowhile": emit(L(s) + "do {"); loopBody(s.body); emit("} while (__R.truth(" + expr(s.cond) + "));"); return;
        case "assert": emit(L(s) + "if (!__R.truth(" + expr(s.cond) + ")) throw __R.userError(" + (s.msg ? expr(s.msg) : jsStr("проверка не прошла" + (s.line ? " (строка " + s.line + ")" : ""))) + ");"); return;
        case "enum": emit(L(s) + "const " + vname(s.name) + " = __R.enumOf([" + s.items.map((it) => "[" + jsStr(it.name) + ", " + (it.value ? expr(it.value) : "undefined") + "]").join(", ") + "]);"); return;
        case "iface": emit(L(s) + "const " + vname(s.name) + " = __R.iface(" + jsStr(s.name) + ", [" + s.methods.map((m) => jsStr(m.name)).join(", ") + "]);"); return;
        case "assign": {
          const t = s.target;
          if (t.k === "name") {
            let v = s.op === "=" ? expr(s.value) : "__R." + ASSIGN_OPS[s.op] + "(" + vname(t.name) + ", " + expr(s.value) + ")";
            if (s.checkType) v = typed(v, s.checkType, t.name);
            emit(L(s) + vname(t.name) + " = " + v + ";");
            return;
          }
          if (t.k === "deref") {
            const r = expr(t.e);
            const rhs = s.value.js || expr(s.value);
            if (s.op === "=") emit(L(s) + "__R.derefSet(" + r + ", " + rhs + ");");
            else emit(L(s) + "{ const __r = " + r + "; __R.derefSet(__r, __R." + ASSIGN_OPS[s.op] + "(__R.deref(__r), " + rhs + ")); }");
            return;
          }
          const id = ++uid, o = "__o" + id, k = "__k" + id;
          const keyCode = t.k === "index" ? expr(t.index) : jsStr(t.name);
          const getter = (t.k === "index" ? "__R.idx(" : "__R.get(") + o + ", " + k + ")";
          const v = s.op === "=" ? expr(s.value) : "__R." + ASSIGN_OPS[s.op] + "(" + getter + ", " + expr(s.value) + ")";
          const setter = (t.k === "index" ? "__R.setIdx(" : "__R.set(") + o + ", " + k + ", " + v + ");";
          emit(L(s) + "{ const " + o + " = " + expr(t.obj) + ", " + k + " = " + keyCode + "; " + (t.safe ? "if (" + o + " != null) " : "") + setter + " }");
          return;
        }
        case "print": emit(L(s) + (s.err ? "__R.eprint([" + args(s.items) + "], " + jsStr(s.err) + ");" : "__R.print([" + args(s.items) + "]);")); return;
        case "expr": emit(L(s) + expr(s.expr) + ";"); return;
        case "if":
          emit(L(s) + "if (__R.truth(" + expr(s.cond) + ")) {");
          blockOf(s.then);
          if (s.otherwise) { emit("} else {"); blockOf(s.otherwise); }
          emit("}");
          return;
        case "while": emit(L(s) + "while (__R.truth(" + expr(s.cond) + ")) {"); loopBody(s.body); emit("}"); return;
        case "repeat": {
          const id = ++uid;
          emit(L(s) + "for (let __i" + id + " = 0, __n" + id + " = __R.count(" + expr(s.count) + "); __i" + id + " < __n" + id + "; __i" + id + "++) {");
          loopBody(s.body); emit("}");
          return;
        }
        case "foreach":
          if (s.name2) emit(L(s) + "for (const [" + vname(s.name) + ", " + vname(s.name2) + "] of __R.pairs(" + expr(s.list) + ")) {");
          else emit(L(s) + "for (const " + vname(s.name) + " of __R.iter(" + expr(s.list) + ")) {");
          loopBody(s.body); emit("}");
          return;
        case "forrange": {
          const id = ++uid, a = "__a" + id, b = "__b" + id, st = "__s" + id, i = "__i" + id;
          emit(L(s) + "for (let " + a + " = __R.num(" + expr(s.from) + ", \"начало цикла\"), " + b + " = __R.num(" + expr(s.to) + ", \"конец цикла\"), " + st + " = " + (s.step ? "__R.step(" + expr(s.step) + ")" : "(" + a + " <= " + b + " ? 1 : -1)") + ", " + i + " = " + a + "; " + st + " > 0 ? " + i + " <= " + b + " : " + i + " >= " + b + "; " + i + " += " + st + ") {");
          ind++; emit("let " + vname(s.name) + " = " + i + ";"); ind--;
          loopBody(s.body); emit("}");
          return;
        }
        case "func":
          emit(L(s) + "function " + vname(s.name) + "(" + paramList(s.params) + ") {");
          ind++; funcBody(s); ind--; emit("}");
          return;
        case "return": emit(L(s) + "return " + (s.value ? expr(s.value) : "null") + ";"); return;
        case "break": emit(L(s) + "break;"); return;
        case "continue": emit(L(s) + "continue;"); return;
        case "throw": emit(L(s) + "throw __R.userError(" + expr(s.value) + ");"); return;
        case "try": {
          const e = "__err" + (++uid);
          emit(L(s) + "try {");
          blockOf(s.body);
          const hs = s.handlers || (s.handler ? [{ type: null, errName: s.errName, body: s.handler }] : []);
          if (hs.length === 1 && !hs[0].type) {
            emit("} catch (" + e + ") {");
            ind++;
            emit("__R.rethrowFatal(" + e + ");");
            if (hs[0].errName) emit("let " + vname(hs[0].errName) + " = __R.errValue(" + e + ");");
            stmts(hs[0].body);
            ind--;
          } else if (hs.length) {
            const ev = "__ev" + uid;
            emit("} catch (" + e + ") {");
            ind++;
            emit("__R.rethrowFatal(" + e + ");");
            emit("const " + ev + " = __R.errValue(" + e + ");");
            hs.forEach((h, i) => {
              const cond = h.type ? "__R.isa(" + ev + ", " + jsStr(h.type) + ", " + classRef(h.type) + ")" : "true";
              emit((i ? "} else " : "") + "if (" + cond + ") {");
              ind++;
              if (h.errName) emit("let " + vname(h.errName) + " = " + ev + ";");
              stmts(h.body);
              ind--;
            });
            emit("} else throw " + e + ";");
            ind--;
          }
          if (s.fin) { emit("} finally {"); blockOf(s.fin); }
          emit("}");
          return;
        }
        case "switch": {
          const id = ++uid, lab = "__sw" + id, sv = "__v" + id;
          emit(L(s) + lab + ": { const " + sv + " = " + expr(s.subject) + ";");
          ind++;
          for (const c of s.cases) {
            if (c.bind) {
              emit("{ let " + vname(c.bind) + " = " + sv + ";");
              ind++;
              emit("if (" + (c.guard ? "__R.truth(" + expr(c.guard) + ")" : "true") + ") {");
            } else {
              const cond = c.values.map((v) => "__R.eq(" + sv + ", " + expr(v) + ")").join(" || ");
              emit("if ((" + cond + ")" + (c.guard ? " && __R.truth(" + expr(c.guard) + ")" : "") + ") {");
            }
            ind++; stmts(c.body); emit("break " + lab + ";"); ind--;
            emit("}");
            if (c.bind) { ind--; emit("}"); }
          }
          if (s.def) stmts(s.def);
          ind--;
          emit("}");
          return;
        }
        case "class": {
          emit(L(s));
          emitClassLike(s, vname(s.name));
          return;
        }
        case "label": return;
        case "goto": {
          if (!gotoCtx || !gotoCtx.labels.has(s.name)) emit(L(s) + "__R.fail(" + jsStr("нет метки «" + s.name + "»") + ");");
          else emit(L(s) + gotoCtx.pc + " = " + gotoCtx.labels.get(s.name) + "; continue " + gotoCtx.lab + ";");
          return;
        }
        case "thread":
          emit(L(s) + expr(s) + ";");
          return;
        case "with": {
          const id = ++uid, m = "__m" + id;
          emit(L(s) + "{ const " + m + " = " + expr(s.value) + "; let " + vname(s.name) + "; try { " + vname(s.name) + " = __R.enter(" + m + ");");
          blockOf(s.body);
          emit("} finally { __R.exit(" + m + "); } }");
          return;
        }
        case "module": {
          let tmp = s.share && moduleShares.has(s.share) ? moduleShares.get(s.share) : null;
          if (!tmp) {
            tmp = s.alias ? vname(s.alias) : "__mod" + (++uid);
            const exp = (s.exports || []).map((n) => "[" + jsStr(n) + ", " + vname(n) + "]").join(", ");
            emit(L(s) + "const " + tmp + " = (() => {");
            ind++;
            emitGotoBody(s.body, s);
            emit("return __R.dict([" + exp + "]);");
            ind--;
            emit("})();");
            if (s.share) moduleShares.set(s.share, tmp);
          } else if (s.alias) emit(L(s) + "const " + vname(s.alias) + " = " + tmp + ";");
          if (s.names) {
            for (const n of s.names) emit("let " + vname(n.as) + " = __R.get(" + tmp + ", " + jsStr(n.name) + ");");
          }
          return;
        }
        case "struct":
          emit(L(s) + "class " + vname(s.name) + " {");
          ind++;
          emit("constructor(" + paramList(s.params) + ") { " + s.params.map((q) => (q.def ? "" : "if (" + vname(q.name) + " === undefined) __R.missing(" + jsStr(q.name) + "); ") + "this[" + jsStr(q.name) + "] = " + (q.type ? typed(vname(q.name), q.type, q.name) : vname(q.name)) + ";").join(" ") + " }");
          emit("get __cls() { return " + jsStr(s.name) + "; }");
          ind--;
          emit("}");
          return;
      }
    }
    function paramList(ps) { return ps.map((q) => vname(q.name) + (q.def ? " = " + expr(q.def) : "")).join(", "); }
    function funcBody(f) {
      for (const q of f.params) {
        if (!q.def) emit("if (" + vname(q.name) + " === undefined) __R.missing(" + jsStr(q.name) + ");");
        if (q.type) emit(vname(q.name) + " = " + typed(vname(q.name), q.type, q.name) + ";");
      }
      const runTyped = () => {
        if (f.ret) {
          const inner = "__f" + (++uid);
          emit("const " + inner + " = () => {");
          ind++; emitGotoBody(f.body, f); emit("return null;"); ind--;
          emit("};");
          emit("return " + typed(inner + "()", f.ret, "результат функции «" + (f.name || "") + "»") + ";");
        } else { emitGotoBody(f.body, f); if (!f.body.length || f.body[f.body.length - 1].k !== "return") emit("return null;"); }
      };
      if (f.async) {
        emit("return __R.task(() => {");
        ind++; runTyped(); ind--;
        emit("});");
      } else runTyped();
    }
    function expr(e) {
      switch (e.k) {
        case "num": return String(e.v);
        case "str": return jsStr(e.v);
        case "tmpl": return "__R.tmpl([" + e.parts.map((x) => (typeof x === "string" ? jsStr(x) : expr(x))).join(", ") + "])";
        case "bool": return e.v ? "true" : "false";
        case "null": return "null";
        case "this": return "this";
        case "super": return "super[" + jsStr(e.name) + "](" + args(e.args) + ")";
        case "name": return e.ref === "builtin" ? "__R.b[" + jsStr(e.name) + "]" : e.lazy ? vname(e.name) + "()" : vname(e.name);
        case "raw": return e.js;
        case "list": return "[" + args(e.items) + "]";
        case "dict": return "__R.dict([" + e.entries.map((x) => "[" + expr(x.key) + ", " + expr(x.value) + "]").join(", ") + "])";
        case "bin":
          if (e.op === "и") return "__R.and(" + expr(e.l) + ", () => " + expr(e.r) + ")";
          if (e.op === "или") return "__R.or(" + expr(e.l) + ", () => " + expr(e.r) + ")";
          return "__R." + OPS[e.op] + "(" + expr(e.l) + ", " + expr(e.r) + ")";
        case "un": return e.op === "не" ? "!__R.truth(" + expr(e.e) + ")" : "__R.neg(" + expr(e.e) + ")";
        case "cond": return "(__R.truth(" + expr(e.cond) + ") ? " + expr(e.a) + " : " + expr(e.b) + ")";
        case "index": return (e.safe ? "__R.sidx(" : "__R.idx(") + expr(e.obj) + ", " + expr(e.index) + ")";
        case "member": return (e.safe ? "__R.sget(" : "__R.get(") + expr(e.obj) + ", " + jsStr(e.name) + ")";
        case "mcall": return (e.safe ? "__R.scall(" : "__R.call(") + expr(e.obj) + ", " + jsStr(e.name) + ", [" + args(e.args) + "])";
        case "new": return "new " + vname(e.name) + "(" + args(e.args) + ")";
        case "call":
          if (e.thisCtor || e.fn.k === "this") return "this[" + jsStr("создать") + "](" + args(e.args) + ")";
          if (e.fn.k === "name" && e.fn.ref === "class") return "new " + vname(e.fn.name) + "(" + args(e.args) + ")";
          if (e.fn.k === "name" && e.fn.ref === "builtin") return "__R.b[" + jsStr(e.fn.name) + "](" + args(e.args) + ")";
          if (e.fn.k === "name") return "__R.fn(" + vname(e.fn.name) + (e.fn.lazy ? "()" : "") + ", " + jsStr(e.fn.name) + ")(" + args(e.args) + ")";
          if (e.safe) return "__R.sapply(" + expr(e.fn) + ", [" + args(e.args) + "])";
          return "__R.fn(" + expr(e.fn) + ", \"значение\")(" + args(e.args) + ")";
        case "pipe": {
          const all = [expr(e.left)].concat(e.args.map(expr)).join(", ");
          if (e.fn) return "__R.fn(" + expr(e.fn) + ", \"значение\")(" + all + ")";
          if (e.mode === "builtin") return "__R.b[" + jsStr(e.name) + "](" + all + ")";
          if (e.mode === "class") return "new " + vname(e.name) + "(" + all + ")";
          if (e.mode === "user") return "__R.fn(" + vname(e.name) + (e.lazy ? "()" : "") + ", " + jsStr(e.name) + ")(" + all + ")";
          return "__R.call(" + expr(e.left) + ", " + jsStr(e.name) + ", [" + args(e.args) + "])";
        }
        case "lambda": {
          const lines = capture(() => funcBody({ params: e.params, body: e.body || [{ k: "return", value: e.value }], ret: null, hasGoto: e.hasGoto }));
          return "((" + paramList(e.params) + ") => { " + lines.map((x) => x.trim()).join(" ") + " })";
        }
        case "walrus": return "(" + vname(e.name) + " = " + expr(e.value) + ")";
        case "refof": {
          const t = e.target;
          if (t.k === "deref") return expr(t.e);
          if (t.k === "name") return "__R.ref(() => " + vname(t.name) + ", (__v) => { " + vname(t.name) + " = __v; })";
          if (t.k === "index") return "(() => { const __o = " + expr(t.obj) + ", __k = " + expr(t.index) + "; return __R.ref(() => __R.idx(__o, __k), (__v) => { __R.setIdx(__o, __k, __v); }); })()";
          if (t.k === "member") return "(() => { const __o = " + expr(t.obj) + "; return __R.ref(() => __R.get(__o, " + jsStr(t.name) + "), (__v) => { __R.set(__o, " + jsStr(t.name) + ", __v); }); })()";
          throw new CompileError("ссылку можно взять только на переменную, элемент списка или поле", e.line, e.col);
        }
        case "deref": return "__R.deref(" + expr(e.e) + ")";
        case "await": return "__R.await(" + expr(e.e) + ")";
        case "methodref": return "__R.methodRef(" + expr(e.obj) + ", " + jsStr(e.name) + ")";
        case "thread": {
          const lines = capture(() => { emitGotoBody(e.body, e); emit("return null;"); });
          return "__R.task(() => { " + lines.map((x) => x.trim()).join(" ") + " })";
        }
        case "setlit": return "new Set([" + args(e.items) + "])";
        case "listcomp": case "setcomp": case "dictcomp": {
          const pair = !!e.name2;
          const src = (pair ? "__R.pairs(" : "__R.iter(") + expr(e.list) + ")";
          const bind = pair ? "([" + vname(e.name) + ", " + vname(e.name2) + "])" : "(" + vname(e.name) + ")";
          const cond = e.cond ? "if (!__R.truth(" + expr(e.cond) + ")) return []; " : "";
          if (e.k === "dictcomp") return "__R.dict(" + src + ".flatMap(" + bind + " => { " + cond + "return [[" + expr(e.key) + ", " + expr(e.value) + "]]; }))";
          const mapped = src + ".flatMap(" + bind + " => { " + cond + "return [" + expr(e.value) + "]; })";
          return e.k === "setcomp" ? "new Set(" + mapped + ")" : mapped;
        }
        case "switchexpr": {
          const id = ++uid, sv = "__v" + id;
          const lines = capture(() => {
            emit("const " + sv + " = " + expr(e.subject) + ";");
            for (const c of e.cases) {
              if (c.bind) {
                emit("{ let " + vname(c.bind) + " = " + sv + ";");
                emit("if (" + (c.guard ? "__R.truth(" + expr(c.guard) + ")" : "true") + ") {");
              } else {
                const cond = (c.values || []).map((v) => "__R.eq(" + sv + ", " + expr(v) + ")").join(" || ") || "false";
                emit("if ((" + cond + ")" + (c.guard ? " && __R.truth(" + expr(c.guard) + ")" : "") + ") {");
              }
              if (c.value) emit("return " + expr(c.value) + ";");
              else { stmts(c.body || []); emit("return null;"); }
              emit("}");
              if (c.bind) emit("}");
            }
            if (e.def) {
              if (Array.isArray(e.def)) { stmts(e.def); emit("return null;"); }
              else emit("return " + expr(e.def) + ";");
            }
            emit("return null;");
          });
          return "(() => { " + lines.map((x) => x.trim()).join(" ") + " })()";
        }
        case "anonclass": {
          const id = ++uid, cn = "__A" + id;
          const lines = capture(() => { emitClassLike(e, cn); });
          return "(() => { " + lines.map((x) => x.trim()).join(" ") + " return new " + cn + "(" + args(e.args) + "); })()";
        }
      }
      throw new CompileError("не умею переводить это выражение", e.line, e.col);
    }
    for (const b of program.builtinClasses || []) emit("const " + vname(b) + " = __R.builtinClass(" + jsStr(b) + ");");
    for (const g of program.globals || []) emit("let " + vname(g) + " = null;");
    emitGotoBody(program.body, program);
    return out.join("\n");
  }

  function frontend(src, opts) {
    opts = opts || {};
    const program = resolveImports(parse(src, opts.file, opts.warn), opts);
    analyze(program, opts);
    return program;
  }

  function compile(src, opts) {
    return generate(frontend(src, opts), opts || {});
  }

  function makeRuntime(io) {
    class PonError extends Error {
      constructor(message, value) { super(message); this.ponValue = value === undefined ? message : value; this.isPon = true; }
    }
    const R = { L: 0, steps: 0 };
    const frozenMaps = new WeakSet();
    const fail = (msg) => { throw new PonError(msg); };
    R.fail = fail;
    const typeName = (v) => {
      if (v === null || v === undefined) return "ничего";
      if (typeof v === "number") return "число";
      if (typeof v === "string") return "строка";
      if (typeof v === "boolean") return "логическое";
      if (Array.isArray(v)) return "список";
      if (v instanceof Map) return "словарь";
      if (v instanceof Set) return "множество";
      if (v && v.__iface) return "интерфейс";
      if (typeof v === "function") return "функция";
      if (v && v.__cls) return v.__cls;
      return "значение";
    };
    const fmtNum = (n) => {
      if (Number.isNaN(n)) return "не число";
      if (n === Infinity) return "бесконечность";
      if (n === -Infinity) return "-бесконечность";
      return String(Math.round(n * 1e10) / 1e10).replace(".", ",");
    };
    const isObj = (o) => !!(o && typeof o === "object" && !Array.isArray(o) && !(o instanceof Map) && o.__cls);
    const show = (v, nested) => {
      if (v === null || v === undefined) return "ничего";
      if (v === true) return "истина";
      if (v === false) return "ложь";
      if (typeof v === "number") return fmtNum(v);
      if (typeof v === "string") return nested ? "\"" + v + "\"" : v;
      if (Array.isArray(v)) return "[" + v.map((x) => show(x, true)).join(", ") + "]";
      if (v instanceof Map) return "{" + [...v].map(([k, x]) => show(k, true) + ": " + show(x, true)).join(", ") + "}";
      if (v instanceof Set) return "множество{" + [...v].map((x) => show(x, true)).join(", ") + "}";
      if (v && v.__iface) return "интерфейс " + v.name;
      if (typeof v === "function") return "функция";
      if (isObj(v)) {
        if (typeof v["строкой"] === "function") return show(v["строкой"]());
        const keys = Object.keys(v).filter((k) => !k.startsWith("__"));
        return v.__cls + "(" + keys.map((k) => k + ": " + show(v[k], true)).join(", ") + ")";
      }
      return String(v);
    };
    R.show = show;
    R.typeName = typeName;
    R.print = (items) => io.write(items.map((x) => show(x)).join(" "));
    R.eprint = (items, kind) => {
      const text = (kind === "debug" ? "[отладка] " : "") + items.map((x) => show(x)).join(" ");
      if (io.writeErr) io.writeErr(text); else io.write((kind === "debug" ? "" : "⚠ ") + text);
    };
    R.lazy = (f) => { let done = false, val; return () => { if (!done) { val = f(); done = true; } return val; }; };
    R.tmpl = (parts) => parts.map((x) => show(x)).join("");
    R.truth = (v) => !(v === false || v === null || v === undefined || v === 0 || v === "");
    R.or = (a, fb) => (R.truth(a) ? a : fb());
    R.and = (a, fb) => R.truth(a) && R.truth(fb());
    R.g = () => { if (++R.steps > (io.maxSteps || 5e6)) { const e = new PonError("программа работает слишком долго — возможно, цикл никогда не заканчивается"); e.fatal = true; throw e; } };
    R.num = (v, what) => { if (typeof v !== "number") fail((what || "значение") + " должно быть числом, а получено: " + typeName(v)); return v; };
    R.count = (v) => Math.floor(R.num(v, "количество повторов"));
    R.step = (v) => { R.num(v, "шаг цикла"); if (v === 0) fail("шаг цикла не может быть нулём"); return v; };
    const opName = { plus: "сложить", minus: "вычесть", mul: "умножить", div: "разделить", mod: "найти остаток от деления", pow: "возвести в степень" };
    const arith = (name, f) => (a, b) => {
      if (typeof a !== "number" || typeof b !== "number") fail("нельзя " + opName[name] + " «" + typeName(a) + "» и «" + typeName(b) + "»");
      return f(a, b);
    };
    const MAX_STR = 1e8;
    const capStr = (n) => { if (n > MAX_STR) fail("слишком длинная строка (больше 100 миллионов символов)"); };
    const addNums = arith("plus", (x, y) => x + y);
    R.plus = (a, b) => {
      if (typeof a === "string" || typeof b === "string") { const x = show(a), y = show(b); capStr(x.length + y.length); return x + y; }
      if (Array.isArray(a) && Array.isArray(b)) return a.concat(b);
      return addNums(a, b);
    };
    R.minus = arith("minus", (a, b) => a - b);
    const mulNums = arith("mul", (x, y) => x * y);
    const rep = (a, b) => { const n = Math.max(0, Math.floor(b)); capStr(a.length * n); return a.repeat(n); };
    const repList = (a, b) => { const n = Math.max(0, Math.floor(b)); if (a.length * n > 1e7) fail("слишком длинный список (больше 10 миллионов элементов)"); const r = []; for (let i = 0; i < n; i++) for (const x of a) r.push(x); return r; };
    R.mul = (a, b) => (typeof a === "string" && typeof b === "number" ? rep(a, b) : Array.isArray(a) && typeof b === "number" ? repList(a, b) : mulNums(a, b));
    R.div = arith("div", (a, b) => { if (b === 0) fail("делить на ноль нельзя"); return a / b; });
    R.mod = arith("mod", (a, b) => { if (b === 0) fail("делить на ноль нельзя"); return ((a % b) + b) % b; });
    R.pow = arith("pow", (a, b) => Math.pow(a, b));
    R.neg = (a) => -R.num(a, "значение после минуса");
    const eq = (a, b) => {
      if (a === b) return true;
      if (a == null || b == null) return a == null && b == null;
      if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((x, i) => eq(x, b[i]));
      if (a instanceof Map && b instanceof Map) return a.size === b.size && [...a].every(([k, v]) => b.has(k) && eq(v, b.get(k)));
      if (a instanceof Set && b instanceof Set) return a.size === b.size && [...a].every((x) => b.has(x));
      return false;
    };
    R.eq = eq;
    R.ne = (a, b) => !eq(a, b);
    const cmp = (sym, f) => (a, b) => {
      if (typeof a === "number" && typeof b === "number") return f(a, b);
      if (typeof a === "string" && typeof b === "string") return f(a < b ? -1 : a > b ? 1 : 0, 0);
      fail("нельзя сравнить «" + typeName(a) + "» и «" + typeName(b) + "» знаком «" + sym + "»");
    };
    R.lt = cmp("<", (a, b) => a < b);
    R.gt = cmp(">", (a, b) => a > b);
    R.le = cmp("<=", (a, b) => a <= b);
    R.ge = cmp(">=", (a, b) => a >= b);
    R.has = (x, c) => {
      if (typeof c === "string") return c.includes(show(x));
      if (Array.isArray(c)) return c.some((y) => eq(x, y));
      if (c instanceof Map || c instanceof Set) return c.has(x);
      fail("проверять «в» можно только для строки, списка или словаря, а не для «" + typeName(c) + "»");
    };
    const checkKey = (k) => { if (typeof k !== "string" && typeof k !== "number" && typeof k !== "boolean") fail("ключом словаря может быть строка, число или логическое значение, а не «" + typeName(k) + "»"); return k; };
    R.dict = (entries) => { const m = new Map(); for (const [k, v] of entries) m.set(checkKey(k), v); return m; };
    const isFrozen = (o) => (o instanceof Map || o instanceof Set ? frozenMaps.has(o) : Object.isFrozen(o));
    const mutate = (o) => { if (isFrozen(o)) fail("это значение объявлено «навсегда», его нельзя менять"); };
    R.forever = (v) => {
      if (Array.isArray(v)) { v.forEach(R.forever); Object.freeze(v); }
      else if (v instanceof Map) { v.forEach(R.forever); frozenMaps.add(v); }
      else if (v instanceof Set) frozenMaps.add(v);
      else if (isObj(v)) { Object.values(v).forEach(R.forever); Object.freeze(v); }
      return v;
    };
    const index = (o, i) => {
      if (!Number.isInteger(i)) fail("номер элемента должен быть целым числом, а получено: " + show(i, true));
      const n = o.length;
      const j = i < 0 ? n + i : i;
      if (j < 0 || j >= n) fail("в " + (typeof o === "string" ? "строке" : "списке") + " нет элемента с номером " + i + " (элементов: " + n + ", нумерация с 0)");
      return j;
    };
    R.idx = (o, i) => {
      if (Array.isArray(o) || typeof o === "string") return o[index(o, i)];
      if (o instanceof Map) { if (!o.has(i)) fail("в словаре нет ключа " + show(i, true)); return o.get(i); }
      if (o == null) fail("нельзя взять элемент у «ничего». Подсказка: используй «?.[ ]»");
      fail("квадратные скобки можно использовать только со списком, строкой или словарём, а не с «" + typeName(o) + "»");
    };
    R.sidx = (o, i) => (o == null ? null : o instanceof Map ? (o.has(i) ? o.get(i) : null) : R.idx(o, i));
    R.setIdx = (o, i, v) => {
      if (Array.isArray(o)) { mutate(o); o[index(o, i)] = v; return; }
      if (o instanceof Map) { mutate(o); o.set(checkKey(i), v); return; }
      if (typeof o === "string") fail("строку нельзя менять по буквам — создай новую строку");
      fail("квадратные скобки можно использовать только со списком или словарём");
    };
    const lev = (a, b) => { const d = [...Array(a.length + 1)].map((_, i) => [i]); for (let j = 1; j <= b.length; j++) d[0][j] = j; for (let i = 1; i <= a.length; i++) for (let j = 1; j <= b.length; j++) d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)); return d[a.length][b.length]; };
    const hint = (w, names) => { let best = null, bd = 3; for (const n of names) { const d = lev(w, n); if (d < bd) { bd = d; best = n; } } return best ? " Возможно, вы имели в виду «" + best + "»?" : ""; };
    const own = (t, k) => Object.prototype.hasOwnProperty.call(t, k);
    const BAD_KEY = /^__/;
    const INTERNAL = new Set(["constructor", "prototype", "__proto__", "caller", "callee", "arguments"]);
    const hasMember = (o, k) => {
      if (typeof k !== "string" || BAD_KEY.test(k) || INTERNAL.has(k)) return false;
      if (own(o, k)) return true;
      let p = Object.getPrototypeOf(o);
      while (p && p !== Object.prototype) { if (own(p, k)) return true; p = Object.getPrototypeOf(p); }
      return false;
    };
    const objKeys = (o) => { const s = new Set(Object.keys(o).filter((k) => !k.startsWith("__"))); let p = Object.getPrototypeOf(o); while (p && p !== Object.prototype) { Object.getOwnPropertyNames(p).forEach((k) => k !== "constructor" && !k.startsWith("__") && s.add(k)); p = Object.getPrototypeOf(p); } return [...s]; };
    const isCls = (o) => typeof o === "function" && o.prototype && Object.prototype.hasOwnProperty.call(o.prototype, "__cls");
    const statics = (o) => { const st = o.__statics; return Array.isArray(st) ? st : []; };
    R.get = (o, k) => {
      if (o == null) fail("нельзя взять «" + k + "» у «ничего». Подсказка: используй «?.», чтобы безопасно пропустить");
      if (isCls(o)) { if (statics(o).includes(k)) { const v = o[k]; return typeof v === "function" ? v.bind(o) : v; } fail("у класса «" + o.prototype.__cls + "» нет общего поля или общей функции «" + k + "»." + hint(k, statics(o))); }
      if (k === "длина" && (typeof o === "string" || Array.isArray(o))) return o.length;
      if (k === "длина" && (o instanceof Map || o instanceof Set)) return o.size;
      if (o instanceof Map) { if (!o.has(k)) fail("в словаре нет ключа «" + k + "»." + hint(k, [...o.keys()].filter((x) => typeof x === "string"))); return o.get(k); }
      if (isObj(o)) {
        if (hasMember(o, k)) { const v = o[k]; return typeof v === "function" ? v.bind(o) : v; }
        fail("у «" + o.__cls + "» нет поля «" + k + "»." + hint(k, objKeys(o)));
      }
      const mt = methodsFor(o);
      const m = own(mt, k) ? mt[k] : null;
      if (m) return (...a) => m(o, ...a);
      fail("у значения типа «" + typeName(o) + "» нет поля «" + k + "»");
    };
    R.sget = (o, k) => {
      if (o == null) return null;
      if (o instanceof Map) return o.has(k) ? o.get(k) : null;
      if (isObj(o) && !hasMember(o, k)) return null;
      return R.get(o, k);
    };
    R.set = (o, k, v) => {
      if (o instanceof Map) { mutate(o); o.set(k, v); return; }
      if (isCls(o)) { if (!statics(o).includes(k) || typeof o[k] === "function") fail("у класса «" + o.prototype.__cls + "» нет общего поля «" + k + "»"); o[k] = v; return; }
      if (isObj(o) || (o && o.__ref)) {
        if (typeof k !== "string" || BAD_KEY.test(k) || INTERNAL.has(k)) fail("«" + k + "» — внутреннее имя, его нельзя менять");
        mutate(o);
        let desc, p0 = o;
        while (p0 && p0 !== Object.prototype) {
          if (own(p0, k)) { desc = Object.getOwnPropertyDescriptor(p0, k); break; }
          p0 = Object.getPrototypeOf(p0);
        }
        if (desc && desc.get && !desc.set) fail("свойство «" + k + "» только для чтения");
        if (desc && typeof desc.set === "function") { o[k] = v; return; }
        Object.defineProperty(o, k, { value: v, writable: true, enumerable: true, configurable: true });
        return;
      }
      if (o == null) fail("нельзя записать «" + k + "» в «ничего». Подсказка: «?.» пропустит запись, если там ничего");
      fail("нельзя добавить поле «" + k + "» значению типа «" + typeName(o) + "»");
    };
    const needFn = (f, what) => { if (typeof f !== "function") fail("«" + what + "» должно быть функцией, а получено: " + typeName(f)); return f; };
    const needNum = (v, what) => R.num(v, what);
    const needStr = (v, what) => { if (typeof v !== "string") fail((what || "значение") + " должно быть строкой, а получено: " + typeName(v)); return v; };
    const strM = {
      "большими": (s) => s.toUpperCase(), "заглавными": (s) => s.toUpperCase(), "маленькими": (s) => s.toLowerCase(), "строчными": (s) => s.toLowerCase(),
      "обрезать": (s) => s.trim(), "содержит": (s, x) => s.includes(needStr(x, "что искать")), "заменить": (s, a, b) => s.split(needStr(a, "что заменить")).join(show(b)),
      "разделить": (s, sep) => (sep === undefined || sep === null ? s.split(/\s+/).filter(Boolean) : s.split(needStr(sep, "разделитель"))),
      "начинается": (s, x) => s.startsWith(needStr(x)), "заканчивается": (s, x) => s.endsWith(needStr(x)),
      "повтори": (s, n) => rep(s, needNum(n, "количество")), "срез": (s, a, b) => s.slice(needNum(a, "начало"), b === undefined ? undefined : needNum(b, "конец")),
      "найди": (s, x) => s.indexOf(needStr(x)), "символы": (s) => [...s], "длина": (s) => s.length, "число": (s) => R.b["число"](s),
      "с_заглавной": (s) => s.charAt(0).toUpperCase() + s.slice(1), "перевёрнуто": (s) => [...s].reverse().join(""),
      "код": (s) => (s.length ? s.codePointAt(0) : fail("у пустой строки нет кода символа")), "найди_последний": (s, x) => s.lastIndexOf(needStr(x)),
      "обрезать_слева": (s) => s.trimStart(), "обрезать_справа": (s) => s.trimEnd(),
      "совпадает": (s, p) => regex(p, "u").test(s), "найди_все": (s, p) => [...s.matchAll(regex(p, "gu"))].map((m) => m[0]),
      "заменить_шаблон": (s, p, b) => s.replace(regex(p, "gu"), show(b)),
    };
    const sortFn = (f) => (f ? (a, b) => needNum(needFn(f, "сравнение")(a, b), "результат сравнения") : (a, b) => (typeof a === "number" && typeof b === "number" ? a - b : show(a) < show(b) ? -1 : show(a) > show(b) ? 1 : 0));
    const listM = {
      "добавь": (l, x) => { mutate(l); l.push(x); return l; }, "вставь": (l, i, x) => { mutate(l); l.splice(needNum(i, "номер"), 0, x); return l; },
      "убери": (l, i) => { mutate(l); if (!l.length) fail("нельзя убрать элемент из пустого списка"); return l.splice(index(l, i === undefined ? -1 : i), 1)[0]; }, "длина": (l) => l.length,
      "содержит": (l, x) => l.some((y) => eq(x, y)), "найди": (l, x) => l.findIndex((y) => eq(x, y)),
      "соедини": (l, sep) => l.map((x) => show(x)).join(sep === undefined ? "" : show(sep)),
      "сортируй": (l, f) => { mutate(l); return l.sort(sortFn(f)); }, "отсортировано": (l, f) => l.slice().sort(sortFn(f)),
      "обрати": (l) => { mutate(l); return l.reverse(); }, "срез": (l, a, b) => l.slice(needNum(a, "начало"), b === undefined ? undefined : needNum(b, "конец")),
      "отфильтруй": (l, f) => l.filter((x) => R.truth(needFn(f, "условие")(x))), "преобразуй": (l, f) => l.map((x) => needFn(f, "преобразование")(x)),
      "свернуть": (l, f, start) => (start === undefined ? l.reduce((a, x) => needFn(f, "функция")(a, x)) : l.reduce((a, x) => needFn(f, "функция")(a, x), start)),
      "для_каждого": (l, f) => { l.forEach((x) => needFn(f, "функция")(x)); return null; },
      "сумма": (l) => l.reduce((a, x) => R.plus(a, x), 0), "первый": (l) => (l.length ? l[0] : null), "последний": (l) => (l.length ? l[l.length - 1] : null),
      "копия": (l) => l.slice(), "пусто": (l) => l.length === 0, "очисти": (l) => { mutate(l); l.length = 0; return l; },
      "любой": (l, f) => l.some((x) => R.truth(needFn(f, "условие")(x))), "все": (l, f) => l.every((x) => R.truth(needFn(f, "условие")(x))),
      "найди_где": (l, f) => { const r = l.find((x) => R.truth(needFn(f, "условие")(x))); return r === undefined ? null : r; },
      "номер_где": (l, f) => l.findIndex((x) => R.truth(needFn(f, "условие")(x))),
      "уникальные": (l) => l.filter((x, i) => l.findIndex((y) => eq(x, y)) === i), "перевёрнуто": (l) => l.slice().reverse(),
      "множество": (l) => new Set(l),
    };
    const setM = {
      "добавь": (st, x) => { mutate(st); st.add(x); return st; }, "убери": (st, x) => { mutate(st); return st.delete(x); }, "содержит": (st, x) => st.has(x), "есть": (st, x) => st.has(x),
      "длина": (st) => st.size, "пусто": (st) => st.size === 0, "очисти": (st) => { mutate(st); st.clear(); return st; }, "копия": (st) => new Set(st), "список": (st) => [...st],
      "объедини": (st, o) => new Set([...st, ...R.iter(o)]), "пересечение": (st, o) => { const b = new Set(R.iter(o)); return new Set([...st].filter((x) => b.has(x))); },
      "разность": (st, o) => { const b = new Set(R.iter(o)); return new Set([...st].filter((x) => !b.has(x))); }, "подмножество": (st, o) => { const b = new Set(R.iter(o)); return [...st].every((x) => b.has(x)); },
      "для_каждого": (st, f) => { st.forEach((x) => needFn(f, "функция")(x)); return null; },
    };
    const dictM = {
      "ключи": (d) => [...d.keys()], "значения": (d) => [...d.values()], "пары": (d) => [...d].map(([k, v]) => [k, v]),
      "есть": (d, k) => d.has(k), "убери": (d, k) => { mutate(d); const v = d.get(k); d.delete(k); return v === undefined ? null : v; },
      "получи": (d, k, def) => (d.has(k) ? d.get(k) : def === undefined ? null : def), "длина": (d) => d.size, "копия": (d) => new Map(d),
      "пусто": (d) => d.size === 0, "очисти": (d) => { mutate(d); d.clear(); return d; },
      "объедини": (d, o) => { if (!(o instanceof Map)) fail("объединять словарь можно только со словарём"); return new Map([...d, ...o]); },
      "для_каждого": (d, f) => { d.forEach((v, k) => needFn(f, "функция")(k, v)); return null; },
    };
    const regex = (p, flags) => { needStr(p, "шаблон"); try { return new RegExp(p, flags); } catch (e) { fail("неправильный шаблон «" + p + "»"); } };
    const numM = { "округли": (n, k) => R.b["округли"](n, k), "целое": (n) => Math.trunc(n), "строкой": (n) => show(n), "модуль": (n) => Math.abs(n) };
    const methodsFor = (o) => (typeof o === "string" ? strM : Array.isArray(o) ? listM : o instanceof Map ? dictM : o instanceof Set ? setM : typeof o === "number" ? numM : {});
    R.call = (o, k, args) => {
      if (o == null) fail("нельзя вызвать «" + k + "» у «ничего». Подсказка: используй «?.»");
      if (isCls(o)) { if (statics(o).includes(k) && typeof o[k] === "function") return o[k](...args); fail("у класса «" + o.prototype.__cls + "» нет общей функции «" + k + "»." + hint(k, statics(o))); }
      if (isObj(o)) {
        if (hasMember(o, k) && typeof o[k] === "function") return o[k](...args);
        if (hasMember(o, k)) fail("«" + k + "» у «" + o.__cls + "» — это поле, а не функция");
        fail("у «" + o.__cls + "» нет функции «" + k + "»." + hint(k, objKeys(o)));
      }
      if (o instanceof Map && o.has(k) && typeof o.get(k) === "function") return o.get(k)(...args);
      const mt = methodsFor(o);
      const m = own(mt, k) ? mt[k] : null;
      if (!m) fail("у значения типа «" + typeName(o) + "» нет функции «" + k + "»." + hint(k, Object.keys(methodsFor(o))));
      return m(o, ...args);
    };
    R.scall = (o, k, args) => (o == null ? null : R.call(o, k, args));
    R.sapply = (f, args) => (f == null ? null : R.fn(f, "значение")(...args));
    R.fn = (f, name) => { if (typeof f !== "function") fail("«" + name + "» — это не функция, а " + typeName(f)); return f; };
    R.iter = (v) => {
      if (Array.isArray(v)) return v.slice();
      if (typeof v === "string") return [...v];
      if (v instanceof Map) return [...v.keys()];
      if (v instanceof Set) return [...v];
      fail("перебирать «для каждого» можно список, строку, словарь или множество, а не «" + typeName(v) + "»");
    };
    R.pairs = (v) => {
      if (v instanceof Map) return [...v];
      if (Array.isArray(v) || typeof v === "string") return [...v].map((x, i) => [i, x]);
      fail("перебирать пары можно у словаря, списка или строки, а не у «" + typeName(v) + "»");
    };
    R.missing = (name) => fail("не передано значение для параметра «" + name + "»");
    const ifaceOk = (v, i) => v != null && typeof v === "object" && i.methods.every((m) => typeof v[m] === "function");
    R.isa = (v, t, cls) => {
      if (t === "любое" || (cls && Object.prototype.hasOwnProperty.call(cls, "__builtinError"))) return true;
      if (cls) return cls.__iface ? ifaceOk(v, cls) : v instanceof cls;
      if (t === "целое") return Number.isInteger(v);
      return typeName(v) === t;
    };
    R.tp = (v, t, name, cls) => {
      if (t === "любое") return v;
      if (!(t === "Ошибка" ? v instanceof cls : R.isa(v, t, cls))) fail("«" + name + "» должно быть типа «" + t + "», а получено: " + typeName(v));
      return v;
    };
    R.userError = (v) => new PonError(typeof v === "string" ? v : show(v), v);
    R.errValue = (e) => (e && e.isPon ? e.ponValue : R.explain(e));
    R.explain = (e) => {
      if (e instanceof RangeError) {
        const m = String(e.message);
        if (/call stack/i.test(m)) return "слишком глубокая рекурсия — функция вызывает себя слишком много раз";
        if (/string length/i.test(m)) return "слишком длинная строка — не хватает памяти";
        if (/array length/i.test(m)) return "слишком длинный список — не хватает памяти";
        return "число или размер вне допустимых пределов";
      }
      return String((e && e.message) || e);
    };
    R.rethrowFatal = (e) => { if (e && e.fatal) throw e; };
    R.unpack = (v, n) => {
      const l = Array.isArray(v) ? v : typeof v === "string" ? [...v] : null;
      if (!l) fail("справа от «=» должен быть список из " + n + " значений, а получено: " + typeName(v));
      if (l.length !== n) fail("слева имён: " + n + ", а справа значений: " + l.length);
      return l;
    };
    R.enumOf = (items) => {
      const m = new Map();
      let last = -1;
      for (const [k, v] of items) { const x = v === undefined ? last + 1 : v; if (typeof x === "number") last = x; m.set(k, x); }
      frozenMaps.add(m);
      return m;
    };
    R.iface = (name, methods) => Object.freeze({ __iface: true, name, methods: Object.freeze(methods.slice()) });
    R.implements = (cls, list) => {
      for (const i of list) for (const m of i.methods) if (typeof cls.prototype[m] !== "function") fail("класс «" + cls.prototype.__cls + "» обещал «реализует " + i.name + "», но в нём нет функции «" + m + "»");
    };
    let ErrCls = null;
    R.builtinClass = (name) => {
      if (name !== "Ошибка") fail("нет встроенного класса «" + name + "»");
      if (!ErrCls) {
        ErrCls = class {
          constructor(...a) { this.__init(); if (typeof this["создать"] === "function") this["создать"](...a); }
          __init() { this["сообщение"] = "ошибка"; }
          get __cls() { return "Ошибка"; }
          ["создать"](с) { if (с !== undefined) this["сообщение"] = с; }
          ["строкой"]() { return show(this["сообщение"]); }
        };
        ErrCls.__builtinError = true;
      }
      return ErrCls;
    };
    R.ref = (get, set) => Object.create(Object.prototype, {
      __ref: { value: true },
      __cls: { value: "ссылка" },
      значение: { enumerable: true, get, set },
    });
    R.deref = (r) => {
      if (!r || !r.__ref) fail("звёздочку «*» можно ставить только перед ссылкой (например: *п), а получено: " + typeName(r));
      return r.значение;
    };
    R.derefSet = (r, v) => {
      if (!r || !r.__ref) fail("присваивать через «*» можно только ссылке");
      r.значение = v;
      return v;
    };
    R.task = (f) => {
      try { return { __task: true, __cls: "задача", done: true, value: f(), error: null }; }
      catch (e) { return { __task: true, __cls: "задача", done: true, value: null, error: e }; }
    };
    R.await = (v) => {
      if (v && v.__task) { if (v.error) throw v.error; return v.value; }
      if (v && typeof v.then === "function") fail("«ждать» здесь выполняется сразу: настоящий промис подождать нельзя");
      return v;
    };
    R.methodRef = (o, name) => {
      if (o == null) fail("нельзя взять «::» у «ничего»");
      return (...a) => R.call(o, name, a);
    };
    R.enter = (m) => {
      if (m == null) fail("у «с … как» должно быть значение");
      if (typeof m["войти"] === "function") return m["войти"]();
      if (typeof m["открыть"] === "function") return m["открыть"]();
      return m;
    };
    R.exit = (m) => {
      if (m == null) return;
      if (typeof m["выйти"] === "function") return m["выйти"]();
      if (typeof m["закрыть"] === "function") return m["закрыть"]();
      if (typeof m["закрой"] === "function") return m["закрой"]();
    };
    R.pickCtor = (list, n) => {
      let loose = null;
      for (const [id, mn, mx] of list) {
        if (n >= mn && n <= mx) {
          if (n === mx) return "создать#" + id;
          if (loose == null) loose = id;
        }
      }
      if (loose != null) return "создать#" + loose;
      fail("нет функции «создать» для " + n + " значений");
    };
    R.sizeof = (v) => {
      if (v === null || v === undefined) return 0;
      if (typeof v === "boolean") return 1;
      if (typeof v === "number") return 8;
      if (typeof v === "string") return v.length * 2;
      if (Array.isArray(v)) return v.length;
      if (v instanceof Map || v instanceof Set) return v.size;
      if (v && v.__ref) return 8;
      if (isObj(v)) return objKeys(v).length;
      return 0;
    };
    R.printf = (fmt, args) => {
      if (typeof fmt !== "string") fail("шаблон для «формат» должен быть строкой, а получено: " + typeName(fmt));
      let i = 0;
      const out = fmt.replace(/%(-)?(\d+)?(?:\.(\d+))?([%sdifFgGxXoc])/g, (_, left, width, prec, spec) => {
        if (spec === "%") return "%";
        if (i >= args.length) fail("в шаблоне «формат» не хватает значений");
        const a = args[i++];
        let str;
        if (spec === "s") str = show(a);
        else if (spec === "c") str = typeof a === "number" ? String.fromCodePoint(a) : show(a).charAt(0);
        else if (spec === "d" || spec === "i") str = String(Math.trunc(needNum(a, "число")));
        else if (spec === "x" || spec === "X") { str = (Math.trunc(needNum(a, "число")) >>> 0).toString(16); if (spec === "X") str = str.toUpperCase(); }
        else if (spec === "o") str = (Math.trunc(needNum(a, "число")) >>> 0).toString(8);
        else if (spec === "f" || spec === "F" || spec === "g" || spec === "G") {
          const n = needNum(a, "число");
          const pr = prec !== undefined ? Number(prec) : (spec === "f" || spec === "F" ? 6 : undefined);
          str = pr === undefined ? String(n) : n.toFixed(pr);
          if (spec === "F" || spec === "G") str = str.toUpperCase();
        } else str = show(a);
        if (width) str = left ? str.padEnd(Number(width)) : str.padStart(Number(width));
        return str;
      });
      if (i < args.length) fail("в «формат» передано слишком много значений: использовано " + i + ", а передано " + args.length);
      return out;
    };
    const openFile = (path, mode) => {
      needStr(path, "путь к файлу");
      const md = mode === undefined || mode === null ? "читать" : show(mode);
      if (md !== "читать" && md !== "r" && md !== "писать" && md !== "w" && md !== "дописать" && md !== "a") fail("режим файла «" + md + "»: напишите «читать», «писать» или «дописать»");
      const file = {
        __cls: "файл",
        "путь": path,
        "режим": md,
        ["прочитай"]() { return fileOp("прочитать", path, (fs) => fs.readFileSync(path, "utf8")); },
        ["запиши"](t) { return fileOp("записать", path, (fs) => { fs.writeFileSync(path, show(t), "utf8"); return null; }); },
        ["допиши"](t) { return fileOp("дописать", path, (fs) => { fs.appendFileSync(path, show(t), "utf8"); return null; }); },
        ["закрой"]() { return null; },
        ["закрыть"]() { return null; },
        ["войти"]() { return file; },
        ["выйти"]() { return null; },
      };
      return file;
    };
    const toNum = (v) => {
      if (typeof v === "number") return v;
      if (typeof v === "boolean") return v ? 1 : 0;
      if (typeof v === "string") { const t = v.trim().replace(",", "."); if (t !== "" && !isNaN(Number(t))) return Number(t); fail("не получается превратить «" + v + "» в число"); }
      fail("не получается превратить «" + typeName(v) + "» в число");
    };
    const fsMod = () => { if (!io.fs) fail("работа с файлами доступна только в программе на компьютере, а не в песочнице"); return io.fs; };
    const fileOp = (what, p, f) => { needStr(p, "путь к файлу"); try { return f(fsMod()); } catch (e) { if (e.isPon) throw e; fail("не удалось " + what + " файл «" + p + "»"); } };
    const pick = (a) => (a.length === 1 && Array.isArray(a[0]) ? a[0] : a);
    const bitInt = (v) => { needNum(v, "число"); if (!Number.isInteger(v)) fail("побитовые операции работают только с целыми числами, а получено: " + show(v)); return v; };
    const toJson = (v) => {
      if (v === null || v === undefined) return null;
      if (typeof v === "number" || typeof v === "string" || typeof v === "boolean") return v;
      if (Array.isArray(v) || v instanceof Set) return [...v].map(toJson);
      if (v instanceof Map) { const o = {}; for (const [k, x] of v) o[String(k)] = toJson(x); return o; }
      if (isObj(v)) { const o = {}; for (const k of Object.keys(v)) if (!k.startsWith("__")) o[k] = toJson(v[k]); return o; }
      fail("значение типа «" + typeName(v) + "» нельзя записать в JSON");
    };
    const fromJson = (v) => {
      if (Array.isArray(v)) return v.map(fromJson);
      if (v && typeof v === "object") { const m = new Map(); for (const k of Object.keys(v)) m.set(k, fromJson(v[k])); return m; }
      return v;
    };
    R.b = {
      "спроси": (q) => { const a = io.ask(q === undefined ? "" : show(q)); return a === null || a === undefined ? "" : String(a); },
      "число": toNum,
      "строка": (v) => show(v),
      "логическое": (v) => R.truth(v),
      "длина": (v) => { if (typeof v === "string" || Array.isArray(v)) return v.length; if (v instanceof Map || v instanceof Set) return v.size; fail("длину можно узнать у строки, списка или словаря, а не у «" + typeName(v) + "»"); },
      "добавь": (l, x) => { if (!Array.isArray(l)) fail("добавлять можно только в список"); return listM["добавь"](l, x); },
      "убери": (l, i) => { if (Array.isArray(l)) return listM["убери"](l, i); if (l instanceof Map) return dictM["убери"](l, i); fail("убирать можно только из списка или словаря"); },
      "случайное": (a, b) => { if (a === undefined) return Math.random(); if (b === undefined) { b = a; a = 1; } needNum(a, "начало"); needNum(b, "конец"); return Math.floor(Math.random() * (b - a + 1)) + a; },
      "округли": (v, k) => { needNum(v, "значение"); const m = Math.pow(10, k || 0); return Math.round(v * m) / m; },
      "корень": (v) => { needNum(v, "значение"); if (v < 0) fail("корень из отрицательного числа не извлекается"); return Math.sqrt(v); },
      "модуль": (v) => Math.abs(needNum(v, "значение")),
      "большими": (s) => needStr(s, "значение").toUpperCase(),
      "маленькими": (s) => needStr(s, "значение").toLowerCase(),
      "соедини": (l, sep) => { if (!Array.isArray(l)) fail("соединять можно только список"); return listM["соедини"](l, sep); },
      "от": (a, b, st) => { needNum(a, "начало"); needNum(b, "конец"); st = st === undefined ? 1 : R.step(st); const r = []; for (let i = a; st > 0 ? i <= b : i >= b; i += st) { r.push(i); if (r.length > 1e7) fail("слишком длинный список"); } return r; },
      "тип": typeName,
      "сумма": (l) => { if (!Array.isArray(l)) fail("сумму можно посчитать только у списка"); return listM["сумма"](l); },
      "макс": (...a) => { const l = pick(a); if (!l.length) fail("у пустого списка нет максимума"); return l.reduce((x, y) => (R.gt(y, x) ? y : x)); },
      "мин": (...a) => { const l = pick(a); if (!l.length) fail("у пустого списка нет минимума"); return l.reduce((x, y) => (R.lt(y, x) ? y : x)); },
      "целое": (v) => Math.trunc(needNum(v, "значение")),
      "степень": (a, b) => R.pow(a, b),
      "время": () => Date.now() / 1000,
      "дата_сейчас": () => { const d = new Date(); return new Map([["год", d.getFullYear()], ["месяц", d.getMonth() + 1], ["день", d.getDate()], ["час", d.getHours()], ["минута", d.getMinutes()], ["секунда", d.getSeconds()], ["день_недели", d.getDay() === 0 ? 7 : d.getDay()]]); },
      "пауза": (ms) => { needNum(ms, "пауза в миллисекундах"); if (ms < 0 || ms > 60000) fail("пауза должна быть от 0 до 60000 миллисекунд"); if (typeof SharedArrayBuffer !== "undefined" && typeof Atomics !== "undefined") { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms); } else { const e = Date.now() + ms; while (Date.now() < e) {} } return null; },
      "формат_числа": (v, k) => { needNum(v, "число"); const n = k === undefined ? 2 : needNum(k, "знаков после запятой"); if (n < 0 || n > 10) fail("знаков после запятой — от 0 до 10"); const s = Math.abs(v).toFixed(n).split("."); return (v < 0 && Number(s.join(".")) !== 0 ? "-" : "") + s[0].replace(/\B(?=(\d{3})+(?!\d))/g, " ") + (s[1] ? "," + s[1] : ""); },
      "синус": (v) => Math.sin(needNum(v, "угол в радианах")),
      "косинус": (v) => Math.cos(needNum(v, "угол в радианах")),
      "логарифм": (v, b) => { needNum(v, "число"); if (v <= 0) fail("логарифм есть только у положительного числа"); return b === undefined ? Math.log(v) : Math.log(v) / Math.log(needNum(b, "основание")); },
      "вниз": (v) => Math.floor(needNum(v, "число")),
      "вверх": (v) => Math.ceil(needNum(v, "число")),
      "прочитай_файл": (p) => fileOp("прочитать", p, (fs) => fs.readFileSync(p, "utf8")),
      "запиши_файл": (p, t) => fileOp("записать", p, (fs) => { fs.writeFileSync(p, show(t), "utf8"); return null; }),
      "допиши_файл": (p, t) => fileOp("дописать", p, (fs) => { fs.appendFileSync(p, show(t), "utf8"); return null; }),
      "файл_есть": (p) => fileOp("проверить", p, (fs) => fs.existsSync(p)),
      "ПИ": Math.PI,
      "бесконечность": Infinity,
      "выход": (c) => { const e = new PonError("выход"); e.fatal = true; e.isExit = true; e.code = c === undefined || c === null ? 0 : needNum(c, "код выхода"); throw e; },
      "проверь": (c, m) => { if (!R.truth(c)) throw R.userError(m === undefined ? "проверка не прошла" + (R.L ? " (строка " + R.L + ")" : "") : m); return null; },
      "множество": (l) => (l === undefined ? new Set() : new Set(R.iter(l))),
      "нацело": (a, b) => { needNum(a, "делимое"); needNum(b, "делитель"); if (b === 0) fail("делить на ноль нельзя"); return Math.floor(a / b); },
      "экземпляр": (v, c) => { if (typeof c === "string") return R.isa(v, c, null); if (c && c.__iface) return ifaceOk(v, c); if (typeof c === "function") return v instanceof c; fail("второе значение в «экземпляр» — класс, интерфейс или название типа"); },
      "символ": (n) => { needNum(n, "код символа"); if (!Number.isInteger(n) || n < 0 || n > 0x10ffff) fail("нет символа с кодом " + show(n)); return String.fromCodePoint(n); },
      "код": (s) => strM["код"](needStr(s, "символ")),
      "бит_и": (a, b) => bitInt(a) & bitInt(b), "бит_или": (a, b) => bitInt(a) | bitInt(b), "бит_искл": (a, b) => bitInt(a) ^ bitInt(b), "бит_не": (a) => ~bitInt(a),
      "сдвиг_влево": (a, b) => bitInt(a) << bitInt(b), "сдвиг_вправо": (a, b) => bitInt(a) >> bitInt(b), "сдвиг_вправо_без_знака": (a, b) => bitInt(a) >>> bitInt(b),
      "в_json": (v, ind) => JSON.stringify(toJson(v), null, ind === undefined ? undefined : needNum(ind, "отступ")),
      "из_json": (t) => { needStr(t, "текст JSON"); let v; try { v = JSON.parse(t); } catch (e) { fail("текст не похож на JSON: " + e.message); } return fromJson(v); },
      "нумеруй": (l) => R.iter(l).map((x, i) => [i, x]),
      "попарно": (a, b) => { const x = R.iter(a), y = R.iter(b); return x.slice(0, Math.min(x.length, y.length)).map((v, i) => [v, y[i]]); },
      "список_файлов": (p) => fileOp("прочитать папку", p, (fs) => fs.readdirSync(p).sort()),
      "удали_файл": (p) => fileOp("удалить", p, (fs) => { fs.unlinkSync(p); return null; }),
      "создай_папку": (p) => fileOp("создать папку", p, (fs) => { fs.mkdirSync(p, { recursive: true }); return null; }),
      "тангенс": (v) => Math.tan(needNum(v, "угол в радианах")),
      "арктангенс": (y, x) => (x === undefined ? Math.atan(needNum(y, "число")) : Math.atan2(needNum(y, "y"), needNum(x, "x"))),
      "экспонента": (v) => Math.exp(needNum(v, "степень")),
      "аргументы": (io.args || []).slice(),
      "argv": (io.args || []).slice(),
      "размер": (v) => R.sizeof(v),
      "sizeof": (v) => R.sizeof(v),
      "формат": (fmt, ...a) => R.printf(fmt, a),
      "printf": (fmt, ...a) => R.printf(fmt, a),
      "открой": (path, mode) => openFile(path, mode),
    };
    return R;
  }

  function execute(js, io) {
    const R = makeRuntime(io);
    try {
      new Function("__R", "\"use strict\";\n" + js)(R);
    } catch (e) {
      if (e && e.isExit) return e.code;
      let msg;
      if (e && e.isPon) msg = e.message;
      else if (e instanceof RangeError) msg = R.explain(e);
      else if (e instanceof ReferenceError) msg = "имя используется раньше, чем объявлено";
      else msg = "внутренняя ошибка: " + (e && e.message);
      const err = new Error((R.L ? "Строка " + R.L + ": " : "") + msg);
      err.runtime = true;
      err.line = R.L;
      throw err;
    }
  }

  function run(src, io) {
    io = io || {};
    const lines = [];
    const js = compile(src, { file: io.file, resolve: io.resolve, guard: io.guard !== false, warn: io.warn });
    execute(js, { write: (s) => { lines.push(s); if (io.write) io.write(s); }, writeErr: (s) => { if (io.writeErr) io.writeErr(s); else { lines.push(s); if (io.write) io.write(s); } }, ask: io.ask || (() => ""), fs: io.fs, maxSteps: io.maxSteps, args: io.args || [] });
    return lines;
  }

  function nodeAsk(fs, q) {
    if (q) process.stdout.write(q + " ");
    const bytes = [];
    const buf = Buffer.alloc(1);
    for (;;) {
      let n = 0;
      try { n = fs.readSync(0, buf, 0, 1, null); } catch (e) { if (e.code === "EAGAIN") continue; if (e.code === "EOF") break; throw e; }
      if (n === 0 || buf[0] === 10) break;
      bytes.push(buf[0]);
    }
    return Buffer.from(bytes).toString("utf8").replace(/\r$/, "");
  }

  function standalone(js) {
    return "\"use strict\";\n" +
      "const __makeRuntime = " + makeRuntime.toString() + ";\n" +
      "const __ask = " + nodeAsk.toString() + ";\n" +
      "const __fs = require(\"fs\");\n" +
      "if (process.platform === \"win32\") { try { require(\"child_process\").execFileSync(require(\"path\").join(process.env.SystemRoot || \"C:\\\\Windows\", \"System32\", \"chcp.com\"), [\"65001\"], { stdio: \"ignore\", windowsHide: true }); } catch (e) {} }\n" +
      "const __args = (function (argv) { const a = argv.slice(); if (a.length) a.shift(); if (a[0] && /\\.(js|cjs|mjs)$/i.test(a[0])) a.shift(); if (a[0] && /^(запустить|run|выполнить)$/i.test(a[0])) { a.shift(); if (a[0]) a.shift(); return a; } if (a[0] && /\\.pon$/i.test(a[0])) a.shift(); return a; })(process.argv);\n" +
      "const __R = __makeRuntime({ write: (s) => process.stdout.write(s + \"\\n\"), writeErr: (s) => process.stderr.write(s + \"\\n\"), ask: (q) => __ask(__fs, q), fs: __fs, maxSteps: Infinity, args: __args });\n" +
      "try {\n" + js + "\n} catch (e) {\n" +
      "  if (e && e.isExit) { process.exitCode = e.code; } else {\n" +
      "  const m = e && e.isPon ? e.message : __R.explain(e);\n" +
      "  process.stderr.write(\"Ошибка. \" + (__R.L ? \"Строка \" + __R.L + \": \" : \"\") + m + \"\\n\");\n" +
      "  process.exitCode = 1;\n" +
      "}}\n";
  }

  return { transliterate, makeRenamer, jsIdent, nearest, autoKeyword, mixedScript, MAX_DEPTH, tokenize, parse, analyze, generate, frontend, compile, run, execute, standalone, makeRuntime, nodeAsk, resolveImports, CompileError, suggest, levenshtein, KEYWORDS: ALL_WORDS.concat(Object.keys(ALIASES)), ALIASES, RESERVED, BUILTINS, TYPES };
});
