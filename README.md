# 缘分 · 找对象 Android App（前端）

基于 **Kotlin + Jetpack Compose + Retrofit** 的找对象 App 前端工程，包含四大功能：

| 功能 | 说明 | 页面 |
|------|------|------|
| 0. 注册 / 登录 | 手机号+密码，登录后所有请求带 `Authorization: Bearer <token>` | `LoginScreen` |
| 1. 照片上传 / 删除 | 系统相册选图 → multipart 上传；照片右上角删除 | `PhotosScreen` |
| 2. 用户信息展示 | 我的资料 + 他人资料详情（照片墙、简介、标签） | `ProfileScreen` |
| 3. 查询用户信息 | 关键词 + 性别筛选，分页列表 | `SearchScreen` |
| 4. 聊天（付费解锁） | 会话列表 + 聊天页；未付费发送消息返回 402 → 弹付费墙 | `ConversationListScreen` / `ChatScreen` |

---

## 一、后端接口清单（前端调用的全部 URL）

> Base URL：`https://api.example-dating.com/v1/`（在 `data/api/ApiConfig.kt` 中替换为真实地址）
> 统一约定：响应包装 `{ code, message, data }`，`code == 0` 为成功；鉴权头 `Authorization: Bearer <token>`

### 0. 注册 / 登录（鉴权，Django DRF 实现）

| Method | URL | 说明 |
|--------|-----|------|
| POST | `/auth/register` | 注册，body: `{"username","password","nickname","gender"}`（gender: `M`/`F`，对应 WebUsers.GENDER_CHOICES） |
| POST | `/auth/login` | 登录，body: `{"username","password"}` |
| POST | `/auth/sms/code` | 发送短信验证码（可选，body: `{"phone"}`） |

**后端实现要点**（对齐 RegisterSerializer）：
- 字段：`username`（WebUsers.username）、`password`（write_only）、`nickname`（≤50）、`gender`（M/F）
- 密码走 Django `validate_password`：**≥8位、非纯数字、不与账号相似**（App 端已做同样的前置校验）
- DRF 的 400 错误响应为 `{"username": ["该字段是必填项。"], ...}` 或 `{"detail": "..."}`，App 端会解析并把字段错误显示给用户

**成功返回格式**（登录/注册后前端需要拿到 token）：
- 推荐直接返回 `{"token": "jwt...", "userId": 1, "nickname": "小鹿"}`
- 若使用 simplejwt，返回 `{"access": "...", "refresh": "..."}` 也可，App 端会自动取 `access` 字段
- 若注册接口只返回用户信息（无 token），App 会提示"注册成功，请登录"引导二次登录

**错误码约定**：
- `401`：登录密码错误 / 已登录请求 token 失效（App 收到 401 自动清除登录态）
- `409`：注册时账号已存在
- `400`：DRF 字段校验失败（App 解析 error body 显示具体字段错误）

### 1. 照片管理

| Method | URL | 说明 |
|--------|-----|------|
| GET | `/users/me/photos` | 获取我的照片列表 |
| POST | `/users/me/photos` | 上传照片（multipart，字段名 `file`） |
| DELETE | `/users/me/photos/{photoId}` | 删除照片 |
| POST | `/users/me/photos/{photoId}/avatar` | 设为头像（可选） |

### 2. 用户信息展示

| Method | URL | 说明 |
|--------|-----|------|
| GET | `/users/me` | 我的资料 |
| GET | `/users/{userId}` | 他人资料详情 |

### 3. 查询用户信息

| Method | URL | 说明 |
|--------|-----|------|
| GET | `/users?keyword=&gender=&minAge=&maxAge=&city=&page=&size=` | 条件查询 + 分页 |

### 4. 聊天（付费）

| Method | URL | 说明 |
|--------|-----|------|
| GET | `/conversations` | 会话列表 |
| POST | `/conversations` | 与某人建立会话（body: `{"peerId": 123}`） |
| GET | `/conversations/{conversationId}/messages?beforeId=&size=` | 历史消息 |
| POST | `/conversations/{conversationId}/messages` | 发送消息（body: `{"content": "..."}`，未付费返回 **HTTP 402**） |

