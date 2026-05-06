import Foundation

enum TurnMessageRegexCache {
    static let thinkingSummaryLine = try? NSRegularExpression(pattern: #"^\s*\*\*(.+?)\*\*\s*$"#)
}
