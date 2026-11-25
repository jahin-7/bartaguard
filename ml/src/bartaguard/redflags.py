"""Explainable red-flag rules: transparent indicators, not proof of fraud.

Each rule returns the exact character spans that triggered it, so the apps can
highlight them. Patterns cover Bangla script, Banglish (romanised Bangla) and
English. The same rule list is exported to JSON for the phone apps, so the
patterns are kept to a regex dialect shared by Python, Swift and Kotlin
(no lookbehind, no possessive quantifiers, no inline flags).
"""
from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from urllib.parse import urlsplit

SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "s.id", "shorturl.at",
              "ow.ly", "tiny.cc", "rebrand.ly", "t.ly", "v.gd", "bitly.com"}
RISKY_TLDS = {"xyz", "top", "click", "buzz", "icu", "cfd", "sbs", "rest", "monster", "live", "win", "bet", "vip"}
# Official domains of commonly impersonated brands; anything else using the brand name is flagged.
BRANDS = {"bkash": ["bkash.com"], "nagad": ["nagad.com.bd"], "rocket": ["dutchbanglabank.com"],
          "grameenphone": ["grameenphone.com"], "robi": ["robi.com.bd"], "banglalink": ["banglalink.net"]}


@dataclass(frozen=True)
class Rule:
    key: str
    severity: int          # 1 = context, 2 = warning, 3 = strong warning
    en: str
    bn: str
    patterns: tuple


