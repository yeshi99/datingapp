package com.dating.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dating.app.data.api.RetrofitClient
import com.dating.app.data.model.Message
import com.dating.app.data.model.PayOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 功能 4（下）：聊天页 + 付费墙
 *
 * 后端接口：
 *   GET    /conversations/{conversationId}/messages   拉取历史消息
 *   POST   /conversations/{conversationId}/messages   发送消息（未付费 → HTTP 402）
 *   GET    /users/me/membership                       查询会员状态（isVip）
 *   POST   /pay/orders                                创建支付订单
 *   POST   /pay/orders/{orderId}/mock-pay             模拟支付（联调用）
 *   GET    /pay/orders/{orderId}                      轮询订单支付状态
 *
 * 付费逻辑：
 *   1. 进入聊天页先查 membership.isVip
 *   2. 未付费时：发送请求返回 402 → 弹出付费墙 Dialog
 *   3. 付费墙中创建订单 → 调起收银台（真实项目）/ 模拟支付（联调）
 *   4. 轮询订单状态为 PAID 后刷新 isVip，解锁聊天
 */
@Composable
fun ChatScreen(
    conversationId: Long,
    peerName: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var isVip by remember { mutableStateOf<Boolean?>(null) }
    var showPaywall by remember { mutableStateOf(false) }
    var payStatus by remember { mutableStateOf<PayOrder?>(null) }
    var paying by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 拉取历史消息 + 会员状态
    LaunchedEffect(conversationId) {
        try {
            val resp = RetrofitClient.api.getMessages(conversationId)
            resp.body()?.data?.let { messages = it }
            val m = RetrofitClient.api.getMembership()
            isVip = m.body()?.data?.get("isVip") as? Boolean
        } catch (e: Exception) {
            errorMsg = "网络异常：${e.message}"
        }
    }

    // 消息变化时滚到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        scope.launch {
            try {
                val resp = RetrofitClient.api.sendMessage(conversationId, mapOf("content" to text))
                if (resp.isSuccessful) {
                    // 立即本地追加 + 重新拉取保持一致
                    val respMsgs = RetrofitClient.api.getMessages(conversationId)
                    respMsgs.body()?.data?.let { messages = it }
                    input = ""
                } else if (resp.code() == 402) {
                    // 未付费 → 弹出付费墙
                    showPaywall = true
                } else {
                    errorMsg = "发送失败(${resp.code()})"
                }
            } catch (e: Exception) {
                errorMsg = "网络异常：${e.message}"
            }
        }
    }

    fun doPay(skuId: String) {
        paying = true
        scope.launch {
            try {
                // 1. 创建订单
                val orderResp = RetrofitClient.api.createPayOrder(mapOf("skuId" to skuId))
                val order = orderResp.body()?.data
                if (order == null) {
                    errorMsg = "创建订单失败(${orderResp.code()})"
                    paying = false
                    return@launch
                }
                // 真实项目：此处用 order.payUrl 拉起微信/支付宝收银台
                // 联调阶段：直接调模拟支付
                RetrofitClient.api.mockPay(order.orderId)

                // 2. 轮询订单状态（最多 10 次 × 1s）
                for (i in 1..10) {
                    val statusResp = RetrofitClient.api.getPayOrder(order.orderId)
                    val po = statusResp.body()?.data
                    payStatus = po
                    if (po?.status == "PAID") {
                        isVip = true
                        showPaywall = false
                        payStatus = null
                        break
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                errorMsg = "支付异常：${e.message}"
            }
            paying = false
        }
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(peerName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                // 会员角标
                when (isVip) {
                    true -> AssistChip(onClick = {}, label = { Text("已解锁") })
                    false -> AssistChip(
                        onClick = { showPaywall = true },
                        label = { Text("付费解锁") },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    null -> {}
                }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(if (isVip == false) "付费后即可发送消息" else "说点什么...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { send() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            errorMsg?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                )
            }
            if (messages.isEmpty()) {
                Text(
                    "开始你们的对话吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(msg)
                    }
                }
            }
        }
    }

    // ===== 付费墙 =====
    if (showPaywall) {
        PaywallDialog(
            paying = paying,
            onDismiss = { if (!paying) showPaywall = false },
            onPay = { doPay(it) }
        )
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val isMine = msg.isMine
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = if (isMine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                msg.content,
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isMine) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PaywallDialog(
    paying: Boolean,
    onDismiss: () -> Unit,
    onPay: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解锁聊天") },
        text = {
            Column {
                Text("开通会员后即可与心动对象畅聊", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                // 会员 SKU（对应后端 skuId）
                PaySkuRow("月度会员", "¥30 / 月", "vip_month", paying, onPay)
                Spacer(Modifier.height(8.dp))
                PaySkuRow("季度会员", "¥68 / 季", "vip_quarter", paying, onPay)
                Spacer(Modifier.height(8.dp))
                PaySkuRow("年度会员", "¥198 / 年", "vip_year", paying, onPay)
                if (paying) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("支付中，请稍候...")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !paying) { Text("暂不需要") }
        }
    )
}

@Composable
private fun PaySkuRow(name: String, price: String, skuId: String, paying: Boolean, onPay: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(price, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(12.dp))
            Button(onClick = { onPay(skuId) }, enabled = !paying) { Text("购买") }
        }
    }
}
