import SwiftUI
import HerMorseCore

struct ListenView: View {
    @ObservedObject var model: AppModel
    @State private var tokens: [String] = []
    @State private var typed = "", stage = "ready"
    @State private var index = -1
    @State private var result: Alignment?
    @State private var characterWpm = 12, effectiveWpm = 5, level = 1
    @State private var guided = true
    var body: some View {
        ScrollView {
            VStack(alignment: .leading,spacing: 18) {
                LevelHeader(guided: stage == "ready" ? model.settings.guided : guided,
                            level: stage == "ready" ? model.stats.rxLevel : level,
                            speed: stage == "ready" ? "\(model.speeds.0) / \(model.speeds.1) WPM" : "\(characterWpm) / \(effectiveWpm) WPM")
                if stage == "ready" {
                    Text("Écoutez la série et saisissez ce que vous entendez. La correction apparaît à la fin.")
                    if model.settings.guided {
                        HStack {
                            Button("Plus lent") { model.updateStats { $0.rxSpeed3 = max(0,$0.rxSpeed3-1) } }
                            Spacer()
                            Button("Plus vite") { model.updateStats { $0.rxSpeed3 = min(9,$0.rxSpeed3+1) } }
                        }.buttonStyle(.bordered)
                    }
                }
                if stage == "playing" || stage == "ended" {
                    ProgressView(value: stage == "ended" ? 1 : Double(max(0,index))/Double(max(1,tokens.count)))
                    Text(stage == "playing" ? "Écoute en cours…" : "Écoute terminée. Finissez votre saisie.").foregroundStyle(.secondary)
                    TextField("Ce que vous entendez",text: $typed,axis: .vertical)
                        .lineLimit(4...8).textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .font(.system(.title3,design: .monospaced))
                    Button(stage == "playing" ? "Arrêter et corriger" : "Corriger") { correct() }.buttonStyle(.borderedProminent)
                }
                if let result, stage == "done" {
                    Text("Résultat : \(Int(result.accuracy*100)) %").font(.title2.bold()).foregroundStyle(result.accuracy >= 0.9 ? .green : .red)
                    Text("Attendu").font(.caption)
                    Text(tokens.joined()).font(.system(.title2,design: .monospaced)).textSelection(.enabled)
                    Text("Votre saisie").font(.caption)
                    Text(typed.isEmpty ? "(vide)" : typed.uppercased()).font(.system(.title3,design: .monospaced))
                    Text("\(result.correct.filter { $0 }.count) caractères justes sur \(result.correct.count), \(result.insertions) ajout(s).").foregroundStyle(.secondary)
                    if !result.substitutions.isEmpty {
                        Text("Confusions : " + result.substitutions.map { "\($0.0) → \($0.1)" }.joined(separator: ", ")).font(.caption)
                    }
                }
                if stage == "ready" || stage == "done" {
                    Button(stage == "done" ? "Série suivante" : "Démarrer la série") { start() }.buttonStyle(.borderedProminent)
                }
                if stage != "ready" { Button("Annuler la série",role: .cancel) { cancel() } }
            }.padding()
        }.background(Color(uiColor: .systemGroupedBackground))
            .onDisappear { if stage == "playing" || stage == "ended" { cancel() } else { model.audio.cancelPlayback() } }
            .onReceive(NotificationCenter.default.publisher(for: .hermorsePause)) { _ in if stage == "playing" || stage == "ended" { cancel() } }
    }
    private func start() {
        guard model.startAudio() else { return }
        level = model.stats.rxLevel; guided = model.settings.guided
        (characterWpm,effectiveWpm) = model.speeds
        tokens = Morse.tokens(TrainingContent.make(level: level,short: false,settings: model.settings))
        typed = ""; result = nil; index = -1; stage = "playing"
        model.audio.play(tokens: tokens,characterWpm: characterWpm,effectiveWpm: effectiveWpm,
                         progress: { index = $0 },completion: { if stage == "playing" { stage = "ended" } })
    }
    private func correct() {
        guard stage == "playing" || stage == "ended" else { return }
        model.audio.cancelPlayback()
        let expected = tokens.filter { $0 != " " }
        let answer = Morse.tokens(typed,preserveUnknown: true).filter { $0 != " " }
        // Limit edit-distance memory for accidentally pasted large texts.
        guard answer.count <= 1000 else { model.error = "La réponse est limitée à 1 000 caractères."; return }
        let alignment = Alignment.compare(expected: expected,typed: answer)
        result = alignment; stage = "done"
        let results = zip(expected,alignment.correct).map { CharacterResult($0.0,$0.1) }
        model.finish(reception: true,results: results,characterWpm: characterWpm,effectiveWpm: effectiveWpm,
                     substitutions: alignment.substitutions,accuracy: alignment.accuracy)
    }
    private func cancel() { model.audio.cancelPlayback(); stage = "ready"; result = nil }
}

