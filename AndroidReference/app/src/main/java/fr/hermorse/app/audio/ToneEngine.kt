package fr.hermorse.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Générateur de tonalité à faible latence : un flux audio tourne en continu,
 * et l'appui sur le manipulateur ouvre ou ferme le son (avec une rampe de 5 ms anti-clic).
 */
class ToneEngine(private val context: Context) {

    @Volatile var freq = 600.0
    @Volatile var volume = 0.6f
    @Volatile var sidetone = true
    @Volatile var vibrate = false

    @Volatile private var keyDown = false
    @Volatile private var forceSound = false
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var output = "auto"

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun start() {
        if (running) return
        val sr = 48000
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track = t
        applyOutput()
        t.play()
        running = true
        thread = Thread { loop(t, sr) }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun loop(t: AudioTrack, sr: Int) {
        val n = 96
        val buf = ShortArray(n)
        var phase = 0.0
        var amp = 0.0
        val step = 1.0 / (0.005 * sr)
        while (running) {
            val target = if (keyDown && (sidetone || forceSound)) volume.toDouble() else 0.0
            val inc = 2 * PI * freq / sr
            for (i in 0 until n) {
                amp = if (amp < target) min(target, amp + step) else max(target, amp - step)
                buf[i] = (sin(phase) * amp * 32000).toInt().toShort()
                phase += inc
                if (phase > 2 * PI) phase -= 2 * PI
            }
            t.write(buf, 0, n)
        }
    }

    fun stop() {
        running = false
        keyDown = false
        thread?.join(300)
        thread = null
        track?.let {
            try { it.stop() } catch (_: Exception) { }
            it.release()
        }
        track = null
    }

    fun on() {
        keyDown = true
        if (vibrate) vibrator?.vibrate(VibrationEffect.createOneShot(3000, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun off() {
        keyDown = false
        if (vibrate) vibrator?.cancel()
    }

    fun setOutput(o: String) {
        output = o
        applyOutput()
    }

    private fun applyOutput() {
        val t = track ?: return
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val types = when (output) {
            "speaker" -> listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            "wired" -> listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET
            )
            "bt" -> listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            else -> emptyList()
        }
        val device = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in types }
        t.preferredDevice = device
    }

    /**
     * Joue un texte en morse avec l'espacement Farnsworth.
     * onChar reçoit l'index du caractère joué, -1 à la fin.
     */
    suspend fun playText(text: String, charWpm: Int, effWpm: Int, onChar: (Int) -> Unit) {
        val u = 1200.0 / charWpm
        val charGap: Double
        val wordGap: Double
        if (effWpm >= charWpm) {
            charGap = 3 * u; wordGap = 7 * u
        } else {
            val ta = (60.0 * charWpm - 37.2 * effWpm) / (charWpm * effWpm) * 1000.0
            charGap = 3 * ta / 19; wordGap = 7 * ta / 19
        }
        val unit = u.toLong()
        forceSound = true
        try {
            delay(400)
            text.forEachIndexed { i, c ->
                onChar(i)
                if (c == ' ') {
                    delay((wordGap - charGap).toLong().coerceAtLeast(0))
                } else {
                    val code = fr.hermorse.app.data.Morse.byLabel(c.toString())?.code ?: ""
                    code.forEach { e ->
                        keyDown = true
                        delay(if (e == '.') unit else 3 * unit)
                        keyDown = false
                        delay(unit)
                    }
                    delay((charGap - u).toLong().coerceAtLeast(0))
                }
            }
        } finally {
            keyDown = false
            forceSound = false
            onChar(-1)
        }
    }

    /** Joue un code morse (« .-- ») ; onIndex reçoit l'élément en cours, -1 à la fin. */
    suspend fun playCode(code: String, unitMs: Long, onIndex: (Int) -> Unit) {
        forceSound = true
        try {
            code.forEachIndexed { i, c ->
                onIndex(i)
                keyDown = true
                delay(if (c == '.') unitMs else 3 * unitMs)
                keyDown = false
                delay(unitMs)
            }
        } finally {
            keyDown = false
            forceSound = false
            onIndex(-1)
        }
    }
}
