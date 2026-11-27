package com.dating.app.data.model

/** 用户信息（资料页 / 搜索结果通用） */
data class User(
    val id: Long,
    val nickname: String,
    val gender: String,          // MALE / FEMALE / OTHER
    val age: Int,
    val city: String?,
    val bio: String?,            // 个人简介
    val job: String?,            // 职业
    val tags: List<String>? = null,   // 兴趣标签
    val avatarUrl: String?,      // 头像（取照片列表第一张，后端也可直接下发）
    val photos: List<String>? = null,
    val isVip: Boolean = false   // 是否已付费会员（决定能否聊天）
)

/** 单张照片 */
data class Photo(
    val id: Long,
    val url: String,
    val createdAt: String?
)

/** 会话 */
data class Conversation(
    val id: Long,
    val peerId: Long,
    val peerNickname: String,
    val peerAvatarUrl: String?,
    val lastMessage: String?,
    val lastMessageTime: String?,
    val unreadCount: Int = 0
)

/** 聊天消息 */
data class Message(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val content: String,
    val sentAt: String,
    val isMine: Boolean = false
)

/** 发送消息的响应（未付费时后端返回 402，前端据此弹出付费墙） */
data class SendMessageResponse(
    val id: Long? = null,
    val conversationId: Long? = null,
    val sentAt: String? = null,
    val needPayment: Boolean = false
)

/** 支付订单 */
data class PayOrder(
    val orderId: String,
    val amountFen: Int,          // 金额（分）
    val status: String           // CREATED / PAID / FAILED / EXPIRED
)

/** 创建订单响应 */
data class CreateOrderResponse(
    val orderId: String,
    val amountFen: Int,
    val payUrl: String? = null   // 收银台链接（微信/支付宝拉起）
)

/** 注册/登录响应 */
data class AuthResponse(
    val token: String,
    val userId: Long,
    val nickname: String? = null
)

/** 通用响应包装（后端统一格式） */
data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
)

/** 分页响应 */
data class PageData<T>(
    val list: List<T>,
    val total: Long,
    val page: Int,
    val size: Int
)
