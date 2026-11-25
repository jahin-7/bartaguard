"""BartaGuard API: the same checks as the apps, for integrations that can't run them locally.

    uvicorn app.main:app --port 8020   (from services/api)

Privacy: message text is processed in memory and never logged or stored. The apps and the website
don't need this server at all; they run the engine on the device.
"""
from __future__ import annotations

import json
import sys
import time
from collections import defaultdict, deque
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "ml" / "src"))

from bartaguard import redflags  # noqa: E402
from bartaguard.ondevice import OnDeviceModel, analyze  # noqa: E402

ART = ROOT / "ml" / "artifacts"
MODEL = OnDeviceModel(ART / "ondevice_model.json")
THRESHOLD = json.loads((ART / "metrics_system.json").read_text())["threshold"]
MAX_CHARS = 4000

app = FastAPI(title="BartaGuard API", version="0.1.0", docs_url="/api/docs", openapi_url="/api/openapi.json")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET", "POST"], allow_headers=["*"])

# Small in-memory rate limit: 60 requests per minute per client address.
_hits: dict[str, deque] = defaultdict(deque)


@app.middleware("http")
async def rate_limit(request: Request, call_next):
    ip = request.client.host if request.client else "?"
    q, now = _hits[ip], time.monotonic()
    while q and now - q[0] > 60:
        q.popleft()
    if len(q) >= 60:
        from fastapi.responses import JSONResponse
        return JSONResponse({"detail": "Too many requests"}, status_code=429)
    q.append(now)
    return await call_next(request)


class TextIn(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_CHARS)


class UrlIn(BaseModel):
    url: str = Field(min_length=1, max_length=2048)


@app.post("/api/analyze-text")
def analyze_text(body: TextIn):
    r = analyze(MODEL, body.text, THRESHOLD)
    return {
        "verdict": r["verdict"],
        "reasons": r["reasons"],
        "model": {"label": r["model_label"], "confidence": r["model_confidence"]},
        "out_of_domain": r["out_of_domain"],
        "flags": [{k: f[k] for k in ("key", "severity", "en", "bn", "spans") if k in f} | ({"url": f["url"]} if "url" in f else {})
                  for f in r["flags"]],
        "stored": False,
    }


@app.post("/api/inspect-url")
def inspect_url(body: UrlIn):
    return redflags.inspect_url(body.url.strip())


@app.get("/api/model-card")
def model_card():
    system = json.loads((ART / "metrics_system.json").read_text())
    cpu = json.loads((ART / "metrics_cpu.json").read_text())
    tr = ART / "metrics_transformers.json"
    return {
        "model": "TF-IDF (word 1-2 + char 2-4) + logistic regression, with explainable red-flag rules",
        "data": "BTTC (Tahmid et al., 2026, CC BY 4.0, doi:10.17632/5wrm959d6f.1); near-duplicate groups never cross splits",
        "threshold": THRESHOLD,
        "system_test": {k: system[k] for k in ("test", "test_sms", "test_telegram")},
        "text_models_test": [{k: r[k] for k in ("model", "macro_f1", "accuracy") if k in r} for r in cpu["overall"]],
        "transformers_test": [{"model": r["model"], "macro_f1": r["test"]["macro_f1"], "sms_macro_f1": r["test_sms"]["macro_f1"]}
                              for r in json.loads(tr.read_text())["results"]] if tr.exists() else [],
        "limitations": [
            "Trained on Bangla text only; English and Banglish get rule checks but no model opinion.",
            "SMS spam is scarce in the data (39 test messages); 15% of SMS scams were called safe.",
            "About 11% of legitimate test messages were called suspicious.",
            "Links are judged from the address only; they are never opened.",
        ],
    }


@app.get("/api/scam-guides")
def scam_guides():
    return json.loads((Path(__file__).parent / "guides.json").read_text())


@app.get("/api/health")
def health():
    return {"ok": True}


@app.exception_handler(ValueError)
async def bad_value(_: Request, e: ValueError):
    raise HTTPException(400, str(e))
