package com.dating.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dating.app.data.api.RetrofitClient
import com.dating.app.data.api.TokenStore
import com.dating.app.ui.screens.ChatScreen
import com.dating.app.ui.screens.ConversationListScreen
import com.dating.app.ui.screens.LoginScreen
import com.dating.app.ui.screens.PhotosScreen
import com.dating.app.ui.screens.ProfileScreen
import com.dating.app.ui.screens.SearchScreen
import com.dating.app.ui.theme.DatingAppTheme
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 恢复持久化登录态（token/userId/nickname）
        TokenStore.init(this)
        setContent {
            DatingAppTheme {
                AppScaffold()
            }
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: () -> ImageVector)

private val bottomItems = listOf(
    BottomItem("discover", "发现", { Icons.Default.Search }),
    BottomItem("profile_me", "我的", { Icons.Default.Person }),
    BottomItem("photos", "照片", { Icons.Default.PhotoLibrary }),
    BottomItem("messages", "消息", { Icons.Default.ChatBubble }),
)

@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 登录页与聊天详情页隐藏底部导航
    val showBottomBar = currentRoute !in setOf(
        "login",
        "chat/{conversationId}/{peerName}"
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon(), contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            // 已登录直接进主界面，未登录先进登录页
            startDestination = if (TokenStore.isLogin) "discover" else "login",
            modifier = Modifier.padding(padding)
        ) {
            // 注册 / 登录（登录成功后进入主界面，并清掉登录页路由）
            composable("login") {
                LoginScreen(onLoginSuccess = {
                    navController.navigate("discover") {
                        popUpTo("login") { inclusive = true }
                    }
                })
            }
            // 功能 3：查询用户
            composable("discover") {
                SearchScreen(onOpenUser = { userId ->
                    navController.navigate("profile/$userId")
                })
            }
            // 功能 2：我的资料
            composable("profile_me") {
                ProfileScreen(userId = null, onChatWithPeer = { /* 自己不能跟自己聊 */ })
            }
            // 功能 1：照片管理
            composable("photos") {
                PhotosScreen()
            }
            // 功能 4：会话列表
            composable("messages") {
                ConversationListScreen(onOpenConversation = { conv ->
                    navController.navigate("chat/${conv.id}/${conv.peerNickname}")
                })
            }
            // 功能 2：他人资料详情
            composable(
                route = "profile/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.LongType })
            ) { entry ->
                val userId = entry.arguments?.getLong("userId") ?: 0L
                ProfileScreen(userId = userId, onChatWithPeer = { peerId ->
                    navController.navigate("chat_new/$peerId")
                })
            }
            // 与新对象发起会话再进入聊天页
            composable(
                route = "chat_new/{peerId}",
                arguments = listOf(navArgument("peerId") { type = NavType.LongType })
            ) { entry ->
                val peerId = entry.arguments?.getLong("peerId") ?: 0L
                CreateThenChat(peerId, navController)
            }
            // 聊天页
            composable(
                route = "chat/{conversationId}/{peerName}",
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.LongType },
                    navArgument("peerName") { type = NavType.StringType }
                )
            ) { entry ->
                ChatScreen(
                    conversationId = entry.arguments?.getLong("conversationId") ?: 0L,
                    peerName = entry.arguments?.getString("peerName") ?: "聊天",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/** 进入聊天前先调 POST /conversations 建立会话，然后跳到聊天页 */
@Composable
private fun CreateThenChat(peerId: Long, navController: NavController) {
    LaunchedEffect(peerId) {
        try {
            val resp = RetrofitClient.api.createConversation(mapOf("peerId" to peerId))
            val conv = resp.body()?.data
            if (conv != null) {
                navController.navigate("chat/${conv.id}/${conv.peerNickname}") {
                    popUpTo("chat_new/{peerId}") { inclusive = true }
                }
            } else {
                navController.popBackStack()
            }
        } catch (e: Exception) {
            navController.popBackStack()
        }
    }
    Box {}
}
