package com.smartledger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlipayAutoDebitTest {
    private val alipay = "com.eg.android.AlipayGphone"
    private val title = "交易提醒"
    private val example = "你在示例商户有一笔3.50元的免密服务扣款支付。"

    @Test
    fun recognizesAutoDebitAndRequestsConfirmation() {
        assertTrue(NotificationParser.isAlipayAutoDebitNotice("$title $example", alipay))
        val payment = NotificationParser.parse(title, example, alipay)
        assertNotNull(payment)
        assertEquals(3.50, payment!!.amount, 0.001)
        assertEquals("expense", payment.type)
        assertEquals(ParseConfidence.UNCERTAIN, payment.confidence)
        assertEquals(NotificationParser.AUTO_DEBIT_CONFIRM_REASON, payment.uncertainReason)
    }

    @Test
    fun permitsAnUnrelatedCallToActionAfterTheNotice() {
        val payment = NotificationParser.parse(title, "$example 点击领取", alipay)
        assertEquals(ParseConfidence.UNCERTAIN, payment!!.confidence)
    }

    @Test
    fun rejectsOtherPackagesAndTitles() {
        assertFalse(NotificationParser.isAlipayAutoDebitNotice("$title $example", "com.tencent.mm"))
        assertNull(NotificationParser.parse("群聊", example, alipay))
    }

    @Test
    fun rejectsFutureFailedCancelledRefundAndPromotionalNotices() {
        val suffixes = listOf("即将扣款", "待扣款", "未扣款", "扣款失败", "支付失败", "已取消", "退款中", "优惠券推广")
        for (suffix in suffixes) {
            val content = "$example $suffix"
            assertFalse(NotificationParser.isAlipayAutoDebitNotice("$title $content", alipay))
            assertNull(NotificationParser.parse(title, content, alipay))
        }
    }

    @Test
    fun rejectsMalformedAndIncompleteAmounts() {
        val cases = listOf(
            "你在示例商户有一笔元的免密服务扣款支付。",
            "你在示例商户有一笔3.50元的免密服务。",
            "你在示例商户有一笔0元的免密服务扣款支付。"
        )
        for (content in cases) {
            assertNull(NotificationParser.parse(title, content, alipay))
        }
    }
}
