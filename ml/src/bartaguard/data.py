"""Load BTTC, group near-duplicates, and make leak-free stratified splits.

    python -m bartaguard.data --raw data/raw/BTTC.csv --out data/processed

Messages that are identical after normalisation, or whose character 3-5-gram
TF-IDF cosine similarity is >= NEAR_DUP_SIM, share a group; a group never
spans two splits. Stratification is on label x source so the SMS/Telegram mix
is preserved in every split.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import StratifiedGroupKFold
from sklearn.neighbors import NearestNeighbors

from .text import group_key

NEAR_DUP_SIM = 0.85
LABELS = ["HAM", "PROMO", "SPAM"]
SEED = 13


def load(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path, encoding="utf-8-sig")
    df = df.rename(columns={"Text": "text_raw", "Text_Clean": "text", "Label": "label",
                            "Source": "source", "Annotation_Process": "annotation"})
    df["text"] = df["text"].fillna(df["text_raw"]).astype(str).str.strip()
    return df.reset_index(drop=True)


def _union_find(n: int, pairs) -> np.ndarray:
    parent = np.arange(n)

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for a, b in pairs:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[max(ra, rb)] = min(ra, rb)
    return np.array([find(i) for i in range(n)])


def near_dup_groups(texts: pd.Series) -> np.ndarray:
    keys = texts.map(group_key)
    pairs = []
    # exact template duplicates
    for _, idx in keys.groupby(keys).groups.items():
        idx = list(idx)
        pairs += [(idx[0], j) for j in idx[1:]]
    # near duplicates
    X = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=2, sublinear_tf=True).fit_transform(keys)
    nn = NearestNeighbors(radius=1 - NEAR_DUP_SIM, metric="cosine").fit(X)
    _, nbrs = nn.radius_neighbors(X)
    for i, js in enumerate(nbrs):
        pairs += [(i, int(j)) for j in js if j != i]
    return _union_find(len(texts), pairs)


def make_splits(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["group"] = near_dup_groups(df["text"])
    strat = df["label"] + "_" + df["source"]
    sgkf = StratifiedGroupKFold(n_splits=7, shuffle=True, random_state=SEED)
    folds = np.zeros(len(df), dtype=int)
    for k, (_, te) in enumerate(sgkf.split(df, strat, df["group"])):
        folds[te] = k
    df["split"] = np.where(folds == 0, "test", np.where(folds == 1, "val", "train"))
    return df


def audit(df: pd.DataFrame) -> dict:
    g = df.groupby("group")
    conflicts = int((g["label"].nunique() > 1).sum())
    leak = int((g["split"].nunique() > 1).sum())
    return {
        "rows": int(len(df)),
        "labels": df["label"].value_counts().to_dict(),
        "label_by_source": pd.crosstab(df["label"], df["source"]).to_dict(),
        "label_by_annotation": pd.crosstab(df["label"], df["annotation"]).to_dict(),
        "groups": int(df["group"].nunique()),
        "rows_in_multi_member_groups": int((g["text"].transform("size") > 1).sum()),
        "groups_with_conflicting_labels": conflicts,
        "groups_split_across_splits": leak,
        "split_sizes": df["split"].value_counts().to_dict(),
        "split_label_source": {s: pd.crosstab(d["label"], d["source"]).to_dict() for s, d in df.groupby("split")},
        "near_dup_similarity_threshold": NEAR_DUP_SIM,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", type=Path, default=Path("data/raw/BTTC.csv"))
    ap.add_argument("--out", type=Path, default=Path("data/processed"))
    a = ap.parse_args()
    a.out.mkdir(parents=True, exist_ok=True)
    df = make_splits(load(a.raw))
    rep = audit(df)
    assert rep["groups_split_across_splits"] == 0
    df[["text", "label", "source", "annotation", "group", "split"]].to_csv(a.out / "bttc_splits.csv", index=False)
    (a.out / "data_audit.json").write_text(json.dumps(rep, indent=1, ensure_ascii=False))
    print(json.dumps({k: rep[k] for k in ("rows", "groups", "rows_in_multi_member_groups",
                                           "groups_with_conflicting_labels", "split_sizes")}, indent=1))


if __name__ == "__main__":
    main()
