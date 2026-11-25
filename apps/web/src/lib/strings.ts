import type { Analysis, Verdict } from "./engine";

/** Same wording as the phone apps. */
export function S(bn: boolean) {
  const t = (en: string, b: string) => (bn ? b : en);
  const n = (x: number) => (bn ? String(x).replace(/\d/g, (d) => "০১২৩৪৫৬৭৮৯"[+d]) : String(x));
  const pct = (x: number) => n(Math.round(x * 100)) + "%";
  return {
    bn, t, n, pct,
    title: (v: Verdict) => ({
      suspicious: t("Looks like a scam", "প্রতারণা মনে হচ্ছে"),
      uncertain: t("Can't tell for sure", "নিশ্চিত বলা যাচ্ছে না"),
      promotional: t("Looks like an ad", "বিজ্ঞাপন মনে হচ্ছে"),
      normal: t("No warning signs", "সতর্ক হওয়ার মতো কিছু পাওয়া যায়নি"),
    })[v],
    summary: (a: Analysis) => {
      switch (a.verdict) {
        case "suspicious":
          return t("Don't reply, call back, open links, or send money or any code.", "উত্তর দেবেন না, ফোন করবেন না, লিংকে যাবেন না, টাকা বা কোনো কোড পাঠাবেন না।");
        case "uncertain":
          return a.outOfDomain
            ? t("BartaGuard's model was trained on Bangla messages. This one is mostly English or Banglish, so only the warning-sign checks ran. Be careful.",
                "BartaGuard-এর মডেল বাংলা মেসেজে প্রশিক্ষিত। এই মেসেজটি মূলত ইংরেজি বা বাংলিশ, তাই শুধু সতর্কসংকেত যাচাই হয়েছে। সাবধান থাকুন।")
            : t("Some signs point both ways. Verify through the official app or number before you act.", "কিছু লক্ষণ দুই দিকেই। কিছু করার আগে অফিসিয়াল অ্যাপ বা নম্বরে যাচাই করুন।");
        case "promotional":
          return t("Seems to be a regular offer or ad. Buy only through official channels.", "সাধারণ অফার বা বিজ্ঞাপন মনে হচ্ছে। শুধু অফিসিয়াল মাধ্যমে কিনুন।");
        case "normal":
          return t("Nothing suspicious found. That isn't a guarantee: never share a PIN or OTP with anyone.", "সন্দেহজনক কিছু পাওয়া যায়নি। তবে এটি নিশ্চয়তা নয়: কাউকে পিন বা OTP দেবেন না।");
      }
    },
    modelLine: (a: Analysis) => {
      if (a.modelLabel === null || a.modelConfidence === null) return null;
      const name = a.modelLabel === "SPAM" ? t("scam/spam", "প্রতারণা/স্প্যাম") : a.modelLabel === "PROMO" ? t("promotion", "প্রচারণা") : t("normal", "সাধারণ");
      return t(`Text model: ${pct(a.modelConfidence)} ${name}`, `টেক্সট মডেল: ${pct(a.modelConfidence)} ${name}`);
    },
    linkFlag: (f: string) => {
      if (f.startsWith("brand_lookalike:")) {
        const b = f.split(":")[1];
        return t(`Pretends to be ${b} but isn't its official site`, `${b}-এর নাম ব্যবহার করছে, কিন্তু অফিসিয়াল সাইট নয়`);
      }
      return ({
        shortener: t("Short link that hides the real address", "শর্ট লিংক, আসল ঠিকানা লুকানো"),
        punycode: t("Uses look-alike letters (punycode)", "দেখতে একই রকম অক্ষর ব্যবহার করেছে (punycode)"),
        ip_address: t("A bare number address instead of a name", "নামের বদলে শুধু সংখ্যার ঠিকানা"),
        risky_tld: t("Domain ending often used by scam sites", "প্রতারণার সাইটে প্রায়ই ব্যবহৃত ডোমেইন"),
        not_https: t("Not a secure (https) link", "নিরাপদ (https) লিংক নয়"),
        userinfo_trick: t("Hides the real address behind an @", "@ চিহ্নের পেছনে আসল ঠিকানা লুকানো"),
      } as Record<string, string>)[f] ?? f;
    },
    whatToDo: [
      t("Don't reply or call the number in the message.", "মেসেজের নম্বরে উত্তর বা কল দেবেন না।"),
      t('Never share a PIN, OTP or password, even with "customer care".', '"কাস্টমার কেয়ার" হলেও পিন, OTP বা পাসওয়ার্ড দেবেন না।'),
      t("Check your balance or account in the official app.", "অফিসিয়াল অ্যাপে ব্যালেন্স বা অ্যাকাউন্ট দেখুন।"),
      t("If you already sent money or a code, call your provider's official helpline now.", "যদি টাকা বা কোড পাঠিয়ে থাকেন, এখনই প্রতিষ্ঠানের অফিসিয়াল হেল্পলাইনে ফোন করুন।"),
    ],
  };
}
export type Strings = ReturnType<typeof S>;

