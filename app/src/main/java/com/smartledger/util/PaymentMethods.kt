package com.smartledger.util

/**
 * 支付渠道（记账 / 筛选共用）
 */
object PaymentMethods {
    val PRESETS = listOf("微信", "支付宝", "云闪付", "现金", "银行卡", "抖音", "京东")

    const val CUSTOM_LABEL = "自定义"

    /** 是否为预设渠道（不含自定义文案） */
    fun isPreset(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return PRESETS.any { it == name }
    }
}
