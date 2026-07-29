package com.smartledger.util

import java.text.DecimalFormat

object CurrencyUtil {

    private val format = DecimalFormat("#,##0.00")
    private val formatNoDecimal = DecimalFormat("#,##0")

    fun format(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            formatNoDecimal.format(amount)
        } else {
            format.format(amount)
        }
    }

    fun formatWithSymbol(amount: Double, type: String): String {
        val symbol = if (type == "expense") "-" else "+"
        return "$symbol${format(amount)}"
    }
}
