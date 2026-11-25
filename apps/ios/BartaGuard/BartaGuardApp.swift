import BartaGuardCore
import SwiftUI

@main
struct BartaGuardApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView().environment(model)
        }
    }
}

struct SavedCheck: Codable, Identifiable {
    var id: Date { time }
    let time: Date
    let verdict: String
    let preview: String
}

/// Everything stays on the phone. History is off by default and keeps only a short preview.
@Observable
final class AppModel {
    var lang: String? = Lang.saved { didSet { Lang.saved = lang } }
    /// "system", "light" or "dark".
    var appearance = UserDefaults.standard.string(forKey: "appearance") ?? "system" {
        didSet { UserDefaults.standard.set(appearance, forKey: "appearance") }
    }
    var scheme: ColorScheme? { appearance == "dark" ? .dark : appearance == "light" ? .light : nil }
    var tab = 0
    var input = ""
    var checked: (text: String, analysis: Analysis)?
    var linkInput = ""
    var link: RuleSet.URLInfo?
    var keepHistory = UserDefaults.standard.bool(forKey: "history") {
        didSet { UserDefaults.standard.set(keepHistory, forKey: "history"); if !keepHistory { clearHistory() } }
    }
    var history: [SavedCheck] = AppModel.loadHistory()

    var l: L { L(bn: lang != "en") }

    func check() {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let e = EngineStore.shared else { return }
        let a = e.analyze(text)
        withAnimation(.spring(duration: 0.45)) { checked = (text, a) }
        if keepHistory {
            history.insert(SavedCheck(time: .now, verdict: a.verdict.rawValue, preview: String(text.prefix(90))), at: 0)
            history = Array(history.prefix(50))
            UserDefaults.standard.set(try? JSONEncoder().encode(history), forKey: "checks")
        }
    }

    func reset() { withAnimation { checked = nil; input = "" } }

    func inspect() {
        let u = linkInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !u.isEmpty, let e = EngineStore.shared else { return }
        withAnimation { link = e.inspect(url: u) }
    }

    func clearHistory() { history = []; UserDefaults.standard.removeObject(forKey: "checks") }

    private static func loadHistory() -> [SavedCheck] {
        guard let d = UserDefaults.standard.data(forKey: "checks") else { return [] }
        return (try? JSONDecoder().decode([SavedCheck].self, from: d)) ?? []
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        @Bindable var m = m
        let l = m.l
        Group {
            if m.lang == nil {
                LanguageView { m.lang = $0 }.background(Backdrop(tint: nil))
            } else {
                TabView(selection: $m.tab) {
                    CheckView().tabItem { Label(l.t("Check", "যাচাই"), systemImage: "checkmark.shield") }.tag(0)
                    LinksView().tabItem { Label(l.t("Links", "লিংক"), systemImage: "link") }.tag(1)
                    LearnView().tabItem { Label(l.t("Learn", "জানুন"), systemImage: "book") }.tag(2)
                    SettingsView().tabItem { Label(l.t("Settings", "সেটিংস"), systemImage: "gearshape") }.tag(3)
                }
            }
        }
        .preferredColorScheme(m.scheme)
        .animation(.easeInOut(duration: 0.4), value: m.appearance)
    }
}
