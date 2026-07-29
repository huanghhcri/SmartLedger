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
        Regex("支付([\\d.]+)元"),
        Regex("向(.+?)付款[￥¥]([\\d.]+)"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("已支付[￥¥]([\\d.]+)"),              // 已支付¥0.01
        Regex("支付[￥¥]([\\d.]+)"),                 // 支付¥0.01
        Regex("支出([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("微信支付.*?[￥¥]([\\d.]+)")
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
        Regex("[￥¥]([\\d.]+).*支出"),             // ¥42.75支出
        Regex("积分.*?([\\d.]+)元")               // 领取积分（带金额的通常是支出）
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
    // 工商银行
    // ═══════════════════════════════════════════════════════
    private val icbcExpensePatterns = listOf(
        Regex("支出人民币([\\d.]+)元"),
        Regex("消费人民币([\\d.]+)元"),
        Regex("扣款人民币([\\d.]+)元"),
        Regex("支出[￥¥]([\\d.]+)"),
        Regex("消费[￥¥]([\\d.]+)"),
        Regex("交易金额[￥¥]([\\d.]+)"),
        Regex("支出.*?([\\d.]+)元"),                 // 支出(消费财付通-扫二维码付款)0.01元
        Regex("消费.*?([\\d.]+)元"),                 // 消费(XXX)0.01元
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
        Regex("退款.*?([\\d.]+)元"),
        Regex("收入[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入.*?([\\d.]+)元"),                 // 收入(退款XXX)42.75元
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
    private val bankExpensePatterns = listOf(
        Regex("消费人民币([\\d.]+)元"),
        Regex("支出.*?([\\d.]+)元"),
        Regex("消费.*?([\\d.]+)元"),
        Regex("扣款.*?([\\d.]+)元"),
        Regex("消费[￥¥]([\\d.]+)"),
        Regex("交易[￥¥]([\\d.]+)"),
        Regex("转出[￥¥]([\\d.]+)"),
        Regex("扣款[￥¥]([\\d.]+)"),
        Regex("支出([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出")
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
        Regex("退款至(.+?)(?:\\s|$)"),
        Regex("来自(.+?)(?:\\s|$)")
    )

    // ═══════════════════════════════════════════════════════
    // 支付方式识别
    // ═══════════════════════════════════════════════════════
    private fun identifyPaymentMethod(packageName: String, text: String): String {
        return when {
            packageName.contains("tencent.mm") -> "微信"
            packageName.contains("AlipayGphone") -> "支付宝"
            packageName.contains("unionpay") -> "云闪付"
            packageName.contains("icbc") -> "工商银行"
            packageName.contains("chinapost") || packageName.contains("psbc") -> "邮政储蓄"
            packageName.contains("ccb") -> "建设银行"
            packageName.contains("bocmbci") -> "中国银行"
            packageName.contains("abcpocket") || packageName.contains("abchina") -> "农业银行"
            packageName.contains("cmb") -> "招商银行"
            packageName.contains("pingan") -> "平安银行"
            text.contains("微信") -> "微信"
            text.contains("支付宝") || text.contains("花呗") -> "支付宝"
            text.contains("云闪付") || text.contains("银联") -> "云闪付"
            text.contains("工商银行") || text.contains("工行") -> "工商银行"
            text.contains("邮政") || text.contains("邮储") -> "邮政储蓄"
            text.contains("建设银行") || text.contains("建行") -> "建设银行"
            text.contains("中国银行") || text.contains("中行") -> "中国银行"
            text.contains("农业银行") || text.contains("农行") -> "农业银行"
            text.contains("招商银行") || text.contains("招行") -> "招商银行"
            else -> "银行卡"
        }
    }

    // ═══════════════════════════════════════════════════════
    // 核心解析
    // ═══════════════════════════════════════════════════════
    fun parse(title: String, content: String, packageName: String): ParsedPayment? {
        val text = "$title $content"

        val paymentMethod = identifyPaymentMethod(packageName, text)

        // 判断是收入还是支出
        val isIncome = INCOME_KEYWORDS.any { text.contains(it) }
        val isExpense = EXPENSE_KEYWORDS.any { text.contains(it) }

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
            if (text.contains("退款") || text.contains("退回") || text.contains("到账")) {
                type = "income"
                amountPatterns = getIncomePatterns(paymentMethod, packageName)
            } else {
                type = "expense"
                amountPatterns = getExpensePatterns(paymentMethod, packageName)
            }
        } else {
            // 都没有匹配，但可能是银行通知（没有明确关键词），尝试通用解析
            // 检查是否有金额相关格式
            val hasAmount = text.contains("元") || text.contains("￥") || text.contains("¥")
            if (hasAmount) {
                // 默认为支出（银行扣款通知通常是支出）
                type = "expense"
                amountPatterns = getExpensePatterns(paymentMethod, packageName) + listOf(
                    Regex("([\\d.]+)元"),           // 通用：X元
                    Regex("[￥¥]([\\d.]+)")          // 通用：¥X
                )
            } else {
                return null
            }
        }

        // 提取金额
        var amount: Double? = null
        for (pattern in amountPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val amountStr = match.groupValues.lastOrNull { it.matches(Regex("[\\d.]+")) }
                amount = amountStr?.toDoubleOrNull()
                if (amount != null && amount > 0) break
            }
        }
        if (amount == null || amount <= 0) return null

        // 提取商户名
        var merchant: String? = null
        for (pattern in merchantPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                merchant = match.groupValues[1].trim()
                if (merchant.isNotBlank()) break
            }
        }

        // 退款时商户名标注
        if (type == "income" && merchant == null) {
            merchant = when {
                text.contains("退款") -> "退款"
                text.contains("红包") -> "红包"
                text.contains("工资") -> "工资"
                text.contains("利息") -> "利息"
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
            packageName.contains("icbc") -> icbcExpensePatterns
            packageName.contains("chinapost") || packageName.contains("psbc") -> psbcExpensePatterns
            else -> bankExpensePatterns
        }
    }

    // ═══════════════════════════════════════════════════════
    // 获取收入解析规则
    // ═══════════════════════════════════════════════════════
    private fun getIncomePatterns(paymentMethod: String, packageName: String): List<Regex> {
        return when {
            packageName.contains("tencent.mm") -> wechatIncomePatterns
            packageName.contains("AlipayGphone") -> alipayIncomePatterns
            packageName.contains("unionpay") -> unionpayIncomePatterns
            packageName.contains("icbc") -> icbcIncomePatterns
            packageName.contains("chinapost") || packageName.contains("psbc") -> psbcIncomePatterns
            else -> bankIncomePatterns
        }
    }
}
