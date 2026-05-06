import Foundation

public enum IncomingNotificationParser {
    public static func envelopeEvent(_ params: RPCObject?) -> RPCObject? {
        params?["msg"]?.objectValue ?? params?["event"]?.objectValue
    }

    public static func extractThreadID(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        if let value = normalizeIdentifier(params["threadId"]?.stringValue) {
            return value
        }
        if let value = normalizeIdentifier(params["thread_id"]?.stringValue) {
            return value
        }
        if let value = normalizeIdentifier(params["thread"]?.objectValue?["id"]?.stringValue) {
            return value
        }

        let event = envelopeEvent(params)
        if let value = normalizeIdentifier(event?["threadId"]?.stringValue) {
            return value
        }
        if let value = normalizeIdentifier(event?["thread_id"]?.stringValue) {
            return value
        }
        if let value = normalizeIdentifier(event?["thread"]?.objectValue?["id"]?.stringValue) {
            return value
        }
        return nil
    }

    public static func extractTurnID(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        if let value = trimmedNonEmptyString(params["turn"]?.objectValue?["id"]?.stringValue) {
            return value
        }
        if let value = trimmedNonEmptyString(params["turnId"]?.stringValue) {
            return value
        }
        if let value = trimmedNonEmptyString(params["turn_id"]?.stringValue) {
            return value
        }

        let event = envelopeEvent(params)
        if let value = trimmedNonEmptyString(event?["turnId"]?.stringValue) {
            return value
        }
        if let value = trimmedNonEmptyString(event?["turn_id"]?.stringValue) {
            return value
        }
        if let value = trimmedNonEmptyString(event?["turn"]?.objectValue?["id"]?.stringValue) {
            return value
        }

        return nil
    }

    public static func extractTurnIDForLifecycleEvent(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        if let value = extractTurnID(params) {
            return value
        }
        if let value = normalizeIdentifier(params["id"]?.stringValue) {
            return value
        }

        let event = envelopeEvent(params)
        if let value = normalizeIdentifier(event?["id"]?.stringValue) {
            return value
        }
        if let value = normalizeIdentifier(params["event"]?.objectValue?["id"]?.stringValue) {
            return value
        }
        return nil
    }

    public static func extractItemID(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        let event = envelopeEvent(params)
        let candidates: [String?] = [
            params["itemId"]?.stringValue,
            params["item_id"]?.stringValue,
            params["call_id"]?.stringValue,
            params["callId"]?.stringValue,
            params["item"]?.objectValue?["id"]?.stringValue,
            event?["itemId"]?.stringValue,
            event?["item_id"]?.stringValue,
            event?["call_id"]?.stringValue,
            event?["callId"]?.stringValue,
            event?["item"]?.objectValue?["id"]?.stringValue,
        ]

        return firstNonEmptyString(candidates)
    }

    public static func extractTextDelta(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        let event = envelopeEvent(params)
        let nested = params["event"]?.objectValue
        let candidates: [String?] = [
            params["delta"]?.stringValue,
            params["textDelta"]?.stringValue,
            params["text_delta"]?.stringValue,
            params["text"]?.stringValue,
            params["summary"]?.stringValue,
            params["part"]?.stringValue,
            event?["delta"]?.stringValue,
            event?["text"]?.stringValue,
            event?["summary"]?.stringValue,
            event?["part"]?.stringValue,
            nested?["delta"]?.stringValue,
            nested?["text"]?.stringValue,
        ]

        return firstNonEmptyString(candidates)
    }

    public static func extractIncomingItemObject(
        params: RPCObject,
        event: RPCObject?
    ) -> RPCObject? {
        if let item = params["item"]?.objectValue {
            return item
        }
        if let item = event?["item"]?.objectValue {
            return item
        }
        if let item = params["event"]?.objectValue?["item"]?.objectValue {
            return item
        }
        if isLikelyIncomingItemPayload(params) {
            return params
        }
        if let event, isLikelyIncomingItemPayload(event) {
            return event
        }
        if let nested = params["event"]?.objectValue, isLikelyIncomingItemPayload(nested) {
            return nested
        }
        return nil
    }

    public static func extractAssistantDelta(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        if let value = extractTextDelta(params) {
            return value
        }

        let event = envelopeEvent(params)
        let candidates: [String?] = [
            params["delta"]?.stringValue,
            params["text"]?.stringValue,
            event?["delta"]?.stringValue,
            event?["text"]?.stringValue,
        ]
        return firstNonEmptyString(candidates)
    }

    public static func extractErrorMessage(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        let event = envelopeEvent(params)
        let candidates: [String?] = [
            params["message"]?.stringValue,
            params["error"]?.objectValue?["message"]?.stringValue,
            event?["message"]?.stringValue,
            event?["error"]?.objectValue?["message"]?.stringValue,
        ]
        return firstNonEmptyString(candidates) ?? "Server error"
    }

    public static func extractUserMirrorText(_ params: RPCObject?) -> String? {
        guard let params else { return nil }

        let candidates: [String?] = [
            params["message"]?.stringValue,
            params["text"]?.stringValue,
            envelopeEvent(params)?["message"]?.stringValue,
        ]
        return firstNonEmptyString(candidates)
    }
}

private extension IncomingNotificationParser {
    static func isLikelyIncomingItemPayload(_ object: RPCObject) -> Bool {
        guard let type = object["type"]?.stringValue else {
            return false
        }
        if normalizedItemType(type).isEmpty {
            return false
        }
        return object.keys.contains {
            $0 == "content"
                || $0 == "status"
                || $0 == "output"
                || $0 == "changes"
                || $0 == "files"
                || $0 == "diff"
                || $0 == "patch"
                || $0 == "result"
                || $0 == "payload"
                || $0 == "data"
        }
    }

    static func normalizeIdentifier(_ value: String?) -> String? {
        trimmedNonEmptyString(value)
    }

    static func normalizedItemType(_ raw: String) -> String {
        raw
            .replacingOccurrences(of: "_", with: "")
            .replacingOccurrences(of: "-", with: "")
            .lowercased()
    }

    static func firstNonEmptyString(_ values: [String?]) -> String? {
        for value in values {
            if let trimmed = trimmedNonEmptyString(value) {
                return trimmed
            }
        }
        return nil
    }

    static func trimmedNonEmptyString(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
