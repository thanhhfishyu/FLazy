package io.github.thanhhfishyu.flazy

/** One recognised line of text and the paragraph (OCR block) it belongs to. */
data class OcrLine(val text: String, val block: Int)

/** Pure text helpers: no Android dependencies, so they are easy to reason about. */
object PageText {

    private fun isCjk(c: Char): Boolean {
        val b = Character.UnicodeBlock.of(c)
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
            b == Character.UnicodeBlock.GENERAL_PUNCTUATION
    }

    /**
     * Turns OCR lines into readable text: lines of one paragraph are joined
     * (without a space between Chinese characters), paragraphs are separated by a newline.
     */
    fun format(lines: List<OcrLine>): String {
        val sb = StringBuilder()
        var lastBlock = -1
        for (line in lines) {
            val t = line.text.trim()
            if (t.isEmpty()) continue
            if (sb.isEmpty()) {
                sb.append(t)
            } else if (line.block != lastBlock) {
                sb.append('\n').append(t)
            } else {
                val prev = sb[sb.length - 1]
                if (isCjk(prev) || isCjk(t[0])) sb.append(t) else sb.append(' ').append(t)
            }
            lastBlock = line.block
        }
        return sb.toString()
    }

    /** Text with all whitespace removed, used to compare two pages. */
    fun normalize(lines: List<OcrLine>): String =
        lines.joinToString("") { it.text }.filterNot { it.isWhitespace() }

    /** Similarity of two texts from 0.0 to 1.0, based on shared character pairs. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        fun pairs(s: String): Map<String, Int> {
            if (s.length < 2) return mapOf(s to 1)
            val m = HashMap<String, Int>()
            for (i in 0 until s.length - 1) {
                val k = s.substring(i, i + 2)
                m[k] = (m[k] ?: 0) + 1
            }
            return m
        }
        val pa = pairs(a)
        val pb = pairs(b)
        var shared = 0
        for ((k, n) in pa) shared += minOf(n, pb[k] ?: 0)
        val total = pa.values.sum() + pb.values.sum()
        return 2.0 * shared / total
    }

    /**
     * For scrolling readers, the top of a new screen repeats the bottom of the previous one.
     * Drops the leading lines of [current] that match the trailing lines of [previous].
     */
    fun dropOverlap(previous: List<OcrLine>, current: List<OcrLine>): List<OcrLine> {
        if (previous.isEmpty() || current.isEmpty()) return current
        val prev = previous.map { it.text.filterNot(Char::isWhitespace) }
        val cur = current.map { it.text.filterNot(Char::isWhitespace) }
        val maxK = minOf(prev.size, cur.size)
        for (k in maxK downTo 1) {
            var match = true
            for (i in 0 until k) {
                val p = prev[prev.size - k + i]
                val c = cur[i]
                if (p != c && similarity(p, c) < 0.85) {
                    match = false
                    break
                }
            }
            if (match) return current.drop(k)
        }
        return current
    }
}
