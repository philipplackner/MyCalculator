package com.plcoding.mycalculator.calculator.domain

/**
 * Evaluates a calculator expression that was built by the keypad.
 *
 * The expression alphabet is exactly:
 * - the digits `0`–`9`
 * - the decimal separator `.`
 * - the operator symbols of [CalculatorOperator] (`+`, `−`, `×`, `÷`)
 * - the parentheses `(` and `)`
 *
 * The keypad guarantees a few things about what it hands over, which keeps parsing simple:
 * - A leading `−`, and a `−` directly after a `(`, denote a negative operand. Every other
 *   operator is binary and always sits between two operands, so `5×−3` can never occur.
 * - A group is never empty and never ends on an operator, so `()` and `(5+)` cannot occur.
 * - Multiplication against a group is always explicit: the keypad turns `2(3)` into `2×(3)`.
 * - Parentheses can still be left unbalanced while the user is typing, e.g. `(5+3`.
 *
 * Implementations return `null` when the expression cannot be turned into a number — it is
 * empty, still incomplete (a trailing operator or an unclosed group), or mathematically
 * undefined (a division by zero).
 */
fun interface ExpressionEvaluator {
    fun evaluate(expression: String): Double?
}
