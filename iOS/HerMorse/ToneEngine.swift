import AVFoundation
import UIKit
import HerMorseCore

/// Continuous 48kHz oscillator, 5ms amplitude ramp, playback with cancellable tasks.
final class ToneEngine {
    private final class RenderState {
        let lock = NSLock()
        var frequency = 600.0, volume = 0.6
        var key = false
        var phase = 0.0, amplitude = 0.0
    }
    private let state = RenderState()
    private var engine: AVAudioEngine?
    private var settings = Settings()
    private var playback: Task<Void, Never>?
    private var playbackID = UUID()
    private var interruption: NSObjectProtocol?
    var onInterruption: (() -> Void)?
    init() {
        interruption = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] _ in
            self?.stop(); self?.onInterruption?()
        }
    }
    deinit { if let interruption { NotificationCenter.default.removeObserver(interruption) } }
    @MainActor
    func start(settings: Settings) throws {
        self.settings = settings
        state.lock.lock(); state.frequency = Double(settings.toneHz); state.volume = settings.volume; state.lock.unlock()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default)
        try session.setPreferredIOBufferDuration(0.005)
        try session.setActive(true)
        if let engine, engine.isRunning { return }
        let audio = AVAudioEngine()
        let format = AVAudioFormat(standardFormatWithSampleRate: 48000, channels: 1)!
        let state = self.state
        let source = AVAudioSourceNode { _, _, count, list -> OSStatus in
            state.lock.lock()
            let frequency = state.frequency, target = state.key ? state.volume : 0
            state.lock.unlock()
            let buffers = UnsafeMutableAudioBufferListPointer(list)
            for frame in 0..<Int(count) {
                let step = 1.0 / (0.005 * 48000)
                state.amplitude += min(step, max(-step, target - state.amplitude))
                let value = Float(sin(state.phase) * state.amplitude * 0.95)
                state.phase += 2 * .pi * frequency / 48000
                if state.phase > 2 * .pi { state.phase -= 2 * .pi }
                for buffer in buffers { buffer.mData?.assumingMemoryBound(to: Float.self)[frame] = value }
            }
            return noErr
        }
        audio.attach(source); audio.connect(source, to: audio.mainMixerNode, format: format)
        try audio.start(); engine = audio
    }
    func key(_ pressed: Bool, force: Bool = false) {
        state.lock.lock(); state.key = pressed && (force || settings.sidetone); state.lock.unlock()
        if pressed && settings.vibration {
            DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        }
    }
    @MainActor
    func play(tokens: [String], characterWpm: Int, effectiveWpm: Int,
              progress: @escaping (Int) -> Void = { _ in }, completion: @escaping () -> Void = {}) {
        cancelPlayback()
        let id = UUID(); playbackID = id
        let sequence = MorseTiming.sequence(tokens: tokens, characterWpm: characterWpm, effectiveWpm: effectiveWpm)
        playback = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await Task.sleep(nanoseconds: 400_000_000)
                for segment in sequence {
                    try Task.checkCancellation()
                    progress(segment.tokenIndex); self.key(segment.on, force: true)
                    try await Task.sleep(nanoseconds: UInt64(segment.milliseconds * 1_000_000))
                }
                guard self.playbackID == id else { return }
                self.key(false); progress(-1); self.playback = nil; completion()
            } catch {
                if self.playbackID == id { self.key(false); progress(-1) }
            }
        }
    }
    func cancelPlayback() {
        playbackID = UUID(); playback?.cancel(); playback = nil; key(false)
    }
    func stop() {
        cancelPlayback(); engine?.stop(); engine = nil
    }
}
