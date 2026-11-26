import BartaGuardCore
import SwiftUI

/// Large-title page over the colour field; content scrolls under the glass tab bar.
struct Page<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    var tint: Color? = nil
    @ViewBuilder let content: Content

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let subtitle { Text(subtitle).font(.subheadline).foregroundStyle(.secondary).padding(.horizontal, 4) }
                    content
                }
                .padding(.horizontal, 16).padding(.bottom, 32)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Backdrop(tint: tint))
            .navigationTitle(title)
        }
    }
}

// MARK: - First open

struct LanguageView: View {
    let choose: (String) -> Void

    var body: some View {
        VStack(spacing: 14) {
            Spacer()
            Image("Logo").resizable().frame(width: 104, height: 104)
                .clipShape(.rect(cornerRadius: 24, style: .continuous))
                .shadow(color: .black.opacity(0.25), radius: 18, y: 8)
            Text("BartaGuard").font(.system(size: 34, weight: .bold)).padding(.top, 10)
            Text("বার্তা যাচাই · Check a message before you trust it").font(.subheadline).foregroundStyle(.secondary)
                .multilineTextAlignment(.center).padding(.bottom, 22)
            Button { choose("bn") } label: { Text("বাংলা").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6) }.glassButton(prominent: true)
            Button { choose("en") } label: { Text("English").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6) }.glassButton()
            Text("Works offline. Messages never leave your phone.\nঅফলাইনে কাজ করে। মেসেজ ফোনের বাইরে যায় না।")
                .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.top, 10)
            Spacer()
        }
        .padding(24)
        .controlSize(.large)
    }
}

// MARK: - Check

struct CheckView: View {
    @Environment(AppModel.self) private var m
    @FocusState private var focused: Bool