RULES = [
    Rule("credential_request", 3,
         "Asks for an OTP, PIN, password or verification code. No bank, MFS or company needs these.",
         "OTP, পিন, পাসওয়ার্ড বা ভেরিফিকেশন কোড চাইছে। কোনো ব্যাংক, MFS বা প্রতিষ্ঠান এগুলো চায় না।",
         (r"\b(otp|o\.t\.p|pin|password|passcode|verification code|cvv)\b",
          r"(ওটিপি|পিন নম্বর|পিন কোড|গোপন পিন|পাসওয়ার্ড|ভেরিফিকেশন কোড|কোডটি দিন|কোড দিন|কোডটি বলুন|সিভিভি)",
          r"(পিন|pin)\s*(\S+\s*){0,3}(দিয়ে|দিন|বলুন|বলেন|পাঠান|din|bolun|bolen)",
          r"\b(otp|pin|code)\s*(ta|ti|ti\s*din|din|bolen|bolun|pathan|share)\b")),
    Rule("account_threat", 3,
         "Claims your account will be blocked, suspended or closed. A classic pressure tactic.",
         "আপনার অ্যাকাউন্ট বন্ধ/ব্লক হবে বলে ভয় দেখাচ্ছে, এটি একটি পরিচিত চাপ সৃষ্টির কৌশল।",
         (r"(account|acc)\s*(will be\s*)?(blocked|suspended|closed|locked|deactivated)",
          r"(অ্যাকাউন্ট|একাউন্ট|অ্যাকাউন্টটি|একাউন্টটি)\s*(\S+\s*){0,3}(বন্ধ|ব্লক|স্থগিত|লক)",
          r"(account|acc|একাউন্ট)\s*(bondho|block|lock)\s*(hoye|hobe|kora)")),
    Rule("advance_payment", 3,
         "Asks you to pay or send money first (fee, deposit, registration, processing) to receive a job, prize or refund.",
         "চাকরি, পুরস্কার বা রিফান্ড পেতে আগে টাকা/ফি/জামানত পাঠাতে বলছে।",
         (r"(registration|processing|delivery|security)\s*(fee|charge|deposit)",
          r"(রেজিস্ট্রেশন|রেজিষ্ট্রেশন|প্রসেসিং|ডেলিভারি|জামানত|অগ্রিম)\s*(ফি|চার্জ|টাকা)?",
          r"(send|pathan|pathiye|পাঠান|পাঠিয়ে|সেন্ড)\s*(\S+\s*){0,3}(taka|tk|টাকা)",
          r"(টাকা|taka)\s*(\S+\s*){0,2}(পাঠান|পাঠিয়ে দিন|send kor|pathan)")),
    Rule("wrong_transfer_refund", 3,
         "Says money was sent to you by mistake and asks you to send it back. Check your own balance in the official app first; this is a common scam.",
         "ভুল করে টাকা পাঠানো হয়েছে বলে ফেরত চাইছে। আগে অফিসিয়াল অ্যাপে নিজের ব্যালেন্স দেখুন; এটি একটি প্রচলিত প্রতারণা।",
         (r"ভুল\s*(করে|বশত)\s*(\S+\s*){0,6}(পাঠিয়েছি|চলে গেছে|গেছে|এসেছে)",
          r"(ফেরত|ferot|ferat|return)\s*(\S+\s*){0,2}(দিন|পাঠান|din|pathan|diben|koren)",
          r"\b(vul|bhul)\s*(kore|e)\s*(\S+\s*){0,5}(chole geche|pathiyechi|pathaisi|geche)",
          r"\b(sent|transferred)\s*(\S+\s*){0,3}by mistake")),
    Rule("prize_lottery", 2,
         "Says you have won a prize, lottery or reward you did not enter for.",
         "আপনি পুরস্কার/লটারি জিতেছেন বলছে, যেটিতে আপনি অংশ নেননি।",
         (r"\b(you (have )?won|winner|congratulations|lottery|jackpot|prize|reward)\b",
          r"(অভিনন্দন|বিজয়ী|জিতেছেন|লটারি|পুরস্কার|পুরষ্কার|উপহার জিতে)",
          r"\b(jitechen|bijoyi|lottery jitechen|puroskar)\b")),
    Rule("easy_income", 2,
         "Promises easy or guaranteed income, daily earnings or work-from-home money.",
         "সহজ বা নিশ্চিত আয়, দৈনিক ইনকাম বা ঘরে বসে টাকা আয়ের প্রতিশ্রুতি দিচ্ছে।",
         (r"\b(earn|income|profit)\b\s*(\S+\s*){0,3}(daily|per day|guaranteed|from home)",
          r"(ঘরে বসে|দৈনিক|প্রতিদিন|নিশ্চিত)\s*(\S+\s*){0,3}(আয়|ইনকাম|ইনকাম করুন|টাকা আয়)",
          r"(ইনকাম|income)\s*(\S+\s*){0,3}(করুন|korun|kora jay|করা যায়)",
          r"\b(ghore bose|ghore boshe)\b")),
    Rule("gambling", 2,
         "Mentions betting, casino or gambling. Common in scam and fraud campaigns.",
         "বেটিং, ক্যাসিনো বা জুয়ার কথা বলছে, যা প্রতারণামূলক প্রচারণায় প্রচলিত।",
         (r"\b(bet|betting|casino|1xbet|jackpot|spin|slot|aviator|crazy time)\b",
          r"(বেটিং|বাজি|ক্যাসিনো|জুয়া|স্লট|স্পিন)")),
    Rule("urgency", 1,
         "Creates urgency (\"now\", \"within 24 hours\", \"last chance\"). Slow down and verify.",
         "তাড়াহুড়ো তৈরি করছে (\"এখনই\", \"২৪ ঘণ্টার মধ্যে\", \"শেষ সুযোগ\")। ধীরে যাচাই করুন।",
         (r"\b(urgent|immediately|right now|last chance|within \d+ (hours?|minutes?)|expires? today)\b",
          r"(জরুরি|অবিলম্বে|শেষ সুযোগ|\d+\s*ঘণ্টার মধ্যে|এখনই\s*(\S+\s*){0,2}(না হলে|নইলে))",
          r"\b(ekhoni|joruri|druto)\b")),
    Rule("impersonation", 1,
         "Uses the name of a bank, mobile financial service, telecom or government office. Check it through official channels.",
         "ব্যাংক, মোবাইল ব্যাংকিং, টেলিকম বা সরকারি দপ্তরের নাম ব্যবহার করছে। অফিসিয়াল মাধ্যমে যাচাই করুন।",
         (r"\b(bkash|b-kash|nagad|rocket|upay|bank|police|govt|government|bangladesh bank)\b",
          r"(বিকাশ|নগদ|রকেট|উপায়|ব্যাংক|পুলিশ|সরকার|বাংলাদেশ ব্যাংক)")),
    Rule("callback_number", 1,
         "Asks you to call or message a number given in the message. Use the official number instead.",
         "মেসেজে দেওয়া নম্বরে কল/মেসেজ করতে বলছে। বরং অফিসিয়াল নম্বর ব্যবহার করুন।",
         (r"(call|whatsapp|imo|telegram|contact)\s*(\S+\s*){0,2}(0|\+?880)1[3-9]\d",
          r"(কল করুন|যোগাযোগ করুন|হোয়াটসঅ্যাপ|ইমো)\s*(\S+\s*){0,2}(0|\+?880|০|৮৮০)(1|১)")),
]

