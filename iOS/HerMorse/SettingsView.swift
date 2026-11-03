import SwiftUI
import AVKit
import HerMorseCore

private struct AudioRoutePicker: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView { AVRoutePickerView() }
    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}

struct SettingsView: View {
    @ObservedObject var model: AppModel
    var body: some View {
        Form {
            Section("Manipulateur") {
                Picker("Source",selection: $model.settings.keySource) {
                    Text("Écran tactile").tag("virtual"); Text("Clavier / boîtier USB").tag("external")
                }
                Picker("Type",selection: $model.settings.keyMode) {
                    Text("Pioche").tag("straight"); Text("Iambique A").tag("iambicA"); Text("Iambique B").tag("iambicB")
                }
                Toggle("Inverser les palettes",isOn: $model.settings.swapPaddles)
                Stepper("Hauteur : \(model.settings.keyHeight)",value: $model.settings.keyHeight,in: 90...340,step: 10)
                if model.settings.keyMode == "straight" {
                    Toggle("Décodage adaptatif",isOn: $model.settings.adaptiveDecode)
                    if !model.settings.adaptiveDecode { Stepper("Vitesse : \(model.settings.fixedWpm) WPM",value: $model.settings.fixedWpm,in: 5...60) }
                } else { Stepper("Vitesse : \(model.settings.keyerWpm) WPM",value: $model.settings.keyerWpm,in: 5...60) }
                if model.settings.keySource == "external" {
                    Text("Le firmware fourni envoie Ctrl gauche et Ctrl droit. Les touches [ et ] sont aussi reconnues. La réception de ces touches et l’alimentation USB restent à tester sur votre appareil iOS.").font(.caption).foregroundStyle(.secondary)
                }
            }
            Section {
                Toggle("Retour sonore du manipulateur",isOn: $model.settings.sidetone)
                Stepper("Tonalité : \(model.settings.toneHz) Hz",value: $model.settings.toneHz,in: 300...1200,step: 10)
                HStack { Text("Volume"); Slider(value: $model.settings.volume,in: 0...1) }
                Toggle("Retour haptique à l’appui",isOn: $model.settings.vibration)
                HStack { Text("Sortie audio iOS"); Spacer(); AudioRoutePicker().frame(width: 44,height: 44) }
            } header: { Text("Audio") } footer: {
                Text("iOS choisit la sortie selon le casque et les périphériques connectés. Le sélecteur système permet de choisir une sortie disponible. Le réglage Android « output » est conservé dans le profil pour les échanges.")
            }
            Section("Apprentissage") {
                Toggle("Progression guidée Koch",isOn: $model.settings.guided)
                Toggle("Afficher l’aide en manipulation",isOn: $model.settings.showHint)
                if !model.settings.guided {
                    Stepper("Caractères : \(model.settings.listenWpm) WPM",value: $model.settings.listenWpm,in: 5...60)
                    Stepper("Effectif Farnsworth : \(model.settings.farnsworthWpm) WPM",value: $model.settings.farnsworthWpm,in: 3...model.settings.listenWpm)
                    Toggle("Lettres",isOn: $model.settings.useLetters)
                    Toggle("Chiffres",isOn: $model.settings.useDigits)
                    Toggle("Ponctuation",isOn: $model.settings.usePunct)
                    Toggle("Prosignes",isOn: $model.settings.useProsigns)
                    Toggle("Accents",isOn: $model.settings.useAccents)
                }
            }
            Section("Personnel") {
                TextField("Indicatif",text: $model.settings.callsign).textInputAutocapitalization(.characters).autocorrectionDisabled()
                Picker("Apparence",selection: $model.settings.theme) {
                    Text("Système").tag("system"); Text("Clair").tag("light"); Text("Sombre").tag("dark")
                }
            }
        }.navigationTitle("Réglages")
            .onChange(of: model.settings.listenWpm) { value in if model.settings.farnsworthWpm > value { model.settings.farnsworthWpm = value } }
    }
}

struct AboutView: View {
    var body: some View {
        List {
            Section("HerMorse · portage iOS") {
                Text("Apprentissage du CW par la méthode Koch, à partir des sources Android v0.4.0 fournies.")
                Text("Hermès, messager des dieux, et Morse.")
                Text("Application hors ligne. Les profils sont stockés localement et exportables au format .hermorse.")
            }
            Section("État de cette version") {
                Text("Le projet iOS doit être compilé dans Xcode. L’audio, le microphone, les gestes et le boîtier doivent être validés sur un appareil Apple avant diffusion.")
                Text("Le mode Live n’est pas implémenté dans la source Android v0.4.")
            }
            Section { Text("© 2026 HerMorse · by ADT").foregroundStyle(.secondary) }
        }.navigationTitle("À propos")
    }
}
