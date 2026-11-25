import Foundation
import Testing
@testable import BartaGuardCore

/// The Swift engine must agree with the Python training code on every golden case.
struct GoldenTests {
    struct Case: Decodable {
        let text: String
        let proba: [Double]
        let flags: [String]
        let max_severity: Int
        let in_domain: Bool
        let verdict: String
    }
    struct Golden: Decodable { let threshold: Double; let cases: [Case] }

    let golden: Golden
    let engine: BartaGuardEngine

    init() throws {
        let url = try #require(Bundle.module.url(forResource: "ondevice_golden", withExtension: "json"))
        golden = try JSONDecoder().decode(Golden.self, from: Data(contentsOf: url))
        engine = try BartaGuardEngine(threshold: golden.threshold)
    }

    @Test func probabilitiesMatchPython() {
        for c in golden.cases {
            let p = engine.probabilities(c.text)
            for (i, l) in ["HAM", "PROMO", "SPAM"].enumerated() {
                #expect(abs(p[l]! - c.proba[i]) < 2e-3, "\(l) \(p[l]!) vs \(c.proba[i]) for: \(c.text.prefix(50))")
            }
        }
    }

    @Test func flagsMatchPython() {
        for c in golden.cases {
            #expect(engine.flags(c.text).map(\.key).sorted() == c.flags, "\(c.text.prefix(60))")
        }
    }

    @Test func verdictsMatchPython() {
        for c in golden.cases {
            #expect(BartaGuardEngine.inDomain(c.text) == c.in_domain, "\(c.text.prefix(60))")
            #expect(engine.analyze(c.text).verdict.rawValue == c.verdict, "\(c.text.prefix(60))")
        }
    }

    @Test func normalisation() {
        #expect(TextNorm.normalize("  ৳২২০:  ১৫ জিবি ") == "৳0: 0 জিবি")
        #expect(TextNorm.words("কিনতে ডায়াল") == ["কিনতে", "ডায়াল"])  // vowel signs stay inside words
    }
}
