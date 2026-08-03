package com.smartledger.service

data class ParsedPayment(
    val amount: Double,
    val merchant: String?,
    val paymentMethod: String,
    val notificationKey: String,
    val type: String // "expense" or "income"
)

object NotificationParser {

    // ═══════════════════════════════════════════════════════
    // 支出关键词
    // ═══════════════════════════════════════════════════════
    private val EXPENSE_KEYWORDS = listOf(
        "付款", "支付", "消费", "支出", "转出", "扣款", "已付", "成功付款",
        "购买", "缴费", "还款", "充值", "已扣", "交易支出"
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
        Regex("微信支付.*?已支付[￥¥]([\\d.]+)")
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
        Regex("支出人民币([\\d.]+)元"),
        Regex("消费人民币([\\d.]+)元"),
        Regex("扣款人民币([\\d.]+)元"),
        Regex("支出[￥¥]([\\d.]+)"),
        Regex("消费[￥¥]([\\d.]+)"),
        Regex("交易金额[￥¥]([\\d.]+)"),
        // 动账：支出(消费网银在线-良品铺子…)16.90（通知截断时常无「元」）
        Regex("支出\\([^)]*\\)([\\d.]+)"),
        Regex("消费\\([^)]*\\)([\\d.]+)"),
        Regex("支出.*?([\\d.]+)元"),
        Regex("消费.*?([\\d.]+)元"),
        Regex("扣款.*?([\\d.]+)元"),
        Regex("支出([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出")
    )

