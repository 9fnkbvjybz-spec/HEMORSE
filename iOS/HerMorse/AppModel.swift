import SwiftUI
import HerMorseCore

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var profiles: [ProfileDocument] = []
    @Published private(set) var activeName: String?
    @Published var settings = Settings() { didSet { if !loading { save() } } }
    @Published private(set) var stats = Stats()
    @Published var error: String?
    @Published var announcement: String?
    @Published private(set) var storageAvailable = true
    let audio = ToneEngine()
    private let repository: ProfileRepository
    private var loading = false

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        repository = ProfileRepository(url: base.appendingPathComponent("HerMorse/profiles.json"))
        do { profiles = try repository.load() }
        catch {
            storageAvailable = false
            self.error = "Lecture des profils impossible. Le fichier existant est conservé : \(error.localizedDescription)"
        }
    }
    var speeds: (Int,Int) { Levels.speeds(settings: settings, stats: stats) }
    func select(_ name: String) {
        guard storageAvailable else { return }
        audio.stop()
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        loading = true
        let profile = profiles.first { $0.name == trimmed } ?? ProfileDocument(name: trimmed)
        activeName = profile.name; settings = profile.settings; stats = profile.stats
        loading = false; save()
    }
    func logout() { save(); audio.stop(); activeName = nil }
    func delete(_ name: String) {
        guard storageAvailable else { return }
        let old = profiles
        profiles.removeAll { $0.name == name }
        do { try repository.save(profiles) }
        catch { profiles = old; self.error = error.localizedDescription }
        if activeName == name { activeName = nil; audio.stop() }
    }
    func save() {
        guard storageAvailable, let name = activeName else { return }
        let document = ProfileDocument(name: name, settings: settings, stats: stats)
        if let i = profiles.firstIndex(where: { $0.name == name }) { profiles[i] = document }
        else { profiles.append(document) }
        do { try repository.save(profiles) }
        catch { self.error = "Sauvegarde impossible : \(error.localizedDescription)" }
    }
    func importProfile(_ data: Data) throws {
        guard storageAvailable else { throw ProfileError.invalidFormat }
        var document = try ProfileDocument.read(data)
        document.name = ProfileDocument.availableName(document.name, existing: profiles.map(\.name))
        try repository.save(profiles + [document])
        profiles.append(document); select(document.name)
    }
    var exportProfile: ProfileDocument { ProfileDocument(name: activeName ?? "Profil", settings: settings, stats: stats) }
    func updateStats(_ change: (inout Stats) -> Void) {
        var copy = stats; change(&copy)
        let earned = Trophies.check(copy)
        for id in earned { copy.trophies[id] = Int64(Date().timeIntervalSince1970 * 1000) }
        if let id = earned.first, let trophy = Trophies.all.first(where: { $0.id == id }) {
            announcement = "Trophée : \(trophy.name)"
        }
        stats = copy; save()
    }
    func recordCharacter(_ label: String, wpm: Double, known: Bool) {
        updateStats {
            $0.totalChars += 1
            if !known { $0.errors += 1 }
            $0.recentWpm = Array(($0.recentWpm + [wpm]).suffix(50))
            if $0.recentWpm.count >= 10 { $0.bestWpm = max($0.bestWpm, $0.recentWpm.suffix(10).reduce(0,+)/10) }
        }
    }
    func addTraining(_ milliseconds: Int64) {
        guard activeName != nil, milliseconds > 0 else { return }
        updateStats {
            $0.trainingMs += milliseconds
            $0.days[TrainingDays.key(), default: 0] += milliseconds
            if $0.days.count > 400 { for key in $0.days.keys.sorted().dropLast(400) { $0.days.removeValue(forKey: key) } }
        }
    }
    func award(_ id: String) {
        guard stats.trophies[id] == nil else { return }
        updateStats { $0.trophies[id] = Int64(Date().timeIntervalSince1970 * 1000) }
        announcement = Trophies.all.first { $0.id == id }.map { "Trophée : \($0.name)" }
    }
    func finish(reception: Bool, results: [CharacterResult], characterWpm: Int, effectiveWpm: Int,
                substitutions: [(String,String)] = [], accuracy: Double? = nil) {
        var message: String?
        updateStats { copy in
            message = Progression.finish(stats: &copy, settings: settings, reception: reception,
                                         results: results, charWpm: characterWpm, effectiveWpm: effectiveWpm,
                                         substitutions: substitutions, accuracy: accuracy)
        }
        if let message { announcement = message }
    }
    func startAudio() -> Bool {
        do { try audio.start(settings: settings); return true }
        catch { self.error = "Audio indisponible : \(error.localizedDescription)"; return false }
    }
}
