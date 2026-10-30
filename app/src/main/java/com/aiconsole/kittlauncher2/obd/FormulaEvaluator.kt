package com.aiconsole.kittlauncher2.obd

/**
 * Torque/Car Scanner style PID equations, e.g. `((A*256)+B)/4` or `(signed(A)*256+B)/16`.
 * Letters A..Z are the response data bytes (unsigned) after the echoed service+PID.
 * Supports + - * /, parentheses, unary minus, and signed(), abs(), min(), max().
 * Formulas are compiled once and cached; evaluation is allocation-free.
 */
object FormulaEvaluator {

    class FormulaException(message: String) : IllegalArgumentException(message)

    fun interface Expr {
        fun eval(data: ByteArray): Double
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Expr>()

    fun compile(formula: String): Expr = cache.getOrPut(formula) { Parser(formula).parse() }

    /** Validates a formula; returns an error message, or null when it compiles. */
    fun validate(formula: String): String? = try {
        compile(formula)
        null
    } catch (e: FormulaException) {
        e.message
    }

    fun evaluate(formula: String, data: ByteArray): Double = compile(formula).eval(data)

    /** Highest byte index a formula reads (A=0), used to know how many bytes a PID returns. */
    fun bytesUsed(formula: String): Int =
        Regex("(?<![A-Za-z])([A-Z])(?![A-Za-z(])").findAll(formula).maxOfOrNull { it.groupValues[1][0] - 'A' + 1 } ?: 0

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Expr {
            val expr = expression()
            skipSpace()
            if (pos < text.length) throw FormulaException("Unexpected '${text[pos]}' at position ${pos + 1}")
            return expr
        }

        private fun expression(): Expr {
            var left = term()
            while (true) {
                skipSpace()
                left = when (peek()) {
                    '+' -> { pos++; val l = left; val r = term(); Expr { l.eval(it) + r.eval(it) } }
                    '-' -> { pos++; val l = left; val r = term(); Expr { l.eval(it) - r.eval(it) } }
                    else -> return left
                }
            }
        }

        private fun term(): Expr {
            var left = factor()
            while (true) {
                skipSpace()
                left = when (peek()) {
                    '*' -> { pos++; val l = left; val r = factor(); Expr { l.eval(it) * r.eval(it) } }
                    '/' -> { pos++; val l = left; val r = factor(); Expr { l.eval(it) / r.eval(it) } }
                    else -> return left
                }
            }
        }

        private fun factor(): Expr {
            skipSpace()
            val c = peek() ?: throw FormulaException("Formula ends unexpectedly")
            return when {
                c == '-' -> { pos++; val inner = factor(); Expr { -inner.eval(it) } }
                c == '+' -> { pos++; factor() }
                c == '(' -> {
                    pos++
                    val inner = expression()
                    expect(')')
                    inner
                }
                c.isDigit() || c == '.' -> number()
                c.isLetter() -> identifier()
                else -> throw FormulaException("Unexpected '$c' at position ${pos + 1}")
            }
        }

        private fun number(): Expr {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            val value = text.substring(start, pos).toDoubleOrNull()
                ?: throw FormulaException("Bad number '${text.substring(start, pos)}'")
            return Expr { value }
        }

        private fun identifier(): Expr {
            val start = pos
            while (pos < text.length && text[pos].isLetter()) pos++
            val name = text.substring(start, pos)
            skipSpace()
            if (peek() == '(') {
                pos++
                val args = mutableListOf(expression())
                skipSpace()
                while (peek() == ',') {
                    pos++
                    args += expression()
                    skipSpace()
                }
                expect(')')
                return function(name.lowercase(), args)
            }
            if (name.length == 1 && name[0] in 'A'..'Z') {
                val index = name[0] - 'A'
                return Expr { data ->
                    if (index >= data.size) throw FormulaException("Response has no byte $name")
                    (data[index].toInt() and 0xFF).toDouble()
                }
            }
            throw FormulaException("Unknown name '$name' (use A-Z for data bytes)")
        }

        private fun function(name: String, args: List<Expr>): Expr = when (name) {
            "signed" -> {
                requireArgs(name, args, 1)
                val a = args[0]
                Expr { val v = a.eval(it); if (v >= 128) v - 256 else v }
            }
            "abs" -> { requireArgs(name, args, 1); val a = args[0]; Expr { kotlin.math.abs(a.eval(it)) } }
            "min" -> { requireArgs(name, args, 2); val (a, b) = args; Expr { minOf(a.eval(it), b.eval(it)) } }
            "max" -> { requireArgs(name, args, 2); val (a, b) = args; Expr { maxOf(a.eval(it), b.eval(it)) } }
            "tpms" -> {
                requireArgs(name, args, 1)
                val a = args[0]
                Expr { data ->
                    val byteA = a.eval(data)
                    if (data.size >= 2) {
                        val byteB = (data[1].toInt() and 0xFF).toDouble()
                        val v16 = (byteA * 256.0 + byteB) / 16.0
                        val v4 = byteA * 4.0
                        if (v16 in 50.0..450.0) v16 else if (v4 in 50.0..450.0) v4 else v16
                    } else {
                        byteA * 4.0
                    }
                }
            }
            else -> throw FormulaException("Unknown function '$name'")
        }

        private fun requireArgs(name: String, args: List<Expr>, count: Int) {
            if (args.size != count) throw FormulaException("$name() takes $count argument(s)")
        }

        private fun expect(c: Char) {
            skipSpace()
            if (peek() != c) throw FormulaException("Expected '$c' at position ${pos + 1}")
            pos++
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
