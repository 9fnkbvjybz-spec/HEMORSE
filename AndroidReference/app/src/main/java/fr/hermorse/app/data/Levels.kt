package fr.hermorse.app.data

import kotlin.random.Random

/**
 * Niveaux : 1 à 39 = leçons Koch (la leçon n utilise les n+1 premiers caractères Koch),
 * 40 = Opérateur (mots), 41 = Trafic (indicatifs), 42 = QSO (phrases).
 */
object Levels {
    const val KOCH_DONE = 40
    const val MAX = 42
    const val PASS = 0.9
    const val STREAK_FOR_PHASE = 3

    /**
     * Paliers de vitesse à l'écoute en mode guidé : (vitesse des caractères, vitesse effective Farnsworth).
     * On démarre lentement, puis on monte d'un palier toutes les 4 leçons Koch (jusqu'à 20/15),
     * puis à chaque phase validée après Koch.
     */
    val speedLadder = listOf(12 to 5, 15 to 6, 15 to 8, 18 to 10, 20 to 12, 20 to 15, 20 to 18, 20 to 20, 25 to 25, 30 to 30)
    const val KOCH_MAX_SPEED_IDX = 5

    fun kochChars(level: Int): List<String> = Morse.kochOrder.take((level + 1).coerceIn(2, Morse.kochOrder.size))

    fun newChar(level: Int): String? = if (level in 1 until KOCH_DONE) Morse.kochOrder[level] else null

    fun title(level: Int): String = when {
        level < KOCH_DONE -> "Leçon Koch $level"
        level == 40 -> "Opérateur · mots"
        level == 41 -> "Trafic · indicatifs"
        else -> "QSO · phrases"
    }

    fun subtitle(level: Int): String = when {
        level < KOCH_DONE -> kochChars(level).joinToString(" ")
        level == 40 -> "Mots courants et abréviations CW"
        level == 41 -> "Indicatifs d'appel"
        else -> "Phrases de QSO structurées"
    }

    fun phaseName(level: Int): String = when {
        level < KOCH_DONE -> "Apprenti"
        level == 40 -> "Opérateur"
        level == 41 -> "Trafic"
        else -> "QSO"
    }
}

/** Générateur de textes d'exercice. */
object Content {
    private val rnd = Random(System.nanoTime())

    private val words = listOf(
        "LE", "DE", "UN", "ET", "EST", "QUI", "PAS", "SUR", "AVEC", "MAIS", "NOUS", "VOUS", "BIEN", "TEMPS",
        "RADIO", "ANTENNE", "SOLEIL", "MAISON", "MER", "VENT", "AMI", "BONJOUR", "MERCI", "SALUT", "JOUR",
        "CQ", "TNX", "TU", "73", "88", "QTH", "QRZ", "QSL", "QSO", "QRM", "QSB", "QRP", "RST", "UR", "FB",
        "OM", "YL", "ES", "HR", "WX", "PWR", "ANT", "RIG", "NAME", "GM", "GA", "GE", "BK", "AGN", "PSE", "599"
    )
    private val prefixes = listOf("F", "F", "F", "ON", "DL", "EA", "G", "I", "HB", "OE", "PA", "LX")
    private val names = listOf("JEAN", "PAUL", "MARC", "LUC", "ANNE", "MARIE", "PIERRE", "ERIC", "ALAIN", "SOPHIE")
    private val cities = listOf("PARIS", "LYON", "NANTES", "LILLE", "BREST", "NICE", "DIJON", "METZ", "TOURS", "REIMS")
    private val phrases = listOf(
        "CQ CQ CQ DE {A} {A} K",
        "{A} DE {B} GM TNX FER CALL UR RST 599 599",
        "NAME {N} QTH {Q} HW CPY? {A} DE {B} K",
        "R R TNX RPT UR RST 579 NAME {N} QTH {Q}",
        "RIG 100W ANT DIPOLE WX SUNNY {A} DE {B} K",
        "TNX FER QSO 73 ES GL {A} DE {B}"
    )

    fun callsign(): String {
        val letters = ('A'..'Z').toList()
        val n = if (rnd.nextBoolean()) 2 else 3
        return prefixes.random(rnd) + (1..9).random(rnd) + (1..n).map { letters.random(rnd) }.joinToString("")
    }

    /** Groupes de 5 caractères tirés parmi [chars], le dernier caractère appris plus souvent (30 %). */
    fun groups(chars: List<String>, count: Int, favor: String?): String =
        (1..count).joinToString(" ") {
            (1..5).joinToString("") {
                if (favor != null && rnd.nextDouble() < 0.3) favor else chars.random(rnd)
            }
        }

    /** Texte d'une série. [short] = série de manipulation (plus courte). */
    fun forLevel(level: Int, short: Boolean): String = when {
        level < Levels.KOCH_DONE -> groups(Levels.kochChars(level), if (short) 4 else 5, Levels.kochChars(level).last())
        level == 40 -> (1..(if (short) 5 else 8)).joinToString(" ") { words.random(rnd) }
        level == 41 -> (1..(if (short) 4 else 6)).joinToString(" ") { callsign() }
        else -> (1..(if (short) 1 else 2)).joinToString(" ") { phrase() }
    }

    fun phrase(): String = phrases.random(rnd)
        .replace("{A}", callsign()).replace("{B}", callsign())
        .replace("{N}", names.random(rnd)).replace("{Q}", cities.random(rnd))

    /** Mode personnalisé : groupes de 5 parmi les caractères cochés dans les réglages. */
    fun custom(s: Settings, short: Boolean): String {
        val pool = Morse.all.filter {
            when (it.category) {
                Category.LETTRES -> s.useLetters
                Category.CHIFFRES -> s.useDigits
                Category.PONCTUATION -> s.usePunct
                Category.ACCENTS -> s.useAccents
                Category.PROSIGNES -> false
            }
        }.map { it.label }.ifEmpty { Morse.kochOrder.take(2) }
        return groups(pool, if (short) 4 else 5, null)
    }
}
