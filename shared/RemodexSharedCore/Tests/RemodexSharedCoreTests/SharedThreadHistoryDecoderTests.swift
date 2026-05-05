import Foundation
import XCTest
@testable import RemodexSharedCore

final class SharedThreadHistoryDecoderTests: XCTestCase {
    func testDecodeFromThreadReadBuildsNormalizedTimelineMessages() {
        let threadObject: [String: JSONValue] = [
            "createdAt": .string("2026-03-30T10:00:00Z"),
            "turns": .array([
                .object([
                    "id": .string("turn-1"),
                    "createdAt": .string("2026-03-30T10:01:00Z"),
                    "items": .array([
                        .object([
                            "id": .string("item-user"),
                            "type": .string("user_message"),
                            "content": .array([
                                .object([
                                    "type": .string("text"),
                                    "text": .string("Hello from user"),
                                ]),
                            ]),
                        ]),
                        .object([
                            "id": .string("item-assistant"),
                            "type": .string("assistant_message"),
                            "content": .array([
                                .object([
                                    "type": .string("output_text"),
                                    "text": .string("Hello from assistant"),
                                ]),
                            ]),
                        ]),
                        .object([
                            "id": .string("item-reasoning"),
                            "type": .string("reasoning"),
                            "summary": .string("Checking local state"),
                        ]),
                        .object([
                            "id": .string("item-command"),
                            "type": .string("command_execution"),
                            "command": .string("swift test"),
                            "status": .string("completed"),
                        ]),
                    ]),
                ]),
            ]),
        ]

        let messages = SharedThreadHistoryDecoder.decodeFromThreadRead(
            threadId: "thread-1",
            threadObject: threadObject
        )

        XCTAssertEqual(messages.count, 4)
        XCTAssertEqual(messages.map(\.role), [.user, .assistant, .system, .system])
        XCTAssertEqual(messages.map(\.kind), [.chat, .chat, .thinking, .commandExecution])
        XCTAssertEqual(messages[0].text, "Hello from user")
        XCTAssertEqual(messages[1].text, "Hello from assistant")
        XCTAssertEqual(messages[2].text, "Checking local state")
        XCTAssertEqual(messages[3].text, "completed swift test")
        XCTAssertEqual(messages.map(\.turnId), ["turn-1", "turn-1", "turn-1", "turn-1"])
    }

    func testDecodeCompletedItemNormalizesPlanFallback() {
        let itemObject: [String: JSONValue] = [
            "type": .string("plan"),
            "summary": .string(""),
        ]

        let item = SharedThreadHistoryDecoder.decodeCompletedItem(itemObject: itemObject)

        XCTAssertEqual(item, SharedCompletedItem(role: .system, kind: .plan, text: "[plan]"))
    }

    func testDecodeThreadReadJSONReturnsSerializableMessages() throws {
        let payload = """
        {
          "createdAt": "2026-03-30T10:00:00Z",
          "turns": [
            {
              "id": "turn-2",
              "items": [
                {
                  "id": "item-file",
                  "type": "file_change",
                  "path": "Docs/android-roadmap.md"
                }
              ]
            }
          ]
        }
        """

        let output = try SharedThreadHistoryDecoder.decodeThreadReadJSON(
            threadId: "thread-2",
            threadObjectJSON: payload
        )

        XCTAssertTrue(output.contains("\"threadId\":\"thread-2\""))
        XCTAssertTrue(output.contains("\"kind\":\"fileChange\""))
        XCTAssertTrue(output.contains("\"text\":\"Docs/android-roadmap.md\""))
    }
}