_URL = re.compile(r"(https?://[^\s<>\"']+|www\.[^\s<>\"']+|\b[a-z0-9-]+(\.[a-z0-9-]+)*\.(com|net|org|xyz|top|click|link|info|bd|me|io|ly|gl|co|cc|gd|at|live|win|bet|vip|buzz|icu|site|online|shop)(/[^\s<>\"']*)?)",
                  re.IGNORECASE)


def _norm(text: str) -> str:
    return unicodedata.normalize("NFC", text)


def find_urls(text: str) -> list:
    return [{"url": m.group(0), "start": m.start(), "end": m.end()} for m in _URL.finditer(_norm(text))]


def inspect_url(url: str) -> dict:
    """Local heuristics only: parse, never fetch."""
    u = url if "://" in url else "http://" + url
    parts = urlsplit(u)
    host = (parts.hostname or "").lower()
    flags = []
    if host in SHORTENERS:
        flags.append("shortener")
    if host.startswith("xn--") or ".xn--" in host:
        flags.append("punycode")
    if re.fullmatch(r"\d+\.\d+\.\d+\.\d+", host):
        flags.append("ip_address")
    tld = host.rsplit(".", 1)[-1] if "." in host else ""
    if tld in RISKY_TLDS:
        flags.append("risky_tld")
    if parts.scheme == "http":
        flags.append("not_https")
    for brand, official in BRANDS.items():
        if brand in host.replace("-", "") and not any(host == d or host.endswith("." + d) for d in official):
            flags.append(f"brand_lookalike:{brand}")
    if "@" in parts.netloc:
        flags.append("userinfo_trick")
    return {"url": url, "host": host, "flags": flags,
            "note": "Local heuristics only; the link was not opened or checked against a threat database."}


# Genuine OTP deliveries warn you not to share the code; that is a protective sign.
DONT_SHARE = (r"(do not|don't|never)\s+(share|tell|give)",
              r"(কাউকে|কারো সাথে|কারও সাথে)\s*(\S+\s*){0,3}(শেয়ার|দিবেন না|বলবেন না|জানাবেন না)",
              r"(share|kauke)\s*(\S+\s*){0,2}(korben na|diben na|bolben na)")


def scan(text: str) -> list:
    t = _norm(text)
    found = []
    dont_share = any(re.search(p, t, flags=re.IGNORECASE) for p in DONT_SHARE)
    for r in RULES:
        spans = []
        for p in r.patterns:
            for m in re.finditer(p, t, flags=re.IGNORECASE):
                if m.end() > m.start():
                    spans.append({"start": m.start(), "end": m.end(), "text": m.group(0)})
        if spans:
            if r.key == "credential_request" and dont_share:
                found.append({"key": "code_delivery", "severity": 0,
                              "en": "Looks like a code being sent to you with a warning not to share it. Never give this code to anyone who calls or messages you.",
                              "bn": "মনে হচ্ছে আপনাকে একটি কোড পাঠানো হয়েছে এবং শেয়ার না করতে সতর্ক করা হয়েছে। কেউ ফোন বা মেসেজ করে চাইলে এই কোড দেবেন না।",
                              "spans": spans})
                continue
            found.append({"key": r.key, "severity": r.severity, "en": r.en, "bn": r.bn, "spans": spans})
    for u in find_urls(t):
        info = inspect_url(u["url"])
        if info["flags"]:
            found.append({"key": "suspicious_link", "severity": 2 if set(info["flags"]) - {"not_https"} else 1,
                          "en": f"Link looks suspicious ({', '.join(info['flags'])}). Do not open it; type the official address yourself.",
                          "bn": f"লিংকটি সন্দেহজনক ({', '.join(info['flags'])})। এটি খুলবেন না; অফিসিয়াল ঠিকানা নিজে টাইপ করুন।",
                          "spans": [{"start": u["start"], "end": u["end"], "text": u["url"]}], "url": info})
    return sorted(found, key=lambda f: -f["severity"])


def export_rules() -> str:
    """JSON for the phone apps (same regexes, case-insensitive)."""
    return json.dumps({"rules": [{"key": r.key, "severity": r.severity, "en": r.en, "bn": r.bn,
                                  "patterns": list(r.patterns)} for r in RULES],
                       "dont_share_patterns": list(DONT_SHARE),
                       "shorteners": sorted(SHORTENERS), "risky_tlds": sorted(RISKY_TLDS),
                       "brands": BRANDS, "url_pattern": _URL.pattern}, ensure_ascii=False, indent=1)