    var body: some View {
        @Bindable var m = m
        let l = m.l
        Page(title: l.t("Check a message", "মেসেজ যাচাই"), subtitle: l.t("On your phone. Nothing is uploaded.", "আপনার ফোনেই। কিছুই আপলোড হয় না।"),
             tint: m.checked?.analysis.verdict.color) {
            if let c = m.checked {
                ResultView(text: c.text, analysis: c.analysis, l: l)
                Button { m.reset() } label: {
                    Label(l.t("Check another message", "আরেকটি মেসেজ যাচাই"), systemImage: "arrow.counterclockwise").frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .glassButton().controlSize(.large)
            } else {
                VStack(spacing: 12) {
                    ZStack(alignment: .topLeading) {
                        if m.input.isEmpty {
                            Text(l.t("Paste or type the SMS, WhatsApp or Messenger message here…", "এখানে SMS, হোয়াটসঅ্যাপ বা মেসেঞ্জারের মেসেজটি পেস্ট করুন বা লিখুন…"))
                                .foregroundStyle(.tertiary).padding(.top, 8).padding(.leading, 5)
                        }
                        TextEditor(text: $m.input).focused($focused).scrollContentBackground(.hidden).frame(minHeight: 140)
                    }
                    .font(.body)
                    HStack(spacing: 10) {
                        // PasteButton reads the clipboard only when tapped, so iOS shows no paste prompt.
                        PasteButton(payloadType: String.self) { items in
                            if let s = items.first { Task { @MainActor in m.input = s } }
                        }
                        .labelStyle(.titleAndIcon).buttonBorderShape(.capsule)
                        Button { focused = false; m.check() } label: {
                            Label(l.t("Check", "যাচাই করুন"), systemImage: "magnifyingglass").frame(maxWidth: .infinity)
                        }
                        .glassButton(prominent: true)
                        .disabled(m.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                    .controlSize(.large)
                }
                .padding(16).glassCard()

                tip("message.badge.filled.fill", l.t("Unknown senders' SMS are checked automatically", "অচেনা নম্বরের SMS নিজে থেকেই যাচাই হয়"),
                    l.t("Turn on BartaGuard as your SMS filter in Settings. Likely scams go to the Junk folder.", "সেটিংসে BartaGuard-কে SMS ফিল্টার হিসেবে চালু করুন। সম্ভাব্য প্রতারণা জাঙ্ক ফোল্ডারে যাবে।"))
                    .contentShape(.rect).onTapGesture { m.showFilterSetup = true }
                tip("square.and.arrow.up", l.t("WhatsApp, Messenger, imo…", "হোয়াটসঅ্যাপ, মেসেঞ্জার, imo…"),
                    l.t("Long-press a message → Share → BartaGuard. The check opens right there.", "মেসেজ চেপে ধরুন → শেয়ার → BartaGuard। সেখানেই যাচাই দেখাবে।"))
            }
        }
    }

    private func tip(_ symbol: String, _ title: String, _ body: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol).font(.title3).foregroundStyle(.blue).frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(body).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(18).glassCard()
    }
}

// MARK: - Links

struct LinksView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        @Bindable var m = m
        let l = m.l
        Page(title: l.t("Check a link", "লিংক যাচাই"), subtitle: l.t("We read the address. We never open it.", "আমরা শুধু ঠিকানাটি পড়ি, কখনো খুলি না।")) {
            VStack(spacing: 12) {
                TextField("bit.ly/… · bkash-verify.xyz", text: $m.linkInput)
                    .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().submitLabel(.search)
                    .onSubmit { m.inspect() }.padding(.vertical, 6)
                Button { m.inspect() } label: { Label(l.t("Inspect", "পরীক্ষা করুন"), systemImage: "magnifyingglass").frame(maxWidth: .infinity) }
                    .glassButton(prominent: true).controlSize(.large).disabled(m.linkInput.isEmpty)
            }
            .padding(16).glassCard()

            if let info = m.link {
                let bad = info.flags.contains { $0 != "not_https" }
                let c: Color = bad ? .red : info.flags.isEmpty ? .green : .orange
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 12) {
                        Image(systemName: bad ? "exclamationmark.triangle.fill" : "lock.fill").font(.title2).foregroundStyle(c)
                        VStack(alignment: .leading) {
                            Text(bad ? l.t("Don't open this link", "এই লিংকে যাবেন না") : info.flags.isEmpty ? l.t("No warning signs in the address", "ঠিকানায় সতর্কসংকেত নেই") : l.t("Be careful", "সাবধান"))
                                .font(.title3.bold())
                            Text(info.host.isEmpty ? info.url : info.host).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    ForEach(info.flags, id: \.self) { f in
                        HStack(alignment: .top, spacing: 10) {
                            Circle().fill(f == "not_https" ? .orange : .red).frame(width: 8, height: 8).padding(.top, 6)
                            Text(l.linkFlag(f)).font(.subheadline)
                        }
                    }
                    Text(l.t("A clean address isn't proof a site is safe. When money is involved, type the official address yourself.",
                             "ঠিকানা ঠিক থাকলেই সাইট নিরাপদ নয়। টাকার বিষয় হলে অফিসিয়াল ঠিকানা নিজে টাইপ করুন।"))
                        .font(.footnote).foregroundStyle(.secondary).padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading).padding(20).glassCard(tint: c)
            }
        }
    }
}

// MARK: - Learn

struct LearnView: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Int> = [0]

    var body: some View {
        let l = m.l
        Page(title: l.t("Common scams", "প্রচলিত প্রতারণা"), subtitle: l.t("Know the tricks before they reach you.", "কৌশলগুলো আগে থেকেই জেনে রাখুন।")) {
            ForEach(guides) { g in
                VStack(alignment: .leading, spacing: 10) {
                    Button {
                        withAnimation(.snappy) { if open.contains(g.id) { open.remove(g.id) } else { open.insert(g.id) } }
                    } label: {
                        HStack {
                            Text(l.t(g.en, g.bn)).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                            Spacer()
                            Image(systemName: "chevron.down").rotationEffect(.degrees(open.contains(g.id) ? 180 : 0)).foregroundStyle(.secondary)
                        }
                    }
                    if open.contains(g.id) {
                        Text(l.t(g.signsEn, g.signsBn)).font(.subheadline).foregroundStyle(.secondary)
                        Label(l.t(g.doEn, g.doBn), systemImage: "checkmark.circle.fill").font(.subheadline)
                            .symbolRenderingMode(.multicolor).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                            .background(.green.opacity(0.12), in: .rect(cornerRadius: 16))
                    }
                }
                .padding(20).glassCard()
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(l.t("In danger or being threatened? Call 999.", "বিপদে বা হুমকির মুখে? ৯৯৯-এ কল করুন।")).font(.headline)
                Text(l.t("For money sent to a scammer, call your bank's or MFS provider's official helpline right away. The number is in their official app.",
                         "প্রতারকের কাছে টাকা গেলে এখনই আপনার ব্যাংক বা MFS-এর অফিসিয়াল হেল্পলাইনে ফোন করুন। নম্বরটি তাদের অফিসিয়াল অ্যাপে আছে।"))
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(20).glassCard()
        }
    }
}

