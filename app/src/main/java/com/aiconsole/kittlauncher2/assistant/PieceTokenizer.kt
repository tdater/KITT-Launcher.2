package com.aiconsole.kittlauncher2.assistant

/**
 * Turns plain words ("HEY KITT") into the sub-word pieces the offline keyword spotter listens for
 * ("▁HE Y ▁KI T T"), from the model's own sentencepiece file (bpe.model), the way sentencepiece
 * itself splits them: best-scoring split for unigram models, ranked merges for BPE models.
 * Plain Kotlin with its own small protobuf reader (unit-tested against the model's examples).
 */
class PieceTokenizer private constructor(private val scores: Map<String, Float>, private val bpe: Boolean) {

    /** The pieces for [text], space-separated, or null if a word can't be spelled with the model's pieces. */
    fun encode(text: String): String? {
        val pieces = text.trim().uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.flatMap { word ->
            val symbol = "$SPACE$word"
            (if (bpe) mergeBpe(symbol) else viterbi(symbol)) ?: return null
        }
        return pieces.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** Unigram: the split with the highest total score. */
    private fun viterbi(s: String): List<String>? {
        val best = FloatArray(s.length + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(s.length + 1) { -1 }
        best[0] = 0f
        for (end in 1..s.length) {
            for (start in maxOf(0, end - MAX_PIECE)..<end) {
                if (best[start] == Float.NEGATIVE_INFINITY) continue
                val score = scores[s.substring(start, end)] ?: continue
                val total = best[start] + score
                if (total > best[end]) {
                    best[end] = total
                    from[end] = start
                }
            }
        }
        if (from[s.length] < 0) return null
        val out = ArrayList<String>()
        var end = s.length
        while (end > 0) {
            val start = from[end]
            out += s.substring(start, end)
            end = start
        }
        return out.reversed()
    }

    /** BPE: merge the best-ranked adjacent pair until no pair is a piece. */
    private fun mergeBpe(s: String): List<String>? {
        val symbols = s.map { it.toString() }.toMutableList()
        if (symbols.any { it !in scores }) return null
        while (symbols.size > 1) {
            var bestAt = -1
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in 0 until symbols.size - 1) {
                val score = scores[symbols[i] + symbols[i + 1]] ?: continue
                if (score > bestScore) {
                    bestScore = score
                    bestAt = i
                }
            }
            if (bestAt < 0) break
            symbols[bestAt] = symbols[bestAt] + symbols.removeAt(bestAt + 1)
        }
        return symbols
    }

    companion object {
        const val SPACE = "▁"
        private const val MAX_PIECE = 16

        /** Reads a sentencepiece ModelProto (bpe.model). */
        fun fromModel(bytes: ByteArray): PieceTokenizer {
            val scores = HashMap<String, Float>()
            var bpe = false
            val top = Proto(bytes, 0, bytes.size)
            while (top.more()) {
                val (field, wire) = top.tag()
                when {
                    field == 1 && wire == 2 -> {
                        val piece = top.message()
                        var text: String? = null
                        var score = 0f
                        var type = 1
                        while (piece.more()) {
                            val (f, w) = piece.tag()
                            when {
                                f == 1 && w == 2 -> text = piece.string()
                                f == 2 && w == 5 -> score = piece.float()
                                f == 3 && w == 0 -> type = piece.varint().toInt()
                                else -> piece.skip(w)
                            }
                        }
                        // Normal and user-defined pieces only (not <unk>, <s>, control or byte pieces).
                        if (text != null && (type == 1 || type == 4)) scores[text] = score
                    }
                    field == 2 && wire == 2 -> {
                        // TrainerSpec: model_type (3) is 2 for BPE, 1 for unigram.
                        val spec = top.message()
                        while (spec.more()) {
                            val (f, w) = spec.tag()
                            if (f == 3 && w == 0) bpe = spec.varint() == 2L else spec.skip(w)
                        }
                    }
                    else -> top.skip(wire)
                }
            }
            return PieceTokenizer(scores, bpe)
        }
    }

    /** Minimal protobuf reader over [bytes] from [pos] to [end]. */
    private class Proto(private val bytes: ByteArray, private var pos: Int, private val end: Int) {
        fun more() = pos < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun tag(): Pair<Int, Int> = varint().let { (it ushr 3).toInt() to (it and 7).toInt() }

        fun message(): Proto {
            val len = varint().toInt()
            return Proto(bytes, pos, pos + len).also { pos += len }
        }

        fun string(): String {
            val len = varint().toInt()
            return String(bytes, pos, len, Charsets.UTF_8).also { pos += len }
        }

        fun float(): Float {
            val bits = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return java.lang.Float.intBitsToFloat(bits)
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> pos += varint().toInt()
                5 -> pos += 4
                else -> pos = end
            }
        }
    }
}
