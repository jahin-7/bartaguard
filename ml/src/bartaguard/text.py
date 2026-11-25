"""Text normalisation and featurisation shared by training, the API and the phone apps.

Everything here is deliberately simple and explicit so the Swift (iOS) and
Kotlin (Android) ports in apps/ can reproduce it exactly; golden test vectors
in ml/artifacts/ondevice_golden.json check that they do.

Why not sklearn's default tokenizer: Python's ``\\w`` does not match Bangla
vowel signs (Unicode Mn/Mc), so the default pattern splits words like "কিনতে"
into pieces. We treat the whole Bengali block U+0980–U+09FF as word characters.
"""
from __future__ import annotations

import re
import unicodedata

BN_DIGITS = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")
_WORD = re.compile(r"[ঀ-৿a-z0-9]+")
_SPACE = re.compile(r"\s+")
_NUM = re.compile(r"[0-9]+")


def normalize(text: str) -> str:
    """NFC, lowercase, Bangla digits -> ASCII, digit runs -> '0', collapse spaces."""
    t = unicodedata.normalize("NFC", str(text)).lower().translate(BN_DIGITS)
    t = _NUM.sub("0", t)
    return _SPACE.sub(" ", t).strip()


def word_tokens(text: str) -> list:
    """Unigrams + bigrams of Bangla/Latin/digit words (on normalised text)."""
    w = _WORD.findall(normalize(text))
    return w + [f"{a} {b}" for a, b in zip(w, w[1:])]


def char_ngrams(text: str, lo: int = 2, hi: int = 4) -> list:
    """sklearn 'char_wb'-style n-grams: per word, padded with one space each side."""
    out = []
    for word in normalize(text).split(" "):
        if not word:
            continue
        p = f" {word} "
        for n in range(lo, hi + 1):
            out.extend(p[i:i + n] for i in range(len(p) - n + 1))
    return out


def group_key(text: str) -> str:
    """Key for exact/template duplicates: normalised text with URLs/masks/punctuation removed."""
    t = normalize(text)
    t = re.sub(r"https?://\S+|xxxxx", " ", t)
    return " ".join(_WORD.findall(t))
