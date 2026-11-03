package fr.hermorse.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.hermorse.app.data.Settings
import fr.hermorse.app.data.Stats
import fr.hermorse.app.data.Store
import fr.hermorse.app.data.Days
import fr.hermorse.app.data.Levels
import fr.hermorse.app.data.SeriesRec
import fr.hermorse.app.data.Trophies

/** État global de l'appli : profil actif, réglages, stats, trophée à annoncer. */
class AppModel(val store: Store) {
    var profile by mutableStateOf<String?>(null)
        private set
    var settings by mutableStateOf(Settings())
        private set
    var stats by mutableStateOf(Stats())
        private set
    var newTrophy by mutableStateOf<String?>(null)
    var levelMessage by mutableStateOf<String?>(null)

    /** Vitesses d'écoute selon le mode guidé ou personnalisé : (caractères, effective). */
    fun listenSpeeds(): Pair<Int, Int> =
        if (settings.guided) Levels.speedLadder[stats.rxSpeedIdx.coerceIn(0, Levels.speedLadder.lastIndex)]
        else settings.listenWpm to settings.farnsworthWpm.coerceAtMost(settings.listenWpm)

    fun select(name: String) {
        store.addProfile(name)
        store.setLast(name)
        settings = store.loadSettings(name)
        stats = store.loadStats(name)
        profile = name
    }

    /** Attribue directement un trophée (s'il n'est pas déjà obtenu). */
    fun award(id: String) {
        if (id !in stats.trophies) saveStats(stats, listOf(id))
    }

    /** Sauvegarde immédiate des stats (avant export). */
    fun flush() {
        profile?.let { store.saveStats(it, stats) }
    }

    fun logout() {
        flush()
        profile = null
    }

    fun updateSettings(s: Settings) {
        settings = s
        profile?.let { store.saveSettings(it, s) }
    }

    private fun saveStats(s: Stats, extra: List<String> = emptyList()) {
        var st = s
        val won = (Trophies.check(st) + extra.filter { it !in st.trophies }).distinct()
        if (won.isNotEmpty()) {
            val t = System.currentTimeMillis()
            st = st.copy(trophies = st.trophies + won.associateWith { t })
            newTrophy = won.first()
        }
        stats = st
        profile?.let { store.saveStats(it, st) }
    }

    /** Mode guidé : ralentir (-1) ou accélérer (+1) l'écoute d'un palier. */
    fun changeSpeed(delta: Int) {
        val i = (stats.rxSpeedIdx + delta).coerceIn(0, Levels.speedLadder.lastIndex)
        saveStats(stats.copy(rxSpeedIdx = i))
    }

    /** Temps passé dans les exercices (appelé régulièrement). */
    fun addTraining(ms: Long) {
        val k = Days.key()
        val days = (stats.days + (k to (stats.days[k] ?: 0L) + ms)).let { m ->
            if (m.size > 400) m.toSortedMap().entries.toList().takeLast(400).associate { it.key to it.value } else m
        }
        saveStats(stats.copy(trainingMs = stats.trainingMs + ms, days = days))
    }

    /** Rapport trait/point (sauvegardé avec le caractère suivant). */
    fun recordRatio(r: Double) {
        stats = stats.copy(recentRatio = (stats.recentRatio + r).takeLast(Trophies.WINDOW))
    }

    fun recordConfusion(expected: String, got: String) {
        val k = "$expected>$got"
        saveStats(stats.copy(confusions = stats.confusions + (k to (stats.confusions[k] ?: 0) + 1)))
    }

    private fun withHistory(st: Stats, track: String, level: Int, acc: Double, wpm: Int): Stats =
        st.copy(history = (st.history + SeriesRec(System.currentTimeMillis(), track, level, acc, wpm)).takeLast(200))

    fun recordChar(known: Boolean, wpm: Double, ratio: Double? = null) {
        val recent = (stats.recentWpm + wpm).takeLast(Trophies.WINDOW)
        val ratios = if (ratio != null) (stats.recentRatio + ratio).takeLast(Trophies.WINDOW) else stats.recentRatio
        val best = if (recent.size >= 10) maxOf(stats.bestWpm, recent.takeLast(10).average()) else stats.bestWpm
        saveStats(
            stats.copy(
                totalChars = stats.totalChars + 1,
                errors = if (known) stats.errors else stats.errors + 1,
                recentWpm = recent,
                recentRatio = ratios,
                bestWpm = best
            )
        )
    }

