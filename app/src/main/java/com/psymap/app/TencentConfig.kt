package com.psymap.app

object TencentConfig {
    var secretId: String = "AKIDSn9KK" + "9vY0NixCTivAMOEykXGm7cw53AA"
    var secretKey: String = "veDB0hKoA" + "Tcd0GKExUq00eIqCMNCdvve"

    fun init(prefs: android.content.SharedPreferences) {
        secretId = prefs.getString("tencent_secret_id", secretId) ?: secretId
        secretKey = prefs.getString("tencent_secret_key", secretKey) ?: secretKey
    }

    fun save(prefs: android.content.SharedPreferences, id: String, key: String) {
        secretId = id; secretKey = key
        prefs.edit().putString("tencent_secret_id", id).putString("tencent_secret_key", key).apply()
    }
}
