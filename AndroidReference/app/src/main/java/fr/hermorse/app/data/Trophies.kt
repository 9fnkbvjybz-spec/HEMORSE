package fr.hermorse.app.data

enum class Tier(val title: String, val emoji: String) {
    BRONZE("Bronze", "🥉"),
    ARGENT("Argent", "🥈"),
    OR("Or", "🥇"),
    PLATINE("Platine", "🏆"),
    SECRET("Easter egg", "🥚")
}

data class Trophy(
    val id: String,
    val name: String,
    val description: String,
    val tier: Tier,
    val availableFrom: String = ""
)

object Trophies {
    const val WINDOW = 50
    const val SLOW_WPM = 6.0
    const val FAST_WPM = 25.0
    const val ERRORS_LIMIT = 50

    val all = listOf(
        Trophy("premier_bip", "Premier bip", "Manipuler son tout premier caractère", Tier.BRONZE),
        Trophy("km", "K et M maîtrisés", "Valider la leçon Koch n°1 (écoute ou manipulation)", Tier.BRONZE),
        Trophy("serie7", "7 jours d'affilée", "S'entraîner 7 jours de suite", Tier.BRONZE),
        Trophy("alphabet", "Alphabet complet", "Reconnaître à l'écoute chacune des 26 lettres", Tier.ARGENT),
        Trophy("dix_heures", "10 heures d'entraînement", "Cumuler 10 heures d'entraînement", Tier.ARGENT),
        Trophy("live", "Premier QSO en Live", "Faire un QSO avec un autre utilisateur", Tier.ARGENT, "v0.5"),
        Trophy("koch", "Koch terminé", "Valider les 40 caractères Koch à l'écoute", Tier.OR),
        Trophy("vingt_wpm", "20 WPM sans faute", "Une série d'écoute à 20 WPM effectifs sans aucune erreur", Tier.OR),
        Trophy("qso_reel", "Premier QSO réel traduit", "Traduire 100 caractères d'un vrai QSO dans l'onglet QSO", Tier.OR),
        Trophy("platine", "Platine", "Obtenir tous les trophées", Tier.PLATINE),
        Trophy("dyslexique", "Dyslexique des doigts", "Plus de $ERRORS_LIMIT erreurs au total", Tier.SECRET),
        Trophy("tortue", "La tortue du CW", "Moins de ${SLOW_WPM.toInt()} WPM de moyenne sur $WINDOW caractères d'affilée", Tier.SECRET),
        Trophy("lievre", "Le lièvre du CW", "Au moins ${FAST_WPM.toInt()} WPM de moyenne sur $WINDOW caractères d'affilée", Tier.SECRET)
    )

    fun byId(id: String) = all.first { it.id == id }

    /** Renvoie les nouveaux trophées gagnés avec ces stats. */
    fun check(s: Stats): List<String> {
        val earned = mutableListOf<String>()
        fun win(id: String, cond: Boolean) {
            if (cond && id !in s.trophies) earned += id
        }
        win("premier_bip", s.totalChars >= 1)
        win("km", s.rxLevel >= 2 || s.txLevel >= 2)
        win("alphabet", ('A'..'Z').all { (s.rxChars[it.toString()]?.first ?: 0) > 0 })
        win("koch", s.rxLevel >= Levels.KOCH_DONE)
        win("dyslexique", s.errors > ERRORS_LIMIT)
        win("serie7", Days.streak(s.days) >= 7)
        win("dix_heures", s.trainingMs >= 10L * 3600 * 1000)
        if (s.recentWpm.size >= WINDOW) {
            val avg = s.recentWpm.takeLast(WINDOW).average()
            win("tortue", avg < SLOW_WPM)
            win("lievre", avg >= FAST_WPM)
        }
        val others = all.filter { it.id != "platine" }.map { it.id }
        if (others.all { it in s.trophies || it in earned }) win("platine", true)
        return earned
    }
}

object Days {
    private val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    fun key(t: Long = System.currentTimeMillis()): String = fmt.format(java.util.Date(t))

    /** Nombre de jours d'affilée avec entraînement, jusqu'à aujourd'hui (ou hier). */
    fun streak(days: Map<String, Long>): Int {
        val dayMs = 24L * 3600 * 1000
        var t = System.currentTimeMillis()
        if ((days[key(t)] ?: 0) <= 0) t -= dayMs
        var n = 0
        while ((days[key(t)] ?: 0) > 0) { n++; t -= dayMs }
        return n
    }
}