struct SendView: View {
    @ObservedObject var model: AppModel
    @StateObject private var session: KeySession
    @State private var tokens: [String] = [], statuses: [Int] = []
    @State private var index = 0, level = 1
    @State private var stage = "ready", correcting = false, accuracy = 0.0
    init(model: AppModel) { self.model = model; _session = StateObject(wrappedValue: KeySession(model: model)) }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading,spacing: 14) {
                LevelHeader(guided: model.settings.guided,level: stage == "ready" ? model.stats.txLevel : level,
                            speed: model.settings.keyMode == "straight" ? "Pioche" : "\(model.settings.keyerWpm) WPM")
                if stage == "ready" {
                    Text("Manipulez chaque caractère affiché. Après une erreur, écoutez la correction puis recommencez le caractère.")
                    Button("Démarrer la série") { start() }.buttonStyle(.borderedProminent)
                } else if stage == "done" {
                    Text("\(Int(accuracy*100)) % du premier coup").font(.title2.bold())
                    Button("Série suivante") { start() }.buttonStyle(.borderedProminent)
                } else {
                    renderedText.font(.system(.title2,design: .monospaced)).lineSpacing(6)
                    ProgressView(value: Double(index),total: Double(max(1,tokens.count)))
                    if index < tokens.count, model.settings.showHint, let item = Morse.character(tokens[index]) {
                        HStack { Text(item.label).font(.title.bold()); MorseDiagram(code: item.code) }
                    }
                    HStack { Text("Vous :"); MorseDiagram(code: session.pattern) }
                    if correcting { Label("Écoutez la correction…",systemImage: "speaker.wave.2").foregroundStyle(.orange) }
                    KeyPad(model: model,session: session).allowsHitTesting(!correcting)
                    Button("Annuler la série") { cancel() }
                }
            }.padding()
        }.background(Color(uiColor: .systemGroupedBackground))
            .onAppear { session.onCharacter = receive }
            .onDisappear { session.stop(); session.onCharacter = nil }
            .onReceive(NotificationCenter.default.publisher(for: .hermorsePause)) { _ in if stage == "running" { cancel() } }
    }
    private var renderedText: Text {
        tokens.indices.reduce(Text("")) { text,i in
            let color: Color = statuses.indices.contains(i) && statuses[i] > 0 ? (statuses[i] == 2 ? .red : .green) : .secondary
            return text + Text(tokens[i]).foregroundColor(i == index ? .primary : color).bold()
                .underline(i == index)
        }
    }
    private func start() {
        level = model.stats.txLevel
        tokens = Morse.tokens(TrainingContent.make(level: level,short: true,settings: model.settings)).filter { $0 != " " }
        statuses = Array(repeating: 0,count: tokens.count); index = 0; correcting = false
        session.enabled = true; session.onCharacter = receive; session.start(); stage = "running"
    }
    private func receive(_ label: String, _ wpm: Double, _ known: Bool) {
        guard stage == "running", !correcting, index < tokens.count else { return }
        let expected = tokens[index], correct = Morse.equivalent(label,expected)
        model.recordCharacter(label,wpm: wpm,known: correct)
        if correct {
            statuses[index] = statuses[index] == 2 ? 3 : 1; index += 1
            if index >= tokens.count {
                stage = "done"; session.enabled = false; session.stop()
                let results = tokens.indices.map { CharacterResult(tokens[$0],statuses[$0] == 1) }
                accuracy = Double(results.filter { $0.correct }.count)/Double(max(1,results.count))
                model.finish(reception: false,results: results,characterWpm: Int(wpm),effectiveWpm: Int(wpm))
            }
        } else {
            statuses[index] = 2; correcting = true; session.enabled = false; session.reset()
            model.updateStats { $0.confusions["\(expected)>\(label)",default: 0] += 1 }
            model.audio.play(tokens: [expected],characterWpm: model.speeds.0,effectiveWpm: model.speeds.0) {
                _ in
            } completion: {
                guard stage == "running" else { return }
                session.reset(); correcting = false; session.enabled = true
            }
        }
    }
    private func cancel() { session.stop(); session.enabled = false; correcting = false; stage = "ready" }
}
