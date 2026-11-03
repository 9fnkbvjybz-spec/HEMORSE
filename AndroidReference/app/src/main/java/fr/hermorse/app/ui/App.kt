@file:OptIn(ExperimentalMaterial3Api::class)

package fr.hermorse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.hermorse.app.BuildConfig
import fr.hermorse.app.R
import fr.hermorse.app.audio.ToneEngine
import fr.hermorse.app.data.Store
import fr.hermorse.app.data.Days
import fr.hermorse.app.data.Levels
import fr.hermorse.app.data.Tier
import fr.hermorse.app.data.Trophies
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab(val title: String, val icon: ImageVector) {
    ACCUEIL("Accueil", Icons.Filled.Home),
    APPRENDRE("Apprendre", Icons.Filled.School),
    LIVE("Live", Icons.Filled.Groups),
    QSO("QSO", Icons.Filled.Radio)
}

enum class Overlay(val title: String) { NONE(""), SETTINGS("Réglages"), STATS("Statistiques"), TROPHIES("Trophées"), ABOUT("À propos") }

@Composable
fun HerMorseApp(store: Store, tone: ToneEngine) {
    val model = remember { AppModel(store) }
    val s = model.settings
    val dark = when (s.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(s) {
        tone.freq = s.toneHz.toDouble()
        tone.volume = s.volume
        tone.sidetone = s.sidetone
        tone.vibrate = s.vibration
        tone.setOutput(s.output)
    }
    HerMorseTheme(dark) {
        if (model.profile == null) {
            ProfileScreen(store) { model.select(it) }
        } else {
            MainScreen(model, tone)
        }
        val lvl = model.levelMessage
        if (lvl != null && model.newTrophy == null) {
            AlertDialog(
                onDismissRequest = { model.levelMessage = null },
                confirmButton = { TextButton(onClick = { model.levelMessage = null }) { Text("En avant !") } },
                title = { Text("Niveau supérieur !") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("📡", fontSize = 48.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(lvl, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        }
        model.newTrophy?.let { id ->
            val t = Trophies.byId(id)
            AlertDialog(
                onDismissRequest = { model.newTrophy = null },
                confirmButton = { TextButton(onClick = { model.newTrophy = null }) { Text("Génial !") } },
                title = { Text("Trophée débloqué !") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(t.tier.emoji, fontSize = 56.sp)
                        Text(t.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(t.description, textAlign = TextAlign.Center)
                    }
                }
            )
        }
    }
}

// ---------------- Profils ----------------

@Composable
fun AppLogo(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.23f).dp))) {
        Image(painterResource(R.mipmap.ic_launcher_background), null, Modifier.fillMaxSize())
        Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
    }
}

@Composable
fun ProfileScreen(store: Store, onSelect: (String) -> Unit) {
    var profiles by remember { mutableStateOf(store.profiles()) }
    var newName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<String?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            AppLogo(112)
            Spacer(Modifier.height(16.dp))
            Text("HerMorse", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Entraînement au morse (CW)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Text("Qui s'entraîne ?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            profiles.forEach { p ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(p) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(p, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { toDelete = p }) {
                            Icon(Icons.Filled.Delete, "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(24) },
                label = { Text("Nouveau profil (ton nom)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { val n = newName.trim(); if (n.isNotEmpty()) onSelect(n) },
                enabled = newName.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Créer et commencer") }
            Spacer(Modifier.height(16.dp))
            ImportProfileButton(store) { name ->
                profiles = store.profiles()
                importMsg = if (name == null) "Fichier non reconnu." else "Profil « $name » importé."
            }
            importMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer $p ?") },
            text = { Text("Les réglages, la progression et les trophées de ce profil seront effacés.") },
            confirmButton = {
                TextButton(onClick = { store.deleteProfile(p); profiles = store.profiles(); toDelete = null }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }
}

// ---------------- Structure principale ----------------

@Composable
fun MainScreen(model: AppModel, tone: ToneEngine) {
    var tab by remember { mutableStateOf(Tab.ACCUEIL) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var learnMode by remember { mutableStateOf(LearnMode.FICHES) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = overlay != Overlay.NONE || drawerState.isOpen) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else overlay = Overlay.NONE
    }

    fun openOverlay(o: Overlay) {
        overlay = o
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(48)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("HerMorse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            model.profile + (model.settings.callsign.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                DrawerItem("Réglages", Icons.Filled.Settings) { openOverlay(Overlay.SETTINGS) }
                DrawerItem("Statistiques", Icons.Filled.BarChart) { openOverlay(Overlay.STATS) }
                DrawerItem("Trophées", Icons.Filled.EmojiEvents) { openOverlay(Overlay.TROPHIES) }
                DrawerItem("Changer de profil", Icons.Filled.Person) {
                    scope.launch { drawerState.close() }
                    overlay = Overlay.NONE
                    model.logout()
                }
                DrawerItem("À propos", Icons.Filled.Info) { openOverlay(Overlay.ABOUT) }
                Spacer(Modifier.weight(1f))
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (overlay != Overlay.NONE) overlay.title else tab.title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "Menu")
                        }
                    },
                    actions = {
                        if (overlay != Overlay.NONE) {
                            IconButton(onClick = { overlay = Overlay.NONE }) { Icon(Icons.Filled.Close, "Fermer") }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = overlay == Overlay.NONE && tab == t,
                            onClick = { tab = t; overlay = Overlay.NONE },
                            icon = { Icon(t.icon, t.title) },
                            label = { Text(t.title) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (overlay) {
                    Overlay.SETTINGS -> SettingsScreen(model.settings) { model.updateSettings(it) }
                    Overlay.STATS -> StatsScreen(model)
                    Overlay.TROPHIES -> TrophiesScreen(model)
                    Overlay.ABOUT -> AboutScreen()
                    Overlay.NONE -> when (tab) {
                        Tab.ACCUEIL -> HomeScreen(model) { mode ->
                            learnMode = mode
                            tab = Tab.APPRENDRE
                        }
                        Tab.APPRENDRE -> LearnScreen(model, tone, learnMode) { learnMode = it }
                        Tab.LIVE -> ComingSoon(
                            Icons.Filled.Groups, "Live", "v0.5",
                            "Connecte plusieurs téléphones dans la même pièce et faites un QSO chacun votre tour, identifiés par vos indicatifs."
                        )
                        Tab.QSO -> QsoScreen(model)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

// ---------------- Composants communs ----------------

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun ComingSoon(icon: ImageVector, title: String, version: String, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Arrive en $version", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- Accueil ----------------

private val exerciseNames = mapOf(
    "fiches" to ("Fiches des caractères" to LearnMode.FICHES),
    "libre" to ("Saisie libre" to LearnMode.LIBRE),
    "ecoute" to ("Écouter → écrire" to LearnMode.ECOUTE),
    "lecture" to ("Lire → manipuler" to LearnMode.LECTURE)
)

@Composable
fun HomeScreen(model: AppModel, goLearn: (LearnMode) -> Unit) {
    val st = model.stats
    val s = model.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "Bonjour ${model.profile}" + (s.callsign.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
        )
        Text(
            if (s.guided) "Mode guidé · méthode Koch" else "Mode personnalisé",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        SectionCard("Niveaux") {
            val (cw, ew) = model.listenSpeeds()
            LevelRow("Réception", Levels.title(st.rxLevel))
            LevelDetail(st.rxLevel, st.rxStreak, "Écoute à $cw WPM, effectif $ew WPM")
            Spacer(Modifier.height(12.dp))
            LevelRow("Manipulation", Levels.title(st.txLevel))
            LevelDetail(st.txLevel, st.txStreak, null)
        }

        SectionCard("Dernières stats") {
            val avg = if (st.recentWpm.isEmpty()) "—" else "%.1f WPM".format(st.recentWpm.takeLast(10).average())
            Row {
                StatTile("Caractères", st.totalChars.toString(), Modifier.weight(1f))
                StatTile("Vitesse récente", avg, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row {
                StatTile("Temps manipulé", formatDuration(st.keyTimeMs), Modifier.weight(1f))
                StatTile("Erreurs", st.errors.toString(), Modifier.weight(1f))
            }
        }

        SectionCard("Derniers trophées") {
            Text(
                "Jours d'affilée : ${Days.streak(st.days)} · entraînement : ${formatDuration(st.trainingMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val last = st.trophies.entries.sortedByDescending { it.value }.take(3)
            if (last.isEmpty()) {
                Text("Aucun trophée pour l'instant. Manipule ton premier caractère !")
            } else {
                last.forEach { (id, _) ->
                    val t = Trophies.byId(id)
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.tier.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(t.name, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        SectionCard("Mes exercices") {
            val used = st.usage.entries.sortedByDescending { it.value }.map { it.key }
            val list = (used + listOf("ecoute", "lecture", "libre", "fiches")).distinct().filter { it in exerciseNames }
            list.forEach { id ->
                val (name, mode) = exerciseNames.getValue(id)
                OutlinedButton(onClick = { goLearn(mode) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(name, modifier = Modifier.weight(1f))
                    Text("${st.usage[id] ?: 0}×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LevelRow(title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LevelDetail(level: Int, streak: Int, extra: String?) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    if (level < Levels.KOCH_DONE) {
        Text(
            "${Levels.phaseName(level)} · ${level + 1} caractères sur 40 · dernier appris : ${Levels.kochChars(level).last()}",
            style = MaterialTheme.typography.bodySmall, color = sub
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { (level + 1) / 40f }, modifier = Modifier.fillMaxWidth())
    } else {
        Text(
            "${Levels.phaseName(level)} · séries réussies d'affilée : $streak / ${Levels.STREAK_FOR_PHASE}",
            style = MaterialTheme.typography.bodySmall, color = sub
        )
    }
    if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = sub)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatDuration(ms: Long): String {
    val totalS = ms / 1000
    val h = totalS / 3600
    val m = (totalS % 3600) / 60
    val sec = totalS % 60
    return when {
        h > 0 -> "${h} h ${m} min"
        m > 0 -> "${m} min ${sec} s"
        else -> "${sec} s"
    }
}

// ---------------- Trophées ----------------

@Composable
fun TrophiesScreen(model: AppModel) {
    val got = model.stats.trophies
    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "${got.size} / ${Trophies.all.size} trophées obtenus",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
        )
        Tier.entries.forEach { tier ->
            val list = Trophies.all.filter { it.tier == tier }
            SectionCard("${tier.emoji} ${tier.title}") {
                list.forEachIndexed { i, t ->
                    val date = got[t.id]
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (date != null) tier.emoji else "🔒", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.SemiBold)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            when {
                                date != null -> Text("Obtenu le ${df.format(Date(date))}", style = MaterialTheme.typography.bodySmall, color = GoodGreen)
                                t.availableFrom.isNotBlank() -> Text("Disponible en ${t.availableFrom}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (i < list.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

// ---------------- À propos ----------------

@Composable
fun AboutScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppLogo(96)
        Spacer(Modifier.height(12.dp))
        Text("HerMorse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        SectionCard {
            Text("Hermès, messager des dieux, et Morse : une appli pour apprendre le CW avec la méthode Koch.")
            Spacer(Modifier.height(8.dp))
            Text("v0.1 : profils, réglages, manipulateur virtuel (pioche et iambique A/B), sidetone, décodeur, fiches des caractères, saisie libre et premiers trophées.")
            Spacer(Modifier.height(8.dp))
            Text("v0.4 : onglet QSO (traduction en direct d'un vrai poste par le micro, spectre, suivi de tonalité), manipulateur externe via le boîtier USB HerMorse.")
            Spacer(Modifier.height(8.dp))
            Text("v0.3 : statistiques détaillées (activité, courbes, réussite par caractère, confusions, rapport trait/point), trophées 7 jours, 10 heures et platine, export et import du profil, départ plus lent, bulle d'aide.")
            Spacer(Modifier.height(8.dp))
            Text("v0.2 : écouter → écrire, lire → manipuler, progression Koch automatique sur deux pistes (réception et manipulation), phases mots, indicatifs et QSO, paliers de vitesse.")
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "© 2026 HerMorse · Brevet déposé · by ADT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