    /** Temps d'appui cumulé (sauvegardé avec le caractère suivant). */
    fun addKeyTime(ms: Long) {
        stats = stats.copy(keyTimeMs = stats.keyTimeMs + ms)
    }

    fun useExercise(id: String) {
        saveStats(stats.copy(usage = stats.usage + (id to (stats.usage[id] ?: 0) + 1)))
    }

    private data class Step(val level: Int, val streak: Int, val phaseUp: Boolean, val message: String?)

    private fun step(level: Int, streak: Int, acc: Double): Step {
        if (!settings.guided) return Step(level, streak, false, null)
        val pass = acc >= Levels.PASS
        if (!pass) return Step(level, 0, false, null)
        if (level < Levels.KOCH_DONE) {
            val nl = level + 1
            val msg = if (nl < Levels.KOCH_DONE)
                "Leçon Koch $level validée !\nNouveau caractère : ${Levels.kochChars(nl).last()}"
            else "Méthode Koch terminée !\nPassage en phase Opérateur : mots et abréviations."
            return Step(nl, 0, true, msg)
        }
        val ns = streak + 1
        if (ns >= Levels.STREAK_FOR_PHASE && level < Levels.MAX) {
            return Step(level + 1, 0, true, "Nouvelle phase : ${Levels.title(level + 1)}")
        }
        if (ns >= Levels.STREAK_FOR_PHASE) return Step(level, 0, true, null)
        return Step(level, ns, false, null)
    }

    private fun merge(m: Map<String, Pair<Int, Int>>, results: List<Pair<String, Boolean>>): Map<String, Pair<Int, Int>> {
        val out = m.toMutableMap()
        results.forEach { (c, ok) ->
            val (a, b) = out[c] ?: (0 to 0)
            out[c] = (a + if (ok) 1 else 0) to (b + 1)
        }
        return out
    }

    /** Fin d'une série d'écoute. results = (caractère attendu, juste ?). */
    fun finishListen(
        results: List<Pair<String, Boolean>>, charWpm: Int, effWpm: Int,
        subs: List<Pair<String, String>> = emptyList()
    ): Double {
        val acc = if (results.isEmpty()) 0.0 else results.count { it.second }.toDouble() / results.size
        val st = step(stats.rxLevel, stats.rxStreak, acc)
        var speedIdx = stats.rxSpeedIdx
        var msg = st.message
        val bump = when {
            !st.phaseUp -> false
            stats.rxLevel < Levels.KOCH_DONE -> st.level % 4 == 0 && speedIdx < Levels.KOCH_MAX_SPEED_IDX
            else -> speedIdx < Levels.speedLadder.lastIndex
        }
        if (bump) {
            speedIdx++
            val (c, e) = Levels.speedLadder[speedIdx]
            val v = "Nouvelle vitesse d'écoute : $c WPM, effectif $e WPM"
            msg = if (msg == null) "Palier de vitesse franchi !\n$v" else "$msg\n$v"
        }
        val conf = stats.confusions.toMutableMap()
        subs.forEach { (a, b) -> conf["$a>$b"] = (conf["$a>$b"] ?: 0) + 1 }
        val extra = if (acc >= 1.0 && charWpm >= 20 && effWpm >= 20) listOf("vingt_wpm") else emptyList()
        saveStats(
            withHistory(
                stats.copy(
                    rxLevel = st.level, rxStreak = st.streak, rxSpeedIdx = speedIdx,
                    rxChars = merge(stats.rxChars, results), series = stats.series + 1, confusions = conf
                ), "rx", stats.rxLevel, acc, effWpm
            ), extra
        )
        if (msg != null) levelMessage = msg
        return acc
    }

    /** Fin d'une série de manipulation. results = (caractère, juste du premier coup ?). */
    fun finishKey(results: List<Pair<String, Boolean>>, wpm: Int): Double {
        val acc = if (results.isEmpty()) 0.0 else results.count { it.second }.toDouble() / results.size
        val st = step(stats.txLevel, stats.txStreak, acc)
        saveStats(
            withHistory(
                stats.copy(
                    txLevel = st.level, txStreak = st.streak,
                    txChars = merge(stats.txChars, results), series = stats.series + 1
                ), "tx", stats.txLevel, acc, wpm
            )
        )
        st.message?.let { levelMessage = "Manipulation · $it" }
        return acc
    }
}
