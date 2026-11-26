"use client";

import { AnimatePresence, motion } from "motion/react";
import Image from "next/image";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { Icon } from "@/components/Icons";
import { type Analysis, Engine, type Flag, type UrlInfo, type Verdict } from "@/lib/engine";
import { GUIDES, S, type Strings } from "@/lib/strings";

const VC: Record<Verdict, string> = { suspicious: "var(--danger)", uncertain: "var(--warn)", promotional: "var(--info)", normal: "var(--ok)" };
const VI: Record<Verdict, string> = { suspicious: "warning", uncertain: "question", promotional: "megaphone", normal: "check" };

function load(key: string) { try { return localStorage.getItem(key); } catch { return null; } }

// Saved choices (language, theme) read through useSyncExternalStore: the first client render
// already has them, and the server render gets `undefined` ("not known yet").
const storeListeners = new Set<() => void>();
function save(key: string, v: string) {
  try { localStorage.setItem(key, v); } catch { /* private mode */ }
  storeListeners.forEach((l) => l());
}
function subscribeStore(l: () => void) { storeListeners.add(l); return () => { storeListeners.delete(l); }; }
function useStored(key: string): string | null | undefined {
  return useSyncExternalStore(subscribeStore, () => load(key), () => undefined);
}

export default function Home() {
  const lang = useStored("lang");
  const [tab, setTab] = useState(0);
  const [engine, setEngine] = useState<Engine | null>(null);
  const [checked, setChecked] = useState<{ text: string; a: Analysis } | null>(null);
  const theme = useStored("theme") ?? "system";

  useEffect(() => { Engine.load().then(setEngine).catch(() => setEngine(null)); }, []);
  const chooseTheme = (t: string) => { save("theme", t); applyTheme(t); };
  useEffect(() => { if (lang) document.documentElement.lang = lang; }, [lang]);

  const s = S(lang !== "en");
  const tint = tab === 0 && checked ? VC[checked.a.verdict] : null;
  const choose = (l: string) => save("lang", l);
  const tabs: [string, string][] = [["shieldCheck", s.t("Check", "যাচাই")], ["link", s.t("Links", "লিংক")], ["book", s.t("Learn", "জানুন")], ["info", s.t("About", "সম্পর্কে")]];

  return (
    <div className="relative isolate min-h-dvh overflow-x-hidden">
      <Backdrop tint={tint} />
      {lang === undefined ? null : lang === null ? (
        <LanguagePicker choose={choose} />
      ) : (
        <>
          <header className="sticky top-0 z-20 px-4 pt-3 lg:pt-5">
            <div className="mx-auto flex max-w-5xl items-center gap-3">
              <div className="flex items-center gap-2.5 font-bold text-lg"><Image src="/logo.svg" alt="" width={32} height={32} unoptimized className="size-8 rounded-[9px]" />BartaGuard</div>
              <nav className="bar ml-auto hidden rounded-full p-1 lg:flex">
                {tabs.map(([icon, label], i) => (
                  <button key={i} onClick={() => setTab(i)} className={`flex items-center gap-2 rounded-full px-4 py-2 text-sm font-semibold transition ${tab === i ? "bg-accent/15 text-accent" : "text-sub hover:text-text"}`}>
                    <Icon name={icon} className="size-4" />{label}
                  </button>
                ))}
              </nav>
              <button onClick={() => choose(s.bn ? "en" : "bn")} className="bar ml-auto rounded-full px-4 py-2 text-sm font-semibold lg:ml-2">{s.bn ? "English" : "বাংলা"}</button>
            </div>
          </header>
          <main className="mx-auto max-w-5xl px-4 pb-32 pt-4 lg:pb-16">
            <AnimatePresence mode="wait">
              <motion.div key={tab} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.2 }}>
                {tab === 0 && <CheckTab s={s} engine={engine} checked={checked} setChecked={setChecked} />}
                {tab === 1 && <LinksTab s={s} engine={engine} />}
                {tab === 2 && <LearnTab s={s} />}
                {tab === 3 && <AboutTab s={s} theme={theme} chooseTheme={chooseTheme} />}
              </motion.div>
            </AnimatePresence>
          </main>
          {/* floating capsule tab bar on phones */}
          <nav className="bar fixed inset-x-4 bottom-[max(1rem,env(safe-area-inset-bottom))] z-30 flex rounded-full p-1.5 lg:hidden">
            {tabs.map(([icon, label], i) => (
              <button key={i} onClick={() => setTab(i)} className={`flex flex-1 flex-col items-center gap-0.5 rounded-full py-2 text-[11px] font-semibold transition ${tab === i ? "bg-accent/15 text-accent" : "text-sub"}`}>
                <Icon name={icon} className="size-6" />{label}
              </button>
            ))}
          </nav>
        </>
      )}
    </div>
  );
}

