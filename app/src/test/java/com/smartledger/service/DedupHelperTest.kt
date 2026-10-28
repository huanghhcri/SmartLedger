package com.smartledger.service

import com.smartledger.data.db.entity.Category
import com.smartledger.util.CurrencyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupHelperTest {

    private val base = 1_761_000_000_000L

    private fun pay(
        cents: Long,
        method: String?,
        merchant: String? = null,
        time: Long = base,
        type: String = "expense",
        hint: String? = null,
    ) = DedupHelper.PaymentSignature(cents, type, merchant, method, time, hint)

    @Test
    fun jdAndIcbcSameMinuteAreOnePayment() {
        val cents = CurrencyUtil.toCents(118.07)
        val jd = pay(cents, "京东", "京东", base)
        val bank = pay(
            cents,
            "工商银行",
            "理财网银在线-平台商户",
            base + 40_000,
            hint = "京东",
        )
        assertTrue(DedupHelper.paymentsAreDuplicate(jd, bank))
        assertTrue(DedupHelper.paymentsAreDuplicate(bank, jd))
    }

    @Test
    fun bankArrivingWithEarlierTimestampStillMatches() {
        val cents = CurrencyUtil.toCents(118.07)
        val jd = pay(cents, "京东", "京东", base + 20_000)
        val bank = pay(cents, "工商银行", null, base, hint = "京东")
        assertTrue(DedupHelper.paymentsAreDuplicate(jd, bank))
    }

    @Test
    fun wechatAndIcbcSameAmountWithinThreeMinutesMatch() {
        val cents = CurrencyUtil.toCents(8.50)
        val wechat = pay(cents, "微信", null, base)
        val bank = pay(cents, "工商银行", "厦门多可立供应链管理", base + 50_000, hint = "微信")
        assertTrue(DedupHelper.paymentsAreDuplicate(wechat, bank))
    }

    @Test
    fun jdAndBankStillMatchWhenBankNoticeIsTwoMinutesLate() {
        val cents = CurrencyUtil.toCents(118.07)
        val jd = pay(cents, "京东", "京东", base)
        val bank = pay(cents, "工商银行", null, base + 120_000)
        assertTrue(DedupHelper.paymentsAreDuplicate(jd, bank))
    }

    @Test
    fun wechatPaidByBankCardMatchesWithoutGatewayKeyword() {
        val cents = CurrencyUtil.toCents(8.50)
        val wechat = pay(cents, "微信", "便利店", base)
        val bank = pay(cents, "工商银行", "另一家店", base + 100_000)
        assertTrue(DedupHelper.paymentsAreDuplicate(wechat, bank))
        assertTrue(DedupHelper.paymentsAreDuplicate(bank, wechat))
    }

    @Test
    fun alipayPaidByBankCardMatchesWithinThreeMinutes() {
        val cents = CurrencyUtil.toCents(26.0)
        val alipay = pay(cents, "支付宝", "盒马", base)
        val bank = pay(cents, "招商银行", "盒马", base + 150_000)
        assertTrue(DedupHelper.paymentsAreDuplicate(alipay, bank))
    }

    @Test
    fun wechatNoticeLabeledAsBankStillMatchesBankApp() {
        val cents = CurrencyUtil.toCents(8.50)
        // 微信正文写了储蓄卡名，支付方式被识别成工商银行，但渠道标记仍是微信
        val wechat = pay(cents, "工商银行", "便利店", base, hint = "微信")
        val bank = pay(cents, "工商银行", "便利店分店", base + 120_000)
        assertTrue(DedupHelper.paymentsAreDuplicate(wechat, bank))
    }

    @Test
    fun twoRealBankChargesWithoutWalletHintStaySeparate() {
        val cents = CurrencyUtil.toCents(8.50)
        val first = pay(cents, "工商银行", "便利店", base)
        val second = pay(cents, "工商银行", "超市", base + 90_000)
        assertFalse(DedupHelper.paymentsAreDuplicate(first, second))
    }

    @Test
    fun checkoutAndBankBeyondThreeMinutesStaySeparate() {
        val cents = CurrencyUtil.toCents(118.07)
        val jd = pay(cents, "京东", "京东", base)
        val bank = pay(cents, "工商银行", null, base + 181_000, hint = "京东")
        assertFalse(DedupHelper.paymentsAreDuplicate(jd, bank))
    }

    @Test
    fun afternoonReplayIsNotATimeWindowDuplicate() {
        val cents = CurrencyUtil.toCents(10.0)
        val morning = pay(cents, "微信", null, base)
        val afternoon = pay(cents, "微信", null, base + 6 * 60 * 60 * 1000)
        assertFalse(DedupHelper.paymentsAreDuplicate(morning, afternoon))
    }

    @Test
    fun conflictingChannelsAreNotMerged() {
        val cents = CurrencyUtil.toCents(10.0)
        val wechat = pay(cents, "微信", null, base, hint = "微信")
        val alipayBank = pay(cents, "工商银行", null, base + 60_000, hint = "支付宝")
        assertFalse(DedupHelper.paymentsAreDuplicate(wechat, alipayBank))
    }

    @Test
    fun twoQuickWechatPaymentsWithoutMerchantStaySeparate() {
        val cents = CurrencyUtil.toCents(15.0)
        val first = pay(cents, "微信", null, base)
        val second = pay(cents, "微信", null, base + 20_000)
        assertFalse(DedupHelper.paymentsAreDuplicate(first, second))
    }

    @Test
    fun sameChannelSummaryAndChildWithinFifteenSecondsMerge() {
        val cents = CurrencyUtil.toCents(15.0)
        val first = pay(cents, "微信", null, base)
        val second = pay(cents, "微信", null, base + 5_000)
        assertTrue(DedupHelper.paymentsAreDuplicate(first, second))
    }

    @Test
    fun differentAmountsNeverMerge() {
        val jd = pay(CurrencyUtil.toCents(118.07), "京东", "京东", base)
        val bank = pay(CurrencyUtil.toCents(118.08), "工商银行", null, base, hint = "京东")
        assertFalse(DedupHelper.paymentsAreDuplicate(jd, bank))
    }

    @Test
    fun refundDoesNotCancelTheOriginalExpense() {
        val cents = CurrencyUtil.toCents(118.07)
        val paid = pay(cents, "京东", "京东", base, type = "expense")
        val refund = pay(cents, "工商银行", null, base + 10_000, type = "income", hint = "京东")
        assertFalse(DedupHelper.paymentsAreDuplicate(paid, refund))
    }

    @Test
    fun refreshedNotificationUsesOriginalWhen() {
        val morning = base
        val afternoon = base + 6 * 60 * 60 * 1000
        assertEquals(morning, DedupHelper.stableEventTime(afternoon, morning, afternoon))
        assertEquals(afternoon, DedupHelper.stableEventTime(afternoon, afternoon, afternoon))
        assertEquals(afternoon, DedupHelper.stableEventTime(afternoon, 0L, afternoon))
    }

    @Test
    fun merchantPrefersShopOverGateway() {
        assertEquals(
            "京东",
            DedupHelper.chooseMerchant("理财网银在线-平台商户", "京东")
        )
        assertEquals(
            "京东",
            DedupHelper.chooseMerchant("京东", "理财网银在线-平台商户")
        )
        assertEquals(
            "厦门多可立供应链管理",
            DedupHelper.chooseMerchant(null, "厦门多可立供应链管理")
        )
        assertNull(DedupHelper.chooseMerchant("平台商户", "网银在线"))
    }

    @Test
    fun methodPrefersBankOverCheckout() {
        assertEquals("工商银行", DedupHelper.chooseMethod("京东", "工商银行"))
        assertEquals("工商银行", DedupHelper.chooseMethod("工商银行", "京东"))
    }

    @Test
    fun otherCategoryUpgradesWhenShopNameArrives() {
        val other = Category(id = 1, name = "其他", icon = "", color = 0, type = "expense")
        val shopping = Category(id = 2, name = "购物", icon = "", color = 0, type = "expense")
        assertEquals(
            2L,
            DedupHelper.upgradedCategoryId(1L, 2L, listOf(other, shopping))
        )
        assertEquals(
            2L,
            DedupHelper.upgradedCategoryId(2L, 1L, listOf(other, shopping))
        )
    }

    @Test
    fun channelHintReadsBankGateway() {
        assertEquals(
            "京东",
            DedupHelper.inferChannelHint("支出(消费理财网银在线-平台商户)118.07元")
        )
        assertEquals(
            "微信",
            DedupHelper.inferChannelHint("支出(财付通-厦门多可立供应链管理)8.50元")
        )
    }

    @Test
    fun noticeKeysKeepChannelHint() {
        val stored = DedupHelper.appendNoticeKeys(
            null,
            DedupHelper.withChannelHint(listOf("aaa"), "微信")
        )
        assertEquals("aaa,ch:微信", stored)
        assertEquals("微信", DedupHelper.channelHintFromStoredKey(stored))
        assertEquals(
            "aaa,ch:微信,bbb",
            DedupHelper.appendNoticeKeys(stored, listOf("bbb", "ch:支付宝"))
        )
    }
}
