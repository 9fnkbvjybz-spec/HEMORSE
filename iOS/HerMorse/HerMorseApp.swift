import SwiftUI
import UniformTypeIdentifiers
import HerMorseCore

extension UTType {
    static let hermorseProfile = UTType(exportedAs: "fr.hermorse.profile", conformingTo: .json)
}
struct ProfileFile: FileDocument {
    static var readableContentTypes: [UTType] { [.hermorseProfile,.json] }
    var profile: ProfileDocument
    init(_ profile: ProfileDocument) { self.profile = profile }
    init(configuration: ReadConfiguration) throws {
        profile = try ProfileDocument.read(configuration.file.regularFileContents ?? Data())
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: try profile.data())
    }
}

@main
struct HerMorseApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .tint(Color(red: 0.16, green: 0.36, blue: 0.70))
                .preferredColorScheme(model.settings.theme == "dark" ? .dark : model.settings.theme == "light" ? .light : nil)
        }
    }
}

struct RootView: View {
    @ObservedObject var model: AppModel
    @Environment(\.scenePhase) private var phase
    @State private var tab = "home"
    @State private var mode: LearnMode = .cards
    @State private var sheet: String?
    @State private var importing = false, exporting = false
    var body: some View {
        Group {
            if model.activeName == nil {
                ProfilePicker(model: model, importing: $importing)
            } else {
                NavigationStack {
                    TabView(selection: $tab) {
                        HomeView(model: model, tab: $tab, mode: $mode)
                            .tabItem { Label("Accueil",systemImage: "house") }.tag("home")
                        LearnView(model: model, mode: $mode)
                            .tabItem { Label("Apprendre",systemImage: "graduationcap") }.tag("learn")
                        QSOView(model: model)
                            .tabItem { Label("QSO",systemImage: "waveform") }.tag("qso")
                    }
                    .navigationTitle("HerMorse")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Menu {
                                Text(model.activeName ?? "")
                                Button("Statistiques",systemImage: "chart.bar") { sheet = "stats" }
                                Button("Trophées",systemImage: "trophy") { sheet = "trophies" }
                                Button("Réglages",systemImage: "gear") { sheet = "settings" }
                                Button("Exporter le profil",systemImage: "square.and.arrow.up") { model.save(); exporting = true }
                                Button("Importer un profil",systemImage: "square.and.arrow.down") { importing = true }
                                Button("À propos",systemImage: "info.circle") { sheet = "about" }
                                Divider()
                                Button("Changer de profil",systemImage: "person") { pause(); model.logout() }
                            } label: { Image(systemName: "line.3.horizontal") }
                            .accessibilityLabel("Menu")
                        }
                    }
                }
            }
        }
        .sheet(isPresented: Binding(get: { sheet != nil },set: { if !$0 { sheet = nil } })) {
            NavigationStack {
                Group {
                    switch sheet {
                    case "settings": SettingsView(model: model)
                    case "stats": StatisticsView(model: model)
                    case "trophies": TrophiesView(model: model)
                    default: AboutView()
                    }
                }.toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fermer") { sheet = nil } } }
            }.onAppear { pause() }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.hermorseProfile,.json,.data]) { result in
            do { try importURL(result.get()) } catch { model.error = error.localizedDescription }
        }
        .fileExporter(isPresented: $exporting, document: ProfileFile(model.exportProfile), contentType: .hermorseProfile,
                      defaultFilename: (model.activeName ?? "Profil").replacingOccurrences(of: "/", with: "-") + ".hermorse") { result in
            if case .failure(let error) = result { model.error = error.localizedDescription }
        }
        .onOpenURL { url in do { try importURL(url) } catch { model.error = error.localizedDescription } }
        .alert("HerMorse", isPresented: Binding(get: { model.error != nil },set: { if !$0 { model.error = nil } })) {
            Button("OK") { model.error = nil }
        } message: { Text(model.error ?? "") }
        .alert("Bravo !", isPresented: Binding(get: { model.announcement != nil && model.error == nil },set: { if !$0 { model.announcement = nil } })) {
            Button("Continuer") { model.announcement = nil }
        } message: { Text(model.announcement ?? "") }
        .onChange(of: phase) { value in if value != .active { pause(); model.save() } }
        .onAppear { model.audio.onInterruption = { NotificationCenter.default.post(name: .hermorsePause, object: nil) } }
    }
    private func pause() { model.audio.stop(); NotificationCenter.default.post(name: .hermorsePause, object: nil) }
    private func importURL(_ url: URL) throws {
        pause()
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        if let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > 5_000_000 { throw ProfileError.tooLarge }
        try model.importProfile(Data(contentsOf: url))
    }
}

struct ProfilePicker: View {
    @ObservedObject var model: AppModel
    @Binding var importing: Bool
    @State private var name = "", deleting: String?
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading,spacing: 12) {
                        Text("HER MORSE").font(.largeTitle.bold())
                        Text("Apprendre le CW, à votre rythme.").foregroundStyle(.secondary)
                    }.padding(.vertical)
                }
                Section("Qui s’entraîne ?") {
                    ForEach(model.profiles, id: \.name) { profile in
                        Button { model.select(profile.name) } label: { Label(profile.name,systemImage: "person.crop.circle") }
                            .swipeActions { Button("Supprimer",role: .destructive) { deleting = profile.name } }
                    }
                    TextField("Nom du nouveau profil", text: $name).textContentType(.nickname)
                    Button("Créer le profil") { model.select(name); name = "" }
                        .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !model.storageAvailable)
                }
                Button("Importer un profil .hermorse") { importing = true }.disabled(!model.storageAvailable)
            }
            .navigationTitle("Bienvenue")
            .confirmationDialog("Supprimer définitivement ce profil ?",isPresented: Binding(get: { deleting != nil },set: { if !$0 { deleting = nil } }),titleVisibility: .visible) {
                Button("Supprimer",role: .destructive) { if let deleting { model.delete(deleting) }; deleting = nil }
            }
        }
    }
}

struct HomeView: View {
    @ObservedObject var model: AppModel
    @Binding var tab: String
    @Binding var mode: LearnMode
    var body: some View {
        List {
            Section {
                VStack(alignment: .leading,spacing: 8) {
                    Text("Bonjour \(model.activeName ?? "")").font(.title.bold())
                    Text("Un peu de pratique, chaque jour.").foregroundStyle(.secondary)
                }.padding(.vertical,12)
            }
            Section("Votre progression") {
                Label("Écoute · \(Levels.title(model.stats.rxLevel))",systemImage: "ear")
                Label("Manipulation · \(Levels.title(model.stats.txLevel))",systemImage: "hand.tap")
                Text("\(model.stats.series) séries · \(TrainingDays.streak(model.stats.days)) jours d’affilée")
            }
            Section("S’entraîner") {
                ForEach(LearnMode.allCases) { item in
                    Button { mode = item; tab = "learn" } label: { Label(item.rawValue,systemImage: item.icon) }
                }
                Button { tab = "qso" } label: { Label("Traduire un poste au microphone",systemImage: "waveform") }
            }
            Section("Live · prévu dans Android v0.5") {
                Text("Les échanges entre utilisateurs ne sont pas encore disponibles dans la version source v0.4.").foregroundStyle(.secondary)
            }
        }
    }
}