/** The plain system background; a verdict washes the top of it, so colour always means something. */
function Backdrop({ tint }: { tint: string | null }) {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 -z-10">
      <div className="absolute inset-0 transition-[background] duration-700" style={{ background: tint ? `linear-gradient(180deg, color-mix(in srgb, ${tint} 18%, transparent), transparent 55%)` : "transparent" }} />
    </div>
  );
}

function LanguagePicker({ choose }: { choose: (l: string) => void }) {
  return (
    <div className="mx-auto flex min-h-dvh max-w-sm flex-col items-center justify-center gap-3 px-6 text-center">
      <Image src="/logo.svg" alt="" width={104} height={104} unoptimized priority className="mb-4 size-26 rounded-[24px] shadow-[0_10px_30px_rgb(0_0_0/0.25)]" />
      <h1 className="text-4xl font-bold tracking-tight">BartaGuard</h1>
      <p className="mb-6 text-sub">বার্তা যাচাই · Check a message before you trust it</p>
      <button className="btn btn-prominent w-full text-lg" onClick={() => choose("bn")}>বাংলা</button>
      <button className="btn glass w-full text-lg" onClick={() => choose("en")}>English</button>
      <p className="mt-4 text-sm text-faint">Runs in your browser. Messages are never uploaded.<br />আপনার ব্রাউজারেই চলে। মেসেজ কখনো আপলোড হয় না।</p>
    </div>
  );
}

function Title({ title, sub }: { title: string; sub?: string }) {
  return (
    <div className="mb-4 px-1">
      <h1 className="text-[34px] font-bold leading-tight tracking-tight">{title}</h1>
      {sub && <p className="mt-1 text-sub">{sub}</p>}
    </div>
  );
}

// ---------------- Check ----------------

