package fr.hermorse.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.hermorse.app.audio.CwListener
import kotlin.math.roundToInt

@Composable
fun QsoScreen(model: AppModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val s = model.settings

    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var keyed by remember { mutableStateOf(false) }
    var signal by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var spectrum by remember { mutableStateOf(FloatArray(0)) }
    var freq by remember { mutableDoubleStateOf(s.qsoFreq.toDouble()) }
    var wpm by remember { mutableDoubleStateOf(0.0) }
    var sessionChars by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val listener = remember {
        CwListener { e ->
            when (e) {
                is CwListener.Event.Decoded -> {
                    text = (text + e.label).takeLast(5000)
                    if (e.label != "✱") {
                        sessionChars++
                        if (sessionChars >= 100) model.award("qso_reel")
                    }
                }
                is CwListener.Event.Space -> if (!text.endsWith(" ")) text += " "
                is CwListener.Event.Status -> {
                    keyed = e.keyed; signal = e.signal; level = e.level
                    spectrum = e.spectrum; freq = e.freq; wpm = e.wpm
                }
            }
        }
    }
    LaunchedEffect(s.qsoSensitivity, s.qsoAfc) {
        listener.sensitivity = s.qsoSensitivity
        listener.afc = s.qsoAfc
    }

    fun startListening() {
        listener.targetFreq = freq
        if (listener.start()) {
            listening = true; error = null
            view.keepScreenOn = true
        } else error = "Impossible d'ouvrir le micro."
    }

    fun stopListening() {
        listener.stop()
        listening = false
        view.keepScreenOn = false
        model.updateSettings(model.settings.copy(qsoFreq = freq.roundToInt()))
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening() else error = "HerMorse a besoin du micro pour écouter ton poste."
    }

    DisposableEffect(Unit) {
        onDispose {
            listener.stop()
            view.keepScreenOn = false
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Spectre : touche pour choisir la tonalité
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(16.dp).clip(RoundedCornerShape(50))
                        .background(if (keyed) Gold else MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${freq.roundToInt()} Hz",
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Text(
                    when {
                        !listening -> "En pause"
                        !signal -> "Pas de signal"
                        else -> "≈ %.0f WPM".format(wpm)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            SpectrumView(spectrum, freq) { f ->
                freq = f
                listener.targetFreq = f
                if (s.qsoAfc) model.updateSettings(model.settings.copy(qsoAfc = false))
            }
            Row {
                Text("300 Hz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Touche le spectre pour choisir la tonalité", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("1200 Hz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(level).height(6.dp).background(if (keyed) Gold else MaterialTheme.colorScheme.primary))
            }
        }
        Spacer(Modifier.height(10.dp))

        // Texte décodé
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRADUCTION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                    context.startActivity(Intent.createChooser(i, "Partager la traduction"))
                }, enabled = text.isNotBlank()) { Text("Partager") }
                TextButton(onClick = { text = "" }) { Text("Effacer") }
            }
            Text(
                text.ifEmpty { "Pose le téléphone près du haut-parleur de ton poste (filtre CW étroit conseillé), puis lance l'écoute." },
                fontFamily = FontFamily.Monospace,
                fontSize = if (text.isEmpty()) 15.sp else 24.sp,
                color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).verticalScroll(scroll)
            )
        }
        Spacer(Modifier.height(10.dp))

        // Réglages rapides
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Suivi auto de la tonalité", Modifier.weight(1f))
            Switch(checked = s.qsoAfc, onCheckedChange = { model.updateSettings(model.settings.copy(qsoAfc = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sensibilité", Modifier.width(96.dp))
            Slider(
                value = s.qsoSensitivity,
                onValueChange = { model.updateSettings(model.settings.copy(qsoSensitivity = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        error?.let { Text(it, color = BadRed) }
        Spacer(Modifier.height(6.dp))
        if (listening) {
            OutlinedButton(onClick = { stopListening() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Arrêter l'écoute") }
        } else {
            Button(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startListening() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Écouter le poste") }
        }
    }
}

@Composable
private fun SpectrumView(bins: FloatArray, freq: Double, onPick: (Double) -> Unit) {
    val bar = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val fMin = CwListener.F_MIN
    val fMax = CwListener.F_MAX
    Canvas(
        Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    onPick(fMin + (fMax - fMin) * (p.x / size.width).toDouble().coerceIn(0.0, 1.0))
                }
            }
    ) {
        drawRect(bg)
        if (bins.isNotEmpty()) {
            val w = size.width / bins.size
            bins.forEachIndexed { i, v ->
                val h = v * size.height
                drawRect(bar.copy(alpha = 0.35f + 0.65f * v), Offset(i * w, size.height - h), Size(w * 0.85f, h))
            }
        }
        val x = ((freq - fMin) / (fMax - fMin)).toFloat().coerceIn(0f, 1f) * size.width
        drawLine(Gold, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
    }
}

