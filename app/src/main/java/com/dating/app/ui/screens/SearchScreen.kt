package com.dating.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch

/**
 * 功能 3：查询/搜索用户页
 *
 * 后端接口：
 *   GET /users?keyword=&gender=&minAge=&maxAge=&city=&page=&size=
 */
@Composable
fun SearchScreen(onOpenUser: (Long) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }   // null=不限, MALE, FEMALE
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doSearch(page: Int = 1) {
        loading = true
        errorMsg = null
        scope.launch {
            try {
                val resp = RetrofitClient.api.searchUsers(
                    keyword = keyword.ifBlank { null },
                    gender = gender,
                    minAge = null,
                    maxAge = null,
                    city = null,
                    page = page
                )
                val body = resp.body()
                if (resp.isSuccessful && body?.data != null) {
                    results = body.data!!.list
                    searched = true
                } else {
                    errorMsg = "查询失败(${resp.code()})"
                }
            } catch (e: Exception) {
                errorMsg = "网络异常：${e.message}"
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("发现缘分", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // 搜索栏
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = { Text("搜索昵称 / 关键词") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                TextButton(onClick = { doSearch() }) { Text("搜索") }
            }
        )

        Spacer(Modifier.height(8.dp))

        // 性别筛选
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = gender == null,
                onClick = { gender = null; doSearch() },
                label = { Text("不限") }
            )
            FilterChip(
                selected = gender == "FEMALE",
                onClick = { gender = "FEMALE"; doSearch() },
                label = { Text("女生") }
            )
            FilterChip(
                selected = gender == "MALE",
                onClick = { gender = "MALE"; doSearch() },
                label = { Text("男生") }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMsg != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (searched) "没有找到符合条件的人" else "输入条件，找到你的心动对象",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.id }) { user ->
                    UserCard(user) { onOpenUser(user.id) }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.nickname, style = MaterialTheme.typography.titleMedium)
                    if (user.isVip) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                            Text("VIP", Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("${user.age}岁 · ${user.city ?: "未知"} · ${user.job ?: "自由职业"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                user.bio?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
