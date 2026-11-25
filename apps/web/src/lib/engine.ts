// In-browser port of the BartaGuard engine (same as apps/ios/BartaGuardCore and the Android app).
// Messages are checked on the visitor's device; nothing is sent to a server.
// test/golden.test.ts keeps it in step with the Python training code.

export type Verdict = "normal" | "promotional" | "suspicious" | "uncertain";

export interface UrlInfo { url: string; host: string; flags: string[] }
export interface Span { start: number; end: number }
export interface Flag { key: string; severity: number; en: string; bn: string; spans: Span[]; link?: UrlInfo }
export interface Analysis {
  verdict: Verdict;
  flags: Flag[];
  modelLabel: string | null;
  modelConfidence: number | null;
  outOfDomain: boolean;
}

// ---------- text ----------

const isSpace = (c: string) => /\s/u.test(c);

/** NFC, lowercase, Bangla digits -> ASCII, digit runs -> "0", collapse whitespace. */
export function normalize(text: string): string {
  let out = "";
  let inDigits = false;
  let pendingSpace = false;
  for (const ch of text.normalize("NFC").toLowerCase()) {
    const cp = ch.codePointAt(0)!;
    const c = cp >= 0x09e6 && cp <= 0x09ef ? String.fromCharCode(48 + cp - 0x09e6) : ch;
    if (isSpace(c)) { pendingSpace = out.length > 0; inDigits = false; continue; }
    if (pendingSpace) { out += " "; pendingSpace = false; }
    if (c >= "0" && c <= "9") {
      if (!inDigits) out += "0";
      inDigits = true;
    } else {
      inDigits = false;
      out += c;
    }
  }
  return out;
}

const isWordChar = (c: string) => {
  const cp = c.codePointAt(0)!;
  return (cp >= 0x0980 && cp <= 0x09ff) || (c >= "a" && c <= "z") || (c >= "0" && c <= "9");
};

export function words(text: string): string[] {
  const out: string[] = [];
  let cur = "";
  for (const c of normalize(text)) {
    if (isWordChar(c)) cur += c;
    else if (cur) { out.push(cur); cur = ""; }
  }
  if (cur) out.push(cur);
  return out;
}

export function wordTokens(text: string): string[] {
  const w = words(text);
  if (w.length <= 1) return w;
  return [...w, ...w.slice(0, -1).map((x, i) => `${x} ${w[i + 1]}`)];
}

/** sklearn "char_wb" n-grams over code points. */
export function charNgrams(text: string, lo = 2, hi = 4): string[] {
  const out: string[] = [];
  for (const word of normalize(text).split(" ").filter(Boolean)) {
    const p = Array.from(` ${word} `);
    for (let n = lo; n <= hi; n++) {
      if (p.length < n) continue;
      for (let i = 0; i <= p.length - n; i++) out.push(p.slice(i, i + n).join(""));
    }
  }
  return out;
}

// ---------- model ----------

interface Block { vocab: Map<string, number>; idf: Float32Array }

/** Reads ondevice_model.bin (layout in ml/src/bartaguard/export_binary.py). */
export class LinearModel {
  readonly labels: string[];
  private word: Block;
  private char: Block;
  private nWord: number;
  private intercept: Float64Array;
  private coef: Float32Array;

  constructor(buf: ArrayBuffer) {
    const dv = new DataView(buf);
    let o = 0;
    const magic = new TextDecoder().decode(new Uint8Array(buf, 0, 4));
    if (magic !== "BGM1") throw new Error("not a BartaGuard model");
    o = 4;
    const u32 = () => { const v = dv.getUint32(o, true); o += 4; return v; };
    const nk = u32(), nw = u32(), nc = u32();
    const f64 = (n: number) => { const a = new Float64Array(n); for (let i = 0; i < n; i++) a[i] = dv.getFloat64(o + i * 8, true); o += n * 8; return a; };
    const f32 = (n: number) => { const a = new Float32Array(n); for (let i = 0; i < n; i++) a[i] = dv.getFloat32(o + i * 4, true); o += n * 4; return a; };
    const lines = () => { const n = u32(); const s = new TextDecoder().decode(new Uint8Array(buf, o, n)); o += n; return s.split("\n"); };
    this.intercept = f64(nk);
    const idfW = f32(nw), idfC = f32(nc);
    this.coef = f32((nw + nc) * nk);
    this.labels = lines();
    const vocab = (toks: string[]) => new Map(toks.map((t, i) => [t, i] as [string, number]));
    this.word = { vocab: vocab(lines()), idf: idfW };
    this.char = { vocab: vocab(lines()), idf: idfC };
    this.nWord = nw;
  }

