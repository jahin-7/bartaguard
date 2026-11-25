"""Fine-tune Bangla-capable transformers on the BTTC splits (GPU: Kaggle T4 or Apple MPS).

    python -m bartaguard.transformer --data DIR --out ml/artifacts --models sagorsarker/bangla-bert-base

Per checkpoint: train with class-balanced loss, keep the epoch with the best
validation macro-F1, pick the abstention threshold on validation, then score
the test split (overall, SMS, Telegram) and the challenge set once.
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from torch.utils.data import DataLoader, TensorDataset

LABELS = ["HAM", "PROMO", "SPAM"]
L2I = {l: i for i, l in enumerate(LABELS)}


def device():
    if torch.cuda.is_available():
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def encode(tok, texts, max_len):
    e = tok(list(texts), truncation=True, max_length=max_len, padding="max_length", return_tensors="pt")
    return e["input_ids"], e["attention_mask"]


@torch.no_grad()
def predict(model, ids, mask, dev, bs=128):
    model.eval()
    out = []
    for i in range(0, len(ids), bs):
        logits = model(input_ids=ids[i:i + bs].to(dev), attention_mask=mask[i:i + bs].to(dev)).logits
        out.append(torch.softmax(logits.float(), -1).cpu().numpy())
    return np.concatenate(out)


def metrics(y, proba):
    from sklearn.metrics import average_precision_score, confusion_matrix, f1_score, precision_recall_fscore_support
    pred = np.array(LABELS)[proba.argmax(1)]
    p, r, f, s = precision_recall_fscore_support(y, pred, labels=LABELS, zero_division=0)
    return {"n": int(len(y)), "macro_f1": float(f1_score(y, pred, average="macro")), "accuracy": float((pred == y).mean()),
            "per_class_f1": {l: float(f[i]) for i, l in enumerate(LABELS)},
            "spam_recall": float(r[2]), "spam_precision": float(p[2]),
            "spam_pr_auc": float(average_precision_score(y == "SPAM", proba[:, 2])) if (y == "SPAM").any() else None,
            "legit_flagged_as_spam_rate": float(((pred == "SPAM") & (y != "SPAM")).sum() / max((y != "SPAM").sum(), 1)),
            "confusion": confusion_matrix(y, pred, labels=LABELS).tolist()}


def pick_threshold(y, proba, target=0.95):
    conf, ok = proba.max(1), np.array(LABELS)[proba.argmax(1)] == y
    for t in np.round(np.arange(0.34, 0.99, 0.01), 2):
        k = conf >= t
        if k.sum() and ok[k].mean() >= target:
            return float(t)
    return 0.99


def run(ckpt, tr, va, te, ch, out_dir, epochs=4, lr=3e-5, bs=32, max_len=160, log=print):
    from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup
    torch.manual_seed(13)
    dev = device()
    tok = AutoTokenizer.from_pretrained(ckpt)
    model = AutoModelForSequenceClassification.from_pretrained(ckpt, num_labels=3, id2label=dict(enumerate(LABELS)),
                                                               label2id=L2I).to(dev)
    enc = {k: encode(tok, d.text, max_len) for k, d in (("tr", tr), ("va", va), ("te", te), ("ch", ch))}
    ytr = torch.tensor(tr.label.map(L2I).values)
    dl = DataLoader(TensorDataset(*enc["tr"], ytr), batch_size=bs, shuffle=True)
    counts = np.bincount(ytr.numpy(), minlength=3)
    weights = torch.tensor(len(ytr) / (3 * counts), dtype=torch.float32, device=dev)
    opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=0.01)
    sched = get_linear_schedule_with_warmup(opt, int(0.06 * epochs * len(dl)), epochs * len(dl))
    lossf = torch.nn.CrossEntropyLoss(weight=weights)
    use_amp = dev.type == "cuda"
    scaler = torch.amp.GradScaler("cuda", enabled=use_amp)
    best, hist = (-1, None), []
    safe = ckpt.replace("/", "__")
    for ep in range(epochs):
        model.train()
        t0, tot = time.time(), 0.0
        for ids, mask, y in dl:
            opt.zero_grad()
            with torch.autocast(device_type=dev.type, dtype=torch.float16, enabled=use_amp):
                loss = lossf(model(input_ids=ids.to(dev), attention_mask=mask.to(dev)).logits.float(), y.to(dev))
            scaler.scale(loss).backward()
            scaler.unscale_(opt)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(opt)
            scaler.update()
            sched.step()
            tot += loss.item() * len(y)
        pv = predict(model, *enc["va"], dev)
        f = metrics(va.label.values, pv)["macro_f1"]
        hist.append({"epoch": ep + 1, "train_loss": tot / len(ytr), "val_macro_f1": f, "sec": round(time.time() - t0)})
        log(f"{ckpt} epoch {ep + 1} loss={tot / len(ytr):.4f} val macroF1={f:.4f} ({time.time() - t0:.0f}s)")
        if f > best[0]:
            best = (f, ep + 1)
            model.save_pretrained(out_dir / safe)
            tok.save_pretrained(out_dir / safe)
    model = AutoModelForSequenceClassification.from_pretrained(out_dir / safe).to(dev)
    pv, pt, pc = (predict(model, *enc[k], dev) for k in ("va", "te", "ch"))
    thr = pick_threshold(va.label.values, pv)
    keep = pt.max(1) >= thr
    res = {"model": ckpt, "best_epoch": best[1], "val_macro_f1": best[0], "history": hist, "threshold": thr,
           "test": metrics(te.label.values, pt),
           "test_sms": metrics(te[te.source == "SMS"].label.values, pt[(te.source == "SMS").values]),
           "test_telegram": metrics(te[te.source == "Telegram"].label.values, pt[(te.source == "Telegram").values]),
           "test_abstention": {"coverage": float(keep.mean()),
                               "accuracy_when_answered": float((np.array(LABELS)[pt[keep].argmax(1)] == te.label.values[keep]).mean()) if keep.any() else None},
           "challenge": metrics(ch.label.values, pc),
           "challenge_by_script": {s: float((np.array(LABELS)[pc[(ch.script == s).values].argmax(1)] == ch.label.values[(ch.script == s).values]).mean())
                                   for s in ch.script.unique()}}
    pd.DataFrame(pt, columns=LABELS).assign(text=te.text.values, label=te.label.values, source=te.source.values) \
        .to_parquet(out_dir / f"test_proba_{safe}.parquet", index=False)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=Path, required=True)
    ap.add_argument("--out", type=Path, default=Path("ml/artifacts"))
    ap.add_argument("--models", nargs="+", default=["sagorsarker/bangla-bert-base", "FacebookAI/xlm-roberta-base"])
    ap.add_argument("--epochs", type=int, default=4)
    a = ap.parse_args()
    a.out.mkdir(parents=True, exist_ok=True)
    df = pd.read_csv(a.data / "bttc_splits.csv")
    ch_path = a.data / "challenge_set.csv"
    if not ch_path.exists():  # repo layout: data/processed + ml/challenge
        ch_path = Path(__file__).resolve().parents[2] / "challenge" / "challenge_set.csv"
    ch = pd.read_csv(ch_path)
    tr, va, te = (df[df.split == s].reset_index(drop=True) for s in ("train", "val", "test"))
    results = []
    for ckpt in a.models:
        r = run(ckpt, tr, va, te, ch, a.out, epochs=a.epochs)
        results.append(r)
        print(json.dumps({k: r[k] for k in ("model", "best_epoch", "val_macro_f1")}),
              "test macroF1", round(r["test"]["macro_f1"], 4), "SMS", round(r["test_sms"]["macro_f1"], 4),
              "challenge acc", round(r["challenge"]["accuracy"], 3), r["challenge_by_script"], flush=True)
        (a.out / "metrics_transformers.json").write_text(json.dumps({"results": results, "device": str(device()),
                                                                     "gpu": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None}, indent=1))


if __name__ == "__main__":
    main()
