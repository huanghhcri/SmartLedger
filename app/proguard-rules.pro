# SmartLedger ProGuard Rules
-keep class com.smartledger.data.db.entity.** { *; }
-keep class com.smartledger.service.PaymentNotificationListener { *; }
-keep class com.smartledger.service.KeepAliveService { *; }
-keep class com.smartledger.service.FloatingWindowService { *; }
-keep class com.smartledger.service.BootReceiver { *; }
-keep class com.smartledger.service.PackageReplacedReceiver { *; }
-keep class com.smartledger.service.SmsReceiver { *; }
-keep class com.smartledger.util.ListenerRebindWorker { *; }
