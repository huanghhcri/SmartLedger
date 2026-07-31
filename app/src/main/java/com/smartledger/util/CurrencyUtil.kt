package com.smartledger.util

import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.roundToLong

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

    /** 金额转分，避免 (19.99 * 100).toLong() == 1998 的浮点误差 */
    fun toCents(amount: Double): Long = (amount * 100.0).roundToLong()

    /** 编辑框初始值：保留最多两位小数，不截成整数 */
    fun toEditableString(amount: Double): String {
        val cents = toCents(amount)
        return if (cents % 100L == 0L) {
            (cents / 100L).toString()
        } else {
            String.format(Locale.US, "%.2f", cents / 100.0)
        }
    }
}
