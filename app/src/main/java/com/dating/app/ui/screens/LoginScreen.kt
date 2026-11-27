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
import kotlinx.coroutines.launch

/**
 * 注册 / 登录页（未登录时为启动页）
 *
 * 后端接口（尚未实现，按 README 约定开发）：
 *   POST /auth/register  body: {"phone","password","nickname","gender"} → { token, userId, nickname }
 *   POST /auth/login     body: {"phone","password"}                    → { token, userId, nickname }
 *
 * 登录成功后 token 存入 TokenStore（SharedPreferences 持久化），
 * 之后所有请求由 RetrofitClient 拦截器自动携带 Authorization: Bearer <token>
 */
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("FEMALE") }   // 注册时选择
    var showPassword by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun submit() {
        // 前端基础校验
        if (phone.length < 11) { errorMsg = "请输入 11 位手机号"; return }
        if (password.length < 6) { errorMsg = "密码至少 6 位"; return }
        if (isRegisterMode) {
            if (nickname.isBlank()) { errorMsg = "请填写昵称"; return }
            if (password != confirmPassword) { errorMsg = "两次密码输入不一致"; return }
        }

        loading = true
        errorMsg = null
        scope.launch {
            try {
                val resp = if (isRegisterMode) {
                    RetrofitClient.api.register(
                        mapOf(
                            "phone" to phone,
                            "password" to password,
                            "nickname" to nickname,
                            "gender" to gender
                        )
                    )
                } else {
                    RetrofitClient.api.login(
                        mapOf("phone" to phone, "password" to password)
                    )
                }
                val body = resp.body()
                val data = body?.data
                when {
                    resp.isSuccessful && data != null -> {
                        TokenStore.save(data.token, data.userId, data.nickname)
                        onLoginSuccess()
                    }
                    resp.code() == 401 -> errorMsg = "手机号或密码错误"
                    resp.code() == 409 -> errorMsg = "该手机号已注册，请直接登录"
                    else -> errorMsg = if (body?.message.isNullOrBlank())
                        "请求失败(${resp.code()})" else body!!.message
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

        // Logo 区
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

        // 登录 / 注册 切换
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
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(12) },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = gender == "FEMALE",
                    onClick = { gender = "FEMALE" },
                    label = { Text("我是女生") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = gender == "MALE",
                    onClick = { gender = "MALE" },
                    label = { Text("我是男生") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
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
