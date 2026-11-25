import BartaGuardCore
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Share → BartaGuard from WhatsApp, Messenger, Messages or anywhere text can be shared.
/// The verdict shows right in the sheet; nothing is saved or uploaded.
final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        let state = ShareState()
        let host = UIHostingController(rootView: ShareSheet(state: state) { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        })
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        host.view.backgroundColor = .clear
        view.addSubview(host.view)
        host.didMove(toParent: self)
        Task { await state.load(from: extensionContext) }
    }
}

@MainActor @Observable
final class ShareState {
    var text: String?
    var analysis: Analysis?
    var failed = false

    func load(from ctx: NSExtensionContext?) async {
        let providers = (ctx?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        var parts: [String] = []
        for p in providers {
            if p.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
               let s = try? await p.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
                parts.append(s)
            } else if p.hasItemConformingToTypeIdentifier(UTType.url.identifier),
                      let u = try? await p.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                parts.append(u.absoluteString)
            }
        }
        // Some apps put the text in the item itself rather than an attachment.
        if parts.isEmpty, let t = (ctx?.inputItems.first as? NSExtensionItem)?.attributedContentText?.string { parts.append(t) }
        let text = parts.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let engine = EngineStore.shared else { failed = true; return }
        self.text = text
        withAnimation(.spring(duration: 0.4)) { analysis = engine.analyze(text) }
    }
}

struct ShareSheet: View {
    let state: ShareState
    let done: () -> Void
    private var l: L { L(bn: Lang.saved.map { $0 != "en" } ?? true) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let t = state.text, let a = state.analysis {
                        ResultView(text: t, analysis: a, l: l)
                    } else if state.failed {
                        Text(l.t("No text found to check. Copy the message and paste it in the BartaGuard app instead.",
                                 "যাচাই করার মতো লেখা পাওয়া যায়নি। মেসেজটি কপি করে BartaGuard অ্যাপে পেস্ট করুন।"))
                            .padding(20).glassCard()
                    } else {
                        ProgressView().frame(maxWidth: .infinity).padding(40)
                    }
                }
                .padding(16)
            }
            .background(Backdrop(tint: state.analysis?.verdict.color))
            .navigationTitle("BartaGuard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(l.t("Done", "ঠিক আছে"), action: done) } }
        }
    }
}
