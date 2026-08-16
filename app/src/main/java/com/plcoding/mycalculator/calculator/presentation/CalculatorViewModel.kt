package com.plcoding.mycalculator.calculator.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plcoding.mycalculator.calculator.domain.CalculatorOperator
import com.plcoding.mycalculator.calculator.domain.DefaultExpressionEvaluator
import com.plcoding.mycalculator.calculator.domain.ExpressionEvaluator
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CalculatorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val expressionEvaluator: ExpressionEvaluator,
) : ViewModel() {

    private val _state = MutableStateFlow(CalculatorState())
    val state = _state.asStateFlow()

    init {
        updateExpression(savedStateHandle[KEY_EXPRESSION] ?: "")
    }

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.OnDigitClick -> enterDigit(action.digit)
            is CalculatorAction.OnOperatorClick -> enterOperator(action.operator)
            CalculatorAction.OnDecimalClick -> enterDecimalSeparator()
            CalculatorAction.OnLeftParenthesisClick -> openGroup()
            CalculatorAction.OnRightParenthesisClick -> closeGroup()
            CalculatorAction.OnDeleteClick -> deleteLastCharacter()
            CalculatorAction.OnClearClick -> clear()
            CalculatorAction.OnCalculateClick -> calculate()
        }
    }

    private fun enterDigit(digit: Int) {
        val expression = state.value.expression.withImplicitMultiplication()
        val currentNumber = expression.currentNumber()
        if (currentNumber.count { it.isDigit() } >= MAX_NUMBER_LENGTH) {
            return
        }

        // A number never keeps a leading zero: "0" becomes "7", but "0." stays "0.7".
        val updatedExpression = if (currentNumber == "0") {
            expression.dropLast(1) + digit
        } else {
            expression + digit
        }
        updateExpression(updatedExpression)
    }

    private fun enterDecimalSeparator() {
        val expression = state.value.expression.withImplicitMultiplication()
        val currentNumber = expression.currentNumber()
        if (currentNumber.contains(DECIMAL_SEPARATOR)) {
            return
        }

        // Starting a number with the separator implies a leading zero: "×." becomes "×0.".
        val updatedExpression = if (currentNumber.isEmpty()) {
            expression + "0" + DECIMAL_SEPARATOR
        } else {
            expression + DECIMAL_SEPARATOR
        }
        updateExpression(updatedExpression)
    }

    private fun enterOperator(operator: CalculatorOperator) {
        // A number is never left dangling on a separator: "5." becomes "5+".
        val expression = state.value.expression.trimEnd(DECIMAL_SEPARATOR)

        // Tapping a second operator swaps it out instead of stacking: "5+" becomes "5×".
        val operand = expression.dropLastWhile { CalculatorOperator.isOperator(it) }

        // At the start of an expression or of a group, the only operator that makes sense is
        // a minus, marking the operand that follows as negative.
        val isOperandStart = operand.isEmpty() || operand.last() == LEFT_PARENTHESIS
        if (isOperandStart && operator != CalculatorOperator.SUBTRACT) {
            return
        }
        if (operand.length >= MAX_EXPRESSION_LENGTH) {
            return
        }
        updateExpression(operand + operator.symbol)
    }

    private fun openGroup() {
        // A group written against a finished value multiplies with it: "5(" becomes "5×(".
        val expression = state.value.expression.trimEnd(DECIMAL_SEPARATOR)
        val prefix = if (expression.endsOnCompleteOperand()) {
            expression + CalculatorOperator.MULTIPLY.symbol
        } else {
            expression
        }
        if (prefix.length >= MAX_EXPRESSION_LENGTH) {
            return
        }
        updateExpression(prefix + LEFT_PARENTHESIS)
    }

    private fun closeGroup() {
        val expression = state.value.expression.trimEnd(DECIMAL_SEPARATOR)
        if (!expression.canCloseGroup()) {
            return
        }
        updateExpression(expression + RIGHT_PARENTHESIS)
    }

    private fun deleteLastCharacter() {
        updateExpression(state.value.expression.dropLast(1))
    }

    private fun clear() {
        updateExpression("")
    }

    private fun calculate() {
        // The result is already computed as a live preview — pressing equals just commits it,
        // so the user can keep calculating with it.
        val result = state.value.result ?: return
        updateExpression(result)
    }

    private fun updateExpression(expression: String) {
        savedStateHandle[KEY_EXPRESSION] = expression
        _state.update {
            it.copy(
                expression = expression,
                result = expression.previewResult(),
            )
        }
    }

    private fun String.previewResult(): String? {
        // A lone number is not a calculation, so there is nothing to preview yet. The leading
        // character is skipped because a leading minus only makes the number negative.
        val hasOperation = drop(1).any { CalculatorOperator.isOperator(it) }
        if (!hasOperation) {
            return null
        }
        return expressionEvaluator.evaluate(this)?.format()
    }

    private fun Double.format(): String = BigDecimal.valueOf(this)
        .setScale(MAX_RESULT_DECIMALS, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('-', CalculatorOperator.SUBTRACT.symbol)

    companion object {
        private const val KEY_EXPRESSION = "expression"
        private const val MAX_NUMBER_LENGTH = 12
        private const val MAX_EXPRESSION_LENGTH = 40
        private const val MAX_RESULT_DECIMALS = 8

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CalculatorViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    expressionEvaluator = DefaultExpressionEvaluator(),
                )
            }
        }
    }
}