function CheckTab({ s, engine, checked, setChecked }: { s: Strings; engine: Engine | null; checked: { text: string; a: Analysis } | null; setChecked: (c: { text: string; a: Analysis } | null) => void }) {
  const [text, setText] = useState("");
  const [ocr, setOcr] = useState<{ busy: boolean; progress: number; done: boolean; error?: string }>({ busy: false, progress: 0, done: false });
  const file = useRef<HTMLInputElement>(null);

  const run = () => { if (engine && text.trim()) setChecked({ text: text.trim().normalize("NFC"), a: engine.analyze(text.trim()) }); };

  async function readImage(img: Blob) {
    setOcr({ busy: true, progress: 0, done: false });
    try {
      // OCR runs in a web worker in this tab. The language files download once; the image itself never leaves the device.
      const { createWorker } = await import("tesseract.js");
      const worker = await createWorker(["ben", "eng"], 1, {
        logger: (m: { status: string; progress: number }) => { if (m.status === "recognizing text") setOcr((o) => ({ ...o, progress: m.progress })); },
      });
      const { data } = await worker.recognize(img);
      await worker.terminate();
      setText(data.text.replace(/[ \t]+\n/g, "\n").trim());
      setOcr({ busy: false, progress: 1, done: true });
    } catch {
      setOcr({ busy: false, progress: 0, done: false, error: s.t("Couldn't read that image. Try typing or pasting the text.", "ছবিটি পড়া যায়নি। লেখাটি টাইপ বা পেস্ট করে দেখুন।") });
    }
  }

  function onPaste(e: React.ClipboardEvent) {
    const item = Array.from(e.clipboardData.items).find((i) => i.type.startsWith("image/"));
    const blob = item?.getAsFile();
    if (blob) { e.preventDefault(); readImage(blob); }
  }

  return (
    <>
      <Title title={s.t("Check a message", "মেসেজ যাচাই")} sub={s.t("Runs in your browser. Nothing is uploaded.", "আপনার ব্রাউজারেই চলে। কিছুই আপলোড হয় না।")} />
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] lg:items-start">
        <section className={`glass glass-strong rounded-[28px] p-4 ${checked ? "hidden lg:block" : ""}`}>
          <textarea
            value={text} onChange={(e) => setText(e.target.value)} onPaste={onPaste} rows={7}
            placeholder={s.t("Paste or type the SMS, WhatsApp or Messenger message here, or paste a screenshot…", "এখানে SMS, হোয়াটসঅ্যাপ বা মেসেঞ্জারের মেসেজ পেস্ট করুন বা লিখুন, অথবা স্ক্রিনশট পেস্ট করুন…")}
            className="block w-full resize-y text-[17px] leading-relaxed"
          />
          {ocr.busy && (
            <div className="mt-2">
              <div className="text-sm text-sub">{s.t("Reading the screenshot on this device…", "এই ডিভাইসেই স্ক্রিনশট পড়া হচ্ছে…")}</div>
              <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-black/10"><div className="h-full bg-accent transition-all" style={{ width: `${Math.round(ocr.progress * 100)}%` }} /></div>
            </div>
          )}
          {ocr.done && <p className="mt-2 rounded-2xl bg-warn/15 px-3 py-2 text-sm">{s.t("Text read from the screenshot. Fix any mistakes before checking.", "স্ক্রিনশট থেকে পড়া লেখা। যাচাইয়ের আগে ভুল থাকলে ঠিক করে নিন।")}</p>}
          {ocr.error && <p className="mt-2 text-sm text-danger">{ocr.error}</p>}
          <div className="mt-3 flex flex-wrap gap-2">
            <button className="btn glass" onClick={async () => { try { setText(await navigator.clipboard.readText()); } catch { /* permission denied */ } }}><Icon name="paste" />{s.t("Paste", "পেস্ট")}</button>
            <button className="btn glass" onClick={() => file.current?.click()} disabled={ocr.busy}><Icon name="image" />{s.t("Screenshot", "স্ক্রিনশট")}</button>
            <input ref={file} type="file" accept="image/*" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) readImage(f); e.target.value = ""; }} />
            <button className="btn btn-prominent min-w-36 flex-1" onClick={run} disabled={!engine || !text.trim()}><Icon name="search" />{s.t("Check", "যাচাই করুন")}</button>
          </div>
        </section>

        {checked ? (
          <div>
            <Result s={s} text={checked.text} a={checked.a} />
            <button className="btn glass mt-4 w-full" onClick={() => { setChecked(null); setText(""); setOcr({ busy: false, progress: 0, done: false }); }}>
              <Icon name="refresh" />{s.t("Check another message", "আরেকটি মেসেজ যাচাই")}
            </button>
          </div>
        ) : (
          <div className="grid gap-4">
            <Tip icon="image" title={s.t("Got a screenshot?", "স্ক্রিনশট আছে?")} body={s.t("Upload or paste it. The text is read on this device, then you can correct it before checking.", "আপলোড বা পেস্ট করুন। লেখাটি এই ডিভাইসেই পড়া হয়, যাচাইয়ের আগে ঠিক করে নিতে পারবেন।")} />
            <Tip icon="shield" title={s.t("Get the phone app", "ফোনের অ্যাপ নিন")} body={s.t("On Android, BartaGuard can warn you automatically when a scam message arrives. On iPhone it filters SMS from unknown numbers.", "অ্যান্ড্রয়েডে প্রতারণার মেসেজ এলে BartaGuard নিজে থেকেই সতর্ক করতে পারে। আইফোনে অচেনা নম্বরের SMS ফিল্টার করে।")} />
          </div>
        )}
      </div>
    </>
  );
}

function Tip({ icon, title, body }: { icon: string; title: string; body: string }) {
  return (
    <div className="glass flex gap-3 rounded-[28px] p-5">
      <span className="mt-0.5 text-accent"><Icon name={icon} className="size-6" /></span>
      <div><div className="font-semibold">{title}</div><div className="text-sm text-sub">{body}</div></div>
    </div>
  );
}

