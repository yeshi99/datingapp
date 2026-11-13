@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dating.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dating.app.data.api.RetrofitClient
import com.dating.app.data.model.User

/**
 * 功能 2：用户信息展示页（支持展示"我的资料"和"他人资料"两种模式）
 *
 * 后端接口：
 *   GET /users/me         我的资料
 *   GET /users/{userId}   他人资料
 */
@Composable
fun ProfileScreen(
    userId: Long?,                        // null 表示"我的资料"
    onChatWithPeer: (Long) -> Unit        // 点击"打招呼"→ 进入聊天
) {
    var user by remember { mutableStateOf<User?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        try {
            val resp = if (userId == null) RetrofitClient.api.getMe()
                       else RetrofitClient.api.getUser(userId)
            val body = resp.body()
            if (resp.isSuccessful && body?.data != null) {
                user = body.data
                errorMsg = null
            } else {
                errorMsg = "加载失败(${resp.code()})"
            }
        } catch (e: Exception) {
            errorMsg = "网络异常：${e.message}"
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            errorMsg != null -> Text(
                errorMsg!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
            user != null -> ProfileContent(user!!, userId != null, onChatWithPeer)
        }
    }
}

@Composable
private fun ProfileContent(user: User, isOther: Boolean, onChatWithPeer: (Long) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 照片墙（接口同时下发 photos 列表）
        if (!user.photos.isNullOrEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().height(260.dp)
            ) {
                items(user.photos) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("TA 还没有上传照片")
            }
        }

        Column(Modifier.padding(16.dp)) {
            // 昵称 + 年龄 + 会员标记
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${user.nickname}", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(8.dp))
                Text("· ${user.age}岁", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (user.isVip) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                        Text("VIP", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            genderLabel(user.gender)?.let {
                AssistChip(onClick = {}, label = { Text(it) })
                Spacer(Modifier.height(4.dp))
            }
            user.city?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            user.job?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Work, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("关于我", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(user.bio ?: "这个人很神秘，什么都没写~", color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!user.tags.isNullOrEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("兴趣标签", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    user.tags.forEach { tag ->
                        SuggestionChip(onClick = {}, label = { Text(tag) })
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 只有"他人资料"才显示打招呼入口（聊天按付费逻辑在聊天页处理）
            if (isOther) {
                Button(
                    onClick = { onChatWithPeer(user.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Chat, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("打个招呼")
                }
            }
        }
    }
}

private fun genderLabel(g: String) = when (g.uppercase()) {
    "MALE" -> "男"
    "FEMALE" -> "女"
    else -> null
}
