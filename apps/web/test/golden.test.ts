import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { Engine, LinearModel, RuleSet, inDomain, normalize, words } from "../src/lib/engine";

const root = join(__dirname, "..");
const bin = readFileSync(join(root, "public/ondevice_model.bin"));
const engine = new Engine(
  new LinearModel(bin.buffer.slice(bin.byteOffset, bin.byteOffset + bin.byteLength)),
  new RuleSet(JSON.parse(readFileSync(join(root, "public/redflags_rules.json"), "utf8"))),
);
const golden = JSON.parse(readFileSync(join(root, "../../ml/artifacts/ondevice_golden.json"), "utf8")) as {
  threshold: number;
  cases: { text: string; proba: number[]; flags: string[]; in_domain: boolean; verdict: string }[];
};

describe("engine matches the Python training code", () => {
  it("probabilities", () => {
    for (const c of golden.cases) {
      const p = engine.model.predictProba(c.text);
      ["HAM", "PROMO", "SPAM"].forEach((l, i) => expect(Math.abs(p[l] - c.proba[i]), `${l}: ${c.text.slice(0, 50)}`).toBeLessThan(2e-3));
    }
  });
  it("red flags", () => {
    for (const c of golden.cases) expect(engine.rules.scan(c.text).map((f) => f.key).sort(), c.text.slice(0, 60)).toEqual(c.flags);
  });
  it("verdicts", () => {
    for (const c of golden.cases) {
      expect(inDomain(c.text), c.text.slice(0, 60)).toBe(c.in_domain);
      expect(engine.analyze(c.text).verdict, c.text.slice(0, 60)).toBe(c.verdict);
    }
  });
  it("normalisation", () => {
    expect(normalize("  ৳২২০:  ১৫ জিবি ")).toBe("৳0: 0 জিবি");
    expect(words("কিনতে ডায়াল")).toEqual(["কিনতে", "ডায়াল"]);
  });
  it("links are inspected, not fetched", () => {
    expect(engine.rules.inspect("http://bkash-verify.xyz/login").flags).toEqual(expect.arrayContaining(["brand_lookalike:bkash", "not_https"]));
    expect(engine.rules.inspect("https://www.bkash.com").flags).toEqual([]);
  });
});
