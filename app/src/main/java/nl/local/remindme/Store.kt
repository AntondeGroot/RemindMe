package nl.local.remindme

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Settings live in a single JSON blob in SharedPreferences. Small, and easy to back up. */
object Store {

    private const val PREFS = "remindme"
    private const val KEY = "config"

    @Volatile
    private var cached: Config? = null

    fun load(context: Context): Config {
        cached?.let { return it }
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        val config = if (raw.isNullOrBlank()) Defaults.config else runCatching { decode(raw) }
            .getOrDefault(Defaults.config)
        cached = config
        return config
    }

    fun save(context: Context, config: Config) {
        val pruned = config.copy(overrides = prune(config.overrides))
        cached = pruned
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(pruned))
            .apply()
    }

    /** Drop one-off day types older than yesterday so the blob cannot grow forever. */
    private fun prune(overrides: Map<String, DayType>): Map<String, DayType> {
        val cutoff = LocalDate.now().minusDays(1)
        return overrides.filterKeys {
            runCatching { LocalDate.parse(it) >= cutoff }.getOrDefault(false)
        }
    }

    private fun encode(config: Config): String {
        val reminders = JSONArray()
        config.reminders.forEach { r ->
            val o = JSONObject()
                .put("id", r.id)
                .put("emoji", r.emoji)
                .put("title", r.title)
                .put("active", r.active)
                .put("specific", encodeSpecific(r.specific))
            DayType.entries.forEach { type ->
                o.put(type.name, JSONArray().apply { r.timesFor(type).forEach { put(it) } })
            }
            reminders.put(o)
        }

        val week = JSONObject()
        config.week.forEach { (day, type) -> week.put(day.toString(), type.name) }

        val overrides = JSONObject()
        config.overrides.forEach { (date, type) -> overrides.put(date, type.name) }

        return JSONObject()
            .put("reminders", reminders)
            .put("week", week)
            .put("overrides", overrides)
            .toString()
    }

    private fun encodeSpecific(specific: SpecificDays): JSONObject = JSONObject()
        .put("days", JSONArray().apply { specific.days.sorted().forEach { put(it) } })
        .put("weeks", specific.weeks.name)
        .put("times", JSONArray().apply { specific.times.forEach { put(it) } })

    /** Absent for reminders written before specific days existed, which simply have none. */
    private fun decodeSpecific(o: JSONObject?): SpecificDays {
        if (o == null) return SpecificDays()
        return SpecificDays(
            days = readInts(o.optJSONArray("days")).filter { it in 1..7 }.toSet(),
            weeks = Weeks.from(o.optString("weeks")),
            times = readInts(o.optJSONArray("times")).sorted()
        )
    }

    private fun readInts(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getInt(it) }
    }

    private fun decode(raw: String): Config {
        val root = JSONObject(raw)

        val reminders = mutableListOf<Reminder>()
        val array = root.optJSONArray("reminders") ?: JSONArray()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val times = DayType.entries.associateWith { type ->
                val list = o.optJSONArray(type.name) ?: JSONArray()
                (0 until list.length()).map { list.getInt(it) }.sorted()
            }
            reminders += Reminder(
                id = o.optString("id"),
                emoji = o.optString("emoji", "🔔"),
                title = o.optString("title"),
                active = o.optBoolean("active", true),
                times = times,
                specific = decodeSpecific(o.optJSONObject("specific"))
            )
        }

        val week = mutableMapOf<Int, DayType>()
        root.optJSONObject("week")?.let { w ->
            w.keys().forEach { key -> week[key.toInt()] = DayType.from(w.optString(key)) }
        }

        val overrides = mutableMapOf<String, DayType>()
        root.optJSONObject("overrides")?.let { o ->
            o.keys().forEach { key -> overrides[key] = DayType.from(o.optString(key)) }
        }

        return Config(
            reminders = reminders,
            week = week.ifEmpty { Defaults.config.week },
            overrides = overrides
        )
    }
}
