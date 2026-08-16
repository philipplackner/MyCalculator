package com.plcoding.mycalculator.calculator.domain

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.math.BigDecimal
import java.util.Locale
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The evaluator has a single entry point and no collaborators, so every break vector has to
 * travel through the expression string — or through the `Double` semantics on the way out.
 *
 * Note the operator alphabet: subtraction is U+2212 `−`, not the ASCII hyphen, and the
 * multiplication and division symbols are `×` and `÷`.
 */
class DefaultExpressionEvaluatorTest {

    private val evaluator = DefaultExpressionEvaluator()

    @Nested
    inner class TheFourOperators {

        @Test
        fun `adds two operands`() {
            assertThat(evaluator.evaluate("1+2")).isEqualTo(3.0)
        }

        @Test
        fun `subtracts two operands`() {
            assertThat(evaluator.evaluate("9−4")).isEqualTo(5.0)
        }

        @Test
        fun `multiplies two operands`() {
            assertThat(evaluator.evaluate("6×7")).isEqualTo(42.0)
        }

        @Test
        fun `divides two operands into a fractional result`() {
            assertThat(evaluator.evaluate("9÷2")).isEqualTo(4.5)
        }
    }

    @Nested
    inner class PrecedenceAndAssociativity {

        @Test
        fun `multiplication binds tighter than addition`() {
            assertThat(evaluator.evaluate("2+3×4")).isEqualTo(14.0)
        }

        @Test
        fun `division binds tighter than subtraction`() {
            assertThat(evaluator.evaluate("10−4÷2")).isEqualTo(8.0)
        }

        /** Right-associativity would give 10−(4−3) = 9. */
        @Test
        fun `additions and subtractions chain from left to right`() {
            assertThat(evaluator.evaluate("10−4−3")).isEqualTo(3.0)
        }

        /** Right-associativity would give 12÷(4÷3) = 9. */
        @Test
        fun `multiplications and divisions chain from left to right`() {
            assertThat(evaluator.evaluate("12÷4÷3")).isEqualTo(1.0)
        }

        @Test
        fun `both precedence levels hold in one expression`() {
            assertThat(evaluator.evaluate("2+3×4−10÷5")).isEqualTo(12.0)
        }
    }

