package com.plcoding.mycalculator.calculator.domain

/**
 * An evaluator that never produces a result, so the UI never shows a preview and the equals
 * key stays disabled. The app itself runs on [DefaultExpressionEvaluator]; this one is kept
 * as the test double for exercising the keypad rules on their own.
 */
class NoOpExpressionEvaluator : ExpressionEvaluator {
    override fun evaluate(expression: String): Double? = null
}
