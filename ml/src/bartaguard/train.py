"""Train and evaluate the CPU models; export the on-device model.

    python -m bartaguard.train --splits data/processed/bttc_splits.csv --out ml/artifacts

Models (all fit on train, tuned/calibrated on val, scored once on test):
  shortcut  - logistic regression on length/emoji/link/digit/source features only.
              Measures how much of the task the dataset's platform shortcut explains.
  tfidf_lr  - word 1-2 grams + char 2-4 grams, logistic regression (this is the on-device model)
  tfidf_svm - same features, linear SVM (sigmoid-calibrated on val for probabilities)

Abstention: if the top probability is below UNCERTAIN_BELOW (chosen on val so
that the answered messages are >= TARGET_ACC accurate), the app says
"Unable to assess reliably" instead of guessing.
"""
from __future__ import annotations

import argparse
import json
import re
import time
from pathlib import Path

import numpy as np
import pandas as pd
from scipy import sparse
from sklearn.calibration import CalibratedClassifierCV
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.frozen import FrozenEstimator
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (average_precision_score, classification_report, confusion_matrix, f1_score,
                             precision_recall_fscore_support)
from sklearn.svm import LinearSVC

from .data import LABELS
from .redflags import scan
from .text import char_ngrams, word_tokens

TARGET_ACC = 0.95
_EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿]")


def vectorizers(max_word=20000, max_char=40000):
    w = TfidfVectorizer(analyzer=word_tokens, sublinear_tf=True, min_df=2, max_features=max_word)
    c = TfidfVectorizer(analyzer=char_ngrams, sublinear_tf=True, min_df=3, max_features=max_char)
    return w, c


def featurize(w, c, texts, fit=False):
    if fit:
        return sparse.hstack([w.fit_transform(texts), c.fit_transform(texts)]).tocsr()
    return sparse.hstack([w.transform(texts), c.transform(texts)]).tocsr()


def shortcut_features(df: pd.DataFrame) -> np.ndarray:
    t = df["text"]
    n = t.str.len().clip(lower=1)
    return np.column_stack([np.log1p(n), t.map(lambda s: len(_EMOJI.findall(s))),
                            t.str.count(r"https?://|t\.me"), t.str.count(r"\d") / n,
                            t.str.count(r"[A-Za-z]") / n, (df["source"] == "Telegram").astype(float)])


def evaluate(y, proba, name, df=None) -> dict:
    pred = np.array(LABELS)[proba.argmax(1)]
    p, r, f, s = precision_recall_fscore_support(y, pred, labels=LABELS, zero_division=0)
    spam_idx = LABELS.index("SPAM")
    out = {"model": name, "n": int(len(y)), "macro_f1": float(f1_score(y, pred, average="macro")),
           "accuracy": float((pred == y).mean()),
           "per_class": {l: {"precision": float(p[i]), "recall": float(r[i]), "f1": float(f[i]), "support": int(s[i])}
                         for i, l in enumerate(LABELS)},
           "confusion": confusion_matrix(y, pred, labels=LABELS).tolist(),
           "spam_pr_auc": float(average_precision_score(y == "SPAM", proba[:, spam_idx])),
           # legitimate messages (HAM or PROMO) wrongly called SPAM
           "legit_flagged_as_spam_rate": float(((pred == "SPAM") & (y != "SPAM")).sum() / max((y != "SPAM").sum(), 1)),
           "ham_flagged_as_spam_rate": float(((pred == "SPAM") & (y == "HAM")).sum() / max((y == "HAM").sum(), 1))}
    if df is not None:
        out["by_source"] = {}
        for src in ("SMS", "Telegram"):
            m = (df["source"] == src).values
            if m.sum() and len(set(y[m])) > 1:
                out["by_source"][src] = {"n": int(m.sum()), "macro_f1": float(f1_score(y[m], pred[m], average="macro")),
                                         "spam_recall": float(((pred[m] == "SPAM") & (y[m] == "SPAM")).sum() / max((y[m] == "SPAM").sum(), 1)),
                                         "spam_support": int((y[m] == "SPAM").sum())}
        out["by_annotation"] = {a: float(f1_score(y[(df["annotation"] == a).values], pred[(df["annotation"] == a).values], average="macro"))
                                for a in df["annotation"].unique()}
    return out


def pick_threshold(y_val, proba_val) -> float:
    conf = proba_val.max(1)
    correct = np.array(LABELS)[proba_val.argmax(1)] == y_val
    for t in np.round(np.arange(0.34, 0.99, 0.01), 2):
        keep = conf >= t
        if keep.sum() and correct[keep].mean() >= TARGET_ACC:
            return float(t)
    return 0.99


def abstention(y, proba, t) -> dict:
    keep = proba.max(1) >= t
    pred = np.array(LABELS)[proba.argmax(1)]
    return {"threshold": t, "coverage": float(keep.mean()),
            "accuracy_when_answered": float((pred[keep] == y[keep]).mean()) if keep.any() else None,
            "uncertain_rate": float(1 - keep.mean())}


def ece(y, proba, bins=10) -> float:
    conf, pred = proba.max(1), np.array(LABELS)[proba.argmax(1)]
    edges = np.linspace(0, 1, bins + 1)
    e = 0.0
    for lo, hi in zip(edges[:-1], edges[1:]):
        m = (conf > lo) & (conf <= hi)
        if m.any():
            e += m.mean() * abs((pred[m] == y[m]).mean() - conf[m].mean())
    return float(e)


