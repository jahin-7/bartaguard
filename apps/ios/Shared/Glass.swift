import BartaGuardCore
import SwiftUI

// Liquid Glass on iOS 26 (the real .glassEffect material), frosted .ultraThinMaterial before that,
// so the same screens work back to iOS 17.

extension Verdict {
    var color: Color {
        switch self {
        case .suspicious: .red
        case .uncertain: .orange
        case .promotional: .blue
        case .normal: .green
        }
    }
    var symbol: String {
        switch self {
        case .suspicious: "exclamationmark.triangle.fill"
        case .uncertain: "questionmark.circle.fill"
        case .promotional: "megaphone.fill"
        case .normal: "checkmark.shield.fill"
        }
    }
}

struct GlassCard: ViewModifier {
    var radius: CGFloat = 28
    var tint: Color? = nil
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(tint.map { .regular.tint($0.opacity(0.18)) } ?? .regular, in: .rect(cornerRadius: radius))
        } else {
            content
                .background(.ultraThinMaterial, in: .rect(cornerRadius: radius))
                .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.7), .white.opacity(0.1)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
                .shadow(color: .black.opacity(0.08), radius: 16, y: 8)
        }
    }
}

extension View {
    func glassCard(radius: CGFloat = 28, tint: Color? = nil) -> some View { modifier(GlassCard(radius: radius, tint: tint)) }

    @ViewBuilder func glassButton(prominent: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            if prominent { buttonStyle(.glassProminent) } else { buttonStyle(.glass) }
        } else {
            if prominent { buttonStyle(.borderedProminent).buttonBorderShape(.capsule) } else { buttonStyle(.bordered).buttonBorderShape(.capsule) }
        }
    }
}

/// Slow colour field behind the glass, washed with the verdict colour after a check.
struct Backdrop: View {
    var tint: Color?
    @Environment(\.colorScheme) private var scheme
    @State private var phase = false

    var body: some View {
        let dark = scheme == .dark
        let blobs: [Color] = dark ? [Color(red: 0.12, green: 0.30, blue: 0.72), Color(red: 0.48, green: 0.18, blue: 0.42), Color(red: 0.06, green: 0.43, blue: 0.36), Color(red: 0.29, green: 0.18, blue: 0.66)]
                                  : [Color(red: 0.62, green: 0.77, blue: 1.0), Color(red: 1.0, green: 0.78, blue: 0.66), Color(red: 0.73, green: 0.95, blue: 0.85), Color(red: 0.85, green: 0.77, blue: 1.0)]
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            ZStack {
                (dark ? Color(red: 0.03, green: 0.035, blue: 0.06) : Color(red: 0.94, green: 0.95, blue: 0.97))
                ForEach(0..<4, id: \.self) { i in
                    let spots = [CGPoint(x: w * (phase ? 0.35 : 0.15), y: h * 0.12), CGPoint(x: w * (phase ? 0.8 : 0.95), y: h * 0.32),
                                 CGPoint(x: w * 0.2, y: h * (phase ? 0.72 : 0.62)), CGPoint(x: w * (phase ? 0.9 : 0.8), y: h * 0.88)]
                    Circle().fill(RadialGradient(colors: [blobs[i].opacity(dark ? 0.55 : 0.7), .clear], center: .center, startRadius: 0, endRadius: w * 0.8))
                        .frame(width: w * 1.6, height: w * 1.6).position(spots[i])
                }
                if let tint {
                    LinearGradient(colors: [tint.opacity(dark ? 0.35 : 0.25), .clear], startPoint: .top, endPoint: .center)
                }
            }
        }
        .ignoresSafeArea()
        .animation(.easeInOut(duration: 0.6), value: tint)
        .onAppear { withAnimation(.easeInOut(duration: 18).repeatForever(autoreverses: true)) { phase = true } }
    }
}