export const GUIDES = [
  { en: "Fake OTP / PIN request", bn: "ভুয়া OTP / পিন চাওয়া",
    signsEn: "Someone calls or texts saying they are from bKash, Nagad, a bank or your operator and asks for a code.",
    signsBn: "কেউ বিকাশ, নগদ, ব্যাংক বা অপারেটরের লোক পরিচয় দিয়ে ফোন/মেসেজ করে কোড চায়।",
    doEn: "Never share a PIN or OTP. No company asks for it. Hang up and call the number in the official app.",
    doBn: "কখনো পিন বা OTP দেবেন না। কোনো প্রতিষ্ঠান তা চায় না। ফোন রেখে অফিসিয়াল অ্যাপের নম্বরে কল করুন।" },
  { en: '"Money sent by mistake"', bn: '"ভুল করে টাকা চলে গেছে"',
    signsEn: "A message says money reached you by mistake, then a call begs you to send it back.",
    signsBn: "মেসেজে বলে ভুল করে আপনার কাছে টাকা গেছে, তারপর ফোন করে ফেরত চায়।",
    doEn: "Open your official app and check your balance. Fake SMS can look exactly like the real one.",
    doBn: "অফিসিয়াল অ্যাপ খুলে ব্যালেন্স দেখুন। ভুয়া SMS হুবহু আসলের মতো হতে পারে।" },
  { en: "Prize or lottery win", bn: "পুরস্কার বা লটারি জেতা",
    signsEn: 'You "won" something you never entered, and must pay a fee or share details to claim it.',
    signsBn: 'আপনি এমন কিছু "জিতেছেন" যাতে অংশই নেননি, আর পেতে হলে ফি দিতে বা তথ্য দিতে হবে।',
    doEn: "Real prizes don't ask you to pay first. Ignore and delete.",
    doBn: "আসল পুরস্কারে আগে টাকা দিতে হয় না। উপেক্ষা করে মুছে ফেলুন।" },
  { en: "Job or easy income offers", bn: "চাকরি বা সহজ আয়ের অফার",
    signsEn: 'Work from home, daily income, "like videos and earn", but first a registration fee.',
    signsBn: 'ঘরে বসে কাজ, দৈনিক আয়, "ভিডিও লাইক করে আয়", কিন্তু আগে রেজিস্ট্রেশন ফি।',
    doEn: "Don't pay to get a job. Look the company up yourself.",
    doBn: "চাকরি পেতে টাকা দেবেন না। প্রতিষ্ঠানটি নিজে খোঁজ নিয়ে দেখুন।" },
  { en: "Account will be blocked", bn: "অ্যাকাউন্ট বন্ধ হয়ে যাবে",
    signsEn: "Threatens that your SIM, bank or MFS account will close unless you act now or click a link.",
    signsBn: "ভয় দেখায় যে এখনই কিছু না করলে বা লিংকে না গেলে সিম, ব্যাংক বা MFS অ্যাকাউন্ট বন্ধ হবে।",
    doEn: "Pressure is the trick. Check in the official app or at a branch.",
    doBn: "চাপ দেওয়াটাই কৌশল। অফিসিয়াল অ্যাপে বা শাখায় গিয়ে যাচাই করুন।" },
  { en: "Betting and casino links", bn: "বেটিং ও ক্যাসিনো লিংক",
    signsEn: "Bonus offers for betting apps, often over Telegram or WhatsApp, with short links.",
    signsBn: "বেটিং অ্যাপের বোনাস অফার, প্রায়ই টেলিগ্রাম বা হোয়াটসঅ্যাপে, শর্ট লিংকসহ।",
    doEn: "Online gambling is illegal in Bangladesh and these sites often take deposits and vanish.",
    doBn: "বাংলাদেশে অনলাইন জুয়া অবৈধ, আর এসব সাইট প্রায়ই টাকা নিয়ে উধাও হয়।" },
];
