import AVFoundation
import SwiftUI
import UIKit
import HerMorseCore

extension Notification.Name { static let hermorsePause = Notification.Name("HerMorse.pause") }

@MainActor
final class MicrophoneService: ObservableObject {
    @Published private(set) var listening = false
    @Published private(set) var text = ""
    @Published private(set) var status: SignalStatus?
    @Published var error: String?
    var onHundredCharacters: (() -> Void)?
    private let queue = DispatchQueue(label: "fr.hermorse.cw.analysis", qos: .userInitiated)
    private var engine: AVAudioEngine?
    private var analyzer: SignalAnalyzer?
    private var generation = UUID()
    private var decodedCount = 0
    private var observer: NSObjectProtocol?
    init() {
        observer = NotificationCenter.default.addObserver(forName: .hermorsePause, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.stop() }
        }
    }
    deinit { if let observer { NotificationCenter.default.removeObserver(observer) } }
    func requestStart(settings: Settings) {
        guard !listening else { return }
        let requestID = UUID(); generation = requestID
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            Task { @MainActor in
                guard let self, self.generation == requestID else { return }
                if granted { self.start(settings: settings) }
                else { self.error = "Autorisez le microphone dans Réglages > HerMorse pour écouter votre poste." }
            }
        }
    }
    private func start(settings: Settings) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement)
            try session.setActive(true)
            let audio = AVAudioEngine(), detector = SignalAnalyzer()
            detector.frequency = Double(settings.qsoFreq); detector.afc = settings.qsoAfc; detector.sensitivity = settings.qsoSensitivity
            let id = generation
            detector.onDecode = { [weak self] event in
                Task { @MainActor in
                    guard let self, self.generation == id, self.listening else { return }
                    switch event {
                    case .character(let label):
                        self.text = String((self.text + label).suffix(5000))
                        if label != "✱" { self.decodedCount += 1 }
                        if self.decodedCount == 100 { self.onHundredCharacters?() }
                    case .space: if !self.text.isEmpty && !self.text.hasSuffix(" ") { self.text += " " }
                    }
                }
            }
            detector.onStatus = { [weak self] value in
                Task { @MainActor in
                    guard let self, self.generation == id, self.listening else { return }
                    self.status = value
                }
            }
            let input = audio.inputNode, format = input.outputFormat(forBus: 0)
            guard format.sampleRate >= 8000, format.channelCount > 0 else {
                throw NSError(domain: "HerMorse", code: 1, userInfo: [NSLocalizedDescriptionKey: "Aucune entrée audio compatible disponible."])
            }
            // Captured state is accessed only by the analysis queue.
            var resampler = CWResampler()
            let analysisQueue = queue
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                guard let channel = buffer.floatChannelData?[0] else { return }
                let samples = Array(UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength)))
                analysisQueue.async { detector.feed(resampler.process(samples, sampleRate: format.sampleRate)) }
            }
            engine = audio; analyzer = detector
            try audio.start(); listening = true; error = nil
            UIApplication.shared.isIdleTimerDisabled = true
        } catch {
            stop(); self.error = "Impossible d’ouvrir le microphone : \(error.localizedDescription)"
        }
    }
    func configure(_ settings: Settings) {
        guard let analyzer else { return }
        queue.async {
            analyzer.frequency = Double(settings.qsoFreq)
            analyzer.afc = settings.qsoAfc; analyzer.sensitivity = settings.qsoSensitivity
        }
    }
    func clear() { text = "" }
    func stop() {
        generation = UUID()
        if let engine { engine.inputNode.removeTap(onBus: 0); engine.stop() }
        engine = nil; analyzer = nil; listening = false
        UIApplication.shared.isIdleTimerDisabled = false
    }
}
