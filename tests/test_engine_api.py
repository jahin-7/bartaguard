import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ml" / "src"))
sys.path.insert(0, str(ROOT / "services" / "api"))

from bartaguard import redflags  # noqa: E402
from bartaguard.ondevice import OnDeviceModel, analyze  # noqa: E402
from bartaguard.verdict import in_domain  # noqa: E402

ART = ROOT / "ml" / "artifacts"
GOLDEN = json.loads((ART / "ondevice_golden.json").read_text())
MODEL = OnDeviceModel(ART / "ondevice_model.json")


@pytest.mark.parametrize("case", GOLDEN["cases"], ids=lambda c: c["text"][:30])
def test_exported_model_matches_sklearn(case):
    p = MODEL.predict_proba(case["text"])
    for i, l in enumerate(["HAM", "PROMO", "SPAM"]):
        assert abs(p[l] - case["proba"][i]) < 2e-3
    assert sorted(f["key"] for f in redflags.scan(case["text"])) == case["flags"]
    assert in_domain(case["text"]) == case["in_domain"]
    assert analyze(MODEL, case["text"], GOLDEN["threshold"])["verdict"] == case["verdict"]


def test_genuine_otp_is_not_called_a_credential_request():
    flags = redflags.scan("আপনার বিকাশ ভেরিফিকেশন কোড 482913। কাউকে এই কোড শেয়ার করবেন না।")
    keys = {f["key"] for f in flags}
    assert "code_delivery" in keys and "credential_request" not in keys


def test_links_are_never_fetched(monkeypatch):
    import socket
    monkeypatch.setattr(socket, "create_connection", lambda *a, **k: pytest.fail("network used"))
    assert "brand_lookalike:bkash" in redflags.inspect_url("http://bkash-verify.xyz/login")["flags"]


@pytest.fixture(scope="module")
def client():
    from fastapi.testclient import TestClient
    from app.main import app
    return TestClient(app)


def test_api_analyze(client):
    r = client.post("/api/analyze-text", json={"text": "আপনার একাউন্ট বন্ধ হয়ে যাবে, এখনই পিন নম্বর দিন"})
    assert r.status_code == 200
    body = r.json()
    assert body["verdict"] == "suspicious" and body["stored"] is False
    assert {"account_threat", "credential_request"} <= {f["key"] for f in body["flags"]}


def test_api_rejects_empty_and_huge(client):
    assert client.post("/api/analyze-text", json={"text": ""}).status_code == 422
    assert client.post("/api/analyze-text", json={"text": "ক" * 5000}).status_code == 422


def test_api_other_endpoints(client):
    assert client.post("/api/inspect-url", json={"url": "bit.ly/x"}).json()["flags"][0] == "shortener"
    card = client.get("/api/model-card").json()
    assert card["system_test"]["test"]["n"] == 751 and card["limitations"]
    assert len(client.get("/api/scam-guides").json()) == 6
    assert client.get("/api/health").json() == {"ok": True}
