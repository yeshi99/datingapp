package com.dating.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dating.app.data.api.RetrofitClient
import com.dating.app.data.api.TokenStore
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * 注册 / 登录页（未登录时为启动页）
 *
 * 后端接口（Django REST Framework）：
 *   POST /auth/register  body: {"username","password","nickname","gender"}  gender: "M"|"F"
 *   POST /auth/login     body: {"username","password"}
 *
 * 密码规则（对齐 Django validate_password 默认策略，前端先校验避免 400）：
 *   ≥ 8 位、非纯数字、不与账号相同/包含账号
 */
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("F") }   // 后端枚举：M=男 F=女
    var showPassword by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    /** 校验失败返回提示文案，通过返回 null */
    fun validate(): String? {
        if (username.isBlank()) return "请输入账号"
        if (password.length < 8) return "密码至少 8 位（Django 密码策略）"
        if (password.all { it.isDigit() }) return "密码不能是纯数字"
        if (password.contains(username) || password == username) return "密码不能包含账号"
        if (isRegisterMode) {
            if (nickname.isBlank()) return "请填写昵称"
            if (password != confirmPassword) return "两次密码输入不一致"
        }
        return null
    }

    fun submit() {
        validate()?.let { errorMsg = it; return }

        loading = true
        errorMsg = null
        scope.launch {
            try {
                val resp = if (isRegisterMode) {
                    RetrofitClient.api.register(
                        mapOf(
                            "username" to username,
                            "password" to password,
                            "nickname" to nickname,
                            "gender" to gender
                        )
                    )
                } else {
                    RetrofitClient.api.login(
                        mapOf("username" to username, "password" to password)
                    )
                }

                if (!resp.isSuccessful) {
                    errorMsg = when {
                        resp.code() == 400 -> "参数校验失败：${extractError(resp) ?: "未知字段错误"}"
                        resp.code() == 401 -> "账号或密码错误"
                        resp.code() == 409 -> "该账号已注册，请直接登录"
                        else -> "请求失败(${resp.code()}) ${extractError(resp) ?: ""}"
                    }
                    loading = false
                    return@launch
                }

                // 兼容多种成功返回：{token} / {access}(simplejwt) / {data:{...}} 包装
                val body = resp.body()
                val data = body?.get("data") as? Map<*, *>
                val token = (body?.get("token") ?: body?.get("access") ?: data?.get("token")
                    ?: data?.get("access"))?.toString()
                val userId = ((body?.get("userId") ?: body?.get("user_id") ?: body?.get("id")
                    ?: data?.get("userId") ?: data?.get("user_id") ?: data?.get("id")) as? Number)?.toLong() ?: 0L
                val nick = (body?.get("nickname") ?: data?.get("nickname"))?.toString()

                if (!token.isNullOrBlank()) {
                    TokenStore.save(token, userId, nick)
                    onLoginSuccess()
                } else if (isRegisterMode) {
                    // 注册成功但未直接返回 token（DRF 常见：返回用户信息）→ 引导登录
                    isRegisterMode = false
                    errorMsg = "注册成功，请登录"
                } else {
                    errorMsg = "登录成功但响应中没有 token，请检查后端返回格式"
                }
            } catch (e: Exception) {
                errorMsg = "网络异常：${e.message}"
            }
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("缘", color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("缘分", style = MaterialTheme.typography.headlineMedium)
        Text("遇见对的人", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(32.dp))

        TabRow(
            selectedTabIndex = if (isRegisterMode) 1 else 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = !isRegisterMode, onClick = {
                isRegisterMode = false; errorMsg = null
            }, text = { Text("登录") })
            Tab(selected = isRegisterMode, onClick = {
                isRegisterMode = true; errorMsg = null
            }, text = { Text("注册") })
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it.take(30) },
            label = { Text("账号（手机号/用户名）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(50) },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = gender == "F",
                    onClick = { gender = "F" },
                    label = { Text("我是女生") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = gender == "M",
                    onClick = { gender = "M" },
                    label = { Text("我是男生") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码（至少8位，非纯数字）") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (isRegisterMode) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("确认密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        errorMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { submit() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isRegisterMode) "注册并进入" else "登录")
        }

        Spacer(Modifier.height(40.dp))
    }
}

/** 解析 DRF 风格的错误响应：{"detail":"..."} 或 {"username":["该字段是必填项。"], ...} */
private fun extractError(resp: Response<Map<String, Any>>): String? {
    val raw = try { resp.errorBody()?.string() } catch (e: Exception) { null } ?: return null
    return try {
        val json = Gson().fromJson(raw, Map::class.java) as? Map<*, *>
        when {
            json == null || json.isEmpty() -> raw.take(120)
            json.containsKey("detail") -> json["detail"].toString()
            else -> json.entries.take(3).joinToString("；") { (k, v) ->
                val msg = (v as? List<*>)?.joinToString("、") { it.toString() } ?: v.toString()
                "$k：$msg"
            }
        }
    } catch (e: Exception) {
        raw.take(120)
    }
}