  /** sublinear tf (1 + ln count) x idf, L2-normalised per block, like sklearn's TfidfVectorizer. */
  private tfidf(tokens: string[], b: Block, offset: number, into: Map<number, number>) {
    const counts = new Map<number, number>();
    for (const t of tokens) { const i = b.vocab.get(t); if (i !== undefined) counts.set(i, (counts.get(i) ?? 0) + 1); }
    let norm = 0;
    const v: [number, number][] = [];
    for (const [i, c] of counts) { const x = (1 + Math.log(c)) * b.idf[i]; v.push([i + offset, x]); norm += x * x; }
    norm = Math.sqrt(norm);
    for (const [k, x] of v) into.set(k, norm > 0 ? x / norm : x);
  }

  predictProba(text: string): Record<string, number> {
    const x = new Map<number, number>();
    this.tfidf(wordTokens(text), this.word, 0, x);
    this.tfidf(charNgrams(text), this.char, this.nWord, x);
    const nk = this.labels.length;
    const scores = this.labels.map((_, k) => { let s = this.intercept[k]; for (const [i, v] of x) s += this.coef[i * nk + k] * v; return s; });
    const m = Math.max(...scores);
    const e = scores.map((s) => Math.exp(s - m));
    const z = e.reduce((a, b) => a + b, 0);
    return Object.fromEntries(this.labels.map((l, i) => [l, e[i] / z]));
  }
}

// ---------- red flags ----------

interface RulesPayload {
  rules: { key: string; severity: number; en: string; bn: string; patterns: string[] }[];
  dont_share_patterns: string[];
  shorteners: string[];
  risky_tlds: string[];
  brands: Record<string, string[]>;
  url_pattern: string;
}

// Python's str regexes treat \d and \b as Unicode-aware (Bangla digits are digits, Bangla letters are
// word characters). JavaScript's are ASCII-only even with the u flag, so rewrite them.
const W = "[\\p{L}\\p{N}_]";
function pyToJs(p: string): RegExp {
  let out = "";
  let inClass = false;
  for (let i = 0; i < p.length; i++) {
    const c = p[i];
    if (c === "\\" && i + 1 < p.length) {
      const n = p[i + 1];
      i++;
      if (n === "d") out += "\\p{Nd}";
      else if (n === "b" && !inClass) out += `(?:(?<=${W})(?!${W})|(?<!${W})(?=${W}))`;
      // Python lets any punctuation be escaped; unicode-mode JS only allows syntax characters.
      else if (/[A-Za-z0-9]/.test(n) || "^$\\.*+?()[]{}|/-".includes(n)) out += "\\" + n;
      else out += n;
      continue;
    }
    if (c === "[") inClass = true;
    else if (c === "]") inClass = false;
    out += c;
  }
  return new RegExp(out, "giu");
}

function matches(re: RegExp, t: string): Span[] {
  const out: Span[] = [];
  re.lastIndex = 0;
  for (const m of t.matchAll(re)) if (m[0].length > 0) out.push({ start: m.index!, end: m.index! + m[0].length });
  return out;
}

export class RuleSet {
  private rules: { key: string; severity: number; en: string; bn: string; res: RegExp[] }[];
  private dontShare: RegExp[];
  private shorteners: Set<string>;
  private riskyTlds: Set<string>;
  private brands: [string, string[]][];
  private urlRe: RegExp;

  constructor(p: RulesPayload) {
    this.rules = p.rules.map((r) => ({ ...r, res: r.patterns.map(pyToJs) }));
    this.dontShare = p.dont_share_patterns.map(pyToJs);
    this.shorteners = new Set(p.shorteners);
    this.riskyTlds = new Set(p.risky_tlds);
    this.brands = Object.entries(p.brands).sort(([a], [b]) => (a < b ? -1 : 1));
    this.urlRe = pyToJs(p.url_pattern);
  }

