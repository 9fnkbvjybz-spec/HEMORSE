@file:OptIn(ExperimentalMaterial3Api::class)

package fr.hermorse.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.hermorse.app.audio.ToneEngine
import fr.hermorse.app.data.Content
import fr.hermorse.app.data.Levels
import fr.hermorse.app.data.Morse
import fr.hermorse.app.key.KeyController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlignResult(val ok: BooleanArray, val subs: List<Pair<String, String>>)

/** Aligne la saisie sur le texte attendu (distance d'édition) : ok = caractère attendu bien reçu, subs = confusions. */
fun align(exp: String, typ: String): AlignResult {
    val n = exp.length
    val m = typ.length
    val d = Array(n + 1) { IntArray(m + 1) }
    for (i in 0..n) d[i][0] = i
    for (j in 0..m) d[0][j] = j
    for (i in 1..n) for (j in 1..m) {
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (exp[i - 1] == typ[j - 1]) 0 else 1)
    }
    val ok = BooleanArray(n)
    val subs = mutableListOf<Pair<String, String>>()
    var i = n
    var j = m
    while (i > 0 && j > 0) {
        when {
            exp[i - 1] == typ[j - 1] && d[i][j] == d[i - 1][j - 1] -> { ok[i - 1] = true; i--; j-- }
            d[i][j] == d[i - 1][j - 1] + 1 -> { subs += exp[i - 1].toString() to typ[j - 1].toString(); i--; j-- }
            d[i][j] == d[i - 1][j] + 1 -> i--
            else -> j--
        }
    }
    return AlignResult(ok, subs)
}

