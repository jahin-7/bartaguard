"""Combine the model and the red-flag rules into the verdict users see.

Order of decisions (same logic is ported to Swift and Kotlin):
1. Text that is mostly Latin script is outside what the BTTC-trained model has
   seen (it learned "Latin letters = Telegram spam"), so the model abstains.
2. Any severity-3 rule (OTP/PIN request, account threat, advance fee, refund
   scam) -> "suspicious", whatever the model says.
3. The model's label is used only if its top probability clears the threshold
   picked on validation and no severity-2+ rule fired against a non-spam label.
4. Everything else -> "uncertain" ("Unable to assess reliably").
Severity-2 rules with an abstaining/uncertain model also give "suspicious".
"""
from __future__ import annotations

import re

LATIN = re.compile(r"[A-Za-z]")
BANGLA = re.compile(r"[ঀ-৿]")
MIN_BANGLA_SHARE = 0.5
MIN_LETTERS = 12


def bangla_share(text: str) -> float:
    b, l = len(BANGLA.findall(text)), len(LATIN.findall(text))
    return b / (b + l) if b + l else 0.0


def in_domain(text: str) -> bool:
    letters = len(BANGLA.findall(text)) + len(LATIN.findall(text))
    return letters >= MIN_LETTERS and bangla_share(text) >= MIN_BANGLA_SHARE


def decide(text: str, proba: dict | None, flags: list, threshold: float) -> dict:
    """proba: {"HAM": p, "PROMO": p, "SPAM": p} or None. Returns verdict + reasons."""
    top_sev = max((f["severity"] for f in flags), default=0)
    reasons = []
    model_label, model_conf = None, None
    if proba is not None and in_domain(text):
        model_label = max(proba, key=proba.get)
        model_conf = proba[model_label]
    elif proba is not None:
        reasons.append("out_of_domain")

    if top_sev >= 3:
        verdict = "suspicious"
        reasons.append("strong_red_flag")
    elif model_label is not None and model_conf >= threshold:
        if model_label == "SPAM":
            verdict = "suspicious"
            reasons.append("model_spam")
        elif top_sev >= 2:
            verdict = "suspicious"
            reasons.append("red_flag_overrides_model")
        else:
            verdict = "normal" if model_label == "HAM" else "promotional"
            reasons.append("model_confident")
    elif top_sev >= 2:
        verdict = "suspicious"
        reasons.append("red_flag")
    else:
        verdict = "uncertain"
        reasons.append("low_confidence" if model_label is not None else "no_model_opinion")
    return {"verdict": verdict, "model_label": model_label, "model_confidence": model_conf,
            "max_severity": top_sev, "reasons": reasons}


VERDICT_TO_LABEL = {"normal": "HAM", "promotional": "PROMO", "suspicious": "SPAM"}
