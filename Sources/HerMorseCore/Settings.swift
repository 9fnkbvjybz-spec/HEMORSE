import Foundation

/// Same JSON keys and defaults as Android v0.4.
public struct Settings: Codable, Equatable {
    public var keySource: String = "virtual"
    public var keyMode: String = "straight"
    public var swapPaddles: Bool = false
    public var keyHeight: Int = 220
    public var output: String = "auto"
    public var sidetone: Bool = true
    public var toneHz: Int = 600
    public var volume: Double = 0.6
    public var vibration: Bool = false
    public var keyerWpm: Int = 12
    public var adaptiveDecode: Bool = true
    public var fixedWpm: Int = 15
    public var listenWpm: Int = 15
    public var farnsworthWpm: Int = 8
    public var useLetters: Bool = true
    public var useDigits: Bool = true
    public var usePunct: Bool = false
    public var useProsigns: Bool = false
    public var useAccents: Bool = false
    public var guided: Bool = true
    public var showHint: Bool = true
    public var qsoSensitivity: Double = 0.5
    public var qsoAfc: Bool = true
    public var qsoFreq: Int = 600
    public var callsign: String = ""
    public var theme: String = "system"
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: JSONKey.self)
        keySource = try c.defaulted("keySource", "virtual")
        keyMode = try c.defaulted("keyMode", "straight")
        swapPaddles = try c.defaulted("swapPaddles", false)
        keyHeight = try c.defaulted("keyHeight", 220)
        output = try c.defaulted("output", "auto")
        sidetone = try c.defaulted("sidetone", true)
        toneHz = try c.defaulted("toneHz", 600)
        volume = try c.defaulted("volume", 0.6)
        vibration = try c.defaulted("vibration", false)
        keyerWpm = try c.defaulted("keyerWpm", 12)
        adaptiveDecode = try c.defaulted("adaptiveDecode", true)
        fixedWpm = try c.defaulted("fixedWpm", 15)
        listenWpm = try c.defaulted("listenWpm", 15)
        farnsworthWpm = try c.defaulted("farnsworthWpm", 8)
        useLetters = try c.defaulted("useLetters", true)
        useDigits = try c.defaulted("useDigits", true)
        usePunct = try c.defaulted("usePunct", false)
        useProsigns = try c.defaulted("useProsigns", false)
        useAccents = try c.defaulted("useAccents", false)
        guided = try c.defaulted("guided", true)
        showHint = try c.defaulted("showHint", true)
        qsoSensitivity = try c.defaulted("qsoSensitivity", 0.5)
        qsoAfc = try c.defaulted("qsoAfc", true)
        qsoFreq = try c.defaulted("qsoFreq", 600)
        callsign = try c.defaulted("callsign", "")
        theme = try c.defaulted("theme", "system")
        sanitize()
    }
    public mutating func sanitize() {
        keyHeight = min(340, max(90, keyHeight))
        toneHz = min(1200, max(300, toneHz))
        volume = min(1, max(0, volume))
        keyerWpm = min(60, max(5, keyerWpm))
        fixedWpm = min(60, max(5, fixedWpm))
        listenWpm = min(60, max(5, listenWpm))
        farnsworthWpm = min(listenWpm, max(3, farnsworthWpm))
        qsoSensitivity = min(1, max(0, qsoSensitivity))
        qsoFreq = min(1200, max(300, qsoFreq))
    }
}

struct JSONKey: CodingKey {
    var stringValue: String
    var intValue: Int? { nil }
    init(_ value: String) { stringValue = value }
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}
extension KeyedDecodingContainer where Key == JSONKey {
    func defaulted<T: Decodable>(_ key: String, _ fallback: T) throws -> T {
        try decodeIfPresent(T.self, forKey: JSONKey(key)) ?? fallback
    }
}
