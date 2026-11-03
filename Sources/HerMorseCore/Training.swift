import Foundation

public enum Levels {
    public static let pass = 0.9
    public static let speeds = [(12,5), (15,6), (15,8), (18,10), (20,12), (20,15), (20,18), (20,20), (25,25), (30,30)]
    public static func characters(_ level: Int) -> [String] { Array(Morse.kochOrder.prefix(min(40, max(2, level + 1)))) }
    public static func title(_ level: Int) -> String {
        switch level {
        case ..<40: return "Leçon Koch \(level)"
        case 40: return "Opérateur · mots"
        case 41: return "Trafic · indicatifs"
        default: return "QSO · phrases"
        }
    }
    public static func speeds(settings: Settings, stats: Stats) -> (Int, Int) {
        settings.guided ? speeds[min(9, max(0, stats.rxSpeed3))] : (settings.listenWpm, min(settings.listenWpm, settings.farnsworthWpm))
    }
}

public struct CharacterResult {
    public let label: String
    public let correct: Bool
    public init(_ label: String, _ correct: Bool) { self.label = label; self.correct = correct }
}

public struct Alignment {
    public let correct: [Bool]
    public let substitutions: [(String, String)]
    public let insertions: Int
    public var accuracy: Double {
        guard !correct.isEmpty else { return 0 }
        return Double(correct.filter { $0 }.count) / Double(correct.count + insertions)
    }
    /// Edit alignment, with insertions counted so additional guesses cannot produce 100%.
    public static func compare(expected: [String], typed: [String]) -> Self {
        let n = expected.count, m = typed.count
        var d = Array(repeating: Array(repeating: 0, count: m + 1), count: n + 1)
        for i in 0...n { d[i][0] = i }
        for j in 0...m { d[0][j] = j }
        if n > 0 && m > 0 {
            for i in 1...n { for j in 1...m {
                d[i][j] = min(d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1] + (expected[i-1] == typed[j-1] ? 0 : 1))
            }}
        }
        var ok = Array(repeating: false, count: n)
        var subs: [(String,String)] = []; var insertions = 0
        var i = n, j = m
        while i > 0 || j > 0 {
            if i > 0 && j > 0 && expected[i-1] == typed[j-1] && d[i][j] == d[i-1][j-1] {
                ok[i-1] = true; i -= 1; j -= 1
            } else if i > 0 && j > 0 && d[i][j] == d[i-1][j-1]+1 {
                subs.append((expected[i-1], typed[j-1])); i -= 1; j -= 1
            } else if i > 0 && (j == 0 || d[i][j] == d[i-1][j]+1) { i -= 1 }
            else { insertions += 1; j -= 1 }
        }
        return .init(correct: ok, substitutions: subs, insertions: insertions)
    }
}

public enum Progression {
    /// Returns the announcement; mutations retain Android's two independent tracks and speed ladder.
    public static func finish(stats: inout Stats, settings: Settings, reception: Bool,
                              results: [CharacterResult], charWpm: Int, effectiveWpm: Int,
                              substitutions: [(String,String)] = [], accuracy override: Double? = nil,
                              now: Date = Date()) -> String? {
        let ratio = override ?? (results.isEmpty ? 0 : Double(results.filter { $0.correct }.count) / Double(results.count))
        let level = reception ? stats.rxLevel : stats.txLevel
        var next = level, streak = reception ? stats.rxStreak : stats.txStreak
        var advanced = false
        var message: String?
        if settings.guided {
            if ratio < Levels.pass { streak = 0 }
            else if level < 40 {
                next += 1; streak = 0; advanced = true
                message = next < 40 ? "Leçon \(level) validée ! Nouveau : \(Levels.characters(next).last ?? "")" : "Méthode Koch terminée ! Phase Opérateur."
            } else {
                streak += 1
                if streak >= 3 {
                    next = min(42, level + 1); streak = 0; advanced = true
                    if next != level { message = "Nouvelle phase : \(Levels.title(next))" }
                }
            }
        }
        if reception {
            stats.rxLevel = next; stats.rxStreak = streak
            if advanced && (level < 40 ? next % 4 == 0 && stats.rxSpeed3 < 5 : stats.rxSpeed3 < 9) {
                stats.rxSpeed3 += 1
                let speed = Levels.speeds[stats.rxSpeed3]
                message = (message.map { $0 + "\n" } ?? "") + "Écoute : \(speed.0) / \(speed.1) WPM"
            }
            merge(&stats.rxChars, results)
        } else {
            stats.txLevel = next; stats.txStreak = streak
            merge(&stats.txChars, results)
        }
        for (a,b) in substitutions { stats.confusions["\(a)>\(b)", default: 0] += 1 }
        stats.series += 1
        stats.history.append(.init(time: Int64(now.timeIntervalSince1970 * 1000), track: reception ? "rx" : "tx", level: level, accuracy: ratio, wpm: effectiveWpm))
        stats.history = Array(stats.history.suffix(200))
        if reception && ratio >= 1 && charWpm >= 20 && effectiveWpm >= 20 {
            stats.trophies["vingt_wpm"] = stats.trophies["vingt_wpm"] ?? Int64(now.timeIntervalSince1970 * 1000)
        }
        return message
    }
    private static func merge(_ table: inout [String:[Int]], _ results: [CharacterResult]) {
        for r in results {
            let old = table[r.label] ?? [0,0]
            table[r.label] = [old[0] + (r.correct ? 1 : 0), old[1] + 1]
        }
    }
}

