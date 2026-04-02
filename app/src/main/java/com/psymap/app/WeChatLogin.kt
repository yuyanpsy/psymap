package com.psymap.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psymap.app.wxapi.WXEntryActivity
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import okhttp3.*
import java.io.IOException

object WeChatLogin {
    private const val APP_ID = "wx842e5368d23fb03c"
    private const val APP_SECRET = "d54d1e37cdba63e6a236c59ca7b71cda"

    private var api: IWXAPI? = null
    private val client = OkHttpClient()
    private val gson = Gson()

    fun init(context: Context) {
        api = WXAPIFactory.createWXAPI(context, APP_ID, true)
        api?.registerApp(APP_ID)
    }

    fun isWeChatInstalled(): Boolean = api?.isWXAppInstalled == true

    /** 发起微信登录请求 */
    fun login() {
        val req = SendAuth.Req()
        req.scope = "snsapi_userinfo"
        req.state = "psymap_login"
        api?.sendReq(req)
    }

    /** 用 code 换取 access_token 和用户信息 */
    fun getAccessToken(
        code: String,
        onResult: (nickname: String, openId: String, avatarUrl: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=$APP_ID&secret=$APP_SECRET&code=$code&grant_type=authorization_code"

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("网络错误: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.body?.string() ?: ""
                    val map = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                    val accessToken = map["access_token"] as? String
                    val openId = map["openid"] as? String

                    if (accessToken != null && openId != null) {
                        getUserInfo(accessToken, openId, onResult, onError)
                    } else {
                        val errMsg = map["errmsg"] as? String ?: "获取token失败"
                        onError(errMsg)
                    }
                } catch (e: Exception) {
                    onError("解析错误: ${e.message}")
                }
            }
        })
    }

    private fun getUserInfo(
        accessToken: String, openId: String,
        onResult: (nickname: String, openId: String, avatarUrl: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://api.weixin.qq.com/sns/userinfo?access_token=$accessToken&openid=$openId"

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("网络错误: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.body?.string() ?: ""
                    val map = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                    val nickname = map["nickname"] as? String ?: "微信用户"
                    val headimgurl = map["headimgurl"] as? String ?: ""
                    val oid = map["openid"] as? String ?: openId
                    onResult(nickname, oid, headimgurl)
                } catch (e: Exception) {
                    onError("解析用户信息失败: ${e.message}")
                }
            }
        })
    }
}
