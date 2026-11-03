import Foundation

public struct SeriesRecord: Codable, Equatable {
    public var t: Int64
    public var k: String
    public var l: Int
    public var a: Double
    public var w: Int
    public init(time: Int64, track: String, level: Int, accuracy: Double, wpm: Int) {
        t = time; k = track; l = level; a = accuracy; w = wpm
    }
}

/// Pair values remain JSON arrays [correct, total], compatible with Android exports.
public struct Stats: Codable, Equatable {
    public var totalChars = 0
    public var errors = 0
    public var keyTimeMs: Int64 = 0
    public var recentWpm: [Double] = []
    public var trophies: [String: Int64] = [:]
    public var usage: [String: Int] = [:]
    public var bestWpm = 0.0
    public var rxLevel = 1
    public var txLevel = 1
    public var rxStreak = 0
    public var txStreak = 0
    public var rxSpeed3 = 0
    public var rxChars: [String: [Int]] = [:]
    public var txChars: [String: [Int]] = [:]
    public var series = 0
    public var trainingMs: Int64 = 0
    public var days: [String: Int64] = [:]
    public var history: [SeriesRecord] = []
    public var confusions: [String: Int] = [:]
    public var recentRatio: [Double] = []
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: JSONKey.self)
        totalChars = max(0, try c.defaulted("totalChars", 0))
        errors = max(0, try c.defaulted("errors", 0))
        keyTimeMs = max(0, try c.defaulted("keyTimeMs", 0))
        recentWpm = Array(try c.defaulted("recentWpm", [Double]()).suffix(50))
        trophies = try c.defaulted("trophies", [:])
        usage = try c.defaulted("usage", [:])
        bestWpm = try c.defaulted("bestWpm", 0)
        rxLevel = min(42, max(1, try c.defaulted("rxLevel", 1)))
        txLevel = min(42, max(1, try c.defaulted("txLevel", 1)))
        rxStreak = try c.defaulted("rxStreak", 0)
        txStreak = try c.defaulted("txStreak", 0)
        rxSpeed3 = min(9, max(0, try c.defaulted("rxSpeed3", 0)))
        rxChars = try c.defaulted("rxChars", [:])
        txChars = try c.defaulted("txChars", [:])
        series = max(0, try c.defaulted("series", 0))
        trainingMs = max(0, try c.defaulted("trainingMs", 0))
        days = try c.defaulted("days", [:])
        history = Array(try c.defaulted("history", [SeriesRecord]()).suffix(200))
        confusions = try c.defaulted("confusions", [:])
        recentRatio = Array(try c.defaulted("recentRatio", [Double]()).suffix(50))
        for table in [rxChars, txChars] {
            guard table.values.allSatisfy({ $0.count == 2 && $0[0] >= 0 && $0[1] >= $0[0] }) else {
                throw ProfileError.invalidStatistics
            }
        }
    }
}

public enum ProfileError: Error, LocalizedError {
    case invalidFormat, invalidStatistics, tooLarge
    public var errorDescription: String? {
        switch self {
        case .invalidFormat: return "Ce fichier n’est pas un profil HerMorse version 1."
        case .invalidStatistics: return "Les statistiques du profil sont invalides."
        case .tooLarge: return "Le profil dépasse la taille maximale de 5 Mo."
        }
    }
}

public struct ProfileDocument: Codable, Equatable {
    public var format = "hermorse"
    public var version = 1
    public var name: String
    public var settings: Settings
    public var stats: Stats
    public init(name: String, settings: Settings = Settings(), stats: Stats = Stats()) {
        self.name = name; self.settings = settings; self.stats = stats
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: JSONKey.self)
        format = try c.defaulted("format", "")
        version = try c.defaulted("version", 1)
        guard format == "hermorse", version == 1 else { throw ProfileError.invalidFormat }
        name = try c.defaulted("name", "Profil").trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty { name = "Profil" }
        settings = try c.defaulted("settings", Settings())
        stats = try c.defaulted("stats", Stats())
    }
    public static func read(_ data: Data) throws -> Self {
        guard data.count <= 5_000_000 else { throw ProfileError.tooLarge }
        return try JSONDecoder().decode(Self.self, from: data)
    }
    public func data() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(self)
    }
    public static func availableName(_ base: String, existing: [String]) -> String {
        var name = base; var suffix = 2
        while existing.contains(name) { name = "\(base) (\(suffix))"; suffix += 1 }
        return name
    }
}