// MARK: - Settings

struct SettingsView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        @Bindable var m = m
        let l = m.l
        Page(title: l.t("Settings", "সেটিংস")) {
            section(l.t("Language", "ভাষা"))
            Picker("", selection: Binding(get: { m.lang ?? "bn" }, set: { m.lang = $0 })) {
                Text("বাংলা").tag("bn"); Text("English").tag("en")
            }
            .pickerStyle(.segmented)

            section(l.t("Appearance", "থিম"))
            Picker("", selection: $m.appearance) {
                Text(l.t("Automatic", "অটো")).tag("system"); Text(l.t("Light", "লাইট")).tag("light"); Text(l.t("Dark", "ডার্ক")).tag("dark")
            }
            .pickerStyle(.segmented)

            section(l.t("Automatic SMS checking", "স্বয়ংক্রিয় SMS যাচাই"))
            VStack(alignment: .leading, spacing: 10) {
                Text(l.t("Turn on the SMS filter", "SMS ফিল্টার চালু করুন")).font(.headline)
                ForEach(Array(filterSteps(l).enumerated()), id: \.offset) { i, s in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(l.n(i + 1)).font(.subheadline.bold()).foregroundStyle(.blue).frame(width: 18)
                        Text(s).font(.subheadline)
                    }
                }
                Text(l.t("iPhone lets filters see only SMS from numbers that aren't in your contacts, and never iMessage or WhatsApp. For those, use Share → BartaGuard. Filtered messages are checked on the phone; nothing is sent anywhere.",
                         "আইফোনে ফিল্টার শুধু কন্টাক্টের বাইরের নম্বরের SMS দেখতে পায়, iMessage বা হোয়াটসঅ্যাপ নয়। সেগুলোর জন্য শেয়ার → BartaGuard ব্যবহার করুন। ফিল্টারের যাচাই ফোনেই হয়; কিছুই কোথাও পাঠানো হয় না।"))
                    .font(.footnote).foregroundStyle(.secondary).padding(.top, 4)
                Button { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } } label: {
                    Label(l.t("Open Settings", "সেটিংস খুলুন"), systemImage: "gear").frame(maxWidth: .infinity)
                }
                .glassButton().controlSize(.large).padding(.top, 4)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(20).glassCard()

            section(l.t("History", "ইতিহাস"))
            VStack(alignment: .leading, spacing: 12) {
                Toggle(isOn: $m.keepHistory) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l.t("Keep a list of my checks", "যাচাইয়ের তালিকা রাখুন")).font(.headline)
                        Text(l.t("Off by default. Stored only on this phone, first 90 characters only.", "ডিফল্ট বন্ধ। শুধু এই ফোনে, প্রথম ৯০ অক্ষর পর্যন্ত।"))
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .tint(.green)
                if m.keepHistory && !m.history.isEmpty {
                    ForEach(m.history.prefix(20)) { h in
                        let v = Verdict(rawValue: h.verdict) ?? .uncertain
                        HStack(spacing: 10) {
                            Circle().fill(v.color).frame(width: 8, height: 8)
                            VStack(alignment: .leading) {
                                Text(h.preview).font(.subheadline).lineLimit(1)
                                Text("\(l.title(v)) · \(h.time.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    Button(l.t("Clear history", "ইতিহাস মুছুন"), role: .destructive) { m.clearHistory() }.font(.subheadline.weight(.semibold))
                }
            }
            .padding(20).glassCard()

            section(l.t("Privacy and accuracy", "গোপনীয়তা ও নির্ভুলতা"))
            VStack(alignment: .leading, spacing: 10) {
                Text(l.t("Every check runs on this phone. BartaGuard has no account, no ads and never contacts a server.",
                         "প্রতিটি যাচাই এই ফোনেই হয়। BartaGuard-এ কোনো অ্যাকাউন্ট বা বিজ্ঞাপন নেই, আর এটি কোনো সার্ভারে যোগাযোগ করে না।"))
                Text(l.t("On held-out test messages it flagged 90% of scams as suspicious and called 2% safe. For SMS scams that miss rate is higher (15%), and it was trained on Bangla only. Use it as a second opinion, not a guarantee.",
                         "পরীক্ষার মেসেজে এটি ৯০% প্রতারণাকে সন্দেহজনক বলেছে এবং ২%-কে নিরাপদ বলেছে। SMS প্রতারণায় ভুলের হার বেশি (১৫%), আর এটি শুধু বাংলায় প্রশিক্ষিত। এটিকে দ্বিতীয় মতামত হিসেবে নিন, নিশ্চয়তা হিসেবে নয়।"))
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            .padding(20).glassCard()
        }
    }

    private func section(_ s: String) -> some View {
        Text(s.uppercased()).font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.leading, 8).padding(.top, 8)
    }
}

