package com.killersparrow1.clashtrackers.core

import java.util.TreeMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Turns ClashKing's `static_data.json` (ClashKingAssets, served from assets.clashk.ing) into the compact game data file
 * the app uses (the format of `assets/max_data.json`, see [MaxData]). It is the same conversion as
 * `tools/gen_max_data_ck.cjs`, which builds the file that ships with the app; keep the two in step.
 *
 * How ClashKing's numbers map to ours:
 *  - buildings and traps: a level holds the cost, time and Town Hall to reach that level;
 *  - troops, spells, heroes, pets, guardians: a level holds the cost and time to go from it to the next level, so the
 *    step to level N + 1 is read from level N and the Town Hall requirement from level N + 1;
 *  - walls: ClashKing lists Gold only; from level 5 walls can also use Elixir ("Gold or Elixir");
 *  - counts per Town Hall: added up from the "unlocks" of each Town Hall level.
 */
object ClashKingData {
    private const val TOWN_HALL = 1000001L
    private val SKIP_IDS = setOf(TOWN_HALL, 1000015L /* Builder's Hut */, 1000064L /* B.O.B's Hut */)
    private val BUILDING_CATEGORY = mapOf("Defense" to "d", "Army" to "a", "Resource" to "r", "Helper" to "p")
    private val RESOURCE = mapOf(
        "Gold" to 0, "Elixir" to 1, "Dark Elixir" to 2, "Gold or Elixir" to 3, "Builder Gold" to 4, "Builder Elixir" to 5,
    )
    private const val BUILDER_HALL = 1000034L
    private val BB_CATEGORY = mapOf("Defense" to "d", "Army" to "a", "Resource" to "r", "BB Builder Hall" to "p")
    /** Builder Base: Wall and B.O.T.O's Shack are left out. */
    private val BB_SKIP_IDS = setOf(1000033L, 1000047L)

    /** troops, spells, heroes, pets, guardians: group, kind, category. */
    private val LEVELLED = listOf(
        Triple("troops", "l", "a"), Triple("spells", "l", "a"), Triple("heroes", "h", "a"),
        Triple("pets", "p", "a"), Triple("guardians", "b", "p"),
    )

    /** True when [a] and [b] hold the same game data (spacing and key order do not matter). */
    fun sameData(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return runCatching {
            val x = Json.parseToJsonElement(a)
            x is JsonObject && x == Json.parseToJsonElement(b)
        }.getOrDefault(false)
    }

    /** True when [newData] differs from the data in use and does not know fewer Town Halls than it. */
    fun isBetter(newText: String, newData: MaxData, current: MaxData, currentText: String?): Boolean =
        newData.maxTownHall >= current.maxTownHall && !sameData(newText, currentText)

    /** ClashKing's file-name rule: lower case, spaces to `_`, and `.` `?` `\q` `’` removed. */
    internal fun clean(name: String): String =
        name.lowercase().replace(" ", "_").replace(".", "").replace("?", "").replace("\\q", "").replace("’", "")

