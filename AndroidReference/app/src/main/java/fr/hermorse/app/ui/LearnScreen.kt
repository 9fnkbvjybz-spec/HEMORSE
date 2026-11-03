@file:OptIn(ExperimentalMaterial3Api::class)

package fr.hermorse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.hermorse.app.audio.ToneEngine
import fr.hermorse.app.data.Category
import fr.hermorse.app.data.Morse
import fr.hermorse.app.data.MorseChar
import fr.hermorse.app.data.Settings
import fr.hermorse.app.key.ExternalKey
import fr.hermorse.app.key.KeyController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LearnMode(val title: String, val ready: Boolean) {
    FICHES("Fiches", true),
    LIBRE("Saisie libre", true),
    ECOUTE("Écouter → écrire", true),
    LECTURE("Lire → manipuler", true)
}

@Composable
fun LearnScreen(model: AppModel, tone: ToneEngine, mode: LearnMode, onMode: (LearnMode) -> Unit) {
    LaunchedEffect(mode) {
        when (mode) {
            LearnMode.FICHES -> model.useExercise("fiches")
            LearnMode.LIBRE -> model.useExercise("libre")
            LearnMode.ECOUTE -> model.useExercise("ecoute")
            LearnMode.LECTURE -> model.useExercise("lecture")
        }
    }
    TrainingTimer(model)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LearnMode.entries.forEach { m ->
                FilterChip(
                    selected = m == mode,
                    onClick = { if (m.ready) onMode(m) },
                    enabled = m.ready,
                    label = { Text(if (m.ready) m.title else "${m.title} · v0.2") }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                LearnMode.FICHES -> FichesScreen(model.listenSpeeds().first, tone)
                LearnMode.LIBRE -> FreeKeyScreen(model, tone)
                LearnMode.ECOUTE -> ListenScreen(model, tone)
                LearnMode.LECTURE -> ReadKeyScreen(model, tone)
            }
        }
    }
}

/** Compte le temps d'entraînement tant que l'écran est affiché et l'appli au premier plan. */
@Composable
fun TrainingTimer(model: AppModel) {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                model.addTraining(10_000)
            }
        }
    }
}

// ---------------- Schéma morse ----------------

@Composable
fun MorseSchema(code: String, dot: Dp = 10.dp, highlight: Int = -1, color: Color = MaterialTheme.colorScheme.primary) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(dot * 0.7f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        code.forEachIndexed { i, c ->
            Box(
                Modifier
                    .width(if (c == '.') dot else dot * 3)
                    .height(dot)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == highlight) Gold else color)
            )
        }
    }
}

// ---------------- Fiches ----------------

