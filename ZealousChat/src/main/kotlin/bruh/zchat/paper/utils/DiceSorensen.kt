package bruh.zchat.paper.utils

object DiceSorensen {
    fun coefficient(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val bigrams1 = getBigrams(s1.lowercase())
        val bigrams2 = getBigrams(s2.lowercase())

        if (bigrams1.isEmpty() && bigrams2.isEmpty()) return 1.0

        val intersection = bigrams1.intersect(bigrams2).size
        val total = bigrams1.size + bigrams2.size

        return if (total == 0) 0.0 else 2.0 * intersection / total
    }

    private fun getBigrams(s: String): Set<String> {
        if (s.length < 2) return emptySet()
        return (0 until s.length - 1).map { i -> s.substring(i, i + 2) }.toSet()
    }
}
