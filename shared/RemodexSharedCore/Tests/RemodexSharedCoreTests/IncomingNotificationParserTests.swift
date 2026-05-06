import XCTest
@testable import RemodexSharedCore

final class IncomingNotificationParserTests: XCTestCase {
    func testLifecycleTurnIDFallsBackToTopLevelID() {
        let params: RPCObject = [
            "id": .string("turn-123"),
        ]

        XCTAssertEqual(
            IncomingNotificationParser.extractTurnIDForLifecycleEvent(params),
            "turn-123"
        )
    }

    func testTextDeltaReadsEnvelopeVariants() {
        let params: RPCObject = [
            "event": .object([
                "delta": .string("  hello  "),
            ]),
        ]

        XCTAssertEqual(
            IncomingNotificationParser.extractTextDelta(params),
            "hello"
        )
    }

    func testIncomingItemObjectAcceptsRawItemPayload() {
        let params: RPCObject = [
            "type": .string("reasoning"),
            "content": .array([]),
        ]

        let item = IncomingNotificationParser.extractIncomingItemObject(params: params, event: nil)
        XCTAssertEqual(item?["type"]?.stringValue, "reasoning")
    }
}