function Result({ s, text, a }: { s: Strings; text: string; a: Analysis }) {
  const c = VC[a.verdict];
  return (
    <motion.div initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: "spring", duration: 0.45 }} className="grid gap-4">
      <section className="glass glass-strong rounded-[28px] p-6" style={{ boxShadow: `inset 0 1px 0 var(--rim), 0 16px 40px color-mix(in srgb, ${c} 22%, transparent)` }}>
        <div className="grid size-16 place-items-center rounded-full" style={{ background: `color-mix(in srgb, ${c} 16%, transparent)`, color: c }}><Icon name={VI[a.verdict]} className="size-9" /></div>
        <h2 className="mt-4 text-[28px] font-bold leading-tight">{s.title(a.verdict)}</h2>
        <p className="mt-1.5 text-sub">{s.summary(a)}</p>
      </section>
      <section className="glass rounded-[28px] p-5">
        <div className="text-sm font-semibold text-faint">{s.t("The message", "মেসেজটি")}</div>
        <Highlighted text={text} flags={a.flags} />
      </section>
      {a.flags.length > 0 && <div className="px-2 text-xs font-semibold uppercase tracking-wide text-faint">{s.t("What we noticed", "যা লক্ষ করা গেছে")}</div>}
      {a.flags.map((f, i) => <FlagRow key={i} s={s} f={f} />)}
      {a.verdict === "suspicious" && (
        <section className="glass rounded-[28px] p-5">
          <div className="font-semibold">{s.t("What to do", "এখন যা করবেন")}</div>
          <ol className="mt-2 grid gap-2">
            {s.whatToDo.map((w, i) => <li key={i} className="flex gap-3 text-sub"><span className="w-4 font-bold text-accent">{s.n(i + 1)}</span>{w}</li>)}
          </ol>
        </section>
      )}
      <p className="px-2 text-sm text-faint">{[s.modelLine(a), s.t("Checked in your browser", "আপনার ব্রাউজারেই যাচাই হয়েছে")].filter(Boolean).join(" · ")}</p>
    </motion.div>
  );
}

/** One severity per character, so overlapping matches read as one clean highlight. */
function Highlighted({ text, flags }: { text: string; flags: Flag[] }) {
  const parts = useMemo(() => {
    const level = new Array<number>(text.length).fill(0);
    for (const f of flags) if (f.severity > 0) for (const sp of f.spans) for (let i = sp.start; i < Math.min(sp.end, text.length); i++) level[i] = Math.max(level[i], f.severity);
    const out: { s: string; l: number }[] = [];
    for (let i = 0; i < text.length;) { let j = i; while (j < text.length && level[j] === level[i]) j++; out.push({ s: text.slice(i, j), l: level[i] }); i = j; }
    return out;
  }, [text, flags]);
  return (
    <p className="mt-2 whitespace-pre-wrap text-[17px] leading-relaxed">
      {parts.map((p, i) => p.l ? (
        <mark key={i} className="rounded-md px-0.5 font-semibold text-text" style={{ background: `color-mix(in srgb, ${p.l >= 2 ? "var(--danger)" : "var(--warn)"} 22%, transparent)` }}>{p.s}</mark>
      ) : <span key={i}>{p.s}</span>)}
    </p>
  );
}

function FlagRow({ s, f }: { s: Strings; f: Flag }) {
  const c = f.severity >= 2 ? "var(--danger)" : f.severity === 1 ? "var(--warn)" : "var(--ok)";
  return (
    <div className="glass flex gap-3 rounded-[22px] p-4">
      <span className="mt-2 size-2.5 shrink-0 rounded-full" style={{ background: c }} />
      <div className="text-[15px]">
        {f.link ? (
          <>
            {s.t("Suspicious link: don't open it. Type the official address yourself.", "সন্দেহজনক লিংক: খুলবেন না। অফিসিয়াল ঠিকানা নিজে টাইপ করুন।")}
            <div className="mt-1 text-sm font-semibold text-sub">{f.link.host}</div>
            {f.link.flags.map((x) => <div key={x} className="text-sm text-sub">• {s.linkFlag(x)}</div>)}
          </>
        ) : s.bn ? f.bn : f.en}
      </div>
    </div>
  );
}

// ---------------- Links ----------------

