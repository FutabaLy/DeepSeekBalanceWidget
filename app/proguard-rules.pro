# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# 保留数据模型字段名（JSON 反射用不到，但保持安全）
-keep class com.deepseek.balancewidget.data.model.** { *; }
