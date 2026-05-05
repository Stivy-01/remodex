import Foundation

public enum SharedThreadHistoryDecoder {
    public static func decodeFromThreadRead(
        threadId: String,
        threadObject: [String: JSONValue]
    ) -> [SharedTimelineMessage] {
        let base = decodeBaseDate(threadObject)
        let turns = threadObject["turns"]?.arrayValue ?? []
        var offset: TimeInterval = 0
        var out: [SharedTimelineMessage] = []

        for turnValue in turns {
            guard let turnObject = turnValue.objectValue else { continue }
            let turnId = trimmed(turnObject["id"]?.stringValue)
            let turnDate = decodeDate(turnObject) ?? base
            let items = turnObject["items"]?.arrayValue ?? []

            for itemValue in items {
                guard let itemObject = itemValue.objectValue,
                      let itemType = itemObject["type"]?.stringValue else {
                    continue
                }

                let syntheticDate = turnDate.addingTimeInterval(offset)
                offset += 0.001
                let createdAt = decodeDate(itemObject) ?? syntheticDate
                let itemId = trimmed(itemObject["id"]?.stringValue)
                let text = decodeItemText(itemObject)

                switch normalizedItemType(itemType) {
                case "usermessage":
                    append(
                        to: &out,
                        threadId: threadId,
                        role: .user,
                        kind: .chat,
                        text: text,
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                case "agentmessage", "assistantmessage":
                    append(
                        to: &out,
                        threadId: threadId,
                        role: .assistant,
                        kind: .chat,
                        text: text,
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                case "message":
                    let roleRaw = itemObject["role"]?.stringValue?.lowercased() ?? ""
                    let role: SharedTimelineRole = roleRaw.contains("user") ? .user : .assistant
                    append(
                        to: &out,
                        threadId: threadId,
                        role: role,
                        kind: .chat,
                        text: text,
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                case "reasoning":
                    append(
                        to: &out,
                        threadId: threadId,
                        role: .system,
                        kind: .thinking,
                        text: decodeReasoningText(itemObject),
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                case "filechange":
                    append(
                        to: &out,
                        threadId: threadId,
                        role: .system,
                        kind: .fileChange,
                        text: decodeFileChangePreview(itemObject),
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                case "toolcall", "diff":
                    let preview = decodeToolOrDiffPreview(itemObject)
                    if !preview.isEmpty {
                        append(
                            to: &out,
                            threadId: threadId,
                            role: .system,
                            kind: .fileChange,
                            text: preview,
                            turnId: turnId,
                            itemId: itemId,
                            createdAt: createdAt
                        )
                    }

                case "commandexecution":
                    append(
                        to: &out,
                        threadId: threadId,
                        role: .system,
                        kind: .commandExecution,
                        text: decodeCommandPreview(itemObject),
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                case "plan":
                    append(
                        to: &out,
                        threadId: threadId,
                        role: .system,
                        kind: .plan,
                        text: text.isEmpty ? "[plan]" : text,
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                case "contextcompaction":
                    append(
                        to: &out,
                        threadId: threadId,
                        role: .system,
                        kind: .commandExecution,
                        text: "Context compacted",
                        turnId: turnId,
                        itemId: itemId,
                        createdAt: createdAt
                    )

                default:
                    continue
                }
            }
        }

        return out
    }

    public static func decodeCompletedItem(itemObject: [String: JSONValue]) -> SharedCompletedItem? {
        guard let itemType = itemObject["type"]?.stringValue else {
            return nil
        }

        switch normalizedItemType(itemType) {
        case "usermessage":
            return SharedCompletedItem(role: .user, kind: .chat, text: decodeItemText(itemObject))

        case "agentmessage", "assistantmessage":
            return SharedCompletedItem(role: .assistant, kind: .chat, text: decodeItemText(itemObject))

        case "message":
            let roleRaw = itemObject["role"]?.stringValue?.lowercased() ?? ""
            let role: SharedTimelineRole = roleRaw.contains("user") ? .user : .assistant
            return SharedCompletedItem(role: role, kind: .chat, text: decodeItemText(itemObject))

        case "reasoning":
            return SharedCompletedItem(role: .system, kind: .thinking, text: decodeReasoningText(itemObject))

        case "filechange":
            return SharedCompletedItem(role: .system, kind: .fileChange, text: decodeFileChangePreview(itemObject))

        case "toolcall", "diff":
            let preview = decodeToolOrDiffPreview(itemObject)
            return preview.isEmpty ? nil : SharedCompletedItem(role: .system, kind: .fileChange, text: preview)

        case "commandexecution":
            return SharedCompletedItem(role: .system, kind: .commandExecution, text: decodeCommandPreview(itemObject))

        case "plan":
            let body = decodeItemText(itemObject)
            return SharedCompletedItem(role: .system, kind: .plan, text: body.isEmpty ? "[plan]" : body)

        default:
            return nil
        }
    }

    public static func decodeThreadReadJSON(
        threadId: String,
        threadObjectJSON: String
    ) throws -> String {
        let decoder = JSONDecoder()
        let threadObject = try decoder.decode([String: JSONValue].self, from: Data(threadObjectJSON.utf8))
        let messages = decodeFromThreadRead(threadId: threadId, threadObject: threadObject)

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]

        let data = try encoder.encode(messages)
        guard let text = String(data: data, encoding: .utf8) else {
            throw HistoryDecodeBridgeError.encodingFailed
        }
        return text
    }

    public enum HistoryDecodeBridgeError: Error {
        case encodingFailed
    }

    private static func append(
        to out: inout [SharedTimelineMessage],
        threadId: String,
        role: SharedTimelineRole,
        kind: SharedTimelineKind,
        text: String,
        turnId: String?,
        itemId: String?,
        createdAt: Date
    ) {
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedText.isEmpty && kind != .plan {
            return
        }

        out.append(
            SharedTimelineMessage(
                threadId: threadId,
                role: role,
                kind: kind,
                text: trimmedText,
                createdAt: createdAt,
                turnId: turnId,
                itemId: itemId
            )
        )
    }

    private static func normalizedItemType(_ raw: String) -> String {
        raw
            .replacingOccurrences(of: "_", with: "")
            .replacingOccurrences(of: "-", with: "")
            .lowercased()
    }

    private static func decodeBaseDate(_ threadObject: [String: JSONValue]) -> Date {
        decodeDate(threadObject) ?? Date(timeIntervalSince1970: 0)
    }

    private static func decodeDate(_ object: [String: JSONValue]) -> Date? {
        for key in ["createdAt", "created_at", "updatedAt", "updated_at"] {
            guard let value = object[key] else { continue }
            if let rawString = value.stringValue, let parsed = parseDate(rawString) {
                return parsed
            }
            if let rawNumber = value.doubleValue {
                return unixTimestampToDate(rawNumber)
            }
        }
        return nil
    }

    private static func unixTimestampToDate(_ rawValue: Double) -> Date {
        let secondsValue = rawValue > 10_000_000_000 ? rawValue / 1000 : rawValue
        return Date(timeIntervalSince1970: secondsValue)
    }

    private static func parseDate(_ value: String) -> Date? {
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedValue.isEmpty else { return nil }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let parsed = formatter.date(from: trimmedValue) {
            return parsed
        }

        let fallbackFormatter = ISO8601DateFormatter()
        fallbackFormatter.formatOptions = [.withInternetDateTime]
        return fallbackFormatter.date(from: trimmedValue)
    }

    private static func decodeItemText(_ itemObject: [String: JSONValue]) -> String {
        let contentItems = itemObject["content"]?.arrayValue ?? []
        var parts: [String] = []

        for value in contentItems {
            guard let object = value.objectValue else { continue }
            let inputType = normalizedItemType(object["type"]?.stringValue ?? "")

            if inputType == "text" || inputType == "inputtext" || inputType == "outputtext" || inputType == "message" {
                if let text = object["text"]?.stringValue {
                    parts.append(text)
                }
                continue
            }

            if inputType == "skill" {
                let skillId = trimmed(object["id"]?.stringValue)
                let skillName = trimmed(object["name"]?.stringValue)
                if let resolved = skillId ?? skillName {
                    parts.append("$\(resolved)")
                }
                continue
            }

            if inputType == "text",
               let dataText = object["data"]?.objectValue?["text"]?.stringValue {
                parts.append(dataText)
            }
        }

        let joined = parts.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        if !joined.isEmpty {
            return joined
        }

        if let directText = trimmed(itemObject["text"]?.stringValue) {
            return directText
        }

        if let messageText = trimmed(itemObject["message"]?.stringValue) {
            return messageText
        }

        return ""
    }

    private static func decodeReasoningText(_ itemObject: [String: JSONValue]) -> String {
        for key in ["summary", "text", "content"] {
            if let text = trimmed(itemObject[key]?.stringValue) {
                return text
            }
        }
        let text = decodeItemText(itemObject)
        return text.isEmpty ? "[reasoning]" : text
    }

    private static func decodeFileChangePreview(_ itemObject: [String: JSONValue]) -> String {
        firstString(
            in: itemObject,
            keys: ["path", "filePath", "file_path", "displayPath", "display_path"]
        ) ?? "[file change]"
    }

    private static func decodeToolOrDiffPreview(_ itemObject: [String: JSONValue]) -> String {
        firstString(
            in: itemObject,
            keys: ["command", "title", "summary", "path"]
        ) ?? decodeItemText(itemObject)
    }

    private static func decodeCommandPreview(_ itemObject: [String: JSONValue]) -> String {
        let command = firstString(
            in: itemObject,
            keys: ["command", "cmd", "raw_command", "rawCommand", "input", "invocation"]
        ) ?? "command"

        let status = decodeCommandStatus(itemObject["status"]) ?? "completed"
        let phase: String
        if status.localizedCaseInsensitiveContains("fail") || status.localizedCaseInsensitiveContains("error") {
            phase = "failed"
        } else if status.localizedCaseInsensitiveContains("cancel")
            || status.localizedCaseInsensitiveContains("abort") {
            phase = "stopped"
        } else if status.localizedCaseInsensitiveContains("complete")
            || status.localizedCaseInsensitiveContains("success") {
            phase = "completed"
        } else {
            phase = "running"
        }

        let shortCommand: String
        if command.count > 92 {
            shortCommand = String(command.prefix(91)) + "..."
        } else {
            shortCommand = command
        }

        return "\(phase) \(shortCommand)"
    }

    private static func decodeCommandStatus(_ value: JSONValue?) -> String? {
        switch value {
        case .string(let status):
            return status
        case .object(let object):
            return object["type"]?.stringValue
                ?? object["statusType"]?.stringValue
                ?? object["status"]?.stringValue
        default:
            return value?.stringValue
        }
    }

    private static func firstString(
        in object: [String: JSONValue],
        keys: [String]
    ) -> String? {
        for key in keys {
            if let value = trimmed(object[key]?.stringValue) {
                return value
            }
        }
        return nil
    }

    private static func trimmed(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmedValue.isEmpty ? nil : trimmedValue
    }
}
