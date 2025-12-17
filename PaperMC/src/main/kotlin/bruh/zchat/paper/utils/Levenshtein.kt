package bruh.zchat.paper.utils

object Levenshtein {
    fun distance(s1: String, s2: String): Int {
        val s1_len = s1.length
        val s2_len = s2.length

        var d = Array(s1_len + 1) { IntArray(s2_len + 1) }

        for (i in 0..s1_len) {
            d[i][0] = i
        }
        for (j in 0..s2_len) {
            d[0][j] = j
        }

        for (i in 1..s1_len) {
            for (j in 1..s2_len) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,      // Deletion
                    d[i][j - 1] + 1,      // Insertion
                    d[i - 1][j - 1] + cost // Substitution
                )
            }
        }
        return d[s1_len][s2_len]
    }
}
