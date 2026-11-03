import SwiftUI
import HerMorseCore

struct QSOView: View {
    @ObservedObject var model: AppModel
    @StateObject private var microphone = MicrophoneService()
    var body: some View {
        ScrollView {
            VStack(alignment: .leading,spacing: 18) {
                HStack {
                    Circle().fill(microphone.status?.keyed == true ? .orange : .gray).frame(width: 14,height: 14)
                    Text("\(Int(microphone.status?.frequency ?? Double(model.settings.qsoFreq))) Hz").bold()
                    Spacer()
                    Text(microphone.listening ? (microphone.status?.signal == true ? "≈ \(Int(microphone.status?.wpm ?? 0)) WPM" : "Pas de signal") : "En pause").font(.caption)
                }
                spectrum.frame(height: 90).accessibilityLabel("Spectre du signal entre 300 et 1 200 hertz")
                ProgressView(value: microphone.status?.level ?? 0)
                HStack {
                    Text("TRADUCTION").font(.caption.bold()); Spacer()
                    ShareLink(item: microphone.text) { Image(systemName: "square.and.arrow.up") }.disabled(microphone.text.isEmpty)
                    Button("Effacer") { microphone.clear() }
                }
                Text(microphone.text.isEmpty ? "Placez l’iPhone près du haut-parleur du poste, puis lancez l’écoute. Un filtre CW étroit améliore le décodage." : microphone.text)
                    .font(.system(.title3,design: .monospaced)).frame(maxWidth: .infinity,minHeight: 150,alignment: .topLeading)
                    .padding().background(Color(uiColor: .secondarySystemGroupedBackground),in: RoundedRectangle(cornerRadius: 16))
                    .textSelection(.enabled)
                Toggle("Suivi automatique de la tonalité",isOn: $model.settings.qsoAfc)
                Text("Sensibilité")
                Slider(value: $model.settings.qsoSensitivity,in: 0...1)
                if !model.settings.qsoAfc {
                    Stepper("Tonalité : \(model.settings.qsoFreq) Hz",value: $model.settings.qsoFreq,in: 300...1200,step: 10)
                }
                if let error = microphone.error { Text(error).foregroundStyle(.red) }
                Button(microphone.listening ? "Arrêter l’écoute" : "Écouter le poste",systemImage: microphone.listening ? "stop.fill" : "mic.fill") {
                    if microphone.listening { stop() }
                    else { model.audio.stop(); microphone.requestStart(settings: model.settings) }
                }.buttonStyle(.borderedProminent)
                Text("Le son est analysé sur l’appareil et n’est ni enregistré ni envoyé à un serveur.").font(.caption).foregroundStyle(.secondary)
            }.padding()
        }.background(Color(uiColor: .systemGroupedBackground))
            .onAppear { model.audio.stop(); microphone.onHundredCharacters = { model.award("qso_reel") } }
            .onDisappear { stop() }
            .onChange(of: model.settings) { microphone.configure($0) }
    }
    private var spectrum: some View {
        GeometryReader { geometry in
            Canvas { context,size in
                let bins = microphone.status?.spectrum ?? Array(repeating: 0.02,count: 58)
                let width = size.width / CGFloat(bins.count)
                for (i,value) in bins.enumerated() {
                    let height = CGFloat(value)*size.height
                    context.fill(Path(CGRect(x: CGFloat(i)*width,y: size.height-height,width: width*0.8,height: height)),with: .color(.blue))
                }
                let frequency = microphone.status?.frequency ?? Double(model.settings.qsoFreq)
                let x = CGFloat((frequency-300)/900)*size.width
                context.fill(Path(CGRect(x: x,y: 0,width: 2,height: size.height)),with: .color(.orange))
            }
            .background(Color(uiColor: .tertiarySystemFill))
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onEnded { value in
                model.settings.qsoFreq = Int(300 + min(1,max(0,value.location.x/max(1,geometry.size.width)))*900)
                model.settings.qsoAfc = false
            })
        }
    }
    private func stop() {
        if microphone.listening, let frequency = microphone.status?.frequency { model.settings.qsoFreq = Int(frequency) }
        microphone.stop()
    }
}
