package com.smartledger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserWalletSuccessTest {
    @Test
    fun parsesExactPaymentSuccessForWechatAndAlipay() {
        val cases = listOf(
            Triple("com.tencent.mm", "微信支付", "支付成功 12.34元"),
            Triple("com.eg.android.AlipayGphone", "支付成功", "56.78元")
        )

        for ((packageName, title, content) in cases) {
            assertTrue(NotificationParser.hasStrongPaymentSignal("$title $content"))
            val payment = NotificationParser.parse(title, content, packageName)
            assertNotNull(payment)
            assertEquals("expense", payment!!.type)
            assertEquals(if (packageName == "com.tencent.mm") 12.34 else 56.78, payment.amount, 0.001)
            assertEquals(ParseConfidence.HIGH, payment.confidence)
        }
    }

    @Test
    fun ignoresOrdinaryMessagesContainingPaymentWords() {
        assertNull(NotificationParser.parse("群聊", "支付成功 12.34元", "com.tencent.mm"))
        assertNull(NotificationParser.parse("普通消息", "午饭 15 元", "com.tencent.mm"))
        assertNull(NotificationParser.parse("支付宝", "领取购物优惠券5元", "com.eg.android.AlipayGphone"))
    }

    @Test
    fun retainsExistingWalletFormats() {
        val wechat = NotificationParser.parse("微信支付", "已支付¥12.34", "com.tencent.mm")
        val alipay = NotificationParser.parse("支付宝", "支付成功，付款金额：¥56.78", "com.eg.android.AlipayGphone")
        assertEquals(12.34, wechat!!.amount, 0.001)
        assertEquals(56.78, alipay!!.amount, 0.001)
    }
}
