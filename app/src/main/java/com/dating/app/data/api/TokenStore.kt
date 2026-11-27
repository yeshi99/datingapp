package com.dating.app.data.api

import android.content.Context

/**
 * 登录态存储：token + 用户信息持久化到 SharedPreferences。
 * 生命周期：
 *   1. MainActivity.onCreate 里调用 init(context) 恢复登录态
 *   2. 登录/注册成功后调用 save()
 *   3. 收到 401 或用户退出登录时调用 clear()
 */
object TokenStore {
    private const val PREF = "dating_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NICKNAME = "nickname"

    @Volatile
    var token: String = ""
        private set

    @Volatile
    var userId: Long = 0L
        private set

    @Volatile
    var nickname: String? = null
        private set

    private var prefs: android.content.SharedPreferences? = null

    /** 在 Application/Activity 创建时调用一次 */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        token = prefs?.getString(KEY_TOKEN, "") ?: ""
        userId = prefs?.getLong(KEY_USER_ID, 0L) ?: 0L
        nickname = prefs?.getString(KEY_NICKNAME, null)
    }

    val isLogin: Boolean get() = token.isNotBlank()

    fun save(newToken: String, newUserId: Long, newNickname: String?) {
        token = newToken
        userId = newUserId
        nickname = newNickname
        prefs?.edit()
            ?.putString(KEY_TOKEN, newToken)
            ?.putLong(KEY_USER_ID, newUserId)
            ?.putString(KEY_NICKNAME, newNickname)
            ?.apply()
    }

    fun clear() {
        token = ""
        userId = 0L
        nickname = null
        prefs?.edit()?.clear()?.apply()
    }
}
