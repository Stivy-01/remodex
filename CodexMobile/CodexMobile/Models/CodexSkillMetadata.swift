// FILE: CodexSkillMetadata.swift
// Purpose: Skill/plugin metadata and mention payload types used by composer autocomplete + turn/start.
// Layer: Model
// Exports: CodexSkillMetadata, CodexPluginMetadata, CodexPluginDetails, CodexTurnSkillMention
// Depends on: Foundation

import Foundation

struct CodexSkillMetadata: Decodable, Hashable, Sendable, Identifiable {
    let name: String
    let description: String?
    let path: String?
    let scope: String?
    let enabled: Bool

    var id: String {
        normalizedName
    }

    var normalizedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private enum CodingKeys: String, CodingKey {
        case name
        case description
        case path
        case scope
        case enabled
    }

    init(
        name: String,
        description: String?,
        path: String?,
        scope: String?,
        enabled: Bool
    ) {
        self.name = name
        self.description = description
        self.path = path
        self.scope = scope
        self.enabled = enabled
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        name = try container.decode(String.self, forKey: .name)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        path = try container.decodeIfPresent(String.self, forKey: .path)
        scope = try container.decodeIfPresent(String.self, forKey: .scope)
        enabled = try container.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
    }
}

struct CodexPluginMetadata: Decodable, Hashable, Sendable, Identifiable {
    let id: String
    let name: String?
    let description: String?
    let path: String?
    let enabled: Bool
    let installed: Bool
    let source: String?
    let marketplace: String?
    let scope: String?
    let version: String?

    var normalizedID: String {
        id.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    var normalizedName: String? {
        let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let trimmed, !trimmed.isEmpty else {
            return nil
        }
        return trimmed.lowercased()
    }

    var displayName: String {
        let trimmedName = name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmedName.isEmpty ? id : trimmedName
    }

    // Always uses the stable plugin id for invocation; display labels stay UI-only.
    var invocationToken: String {
        id.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case pluginID = "pluginId"
        case pluginIDSnake = "plugin_id"
        case slug
        case name
        case description
        case summary
        case path
        case directory
        case enabled
        case installed
        case source
        case sourceType = "sourceType"
        case sourceTypeSnake = "source_type"
        case marketplace
        case marketplaceName = "marketplaceName"
        case marketplaceNameSnake = "marketplace_name"
        case scope
        case version
    }

    init(
        id: String,
        name: String?,
        description: String?,
        path: String?,
        enabled: Bool,
        installed: Bool,
        source: String?,
        marketplace: String?,
        scope: String?,
        version: String?
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.path = path
        self.enabled = enabled
        self.installed = installed
        self.source = source
        self.marketplace = marketplace
        self.scope = scope
        self.version = version
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        let rawID = try container.decodeIfPresent(String.self, forKey: .id)
            ?? (try container.decodeIfPresent(String.self, forKey: .pluginID))
            ?? (try container.decodeIfPresent(String.self, forKey: .pluginIDSnake))
            ?? (try container.decodeIfPresent(String.self, forKey: .slug))
            ?? (try container.decodeIfPresent(String.self, forKey: .name))
            ?? ""
        let normalizedID = rawID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedID.isEmpty else {
            throw DecodingError.dataCorruptedError(
                forKey: .id,
                in: container,
                debugDescription: "Plugin payload is missing a stable identifier."
            )
        }

        id = normalizedID
        name = try container.decodeIfPresent(String.self, forKey: .name)
        description = try container.decodeIfPresent(String.self, forKey: .description)
            ?? (try container.decodeIfPresent(String.self, forKey: .summary))
        path = try container.decodeIfPresent(String.self, forKey: .path)
            ?? (try container.decodeIfPresent(String.self, forKey: .directory))
        enabled = try container.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        installed = try container.decodeIfPresent(Bool.self, forKey: .installed) ?? true
        source = try container.decodeIfPresent(String.self, forKey: .source)
            ?? (try container.decodeIfPresent(String.self, forKey: .sourceType))
            ?? (try container.decodeIfPresent(String.self, forKey: .sourceTypeSnake))
        marketplace = try container.decodeIfPresent(String.self, forKey: .marketplace)
            ?? (try container.decodeIfPresent(String.self, forKey: .marketplaceName))
            ?? (try container.decodeIfPresent(String.self, forKey: .marketplaceNameSnake))
        scope = try container.decodeIfPresent(String.self, forKey: .scope)
        version = try container.decodeIfPresent(String.self, forKey: .version)
    }
}

struct CodexPluginDetails: Hashable, Sendable {
    let plugin: CodexPluginMetadata
    let skills: [CodexSkillMetadata]
}

struct CodexTurnSkillMention: Hashable, Sendable {
    let id: String
    let name: String?
    let path: String?
}