func filterSteps(_ l: L) -> [String] {
    [l.t("Open the Settings app → Apps → Messages.", "সেটিংস অ্যাপ খুলুন → Apps → Messages।"),
     l.t("Tap Unknown & Spam (Text Message Filter on older iOS).", "Unknown & Spam-এ চাপুন (পুরনো iOS-এ Text Message Filter)।"),
     l.t("Choose BartaGuard.", "BartaGuard বেছে নিন।")]
}

// MARK: - Setup sheet

/// The SMS filter steps as a sheet. iOS doesn't tell apps whether their filter is on, so these
/// are instructions, not a live checklist.
struct FilterSetupSheet: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var shown = false

    var body: some View {
        let l = m.l
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                Image(systemName: "message.badge.filled.fill").font(.title).foregroundStyle(.blue)
                    .symbolEffect(.bounce, value: shown)
                VStack(alignment: .leading, spacing: 2) {
                    Text(l.t("Check SMS automatically", "SMS নিজে থেকেই যাচাই")).font(.title2.bold())
                    Text(l.t("Likely scams from unknown numbers go to Junk.", "অচেনা নম্বরের সম্ভাব্য প্রতারণা জাঙ্কে যাবে।"))
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            }
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array(filterSteps(l).enumerated()), id: \.offset) { i, s in
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Text(l.n(i + 1)).font(.subheadline.bold()).foregroundStyle(.white)
                            .frame(width: 26, height: 26).background(.blue, in: .circle)
                        Text(s).font(.body)
                    }
                    .opacity(shown ? 1 : 0).offset(y: shown ? 0 : 12)
                    .animation(.spring(duration: 0.5, bounce: 0.3).delay(0.08 * Double(i) + 0.1), value: shown)
                }
            }
            Text(l.t("Nothing is sent anywhere. iPhone only shows filters SMS from numbers not in your contacts.",
                     "কিছুই কোথাও পাঠানো হয় না। আইফোন ফিল্টারকে শুধু কন্টাক্টের বাইরের নম্বরের SMS দেখায়।"))
                .font(.footnote).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            Button { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } } label: {
                Label(l.t("Open Settings", "সেটিংস খুলুন"), systemImage: "gear").frame(maxWidth: .infinity).padding(.vertical, 4)
            }
            .glassButton(prominent: true).controlSize(.large)
            Button { dismiss() } label: { Text(l.t("Later", "পরে করব")).frame(maxWidth: .infinity).padding(.vertical, 4) }
                .glassButton().controlSize(.large)
        }
        .padding(24).padding(.top, 8)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .sensoryFeedback(.impact(weight: .light), trigger: shown)
        .onAppear { shown = true }
    }
}