public enum TrainingContent {
    public static let words = ["LE","DE","UN","ET","EST","QUI","PAS","SUR","AVEC","MAIS","NOUS","VOUS","BIEN","TEMPS","RADIO","ANTENNE","SOLEIL","MAISON","MER","VENT","AMI","BONJOUR","MERCI","SALUT","JOUR","CQ","TNX","TU","73","88","QTH","QRZ","QSL","QSO","QRM","QSB","QRP","RST","UR","FB","OM","YL","ES","HR","WX","PWR","ANT","RIG","NAME","GM","GA","GE","BK","AGN","PSE","599"]
    public static func callsign() -> String {
        let prefix = ["F","F","F","ON","DL","EA","G","I","HB","OE","PA","LX"].randomElement()!
        return prefix + String(Int.random(in: 1...9)) + String((0..<Int.random(in: 2...3)).map { _ in "ABCDEFGHIJKLMNOPQRSTUVWXYZ".randomElement()! })
    }
    public static func phrase() -> String {
        let a = callsign(), b = callsign()
        let name = ["JEAN","PAUL","MARC","LUC","ANNE","MARIE","PIERRE","ERIC","ALAIN","SOPHIE"].randomElement()!
        let city = ["PARIS","LYON","NANTES","LILLE","BREST","NICE","DIJON","METZ","TOURS","REIMS"].randomElement()!
        return ["CQ CQ CQ DE \(a) \(a) K", "\(a) DE \(b) GM TNX FER CALL UR RST 599 599", "NAME \(name) QTH \(city) HW CPY? \(a) DE \(b) K", "R R TNX RPT UR RST 579 NAME \(name) QTH \(city)", "RIG 100W ANT DIPOLE WX SUNNY \(a) DE \(b) K", "TNX FER QSO 73 ES GL \(a) DE \(b)"].randomElement()!
    }
    public static func make(level: Int, short: Bool, settings: Settings) -> String {
        if !settings.guided {
            var pool = Morse.all.filter {
                switch $0.category {
                case .letters: return settings.useLetters
                case .digits: return settings.useDigits
                case .punctuation: return settings.usePunct
                case .prosigns: return settings.useProsigns
                case .accents: return settings.useAccents
                }
            }.map(\.label)
            if pool.isEmpty { pool = ["K","M"] }
            return groups(pool: pool, count: short ? 4 : 5)
        }
        if level < 40 { return groups(pool: Levels.characters(level), count: short ? 4 : 5, favor: Levels.characters(level).last) }
        if level == 40 { return (0..<(short ? 5 : 8)).map { _ in words.randomElement()! }.joined(separator: " ") }
        if level == 41 { return (0..<(short ? 4 : 6)).map { _ in callsign() }.joined(separator: " ") }
        return (0..<(short ? 1 : 2)).map { _ in phrase() }.joined(separator: " ")
    }
    private static func groups(pool: [String], count: Int, favor: String? = nil) -> String {
        (0..<count).map { _ in (0..<5).map { _ in
            if let favor, Double.random(in: 0..<1) < 0.3 { return favor }
            return pool.randomElement()!
        }.joined() }.joined(separator: " ")
    }
}