    @Nested
    inner class Groups {

        @Test
        fun `a group is evaluated before the operator in front of it`() {
            assertThat(evaluator.evaluate("2×(3+4)")).isEqualTo(14.0)
        }

        @Test
        fun `nested groups are evaluated from the inside out`() {
            assertThat(evaluator.evaluate("((2+3)×2)")).isEqualTo(10.0)
        }

        /**
         * The keypad materialises the `×` before a group, so the parser never has to guess.
         * Feeding it an expression from anywhere else — a paste, a deep link, a state written
         * by another version — therefore has to spell the multiplication out.
         */
        @ParameterizedTest
        @ValueSource(strings = ["5(3)", "(1)(2)", "(3)5", "2(3+4)"])
        fun `implicit multiplication against a group is not parsed`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }
    }

    @Nested
    inner class UnaryMinus {

        @Test
        fun `a leading minus negates only the first operand`() {
            assertThat(evaluator.evaluate("−5+3")).isEqualTo(-2.0)
        }

        @Test
        fun `a minus right after an opening parenthesis negates the operand`() {
            assertThat(evaluator.evaluate("2×(−3)")).isEqualTo(-6.0)
        }

        @Test
        fun `a minus right after a binary operator negates the right operand`() {
            assertThat(evaluator.evaluate("5×−3")).isEqualTo(-15.0)
        }

        @Test
        fun `two minuses in operand position cancel each other out`() {
            assertThat(evaluator.evaluate("−−5")).isEqualTo(5.0)
        }

        @Test
        fun `a minus in front of a group negates the whole group`() {
            assertThat(evaluator.evaluate("−(2+3)")).isEqualTo(-5.0)
        }

        @Test
        fun `subtracting a negative operand adds it`() {
            assertThat(evaluator.evaluate("5−−3")).isEqualTo(8.0)
        }

        @Test
        fun `a negated operand keeps its fractional part`() {
            assertThat(evaluator.evaluate("−2.5+1")).isEqualTo(-1.5)
        }
    }

    @Nested
    inner class NumberLiterals {

        @Test
        fun `an operand may start with the decimal separator`() {
            assertThat(evaluator.evaluate(".5+1")).isEqualTo(1.5)
        }

        @Test
        fun `an operand may end with the decimal separator`() {
            assertThat(evaluator.evaluate("5.+2")).isEqualTo(7.0)
        }

        @Test
        fun `a lone decimal separator is not an operand`() {
            assertThat(evaluator.evaluate(".")).isNull()
        }

        @Test
        fun `an operand with two decimal separators has no result`() {
            assertThat(evaluator.evaluate("1.2.3")).isNull()
        }

        @Test
        fun `leading zeros do not change the value`() {
            assertThat(evaluator.evaluate("007+1")).isEqualTo(8.0)
        }

        /**
         * A German default locale writes 1.5 as "1,5". The evaluator must keep reading `.` as
         * the decimal separator regardless, or the same expression would mean 15 on one device
         * and 1.5 on another.
         */
        @Test
        fun `parsing does not depend on the default locale`() {
            val original = Locale.getDefault()
            Locale.setDefault(Locale.GERMANY)
            try {
                assertThat(evaluator.evaluate("1.5+1")).isEqualTo(2.5)
            } finally {
                Locale.setDefault(original)
            }
        }

        /**
         * `Char.isDigit()` is true for Arabic-Indic, Devanagari and fullwidth digits, so they
         * survive the number scan — only `toDoubleOrNull` rejects them afterwards. Both halves
         * have to stay in agreement about what a digit is.
         */
        @ParameterizedTest
        @ValueSource(strings = ["٥", "٥+١", "५+५", "５+５", "1+٥"])
        fun `digits from other scripts are not accepted`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }
    }

    @Nested
    inner class IncompleteExpressions {

        @ParameterizedTest
        @ValueSource(
            strings = [
                "", "5+", "5−", "5×", "5÷", "+5", "×5", "÷5", "−", "(", "()", "(5+)",
                "5++3", "5+×3", ")",
            ],
        )
        fun `an expression with a missing operand has no result`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }

        @ParameterizedTest
        @ValueSource(strings = ["(5+3", "((1+2)", "(5", "2×(3+4"])
        fun `an unclosed group has no result`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }

        @ParameterizedTest
        @ValueSource(strings = ["5)", "(1))", "1+2)", "5 5"])
        fun `anything left over after a complete expression has no result`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }
    }

    @Nested
    inner class RejectedCharacters {

        /**
         * The keypad emits U+2212, `×` and `÷`. Their ASCII look-alikes are ordinary stray
         * characters, so an expression typed on a hardware keyboard evaluates to nothing.
         */
        @ParameterizedTest
        @ValueSource(strings = ["5-3", "5*3", "5/3", "5%3", "5^3"])
        fun `ASCII operator look-alikes are not operators`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }

        @ParameterizedTest
        @ValueSource(strings = [" ", "1 + 2", " 1+2", "1+2 ", "1\t+2", "1\n+2", "1 +2"])
        fun `whitespace anywhere has no result`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }

        /** Zero-width space, bidi override, NUL and a byte-order mark. */
        @ParameterizedTest
        @ValueSource(strings = ["1\u200B+2", "\u202E1+2", "1\u00002", "\uFEFF1+2"])
        fun `invisible and control characters have no result`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }

        /**
         * A thumbs-up with a skin tone is 4 chars and a ZWJ family is 11, so a char-by-char
         * scan can walk right into the middle of one. No half of a surrogate pair may ever be
         * read as part of a number.
         */
        @ParameterizedTest
        @ValueSource(
            strings = [
                "😀",
                "1+😀",
                "5😀",
                "👍🏽",
                "1+👨‍👩‍👧‍👦+2",
            ],
        )
        fun `emoji have no result`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }

        @ParameterizedTest
        @ValueSource(strings = ["1e5", "1E5", "0x10", "NaN", "Infinity", "1,5", "1_000", "+49", "5f"])
        fun `numbers written in another notation have no result`(expression: String) {
            assertThat(evaluator.evaluate(expression)).isNull()
        }
    }

    @Nested
    inner class NonFiniteResults {

        @Test
        fun `dividing by zero has no result`() {
            assertThat(evaluator.evaluate("5÷0")).isNull()
        }

        /** NaN rather than infinity — a guard that only looks for infinity would let this pass. */
        @Test
        fun `dividing zero by zero has no result`() {
            assertThat(evaluator.evaluate("0÷0")).isNull()
        }

        /** The zero is never a literal here, so only the computed value can reveal it. */
        @Test
        fun `dividing by a group that evaluates to zero has no result`() {
            assertThat(evaluator.evaluate("5÷(3−3)")).isNull()
        }

        @Test
        fun `dividing by negative zero has no result`() {
            assertThat(evaluator.evaluate("5÷(0×−1)")).isNull()
        }

        /** −0.0 is a legal result and stays distinguishable from 0.0. */
        @Test
        fun `multiplying zero by a negative operand keeps the negative sign`() {
            assertThat(evaluator.evaluate("0×−1")).isEqualTo(-0.0)
        }

        @Test
        fun `an operand too large for a Double has no result`() {
            assertThat(evaluator.evaluate("9".repeat(400))).isNull()
        }

        /**
         * 1e300 × 1e100 ÷ 1e100 is 1e300 on paper. In `Double` the middle step is already
         * infinite and nothing brings it back, so the whole expression is worth nothing.
         */
        @Test
        fun `an intermediate overflow is never recovered from`() {
            val e300 = "1" + "0".repeat(300)
            val e100 = "1" + "0".repeat(100)

            assertThat(evaluator.evaluate("$e300×$e100÷$e100")).isNull()
        }

        /**
         * An operand below the smallest subnormal silently becomes 0.0 instead of being
         * rejected, which turns a division by a tiny number into a division by zero.
         */
        @Test
        fun `an operand too small for a Double collapses to zero`() {
            val tiny = "0." + "0".repeat(400) + "1"

            assertThat(evaluator.evaluate(tiny)).isEqualTo(0.0)
            assertThat(evaluator.evaluate("5÷$tiny")).isNull()
        }
    }

    /**
     * Characterisation of `Double` as the calculation type. These results are wrong as decimal
     * arithmetic and right as IEEE-754; they are pinned here so that the day the evaluator moves
     * to `BigDecimal`, exactly these tests report it.
     */
    @Nested
    inner class DoublePrecisionLimits {

        @Test
        fun `a sum of decimal fractions is not exact`() {
            val result = assertThat(evaluator.evaluate("0.1+0.2")).isNotNull()

            result.isNotEqualTo(0.3)
            result.isCloseTo(0.3, 1e-15)
        }

        @Test
        fun `an integer beyond the Double mantissa loses its last digit`() {
            assertThat(evaluator.evaluate("9007199254740993")).isEqualTo(9007199254740992.0)
        }

        /** Both operands are within the keypad's 12-digit input limit, so this is reachable. */
        @Test
        fun `a product of two twelve-digit operands is not exact`() {
            val operand = "999999999999"
            val exact = BigDecimal(operand) * BigDecimal(operand)

            val result = BigDecimal.valueOf(evaluator.evaluate("$operand×$operand")!!)

            assertThat(result.toBigInteger()).isNotEqualTo(exact.toBigInteger())
            assertThat(result.toBigInteger())
                .isEqualTo(BigDecimal("999999999998000000000000").toBigInteger())
        }
    }

    @Nested
    inner class ScaleAndPurity {

        @Test
        fun `a long chain of operators is evaluated without exhausting the stack`() {
            val expression = List(2_000) { "1" }.joinToString("+")

            assertThat(evaluator.evaluate(expression)).isEqualTo(2_000.0)
        }

        @Test
        fun `deeply nested groups are evaluated`() {
            val depth = 200
            val expression = "(".repeat(depth) + "1+1" + ")".repeat(depth)

            assertThat(evaluator.evaluate(expression)).isEqualTo(2.0)
        }

        @Test
        fun `a chain of unary minuses negates once per minus`() {
            assertThat(evaluator.evaluate("−".repeat(100) + "5")).isEqualTo(5.0)
            assertThat(evaluator.evaluate("−".repeat(101) + "5")).isEqualTo(-5.0)
        }

        @Test
        fun `a rejected expression leaves nothing behind for the next one`() {
            evaluator.evaluate("(5+3")
            evaluator.evaluate("5÷0")

            assertThat(evaluator.evaluate("1+1")).isEqualTo(2.0)
        }

        @Test
        fun `the same expression evaluates to the same result every time`() {
            val first = evaluator.evaluate("2×(3+4)")
            val second = evaluator.evaluate("2×(3+4)")

            assertThat(second).isEqualTo(first)
        }
    }
}
