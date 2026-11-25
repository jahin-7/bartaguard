import Foundation

// On-device port of ml/src/bartaguard/{text,redflags,verdict}.py and the TF-IDF + logistic
// regression model. Nothing here touches the network; messages never leave the phone.
// Golden vectors from the Python side (Tests/ondevice_golden.json) keep the two in step.

// MARK: - Text

enum TextNorm {
    private static let bnDigits: [Character: Character] = [
        "০": "0", "১": "1", "২": "2", "৩": "3", "৪": "4", "৫": "5", "৬": "6", "৭": "7", "৮": "8", "৯": "9",
    ]

    /// NFC, lowercase, Bangla digits -> ASCII, digit runs -> "0", collapse whitespace.
    static func normalize(_ text: String) -> String {
        let lowered = text.precomposedStringWithCanonicalMapping.lowercased()
        var out = String.UnicodeScalarView()
        var inDigits = false
        var pendingSpace = false
        for s in lowered.unicodeScalars {
            let c = bnDigits[Character(s)].map { $0.unicodeScalars.first! } ?? s
            if CharacterSet.whitespacesAndNewlines.contains(c) {
                pendingSpace = !out.isEmpty
                inDigits = false
                continue
            }
            if pendingSpace { out.append(" "); pendingSpace = false }
            if ("0"..."9").contains(c) {
                if !inDigits { out.append("0") }
                inDigits = true
            } else {
                inDigits = false
                out.append(c)
            }
        }
        return String(out)
    }

    private static func isWordScalar(_ s: Unicode.Scalar) -> Bool {
        (0x0980...0x09FF).contains(s.value) || ("a"..."z").contains(s) || ("0"..."9").contains(s)
    }

    static func words(_ text: String) -> [String] {
        var words: [String] = []
        var cur = String.UnicodeScalarView()
        for s in normalize(text).unicodeScalars {
            if isWordScalar(s) { cur.append(s) } else if !cur.isEmpty { words.append(String(cur)); cur = .init() }
        }
        if !cur.isEmpty { words.append(String(cur)) }
        return words
    }

    static func wordTokens(_ text: String) -> [String] {
        let w = words(text)
        guard w.count > 1 else { return w }
        return w + (0..<(w.count - 1)).map { "\(w[$0]) \(w[$0 + 1])" }
    }

    /// sklearn "char_wb" n-grams over Unicode scalars (Python indexes code points).
    static func charNgrams(_ text: String, lo: Int = 2, hi: Int = 4) -> [String] {
        var out: [String] = []
        for word in normalize(text).split(separator: " ", omittingEmptySubsequences: true) {
            let p = Array((" " + word + " ").unicodeScalars)
            for n in lo...hi where p.count >= n {
                for i in 0...(p.count - n) {
                    var v = String.UnicodeScalarView()
                    v.append(contentsOf: p[i..<(i + n)])
                    out.append(String(v))
                }
            }
        }
        return out
    }
}

// MARK: - Model

public struct LinearModel: Sendable {
    struct Block: Sendable { let vocab: [String: Int]; let idf: [Float] }

    let labels: [String]
    private let word: Block, char: Block
    private let nWord: Int
    private let intercept: [Double]
    /// Dense weights, feature-major: coef[feature * nClasses + k].
    private let coef: [Float]

    enum FormatError: Error { case badMagic, truncated }

    /// Reads ondevice_model.bin (see ml/src/bartaguard/export_binary.py). Peak memory stays far below the
    /// JSON route, which matters inside the SMS filter extension.
    public init(binary d: Data) throws {
        var o = 0
        func take(_ n: Int) throws -> Data {
            guard o + n <= d.count else { throw FormatError.truncated }
            defer { o += n }
            return d.subdata(in: (d.startIndex + o)..<(d.startIndex + o + n))
        }
        func u32() throws -> Int { Int(try take(4).withUnsafeBytes { $0.loadUnaligned(as: UInt32.self).littleEndian }) }
        func floats(_ n: Int) throws -> [Float] {
            try take(n * 4).withUnsafeBytes { r in (0..<n).map { Float(bitPattern: r.loadUnaligned(fromByteOffset: $0 * 4, as: UInt32.self).littleEndian) } }
        }
        func lines() throws -> [String] {
            try take(try u32()).split(separator: 10, omittingEmptySubsequences: false).map { String(decoding: $0, as: UTF8.self) }
        }
        guard try take(4) == Data("BGM1".utf8) else { throw FormatError.badMagic }
        let nk = try u32(), nw = try u32(), nc = try u32()
        intercept = try take(nk * 8).withUnsafeBytes { r in (0..<nk).map { Double(bitPattern: r.loadUnaligned(fromByteOffset: $0 * 8, as: UInt64.self).littleEndian) } }
        let idfW = try floats(nw), idfC = try floats(nc)
        coef = try floats((nw + nc) * nk)
        labels = try lines()
        func vocab(_ toks: [String]) -> [String: Int] {
            var v = [String: Int](minimumCapacity: toks.count)
            for (i, t) in toks.enumerated() { v[t] = i }
            return v
        }
        word = Block(vocab: vocab(try lines()), idf: idfW)
        char = Block(vocab: vocab(try lines()), idf: idfC)
        nWord = nw
    }

