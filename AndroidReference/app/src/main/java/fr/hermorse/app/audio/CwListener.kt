package fr.hermorse.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import fr.hermorse.app.data.Morse
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Décodeur morse à partir des durées de « bip » et de silence (vitesse adaptative). */
class CwDecoder(private val onChar: (String) -> Unit, private val onSpace: () -> Unit) {
    private var avgDit = 100.0
    private var avgDah = 300.0
    private val pattern = StringBuilder()
    private var spaceSent = true

    fun unit(): Double = (avgDit + avgDah / 3.0) / 2.0
    fun wpm(): Double = 1200.0 / unit()

    fun mark(ms: Int) {
        if (ms < 20) return // parasite
        val d = ms.toDouble()
        val threshold = (avgDit + avgDah) / 2.0
        val sym = if (d < threshold) '.' else '-'
        if (sym == '.') avgDit = 0.8 * avgDit + 0.2 * d else avgDah = 0.8 * avgDah + 0.2 * d
        avgDit = avgDit.coerceIn(20.0, 600.0)
        if (avgDah < 2.0 * avgDit) avgDah = 2.0 * avgDit
        if (avgDah > 5.0 * avgDit) avgDit = avgDah / 5.0
        pattern.append(sym)
        spaceSent = false
        if (pattern.length > 8) flush()
    }

    /** Appelé pendant un silence, avec sa durée depuis le dernier bip. */
    fun silence(ms: Int) {
        val u = unit()
        if (pattern.isNotEmpty() && ms > 2 * u) flush()
        else if (pattern.isEmpty() && !spaceSent && ms > 5 * u) {
            spaceSent = true
            onSpace()
        }
    }

    private fun flush() {
        val p = pattern.toString()
        pattern.clear()
        onChar(Morse.decode(p) ?: "✱")
    }
}

/**
 * Écoute le micro, détecte la tonalité CW (filtre de Goertzel), suit sa fréquence (AFC par FFT)
 * et décode le morse. Les événements sont renvoyés sur le thread principal.
 */
class CwListener(private val onEvent: (Event) -> Unit) {

    sealed class Event {
        data class Decoded(val label: String) : Event()
        object Space : Event()
        class Status(
            val level: Float, val keyed: Boolean, val signal: Boolean,
            val spectrum: FloatArray, val freq: Double, val wpm: Double
        ) : Event()
    }

    companion object {
        const val SR = 8000
        const val FFT = 512
        const val F_MIN = 300.0
        const val F_MAX = 1200.0
        val BIN_MIN = (F_MIN * FFT / SR).toInt()
        val BIN_MAX = (F_MAX * FFT / SR).toInt()
        fun binFreq(bin: Double) = bin * SR / FFT
    }

    @Volatile var targetFreq = 600.0
    @Volatile var afc = true
    @Volatile var sensitivity = 0.5f
    @Volatile private var running = false
    private var thread: Thread? = null
    private var rec: AudioRecord? = null
    private val main = Handler(Looper.getMainLooper())

    private fun post(e: Event) = main.post { onEvent(e) }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SR,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 4096)
            )
        } catch (_: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release(); return false
        }
        rec = r
        r.startRecording()
        running = true
        thread = Thread { loop(r) }.apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        rec?.let {
            try { it.stop() } catch (_: Exception) { }
            it.release()
        }
        rec = null
    }

    private fun loop(r: AudioRecord) {
        val n = 80 // blocs de 10 ms
        val buf = ShortArray(n)
        val ring = DoubleArray(FFT)
        var ringPos = 0
        var floor = 1e-4
        var peak = 1e-4
        var keyed = false
        var stateMs = 0
        var blocks = 0
        var candidate = 0.0
        var candidateCount = 0
        val decoder = CwDecoder({ post(Event.Decoded(it)) }, { post(Event.Space) })
        val re = DoubleArray(FFT)
        val im = DoubleArray(FFT)
        val window = DoubleArray(FFT) { 0.5 - 0.5 * cos(2 * PI * it / (FFT - 1)) }

        while (running) {
            val got = r.read(buf, 0, n)
            if (got <= 0) continue

            // Goertzel sur la fréquence suivie
            val w = 2 * PI * targetFreq / SR
            val coeff = 2 * cos(w)
            var s1 = 0.0
            var s2 = 0.0
            for (i in 0 until got) {
                val x = buf[i] / 32768.0
                ring[ringPos] = x
                ringPos = (ringPos + 1) % FFT
                val s0 = x + coeff * s1 - s2
                s2 = s1; s1 = s0
            }
            val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
            val mag = sqrt(max(power, 0.0)) / got

            // Plancher de bruit et crête, suivis lentement
            if (mag < floor) floor = floor * 0.9 + mag * 0.1 else floor += (mag - floor) * 0.005
            peak = if (mag > peak) mag else peak - (peak - floor) * 0.004
            val span = peak - floor
            val k = 0.65 - 0.4 * sensitivity
            val signal = peak > floor * 4 && peak > 2e-4
            val on = if (keyed) signal && mag > floor + span * k * 0.6 else signal && mag > floor + span * k

            if (on == keyed) {
                stateMs += 10
                if (!keyed) decoder.silence(stateMs)
            } else {
                if (keyed) decoder.mark(stateMs)
                keyed = on
                stateMs = 10
            }

            blocks++
            if (blocks % 10 == 0) {
                // Spectre (FFT) pour l'affichage et l'AFC
                for (i in 0 until FFT) {
                    re[i] = ring[(ringPos + i) % FFT] * window[i]
                    im[i] = 0.0
                }
                fft(re, im)
                val bins = FloatArray(BIN_MAX - BIN_MIN + 1)
                var maxV = 0.0
                var maxI = 0
                for (b in BIN_MIN..BIN_MAX) {
                    val v = sqrt(re[b] * re[b] + im[b] * im[b])
                    bins[b - BIN_MIN] = v.toFloat()
                    if (v > maxV) { maxV = v; maxI = b }
                }
                val sorted = bins.sorted()
                val median = sorted[sorted.size / 2].toDouble().coerceAtLeast(1e-9)
                if (afc && maxV / median > 6 && maxI > BIN_MIN && maxI < BIN_MAX) {
                    // interpolation parabolique autour du pic
                    val a = bins[maxI - 1 - BIN_MIN].toDouble()
                    val b = bins[maxI - BIN_MIN].toDouble()
                    val c = bins[maxI + 1 - BIN_MIN].toDouble()
                    val denom = a - 2 * b + c
                    val delta = if (abs(denom) > 1e-12) 0.5 * (a - c) / denom else 0.0
                    val f = binFreq(maxI + delta)
                    if (abs(f - candidate) < 20) candidateCount++ else { candidate = f; candidateCount = 1 }
                    if (candidateCount >= 3 && abs(f - targetFreq) > 10) targetFreq = f
                }
                val norm = (maxV.toFloat()).coerceAtLeast(1e-6f)
                val spec = FloatArray(bins.size) { (bins[it] / norm).coerceIn(0f, 1f) }
                post(
                    Event.Status(
                        level = (mag / (peak + 1e-9)).toFloat().coerceIn(0f, 1f),
                        keyed = keyed, signal = signal, spectrum = spec,
                        freq = targetFreq, wpm = decoder.wpm()
                    )
                )
            }
        }
    }

    /** FFT radix-2 en place. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
