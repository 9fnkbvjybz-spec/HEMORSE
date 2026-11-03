import Foundation

public enum Category: String, CaseIterable, Codable, Sendable {
    case letters, digits, punctuation, prosigns, accents
    public var title: String {
        switch self {
        case .letters: return "Lettres"
        case .digits: return "Chiffres"
        case .punctuation: return "Ponctuation"
        case .prosigns: return "Prosignes"
        case .accents: return "Lettres accentuées"
        }
    }
}
public struct MorseCharacter: Identifiable, Equatable, Sendable {
    public let label: String
    public let code: String
    public let category: Category
    public let hint: String
    public var id: String { label }
}
public enum Morse {
    public static let all: [MorseCharacter] = [
        .init(label: "A", code: ".-", category: .letters, hint: ""),
        .init(label: "B", code: "-...", category: .letters, hint: ""),
        .init(label: "C", code: "-.-.", category: .letters, hint: ""),
        .init(label: "D", code: "-..", category: .letters, hint: ""),
        .init(label: "E", code: ".", category: .letters, hint: ""),
        .init(label: "F", code: "..-.", category: .letters, hint: ""),
        .init(label: "G", code: "--.", category: .letters, hint: ""),
        .init(label: "H", code: "....", category: .letters, hint: ""),
        .init(label: "I", code: "..", category: .letters, hint: ""),
        .init(label: "J", code: ".---", category: .letters, hint: ""),
        .init(label: "K", code: "-.-", category: .letters, hint: ""),
        .init(label: "L", code: ".-..", category: .letters, hint: ""),
        .init(label: "M", code: "--", category: .letters, hint: ""),
        .init(label: "N", code: "-.", category: .letters, hint: ""),
        .init(label: "O", code: "---", category: .letters, hint: ""),
        .init(label: "P", code: ".--.", category: .letters, hint: ""),
        .init(label: "Q", code: "--.-", category: .letters, hint: ""),
        .init(label: "R", code: ".-.", category: .letters, hint: ""),
        .init(label: "S", code: "...", category: .letters, hint: ""),
        .init(label: "T", code: "-", category: .letters, hint: ""),
        .init(label: "U", code: "..-", category: .letters, hint: ""),
        .init(label: "V", code: "...-", category: .letters, hint: ""),
        .init(label: "W", code: ".--", category: .letters, hint: ""),
        .init(label: "X", code: "-..-", category: .letters, hint: ""),
        .init(label: "Y", code: "-.--", category: .letters, hint: ""),
        .init(label: "Z", code: "--..", category: .letters, hint: ""),
        .init(label: "0", code: "-----", category: .digits, hint: ""),
        .init(label: "1", code: ".----", category: .digits, hint: ""),
        .init(label: "2", code: "..---", category: .digits, hint: ""),
        .init(label: "3", code: "...--", category: .digits, hint: ""),
        .init(label: "4", code: "....-", category: .digits, hint: ""),
        .init(label: "5", code: ".....", category: .digits, hint: ""),
        .init(label: "6", code: "-....", category: .digits, hint: ""),
        .init(label: "7", code: "--...", category: .digits, hint: ""),
        .init(label: "8", code: "---..", category: .digits, hint: ""),
        .init(label: "9", code: "----.", category: .digits, hint: ""),
        .init(label: "<AR>", code: ".-.-.", category: .prosigns, hint: "Fin de message"),
        .init(label: "<SK>", code: "...-.-", category: .prosigns, hint: "Fin de contact"),
        .init(label: "<KN>", code: "-.--.", category: .prosigns, hint: "À toi seulement"),
        .init(label: "<BT>", code: "-...-", category: .prosigns, hint: "Séparation (=)"),
        .init(label: "<AS>", code: ".-...", category: .prosigns, hint: "Attendez"),
        .init(label: ".", code: ".-.-.-", category: .punctuation, hint: ""),
        .init(label: ",", code: "--..--", category: .punctuation, hint: ""),
        .init(label: "?", code: "..--..", category: .punctuation, hint: ""),
        .init(label: "/", code: "-..-.", category: .punctuation, hint: ""),
        .init(label: "=", code: "-...-", category: .punctuation, hint: ""),
        .init(label: "'", code: ".----.", category: .punctuation, hint: ""),
        .init(label: "!", code: "-.-.--", category: .punctuation, hint: ""),
        .init(label: "(", code: "-.--.", category: .punctuation, hint: ""),
        .init(label: ")", code: "-.--.-", category: .punctuation, hint: ""),
        .init(label: ":", code: "---...", category: .punctuation, hint: ""),
        .init(label: ";", code: "-.-.-.", category: .punctuation, hint: ""),
        .init(label: "+", code: ".-.-.", category: .punctuation, hint: ""),
        .init(label: "-", code: "-....-", category: .punctuation, hint: ""),
        .init(label: "\"", code: ".-..-.", category: .punctuation, hint: ""),
        .init(label: "@", code: ".--.-.", category: .punctuation, hint: ""),
        .init(label: "É", code: "..-..", category: .accents, hint: ""),
        .init(label: "È", code: ".-..-", category: .accents, hint: ""),
        .init(label: "À", code: ".--.-", category: .accents, hint: ""),
        .init(label: "Ç", code: "-.-..", category: .accents, hint: ""),
    ]
    public static let kochOrder = ["K", "M", "R", "S", "U", "A", "P", "T", "L", "O", "W", "I", ".", "N", "J", "E", "F", "0", "Y", ",", "V", "G", "5", "/", "Q", "9", "Z", "H", "3", "8", "B", "?", "4", "2", "7", "C", "1", "D", "6", "X"]
    public static func character(_ label: String) -> MorseCharacter? { all.first { $0.label == label } }
    public static func decode(_ code: String) -> String? { all.first { $0.code == code }?.label }
    /// Punctuation and prosigns can share one pattern (e.g. '=' and <BT>).
    public static func equivalent(_ lhs: String, _ rhs: String) -> Bool {
        guard let left = character(lhs), let right = character(rhs) else { return lhs == rhs }
        return left.code == right.code
    }
    public static func spoken(_ code: String) -> String { code.map { $0 == "." ? "ti" : "taah" }.joined(separator: "-") }
    /// Tokenizes <AR> etc. as one symbol; unsupported characters are excluded.
    public static func tokens(_ text: String, preserveUnknown: Bool = false) -> [String] {
        let chars = Array(text.uppercased())
        var out: [String] = []; var i = 0
        while i < chars.count {
            if chars[i].isWhitespace {
                if out.last != " " && !out.isEmpty { out.append(" ") }
                i += 1; continue
            }
            if chars[i] == "<", let end = chars[i...].firstIndex(of: ">") {
                let token = String(chars[i...end])
                if character(token) != nil { out.append(token); i = end + 1; continue }
            }
            let label = String(chars[i])
            if character(label) != nil || preserveUnknown { out.append(label) }
            i += 1
        }
        if out.last == " " { out.removeLast() }
        return out
    }
}
