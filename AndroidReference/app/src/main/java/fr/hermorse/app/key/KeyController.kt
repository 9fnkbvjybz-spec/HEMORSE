package fr.hermorse.app.key

import android.os.SystemClock
import fr.hermorse.app.audio.ToneEngine
import fr.hermorse.app.data.Morse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gère le manipulateur (pioche ou double palette iambique A/B) et décode les caractères.
 * Toutes les méthodes sont appelées depuis le thread principal.
 */
class KeyController(
    private val scope: CoroutineScope,
    private val tone: ToneEngine,
    private val listener: Listener
) {
    interface Listener {
        fun onPattern(pattern: String)
        fun onChar(label: String, wpm: Double, known: Boolean)
        fun onSpace()
        fun onKeyTime(ms: Long)
        fun onRatio(ratio: Double) {}
    }

    var mode = "straight"          // straight | iambicA | iambicB
    var keyerWpm = 18
    var adaptive = true
    var fixedWpm = 15

    // Estimations adaptatives (pioche)
    private var avgDit = 120.0   // départ lent (≈ 10 WPM)
    private var avgDah = 360.0

    private val pattern = StringBuilder()
    private var keyIsDown = false
    private var downAt = 0L
    private var lastUp = 0L
    private var spaceSent = true

    // Iambique
    private var dotPressed = false
    private var dashPressed = false
    private var dotMem = false
    private var dashMem = false
    private var iambicActive = false
    private var job: Job? = null

    private fun now() = SystemClock.uptimeMillis()

    fun currentUnit(): Double = when {
        mode != "straight" -> 1200.0 / keyerWpm
        adaptive -> (avgDit + avgDah / 3.0) / 2.0
        else -> 1200.0 / fixedWpm
    }

    fun currentWpm(): Double = 1200.0 / currentUnit()

    /** Rapport trait/point mesuré à la pioche (idéal : 3), null en iambique ou sans décodage adaptatif. */
    fun dahDitRatio(): Double? = if (mode == "straight" && adaptive) avgDah / avgDit else null

    // ---------- Pioche ----------
    fun straightDown() {
        if (keyIsDown) return
        keyIsDown = true
        downAt = now()
        tone.on()
    }

    fun straightUp() {
        if (!keyIsDown) return
        keyIsDown = false
        tone.off()
        val t = now()
        val d = (t - downAt).toDouble()
        lastUp = t
        listener.onKeyTime(t - downAt)
        if (d < 12) return // rebond
        val sym: Char
        if (adaptive) {
            val threshold = (avgDit + avgDah) / 2.0
            sym = if (d < threshold) '.' else '-'
            if (sym == '.') avgDit = 0.75 * avgDit + 0.25 * d else avgDah = 0.75 * avgDah + 0.25 * d
            avgDit = avgDit.coerceIn(25.0, 500.0)
            if (avgDah < 2.0 * avgDit) avgDah = 2.0 * avgDit
            if (avgDah > 5.0 * avgDit) avgDit = avgDah / 5.0
        } else {
            sym = if (d < 2 * 1200.0 / fixedWpm) '.' else '-'
        }
        addSym(sym)
    }

    // ---------- Double palette ----------
    fun paddle(isDot: Boolean, pressed: Boolean) {
        if (isDot) dotPressed = pressed else dashPressed = pressed
        if (pressed && job?.isActive != true) {
            job = scope.launch { runIambic() }
        }
    }

    private suspend fun runIambic() {
        iambicActive = true
        var last: Char? = null
        try {
            while (true) {
                val dot = dotPressed || dotMem
                val dash = dashPressed || dashMem
                if (!dot && !dash) break
                val sym = when {
                    dot && dash -> if (last == '.') '-' else '.'
                    dot -> '.'
                    else -> '-'
                }
                if (sym == '.') dotMem = false else dashMem = false
                val u = (1200.0 / keyerWpm).toLong()
                val len = if (sym == '.') u else 3 * u
                tone.on()
                val start = now()
                while (now() - start < len) {
                    watchMemory(sym); delay(2)
                }
                tone.off()
                listener.onKeyTime(len)
                addSym(sym)
                last = sym
                val gapStart = now()
                lastUp = gapStart
                while (now() - gapStart < u) {
                    watchMemory(sym); delay(2)
                }
            }
        } finally {
            iambicActive = false
            tone.off()
        }
    }

    /** Mode B : la palette opposée touchée pendant un élément est mémorisée (élément bonus au relâcher). */
    private fun watchMemory(sym: Char) {
        if (mode == "iambicB") {
            if (sym == '.' && dashPressed) dashMem = true
            if (sym == '-' && dotPressed) dotMem = true
        }
    }

    // ---------- Décodage ----------
    private fun addSym(c: Char) {
        pattern.append(c)
        spaceSent = false
        listener.onPattern(pattern.toString())
        if (pattern.length >= 9) flushChar()
    }

    /** À appeler régulièrement (toutes les ~15 ms) pour détecter la fin des caractères et des mots. */
    fun tick() {
        if (keyIsDown || iambicActive) return
        if (lastUp == 0L) return
        val silence = now() - lastUp
        val u = currentUnit()
        val charGap = if (mode == "straight") 2.5 else 2.0
        if (pattern.isNotEmpty() && silence > charGap * u) {
            flushChar()
        } else if (pattern.isEmpty() && !spaceSent && silence > 6 * u) {
            spaceSent = true
            listener.onSpace()
        }
    }

    private fun flushChar() {
        val p = pattern.toString()
        pattern.clear()
        val label = Morse.decode(p)
        listener.onPattern("")
        if (p.contains('.') && p.contains('-')) dahDitRatio()?.let { listener.onRatio(it) }
        listener.onChar(label ?: "✱", currentWpm(), label != null)
    }

    fun reset() {
        pattern.clear()
        spaceSent = true
        listener.onPattern("")
    }

    fun release() {
        job?.cancel()
        dotPressed = false; dashPressed = false; dotMem = false; dashMem = false
        keyIsDown = false
        tone.off()
    }
}
