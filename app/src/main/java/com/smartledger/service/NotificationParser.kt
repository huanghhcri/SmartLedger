package com.smartledger.service

enum class ParseConfidence {
    /** 明确支付/动账，可静默入账 */
    HIGH,
    /** 截断、模糊、疑似营销等，需用户确认 */
    UNCERTAIN
}

data class ParsedPayment(
    val amount: Double,
    val merchant: String?,
    val paymentMethod: String,
    val notificationKey: String,
    val type: String, // "expense" or "income"
    val confidence: ParseConfidence = ParseConfidence.HIGH,
    /** 需确认时的简短原因（展示用） */
    val uncertainReason: String? = null,
    /** 通知原文片段，确认页展示 */
    val rawSnippet: String? = null
)

private data class AmountHit(
    val value: Double,
    /** 带「元/￥」或明确小数，较可信 */
    val isClear: Boolean
)

object NotificationParser {

    /** 金额捕获：整数或最多两位小数；兼容通知末尾「35....」截断 */
    private const val AMOUNT = "([\\d]+(?:\\.[\\d]{1,2})?)"

    // ═══════════════════════════════════════════════════════
    // 支出关键词
    // ═══════════════════════════════════════════════════════
    private val EXPENSE_KEYWORDS = listOf(
        "付款", "支付", "消费", "支出", "转出", "扣款", "已付", "成功付款",
        "购买", "缴费", "还款", "充值", "已扣", "交易支出", "动账"
    )

    // ═══════════════════════════════════════════════════════
    // 收入关键词（退款 + 转入 + 收款）
    // ═══════════════════════════════════════════════════════
    private val INCOME_KEYWORDS = listOf(
        "退款", "退回", "到账", "收款", "转入", "已收", "存入",
        "收入", "入账", "已到账", "退款成功", "红包", "奖励",
        "工资", "奖金", "利息", "返现", "报销"
    )

