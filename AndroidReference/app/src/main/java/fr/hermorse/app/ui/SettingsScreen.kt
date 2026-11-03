@file:OptIn(ExperimentalMaterial3Api::class)

package fr.hermorse.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.hermorse.app.BuildConfig
import fr.hermorse.app.data.Settings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(s: Settings, onChange: (Settings) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

        SectionCard("Manipulateur") {
            ChoiceRow(
                "Source", listOf("virtual" to "Virtuel (écran)", "external" to "Externe USB"),
                s.keySource
            ) { onChange(s.copy(keySource = it)) }
            if (s.keySource == "external") Note("Boîtier USB HerMorse branché en USB‑C (OTG). Pioche ou point sur la pointe du jack, trait sur la bague. Les touches à l'écran restent disponibles en secours.")
            Divider()
            ChoiceRow(
                "Type", listOf("straight" to "Pioche", "iambicA" to "Iambique A", "iambicB" to "Iambique B"),
                s.keyMode
            ) { onChange(s.copy(keyMode = it)) }
            if (s.keyMode != "straight") {
                Divider()
                SwitchRow("Inverser les palettes", "Trait à gauche, point à droite", s.swapPaddles) {
                    onChange(s.copy(swapPaddles = it))
                }
            }
            Divider()
            SliderRow("Hauteur des touches", s.keyHeight, 140..360, "${s.keyHeight} dp", step = 20) {
                onChange(s.copy(keyHeight = it))
            }
        }

        SectionCard("Sortie audio") {
            ChoiceRow(
                "Sortie",
                listOf("auto" to "Auto", "speaker" to "Haut-parleur", "wired" to "Casque filaire", "bt" to "Bluetooth"),
                s.output
            ) { onChange(s.copy(output = it)) }
            if (s.output == "bt") {
                Note("Le Bluetooth retarde le son de 0,1 à 0,3 s : parfait pour l'écoute, mais gênant pour manipuler. Préfère le haut-parleur ou un casque filaire pour la manipulation.")
            }
            Divider()
            SwitchRow("Sidetone", "Entendre le bip quand tu manipules", s.sidetone) { onChange(s.copy(sidetone = it)) }
            Divider()
            SliderRow("Tonalité", s.toneHz, 400..1000, "${s.toneHz} Hz", step = 50) { onChange(s.copy(toneHz = it)) }
            Divider()
            SliderRow("Volume", (s.volume * 100).roundToInt(), 0..100, "${(s.volume * 100).roundToInt()} %", step = 5) {
                onChange(s.copy(volume = it / 100f))
            }
            Divider()
            SwitchRow("Vibration", "Vibrer pendant l'appui", s.vibration) { onChange(s.copy(vibration = it)) }
        }

        SectionCard("Dictée (émission)") {
            SliderRow("Vitesse du manipulateur iambique", s.keyerWpm, 5..40, "${s.keyerWpm} WPM") {
                onChange(s.copy(keyerWpm = it))
            }
            Divider()
            SwitchRow("Décodage adaptatif", "La pioche s'adapte à ta vitesse", s.adaptiveDecode) {
                onChange(s.copy(adaptiveDecode = it))
            }
            if (!s.adaptiveDecode) {
                Divider()
                SliderRow("Vitesse de décodage (pioche)", s.fixedWpm, 5..40, "${s.fixedWpm} WPM") {
                    onChange(s.copy(fixedWpm = it))
                }
            }
        }

        SectionCard("Écoute (réception)") {
            SliderRow("Vitesse des caractères", s.listenWpm, 10..40, "${s.listenWpm} WPM") {
                onChange(s.copy(listenWpm = it, farnsworthWpm = minOf(s.farnsworthWpm, it)))
            }
            Divider()
            SliderRow("Vitesse effective (Farnsworth)", s.farnsworthWpm, 5..s.listenWpm, "${s.farnsworthWpm} WPM") {
                onChange(s.copy(farnsworthWpm = it))
            }
            Note("En mode guidé, la vitesse d'écoute suit les paliers de progression (départ à 12 WPM, effectif 5 WPM). Ces réglages servent au mode personnalisé et aux fiches.")
        }

        SectionCard("Aide") {
            SwitchRow(
                "Bulle d'aide en « Lire → manipuler »",
                "Affiche le schéma du caractère à manipuler au-dessus du manipulateur",
                s.showHint
            ) { onChange(s.copy(showHint = it)) }
        }

        SectionCard("Caractères des exercices") {
            SwitchRow("Lettres", null, s.useLetters) { onChange(s.copy(useLetters = it)) }
            SwitchRow("Chiffres", null, s.useDigits) { onChange(s.copy(useDigits = it)) }
            SwitchRow("Ponctuation", null, s.usePunct) { onChange(s.copy(usePunct = it)) }
            SwitchRow("Prosignes (AR, SK, KN…)", null, s.useProsigns) { onChange(s.copy(useProsigns = it)) }
            SwitchRow("Lettres accentuées", null, s.useAccents) { onChange(s.copy(useAccents = it)) }
        }

        SectionCard("Progression") {
            SwitchRow(
                "Mode guidé (méthode Koch)",
                "Le niveau décide des réglages. Désactive pour tout personnaliser.",
                s.guided
            ) { onChange(s.copy(guided = it)) }
        }

        SectionCard("Live") {
            OutlinedTextField(
                value = s.callsign,
                onValueChange = { onChange(s.copy(callsign = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '/' }.take(12))) },
                label = { Text("Indicatif (ex. F4ABC)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard("Apparence") {
            ChoiceRow(
                "Thème", listOf("system" to "Système", "light" to "Clair", "dark" to "Sombre"),
                s.theme
            ) { onChange(s.copy(theme = it)) }
        }

        SectionCard("À propos") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version", Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (key, label) ->
                FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun SliderRow(title: String, value: Int, range: IntRange, valueLabel: String, step: Int = 1, onChange: (Int) -> Unit) {
    val first = range.first
    val last = maxOf(range.last, first + step)
    val positions = (last - first) / step
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(first, last).toFloat(),
            onValueChange = { v ->
                val snapped = first + ((v - first) / step).roundToInt() * step
                if (snapped != value) onChange(snapped.coerceIn(first, last))
            },
            valueRange = first.toFloat()..last.toFloat(),
            steps = (positions - 1).coerceAtLeast(0)
        )
    }
}
