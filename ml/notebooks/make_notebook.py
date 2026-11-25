"""Generate the Kaggle transformer notebook from the package source.

    python ml/notebooks/make_notebook.py  ->  ml/notebooks/bartaguard_transformers.ipynb
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "ml" / "src" / "bartaguard"
MODULES = ["__init__.py", "text.py", "redflags.py", "verdict.py", "transformer.py"]


def md(s):
    return {"cell_type": "markdown", "metadata": {}, "source": s}


def code(s):
    return {"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [], "source": s}


cells = [md("""# BartaGuard: transformer fine-tuning on BTTC

Fine-tunes two MIT-licensed checkpoints on the leak-free BTTC splits made by `bartaguard.data`
(near-duplicate groups never cross splits): `sagorsarker/bangla-bert-base` and `FacebookAI/xlm-roberta-base`.
Model selection and the abstention threshold use the validation split only; the test split and the
hand-written challenge set are scored once at the end.

Attach the dataset with `bttc_splits.csv` and `challenge_set.csv`, turn on a GPU and internet, then Run All.
""")]
cells.append(code("!mkdir -p /kaggle/working/src/bartaguard"))
for m in MODULES:
    cells.append(code(f"%%writefile /kaggle/working/src/bartaguard/{m}\n{(SRC / m).read_text()}"))
cells.append(code("""import glob, os, torch
hits = glob.glob('/kaggle/input/**/bttc_splits.csv', recursive=True)
assert hits, 'Attach the BartaGuard dataset (bttc_splits.csv)'
DATA = os.path.dirname(hits[0]); print(DATA, os.listdir(DATA))
print('cuda', torch.cuda.is_available(), torch.cuda.get_device_name(0) if torch.cuda.is_available() else '')"""))
cells.append(code("""%cd /kaggle/working/src
!python -m bartaguard.transformer --data {DATA} --out /kaggle/working/artifacts --models sagorsarker/bangla-bert-base FacebookAI/xlm-roberta-base"""))
cells.append(code("""import json, pandas as pd
M = json.load(open('/kaggle/working/artifacts/metrics_transformers.json'))
pd.DataFrame([{k: v for k, v in r.items() if not isinstance(v, (dict, list))} for r in M['results']])"""))
cells.append(code("!cd /kaggle/working && rm -rf artifacts/*/checkpoint-* && zip -qr bartaguard_artifacts.zip artifacts && ls -la /kaggle/working"))

nb = {"cells": cells, "metadata": {"kernelspec": {"name": "python3", "display_name": "Python 3", "language": "python"},
                                   "language_info": {"name": "python"}}, "nbformat": 4, "nbformat_minor": 5}
out = ROOT / "ml" / "notebooks" / "bartaguard_transformers.ipynb"
out.write_text(json.dumps(nb, indent=1))
print("wrote", out)
