package fr.hermorse.app.data

enum class Category(val title: String) {
    LETTRES("Lettres"),
    CHIFFRES("Chiffres"),
    PONCTUATION("Ponctuation"),
    PROSIGNES("Prosignes"),
    ACCENTS("Lettres accentuées")
}

data class MorseChar(val label: String, val code: String, val category: Category, val hint: String = "")

object Morse {

    val all: List<MorseChar> = buildList {
        val letters = listOf(
            "A" to ".-", "B" to "-...", "C" to "-.-.", "D" to "-..", "E" to ".", "F" to "..-.",
            "G" to "--.", "H" to "....", "I" to "..", "J" to ".---", "K" to "-.-", "L" to ".-..",
            "M" to "--", "N" to "-.", "O" to "---", "P" to ".--.", "Q" to "--.-", "R" to ".-.",
            "S" to "...", "T" to "-", "U" to "..-", "V" to "...-", "W" to ".--", "X" to "-..-",
            "Y" to "-.--", "Z" to "--.."
        )
        letters.forEach { add(MorseChar(it.first, it.second, Category.LETTRES)) }
        val digits = listOf(
            "0" to "-----", "1" to ".----", "2" to "..---", "3" to "...--", "4" to "....-",
            "5" to ".....", "6" to "-....", "7" to "--...", "8" to "---..", "9" to "----."
        )
        digits.forEach { add(MorseChar(it.first, it.second, Category.CHIFFRES)) }
        // Prosignes avant la ponctuation : ils sont prioritaires au décodage
        add(MorseChar("<AR>", ".-.-.", Category.PROSIGNES, "Fin de message"))
        add(MorseChar("<SK>", "...-.-", Category.PROSIGNES, "Fin de contact"))
        add(MorseChar("<KN>", "-.--.", Category.PROSIGNES, "À toi seulement"))
        add(MorseChar("<BT>", "-...-", Category.PROSIGNES, "Séparation (=)"))
        add(MorseChar("<AS>", ".-...", Category.PROSIGNES, "Attendez"))
        val punct = listOf(
            "." to ".-.-.-", "," to "--..--", "?" to "..--..", "/" to "-..-.", "=" to "-...-",
            "'" to ".----.", "!" to "-.-.--", "(" to "-.--.", ")" to "-.--.-", ":" to "---...",
            ";" to "-.-.-.", "+" to ".-.-.", "-" to "-....-", "\"" to ".-..-.", "@" to ".--.-."
        )
        punct.forEach { add(MorseChar(it.first, it.second, Category.PONCTUATION)) }
        add(MorseChar("É", "..-..", Category.ACCENTS))
        add(MorseChar("È", ".-..-", Category.ACCENTS))
        add(MorseChar("À", ".--.-", Category.ACCENTS))
        add(MorseChar("Ç", "-.-..", Category.ACCENTS))
    }

    private val decodeMap: Map<String, String> = LinkedHashMap<String, String>().apply {
        all.forEach { putIfAbsent(it.code, it.label) }
    }

    fun decode(pattern: String): String? = decodeMap[pattern]

    fun byLabel(label: String): MorseChar? = all.firstOrNull { it.label == label }

    /** Ordre de la méthode Koch (LCWO) */
    val kochOrder = listOf(
        "K", "M", "R", "S", "U", "A", "P", "T", "L", "O", "W", "I", ".", "N", "J", "E", "F", "0", "Y", ",",
        "V", "G", "5", "/", "Q", "9", "Z", "H", "3", "8", "B", "?", "4", "2", "7", "C", "1", "D", "6", "X"
    )

    fun kochRank(label: String): Int? = kochOrder.indexOf(label).takeIf { it >= 0 }?.plus(1)

    /** Lecture à voix haute : « ti-taah » */
    fun spoken(code: String): String = code.map { if (it == '.') "ti" else "taah" }.joinToString("-")
}