    /// sublinear tf (1 + ln count) x idf, then L2-normalised, as sklearn's TfidfVectorizer does per block.
    private func tfidf(_ tokens: [String], _ b: Block, offset: Int) -> [Int: Double] {
        var counts: [Int: Int] = [:]
        for t in tokens { if let i = b.vocab[t] { counts[i, default: 0] += 1 } }
        var v: [Int: Double] = [:]
        var norm = 0.0
        for (i, c) in counts {
            let x = (1 + log(Double(c))) * Double(b.idf[i])
            v[i + offset] = x
            norm += x * x
        }
        norm = norm.squareRoot()
        if norm > 0 { for k in v.keys { v[k]! /= norm } }
        return v
    }

    public func predictProba(_ text: String) -> [String: Double] {
        var x = tfidf(TextNorm.wordTokens(text), word, offset: 0)
        x.merge(tfidf(TextNorm.charNgrams(text), char, offset: nWord)) { a, _ in a }
        let nk = labels.count
        let scores = (0..<nk).map { k in intercept[k] + x.reduce(0) { $0 + Double(coef[$1.key * nk + k]) * $1.value } }
        let m = scores.max() ?? 0
        let e = scores.map { exp($0 - m) }
        let z = e.reduce(0, +)
        return Dictionary(uniqueKeysWithValues: zip(labels, e.map { $0 / z }))
    }
}

// MARK: - Red flags

public struct Flag: Identifiable, Hashable {
    public var id: String { key }
    public let key: String
    public let severity: Int
    public let en: String
    public let bn: String
    public let spans: [Range<String.Index>]
    /// Set for suspicious links: what the address-only inspection found.
    public var link: RuleSet.URLInfo? = nil
}

public struct RuleSet: Sendable {
    struct Rule: Decodable { let key: String; let severity: Int; let en: String; let bn: String; let patterns: [String] }
    struct Payload: Decodable {
        let rules: [Rule]
        let dont_share_patterns: [String]
        let shorteners: [String]
        let risky_tlds: [String]
        let brands: [String: [String]]
        let url_pattern: String
    }

    private let rules: [(Rule, [NSRegularExpression])]
    private let dontShare: [NSRegularExpression]
    private let shorteners: Set<String>, riskyTLDs: Set<String>
    private let brands: [String: [String]]
    private let urlRegex: NSRegularExpression

    public init(data: Data) throws {
        let p = try JSONDecoder().decode(Payload.self, from: data)
        let mk = { (s: String) in try NSRegularExpression(pattern: s, options: [.caseInsensitive]) }
        rules = try p.rules.map { ($0, try $0.patterns.map(mk)) }
        dontShare = try p.dont_share_patterns.map(mk)
        shorteners = Set(p.shorteners); riskyTLDs = Set(p.risky_tlds); brands = p.brands
        urlRegex = try mk(p.url_pattern)
    }

    private func matches(_ re: NSRegularExpression, _ t: String) -> [Range<String.Index>] {
        re.matches(in: t, range: NSRange(t.startIndex..., in: t)).compactMap { m in
            m.range.length > 0 ? Range(m.range, in: t) : nil
        }
    }

    public struct URLInfo: Hashable { public let url: String; public let host: String; public let flags: [String] }

