import SwiftUI
import HerMorseCore

enum LearnMode: String, CaseIterable, Identifiable {
    case cards = "Fiches", free = "Saisie libre", listen = "Écouter → écrire", send = "Lire → manipuler"
    var id: String { rawValue }
    var icon: String {
        switch self { case .cards: return "rectangle.grid.2x2"; case .free: return "hand.tap"; case .listen: return "ear"; case .send: return "text.cursor" }
    }
    var usage: String {
        switch self { case .cards: return "fiches"; case .free: return "libre"; case .listen: return "ecoute"; case .send: return "lecture" }
    }
}
struct LearnView: View {
    @ObservedObject var model: AppModel
    @Binding var mode: LearnMode
    @Environment(\.scenePhase) private var phase
    @State private var visible = false
    var body: some View {
        VStack(spacing: 0) {
            Picker("Exercice",selection: $mode) { ForEach(LearnMode.allCases) { Label($0.rawValue,systemImage: $0.icon).tag($0) } }
                .pickerStyle(.menu).padding(.horizontal)
            switch mode {
            case .cards: CardsView(model: model)
            case .free: FreeKeyView(model: model)
            case .listen: ListenView(model: model)
            case .send: SendView(model: model)
            }
        }
        .onAppear { visible = true; model.updateStats { $0.usage[mode.usage,default: 0] += 1 } }
        .onDisappear { visible = false }
        .onChange(of: mode) { value in
            model.audio.cancelPlayback()
            model.updateStats { $0.usage[value.usage,default: 0] += 1 }
        }
        .onReceive(Timer.publish(every: 10,on: .main,in: .common).autoconnect()) { _ in
            if visible && phase == .active { model.addTraining(10_000) }
        }
    }
}
struct MorseDiagram: View {
    let code: String
    var body: some View {
        Text(code.map { $0 == "." ? "•" : "━" }.joined(separator: " "))
            .font(.system(.title3,design: .monospaced).bold()).foregroundStyle(.orange)
            .accessibilityLabel(Morse.spoken(code))
    }
}
struct CardsView: View {
    @ObservedObject var model: AppModel
    @State private var selected: MorseCharacter?
    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 88))],spacing: 12) {
                ForEach(Category.allCases,id: \.self) { category in
                    Section {
                        ForEach(Morse.all.filter { $0.category == category }) { item in
                            Button { selected = item } label: {
                                VStack(spacing: 8) {
                                    Text(item.label).font(.title2.bold())
                                    MorseDiagram(code: item.code).minimumScaleFactor(0.3).lineLimit(1)
                                    if let index = Morse.kochOrder.firstIndex(of: item.label) { Text("Koch \(index+1)").font(.caption2).foregroundStyle(.secondary) }
                                }.frame(maxWidth: .infinity,minHeight: 86).padding(8)
                                    .background(Color(uiColor: .secondarySystemGroupedBackground),in: RoundedRectangle(cornerRadius: 16))
                            }.buttonStyle(.plain)
                        }
                    } header: {
                        Text(category.title).font(.headline).frame(maxWidth: .infinity,alignment: .leading).padding(.top,12)
                    }
                }
            }.padding()
        }.background(Color(uiColor: .systemGroupedBackground))
        .sheet(item: $selected) { item in
            NavigationStack {
                VStack(spacing: 22) {
                    Text(item.label).font(.system(size: 64,weight: .bold))
                    MorseDiagram(code: item.code)
                    Text(Morse.spoken(item.code)).font(.title3)
                    Text(item.hint).foregroundStyle(.secondary)
                    Text("\(model.speeds.0) WPM")
                    Button("Écouter",systemImage: "play.fill") { play(item) }.buttonStyle(.borderedProminent)
                }.padding().navigationTitle("Caractère")
                    .toolbar { Button("Fermer") { selected = nil } }
                    .onAppear { play(item) }.onDisappear { model.audio.cancelPlayback() }
            }.presentationDetents([.medium,.large])
        }
    }
    private func play(_ item: MorseCharacter) {
        if model.startAudio() { model.audio.play(tokens: [item.label],characterWpm: model.speeds.0,effectiveWpm: model.speeds.0) }
    }
}
struct FreeKeyView: View {
    @ObservedObject var model: AppModel
    @StateObject private var session: KeySession
    init(model: AppModel) { self.model = model; _session = StateObject(wrappedValue: KeySession(model: model)) }
    var body: some View {
        VStack(alignment: .leading,spacing: 12) {
            ScrollView {
                Text(session.text.isEmpty ? "Manipulez pour commencer…" : session.text)
                    .font(.system(.title2,design: .monospaced)).frame(maxWidth: .infinity,alignment: .leading).textSelection(.enabled)
            }.frame(maxHeight: .infinity)
            HStack {
                MorseDiagram(code: session.pattern); Spacer()
                Text("≈ \(session.wpm,specifier: "%.0f") WPM").foregroundStyle(.secondary)
            }
            HStack {
                Text(model.settings.keyMode == "straight" ? "Pioche" : "Iambique · \(model.settings.keyerWpm) WPM").font(.caption)
                Spacer(); Button("Effacer") { session.text = ""; session.reset() }
                Button("Activer") { session.start() }
            }
            KeyPad(model: model,session: session)
        }.padding().onAppear { session.start() }.onDisappear { session.stop() }
    }
}

struct LevelHeader: View {
    let guided: Bool
    let level: Int
    let speed: String
    var body: some View {
        VStack(alignment: .leading,spacing: 6) {
            HStack { Text(guided ? Levels.title(level) : "Mode personnalisé").bold(); Spacer(); Text(speed).font(.caption) }
            if guided {
                if level < 40 { Text(Levels.characters(level).joined(separator: " ")).font(.system(.caption,design: .monospaced)) }
                Text(level < 40 ? "Objectif : 90 %" : "Objectif : 3 séries à 90 % de suite").font(.caption).foregroundStyle(.secondary)
            } else { Text("Sans modification du niveau guidé").font(.caption).foregroundStyle(.secondary) }
        }.padding().background(Color(uiColor: .secondarySystemGroupedBackground),in: RoundedRectangle(cornerRadius: 16))
    }
}
