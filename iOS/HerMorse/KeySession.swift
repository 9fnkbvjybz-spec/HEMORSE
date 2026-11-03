import SwiftUI
import HerMorseCore

@MainActor
final class KeySession: ObservableObject {
    @Published var pattern = ""
    @Published var text = ""
    @Published var wpm = 0.0
    @Published var externalSeen = false
    @Published var leftDown = false
    @Published var rightDown = false
    var enabled = true
    var onCharacter: ((String,Double,Bool) -> Void)?
    private let model: AppModel
    private let keyer: Keyer
    private var timer: Timer?
    private var observer: NSObjectProtocol?
    private var pressedContacts: [String:(Bool,Bool)] = [:]
    init(model: AppModel) {
        self.model = model; keyer = Keyer(settings: model.settings)
        keyer.emit = { [weak self] event in self?.receive(event) }
        observer = NotificationCenter.default.addObserver(forName: .hermorsePause, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.stop() }
        }
    }
    deinit { timer?.invalidate(); if let observer { NotificationCenter.default.removeObserver(observer) } }
    private var now: Double { ProcessInfo.processInfo.systemUptime * 1000 }
    func start() {
        stop(); keyer.settings = model.settings
        guard model.startAudio() else { return }
        let timer = Timer(timeInterval: 0.002, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        RunLoop.main.add(timer, forMode: .common); self.timer = timer
    }
    private func tick() { if enabled { keyer.tick(now: now) } }
    func contact(left: Bool, pressed: Bool, external: Bool = false) {
        guard enabled, timer != nil else { return }
        if external {
            guard model.settings.keySource == "external" else { return }
            externalSeen = true
            if left { leftDown = pressed } else { rightDown = pressed }
        }
        let id = "\(external)-\(left)"
        let dot = model.settings.swapPaddles ? !left : left
        if pressed { pressedContacts[id] = (dot,true) } else { pressedContacts.removeValue(forKey: id) }
        // Touch and USB can overlap; releasing one must not release the other.
        let isPressed = pressedContacts.values.contains { $0.0 == dot && $0.1 }
        keyer.contact(dot: dot, pressed: isPressed, now: now)
    }
    func reset() { keyer.reset(); pressedContacts = [:]; pattern = "" }
    func stop() {
        timer?.invalidate(); timer = nil; reset(); model.audio.cancelPlayback()
        leftDown = false; rightDown = false
    }
    private func receive(_ event: KeyEvent) {
        switch event {
        case .tone(let down): model.audio.key(down)
        case .pattern(let value): pattern = value
        case .character(let label, let speed, let known):
            wpm = speed
            if let onCharacter { onCharacter(label, speed, known) }
            else { text = String((text + label).suffix(5000)); model.recordCharacter(label, wpm: speed, known: known) }
        case .space: if onCharacter == nil && !text.hasSuffix(" ") { text += " " }
        case .keyTime(let ms): model.updateStats { $0.keyTimeMs += Int64(ms) }
        case .ratio(let ratio): model.updateStats { $0.recentRatio = Array(($0.recentRatio + [ratio]).suffix(50)) }
        }
    }
}
