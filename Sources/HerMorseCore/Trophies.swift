import Foundation

public struct Trophy: Identifiable {
    public let id: String
    public let name: String
    public let detail: String
    public let tier: String
    public let availableFrom: String
}
public enum Trophies {
    public static let all: [Trophy] = [
        .init(id: "premier_bip", name: "Premier bip", detail: "Manipuler son tout premier caractère", tier: "BRONZE", availableFrom: ""),
        .init(id: "km", name: "K et M maîtrisés", detail: "Valider la leçon Koch n°1 (écoute ou manipulation)", tier: "BRONZE", availableFrom: ""),
        .init(id: "serie7", name: "7 jours d'affilée", detail: "S'entraîner 7 jours de suite", tier: "BRONZE", availableFrom: ""),
        .init(id: "alphabet", name: "Alphabet complet", detail: "Reconnaître à l'écoute chacune des 26 lettres", tier: "ARGENT", availableFrom: ""),
        .init(id: "dix_heures", name: "10 heures d'entraînement", detail: "Cumuler 10 heures d'entraînement", tier: "ARGENT", availableFrom: ""),
        .init(id: "live", name: "Premier QSO en Live", detail: "Faire un QSO avec un autre utilisateur", tier: "ARGENT", availableFrom: "v0.5"),
        .init(id: "koch", name: "Koch terminé", detail: "Valider les 40 caractères Koch à l'écoute", tier: "OR", availableFrom: ""),
        .init(id: "vingt_wpm", name: "20 WPM sans faute", detail: "Une série d'écoute à 20 WPM effectifs sans aucune erreur", tier: "OR", availableFrom: ""),
        .init(id: "qso_reel", name: "Premier QSO réel traduit", detail: "Traduire 100 caractères d'un vrai QSO dans l'onglet QSO", tier: "OR", availableFrom: ""),
        .init(id: "platine", name: "Platine", detail: "Obtenir tous les trophées", tier: "PLATINE", availableFrom: ""),
        .init(id: "dyslexique", name: "Dyslexique des doigts", detail: "Plus de 50 erreurs au total", tier: "SECRET", availableFrom: ""),
        .init(id: "tortue", name: "La tortue du CW", detail: "Moins de 6 WPM de moyenne sur 50 caractères d'affilée", tier: "SECRET", availableFrom: ""),
        .init(id: "lievre", name: "Le lièvre du CW", detail: "Au moins 25 WPM de moyenne sur 50 caractères d'affilée", tier: "SECRET", availableFrom: ""),
    ]
    public static func check(_ stats: Stats, now: Date = Date()) -> [String] {
        var earned: [String] = []
        func win(_ id: String, _ condition: Bool) {
            if condition && stats.trophies[id] == nil { earned.append(id) }
        }
        win("premier_bip", stats.totalChars >= 1)
        win("km", stats.rxLevel >= 2 || stats.txLevel >= 2)
        win("alphabet", Morse.all.filter { $0.category == .letters }.allSatisfy { (stats.rxChars[$0.label]?.first ?? 0) > 0 })
        win("koch", stats.rxLevel >= 40)
        win("dyslexique", stats.errors > 50)
        win("serie7", TrainingDays.streak(stats.days, now: now) >= 7)
        win("dix_heures", stats.trainingMs >= 36_000_000)
        if stats.recentWpm.count >= 50 {
            let average = stats.recentWpm.suffix(50).reduce(0,+) / 50
            win("tortue", average < 6); win("lievre", average >= 25)
        }
        win("platine", all.filter { $0.id != "platine" }.allSatisfy { stats.trophies[$0.id] != nil || earned.contains($0.id) })
        return earned
    }
}
public enum TrainingDays {
    public static func key(_ date: Date = Date(), calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year,.month,.day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
    public static func streak(_ days: [String:Int64], now: Date = Date(), calendar: Calendar = .current) -> Int {
        var date = now
        if days[key(date, calendar: calendar), default: 0] <= 0 { date = calendar.date(byAdding: .day, value: -1, to: date)! }
        var count = 0
        while days[key(date, calendar: calendar), default: 0] > 0 {
            count += 1; date = calendar.date(byAdding: .day, value: -1, to: date)!
        }
        return count
    }
}
