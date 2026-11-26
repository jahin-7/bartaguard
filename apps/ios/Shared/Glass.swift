import BartaGuardCore
import SwiftUI

// Apple's grouped look: system grey background, solid cards, and Liquid Glass (iOS 26) only where
// iOS itself uses it, on buttons and bars. Works back to iOS 17.

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
    var radius: CGFloat = 26
    var tint: Color? = nil
    func body(content: Content) -> some View {
        content
            .background {
                RoundedRectangle(cornerRadius: radius, style: .continuous).fill(Color(.secondarySystemGroupedBackground))
                    .overlay { if let tint { RoundedRectangle(cornerRadius: radius, style: .continuous).fill(tint.opacity(0.10)) } }
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

/// The plain system background. A verdict washes the top of it, so colour always means something.
struct Backdrop: View {
    var tint: Color?
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ZStack {
            Color(.systemGroupedBackground)
            if let tint {
                LinearGradient(colors: [tint.opacity(scheme == .dark ? 0.28 : 0.16), .clear], startPoint: .top, endPoint: .center)
                    .transition(.opacity)
            }
        }
        .ignoresSafeArea()
        .animation(.easeInOut(duration: 0.6), value: tint)
    }
}

/// The verdict, the message with risky words highlighted, the reasons and next steps.
struct ResultView: View {
    let text: String
    let analysis: Analysis
    let l: L
    var compact = false
    @State private var shown = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 10) {
                Image(systemName: analysis.verdict.symbol)
                    .font(.system(size: 30, weight: .semibold)).foregroundStyle(analysis.verdict.color)
                    .symbolEffect(.bounce, value: shown)
                    .frame(width: 60, height: 60).background(analysis.verdict.color.opacity(0.16), in: .circle)
                    .scaleEffect(shown ? 1 : 0.4).opacity(shown ? 1 : 0)
                Text(l.title(analysis.verdict)).font(.system(size: 28, weight: .bold))
                Text(l.summary(analysis)).font(.body).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(22).glassCard(tint: analysis.verdict.color)
            .onAppear { withAnimation(.spring(response: 0.45, dampingFraction: 0.55).delay(0.1)) { shown = true } }
            .sensoryFeedback(analysis.verdict == .suspicious ? .warning : .success, trigger: shown)

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
