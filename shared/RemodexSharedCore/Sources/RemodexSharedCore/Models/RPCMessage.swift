import Foundation

public typealias RPCObject = [String: JSONValue]

public struct RPCMessage: Codable, Sendable {
    public let jsonrpc: String?
    public let id: JSONValue?
    public let method: String?
    public let params: JSONValue?
    public let result: JSONValue?
    public let error: RPCError?

    public init(
        id: JSONValue? = nil,
        method: String,
        params: JSONValue? = nil,
        includeJSONRPC: Bool = true
    ) {
        self.jsonrpc = includeJSONRPC ? "2.0" : nil
        self.id = id
        self.method = method
        self.params = params
        self.result = nil
        self.error = nil
    }

    public init(
        id: JSONValue?,
        result: JSONValue,
        includeJSONRPC: Bool = true
    ) {
        self.jsonrpc = includeJSONRPC ? "2.0" : nil
        self.id = id
        self.method = nil
        self.params = nil
        self.result = result
        self.error = nil
    }

    public init(
        id: JSONValue?,
        error: RPCError,
        includeJSONRPC: Bool = true
    ) {
        self.jsonrpc = includeJSONRPC ? "2.0" : nil
        self.id = id
        self.method = nil
        self.params = nil
        self.result = nil
        self.error = error
    }

    public init(
        jsonrpc: String? = "2.0",
        id: JSONValue? = nil,
        method: String? = nil,
        params: JSONValue? = nil,
        result: JSONValue? = nil,
        error: RPCError? = nil
    ) {
        self.jsonrpc = jsonrpc
        self.id = id
        self.method = method
        self.params = params
        self.result = result
        self.error = error
    }
}

public extension RPCMessage {
    var isRequest: Bool {
        method != nil
    }

    var isResponse: Bool {
        result != nil || error != nil
    }

    var isErrorResponse: Bool {
        error != nil
    }
}

public struct RPCError: Codable, Error, Sendable {
    public let code: Int
    public let message: String
    public let data: JSONValue?

    public init(code: Int, message: String, data: JSONValue? = nil) {
        self.code = code
        self.message = message
        self.data = data
    }
}
