package fr.hermorse.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val keySource: String = "virtual",      // virtual | external
    val keyMode: String = "straight",       // straight | iambicA | iambicB
    val swapPaddles: Boolean = false,
    val keyHeight: Int = 220,               // dp
    val output: String = "auto",            // auto | speaker | wired | bt
    val sidetone: Boolean = true,
    val toneHz: Int = 600,
    val volume: Float = 0.6f,
    val vibration: Boolean = false,
    val keyerWpm: Int = 12,
    val adaptiveDecode: Boolean = true,
    val fixedWpm: Int = 15,
    val listenWpm: Int = 15,
    val farnsworthWpm: Int = 8,
    val useLetters: Boolean = true,
    val useDigits: Boolean = true,
    val usePunct: Boolean = false,
    val useProsigns: Boolean = false,
    val useAccents: Boolean = false,
    val guided: Boolean = true,
    val showHint: Boolean = true,
    val qsoSensitivity: Float = 0.5f,
    val qsoAfc: Boolean = true,
    val qsoFreq: Int = 600,
    val callsign: String = "",
    val theme: String = "system"            // system | light | dark
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("keySource", keySource); put("keyMode", keyMode); put("swapPaddles", swapPaddles)
        put("keyHeight", keyHeight); put("output", output); put("sidetone", sidetone)
        put("toneHz", toneHz); put("volume", volume.toDouble()); put("vibration", vibration)
        put("keyerWpm", keyerWpm); put("adaptiveDecode", adaptiveDecode); put("fixedWpm", fixedWpm)
        put("listenWpm", listenWpm); put("farnsworthWpm", farnsworthWpm)
        put("useLetters", useLetters); put("useDigits", useDigits); put("usePunct", usePunct)
        put("useProsigns", useProsigns); put("useAccents", useAccents); put("guided", guided); put("showHint", showHint)
        put("qsoSensitivity", qsoSensitivity.toDouble()); put("qsoAfc", qsoAfc); put("qsoFreq", qsoFreq)
        put("callsign", callsign); put("theme", theme)
    }

    companion object {
        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            return Settings(
                keySource = o.optString("keySource", d.keySource),
                keyMode = o.optString("keyMode", d.keyMode),
                swapPaddles = o.optBoolean("swapPaddles", d.swapPaddles),
                keyHeight = o.optInt("keyHeight", d.keyHeight),
                output = o.optString("output", d.output),
                sidetone = o.optBoolean("sidetone", d.sidetone),
                toneHz = o.optInt("toneHz", d.toneHz),
                volume = o.optDouble("volume", d.volume.toDouble()).toFloat(),
                vibration = o.optBoolean("vibration", d.vibration),
                keyerWpm = o.optInt("keyerWpm", d.keyerWpm),
                adaptiveDecode = o.optBoolean("adaptiveDecode", d.adaptiveDecode),
                fixedWpm = o.optInt("fixedWpm", d.fixedWpm),
                listenWpm = o.optInt("listenWpm", d.listenWpm),
                farnsworthWpm = o.optInt("farnsworthWpm", d.farnsworthWpm),
                useLetters = o.optBoolean("useLetters", d.useLetters),
                useDigits = o.optBoolean("useDigits", d.useDigits),
                usePunct = o.optBoolean("usePunct", d.usePunct),
                useProsigns = o.optBoolean("useProsigns", d.useProsigns),
                useAccents = o.optBoolean("useAccents", d.useAccents),
                guided = o.optBoolean("guided", d.guided),
                showHint = o.optBoolean("showHint", d.showHint),
                qsoSensitivity = o.optDouble("qsoSensitivity", d.qsoSensitivity.toDouble()).toFloat(),
                qsoAfc = o.optBoolean("qsoAfc", d.qsoAfc),
                qsoFreq = o.optInt("qsoFreq", d.qsoFreq),
                callsign = o.optString("callsign", d.callsign),
                theme = o.optString("theme", d.theme)
            )
        }
    }
}

