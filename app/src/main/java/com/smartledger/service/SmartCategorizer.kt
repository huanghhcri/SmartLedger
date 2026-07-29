package com.smartledger.service

import com.smartledger.data.db.entity.Category

/**
 * 智能分类引擎
 * 根据商户名、支付场景自动匹配分类
 */
object SmartCategorizer {

    // ═══════════════════════════════════════════════════════
    // 分类关键词映射
    // ═══════════════════════════════════════════════════════

    private val categoryRules = listOf(
        // ═══ 餐饮 ═══
        CategoryRule(
            categoryName = "餐饮",
            keywords = listOf(
                "麦当劳", "肯德基", "KFC", "星巴克", "瑞幸", "喜茶", "奈雪",
                "美团", "饿了么", "外卖", "餐饮", "餐厅", "饭店", "食堂",
                "奶茶", "咖啡", "蛋糕", "面包", "烧烤", "火锅", "小吃",
                "必胜客", "汉堡王", "海底捞", "西贝", "呷哺", "真功夫",
                "蜜雪冰城", "茶百道", "古茗", "沪上阿姨", "霸王茶姬",
                "叮咚买菜", "盒马", "每日优鲜", "永辉", "大润发", "沃尔玛"
            )
        ),

        // ═══ 交通 ═══
        CategoryRule(
            categoryName = "交通",
            keywords = listOf(
                "滴滴", "高德", "打车", "出租车", "地铁", "公交", "一卡通",
                "加油", "加油站", "中石油", "中石化", "壳牌", "停车",
                "高速", "ETC", "过路费", "火车", "12306", "机票", "携程",
                "飞猪", "去哪儿", "同程", "哈啰", "青桔", "美团单车",
                "曹操出行", "T3出行", "花小猪", "首汽", "神州"
            )
        ),

        // ═══ 购物 ═══
        CategoryRule(
            categoryName = "购物",
            keywords = listOf(
                "淘宝", "天猫", "京东", "拼多多", "苏宁", "国美",
                "唯品会", "得物", "闲鱼", "转转", "亚马逊",
                "商城", "超市", "便利店", "711", "全家", "罗森",
                "优衣库", "ZARA", "H&M", "耐克", "阿迪", "李宁", "安踏",
                "华为", "小米", "苹果", "Apple", "数码", "电器"
            )
        ),

        // ═══ 娱乐 ═══
        CategoryRule(
            categoryName = "娱乐",
            keywords = listOf(
                "王者荣耀", "和平精英", "原神", "网易游戏", "腾讯游戏",
                "Steam", "游戏", "充值", "点券", "钻石", "皮肤",
                "电影", "影院", "万达", "猫眼", "淘票票",
                "爱奇艺", "优酷", "腾讯视频", "B站", "哔哩哔哩",
                "网易云", "QQ音乐", "Spotify", "会员", "VIP",
                "KTV", "酒吧", "迪厅", "游乐", "景区", "门票",
                "抖音", "快手", "小红书", "直播", "打赏"
            )
        ),

        // ═══ 居住 ═══
        CategoryRule(
            categoryName = "居住",
            keywords = listOf(
                "物业", "物业费", "水电", "电费", "水费", "燃气", "天然气",
                "房租", "租金", "房贷", "贷款", "还款",
                "装修", "家具", "家电", "建材", "五金",
                "家政", "保洁", "维修", "管道", "开锁"
            )
        ),

        // ═══ 医疗 ═══
        CategoryRule(
            categoryName = "医疗",
            keywords = listOf(
                "医院", "诊所", "药店", "药房", "大药房", "老百姓",
                "挂号", "门诊", "住院", "体检", "疫苗",
                "口腔", "牙科", "眼科", "皮肤科",
                "美团买药", "京东健康", "阿里健康", "叮当"
            )
        ),

        // ═══ 教育 ═══
        CategoryRule(
            categoryName = "教育",
            keywords = listOf(
                "学校", "培训", "教育", "课程", "学费", "辅导",
                "书店", "图书", "当当", "文具",
                "考试", "报名", "教材", "网课",
                "得到", "知乎", "极客", "慕课"
            )
        ),

        // ═══ 通讯 ═══
        CategoryRule(
            categoryName = "通讯",
            keywords = listOf(
                "中国移动", "中国联通", "中国电信", "话费", "流量",
                "宽带", "网费", "充值", "移动", "联通", "电信"
            )
        ),

        // ═══ 服饰 ═══
        CategoryRule(
            categoryName = "购物",
            keywords = listOf(
                "衣服", "裤子", "鞋", "包", "帽", "袜", "内衣",
                "裁缝", "洗衣", "干洗", "染色"
            )
        )
    )

    // ═══════════════════════════════════════════════════════
    // 特殊支付方式推断
    // ═══════════════════════════════════════════════════════

    private val paymentMethodHints = mapOf(
        "滴滴出行" to "交通",
        "花小猪" to "交通",
        "高德打车" to "交通",
        "美团外卖" to "餐饮",
        "饿了么" to "餐饮",
        "京东" to "购物",
        "淘宝" to "购物",
        "拼多多" to "购物"
    )

    // ═══════════════════════════════════════════════════════
    // 核心匹配逻辑
    // ═══════════════════════════════════════════════════════

    /**
     * 根据商户名和支付方式推断分类
     * @param merchant 商户名（可能为null）
     * @param paymentMethod 支付方式
     * @param note 备注
     * @param categories 数据库中的分类列表
     * @return 匹配到的分类ID，未匹配返回null
     */
    fun categorize(
        merchant: String?,
        paymentMethod: String?,
        note: String?,
        categories: List<Category>,
        type: String = "expense"
    ): Long? {
        // 构建待匹配文本
        val text = buildString {
            merchant?.let { append(it).append(" ") }
            paymentMethod?.let { append(it).append(" ") }
            note?.let { append(it) }
        }.lowercase()

        if (text.isBlank()) {
            // 无信息时返回"其他"
            return categories.find { it.name == "其他" && it.type == type }?.id
        }

        // 1. 先按关键词匹配
        for (rule in categoryRules) {
            if (rule.matches(text)) {
                val category = categories.find {
                    it.name == rule.categoryName && it.type == type
                }
                if (category != null) {
                    return category.id
                }
            }
        }

        // 2. 按支付方式推断
        for ((hint, categoryName) in paymentMethodHints) {
            if (text.contains(hint.lowercase())) {
                val category = categories.find {
                    it.name == categoryName && it.type == type
                }
                if (category != null) {
                    return category.id
                }
            }
        }

        // 3. 未匹配到任何分类 → 返回"其他"
        return categories.find { it.name == "其他" && it.type == type }?.id
    }

    // ═══════════════════════════════════════════════════════
    // 数据类
    // ═══════════════════════════════════════════════════════

    private data class CategoryRule(
        val categoryName: String,
        val keywords: List<String>
    ) {
        fun matches(text: String): Boolean {
            return keywords.any { keyword ->
                text.contains(keyword.lowercase())
            }
        }
    }
}