function LinksTab({ s, engine }: { s: Strings; engine: Engine | null }) {
  const [url, setUrl] = useState("");
  const [info, setInfo] = useState<UrlInfo | null>(null);
  const go = () => { if (engine && url.trim()) setInfo(engine.rules.inspect(url.trim())); };
  const bad = info?.flags.some((f) => f !== "not_https");
  const c = bad ? "var(--danger)" : info?.flags.length ? "var(--warn)" : "var(--ok)";
  return (
    <div className="max-w-2xl">
      <Title title={s.t("Check a link", "লিংক যাচাই")} sub={s.t("We read the address. We never open it.", "আমরা শুধু ঠিকানাটি পড়ি, কখনো খুলি না।")} />
      <form className="glass glass-strong flex flex-col gap-3 rounded-[28px] p-4 sm:flex-row" onSubmit={(e) => { e.preventDefault(); go(); }}>
        <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="bit.ly/… · bkash-verify.xyz" inputMode="url" autoCapitalize="off" spellCheck={false} className="min-w-0 flex-1 px-2 py-2 text-[17px]" />
        <button className="btn btn-prominent" disabled={!engine || !url.trim()}><Icon name="search" />{s.t("Inspect", "পরীক্ষা করুন")}</button>
      </form>
      {info && (
        <motion.section initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className="glass mt-4 rounded-[28px] p-5">
          <div className="flex items-center gap-3">
            <span style={{ color: c }}><Icon name={bad ? "warning" : "lock"} className="size-7" /></span>
            <div>
              <div className="text-xl font-bold">{bad ? s.t("Don't open this link", "এই লিংকে যাবেন না") : info.flags.length ? s.t("Be careful", "সাবধান") : s.t("No warning signs in the address", "ঠিকানায় সতর্কসংকেত নেই")}</div>
              <div className="text-sm text-sub break-all">{info.host || info.url}</div>
            </div>
          </div>
          {info.flags.map((f) => (
            <div key={f} className="mt-3 flex gap-2.5"><span className="mt-2 size-2 shrink-0 rounded-full" style={{ background: f === "not_https" ? "var(--warn)" : "var(--danger)" }} />{s.linkFlag(f)}</div>
          ))}
          <p className="mt-4 text-sm text-faint">{s.t("A clean address isn't proof a site is safe. When money is involved, type the official address yourself.", "ঠিকানা ঠিক থাকলেই সাইট নিরাপদ নয়। টাকার বিষয় হলে অফিসিয়াল ঠিকানা নিজে টাইপ করুন।")}</p>
        </motion.section>
      )}
    </div>
  );
}

// ---------------- Learn ----------------

function LearnTab({ s }: { s: Strings }) {
  const [open, setOpen] = useState<Set<number>>(new Set([0]));
  return (
    <>
      <Title title={s.t("Common scams", "প্রচলিত প্রতারণা")} sub={s.t("Know the tricks before they reach you.", "কৌশলগুলো আগে থেকেই জেনে রাখুন।")} />
      <div className="grid gap-4 md:grid-cols-2 md:items-start">
        {GUIDES.map((g, i) => (
          <section key={i} className="glass rounded-[28px] p-5">
            <button className="flex w-full items-center justify-between text-left text-lg font-semibold" onClick={() => setOpen((o) => { const n = new Set(o); if (n.has(i)) n.delete(i); else n.add(i); return n; })}>
              {s.t(g.en, g.bn)}
              <span className={`text-faint transition ${open.has(i) ? "rotate-180" : ""}`}><Icon name="chevron" /></span>
            </button>
            {open.has(i) && (
              <>
                <p className="mt-2 text-sub">{s.t(g.signsEn, g.signsBn)}</p>
                <div className="mt-3 flex gap-2.5 rounded-2xl bg-ok/12 p-3"><span className="text-ok"><Icon name="check" /></span>{s.t(g.doEn, g.doBn)}</div>
              </>
            )}
          </section>
        ))}
        <section className="glass rounded-[28px] p-5 md:col-span-2">
          <div className="font-semibold">{s.t("In danger or being threatened? Call 999.", "বিপদে বা হুমকির মুখে? ৯৯৯-এ কল করুন।")}</div>
          <p className="mt-1 text-sm text-sub">{s.t("For money sent to a scammer, call your bank's or MFS provider's official helpline right away. The number is in their official app.", "প্রতারকের কাছে টাকা গেলে এখনই আপনার ব্যাংক বা MFS-এর অফিসিয়াল হেল্পলাইনে ফোন করুন। নম্বরটি তাদের অফিসিয়াল অ্যাপে আছে।")}</p>
        </section>
      </div>
    </>
  );
}

// ---------------- About ----------------

