// FILE: FileAutocompletePanel.swift
// Purpose: Autocomplete dropdown for mixed @-plugin and @-file mentions.
// Layer: View Component
// Exports: FileAutocompletePanel
// Depends on: SwiftUI, AutocompleteRowButtonStyle

import SwiftUI

struct FileAutocompletePanel: View {
    let pluginItems: [CodexPluginMetadata]
    let fileItems: [CodexFuzzyFileMatch]
    let isLoading: Bool
    let query: String
    let onSelectFile: (CodexFuzzyFileMatch) -> Void
    let onSelectPlugin: (CodexPluginMetadata) -> Void

    private static let fileRowHeight: CGFloat = 38
    private static let pluginRowHeight: CGFloat = 50
    private static let maxPanelHeight: CGFloat = 320

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if isLoading {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                    Text("Searching files and plugins...")
                        .font(AppFont.footnote())
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
            } else if pluginItems.isEmpty, fileItems.isEmpty {
                Text("No files or plugins for @\(query)")
                    .font(AppFont.footnote())
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        if !pluginItems.isEmpty {
                            sectionHeader("Plugins")
                            ForEach(pluginItems) { plugin in
                                pluginRow(plugin)
                            }
                        }

                        if !fileItems.isEmpty {
                            sectionHeader("Files")
                            ForEach(fileItems) { item in
                                fileRow(item)
                            }
                        }
                    }
                }
                .scrollIndicators(.visible)
                .frame(maxHeight: Self.maxPanelHeight)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .padding(4)
        .adaptiveGlass(.regular, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .padding(.horizontal, 4)
    }

    @ViewBuilder
    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AppFont.caption(weight: .semibold))
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
            .padding(.horizontal, 12)
            .padding(.top, 8)
            .padding(.bottom, 4)
    }

    @ViewBuilder
    private func pluginRow(_ plugin: CodexPluginMetadata) -> some View {
        Button {
            HapticFeedback.shared.triggerImpactFeedback(style: .light)
            onSelectPlugin(plugin)
        } label: {
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    Text(plugin.displayName)
                        .font(AppFont.subheadline(weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Spacer(minLength: 8)

                    Text("@\(plugin.invocationToken)")
                        .font(AppFont.footnote())
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                if let description = SkillAutocompletePanel.descriptionLabel(from: plugin.description) {
                    Text(description)
                        .font(AppFont.caption2())
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: Self.pluginRowHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(AutocompleteRowButtonStyle())
    }

    @ViewBuilder
    private func fileRow(_ item: CodexFuzzyFileMatch) -> some View {
        Button {
            HapticFeedback.shared.triggerImpactFeedback(style: .light)
            onSelectFile(item)
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.fileName)
                    .font(AppFont.subheadline(weight: .semibold))
                    .lineLimit(1)

                Text(item.path)
                    .font(AppFont.caption())
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: Self.fileRowHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(AutocompleteRowButtonStyle())
    }
}
