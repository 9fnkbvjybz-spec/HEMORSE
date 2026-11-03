import SwiftUI
import UIKit
import HerMorseCore

/// UIKit touch-down/up preserves holds, cancellation and simultaneous two-paddle input.
private struct ContactButton: UIViewRepresentable {
    let title: String
    var contact: (Bool) -> Void
    func makeUIView(context: Context) -> ContactControl {
        let view = ContactControl(); view.accessibilityTraits = .button
        return view
    }
    func updateUIView(_ uiView: ContactControl, context: Context) {
        uiView.setTitle(title); uiView.onContact = contact
    }
    static func dismantleUIView(_ uiView: ContactControl, coordinator: ()) { uiView.release() }
}

private final class ContactControl: UIView {
    var onContact: (Bool) -> Void = { _ in }
    private let label = UILabel()
    private var down = false
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(red: 0.11, green: 0.24, blue: 0.50, alpha: 1)
        layer.cornerRadius = 24
        label.textColor = .white; label.textAlignment = .center; label.font = .boldSystemFont(ofSize: 30)
        label.translatesAutoresizingMaskIntoConstraints = false; addSubview(label)
        NSLayoutConstraint.activate([label.centerXAnchor.constraint(equalTo: centerXAnchor), label.centerYAnchor.constraint(equalTo: centerYAnchor)])
        isAccessibilityElement = true
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unused") }
    func setTitle(_ title: String) { label.text = title; accessibilityLabel = title }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard !down else { return }; down = true; alpha = 0.55; onContact(true)
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { release() }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { release() }
    override func didMoveToWindow() { if window == nil { release() } }
    func release() { if down { down = false; alpha = 1; onContact(false) } }
    override func accessibilityActivate() -> Bool {
        // VoiceOver activation sends a short dit; physical/touch holds remain available.
        onContact(true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in self?.onContact(false) }
        return true
    }
}

private struct ExternalKeyboard: UIViewRepresentable {
    var onContact: (Bool,Bool) -> Void
    func makeUIView(context: Context) -> KeyboardView { KeyboardView() }
    func updateUIView(_ uiView: KeyboardView, context: Context) {
        uiView.onContact = onContact
        DispatchQueue.main.async { if uiView.window != nil && !uiView.isFirstResponder { uiView.becomeFirstResponder() } }
    }
    static func dismantleUIView(_ uiView: KeyboardView, coordinator: ()) { uiView.releaseAll(); uiView.resignFirstResponder() }
}
private final class KeyboardView: UIView {
    var onContact: (Bool,Bool) -> Void = { _,_ in }
    private var active: [UIKeyboardHIDUsage:Bool] = [:]
    override var canBecomeFirstResponder: Bool { true }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil { becomeFirstResponder() } else { releaseAll() }
    }
    private func side(_ key: UIKey) -> Bool? {
        switch key.keyCode {
        case .keyboardLeftControl, .keyboardOpenBracket: return true
        case .keyboardRightControl, .keyboardCloseBracket: return false
        default: return nil
        }
    }
    private func handle(_ presses: Set<UIPress>, down: Bool) -> Set<UIPress> {
        var remaining = Set<UIPress>()
        for press in presses {
            guard let key = press.key, let left = side(key) else { remaining.insert(press); continue }
            let before = active.values.contains(left)
            if down { active[key.keyCode] = left } else { active.removeValue(forKey: key.keyCode) }
            let after = active.values.contains(left)
            if before != after { onContact(left, after) }
        }
        return remaining
    }
    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) { super.pressesBegan(handle(presses, down: true), with: event) }
    override func pressesEnded(_ presses: Set<UIPress>, with event: UIPressesEvent?) { super.pressesEnded(handle(presses, down: false), with: event) }
    override func pressesCancelled(_ presses: Set<UIPress>, with event: UIPressesEvent?) { super.pressesCancelled(handle(presses, down: false), with: event) }
    func releaseAll() {
        let sides = Set(active.values); active = [:]
        for left in sides { onContact(left, false) }
    }
}

struct KeyPad: View {
    @ObservedObject var model: AppModel
    @ObservedObject var session: KeySession
    var body: some View {
        VStack(spacing: 10) {
            if model.settings.keySource == "external" {
                HStack {
                    Image(systemName: "keyboard")
                    Text(session.externalSeen ? "Entrée clavier reçue" : "Appuyez sur le boîtier pour le tester")
                    Spacer()
                    Circle().fill(session.leftDown ? Color.orange : Color.gray).frame(width: 12, height: 12)
                    Circle().fill(session.rightDown ? Color.orange : Color.gray).frame(width: 12, height: 12)
                }.font(.caption)
                ExternalKeyboard { session.contact(left: $0, pressed: $1, external: true) }.frame(width: 1,height: 1).accessibilityHidden(true)
            }
            HStack(spacing: 12) {
                ContactButton(title: model.settings.keyMode == "straight" ? "PIOCHE" : (model.settings.swapPaddles ? "—" : "•")) {
                    session.contact(left: true, pressed: $0)
                }
                if model.settings.keyMode != "straight" {
                    ContactButton(title: model.settings.swapPaddles ? "•" : "—") { session.contact(left: false, pressed: $0) }
                }
            }.frame(height: model.settings.keySource == "external" ? 90 : CGFloat(model.settings.keyHeight))
        }
    }
}