@Composable
private fun LevelHeader(guided: Boolean, level: Int, speed: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).padding(14.dp)
    ) {
        if (guided) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Levels.title(level), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(speed, color = MaterialTheme.colorScheme.primary)
            }
            Text(Levels.subtitle(level), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            if (level < Levels.KOCH_DONE) {
                Text(
                    "Nouveau : ${Levels.kochChars(level).last()} · objectif 90 %",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Objectif : 3 séries à 90 % de suite",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mode personnalisé", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(speed, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Caractères choisis dans les réglages. Ne compte pas pour la progression.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- Écouter → écrire ----------------

@Composable
fun ListenScreen(model: AppModel, tone: ToneEngine) {
    val scope = rememberCoroutineScope()
    val s = model.settings
    val (charWpm, effWpm) = model.listenSpeeds()
    var text by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("ready") } // ready | playing | ended | done
    var playIdx by remember { mutableIntStateOf(-1) }
    var job by remember { mutableStateOf<Job?>(null) }
    var okArr by remember { mutableStateOf(BooleanArray(0)) }
    var acc by remember { mutableDoubleStateOf(0.0) }
    val focus = remember { FocusRequester() }

    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    LaunchedEffect(state) {
        if (state == "playing") {
            delay(100)
            try { focus.requestFocus() } catch (_: Exception) { }
        }
    }

    fun start() {
        text = if (s.guided) Content.forLevel(model.stats.rxLevel, short = false) else Content.custom(s, short = false)
        typed = ""
        state = "playing"
        job?.cancel()
        job = scope.launch {
            tone.playText(text, charWpm, effWpm) { playIdx = it }
            if (state == "playing") state = "ended"
        }
    }

    fun correct() {
        job?.cancel()
        val exp = text.filter { it != ' ' }
        val typ = typed.uppercase().filter { !it.isWhitespace() }
        val r = align(exp, typ)
        okArr = r.ok
        acc = model.finishListen(exp.mapIndexed { i, c -> c.toString() to okArr[i] }, charWpm, effWpm, r.subs)
        state = "done"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        LevelHeader(s.guided, model.stats.rxLevel, "$charWpm / $effWpm WPM")
        Spacer(Modifier.height(12.dp))

        when (state) {
            "ready" -> {
                if (s.guided) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trop rapide ou trop lent ?", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { model.changeSpeed(-1) }, enabled = model.stats.rxSpeedIdx > 0) { Text("Plus lent") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { model.changeSpeed(1) },
                            enabled = model.stats.rxSpeedIdx < Levels.speedLadder.lastIndex
                        ) { Text("Plus vite") }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "L'appli joue une série en morse. Tape ce que tu entends au fil de l'écoute, sans t'arrêter sur une erreur. La correction s'affiche à la fin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            "playing", "ended" -> {
                val progress = if (text.isEmpty() || playIdx < 0) (if (state == "ended") 1f else 0f) else playIdx.toFloat() / text.length
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    if (state == "playing") "Écoute en cours…" else "Terminé. Finis ta saisie puis corrige.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            "done" -> {
                Text(
                    "Résultat : ${(acc * 100).toInt()} %",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = if (acc >= Levels.PASS) GoodGreen else BadRed
                )
                Spacer(Modifier.height(8.dp))
                Text("Attendu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var k = 0
                Text(
                    buildAnnotatedString {
                        text.forEach { c ->
                            if (c == ' ') append(' ') else {
                                val ok = okArr.getOrElse(k) { false }
                                k++
                                withStyle(SpanStyle(color = if (ok) GoodGreen else BadRed, fontWeight = FontWeight.Bold)) { append(c) }
                            }
                        }
                    },
                    fontFamily = FontFamily.Monospace, fontSize = 24.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("Ta saisie", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(typed.ifBlank { "(vide)" }, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state == "playing" || state == "ended") {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.uppercase() },
                label = { Text("Ce que tu entends") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Password
                ),
                minLines = 3,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(Modifier.height(12.dp))
        }

        when (state) {
            "ready" -> Button(onClick = { start() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Démarrer la série") }
            "playing" -> OutlinedButton(onClick = { correct() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Arrêter et corriger") }
            "ended" -> Button(onClick = { correct() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Corriger") }
            "done" -> Button(onClick = { start() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Série suivante") }
        }
    }
}

// ---------------- Lire → manipuler ----------------

@Composable
fun ReadKeyScreen(model: AppModel, tone: ToneEngine) {
    val scope = rememberCoroutineScope()
    val s = model.settings
    var text by remember { mutableStateOf("") }
    val status = remember { mutableStateListOf<Int>() } // 0 à faire, 1 juste, 2 erreur à refaire, 3 juste après erreur
    var idx by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf("ready") } // ready | running | done
    var correcting by remember { mutableStateOf(false) }
    var pattern by remember { mutableStateOf("") }
    var acc by remember { mutableDoubleStateOf(0.0) }

    fun skipSpaces(from: Int): Int {
        var i = from
        while (i < text.length && text[i] == ' ') i++
        return i
    }

    fun finish() {
        val res = text.indices.filter { text[it] != ' ' }.map { text[it].toString() to (status[it] == 1) }
        val w = if (s.keyMode == "straight") model.stats.recentWpm.takeLast(10).average().takeIf { !it.isNaN() }?.toInt() ?: 0 else s.keyerWpm
        acc = model.finishKey(res, w)
        state = "done"
    }

    val ctrl = remember {
        KeyController(scope, tone, object : KeyController.Listener {
            override fun onPattern(pattern: String) { setPat(pattern) }
            override fun onSpace() {}
            override fun onKeyTime(ms: Long) { model.addKeyTime(ms) }
            override fun onRatio(ratio: Double) { model.recordRatio(ratio) }
            override fun onChar(label: String, wpm: Double, known: Boolean) {
                if (state != "running" || correcting || idx >= text.length) return
                val expected = text[idx].toString()
                if (label == expected) {
                    status[idx] = if (status[idx] == 2) 3 else 1
                    model.recordChar(true, wpm)
                    idx = skipSpaces(idx + 1)
                    if (idx >= text.length) finish()
                } else {
                    status[idx] = 2
                    model.recordChar(false, wpm)
                    model.recordConfusion(expected, label)
                    correcting = true
                    val code = Morse.byLabel(expected)?.code ?: ""
                    val unit = (1200.0 / model.listenSpeeds().first).toLong()
                    scope.launch {
                        delay(350)
                        tone.playCode(code, unit) { }
                        delay(200)
                        correcting = false
                    }
                }
            }
            private fun setPat(p: String) { pattern = p }
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
            ctrl.tick(); delay(15)
        }
    }
    DisposableEffect(Unit) { onDispose { ctrl.release() } }

    fun start() {
        text = if (s.guided) Content.forLevel(model.stats.txLevel, short = true) else Content.custom(s, short = true)
        status.clear()
        repeat(text.length) { status.add(0) }
        idx = skipSpaces(0)
        ctrl.reset()
        correcting = false
        state = "running"
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LevelHeader(s.guided, model.stats.txLevel, if (s.keyMode == "straight") "pioche" else "${s.keyerWpm} WPM")
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp)
        ) {
            when (state) {
                "ready" -> Column {
                    Text(
                        "Un texte s'affiche : manipule-le caractère par caractère. Juste = vert. Faux = rouge : écoute la correction puis refais-le.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { start() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Démarrer la série") }
                }
                "running" -> {
                    val pending = MaterialTheme.colorScheme.onSurfaceVariant
                    val cur = MaterialTheme.colorScheme.primaryContainer
                    Text(
                        buildAnnotatedString {
                            text.forEachIndexed { i, c ->
                                val st = status.getOrElse(i) { 0 }
                                val color = when (st) {
                                    1, 3 -> GoodGreen
                                    2 -> BadRed
                                    else -> pending
                                }
                                withStyle(
                                    SpanStyle(
                                        color = color,
                                        background = if (i == idx) cur else Color.Transparent,
                                        fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal,
                                        textDecoration = if (i == idx) TextDecoration.Underline else TextDecoration.None
                                    )
                                ) { append(c) }
                            }
                        },
                        fontFamily = FontFamily.Monospace, fontSize = 32.sp, lineHeight = 44.sp
                    )
                }
                "done" -> Column {
                    Text(
                        "Résultat : ${(acc * 100).toInt()} % du premier coup",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = if (acc >= Levels.PASS) GoodGreen else BadRed
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { start() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Série suivante") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val expectedChar = if (state == "running" && idx < text.length) Morse.byLabel(text[idx].toString()) else null
        if (s.showHint && expectedChar != null) {
            // Bulle d'aide : schéma du caractère à manipuler, et ce que tu es en train de faire
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        expectedChar.label, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(16.dp))
                    MorseSchema(expectedChar.code, dot = 16.dp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.weight(1f))
                    if (correcting) Text("Correction…", color = BadRed, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.heightIn(min = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Toi : ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    MorseSchema(pattern, dot = 12.dp, color = Gold)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MorseSchema(pattern, dot = 12.dp, color = Gold) }
                if (correcting) Text("Correction…", color = BadRed)
            }
        }
        Spacer(Modifier.height(6.dp))
        KeyPad(s, ctrl)
    }
}

