"""Score text with the exported on-device model (no scikit-learn needed at serving time).

Same maths as the phone apps: sublinear tf x idf, L2 per block, linear layer, softmax.
"""
from __future__ import annotations

import json
import math
from collections import Counter
from pathlib import Path

from . import redflags
from .text import char_ngrams, word_tokens
from .verdict import decide, in_domain


class OnDeviceModel:
    def __init__(self, path: Path):
        m = json.loads(Path(path).read_text())
        self.labels = m["labels"]
        self.word_vocab, self.word_idf = m["word"]["vocab"], m["word"]["idf"]
        self.char_vocab, self.char_idf = m["char"]["vocab"], m["char"]["idf"]
        self.n_word = m["word_feature_count"]
        self.intercept = m["intercept"]
        self.coef = [{int(k): v for k, v in row.items()} for row in m["coef"]]

    @staticmethod
    def _tfidf(tokens, vocab, idf, offset):
        counts = Counter(vocab[t] for t in tokens if t in vocab)
        v = {i + offset: (1 + math.log(c)) * idf[i] for i, c in counts.items()}
        norm = math.sqrt(sum(x * x for x in v.values()))
        return {k: x / norm for k, x in v.items()} if norm else v

    def predict_proba(self, text: str) -> dict:
        x = self._tfidf(word_tokens(text), self.word_vocab, self.word_idf, 0)
        x.update(self._tfidf(char_ngrams(text), self.char_vocab, self.char_idf, self.n_word))
        scores = [b + sum(row.get(i, 0.0) * v for i, v in x.items()) for b, row in zip(self.intercept, self.coef)]
        m = max(scores)
        e = [math.exp(s - m) for s in scores]
        z = sum(e)
        return {l: v / z for l, v in zip(self.labels, e)}


def analyze(model: OnDeviceModel, text: str, threshold: float = 0.6) -> dict:
    flags = redflags.scan(text)
    proba = model.predict_proba(text)
    out = decide(text, proba, flags, threshold)
    out["flags"] = flags
    out["out_of_domain"] = not in_domain(text)
    return out