    private val icbcIncomePatterns = listOf(
        Regex("收入人民币([\\d.]+)元"),
        Regex("存入人民币([\\d.]+)元"),
        Regex("到账人民币([\\d.]+)元"),
        Regex("转入人民币([\\d.]+)元"),
        Regex("收入\\([^)]*\\)([\\d.]+)元?"),       // 收入(退款网银在线-京东…)116.90元
        Regex("退款.*?([\\d.]+)元"),
        Regex("收入[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入.*?([\\d.]+)元"),
        Regex("到账.*?([\\d.]+)元"),
        Regex("收入([\\d.]+)元"),
        Regex("到账([\\d.]+)元")
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
        Regex("消费人民币([\\d.]+)元"),
        Regex("使用【.+?】支付([\\d.]+)"),
        Regex("支出\\([^)]*\\)([\\d.]+)"),
        Regex("消费\\([^)]*\\)([\\d.]+)"),
        Regex("支出.*?([\\d.]+)元"),
        Regex("消费.*?([\\d.]+)元"),
        Regex("扣款.*?([\\d.]+)元"),
        Regex("消费[￥¥]([\\d.]+)"),
        Regex("交易[￥¥]([\\d.]+)"),
        Regex("转出[￥¥]([\\d.]+)"),
        Regex("扣款[￥¥]([\\d.]+)"),
        Regex("支付([\\d.]+)元"),
        Regex("支付([\\d.]+)"),
        Regex("支出([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("金额([\\d.]+)")
    )

    private val bankIncomePatterns = listOf(
        Regex("收入人民币([\\d.]+)元"),
        Regex("存入人民币([\\d.]+)元"),
        Regex("到账人民币([\\d.]+)元"),
        Regex("收入.*?([\\d.]+)元"),
        Regex("到账.*?([\\d.]+)元"),
        Regex("退款.*?[￥¥]([\\d.]+)"),
        Regex("转入[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入([\\d.]+)元"),
        Regex("到账([\\d.]+)元")
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
        // 工行：支出/收入(消费|退款网银在线-良品铺子…)
        Regex("支出\\((?:消费)?(?:网银在线-|财付通-|支付宝-|微信支付-)?(.+?)\\)"),
        Regex("收入\\((?:退款)?(?:网银在线-|财付通-|支付宝-|微信支付-)?(.+?)\\)"),
        Regex("消费\\((?:网银在线-)?(.+?)\\)"),
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
        if (hasStrongPaymentSignal(text)) return false
        val noise = listOf(
            "省钱金额", "已省回", "省回", "倍会费", "PLUS会员", "会员已省",
            "省钱明细", "点击查看", "相当于",
            "提现提醒", "尚未处理", "将于", "过期", "现金打款",
            "可用额度", "信用额度", "剩余额度", "账户剩余", "剩余可用额度",
            "验证码", "登录验证", "登录成功", "账单日",
            "物流", "配送", "已发货", "待收货", "下单关怀", "签收",
            "优惠券", "领券", "领积分", "积分到账", "领券福利", "福利官",
            "猜一局", "大家都在猜", "周末大家都在",
            "提醒：账户", "签到领", "每日签到",
            // 微信公众号 / 企业号外卖券营销
            "干饭", "请你吃", "今日已上新", "已上新", "外卖券",
            "点击领取", "立即领取", "限时领取", "天天特价"
        )
        if (noise.any { text.contains(it) }) return true
        // 「每天5元…」类文案：有「天/每」+「元」但无支付确认
        if (Regex("每天\\d+(\\.\\d+)?元").containsMatchIn(text)) return true
        if (Regex("\\d+(\\.\\d+)?元请你").containsMatchIn(text)) return true
        if (Regex("\\d+(\\.\\d+)?元干饭").containsMatchIn(text)) return true
        return false
    }

    /** 去掉微信等聚合前缀：[2条]微信支付：已支付¥648.00 */
    fun normalizeNotificationText(text: String): String {
        return text
            .replace(Regex("\\[\\d+条\\]"), "")
            .replace(Regex("\\(\\d+条\\)"), "")
            .trim()
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

    /**
     * 按正则提金额；跳过卡号末四位；优先带小数或带「元」的结果。
     */
    private fun extractAmount(
        maskedText: String,
        patterns: List<Regex>,
        rawText: String
    ): Double? {
        var fallback: Double? = null
        for (pattern in patterns) {
            val matches = pattern.findAll(maskedText)
            for (match in matches) {
                val amountStr = match.groupValues.lastOrNull { it.matches(Regex("[\\d.]+")) } ?: continue
                val value = amountStr.toDoubleOrNull() ?: continue
                if (value <= 0) continue
                if (isCardTailAmount(value, rawText, amountStr)) continue

                val matchedWhole = match.value
                val hasYuanOrSymbol = matchedWhole.contains("元") ||
                    matchedWhole.contains("￥") || matchedWhole.contains("¥") ||
                    amountStr.contains('.')
                if (hasYuanOrSymbol) return value
                if (fallback == null) fallback = value
            }
        }
        return fallback
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
        // （否则企业号「每天5元干饭」会被记成支出）
        if ((isShoppingApp(packageName) || isStrictChatPayApp(packageName)) &&
            !hasStrongPaymentSignal(rawText)
        ) {
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

        if (isIncome && !isExpense) {
            type = "income"
            amountPatterns = getIncomePatterns(paymentMethod, packageName)
        } else if (isExpense && !isIncome) {
            type = "expense"
            amountPatterns = getExpensePatterns(paymentMethod, packageName)
        } else if (isIncome && isExpense) {
            // 两者都有，看哪个关键词更具体
            if (rawText.contains("退款") || rawText.contains("退回") ||
                (rawText.contains("到账") && !rawText.contains("提现"))
            ) {
                type = "income"
                amountPatterns = getIncomePatterns(paymentMethod, packageName)
            } else {
                type = "expense"
                amountPatterns = getExpensePatterns(paymentMethod, packageName)
            }
        } else {
            // 仅银行等可走「有金额无关键词」兜底；聊天/电商已在上方拦截
            if (isShoppingApp(packageName) || isStrictChatPayApp(packageName)) return null
            val hasAmount = rawText.contains("元") || rawText.contains("￥") || rawText.contains("¥")
            if (hasAmount) {
                type = "expense"
                amountPatterns = getExpensePatterns(paymentMethod, packageName) + listOf(
                    Regex("([\\d.]+)元"),
                    Regex("[￥¥]([\\d.]+)")
                )
            } else {
                return null
            }
        }

        val amount = extractAmount(text, amountPatterns, rawText) ?: return null

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

        // 生成去重key
        val notificationKey = "$packageName:${System.currentTimeMillis() / 10000}:$amount:$type"

        return ParsedPayment(
            amount = amount,
            merchant = merchant,
            paymentMethod = paymentMethod,
            notificationKey = notificationKey,
            type = type
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
