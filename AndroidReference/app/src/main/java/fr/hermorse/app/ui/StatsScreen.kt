package fr.hermorse.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.hermorse.app.data.Days
import fr.hermorse.app.data.Morse
import fr.hermorse.app.data.Store

private val Orange = Color(0xFFFF9500)

fun readText(context: Context, uri: Uri): String? =
    try { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (_: Exception) { null }

fun writeText(context: Context, uri: Uri, text: String): Boolean =
    try { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }; true } catch (_: Exception) { false }

/** Boutons d'import d'un profil (.hermorse), réutilisés sur l'écran des profils. */
@Composable
fun ImportProfileButton(store: Store, onImported: (String?) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImported(readText(context, uri)?.let { store.importProfile(it) })
    }
    OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
        Text("Importer un profil (.hermorse)")
    }
}

@Composable
fun StatsScreen(model: AppModel) {
    val st = model.stats
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            model.flush()
            val name = model.profile ?: return@rememberLauncherForActivityResult
            message = if (writeText(context, uri, model.store.exportProfile(name))) "Profil exporté." else "Échec de l'export."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

        SectionCard("Résumé") {
            Row {
                Tile("Entraînement", formatDuration(st.trainingMs), Modifier.weight(1f))
                Tile("Jours d'affilée", Days.streak(st.days).toString(), Modifier.weight(1f))
                Tile("Séries", st.series.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Tile("Caractères manipulés", st.totalChars.toString(), Modifier.weight(1f))
                Tile("Meilleure vitesse", if (st.bestWpm > 0) "%.0f WPM".format(st.bestWpm) else "—", Modifier.weight(1f))
                val ratio = st.recentRatio.takeIf { it.isNotEmpty() }?.average()
                Tile("Trait / point", ratio?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Trait / point : rapport mesuré à la pioche, l'idéal est 3.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("Activité des 14 derniers jours (minutes)") { ActivityChart(st.days) }

        SectionCard("Réussite des 30 dernières séries") {
            val rx = st.history.filter { it.track == "rx" }.takeLast(30).map { it.acc }
            val tx = st.history.filter { it.track == "tx" }.takeLast(30).map { it.acc }
            if (rx.isEmpty() && tx.isEmpty()) {
                Text("Fais quelques séries pour voir ta courbe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AccuracyChart(rx, tx)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(MaterialTheme.colorScheme.primary, "Écoute")
                    Spacer(Modifier.width(16.dp))
                    Legend(Gold, "Manipulation")
                    Spacer(Modifier.width(16.dp))
                    Text("pointillés = 90 %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionCard("Réussite par caractère (écoute / manipulation)") {
            Morse.kochOrder.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { c -> CharTile(c, st.rxChars[c], st.txChars[c], Modifier.weight(1f)) }
                }
            }
        }

        SectionCard("Confusions fréquentes") {
            val top = st.confusions.entries.sortedByDescending { it.value }.take(8)
            if (top.isEmpty()) {
                Text("Aucune confusion enregistrée.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                top.forEach { (k, v) ->
                    val parts = k.split(">")
                    val a = parts.getOrElse(0) { "?" }
                    val b = parts.getOrElse(1) { "?" }
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$a pris pour $b", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val ca = Morse.byLabel(a)?.code ?: ""
                        val cb = Morse.byLabel(b)?.code ?: ""
                        Column(horizontalAlignment = Alignment.End) {
                            MorseSchema(ca, dot = 6.dp)
                            Spacer(Modifier.height(3.dp))
                            MorseSchema(cb, dot = 6.dp, color = BadRed)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("$v×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionCard("Partager mon profil") {
            Text(
                "Exporte ta progression, tes stats, tes trophées et tes réglages dans un fichier .hermorse, à envoyer ou à réimporter sur un autre téléphone (écran « Qui s'entraîne ? »).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { exportLauncher.launch("${model.profile}.hermorse") }, modifier = Modifier.fillMaxWidth()) {
                Text("Exporter mon profil")
            }
            Spacer(Modifier.height(6.dp))
            ImportProfileButton(model.store) { name ->
                message = if (name == null) "Fichier non reconnu." else "Profil « $name » importé. Choisis-le via « Changer de profil »."
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun pctColor(p: Pair<Int, Int>?): Color? {
    if (p == null || p.second == 0) return null
    val r = p.first.toDouble() / p.second
    return when {
        r >= 0.9 -> GoodGreen
        r >= 0.7 -> Orange
        else -> BadRed
    }
}

@Composable
private fun CharTile(c: String, rx: Pair<Int, Int>?, tx: Pair<Int, Int>?, modifier: Modifier) {
    val none = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(c, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            rx?.let { if (it.second > 0) "${it.first * 100 / it.second}%" else "—" } ?: "—",
            fontSize = 11.sp, color = pctColor(rx) ?: none, textAlign = TextAlign.Center
        )
        Text(
            tx?.let { if (it.second > 0) "${it.first * 100 / it.second}%" else "—" } ?: "—",
            fontSize = 11.sp, color = pctColor(tx) ?: none, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActivityChart(days: Map<String, Long>) {
    val dayMs = 24L * 3600 * 1000
    val now = System.currentTimeMillis()
    val keys = (13 downTo 0).map { Days.key(now - it * dayMs) }
    val mins = keys.map { (days[it] ?: 0L) / 60000f }
    val max = (mins.maxOrNull() ?: 0f).coerceAtLeast(5f)
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width / keys.size
        mins.forEachIndexed { i, m ->
            val h = if (m <= 0f) 4f else (m / max) * size.height
            drawRoundRect(
                color = if (m <= 0f) empty else bar,
                topLeft = Offset(i * w + w * 0.15f, size.height - h),
                size = Size(w * 0.7f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        keys.forEach { k ->
            Text(k.takeLast(2), Modifier.weight(1f), fontSize = 9.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        "Max : %.0f min".format(mins.maxOrNull() ?: 0f),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AccuracyChart(rx: List<Double>, tx: List<Double>) {
    val cRx = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val y90 = size.height * (1f - 0.9f)
        drawLine(
            grid, Offset(0f, y90), Offset(size.width, y90), strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
        fun series(values: List<Double>, color: Color) {
            if (values.isEmpty()) return
            val n = maxOf(values.size - 1, 1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / n
                val y = size.height * (1f - v.toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color, 5f, Offset(x, y))
            }
            drawPath(path, color, style = Stroke(width = 4f))
        }
        series(rx, cRx)
        series(tx, Gold)
    }
}
