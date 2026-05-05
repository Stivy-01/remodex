import Foundation

public enum SharedTimelineRole: String, Codable, Hashable, Sendable {
    case user
    case assistant
    case system
}

public enum SharedTimelineKind: String, Codable, Hashable, Sendable {
    case chat
    case thinking
    case fileChange
    case commandExecution
    case plan
}

public struct SharedTimelineMessage: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let threadId: String
    public let role: SharedTimelineRole
    public let kind: SharedTimelineKind
    public let text: String
    public let createdAt: Date
    public let turnId: String?
    public let itemId: String?
    public let isStreaming: Bool

    public init(
        id: String = UUID().uuidString,
        threadId: String,
        role: SharedTimelineRole,
        kind: SharedTimelineKind,
        text: String,
        createdAt: Date,
        turnId: String? = nil,
        itemId: String? = nil,
        isStreaming: Bool = false
    ) {
        self.id = id
        self.threadId = threadId
        self.role = role
        self.kind = kind
        self.text = text
        self.createdAt = createdAt
        self.turnId = turnId
        self.itemId = itemId
        self.isStreaming = isStreaming
    }
}

public struct SharedCompletedItem: Equatable, Sendable {
    public let role: SharedTimelineRole
    public let kind: SharedTimelineKind
    public let text: String

    public init(role: SharedTimelineRole, kind: SharedTimelineKind, text: String) {
        self.role = role
        self.kind = kind
        self.text = text
    }
}