    // ═══════════════════════════════════════════════════════
    // 微信支付
    // ═══════════════════════════════════════════════════════
    private val wechatExpensePatterns = listOf(
        Regex("付款[￥¥]([\\d.]+)"),
        Regex("向(.+?)付款[￥¥]([\\d.]+)"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("已支付[￥¥]([\\d.]+)"),              // 已支付¥0.01 / [2条]微信支付：已支付¥648.00
        Regex("微信支付[：:]\\s*已支付[￥¥]([\\d.]+)"),
        Regex("支付[￥¥]([\\d.]+)"),                 // 支付¥0.01（须带￥/¥，避免「支付5元干饭」类）
        Regex("微信支付.*?已支付[￥¥]([\\d.]+)"),
        // 付款码：付款码付款成功 / 付款码已支付¥16.00
        Regex("付款码.*?已支付[￥¥]([\\d.]+)"),
        Regex("付款码.*?付款[￥¥]?([\\d.]+)"),
        Regex("已付款[￥¥]([\\d.]+)"),
        Regex("付款成功[￥¥]?([\\d.]+)")
        // 已移除：支付X元 / 支出X元 / 消费X元 —— 易被公众号营销命中
    )

    private val wechatIncomePatterns = listOf(
        Regex("收款[￥¥]([\\d.]+)"),
        Regex("退款[￥¥]([\\d.]+)"),
        Regex("退款.*?[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入([\\d.]+)元"),
        Regex("红包[￥¥]([\\d.]+)")
    )

    // ═══════════════════════════════════════════════════════
    // 支付宝
    // ═══════════════════════════════════════════════════════
    private val alipayExpensePatterns = listOf(
        Regex("付款([\\d.]+)元"),
        Regex("成功付款([\\d.]+)"),
        Regex("向(.+?)付款([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出"),          // 你有一笔42.75元的支出
        Regex("支出([\\d.]+)元"),                // 支出42.75元
        Regex("扣款([\\d.]+)元"),
        Regex("([\\d.]+)元的支出"),              // 42.75元的支出
        Regex("支付宝.*?[￥¥]([\\d.]+)"),
        Regex("[￥¥]([\\d.]+).*支出")             // ¥42.75支出
        // 注意：不要匹配「积分…X元」，领积分/积分到账会被误记为支出
    )

    private val alipayIncomePatterns = listOf(
        Regex("退款([\\d.]+)元"),
        Regex("退款.*?([\\d.]+)元"),
        Regex("到账([\\d.]+)元"),
        Regex("收款([\\d.]+)元"),
        Regex("转入([\\d.]+)元"),
        Regex("收入([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的收入"),
        Regex("红包.*?([\\d.]+)元")
    )

    // ═══════════════════════════════════════════════════════
    // 云闪付
    // ═══════════════════════════════════════════════════════
    private val unionpayExpensePatterns = listOf(
        Regex("消费人民币([\\d.]+)元"),
        Regex("付款[￥¥]([\\d.]+)"),
        Regex("支出([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("扣款([\\d.]+)元")
    )

    private val unionpayIncomePatterns = listOf(
        Regex("退款.*?([\\d.]+)元"),
        Regex("到账.*?([\\d.]+)元"),
        Regex("转入.*?([\\d.]+)元"),
        Regex("收入([\\d.]+)元"),
        Regex("收款([\\d.]+)元")
    )

    // ═══════════════════════════════════════════════════════
    // 抖音（需真机用 logcat PaymentListener 校对）
    // ═══════════════════════════════════════════════════════
    private val douyinExpensePatterns = listOf(
        Regex("支付[￥¥]([\\d.]+)"),
        Regex("已支付[￥¥]([\\d.]+)"),
        Regex("付款[￥¥]([\\d.]+)"),
        Regex("消费([\\d.]+)元"),
        Regex("支出([\\d.]+)元"),
        Regex("订单.*?[￥¥]([\\d.]+)"),
        Regex("抖音支付.*?[￥¥]([\\d.]+)")
    )

    private val douyinIncomePatterns = listOf(
        Regex("退款[￥¥]([\\d.]+)"),
        Regex("退款.*?[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入([\\d.]+)元")
    )

    // ═══════════════════════════════════════════════════════
    // 工商银行
    // ═══════════════════════════════════════════════════════
    private val icbcExpensePatterns = listOf(
        Regex("支出人民币${AMOUNT}元"),
        Regex("消费人民币${AMOUNT}元"),
        Regex("扣款人民币${AMOUNT}元"),
        Regex("支出[￥¥]$AMOUNT"),
        Regex("消费[￥¥]$AMOUNT"),
        Regex("交易金额[￥¥]$AMOUNT"),
        // 动账：支出(消费抖音支付-商户)35....（截断无「元」、尾随省略号）
        Regex("支出\\([^)]{0,120}?\\)\\s*$AMOUNT"),
        Regex("消费\\([^)]{0,120}?\\)\\s*$AMOUNT"),
        // 括号未闭合截断：支出(消费抖音…35....（注意勿写未转义的 ) ）
        Regex("支出\\([^\\d]{0,100}?$AMOUNT"),
        Regex("支出${AMOUNT}元"),
        Regex("消费${AMOUNT}元"),
        Regex("一笔${AMOUNT}元的支出")
    )

    private val icbcIncomePatterns = listOf(
        Regex("收入人民币${AMOUNT}元"),
        Regex("存入人民币${AMOUNT}元"),
        Regex("到账人民币${AMOUNT}元"),
        Regex("转入人民币${AMOUNT}元"),
        Regex("收入\\([^)]{0,120}\\)\\s*${AMOUNT}元?"),
        Regex("退款.*?${AMOUNT}元"),
        Regex("收入[￥¥]$AMOUNT"),
        Regex("到账[￥¥]$AMOUNT"),
        Regex("收入${AMOUNT}元"),
        Regex("到账${AMOUNT}元")
    )

    // ═══════════════════════════════════════════════════════
    // 邮政储蓄银行
    // ═══════════════════════════════════════════════════════
    private val psbcExpensePatterns = listOf(
        Regex("支出人民币([\\d.]+)元"),
        Regex("消费人民币([\\d.]+)元"),
        Regex("扣款人民币([\\d.]+)元"),
        Regex("支出[￥¥]([\\d.]+)"),
        Regex("消费[￥¥]([\\d.]+)"),
        Regex("转出[￥¥]([\\d.]+)"),
        Regex("支出.*?([\\d.]+)元"),                 // 支出(XXX)0.01元
        Regex("消费.*?([\\d.]+)元"),
        Regex("扣款.*?([\\d.]+)元"),
        Regex("支出([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出")
    )

    private val psbcIncomePatterns = listOf(
        Regex("收入人民币([\\d.]+)元"),
        Regex("存入人民币([\\d.]+)元"),
        Regex("到账人民币([\\d.]+)元"),
        Regex("转入人民币([\\d.]+)元"),
        Regex("退款.*?([\\d.]+)元"),
        Regex("收入[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入.*?([\\d.]+)元"),
        Regex("到账.*?([\\d.]+)元"),
        Regex("收入([\\d.]+)元"),
        Regex("到账([\\d.]+)元")
    )

    // ═══════════════════════════════════════════════════════
    // 其他银行通用
    // ═══════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════
    // 京东 / 京东金融（银行卡支付确认）
    // ═══════════════════════════════════════════════════════
    // 京东/淘宝等：只认明确支付确认，禁止裸「xxx元 / ¥」以免营销文案误记
    private val jdExpensePatterns = listOf(
        Regex("使用【.+?】支付([\\d.]+)"),          // 使用【工商银行储蓄卡(7619)】支付16.90
        Regex("成功支付([\\d.]+)元?"),
        Regex("付款成功.*?([\\d.]+)元"),
        Regex("已支付[￥¥]?([\\d.]+)"),
        Regex("支付成功.*?([\\d.]+)元"),
        Regex("付款([\\d.]+)元"),
        Regex("支付([\\d.]+)元")
        // 不再：支付([\d.]+)、[￥¥]([\d.]+) —— 会吃到「省钱金额已达648.13元」
    )

    private val jdIncomePatterns = listOf(
        Regex("退款成功.*?([\\d.]+)元"),
        Regex("退款([\\d.]+)元"),
        Regex("退款.*?([\\d.]+)元")
        // 不认裸「到账」：提现提醒/未领取打款也会带「到账」
    )

    private val bankExpensePatterns = listOf(
        Regex("消费人民币${AMOUNT}元"),
        Regex("使用【.+?】支付$AMOUNT"),
        Regex("支出\\([^)]{0,120}?\\)\\s*$AMOUNT"),
        Regex("消费\\([^)]{0,120}?\\)\\s*$AMOUNT"),
        Regex("支出\\([^\\d]{0,100}?$AMOUNT"),
        Regex("消费[￥¥]$AMOUNT"),
        Regex("交易金额[￥¥]?$AMOUNT"),
        Regex("转出[￥¥]$AMOUNT"),
        Regex("扣款[￥¥]$AMOUNT"),
        Regex("支出${AMOUNT}元"),
        Regex("消费${AMOUNT}元"),
        Regex("一笔${AMOUNT}元的支出")
        // 已移除过宽的：支付X / 金额X / 支出.*?X元 —— 易误记营销短信
    )

    private val bankIncomePatterns = listOf(
        Regex("收入人民币${AMOUNT}元"),
        Regex("存入人民币${AMOUNT}元"),
        Regex("到账人民币${AMOUNT}元"),
        Regex("收入\\([^)]{0,120}\\)\\s*${AMOUNT}元?"),
        Regex("退款.*?${AMOUNT}元"),
        Regex("转入[￥¥]$AMOUNT"),
        Regex("到账[￥¥]$AMOUNT"),
        Regex("收入${AMOUNT}元"),
        Regex("到账${AMOUNT}元")
    )

    // ═══════════════════════════════════════════════════════
    // 商户名提取
    // ═══════════════════════════════════════════════════════
    private val merchantPatterns = listOf(
        Regex("商户[：:]\\s*(.+?)(?:\\s|$)"),
        Regex("收款方[：:]\\s*(.+?)(?:\\s|$)"),
        Regex("向(.+?)付款"),
        Regex("在(.+?)消费"),
        Regex("于(.+?)消费"),
        // 工行：支出(消费抖音支付-商户名) / 支出(消费网银在线-商户)
        Regex("支出\\((?:消费)?(?:抖音支付-|网银在线-|财付通-|支付宝-|微信支付-|云闪付-)?(.+?)\\)"),
        Regex("收入\\((?:退款)?(?:抖音支付-|网银在线-|财付通-|支付宝-|微信支付-|云闪付-)?(.+?)\\)"),
        Regex("消费\\((?:抖音支付-|网银在线-)?(.+?)\\)"),
        Regex("退款至(.+?)(?:\\s|$)"),
        Regex("来自(.+?)(?:\\s|$)"),
        Regex("账号(.+?)(?:扣款|支出|消费)"),
        Regex("银行卡/账号(.+?)(?:扣款|支出|消费)")
    )

    private val creditProductKeywords = listOf(
        "白条", "花呗", "借呗", "金条", "信用购", "有钱花", "分期乐", "任性付"
    )

    /** 电商 / 营销类 App：易把「省钱金额」等误当成支付 */
    private fun isShoppingApp(packageName: String): Boolean {
        return packageName.contains("jingdong") ||
            packageName.contains("jd.jr") ||
            packageName.contains("jdlite") ||
            packageName.contains("taobao") ||
            packageName.contains("tmall")
    }

    /**
     * 微信 / 支付宝 / 抖音：公众号、企业号、营销推送极多，禁止「见元就记」
     */
    private fun isStrictChatPayApp(packageName: String): Boolean {
        return packageName.contains("tencent.mm") ||
            packageName.contains("AlipayGphone") ||
            packageName.contains("ugc.aweme") ||
            packageName.contains("ugc.live")
    }

    /**
     * 明确的支付/退款确认信号。
     * 注意：不能仅因出现「微信支付」+「元」就放行（企业号「每天5元干饭」会误伤）。
     */
    fun hasStrongPaymentSignal(text: String): Boolean {
        if (text.contains("使用【") && text.contains("支付")) return true
        if (text.contains("成功支付") || text.contains("支付成功")) return true
        if (text.contains("付款成功") || text.contains("成功付款")) return true
        if (text.contains("已支付") || text.contains("已付款")) return true
        if (Regex("付款[￥¥]").containsMatchIn(text)) return true
        if (Regex("支付[￥¥]").containsMatchIn(text)) return true
        if (Regex("向.+?付款[￥¥]").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的支出").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的收入").containsMatchIn(text)) return true
        if (Regex("收款[￥¥]").containsMatchIn(text)) return true
        // 付款码：常与「已支付¥xx」或「付款成功」一起出现
        if (text.contains("付款码") && (
                text.contains("已支付") || text.contains("付款成功") ||
                    text.contains("成功付款") || Regex("[￥¥]\\s*[\\d.]+").containsMatchIn(text)
                )
        ) {
            return true
        }
        // 微信支付：必须带确认动词，不能仅有「元」
        if (text.contains("微信支付") && (
                text.contains("已支付") || text.contains("付款") ||
                    text.contains("收款") || text.contains("退款") ||
                    Regex("支付[￥¥]").containsMatchIn(text)
                )
        ) {
            return true
        }
        if (text.contains("支付宝") && (
                text.contains("付款") || text.contains("成功付款") ||
                    text.contains("的支出") || text.contains("的收入") ||
                    text.contains("退款")
                )
        ) {
            return true
        }
        // 退款入账（排除提现提醒）
        if ((text.contains("退款成功") || text.contains("退款到账") ||
                (text.contains("退款") && (text.contains("元") || text.contains("¥") || text.contains("￥")))) &&
            !text.contains("提现") && !text.contains("尚未处理")
        ) {
            return true
        }
        // 银行动账
        if (text.contains("动账") || text.contains("支出(") || text.contains("收入(")) return true
        if ((text.contains("储蓄卡") || text.contains("借记卡") || text.contains("信用卡")) &&
            (text.contains("支付") || text.contains("消费") || text.contains("扣款"))
        ) {
            return true
        }
        return false
    }

    /**
     * 营销 / 额度 / 物流 / 公众号推送等非真实支付通知。
     * 例：京东省钱金额；微信企业号「每天5元干饭」「5元请你吃外卖」
     */
    fun isPromotionalOrNonPayment(text: String): Boolean {
        // 银行真实动账优先放行
        if (hasBankLedgerSignal(text)) return false
        if (hasStrongPaymentSignal(text)) return false
        val noise = listOf(
            "省钱金额", "已省回", "省回", "倍会费", "PLUS会员", "会员已省",
            "省钱明细", "点击查看", "相当于",
            "提现提醒", "尚未处理", "将于", "过期", "现金打款",
            "可用额度", "信用额度", "剩余额度", "账户剩余", "剩余可用额度",
            "验证码", "登录验证", "登录成功", "账单日", "账单已出",
            "最低还款", "分期付款", "安全锁", "网银登录", "手机银行登录",
            // 银行营销 / 非动账（无「动账/支出(」时会被拦截）
            "专属福利", "限时优惠", "年化利率", "立即申请", "点击申请",
            "贷款额度", "借款额度", "预估可借", "尊享礼遇", "积分兑换",
            "账户余额", "当前余额", "可用余额", "余额变动提醒",
            "还款提醒", "还款日", "即将到期", "逾期提醒",
            "开户成功", "绑卡成功", "签约成功", "开通成功",
            "物流", "配送", "已发货", "待收货", "下单关怀", "签收",
            "优惠券", "领券", "领积分", "积分到账", "领券福利", "福利官",
            "猜一局", "大家都在猜", "周末大家都在",
            "提醒：账户", "签到领", "每日签到",
            "干饭", "请你吃", "今日已上新", "已上新", "外卖券",
            "点击领取", "立即领取", "限时领取", "天天特价"
        )
        if (noise.any { text.contains(it) }) return true
        if (Regex("每天\\d+(\\.\\d+)?元").containsMatchIn(text)) return true
        if (Regex("\\d+(\\.\\d+)?元请你").containsMatchIn(text)) return true
        if (Regex("\\d+(\\.\\d+)?元干饭").containsMatchIn(text)) return true
        return false
    }

    /** 银行 App / 动账类通知的真实账务信号（防营销误记） */
    fun hasBankLedgerSignal(text: String): Boolean {
        if (text.contains("动账")) return true
        if (text.contains("支出(") || text.contains("收入(") || text.contains("消费(")) return true
        if (text.contains("交易金额") || text.contains("支出人民币") || text.contains("收入人民币")) return true
        if (text.contains("消费人民币") || text.contains("扣款人民币") || text.contains("存入人民币")) return true
        if (Regex("尾号\\*{0,4}\\d{0,4}.*(?:支出|收入|消费|扣款)").containsMatchIn(text)) return true
        if (Regex("尾号\\d{4}.*(?:支出|收入|消费|扣款)").containsMatchIn(text)) return true
        if (Regex("(?:支出|收入|消费)\\([^)]*\\)\\s*[\\d]").containsMatchIn(text)) return true
        return false
    }

    private fun isBankApp(packageName: String): Boolean {
        return packageName.contains("icbc") || packageName.contains("psbc") ||
            packageName.contains("chinapost") || packageName.contains("ccb") ||
            packageName.contains("bocmbci") || packageName.contains("bocsoft") ||
            packageName.contains("abchina") || packageName.contains("bankabc") ||
            packageName.contains("cmb.pb") || packageName.contains("cmb.b2c") ||
            packageName.contains("pingan") || packageName.contains("spdb") ||
            packageName.contains("cmbc") || packageName.contains("cebbank") ||
            packageName.contains("bankcomm") || packageName.contains("bocomm") ||
            packageName.contains("cib")
    }

    /** 是否像「[N条]」聚合摘要（金额可能在同组子通知里） */
    fun looksLikeGroupedSummary(text: String): Boolean {
        return Regex("[\\[［【(（]?\\d+条[\\]］】)）]?").containsMatchIn(text) ||
            Regex("共\\d+条").containsMatchIn(text) ||
            Regex("\\d+条新(消息|通知|提醒)").containsMatchIn(text)
    }

    /**
     * 正文里是否已有可解析的明确支付/动账金额。
     * 覆盖微信/支付宝/云闪付/抖音/电商收银台/银行卡动账等。
     */
    fun hasPayableAmountSignal(text: String): Boolean {
        // 微信 / 支付宝 / 通用
        if (Regex("已支付\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("付款\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("支付\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("成功付款\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的支出").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的收入").containsMatchIn(text)) return true
        if (Regex("收款\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        // 云闪付 / 电商收银台
        if (Regex("使用【.+?】支付\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("成功支付\\s*[\\d.]+").containsMatchIn(text)) return true
        // 银行卡动账（含截断无「元」）
        if (Regex("支出\\([^)]{0,120}\\)\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("收入\\([^)]{0,120}\\)\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("消费\\([^)]{0,120}\\)\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("(?:支出|消费|收入|扣款|存入)人民币\\s*[\\d.]+").containsMatchIn(text)) return true
        if (text.contains("动账") && Regex("[\\d]+(?:\\.[\\d]{1,2})?").containsMatchIn(text)) return true
        if (Regex("交易金额\\s*[￥¥]?\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("(?:转出|转入|到账|扣款)\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        return false
    }

    /** 子通知是否像真实账务（用于从聚合组里挑选） */
    fun looksLikeLedgerChild(text: String): Boolean {
        return hasPayableAmountSignal(text) ||
            hasStrongPaymentSignal(text) ||
            hasBankLedgerSignal(text)
    }

    /** 去掉微信等聚合前缀：[2条] / ［10条］ / 【10条】 */
    fun normalizeNotificationText(text: String): String {
        var s = text
            .replace(Regex("[\\[［【]\\d+条[\\]］】]"), "")
            .replace(Regex("[（(]\\d+条[）)]"), "")
            .replace(Regex("(?<!\\d)\\d+条(?=微信支付|支付宝|已支付|付款)"), "")
        // 全角数字 → 半角，避免「已支付¥１６.００」无法识别
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                if (ch in '\uFF10'..'\uFF19') (ch - '\uFF10' + '0'.code).toChar() else ch
            )
        }
        s = sb.toString()
        // 统一常见货币符
        s = s.replace('￥', '¥').replace('＄', '¥')
        return s.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * 是否为白条/花呗等信贷支付（应跳过记账）。
     * 使用【工商银行储蓄卡】等真实银行卡支付不会命中。
     */
    fun isCreditProductPayment(text: String): Boolean {
        // 【】内写明信贷工具
        Regex("【([^】]+)】").findAll(text).forEach { m ->
            val tool = m.groupValues[1]
            if (creditProductKeywords.any { tool.contains(it) }) return true
        }
        // 明确「用白条/花呗支付」且未出现银行卡/储蓄卡/尾号卡
        val hasBankCard = text.contains("储蓄卡") || text.contains("借记卡") ||
            text.contains("信用卡") || text.contains("银行卡") ||
            Regex("尾号\\d{4}").containsMatchIn(text) ||
            Regex("【[^】]*银行[^】]*】").containsMatchIn(text)
        if (hasBankCard) return false

        val creditPayHints = listOf(
            "白条支付", "使用白条", "花呗支付", "使用花呗",
            "借呗", "金条支付", "使用金条", "信用购"
        )
        return creditPayHints.any { text.contains(it) } ||
            (creditProductKeywords.any { text.contains(it) } &&
                (text.contains("支付") || text.contains("付款") || text.contains("消费")))
    }

    // ═══════════════════════════════════════════════════════
    // 支付方式识别（优先正文里的真实银行卡，而非 App 包名）
    // ═══════════════════════════════════════════════════════
    private fun identifyPaymentMethod(packageName: String, text: String): String {
        // 1) 【工商银行储蓄卡(7619)】→ 工商银行
        Regex("【([^】]+)】").find(text)?.groupValues?.getOrNull(1)?.let { tool ->
            bankNameFromTool(tool)?.let { return it }
        }
        // 2) 正文银行名
        bankNameFromTool(text)?.let { return it }

        return when {
            packageName.contains("tencent.mm") -> "微信"
            packageName.contains("AlipayGphone") -> "支付宝"
            packageName.contains("unionpay") -> "云闪付"
            packageName.contains("ugc.aweme") || packageName.contains("ugc.live") -> "抖音"
            packageName.contains("icbc") -> "工商银行"
            packageName.contains("chinapost") || packageName.contains("psbc") -> "邮政储蓄"
            packageName.contains("ccb") -> "建设银行"
            packageName.contains("bocmbci") || packageName.contains("bocsoft") -> "中国银行"
            packageName.contains("abcpocket") || packageName.contains("abchina") || packageName.contains("bankabc") -> "农业银行"
            packageName.contains("cmb") -> "招商银行"
            packageName.contains("pingan") -> "平安银行"
            packageName.contains("jd.jr") || packageName.contains("jingdong") -> "京东"
            text.contains("微信") -> "微信"
            text.contains("支付宝") -> "支付宝"
            text.contains("云闪付") || text.contains("银联") -> "云闪付"
            else -> "银行卡"
        }
    }

    private fun bankNameFromTool(tool: String): String? {
        return when {
            tool.contains("工商银行") || tool.contains("工行") -> "工商银行"
            tool.contains("建设银行") || tool.contains("建行") -> "建设银行"
            tool.contains("中国银行") || (tool.contains("中行") && !tool.contains("中信")) -> "中国银行"
            tool.contains("农业银行") || tool.contains("农行") -> "农业银行"
            tool.contains("招商银行") || tool.contains("招行") -> "招商银行"
            tool.contains("邮政储蓄") || tool.contains("邮储") -> "邮政储蓄"
            tool.contains("浦发银行") || tool.contains("浦发") -> "浦发银行"
            tool.contains("民生银行") -> "民生银行"
            tool.contains("光大银行") -> "光大银行"
            tool.contains("兴业银行") -> "兴业银行"
            tool.contains("平安银行") -> "平安银行"
            tool.contains("中信银行") -> "中信银行"
            tool.contains("交通银行") || tool.contains("交行") -> "交通银行"
            else -> null
        }
    }

    /** 遮罩卡号末四位，避免被金额正则误吃 */
    private fun maskCardTailNumbers(text: String): String {
        return text
            .replace(Regex("尾号\\d{4}"), "尾号****")
            .replace(Regex("(?<=储蓄卡|信用卡|借记卡|银行卡)\\s*[(（]\\d{4}[)）]"), "")
            .replace(Regex("【([^】]*?)[(（]\\d{4}[)）]】"), "【$1】")
    }

    /** 从捕获组清洗金额：去掉「35....」尾随点号 */
    private fun parseAmountToken(raw: String): Double? {
        val cleaned = raw.trim().trimEnd('.', '…', '。', ' ')
        val m = Regex("^(\\d{1,9})(\\.\\d{1,2})?").find(cleaned) ?: return null
        val value = m.value.toDoubleOrNull() ?: return null
        if (value <= 0 || value >= 10_000_000) return null
        return value
    }

    /**
     * 按正则提金额；跳过卡号末四位；优先带小数或带「元」的结果。
     * isClear=false：截断无「元」、仅兜底命中等，应走用户确认。
     */
    private fun extractAmount(
        maskedText: String,
        patterns: List<Regex>,
        rawText: String
    ): AmountHit? {
        var fallback: AmountHit? = null
        for (pattern in patterns) {
            val matches = pattern.findAll(maskedText)
            for (match in matches) {
                val amountStr = match.groupValues.lastOrNull {
                    it.isNotBlank() && it.first().isDigit()
                } ?: continue
                val value = parseAmountToken(amountStr) ?: continue
                if (isCardTailAmount(value, rawText, amountStr.takeWhile { it.isDigit() || it == '.' })) {
                    continue
                }

                val matchedWhole = match.value
                val hasYuanOrSymbol = matchedWhole.contains("元") ||
                    matchedWhole.contains("￥") || matchedWhole.contains("¥") ||
                    amountStr.contains('.')
                val truncatedDots = rawText.contains("....") || rawText.contains("…") ||
                    Regex("\\d\\.{2,}").containsMatchIn(rawText)
                if (hasYuanOrSymbol && !truncatedDots) {
                    return AmountHit(value, isClear = true)
                }
                // 动账截断无「元」：支出(...)35.... —— 可解析但不确定
                if (matchedWhole.contains("支出(") || matchedWhole.contains("收入(") ||
                    matchedWhole.contains("消费(")
                ) {
                    return AmountHit(value, isClear = false)
                }
                if (hasYuanOrSymbol) {
                    return AmountHit(value, isClear = !truncatedDots)
                }
                if (fallback == null) fallback = AmountHit(value, isClear = false)
            }
        }
        return fallback
    }

    /**
     * 全渠道统一置信度：明确收支 → HIGH 静默入账；其余 → UNCERTAIN 弹确认。
     * 适用于微信 / 支付宝 / 抖音 / 云闪付 / 京东淘宝 / 各银行等。
     */
    fun assessConfidence(
        rawText: String,
        amountClear: Boolean,
        typeAmbiguous: Boolean,
        type: String
    ): Pair<ParseConfidence, String?> {
        if (!amountClear) {
            return ParseConfidence.UNCERTAIN to "金额可能被截断或不完整"
        }
        if (rawText.contains("....") || rawText.contains("…") ||
            Regex("\\d\\.{2,}").containsMatchIn(rawText)
        ) {
            return ParseConfidence.UNCERTAIN to "通知内容疑似被截断"
        }
        if (typeAmbiguous) {
            return ParseConfidence.UNCERTAIN to "无法确定是收入还是支出"
        }

        // 券 / 满减 / 福利：一律先确认（即使偶发带「支付」字样）
        val couponNoise = listOf(
            "优惠券", "红包券", "满减", "神券", "外卖券", "抵用券",
            "领券", "已到账一张", "待使用", "点击领取", "限时领取",
            "省钱金额", "已省回"
        )
        if (couponNoise.any { rawText.contains(it) }) {
            return ParseConfidence.UNCERTAIN to "疑似优惠/券类推送"
        }
        if ((rawText.contains("券") || rawText.contains("福利")) &&
            !hasStrongPaymentSignal(rawText)
        ) {
            return ParseConfidence.UNCERTAIN to "疑似营销推送"
        }

        // 明确支出信号（全渠道）
        val clearExpense = hasStrongPaymentSignal(rawText) ||
            (hasBankLedgerSignal(rawText) && (
                rawText.contains("支出") || rawText.contains("消费") ||
                    rawText.contains("扣款") || rawText.contains("转出") ||
                    rawText.contains("动账")
                ) && type == "expense")

        // 明确收入信号（全渠道）
        val clearIncome = (
            rawText.contains("退款成功") || rawText.contains("退款到账") ||
                (rawText.contains("退款") && (rawText.contains("元") || rawText.contains("¥") || rawText.contains("￥"))) ||
                Regex("收款[￥¥]").containsMatchIn(rawText) ||
                Regex("一笔[\\d.]+元的收入").containsMatchIn(rawText) ||
                (hasBankLedgerSignal(rawText) && (
                    rawText.contains("收入") || rawText.contains("存入") ||
                        rawText.contains("到账") || rawText.contains("转入")
                    ))
            ) && type == "income" && !rawText.contains("提现提醒")

        return when {
            type == "expense" && clearExpense -> ParseConfidence.HIGH to null
            type == "income" && clearIncome -> ParseConfidence.HIGH to null
            else -> ParseConfidence.UNCERTAIN to "未能明确识别为真实收支，请确认"
        }
    }

    /** 四位整数且出现在「尾号xxxx / 卡(xxxx)」上下文 → 卡号而非金额 */
    private fun isCardTailAmount(amount: Double, rawText: String, amountStr: String): Boolean {
        if (amountStr.contains('.')) return false
        if (amountStr.length != 4) return false
        if (amount != amount.toLong().toDouble()) return false
        return rawText.contains("尾号$amountStr") ||
            Regex("(?:储蓄卡|信用卡|借记卡|银行卡)\\s*[(（]$amountStr[)）]").containsMatchIn(rawText) ||
            Regex("【[^】]*[(（]$amountStr[)）]】").containsMatchIn(rawText)
    }

    // ═══════════════════════════════════════════════════════
    // 核心解析
    // ═══════════════════════════════════════════════════════
    fun parse(title: String, content: String, packageName: String): ParsedPayment? {
        val rawText = normalizeNotificationText("$title $content")

        if (isCreditProductPayment(rawText)) return null
        if (isPromotionalOrNonPayment(rawText)) return null

        // 电商 / 微信 / 支付宝 / 抖音：必须有明确支付确认，禁止「见元就记」
        if ((isShoppingApp(packageName) || isStrictChatPayApp(packageName)) &&
            !hasStrongPaymentSignal(rawText)
        ) {
            return null
        }

        // 银行 App：必须有动账类信号，禁止营销通知误记
        if (isBankApp(packageName) && !hasBankLedgerSignal(rawText) && !hasStrongPaymentSignal(rawText)) {
            return null
        }

        // 去掉尾号/卡号末四位，防止「尾号7619」「储蓄卡(7619)」被当成金额
        val text = maskCardTailNumbers(rawText)

        val paymentMethod = identifyPaymentMethod(packageName, rawText)

        // 判断是收入还是支出
        val isIncome = INCOME_KEYWORDS.any { rawText.contains(it) }
        val isExpense = EXPENSE_KEYWORDS.any { rawText.contains(it) }

        // 如果同时包含收入和支出关键词，优先判断
        val type: String
        val amountPatterns: List<Regex>
        var typeAmbiguous = false

        if (isIncome && !isExpense) {
            type = "income"
            amountPatterns = getIncomePatterns(paymentMethod, packageName)
        } else if (isExpense && !isIncome) {
            type = "expense"
            amountPatterns = getExpensePatterns(paymentMethod, packageName)
        } else if (isIncome && isExpense) {
            // 两者都有：能落到明确退款/支付语义则不算模糊
            if (rawText.contains("退款") || rawText.contains("退回") ||
                (rawText.contains("到账") && !rawText.contains("提现"))
            ) {
                type = "income"
                amountPatterns = getIncomePatterns(paymentMethod, packageName)
                typeAmbiguous = !hasStrongPaymentSignal(rawText) && !hasBankLedgerSignal(rawText)
            } else {
                type = "expense"
                amountPatterns = getExpensePatterns(paymentMethod, packageName)
                typeAmbiguous = !hasStrongPaymentSignal(rawText) && !hasBankLedgerSignal(rawText)
            }
        } else {
            // 银行无关键词时：仅动账截断场景可继续；禁止裸「xx元」兜底
            if (isShoppingApp(packageName) || isStrictChatPayApp(packageName)) return null
            if (!hasBankLedgerSignal(rawText)) return null
            typeAmbiguous = true
            type = "expense"
            amountPatterns = getExpensePatterns(paymentMethod, packageName)
        }

        val amountHit = extractAmount(text, amountPatterns, rawText) ?: return null
        val amount = amountHit.value

        // 提取商户名（用原文，卡号遮罩不影响商户）
        var merchant: String? = null
        for (pattern in merchantPatterns) {
            val match = pattern.find(rawText)
            if (match != null && match.groupValues.size > 1) {
                merchant = match.groupValues[1].trim()
                if (merchant.isNotBlank()) break
            }
        }
        // 电商收银台默认商户
        if (merchant.isNullOrBlank() && isShoppingApp(packageName)) {
            merchant = when {
                packageName.contains("jingdong") || packageName.contains("jd.") -> "京东"
                packageName.contains("tmall") -> "天猫"
                packageName.contains("taobao") -> "淘宝"
                else -> null
            }
        }

        // 退款时商户名标注
        if (type == "income" && merchant == null) {
            merchant = when {
                rawText.contains("退款") -> "退款"
                rawText.contains("红包") -> "红包"
                rawText.contains("工资") -> "工资"
                rawText.contains("利息") -> "利息"
                else -> null
            }
        }

        val (confidence, reason) = assessConfidence(
            rawText, amountHit.isClear, typeAmbiguous, type
        )

        // 生成去重key
        val notificationKey = "$packageName:${System.currentTimeMillis() / 10000}:$amount:$type"

        return ParsedPayment(
            amount = amount,
            merchant = merchant,
            paymentMethod = paymentMethod,
            notificationKey = notificationKey,
            type = type,
            confidence = confidence,
            uncertainReason = reason,
            rawSnippet = rawText.take(120)
        )
    }

    // ═══════════════════════════════════════════════════════
    // 获取支出解析规则
    // ═══════════════════════════════════════════════════════
    private fun getExpensePatterns(paymentMethod: String, packageName: String): List<Regex> {
        return when {
            packageName.contains("tencent.mm") -> wechatExpensePatterns
            packageName.contains("AlipayGphone") -> alipayExpensePatterns
            packageName.contains("unionpay") -> unionpayExpensePatterns
            packageName.contains("ugc.aweme") || packageName.contains("ugc.live") -> douyinExpensePatterns
            packageName.contains("jd.jr") || packageName.contains("jingdong") ||
                packageName.contains("jdlite") || packageName.contains("taobao") ||
                packageName.contains("tmall") -> jdExpensePatterns
            packageName.contains("icbc") -> icbcExpensePatterns + bankExpensePatterns
            packageName.contains("chinapost") || packageName.contains("psbc") -> psbcExpensePatterns
            else -> bankExpensePatterns
        }
    }

    private fun getIncomePatterns(paymentMethod: String, packageName: String): List<Regex> {
        return when {
            packageName.contains("tencent.mm") -> wechatIncomePatterns
            packageName.contains("AlipayGphone") -> alipayIncomePatterns
            packageName.contains("unionpay") -> unionpayIncomePatterns
            packageName.contains("ugc.aweme") || packageName.contains("ugc.live") -> douyinIncomePatterns
            packageName.contains("jd.jr") || packageName.contains("jingdong") ||
                packageName.contains("jdlite") || packageName.contains("taobao") ||
                packageName.contains("tmall") -> jdIncomePatterns
            packageName.contains("icbc") -> icbcIncomePatterns
            packageName.contains("chinapost") || packageName.contains("psbc") -> psbcIncomePatterns
            else -> bankIncomePatterns
        }
    }
}
