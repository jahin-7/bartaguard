"""Pack ondevice_model.json into a compact little-endian binary for the iOS app and its extensions.

    python -m bartaguard.export_binary ml/artifacts/ondevice_model.json ml/artifacts/ondevice_model.bin

Parsing the 4.6 MB JSON on iOS peaks around 75 MB, too much for an SMS filter extension.
The binary loads with a couple of copies and no JSON decoder.

Layout ("BGM1"):
    magic[4]  u32 n_labels  u32 n_word  u32 n_char
    f64 intercept[n_labels]
    f32 idf_word[n_word]  f32 idf_char[n_char]
    f32 coef[(n_word + n_char) * n_labels]      feature-major: coef[f * n_labels + k]
    u32 n_bytes  utf8 labels joined by "\\n"
    u32 n_bytes  utf8 word vocab joined by "\\n", ordered by feature index
    u32 n_bytes  utf8 char vocab joined by "\\n", ordered by feature index
"""
import json
import struct
import sys
from pathlib import Path

import numpy as np


def pack(src: Path, dst: Path) -> int:
    m = json.loads(src.read_text())
    labels, nk = m["labels"], len(m["labels"])
    wv, cv = m["word"]["vocab"], m["char"]["vocab"]
    nw, nc = len(wv), len(cv)
    assert nw == m["word_feature_count"] and nw == len(m["word"]["idf"]) and nc == len(m["char"]["idf"])
    coef = np.zeros((nw + nc, nk), dtype="<f4")
    for k, row in enumerate(m["coef"]):
        for j, v in row.items():
            coef[int(j), k] = v

    def tokens(v, n):
        out = [""] * n
        for t, i in v.items():
            assert "\n" not in t
            out[i] = t
        return "\n".join(out).encode()

    def blob(b):
        return struct.pack("<I", len(b)) + b

    data = (b"BGM1" + struct.pack("<III", nk, nw, nc)
            + np.asarray(m["intercept"], dtype="<f8").tobytes()
            + np.asarray(m["word"]["idf"], dtype="<f4").tobytes()
            + np.asarray(m["char"]["idf"], dtype="<f4").tobytes()
            + coef.tobytes()
            + blob("\n".join(labels).encode()) + blob(tokens(wv, nw)) + blob(tokens(cv, nc)))
    dst.write_bytes(data)
    return len(data)


if __name__ == "__main__":
    print(pack(Path(sys.argv[1]), Path(sys.argv[2])), "bytes")
