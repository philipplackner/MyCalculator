package com.plcoding.mycalculator.calculator.domain

private const val DECIMAL_SEPARATOR = '.'
private const val LEFT_PARENTHESIS = '('
private const val RIGHT_PARENTHESIS = ')'

class DefaultExpressionEvaluator : ExpressionEvaluator {

    override fun evaluate(expression: String): Double? = Parser(expression).parse()

    /**
     * A recursive descent parser over the expression grammar, one precedence level per method:
     * a sum is made of products, a product is made of operands, and an operand is either a
     * number or a parenthesised sum — which is what puts a group below every operator.
     */
    private class Parser(private val expression: String) {

        private var index = 0

        /**
         * Parsing fails — and the whole expression is worth no result — as soon as a part of it
         * is missing. Anything left over after the sum means the expression was not one number.
         */
        fun parse(): Double? {
            val value = parseSum() ?: return null
            // Anything that is not a finite number — a division by zero above all — is not a
            // result the user can keep calculating with.
            return value.takeIf { index == expression.length && it.isFinite() }
        }

        private fun parseSum(): Double? {
            var value = parseProduct() ?: return null
            while (peek().isAdditive()) {
                val operator = readOperator()
                val right = parseProduct() ?: return null
                value = operator.applyTo(value, right)
            }
            return value
        }

        private fun parseProduct(): Double? {
            var value = parseOperand() ?: return null
            while (peek().isMultiplicative()) {
                val operator = readOperator()
                val right = parseOperand() ?: return null
                value = operator.applyTo(value, right)
            }
            return value
        }

        private fun parseOperand(): Double? {
            // A minus in operand position is unary: it negates the operand that follows.
            if (peek() == CalculatorOperator.SUBTRACT.symbol) {
                index++
                return parseOperand()?.unaryMinus()
            }
            if (peek() == LEFT_PARENTHESIS) {
                index++
                val value = parseSum() ?: return null
                if (peek() != RIGHT_PARENTHESIS) {
                    return null
                }
                index++
                return value
            }
            val start = index
            while (peek()?.isPartOfNumber() == true) {
                index++
            }
            // A number that was never there, or that is only a separator, is not an operand.
            return expression.substring(start, index).toDoubleOrNull()
        }

        private fun peek(): Char? = expression.getOrNull(index)

        private fun readOperator(): CalculatorOperator {
            val symbol = expression[index++]
            return CalculatorOperator.entries.first { it.symbol == symbol }
        }
    }
}

private fun Char.isPartOfNumber(): Boolean = isDigit() || this == DECIMAL_SEPARATOR

private fun Char?.isAdditive(): Boolean =
    this == CalculatorOperator.ADD.symbol || this == CalculatorOperator.SUBTRACT.symbol

private fun Char?.isMultiplicative(): Boolean =
    this == CalculatorOperator.MULTIPLY.symbol || this == CalculatorOperator.DIVIDE.symbol

private fun CalculatorOperator.applyTo(left: Double, right: Double): Double = when (this) {
    CalculatorOperator.ADD -> left + right
    CalculatorOperator.SUBTRACT -> left - right
    CalculatorOperator.MULTIPLY -> left * right
    CalculatorOperator.DIVIDE -> left / right
}