    /** Where the picture of an item is on assets.clashk.ing, relative to the site root. */
    internal fun iconPath(group: String, name: String, levels: Int, builderBase: Boolean = false): String = when (group) {
        "buildings" -> "buildings/${if (builderBase) "builder-base" else "home-village"}/${clean(name)}/level_$levels.webp"
        "traps" -> "traps/${if (builderBase) "builder-base" else "home-village"}/${clean(name)}/level_$levels.webp"
        "spells" -> "spells/${clean(name)}.webp"
        "equipment" -> "equipment/${clean(name)}.webp"
        else -> "$group/${clean(name)}/icon.webp"
    }

    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.long ?: 0L
    private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { runCatching { it.int }.getOrNull() }
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    /**
     * The converted game data as text in the app's format. Throws when [staticData] is not ClashKing's game data.
     * [previous] is the game data in use: buildings ClashKing lists without any levels (Helper Hut, Crafting Station at
     * the moment) keep the steps they already have.
     */
    fun convert(staticData: String, previous: MaxData? = null): String {
        val root = Json.parseToJsonElement(staticData).jsonObject
        val townHall = root.objects("buildings").first { it.long("_id") == TOWN_HALL }
        val thLevels = townHall.objects("levels")
        val maxTh = thLevels.maxOf { it.intOrNull("level") ?: 0 }
        require(maxTh in 1..99) { "Town Hall levels are missing" }

        // Cumulative count of each building by Town Hall level.
        val unlocked = HashMap<Long, HashMap<Int, Int>>()
        for (l in thLevels) {
            val level = l.intOrNull("level") ?: continue
            for (u in l.objects("unlocks")) {
                val id = u.long("_id")
                if (id != 0L) unlocked.getOrPut(id) { HashMap() }[level] = u.intOrNull("quantity") ?: 0
            }
        }
        fun counts(id: Long): List<Int>? {
            val u = unlocked[id] ?: return null
            var total = 0
            return (0..maxTh).map { t -> total += u[t] ?: 0; total }
        }

        // Builder Base counts come from the Builder Hall's unlocks, indexed by Builder Hall level.
        val bbUnlocked = HashMap<Long, HashMap<Int, Int>>()
        val builderHallLevels = root.objects("buildings").firstOrNull { it.long("_id") == BUILDER_HALL }?.objects("levels").orEmpty()
        val maxBh = builderHallLevels.maxOfOrNull { it.intOrNull("level") ?: 0 } ?: 0
        for (l in builderHallLevels) {
            val level = l.intOrNull("level") ?: continue
            for (u in l.objects("unlocks")) {
                val id = u.long("_id")
                if (id != 0L) bbUnlocked.getOrPut(id) { HashMap() }[level] = u.intOrNull("quantity") ?: 0
            }
        }
        fun bbCounts(id: Long): List<Int>? {
            val u = bbUnlocked[id] ?: return null
            var total = 0
            return (0..maxBh).map { t -> total += u[t] ?: 0; total }
        }

        class Out(val kind: String, val category: String, val counts: List<Int>?, val steps: List<List<Long>>, val name: String?, val icon: String?)
        val items = TreeMap<Long, Out>()

        fun add(o: JsonObject, kind: String, category: String, steps: List<List<Long>>, group: String, builderBase: Boolean = false) {
            if (steps.isEmpty()) return
            val id = o.long("_id")
            val name = o.text("name") ?: return
            items[id] = Out(kind, category, if (builderBase) bbCounts(id) else counts(id), steps, name, iconPath(group, name, steps.size, builderBase))
        }
        // A step without a Town Hall requirement would break the reader: assume the newest Town Hall (Builder Hall 1 in the
        // Builder Base, where the Builder Hall itself and B.O.B Control have none).
        fun th(value: Int?, builderBase: Boolean = false) = (value ?: if (builderBase) 1 else maxTh).toLong()
        fun resource(o: JsonObject) = (RESOURCE[o.text("upgrade_resource")] ?: 0).toLong()

        // Buildings of the home village, including walls.
        for (o in root.objects("buildings")) {
            val id = o.long("_id")
            if (o.text("village") != "home" || id in SKIP_IDS || o["levels"] !is JsonArray) continue
            val levels = o.objects("levels")
            val type = o.text("type")
            if (type == "Wall") {
                add(o, "w", "p", levels.map { l ->
                    listOf(l.long("build_time"), l.long("build_cost"), if ((l.intOrNull("level") ?: 0) >= 5) 3L else 0L, th(l.intOrNull("required_townhall")))
                }, "buildings")
                continue
            }
            val category = BUILDING_CATEGORY[type] ?: continue
            add(o, "b", category, levels.map { l ->
                listOf(l.long("build_time"), l.long("build_cost"), resource(o), th(l.intOrNull("required_townhall")))
            }, "buildings")
            // Listed without levels: keep what the app already has.
            if (levels.isEmpty()) previous?.items?.get(id.toInt())?.let { p ->
                items[id] = Out(p.kind.toString(), p.category.toString(), p.counts, p.steps.map { s -> listOf(s.seconds, s.cost, s.resource.toLong(), s.townHall.toLong()) }, p.name, p.icon)
            }
        }
        for (o in root.objects("traps")) {
            if (o.text("village") != "home") continue
            add(o, "b", "p", o.objects("levels").map { l ->
                listOf(l.long("build_time"), l.long("build_cost"), resource(o), th(l.intOrNull("required_townhall")))
            }, "traps")
        }

        // Crafted Defenses: the Crafting Station's seasonal defenses have upgradable modules. Each module is an item of its
        // own (kind b, category c) in the building step format; the picture is its defense's. Keep in step with gen_max_data_ck.cjs.
        root.objects("buildings").firstOrNull { it.long("_id") == 1000097L }?.let { station ->
            for (defense in station.objects("seasonal_defenses")) {
                for (m in defense.objects("modules")) {
                    val levels = m.objects("levels")
                    val name = m.text("name") ?: continue
                    if (levels.isEmpty()) continue
                    items[m.long("_id")] = Out(
                        "b", "c", null,
                        levels.map { l -> listOf(l.long("build_time"), l.long("build_cost"), resource(m), th(l.intOrNull("required_townhall"))) },
                        name, "buildings/seasonal-defense/${clean(defense.text("name") ?: "")}/level_1.webp",
                    )
                }
            }
        }

        // Researched or levelled things: the cost and time on a level are for going to the next one.
        for ((group, kind, category) in LEVELLED) {
            for (o in root.objects(group)) {
                if (o.text("village") == "builderBase") continue
                val levels = o.objects("levels")
                if (levels.isEmpty()) continue
                // Super, seasonal and event troops and spells have no upgrade cost: the player does not level them.
                if (levels.none { it.long("upgrade_cost") > 0 }) continue
                val steps = ArrayList<List<Long>>()
                steps += listOf(0L, 0L, resource(o), th(levels[0].intOrNull("required_townhall"))) // level 1 is unlocked, not bought
                for (i in 0 until levels.size - 1) {
                    steps += listOf(levels[i].long("upgrade_time"), levels[i].long("upgrade_cost"), resource(o), th(levels[i + 1].intOrNull("required_townhall")))
                }
                add(o, kind, category, steps, group)
            }
        }

        // Hero equipment: no timer, paid in up to three ores. A step is [0, shiny ore, 6, Town Hall, glowy ore, starry ore]
        // (the last two only when used); a level holds the cost to the next one.
        for (o in root.objects("equipment")) {
            val levels = o.objects("levels")
            if (levels.isEmpty()) continue
            val steps = ArrayList<List<Long>>()
            steps += listOf(0L, 0L, 6L, th(levels[0].intOrNull("required_townhall"))) // level 1 is unlocked, not bought
            for (i in 0 until levels.size - 1) {
                val cost = (levels[i]["upgrade_cost"] as? JsonObject) ?: JsonObject(emptyMap())
                val step = mutableListOf(0L, cost.long("shiny_ore"), 6L, th(levels[i + 1].intOrNull("required_townhall")))
                if (cost.long("glowy_ore") > 0 || cost.long("starry_ore") > 0) { step += cost.long("glowy_ore"); step += cost.long("starry_ore") }
                steps += step
            }
            add(o, "e", "a", steps, "equipment")
        }

        // Builder Base (kinds in capitals): B buildings and traps, M Battle Machine and Battle Copter, S Star Laboratory
        // troops. The "Town Hall" of a step is the Builder Hall level.
        for (o in root.objects("buildings")) {
            val id = o.long("_id")
            if (o.text("village") != "builderBase" || id in BB_SKIP_IDS || o["levels"] !is JsonArray) continue
            val levels = o.objects("levels")
            val category = BB_CATEGORY[o.text("type")] ?: continue
            if (levels.isEmpty() || RESOURCE[o.text("upgrade_resource")] == null) continue
            add(o, "B", category, levels.map { l ->
                listOf(l.long("build_time"), l.long("build_cost"), resource(o), th(l.intOrNull("required_townhall"), true))
            }, "buildings", true)
        }
        for (o in root.objects("traps")) {
            if (o.text("village") != "builderBase") continue
            add(o, "B", "p", o.objects("levels").map { l ->
                listOf(l.long("build_time"), l.long("build_cost"), resource(o), th(l.intOrNull("required_townhall"), true))
            }, "traps", true)
        }
        for ((group, kind) in listOf("troops" to "S", "heroes" to "M")) {
            for (o in root.objects(group)) {
                if (o.text("village") != "builderBase") continue
                val levels = o.objects("levels")
                if (levels.isEmpty()) continue
                val steps = ArrayList<List<Long>>()
                steps += listOf(0L, 0L, resource(o), th(levels[0].intOrNull("required_townhall"), true))
                for (i in 0 until levels.size - 1) {
                    steps += listOf(levels[i].long("upgrade_time"), levels[i].long("upgrade_cost"), resource(o), th(levels[i + 1].intOrNull("required_townhall"), true))
                }
                add(o, kind, "a", steps, group, true)
            }
        }
        require(items.size >= 20) { "Not enough items in the game data" }

        val out = buildJsonObject {
            put("source", "ClashKingAssets static_data.json")
            put("maxTh", maxTh)
            put("items", buildJsonObject {
                for ((id, it) in items) put(id.toString(), buildJsonObject {
                    put("k", it.kind)
                    put("c", it.category)
                    put("n", it.counts?.let { c -> JsonArray(c.map { v -> JsonPrimitive(v) }) } ?: JsonNull)
                    put("l", JsonArray(it.steps.map { s -> JsonArray(s.map { v -> JsonPrimitive(v) }) }))
                    it.name?.let { n -> put("nm", n) }
                    it.icon?.let { i -> put("i", i) }
                })
            })
        }
        return out.toString()
    }
}