@Composable
fun FichesScreen(wpm: Int, tone: ToneEngine) {
    var selected by remember { mutableStateOf<MorseChar?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Touche un caractère pour voir son schéma et l'écouter. Le n° Koch indique l'ordre d'apprentissage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Category.entries.forEach { cat ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    cat.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                )
            }
            items(Morse.all.filter { it.category == cat }) { mc ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { selected = mc }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(mc.label, fontSize = if (mc.label.length > 2) 16.sp else 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    MorseSchema(mc.code, dot = 5.dp)
                    Morse.kochRank(mc.label)?.let {
                        Text("Koch $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    selected?.let { mc -> CharDetail(mc, wpm, tone) { selected = null } }
}

@Composable
private fun CharDetail(mc: MorseChar, wpm: Int, tone: ToneEngine, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableIntStateOf(-1) }
    var job by remember { mutableStateOf<Job?>(null) }
    val unit = (1200.0 / wpm).toLong()
    fun play() {
        job?.cancel()
        job = scope.launch { tone.playCode(mc.code, unit) { playing = it } }
    }
    LaunchedEffect(mc) { play() }
    AlertDialog(
        onDismissRequest = { job?.cancel(); onClose() },
        confirmButton = { TextButton(onClick = { job?.cancel(); onClose() }) { Text("Fermer") } },
        dismissButton = {
            Button(onClick = { play() }) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Écouter")
            }
        },
        title = null,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(mc.label, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                MorseSchema(mc.code, dot = 18.dp, highlight = playing)
                Spacer(Modifier.height(16.dp))
                Text(Morse.spoken(mc.code), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(mc.code.replace(".", "•").replace("-", "—"), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mc.hint.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(mc.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Morse.kochRank(mc.label)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Leçon Koch n°$it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Text("Joué à $wpm WPM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// ---------------- Saisie libre ----------------

@Composable
fun FreeKeyScreen(model: AppModel, tone: ToneEngine) {
    val s = model.settings
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var lastWpm by remember { mutableDoubleStateOf(0.0) }

    val ctrl = remember {
        KeyController(scope, tone, object : KeyController.Listener {
            override fun onPattern(pattern: String) { setPattern(pattern) }
            override fun onChar(label: String, wpm: Double, known: Boolean) {
                text += label
                lastWpm = wpm
                model.recordChar(known, wpm)
            }
            override fun onSpace() { text += " " }
            override fun onKeyTime(ms: Long) { model.addKeyTime(ms) }
            override fun onRatio(ratio: Double) { model.recordRatio(ratio) }

            private fun setPattern(p: String) { pattern = p }
        })
    }
    LaunchedEffect(s.keyMode, s.keyerWpm, s.adaptiveDecode, s.fixedWpm) {
        ctrl.release()
        ctrl.mode = s.keyMode
        ctrl.keyerWpm = s.keyerWpm
        ctrl.adaptive = s.adaptiveDecode
        ctrl.fixedWpm = s.fixedWpm
    }
    LaunchedEffect(Unit) {
        while (true) {
            ctrl.tick()
            delay(15)
        }
    }
    DisposableEffect(Unit) { onDispose { ctrl.release() } }

    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("TEXTE DÉCODÉ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                text.ifEmpty { "Manipule pour commencer…" },
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp,
                color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).verticalScroll(scroll)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MorseSchema(pattern, dot = 12.dp, color = Gold) }
            Text(
                if (lastWpm > 0) "≈ %.0f WPM".format(lastWpm) else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (s.keyMode) {
                    "iambicA" -> "Double palette · iambique A · ${s.keyerWpm} WPM"
                    "iambicB" -> "Double palette · iambique B · ${s.keyerWpm} WPM"
                    else -> if (s.adaptiveDecode) "Pioche · vitesse adaptative" else "Pioche · ${s.fixedWpm} WPM"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { text = ""; ctrl.reset() }) { Text("Effacer") }
        }
        KeyPad(s, ctrl)
    }
}

@Composable
fun KeyPad(s: Settings, ctrl: KeyController) {
    val external = s.keySource == "external"
    DisposableEffect(ctrl, s.keyMode, s.swapPaddles, external) {
        val fn: (Boolean, Boolean) -> Unit = { left, down ->
            if (s.keyMode == "straight") {
                if (down) ctrl.straightDown() else ctrl.straightUp()
            } else {
                val isDot = if (s.swapPaddles) !left else left
                ctrl.paddle(isDot, down)
            }
        }
        val handler = if (external) fn else null
        ExternalKey.handler = handler
        onDispose { if (handler != null && ExternalKey.handler === handler) ExternalKey.handler = null }
    }
    if (external) {
        ExternalKeyPanel(s)
        Spacer(Modifier.height(8.dp))
    }
    val h = if (external) 90.dp else s.keyHeight.dp
    if (s.keyMode == "straight") {
        KeyButton("PIOCHE", Modifier.fillMaxWidth().height(h), { ctrl.straightDown() }, { ctrl.straightUp() })
    } else {
        val leftIsDot = !s.swapPaddles
        Row(Modifier.fillMaxWidth().height(h), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyButton(
                if (leftIsDot) "•" else "—", Modifier.weight(1f).fillMaxSize(),
                { ctrl.paddle(leftIsDot, true) }, { ctrl.paddle(leftIsDot, false) }
            )
            KeyButton(
                if (leftIsDot) "—" else "•", Modifier.weight(1f).fillMaxSize(),
                { ctrl.paddle(!leftIsDot, true) }, { ctrl.paddle(!leftIsDot, false) }
            )
        }
    }
}

/** Voyants du manipulateur externe (boîtier USB). */
@Composable
fun ExternalKeyPanel(s: Settings) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Manipulateur externe", fontWeight = FontWeight.SemiBold)
            Text(
                if (ExternalKey.seen) "Boîtier USB détecté" else "Appuie sur ton manipulateur pour tester le boîtier",
                style = MaterialTheme.typography.bodySmall,
                color = if (ExternalKey.seen) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (s.keyMode == "straight") {
            Lamp("PIOCHE", ExternalKey.leftDown)
        } else {
            val dotLeft = !s.swapPaddles
            Lamp("•", if (dotLeft) ExternalKey.leftDown else ExternalKey.rightDown)
            Spacer(Modifier.width(8.dp))
            Lamp("—", if (dotLeft) ExternalKey.rightDown else ExternalKey.leftDown)
        }
    }
}

@Composable
private fun Lamp(label: String, on: Boolean) {
    Box(
        Modifier.height(40.dp).width(if (label.length > 2) 80.dp else 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Gold else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = if (on) NavyDeep else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun KeyButton(label: String, modifier: Modifier, onDown: () -> Unit, onUp: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    down()
                    waitForUpOrCancellation()
                    pressed = false
                    up()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = if (label.length > 2) 26.sp else 48.sp,
            fontWeight = FontWeight.Bold,
            color = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