    public func inspect(url: String) -> URLInfo {
        let u = url.contains("://") ? url : "http://" + url
        let comps = URLComponents(string: u)
        let host = (comps?.host ?? "").lowercased()
        var f: [String] = []
        if shorteners.contains(host) { f.append("shortener") }
        if host.hasPrefix("xn--") || host.contains(".xn--") { f.append("punycode") }
        if host.range(of: #"^\d+\.\d+\.\d+\.\d+$"#, options: .regularExpression) != nil { f.append("ip_address") }
        if let tld = host.split(separator: ".").last, host.contains("."), riskyTLDs.contains(String(tld)) { f.append("risky_tld") }
        if comps?.scheme == "http" { f.append("not_https") }
        for (brand, official) in brands.sorted(by: { $0.key < $1.key })
        where host.replacingOccurrences(of: "-", with: "").contains(brand)
            && !official.contains(where: { host == $0 || host.hasSuffix("." + $0) }) {
            f.append("brand_lookalike:\(brand)")
        }
        if comps?.user != nil { f.append("userinfo_trick") }
        return URLInfo(url: url, host: host, flags: f)
    }

    public func scan(_ text: String) -> [Flag] {
        let t = text.precomposedStringWithCanonicalMapping
        let protective = dontShare.contains { !matches($0, t).isEmpty }
        var out: [Flag] = []
        for (r, res) in rules {
            let spans = res.flatMap { matches($0, t) }
            guard !spans.isEmpty else { continue }
            if r.key == "credential_request" && protective {
                out.append(Flag(key: "code_delivery", severity: 0,
                                en: "Looks like a code being sent to you with a warning not to share it. Never give this code to anyone who calls or messages you.",
                                bn: "মনে হচ্ছে আপনাকে একটি কোড পাঠানো হয়েছে এবং শেয়ার না করতে সতর্ক করা হয়েছে। কেউ ফোন বা মেসেজ করে চাইলে এই কোড দেবেন না।",
                                spans: spans))
                continue
            }
            out.append(Flag(key: r.key, severity: r.severity, en: r.en, bn: r.bn, spans: spans))
        }
        for span in matches(urlRegex, t) {
            let info = inspect(url: String(t[span]))
            guard !info.flags.isEmpty else { continue }
            let list = info.flags.joined(separator: ", ")
            out.append(Flag(key: "suspicious_link", severity: Set(info.flags).subtracting(["not_https"]).isEmpty ? 1 : 2,
                            en: "Link looks suspicious (\(list)). Do not open it; type the official address yourself.",
                            bn: "লিংকটি সন্দেহজনক (\(list))। এটি খুলবেন না; অফিসিয়াল ঠিকানা নিজে টাইপ করুন।",
                            spans: [span], link: info))
        }
        return out.sorted { $0.severity > $1.severity }
    }
}

// MARK: - Verdict

public enum Verdict: String { case normal, promotional, suspicious, uncertain }

public struct Analysis {
    public let verdict: Verdict
    public let flags: [Flag]
    public let modelLabel: String?
    public let modelConfidence: Double?
    public let outOfDomain: Bool
}

public final class BartaGuardEngine: @unchecked Sendable {
    public static let minBanglaShare = 0.5
    public static let minLetters = 12

    let model: LinearModel
    let rules: RuleSet
    public let threshold: Double

    public init(modelData: Data, rulesData: Data, threshold: Double) throws {
        model = try LinearModel(binary: modelData)
        rules = try RuleSet(data: rulesData)
        self.threshold = threshold
    }

    /// Loads the model and rules bundled with this package.
    public convenience init(threshold: Double = 0.6) throws {
        guard let m = Bundle.module.url(forResource: "ondevice_model", withExtension: "bin"),
              let r = Bundle.module.url(forResource: "redflags_rules", withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        try self.init(modelData: Data(contentsOf: m), rulesData: Data(contentsOf: r), threshold: threshold)
    }

    public static func inDomain(_ text: String) -> Bool {
        var bangla = 0, latin = 0
        for s in text.unicodeScalars {
            if (0x0980...0x09FF).contains(s.value) { bangla += 1 } else if ("a"..."z").contains(s) || ("A"..."Z").contains(s) { latin += 1 }
        }
        let letters = bangla + latin
        return letters >= minLetters && Double(bangla) / Double(max(letters, 1)) >= minBanglaShare
    }

    public func probabilities(_ text: String) -> [String: Double] { model.predictProba(text) }
    public func flags(_ text: String) -> [Flag] { rules.scan(text) }
    public func inspect(url: String) -> RuleSet.URLInfo { rules.inspect(url: url) }

    public func analyze(_ text: String) -> Analysis {
        let flags = rules.scan(text)
        let top = flags.map(\.severity).max() ?? 0
        let inDom = Self.inDomain(text)
        var label: String?, conf: Double?
        if inDom {
            let p = model.predictProba(text)
            if let best = p.max(by: { $0.value < $1.value }) { label = best.key; conf = best.value }
        }
        let verdict: Verdict
        if top >= 3 {
            verdict = .suspicious
        } else if let l = label, let c = conf, c >= threshold {
            if l == "SPAM" { verdict = .suspicious } else if top >= 2 { verdict = .suspicious } else { verdict = l == "HAM" ? .normal : .promotional }
        } else if top >= 2 {
            verdict = .suspicious
        } else {
            verdict = .uncertain
        }
        return Analysis(verdict: verdict, flags: flags, modelLabel: label, modelConfidence: conf, outOfDomain: !inDom)
    }
}