/** "system" follows the device; the stylesheet reads data-theme for an explicit choice. */
function applyTheme(t: string) {
  const root = document.documentElement;
  root.classList.add("theme-fade");
  if (t === "system") delete root.dataset.theme; else root.dataset.theme = t;
  setTimeout(() => root.classList.remove("theme-fade"), 450);
}

function AboutTab({ s, theme, chooseTheme }: { s: Strings; theme: string; chooseTheme: (t: string) => void }) {
  const rows: [string, string][] = [
    [s.t("Scams flagged as suspicious", "প্রতারণাকে সন্দেহজনক বলেছে"), "90%"],
    [s.t("Scams wrongly called safe", "প্রতারণাকে ভুল করে নিরাপদ বলেছে"), "2%"],
    [s.t("…for SMS scams only", "…শুধু SMS প্রতারণায়"), "15%"],
    [s.t("Text model macro-F1 (3 classes)", "টেক্সট মডেল macro-F1 (৩ শ্রেণি)"), "0.88"],
  ];
  return (
    <div className="grid max-w-3xl gap-4">
      <Title title={s.t("About BartaGuard", "BartaGuard সম্পর্কে")} />
      <div className="px-2 text-xs font-semibold uppercase tracking-wide text-faint">{s.t("Appearance", "থিম")}</div>
      <div className="segmented max-w-md">
        {([["system", s.t("Automatic", "অটো")], ["light", s.t("Light", "লাইট")], ["dark", s.t("Dark", "ডার্ক")]] as const).map(([k, label]) => (
          <button key={k} aria-pressed={theme === k} onClick={() => chooseTheme(k)}>{label}</button>
        ))}
      </div>
      <section className="glass rounded-[28px] p-5">
        <div className="font-semibold">{s.t("Private by design", "শুরু থেকেই গোপনীয়")}</div>
        <p className="mt-1 text-sub">{s.t("The checker and the screenshot reader both run in your browser. No account, no tracking, and your messages are never sent to a server.", "যাচাই আর স্ক্রিনশট পড়া দুটোই আপনার ব্রাউজারে চলে। কোনো অ্যাকাউন্ট বা ট্র্যাকিং নেই, আর আপনার মেসেজ কখনো সার্ভারে পাঠানো হয় না।")}</p>
      </section>
      <section className="glass rounded-[28px] p-5">
        <div className="font-semibold">{s.t("How well it works", "কতটা ভালো কাজ করে")}</div>
        <p className="mt-1 text-sm text-sub">{s.t("Measured on held-out Bangla messages from the BTTC dataset (751 messages; near-duplicates kept out of training).", "BTTC ডেটাসেটের আলাদা রাখা বাংলা মেসেজে মাপা (৭৫১টি মেসেজ; প্রায়-একই মেসেজ প্রশিক্ষণ থেকে বাদ)।")}</p>
        <dl className="mt-3 grid gap-2">
          {rows.map(([k, v]) => <div key={k} className="flex justify-between gap-4 border-b border-black/5 pb-2 last:border-0"><dt className="text-sub">{k}</dt><dd className="font-semibold tabular-nums">{s.bn ? v.replace(/\d/g, (d) => "০১২৩৪৫৬৭৮৯"[+d]) : v}</dd></div>)}
        </dl>
        <p className="mt-3 text-sm text-faint">{s.t("It was trained on Bangla text, so English and Banglish messages only get the warning-sign checks. Treat it as a second opinion, not a guarantee.", "এটি বাংলা লেখায় প্রশিক্ষিত, তাই ইংরেজি ও বাংলিশ মেসেজে শুধু সতর্কসংকেত যাচাই হয়। এটিকে দ্বিতীয় মতামত হিসেবে নিন, নিশ্চয়তা হিসেবে নয়।")}</p>
      </section>
      <section className="glass rounded-[28px] p-5 text-sm text-sub">
        <Link href="/privacy" className="mb-2 block font-semibold text-accent">{s.t("Privacy policy →", "গোপনীয়তা নীতি →")}</Link>
        {s.t("Training data: BTTC, a Bangla tri-class text corpus (Tahmid et al., 2026, CC BY 4.0, doi:10.17632/5wrm959d6f.1).", "প্রশিক্ষণ ডেটা: BTTC, বাংলা তিন-শ্রেণির টেক্সট কর্পাস (Tahmid et al., 2026, CC BY 4.0, doi:10.17632/5wrm959d6f.1)।")}
      </section>
    </div>
  );
}
