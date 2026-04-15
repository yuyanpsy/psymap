package com.psymap.app

object TencentConfig {
    // 从 secrets.properties 或环境变量加载
    // 开发时直接填写，发布时通过 BuildConfig 注入
    var secretId: String = "YOUR_TENCENT_SECRET_ID"
    var secretKey: String = "YOUR_TENCENT_SECRET_KEY"

    fun init(prefs: android.content.SharedPreferences) {
        secretId = prefs.getString("tencent_secret_id", secretId) ?: secretId
        secretKey = prefs.getString("tencent_secret_key", secretKey) ?: secretKey
    }

    fun save(prefs: android.content.SharedPreferences, id: String, key: String) {
        secretId = id; secretKey = key
        prefs.edit().putString("tencent_secret_id", id).putString("tencent_secret_key", key).apply()
    }
}