/// The verdict, the message with risky words highlighted, the reasons and next steps.
struct ResultView: View {
    let text: String
    let analysis: Analysis
    let l: L
    var compact = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 10) {
                Image(systemName: analysis.verdict.symbol)
                    .font(.system(size: 30, weight: .semibold)).foregroundStyle(analysis.verdict.color)
                    .frame(width: 60, height: 60).background(analysis.verdict.color.opacity(0.16), in: .circle)
                Text(l.title(analysis.verdict)).font(.system(size: 28, weight: .bold))
                Text(l.summary(analysis)).font(.body).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(22).glassCard(tint: analysis.verdict.color)

            VStack(alignment: .leading, spacing: 8) {
                Text(l.t("The message", "মেসেজটি")).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                Text(highlighted).font(.body)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(20).glassCard()

            if !analysis.flags.isEmpty {
                Text(l.t("What we noticed", "যা লক্ষ করা গেছে").uppercased()).font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.leading, 8)
                ForEach(Array(analysis.flags.enumerated()), id: \.offset) { _, f in flagRow(f) }
            }
            if analysis.verdict == .suspicious && !compact {
                VStack(alignment: .leading, spacing: 10) {
                    Text(l.t("What to do", "এখন যা করবেন")).font(.headline)
                    ForEach(Array(l.whatToDo.enumerated()), id: \.offset) { i, line in
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(l.n(i + 1)).font(.subheadline.bold()).foregroundStyle(.blue).frame(width: 18)
                            Text(line).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading).padding(20).glassCard()
            }
            Text([l.modelLine(analysis), l.t("Checked on this phone", "এই ফোনেই যাচাই হয়েছে")].compactMap { $0 }.joined(separator: " · "))
                .font(.footnote).foregroundStyle(.tertiary).padding(.horizontal, 8)
        }
    }

    private func flagRow(_ f: Flag) -> some View {
        let c: Color = f.severity >= 2 ? .red : f.severity == 1 ? .orange : .green
        return HStack(alignment: .top, spacing: 12) {
            Circle().fill(c).frame(width: 10, height: 10).padding(.top, 6)
            VStack(alignment: .leading, spacing: 4) {
                if let link = f.link {
                    Text(l.t("Suspicious link: don't open it. Type the official address yourself.", "সন্দেহজনক লিংক: খুলবেন না। অফিসিয়াল ঠিকানা নিজে টাইপ করুন।"))
                    Text(link.host).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                    ForEach(link.flags, id: \.self) { Text("• " + l.linkFlag($0)).font(.subheadline).foregroundStyle(.secondary) }
                } else {
                    Text(l.bn ? f.bn : f.en)
                }
            }
            .font(.subheadline)
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(16).glassCard(radius: 22)
    }

    /// One severity per UTF-16 unit, so overlapping matches read as one clean highlight.
    private var highlighted: AttributedString {
        let t = text.precomposedStringWithCanonicalMapping
        let ns = t as NSString
        var level = [Int](repeating: 0, count: ns.length)
        for f in analysis.flags where f.severity > 0 {
            for r in f.spans {
                let nr = NSRange(r, in: t)
                for i in nr.location..<min(nr.location + nr.length, level.count) { level[i] = max(level[i], f.severity) }
            }
        }
        let out = NSMutableAttributedString(string: t)
        var i = 0
        while i < level.count {
            var j = i
            while j < level.count && level[j] == level[i] { j += 1 }
            if level[i] > 0 {
                let c: UIColor = level[i] >= 2 ? .systemRed : .systemOrange
                out.addAttributes([.backgroundColor: c.withAlphaComponent(0.22), .font: UIFont.preferredFont(forTextStyle: .body).withWeight(.semibold)],
                                  range: NSRange(location: i, length: j - i))
            }
            i = j
        }
        return (try? AttributedString(out, including: \.uiKit)) ?? AttributedString(t)
    }
}

private extension UIFont {
    func withWeight(_ w: UIFont.Weight) -> UIFont {
        UIFont.systemFont(ofSize: pointSize, weight: w)
    }
}