data class Stats(
    val totalChars: Int = 0,
    val errors: Int = 0,
    val keyTimeMs: Long = 0,
    val recentWpm: List<Double> = emptyList(),   // vitesse des 50 derniers caractères
    val trophies: Map<String, Long> = emptyMap(), // id -> date d'obtention
    val usage: Map<String, Int> = emptyMap(),     // exercice -> nombre d'utilisations
    val bestWpm: Double = 0.0,
    val rxLevel: Int = 1,                         // 1..39 leçons Koch, 40+ phases
    val txLevel: Int = 1,
    val rxStreak: Int = 0,
    val txStreak: Int = 0,
    val rxSpeedIdx: Int = 0,                      // index dans Levels.speedLadder
    val rxChars: Map<String, Pair<Int, Int>> = emptyMap(), // caractère -> (justes, total) à l'écoute
    val txChars: Map<String, Pair<Int, Int>> = emptyMap(), // caractère -> (justes du 1er coup, total) en manipulation
    val series: Int = 0,
    val trainingMs: Long = 0,                       // temps passé dans les exercices
    val days: Map<String, Long> = emptyMap(),       // "aaaa-mm-jj" -> ms d'entraînement
    val history: List<SeriesRec> = emptyList(),     // dernières séries
    val confusions: Map<String, Int> = emptyMap(),  // "attendu>reçu" -> nombre
    val recentRatio: List<Double> = emptyList()     // rapport trait/point (pioche)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("totalChars", totalChars); put("errors", errors); put("keyTimeMs", keyTimeMs)
        put("bestWpm", bestWpm)
        put("recentWpm", JSONArray().apply { recentWpm.forEach { put(it) } })
        put("trophies", JSONObject().apply { trophies.forEach { (k, v) -> put(k, v) } })
        put("usage", JSONObject().apply { usage.forEach { (k, v) -> put(k, v) } })
        put("rxLevel", rxLevel); put("txLevel", txLevel); put("rxStreak", rxStreak); put("txStreak", txStreak)
        put("rxSpeed3", rxSpeedIdx); put("series", series)
        put("rxChars", charsToJson(rxChars)); put("txChars", charsToJson(txChars))
        put("trainingMs", trainingMs)
        put("days", JSONObject().apply { days.forEach { (k, v) -> put(k, v) } })
        put("history", JSONArray().apply { history.forEach { put(it.toJson()) } })
        put("confusions", JSONObject().apply { confusions.forEach { (k, v) -> put(k, v) } })
        put("recentRatio", JSONArray().apply { recentRatio.forEach { put(it) } })
    }

    private fun charsToJson(m: Map<String, Pair<Int, Int>>) = JSONObject().apply {
        m.forEach { (k, v) -> put(k, JSONArray().put(v.first).put(v.second)) }
    }

    companion object {
        fun fromJson(o: JSONObject): Stats {
            val rw = o.optJSONArray("recentWpm")
            val tr = o.optJSONObject("trophies")
            val us = o.optJSONObject("usage")
            return Stats(
                totalChars = o.optInt("totalChars", 0),
                errors = o.optInt("errors", 0),
                keyTimeMs = o.optLong("keyTimeMs", 0),
                bestWpm = o.optDouble("bestWpm", 0.0),
                recentWpm = if (rw == null) emptyList() else List(rw.length()) { rw.optDouble(it) },
                trophies = buildMap { tr?.keys()?.forEach { put(it, tr.optLong(it)) } },
                usage = buildMap { us?.keys()?.forEach { put(it, us.optInt(it)) } },
                rxLevel = o.optInt("rxLevel", 1),
                txLevel = o.optInt("txLevel", 1),
                rxStreak = o.optInt("rxStreak", 0),
                txStreak = o.optInt("txStreak", 0),
                rxSpeedIdx = o.optInt("rxSpeed3", 0),
                series = o.optInt("series", 0),
                rxChars = charsFromJson(o.optJSONObject("rxChars")),
                txChars = charsFromJson(o.optJSONObject("txChars")),
                trainingMs = o.optLong("trainingMs", 0),
                days = o.optJSONObject("days").let { d -> buildMap { d?.keys()?.forEach { put(it, d.optLong(it)) } } },
                history = o.optJSONArray("history").let { a ->
                    if (a == null) emptyList() else List(a.length()) { SeriesRec.fromJson(a.getJSONObject(it)) }
                },
                confusions = o.optJSONObject("confusions").let { d -> buildMap { d?.keys()?.forEach { put(it, d.optInt(it)) } } },
                recentRatio = o.optJSONArray("recentRatio").let { a -> if (a == null) emptyList() else List(a.length()) { a.optDouble(it) } }
            )
        }

        private fun charsFromJson(o: JSONObject?): Map<String, Pair<Int, Int>> = buildMap {
            o?.keys()?.forEach { k ->
                val a = o.optJSONArray(k)
                if (a != null && a.length() >= 2) put(k, a.optInt(0) to a.optInt(1))
            }
        }
    }
}

