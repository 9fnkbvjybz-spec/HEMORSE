import Foundation

public struct ToneSegment: Equatable {
    public let on: Bool
    public let milliseconds: Double
    public let tokenIndex: Int
}
public enum MorseTiming {
    public static func sequence(tokens: [String], characterWpm: Int, effectiveWpm: Int) -> [ToneSegment] {
        let cw = Double(max(1, characterWpm)), ew = Double(max(1, min(effectiveWpm, characterWpm)))
        let unit = 1200 / cw
        let spacing = ew >= cw ? unit : (60 * cw - 37.2 * ew) * 1000 / (cw * ew * 19)
        var result: [ToneSegment] = []
        for (index, token) in tokens.enumerated() {
            guard token != " ", let code = Morse.character(token)?.code else { continue }
            for (position, mark) in code.enumerated() {
                result.append(.init(on: true, milliseconds: mark == "." ? unit : 3 * unit, tokenIndex: index))
                let last = position == code.count - 1
                let gap = last ? ((index + 1 < tokens.count && tokens[index + 1] == " ") ? 7 * spacing : 3 * spacing) : unit
                result.append(.init(on: false, milliseconds: gap, tokenIndex: index))
            }
        }
        return result
    }
}

public enum DecoderEvent: Equatable {
    case character(String), space
}
public final class CWDecoder {
    private var dit = 100.0, dah = 300.0
    private var pattern = ""
    private var spaceSent = true
    public var unit: Double { (dit + dah / 3) / 2 }
    public var wpm: Double { 1200 / unit }
    public init() {}
    public func mark(_ milliseconds: Double) -> [DecoderEvent] {
        guard milliseconds >= 20 else { return [] }
        let dot = milliseconds < (dit + dah) / 2
        if dot { dit = 0.8 * dit + 0.2 * milliseconds } else { dah = 0.8 * dah + 0.2 * milliseconds }
        dit = min(600, max(20, dit)); dah = max(dah, 2 * dit)
        if dah > 5 * dit { dit = dah / 5 }
        pattern += dot ? "." : "-"; spaceSent = false
        return pattern.count > 8 ? flush() : []
    }
    public func silence(_ milliseconds: Double) -> [DecoderEvent] {
        if !pattern.isEmpty && milliseconds > 2 * unit { return flush() }
        if pattern.isEmpty && !spaceSent && milliseconds > 5 * unit {
            spaceSent = true; return [.space]
        }
        return []
    }
    private func flush() -> [DecoderEvent] {
        let label = Morse.decode(pattern) ?? "✱"; pattern = ""
        return [.character(label)]
    }
}

public enum KeyEvent: Equatable {
    case tone(Bool), pattern(String), character(String, Double, Bool), space, keyTime(Double), ratio(Double)
}

/// Deterministic state machine. All times are monotonic milliseconds, never wall-clock dates.
public final class Keyer {
    public var settings: Settings
    public var emit: (KeyEvent) -> Void = { _ in }
    private var dit = 120.0, dah = 360.0
    private var pattern = "", downAt: Double?
    private var lastUp: Double?, spaceSent = true
    private var dotPressed = false, dashPressed = false, dotMemory = false, dashMemory = false
    private enum Phase { case idle, mark, gap }
    private var phase = Phase.idle
    private var until = 0.0, markLength = 0.0
    private var last: Character?
    public init(settings: Settings = Settings()) { self.settings = settings }
    public var unit: Double {
        if settings.keyMode != "straight" { return 1200 / Double(max(1, settings.keyerWpm)) }
        return settings.adaptiveDecode ? (dit + dah / 3) / 2 : 1200 / Double(max(1, settings.fixedWpm))
    }
    public var wpm: Double { 1200 / unit }
    public func contact(dot: Bool, pressed: Bool, now: Double) {
        if settings.keyMode == "straight" {
            // Both physical contacts map to one straight key; release only after both are up.
            if dot { dotPressed = pressed } else { dashPressed = pressed }
            let held = dotPressed || dashPressed
            if held && downAt == nil { downAt = now; emit(.tone(true)) }
            else if !held, let start = downAt {
                downAt = nil; emit(.tone(false)); lastUp = now
                let duration = max(0, now - start)
                emit(.keyTime(duration))
                guard duration >= 12 else { return }
                let dot = duration < (settings.adaptiveDecode ? (dit + dah) / 2 : 2 * unit)
                if settings.adaptiveDecode {
                    if dot { dit = 0.75 * dit + 0.25 * duration } else { dah = 0.75 * dah + 0.25 * duration }
                    dit = min(500, max(25, dit)); dah = max(dah, 2 * dit)
                    if dah > 5 * dit { dit = dah / 5 }
                }
                append(dot ? "." : "-")
            }
            return
        }
        if dot { dotPressed = pressed } else { dashPressed = pressed }
        rememberOpposite()
        if pressed && phase == .idle { last = nil; startNext(now: now) }
    }
    private func rememberOpposite() {
        guard settings.keyMode == "iambicB", phase != .idle else { return }
        if last == "." && dashPressed { dashMemory = true }
        if last == "-" && dotPressed { dotMemory = true }
    }
    private func startNext(now: Double) {
        let dot = dotPressed || dotMemory, dash = dashPressed || dashMemory
        guard dot || dash else { phase = .idle; return }
        let symbol: Character = dot && dash ? (last == "." ? "-" : ".") : (dot ? "." : "-")
        if symbol == "." { dotMemory = false } else { dashMemory = false }
        last = symbol; markLength = symbol == "." ? unit : 3 * unit
        phase = .mark; until = now + markLength
        rememberOpposite(); emit(.tone(true))
    }
    public func tick(now: Double) {
        if phase != .idle {
            rememberOpposite()
            if now >= until {
                if phase == .mark {
                    emit(.tone(false)); emit(.keyTime(markLength))
                    if let last { append(last) }
                    lastUp = now; phase = .gap; until = now + unit
                } else { startNext(now: now) }
            }
            if phase != .idle { return }
        }
        guard downAt == nil, let lastUp else { return }
        let gap = now - lastUp
        if !pattern.isEmpty && gap > (settings.keyMode == "straight" ? 2.5 : 2) * unit { flush() }
        else if pattern.isEmpty && !spaceSent && gap > 6 * unit { spaceSent = true; emit(.space) }
    }
    private func append(_ symbol: Character) {
        pattern.append(symbol); spaceSent = false; emit(.pattern(pattern))
        if pattern.count >= 9 { flush() }
    }
    private func flush() {
        let label = Morse.decode(pattern)
        if settings.keyMode == "straight" && settings.adaptiveDecode && pattern.contains(".") && pattern.contains("-") { emit(.ratio(dah / dit)) }
        pattern = ""; emit(.pattern("")); emit(.character(label ?? "✱", wpm, label != nil))
    }
    public func reset() {
        downAt = nil; phase = .idle; last = nil; lastUp = nil
        dotPressed = false; dashPressed = false; dotMemory = false; dashMemory = false
        pattern = ""; spaceSent = true
        emit(.tone(false)); emit(.pattern(""))
    }
}
