"""Score the full system (model + rules + abstention) that the apps run.

    python -m bartaguard.eval_system --art ml/artifacts --model tfidf_lr
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression

from .data import LABELS
from .redflags import scan
from .train import featurize, vectorizers
from .verdict import VERDICT_TO_LABEL, decide


def score(df: pd.DataFrame, probas: np.ndarray, thr: float) -> dict:
    out = []
    for t, p in zip(df.text, probas):
        d = decide(t, dict(zip(LABELS, map(float, p))), scan(t), thr)
        out.append(d["verdict"])
    v = pd.Series(out, index=df.index)
    y = df.label
    answered = v != "uncertain"
    pred = v.map(VERDICT_TO_LABEL)
    spam = y == "SPAM"
    legit = ~spam
    return {
        "n": int(len(df)),
        "verdicts": v.value_counts().to_dict(),
        "uncertain_rate": float((~answered).mean()),
        "accuracy_when_answered": float((pred[answered] == y[answered]).mean()) if answered.any() else None,
        "spam_caught_as_suspicious": float((v[spam] == "suspicious").mean()) if spam.any() else None,
        "spam_missed_called_safe": float(v[spam].isin(["normal", "promotional"]).mean()) if spam.any() else None,
        "legit_called_suspicious": float((v[legit] == "suspicious").mean()) if legit.any() else None,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--splits", type=Path, default=Path("data/processed/bttc_splits.csv"))
    ap.add_argument("--challenge", type=Path, default=Path("ml/challenge/challenge_set.csv"))
    ap.add_argument("--art", type=Path, default=Path("ml/artifacts"))
    a = ap.parse_args()
    m = json.loads((a.art / "metrics_cpu.json").read_text())
    thr = next(r for r in m["overall"] if r["model"] == "tfidf_lr")["abstention_test"]["threshold"]
    df = pd.read_csv(a.splits)
    tr, te = df[df.split == "train"], df[df.split == "test"]
    ch = pd.read_csv(a.challenge)
    w, c = vectorizers()
    lr = LogisticRegression(max_iter=4000, C=m["lr_C"], class_weight="balanced").fit(featurize(w, c, tr.text, fit=True), tr.label)
    res = {"threshold": thr,
           "test": score(te, lr.predict_proba(featurize(w, c, te.text)), thr),
           "test_sms": score(te[te.source == "SMS"], lr.predict_proba(featurize(w, c, te[te.source == "SMS"].text)), thr),
           "test_telegram": score(te[te.source == "Telegram"], lr.predict_proba(featurize(w, c, te[te.source == "Telegram"].text)), thr),
           "challenge_note": "Rules were tuned on this set, so challenge numbers are a sanity check, not an independent test.",
           "challenge": score(ch, lr.predict_proba(featurize(w, c, ch.text)), thr)}
    for s in ch.script.unique():
        sub = ch[ch.script == s]
        res[f"challenge_{s}"] = score(sub, lr.predict_proba(featurize(w, c, sub.text)), thr)
    (a.art / "metrics_system.json").write_text(json.dumps(res, indent=1))
    for k, v in res.items():
        if isinstance(v, dict):
            print(k, {kk: (round(vv, 3) if isinstance(vv, float) else vv) for kk, vv in v.items()})


if __name__ == "__main__":
    main()
