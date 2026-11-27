package com.dating.app.data.api

import com.dating.app.data.model.ApiResponse
import com.dating.app.data.model.Conversation
import com.dating.app.data.model.CreateOrderResponse
import com.dating.app.data.model.Message
import com.dating.app.data.model.PageData
import com.dating.app.data.model.PayOrder
import com.dating.app.data.model.Photo
import com.dating.app.data.model.SendMessageResponse
import com.dating.app.data.model.User
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 后端接口总定义 —— 所有 URL 集中在这里，另见项目根目录 README.md 的接口清单。
 *
 * 约定：
 *  - 所有响应统一包装为 ApiResponse<T>：{ code, message, data }
 *  - code == 0 表示业务成功；HTTP 402 表示“需要付费才能聊天”
 *  - 鉴权：请求头 Authorization: Bearer <token>
 */
interface ApiService {

    // ==================== 0. 注册 / 登录（鉴权） ====================

    /**
     * 发送短信验证码（可选）：POST /auth/sms/code  body: {"phone": "13800138000"}
     */
    @POST("auth/sms/code")
    suspend fun sendSmsCode(
        @Body body: Map<String, String>
    ): Response<ApiResponse<Unit>>

    /**
     * 注册：POST /auth/register
     * body: {"username","password","nickname","gender"}（gender: "M"|"F"）
     * 密码需满足 Django validate_password：≥8位、非纯数字、不与账号相似
     * 成功返回 token（若后端直接返回用户信息无 token，前端自动引导去登录）
     */
    @POST("auth/register")
    suspend fun register(
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    /**
     * 登录：POST /auth/login  body: {"username","password"}
     * 兼容多种返回：{token} / {access}（simplejwt）/ {data:{token,...}} 包装
     * 返回 { token, userId, nickname }，后续所有请求带 Authorization: Bearer <token>
     */
    @POST("auth/login")
    suspend fun login(
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    // ==================== 1. 照片管理 ====================

    /** 获取我的照片列表：GET /users/me/photos */
    @GET("users/me/photos")
    suspend fun getMyPhotos(): Response<ApiResponse<List<Photo>>>

    /** 上传照片（multipart，字段名 file）：POST /users/me/photos */
    @Multipart
    @POST("users/me/photos")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<Photo>>

    /** 删除照片：DELETE /users/me/photos/{photoId} */
    @DELETE("users/me/photos/{photoId}")
    suspend fun deletePhoto(
        @Path("photoId") photoId: Long
    ): Response<ApiResponse<Unit>>

    /** 设为头像：PUT /users/me/photos/{photoId}/avatar（可选功能） */
    @POST("users/me/photos/{photoId}/avatar")
    suspend fun setAvatar(
        @Path("photoId") photoId: Long
    ): Response<ApiResponse<Unit>>

    // ==================== 2. 用户信息展示 ====================

    /** 获取我的资料：GET /users/me */
    @GET("users/me")
    suspend fun getMe(): Response<ApiResponse<User>>

    /** 获取指定用户资料（资料详情页）：GET /users/{userId} */
    @GET("users/{userId}")
    suspend fun getUser(
        @Path("userId") userId: Long
    ): Response<ApiResponse<User>>

    // ==================== 3. 查询/搜索用户 ====================

    /**
     * 按条件查询用户列表：GET /users
     * 支持 keyword（昵称模糊）、gender、minAge、maxAge、city、分页
     */
    @GET("users")
    suspend fun searchUsers(
        @Query("keyword") keyword: String?,
        @Query("gender") gender: String?,
        @Query("minAge") minAge: Int?,
        @Query("maxAge") maxAge: Int?,
        @Query("city") city: String?,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<ApiResponse<PageData<User>>>

    // ==================== 4. 聊天（付费解锁） ====================

    /** 会话列表：GET /conversations */
    @GET("conversations")
    suspend fun getConversations(): Response<ApiResponse<List<Conversation>>>

    /** 历史消息：GET /conversations/{conversationId}/messages */
    @GET("conversations/{conversationId}/messages")
    suspend fun getMessages(
        @Path("conversationId") conversationId: Long,
        @Query("beforeId") beforeId: Long? = null,
        @Query("size") size: Int = 50
    ): Response<ApiResponse<List<Message>>>

    /**
     * 发送消息：POST /conversations/{conversationId}/messages
     * 若当前用户未付费，后端返回 HTTP 402 → 前端弹出付费墙
     */
    @POST("conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: Long,
        @Body body: Map<String, String>   // {"content": "..."}
    ): Response<SendMessageResponse>

    /** 与某人发起/获取会话：POST /conversations  body: {"peerId": 123} */
    @POST("conversations")
    suspend fun createConversation(
        @Body body: Map<String, Long>     // {"peerId": 123}
    ): Response<ApiResponse<Conversation>>

    // ==================== 5. 支付（解锁聊天） ====================

    /** 查询我的会员状态：GET /users/me/membership */
    @GET("users/me/membership")
    suspend fun getMembership(): Response<ApiResponse<Map<String, Any>>>
    // data: { "isVip": true, "expireAt": "2025-12-31T23:59:59Z" }

    /** 创建支付订单：POST /pay/orders  body: {"skuId": "vip_month"} */
    @POST("pay/orders")
    suspend fun createPayOrder(
        @Body body: Map<String, String>   // {"skuId": "vip_month"}
    ): Response<ApiResponse<CreateOrderResponse>>

    /** 模拟支付（联调用）：POST /pay/orders/{orderId}/mock-pay */
    @POST("pay/orders/{orderId}/mock-pay")
    suspend fun mockPay(
        @Path("orderId") orderId: String
    ): Response<ApiResponse<PayOrder>>

    /** 查询订单支付状态：GET /pay/orders/{orderId} */
    @GET("pay/orders/{orderId}")
    suspend fun getPayOrder(
        @Path("orderId") orderId: String
    ): Response<ApiResponse<PayOrder>>
}
