import BartaGuardCore
import IdentityLookup

/// iOS SMS filter for messages from unknown senders. Runs on the phone with no network access:
/// likely scams go to Junk, clear promotions to Promotions, everything else is left to iOS.
final class MessageFilterExtension: ILMessageFilterExtension {}

extension MessageFilterExtension: ILMessageFilterQueryHandling, ILMessageFilterCapabilitiesQueryHandling {
    private static let engine = try? BartaGuardEngine()

    func handle(_ queryRequest: ILMessageFilterQueryRequest, context: ILMessageFilterExtensionContext,
                completion: @escaping (ILMessageFilterQueryResponse) -> Void) {
        let response = ILMessageFilterQueryResponse()
        response.action = Self.action(for: queryRequest.messageBody)
        completion(response)
    }

    func handle(_ capabilitiesQueryRequest: ILMessageFilterCapabilitiesQueryRequest, context: ILMessageFilterExtensionContext,
                completion: @escaping (ILMessageFilterCapabilitiesQueryResponse) -> Void) {
        completion(ILMessageFilterCapabilitiesQueryResponse())
    }

    static func action(for body: String?) -> ILMessageFilterAction {
        guard let body, !body.isEmpty, let engine else { return .none }
        switch engine.analyze(body).verdict {
        case .suspicious: return .junk
        case .promotional: return .promotion
        case .normal, .uncertain: return .none
        }
    }
}
