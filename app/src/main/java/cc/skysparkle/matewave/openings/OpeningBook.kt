package cc.skysparkle.matewave.openings

import android.content.Context

data class OpeningName(val eco: String, val name: String)

/**
 * Opening theory from the Lichess game database (May 2013, CC0), used to tell book moves from
 * deviations, and opening names from lichess-org/chess-openings (CC0). Both are keyed by SAN move
 * sequences from the standard starting position, so no position hashing is needed at runtime.
 *
 * explorer.txt: header "#games N", then one node per line "parentId san white draws black";
 * node ids are line numbers (the root is 0) and parents always come before children. Only the
 * tree shape is used here.
 */
object OpeningBook {

    private class Data(
        val san: Array<String>,
        val children: Array<IntArray>,
        val names: Map<String, OpeningName>,
        /** Length in plies of the longest named line; longer prefixes are never looked up. */
        val longestName: Int
    )

    @Volatile private var data: Data? = null

    /** Reads both files; call from a background thread. Safe to call repeatedly. */
    fun load(context: Context) {
        if (data != null) return
        synchronized(this) {
            if (data != null) return
            val assets = context.applicationContext.assets
            val parents = ArrayList<Int>(40_000)
            val sans = ArrayList<String>(40_000)
            parents.add(-1); sans.add("")
            assets.open("openings/explorer.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank() || line.startsWith("#")) continue
                    val f = line.split(' ')
                    if (f.size < 2) continue
                    parents.add(f[0].toInt()); sans.add(f[1])
                }
            }
            val childLists = Array(parents.size) { ArrayList<Int>(0) }
            for (i in 1 until parents.size) childLists[parents[i]].add(i)

            val names = HashMap<String, OpeningName>(4_000)
            var longest = 0
            assets.open("openings/names.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val f = line.split('\t')
                    if (f.size < 3) continue
                    names.putIfAbsent(f[2], OpeningName(f[0], f[1]))
                    longest = maxOf(longest, f[2].count { it == ' ' } + 1)
                }
            }
            data = Data(
                san = sans.toTypedArray(),
                children = Array(childLists.size) { childLists[it].toIntArray() },
                names = names,
                longestName = longest
            )
        }
    }

    /**
     * Number of leading moves of [path] that are opening theory: played in the game database
     * or part of a named opening line.
     */
    fun bookDepth(path: List<String>): Int {
        val d = data ?: return 0
        var current = 0
        var depth = 0
        for (san in path) {
            current = d.children[current].firstOrNull { d.san[it] == san } ?: break
            depth++
        }
        for (len in minOf(path.size, d.longestName) downTo depth + 1) {
            if (d.names.containsKey(path.subList(0, len).joinToString(" "))) return len
        }
        return depth
    }

    /** Deepest ply covered by the game database; beyond it "out of book" cannot be judged. */
    const val MAX_TREE_PLY = 20

    /** Name of the most specific known opening along [path]. */
    fun openingName(path: List<String>): OpeningName? {
        val d = data ?: return null
        for (len in minOf(path.size, d.longestName) downTo 1) {
            d.names[path.subList(0, len).joinToString(" ")]?.let { return it }
        }
        return null
    }
}