data class SeriesRec(val time: Long, val track: String, val level: Int, val acc: Double, val wpm: Int) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", time); put("k", track); put("l", level); put("a", acc); put("w", wpm)
    }

    companion object {
        fun fromJson(o: JSONObject) = SeriesRec(
            o.optLong("t"), o.optString("k", "rx"), o.optInt("l", 1), o.optDouble("a", 0.0), o.optInt("w", 0)
        )
    }
}

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("hermorse", Context.MODE_PRIVATE)

    fun profiles(): List<String> {
        val a = JSONArray(prefs.getString("profiles", "[]"))
        return List(a.length()) { a.getString(it) }
    }

    private fun saveProfiles(list: List<String>) {
        prefs.edit().putString("profiles", JSONArray(list).toString()).apply()
    }

    fun addProfile(name: String) {
        val list = profiles()
        if (name !in list) saveProfiles(list + name)
    }

    fun deleteProfile(name: String) {
        saveProfiles(profiles() - name)
        prefs.edit().remove("settings_$name").remove("stats_$name").apply()
    }

    fun lastProfile(): String? = prefs.getString("last", null)
    fun setLast(name: String) = prefs.edit().putString("last", name).apply()

    fun loadSettings(name: String): Settings =
        prefs.getString("settings_$name", null)?.let { Settings.fromJson(JSONObject(it)) } ?: Settings()

    fun saveSettings(name: String, s: Settings) =
        prefs.edit().putString("settings_$name", s.toJson().toString()).apply()

    fun loadStats(name: String): Stats =
        prefs.getString("stats_$name", null)?.let { Stats.fromJson(JSONObject(it)) } ?: Stats()

    fun saveStats(name: String, s: Stats) =
        prefs.edit().putString("stats_$name", s.toJson().toString()).apply()

    /** Fichier d'export d'un profil (.hermorse). */
    fun exportProfile(name: String): String = JSONObject().apply {
        put("format", "hermorse"); put("version", 1); put("name", name)
        put("settings", loadSettings(name).toJson()); put("stats", loadStats(name).toJson())
    }.toString(2)

    /** Importe un profil ; renvoie son nom (suffixé s'il existe déjà), ou null si le fichier n'est pas valide. */
    fun importProfile(json: String): String? {
        val o = try { JSONObject(json) } catch (_: Exception) { return null }
        if (o.optString("format") != "hermorse") return null
        val base = o.optString("name", "Profil").ifBlank { "Profil" }
        var name = base
        var n = 2
        while (name in profiles()) { name = "$base ($n)"; n++ }
        addProfile(name)
        o.optJSONObject("settings")?.let { saveSettings(name, Settings.fromJson(it)) }
        o.optJSONObject("stats")?.let { saveStats(name, Stats.fromJson(it)) }
        return name
    }
}