### 5. 支付 / 会员（解锁聊天）

| Method | URL | 说明 |
|--------|-----|------|
| GET | `/users/me/membership` | 查询会员状态，data: `{"isVip": true, "expireAt": "..."}` |
| POST | `/pay/orders` | 创建支付订单（body: `{"skuId": "vip_month|vip_quarter|vip_year"}`），返回 `orderId / amountFen / payUrl` |
| POST | `/pay/orders/{orderId}/mock-pay` | 模拟支付（联调用） |
| GET | `/pay/orders/{orderId}` | 查询订单状态：`CREATED / PAID / FAILED / EXPIRED` |

### 付费解锁聊天的交互流程

```
发送消息 ──HTTP 402──▶ 弹出付费墙（月 ¥30 / 季 ¥68 / 年 ¥198）
                            │ 点击购买
                            ▼
                    POST /pay/orders 创建订单
                            │
              真实项目：用 payUrl 拉起微信/支付宝收银台
              联调阶段：POST /pay/orders/{id}/mock-pay
                            │
                            ▼
                轮询 GET /pay/orders/{orderId} 直到 PAID
                            │
                            ▼
              刷新 membership.isVip = true，聊天解锁 ✅
```

---

## 二、接口请求/响应示例（供后端对接）

```jsonc
// GET /users/me  响应 data：
{
  "id": 1, "nickname": "小鹿", "gender": "FEMALE", "age": 26,
  "city": "杭州", "bio": "喜欢猫和民谣", "job": "插画师",
  "tags": ["旅行", "摄影", "美食"],
  "avatarUrl": "https://cdn.xxx.com/photo/1.jpg",
  "photos": ["https://cdn.xxx.com/photo/1.jpg", "https://cdn.xxx.com/photo/2.jpg"],
  "isVip": false
}

// POST /pay/orders  响应 data：
{ "orderId": "PO20250101001", "amountFen": 3000, "payUrl": "https://pay.xxx.com/..." }

// GET /users/me/membership  响应 data：
{ "isVip": true, "expireAt": "2025-12-31T23:59:59Z" }
```

---

## 三、如何运行

1. 用 Android Studio（Ladybug 或以上）打开 `DatingApp` 目录，等待 Gradle Sync。
2. 把 `data/api/ApiConfig.kt` 中的 `BASE_URL` 换成你的后端地址。
   - 本机联调（模拟器）：`http://10.0.2.2:8080/v1/`
3. 点击 Run 运行到模拟器 / 真机（minSdk 24）。
4. 后端未就绪时，各页面会显示网络异常提示，不影响 UI 调试。

## 四、项目结构

```
app/src/main/java/com/dating/app/
├── MainActivity.kt                  # 入口 + 底部导航（发现/我的/照片/消息）
├── data/
│   ├── api/
│   │   ├── ApiService.kt            # 所有后端 URL 集中定义
│   │   └── RetrofitClient.kt        # Retrofit 单例 + Token 拦截器
│   └── model/Models.kt              # User/Photo/Message/Conversation/PayOrder
└── ui/
    ├── theme/                       # 桃粉色主题
    └── screens/
        ├── PhotosScreen.kt          # 功能1：上传/删除照片
        ├── ProfileScreen.kt         # 功能2：用户资料展示
        ├── SearchScreen.kt          # 功能3：查询用户
        ├── ConversationListScreen.kt# 功能4：会话列表
        └── ChatScreen.kt            # 功能4：聊天 + 付费墙
```

## 五、后端联调注意事项

- 上传照片的 multipart 字段名必须是 `file`。
- 未付费用户发送消息必须返回 **HTTP 402**（前端据此触发付费墙），不要只返回业务 code。
- `mock-pay` 接口上线前务必移除或做环境隔离。
- Token 存储当前是内存变量（`TokenStore`），接入登录后应替换为 DataStore。
