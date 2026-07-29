# SmartLedger 智能记账

一款 Android 智能记账应用，支持自动识别微信、支付宝、云闪付、银行卡的支付通知并自动记账。

## ✨ 功能特点

### 🤖 自动记账
- 自动监听微信、支付宝、云闪付支付通知
- 支持工商银行、邮政储蓄、建设银行、中国银行等主流银行
- 智能识别收入（退款、转账、红包等）
- 自动去重（同一笔交易不会重复录入）
- 智能分类（根据商户名自动匹配分类）

### 📊 数据统计
- 日/周/月/年多维度统计
- 环形图展示分类占比
- 分类排行榜
- 收支趋势分析

### 💰 预算管理
- 月度预算设置
- 分类预算
- 预算使用进度提醒

### 📱 其他功能
- 手动记账
- 数据导出（CSV）
- 数据备份/恢复
- 深色模式（开发中）

## 📸 界面预览

| 首页 | 记账 | 统计 | 我的 |
|------|------|------|------|
| ![](screenshots/home.png) | ![](screenshots/record.png) | ![](screenshots/statistics.png) | ![](screenshots/profile.png) |

## 🚀 安装方式

### 方式一：下载 APK
1. 前往 [Releases](https://github.com/huanghhcri/SmartLedger/releases) 页面
2. 下载最新版本的 `app-release.apk`
3. 在手机上安装（需要开启"允许安装未知来源应用"）

### 方式二：自行编译
```bash
# 1. 克隆项目
git clone https://github.com/huanghhcri/SmartLedger.git

# 2. 用 Android Studio 打开项目

# 3. 连接手机或启动模拟器

# 4. 点击 Run 运行
```

## 📋 系统要求

- Android 8.0 (API 26) 及以上
- 建议 Android 10+ 以获得最佳体验

## 🔐 权限说明

| 权限 | 用途 |
|------|------|
| 通知监听权限 | 读取支付通知，自动识别交易 |
| 悬浮窗权限 | 弹出支付确认窗口 |
| 存储权限 | 导出/备份数据 |

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **数据库**: Room
- **架构**: MVVM
- **异步**: Kotlin Coroutines + Flow

## 📝 使用说明

### 首次使用
1. 安装后打开应用
2. 按提示开启「通知监听权限」
3. 开启「悬浮窗权限」（可选）
4. 开始使用

### 自动记账
- 使用微信/支付宝付款后，App 会自动识别并记录
- 支付宝通知示例：「你有一笔XX元的支出」
- 微信通知示例：「已支付¥XX」
- 银行通知示例：「支出(消费XXX)XX元」

### 智能分类
App 会根据商户名自动分类：
- 麦当劳/肯德基/星巴克 → 餐饮
- 滴滴/地铁/加油 → 交通
- 淘宝/京东/拼多多 → 购物
- 未识别的交易 → 其他

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3](https://m3.material.io/)
- [Room Database](https://developer.android.com/training/data-storage/room)

---

如有问题或建议，欢迎提交 Issue！