  /** Local heuristics only: parse, never fetch. Mirrors urllib.parse.urlsplit closely enough for hosts. */
  inspect(url: string): UrlInfo {
    const u = url.includes("://") ? url : "http://" + url;
    const scheme = u.slice(0, u.indexOf("://")).toLowerCase();
    const rest = u.slice(u.indexOf("://") + 3);
    const netloc = rest.split(/[/?#]/)[0];
    const hostPort = netloc.slice(netloc.lastIndexOf("@") + 1);
    const host = (hostPort.startsWith("[") ? hostPort.slice(1, hostPort.indexOf("]")) : hostPort.split(":")[0]).toLowerCase();
    const f: string[] = [];
    if (this.shorteners.has(host)) f.push("shortener");
    if (host.startsWith("xn--") || host.includes(".xn--")) f.push("punycode");
    if (/^\d+\.\d+\.\d+\.\d+$/.test(host)) f.push("ip_address");
    const tld = host.includes(".") ? host.slice(host.lastIndexOf(".") + 1) : "";
    if (this.riskyTlds.has(tld)) f.push("risky_tld");
    if (scheme === "http") f.push("not_https");
    for (const [brand, official] of this.brands) {
      if (host.replaceAll("-", "").includes(brand) && !official.some((d) => host === d || host.endsWith("." + d))) f.push(`brand_lookalike:${brand}`);
    }
    if (netloc.includes("@")) f.push("userinfo_trick");
    return { url, host, flags: f };
  }

  scan(text: string): Flag[] {
    const t = text.normalize("NFC");
    const protective = this.dontShare.some((re) => matches(re, t).length > 0);
    const out: Flag[] = [];
    for (const r of this.rules) {
      const spans = r.res.flatMap((re) => matches(re, t));
      if (!spans.length) continue;
      if (r.key === "credential_request" && protective) {
        out.push({
          key: "code_delivery", severity: 0, spans,
          en: "Looks like a code being sent to you with a warning not to share it. Never give this code to anyone who calls or messages you.",
          bn: "মনে হচ্ছে আপনাকে একটি কোড পাঠানো হয়েছে এবং শেয়ার না করতে সতর্ক করা হয়েছে। কেউ ফোন বা মেসেজ করে চাইলে এই কোড দেবেন না।",
        });
        continue;
      }
      out.push({ key: r.key, severity: r.severity, en: r.en, bn: r.bn, spans });
    }
    for (const span of matches(this.urlRe, t)) {
      const info = this.inspect(t.slice(span.start, span.end));
      if (!info.flags.length) continue;
      const list = info.flags.join(", ");
      out.push({
        key: "suspicious_link", severity: info.flags.some((x) => x !== "not_https") ? 2 : 1, spans: [span], link: info,
        en: `Link looks suspicious (${list}). Do not open it; type the official address yourself.`,
        bn: `লিংকটি সন্দেহজনক (${list})। এটি খুলবেন না; অফিসিয়াল ঠিকানা নিজে টাইপ করুন।`,
      });
    }
    // stable sort, like Python's sorted()
    return out.map((f, i) => [f, i] as const).sort((a, b) => b[0].severity - a[0].severity || a[1] - b[1]).map(([f]) => f);
  }
}

// ---------- verdict ----------

export const MIN_BANGLA_SHARE = 0.5;
export const MIN_LETTERS = 12;

export function inDomain(text: string): boolean {
  let bangla = 0, latin = 0;
  for (const c of text) {
    const cp = c.codePointAt(0)!;
    if (cp >= 0x0980 && cp <= 0x09ff) bangla++;
    else if ((c >= "a" && c <= "z") || (c >= "A" && c <= "Z")) latin++;
  }
  const letters = bangla + latin;
  return letters >= MIN_LETTERS && bangla / Math.max(letters, 1) >= MIN_BANGLA_SHARE;
}

export class Engine {
  constructor(readonly model: LinearModel, readonly rules: RuleSet, readonly threshold = 0.6) {}

  static async load(base = ""): Promise<Engine> {
    const [m, r] = await Promise.all([fetch(`${base}/ondevice_model.bin`), fetch(`${base}/redflags_rules.json`)]);
    return new Engine(new LinearModel(await m.arrayBuffer()), new RuleSet(await r.json()));
  }

  analyze(text: string): Analysis {
    const flags = this.rules.scan(text);
    const top = Math.max(0, ...flags.map((f) => f.severity));
    const inDom = inDomain(text);
    let label: string | null = null, conf: number | null = null;
    if (inDom) {
      const p = this.model.predictProba(text);
      for (const [l, v] of Object.entries(p)) if (conf === null || v > conf) { label = l; conf = v; }
    }
    let verdict: Verdict;
    if (top >= 3) verdict = "suspicious";
    else if (label !== null && conf !== null && conf >= this.threshold)
      verdict = label === "SPAM" || top >= 2 ? "suspicious" : label === "HAM" ? "normal" : "promotional";
    else if (top >= 2) verdict = "suspicious";
    else verdict = "uncertain";
    return { verdict, flags, modelLabel: label, modelConfidence: conf, outOfDomain: !inDom };
  }
}