def export_ondevice(w, c, lr, path: Path, golden_texts: list) -> dict:
    """Vocabulary + idf + LR weights, sparse (only non-zero weights kept)."""
    def vocab(v, off):
        return {tok: [int(i), float(v.idf_[i])] for tok, i in v.vocabulary_.items()}, off
    wv, cv = w.vocabulary_, c.vocabulary_
    nw = len(wv)
    coef = lr.coef_  # (3, n_features)
    payload = {
        "format": "bartaguard-linear-v1",
        "labels": list(lr.classes_),
        "word": {"ngram": [1, 2], "vocab": {t: int(i) for t, i in wv.items()}, "idf": w.idf_.round(6).tolist()},
        "char": {"ngram": [2, 4], "vocab": {t: int(i) for t, i in cv.items()}, "idf": c.idf_.round(6).tolist()},
        "word_feature_count": nw,
        "intercept": lr.intercept_.round(6).tolist(),
        # coef per class as {feature_index: weight}, dropping near-zero weights
        "coef": [{str(j): round(float(v), 5) for j, v in enumerate(row) if abs(v) > 1e-4} for row in coef],
        "sublinear_tf": True, "norm": "l2 per block (word, char) as in sklearn TfidfVectorizer",
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
    X = featurize(w, c, golden_texts)
    golden = [{"text": t, "proba": [round(float(p), 6) for p in row]} for t, row in zip(golden_texts, lr.predict_proba(X))]
    return {"bytes": path.stat().st_size, "golden": golden}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--splits", type=Path, default=Path("data/processed/bttc_splits.csv"))
    ap.add_argument("--challenge", type=Path, default=Path("ml/challenge/challenge_set.csv"))
    ap.add_argument("--out", type=Path, default=Path("ml/artifacts"))
    a = ap.parse_args()
    a.out.mkdir(parents=True, exist_ok=True)
    t0 = time.time()
    df = pd.read_csv(a.splits)
    tr, va, te = (df[df.split == s].reset_index(drop=True) for s in ("train", "val", "test"))
    ytr, yva, yte = tr.label.values, va.label.values, te.label.values
    ch = pd.read_csv(a.challenge)
    res = {"overall": [], "challenge": [], "abstention": {}, "calibration_ece": {}}

    # shortcut-only baseline
    sc = LogisticRegression(max_iter=2000, C=1.0).fit(shortcut_features(tr), ytr)
    res["overall"].append(evaluate(yte, sc.predict_proba(shortcut_features(te)), "shortcut_len_emoji_source", te))

    w, c = vectorizers()
    Xtr = featurize(w, c, tr.text, fit=True)
    Xva, Xte, Xch = featurize(w, c, va.text), featurize(w, c, te.text), featurize(w, c, ch.text)

    # LR: choose C on validation macro-F1
    best = None
    for C in (1, 3, 10, 30):
        m = LogisticRegression(max_iter=4000, C=C, class_weight="balanced").fit(Xtr, ytr)
        f = f1_score(yva, m.predict(Xva), average="macro")
        print(f"LR C={C} val macroF1={f:.4f}")
        if best is None or f > best[0]:
            best = (f, C, m)
    lr = best[2]
    res["lr_C"] = best[1]

    svm = LinearSVC(C=0.5, class_weight="balanced").fit(Xtr, ytr)
    svm_cal = CalibratedClassifierCV(FrozenEstimator(svm), method="sigmoid").fit(Xva, yva)

    for name, model in (("tfidf_lr", lr), ("tfidf_svm_calibrated", svm_cal)):
        pv, pt = model.predict_proba(Xva), model.predict_proba(Xte)
        r = evaluate(yte, pt, name, te)
        thr = pick_threshold(yva, pv)
        r["abstention_test"] = abstention(yte, pt, thr)
        r["ece_test"] = ece(yte, pt)
        res["overall"].append(r)
        pc = model.predict_proba(Xch)
        cr = evaluate(ch.label.values, pc, name)
        cr["by_script"] = {s: float((np.array(LABELS)[pc[(ch.script == s).values].argmax(1)] == ch.label.values[(ch.script == s).values]).mean())
                           for s in ch.script.unique()}
        cr["rows"] = [{"text": t, "label": l, "pred": LABELS[int(p.argmax())], "p_spam": round(float(p[2]), 3)}
                      for t, l, p in zip(ch.text, ch.label, pc)]
        res["challenge"].append(cr)
        print(f"{name}: test macroF1={r['macro_f1']:.4f} legit->spam={r['legit_flagged_as_spam_rate']:.4f} "
              f"SMS={r['by_source'].get('SMS', {}).get('macro_f1')} challenge acc={cr['accuracy']:.3f} by_script={cr['by_script']}")

    # rule layer false-alarm rate on the untouched test split (rules were tuned on the challenge set, not BTTC)
    sev = te.text.map(lambda t: max([f["severity"] for f in scan(t)], default=0))
    res["rules_on_test"] = {lab: {"n": int((te.label == lab).sum()), "share_with_warning_sev2plus": float((sev[te.label == lab] >= 2).mean())}
                            for lab in LABELS}

    ex = export_ondevice(w, c, lr, a.out / "ondevice_model.json", ch.text.tolist()[:30] + te.text.sample(20, random_state=1).tolist())
    (a.out / "ondevice_golden.json").write_text(json.dumps(ex["golden"], ensure_ascii=False, indent=1))
    from .redflags import export_rules
    (a.out / "redflags_rules.json").write_text(export_rules())
    res["ondevice_bytes"] = ex["bytes"]
    res["meta"] = {"splits": {s: int((df.split == s).sum()) for s in ("train", "val", "test")},
                   "abstain_target_accuracy": TARGET_ACC, "runtime_s": round(time.time() - t0)}
    (a.out / "metrics_cpu.json").write_text(json.dumps(res, indent=1, ensure_ascii=False))
    print(json.dumps(res["rules_on_test"]), "ondevice bytes", ex["bytes"])


if __name__ == "__main__":
    main()
