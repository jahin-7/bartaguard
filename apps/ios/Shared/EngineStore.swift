import BartaGuardCore
import Foundation

/// One engine per process. Loading the bundled model takes ~30 ms.
enum EngineStore {
    static let shared: BartaGuardEngine? = try? BartaGuardEngine()
}

/// Language choice, shared with the Share extension through the standard defaults of each process
/// (no App Group, so the extension falls back to the device language).
enum Lang {
    static var saved: String? {
        get { UserDefaults.standard.string(forKey: "lang") }
        set { UserDefaults.standard.set(newValue, forKey: "lang") }
    }
    static var deviceIsBangla: Bool { Locale.preferredLanguages.first?.hasPrefix("bn") ?? false }
}
