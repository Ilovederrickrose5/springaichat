# AGENTS.md

## 1. 项目基础概述

### 1.1 项目信息
- **项目名称**: 企智通 (EnterpriseRAG)
- **artifactId**: enterprise-rag
- **项目用途**: Spring Boot + Spring AI 企业智能知识问答助手，支持多用户会话隔离、历史记录、上下文记忆、SSE 流式输出和 RAG 检索增强
- **技术栈**:
  - Java 17
  - Spring Boot 3.2.10
  - Spring AI 1.0.0-M4
  - Spring Security + JWT（无状态认证）
  - Spring Data JPA + MySQL 8.0+（持久化唯一数据源）
  - Spring Data Redis + Redis 8.0+（聊天上下文缓存 + 向量存储共用）
  - Redis Vector Store / RediSearch（余弦相似度向量检索）
  - Vue 3 + Vite + Element Plus（前端）
  - Maven 构建工具
- **MCP工具**:
  - `mysql-aichat`: MySQL 数据库 ai_chat_db 访问
  - `filesystem`: 仅限当前项目目录文件访问
  - `codegraph`: 代码符号/调用链分析
  - `browser-console`: 运行中浏览器控制台与网络请求日志

### 1.2 项目核心业务目标（已实现）
1. 用户注册 / 登录（BCrypt 密码 + JWT 令牌）
2. 会话管理（创建、列表、详情、删除；按 userId 隔离）
3. 消息同步发送 + SSE / NDJSON 流式输出
4. 打字机效果 + AI 气泡内「暂停/继续」控制
5. 消息管理（单条删除、批量删除、归属校验、Redis 缓存清理重试）
6. 聊天上下文记忆：Redis Cache-Aside 缓存 + MySQL 持久化 + 双重限制（消息条数 20 / Token 4096）
7. RAG 检索增强：启动自动扫描 knowledge/ Markdown → Token 分块(800/200) → text-embedding-v2 向量化 → Redis Vector Store → 检索(topK=3, sim=0.1, tenant=asset过滤) → 拼进 SystemMessage
8. 首字响应优化：用户消息 / 会话标题更新异步落库（CompletableFuture.runAsync）
9. 流式自动重试：Connection reset 等错误最多重试 2 次

> ⚠️ 未实现（文档中若出现请忽略 / 删除）：用户个人资料扩展字段 / UserController 个人中心 API / ProfileDialog 组件

---

## 2. 数据库完整设计

数据库 `ai_chat_db`，JPA `spring.jpa.hibernate.ddl-auto=update` 自动建表。

### 2.1 所有数据表清单

| 表名 | 业务用途 |
|------|----------|
| `user` | 用户账户（仅 id/username/password/createTime 四个持久化字段） |
| `conversation` | 用户聊天会话（userId 外键关联 user） |
| `message` | 会话消息（conversation_id 外键关联 conversation） |

### 2.2 表结构详细说明（与 JPA 实体一一对应）

#### 表：user（用户表）

| 字段名 | 类型 | 可空 | 键 | 默认值 | 注释 |
|--------|------|------|-----|--------|------|
| `id` | bigint | NO | PK | 自增 | 用户ID |
| `username` | varchar(50) | NO | UNIQUE | - | 用户名（唯一） |
| `password` | varchar(255) | NO | - | - | BCrypt 加密密码 |
| `create_time` | datetime | NO | - | 当前时间 | 创建时间（@PrePersist 填充） |

> 注意：实体 [User.java](file:///d:/Users/30776/IdeaProjects/springaichat/src/main/java/com/example/springaichat/entity/User.java) **未定义** nickname / email / phone / avatar / gender / bio / update_time。

---

#### 表：conversation（会话表）

| 字段名 | 类型 | 可空 | 键 | 默认值 | 注释 |
|--------|------|------|-----|--------|------|
| `id` | bigint | NO | PK | 自增 | 会话ID |
| `user_id` | bigint | NO | INDEX | - | 所属用户（关联 user.id） |
| `title` | varchar(200) | NO | - | 新对话 | 会话标题（非空，实际为 DEFAULT 新对话） |
| `create_time` | datetime | NO | - | 当前时间 | 创建时间 |
| `update_time` | datetime | NO | - | 当前时间 | 最近更新时间（@PreUpdate 自动刷新） |

---

#### 表：message（消息表）

| 字段名 | 类型 | 可空 | 键 | 默认值 | 注释 |
|--------|------|------|-----|--------|------|
| `id` | bigint | NO | PK | 自增 | 消息ID |
| `conversation_id` | bigint | NO | INDEX | - | 所属会话（关联 conversation.id） |
| `role` | varchar(20) | NO | - | - | 角色 user / assistant |
| `content` | TEXT | NO | - | - | 消息文本 |
| `create_time` | datetime | NO | - | 当前时间 | 创建时间 |

### 2.3 表关联关系

```
user (1) ──(user_id)── (*) conversation (1) ──(conversation_id)── (*) message
```

---

## 3. 代码分层架构说明

### 3.1 包分层结构（与实际文件完全对应）

```
src/main/java/com/example/springaichat/
├── config/           # 6 个配置类
│   ├── SecurityConfig
│   ├── JwtAuthenticationFilter
│   ├── CorsConfig
│   ├── RedisConfig
│   ├── OpenAiChatConfig   # ChatClient @Primary Bean (qwen-turbo)
│   └── RagConfig          # VectorStore + QuestionAnswerAdvisor (@ConditionalOnProperty rag.enabled)
├── controller/       # AuthController、ChatController（含 SSE + NDJSON 两种流式）
├── dto/              # 7 个 DTO
│   ├── LoginRequest / LoginResponse
│   ├── RegisterRequest
│   ├── MessageRequest / MessageResponse
│   ├── ConversationResponse
│   └── BatchDeleteRequest
├── entity/           # User、Conversation、Message
├── exception/        # GlobalExceptionHandler
├── repository/       # UserRepository、ConversationRepository、MessageRepository
├── service/          # 3 个 Service
│   ├── AuthService         # 注册/登录 + BCrypt + JWT 签发
│   ├── ChatService         # 核心：会话/消息 CRUD + 缓存 + 流式响应（异步落库 + 重试 + RAG）
│   └── KnowledgeBaseService # 启动加载知识库 + TokenTextSplitter + 向量存储 + Hash 去重
├── util/             # JwtUtil（生成 / 解析 / 校验签名与过期）
└── SpringAiChatApplication.java

frontend/src/
├── api/config.js
├── router/index.js      # / → Login.vue ; /chat → Chat.vue (路由守卫)
├── utils/axios.js       # Axios 实例 + Authorization 拦截器
├── views/
│   ├── Chat.vue         # SSE 流 / 打字机 10ms / 暂停 / 右键菜单 / 批量删除
│   └── Login.vue        # 登录 + 注册 Tab
├── App.vue
├── main.js
└── style.css
```

### 3.2 各层职责

| 层级 | 包 | 核心类 | 职责 |
|------|-----|--------|------|
| 控制层 | controller | AuthController / ChatController | HTTP 参数校验、注入当前用户、调用 Service、统一响应结构 |
| 业务层 | service | AuthService / ChatService / KnowledgeBaseService | 核心业务、数据归属校验、异步落库、AI 调用、缓存操作、RAG 编排 |
| 数据层 | repository | UserRepository / ConversationRepository / MessageRepository | JPA 原生查询 + 自定义按 userId/conversationId 条件 |
| 实体层 | entity | User / Conversation / Message | 严格对应 MySQL 表字段 |
| 传输层 | dto | 7 个 DTO | 前后端请求 / 响应数据隔离 |
| 配置层 | config | 6 个配置类 | Security / JWT / CORS / Redis / ChatClient / VectorStore |
| 工具层 | util | JwtUtil | Token 生成与解析 |
| 异常层 | exception | GlobalExceptionHandler | 兜底统一错误响应 |

### 3.3 application.properties 关键配置（注意：项目为 properties 非 yml）

| 配置 | 说明 |
|------|------|
| server.port=8080 | 后端端口 |
| spring.application.name=enterprise-rag | 应用名 |
| spring.datasource.* / hikari.* | MySQL + Hikari，密码 ${MYSQL_PASSWORD} |
| spring.jpa.hibernate.ddl-auto=update | 自动建/改表（开发环境） |
| spring.data.redis.host=localhost port=6379 | Redis 缓存 + 向量库共用 |
| spring.ai.openai.* | 阿里云百炼兼容 OpenAI：qwen-turbo 聊天，text-embedding-v2 嵌入 |
| spring.ai.vectorstore.redis.* | index=chat_knowledge_index prefix=rag:vector: initialize-schema=true |
| jwt.secret=${JWT_SECRET:…至少32字符} jwt.expiration=86400000 | JWT 密钥 / 24h 过期 |
| chat.max-history-size=20 / max-message-length=4000 / max-tokens=4096 / cache-expire-hours=24 | 聊天上下文双重限制 + 缓存 TTL |
| rag.enabled=true / retrieval.top-k=3 / retrieval.similarity-threshold=0.1 | RAG 检索参数 |
| rag.chunk.max-size=800 / overlap-size=200 | Token 分块参数 |
| rag.knowledge.directory=knowledge/ default-tenant=asset | 知识库目录 + 租户元数据 |

---

## 4. 核心业务流程

### 4.1 用户认证流程

```
POST /api/auth/login
  → AuthController.login(LoginRequest)
     → AuthService.login()
        1. UserRepository.findByUsername(username)
        2. PasswordEncoder.matches(raw, BCrypt encoded)
        3. JwtUtil.generateToken(userId, username, expiration=24h)
        4. LoginResponse: token + user { id, username }

前端：localStorage 存 token → 后续请求 Authorization: Bearer xxx
      axios 拦截器统一注入 (utils/axios.js)
```

接口：
- `POST /api/auth/register` - 注册（密码 BCrypt.encode）
- `POST /api/auth/login` - 登录

### 4.2 会话管理流程

每个接口先通过 `@AuthenticationPrincipal User user` 取当前用户，**所有查询都带 user_id 条件**，防止越权：
- `GET /api/chat/conversations` → `ChatService.getConversations(userId)`
- `POST /api/chat/conversations` → 创建，title 默认「新对话」
- `GET /api/chat/conversations/{id}` → getConversation(userId, id) 归属校验
- `DELETE /api/chat/conversations/{id}` → 删除会话 + 消息 + 删除 Redis 聊天历史缓存

### 4.3 消息发送（SSE 流式）完整链路

```
前端 Chat.vue sendMessageStream()
  │  POST /api/chat/messages/stream   Content-Type: text/event-stream
  ▼
ChatController.streamMessage()
  │  校验 user != null，否则直接返回 {"error": "用户未登录..."}
  │  返回 chatService.streamMessage()  Flux<String>
  │  controller 层加：onErrorResume(error → JSON 错误事件)
  ▼
ChatService.streamMessage(userId, MessageRequest)
  ├── 1. 归属校验：getConversation(userId, conversationId)
  ├── 2. ✅ 异步：CompletableFuture.runAsync(saveUserMessageAsync(...)) 不阻塞首字
  ├── 3. ✅ 异步：若为新会话 -> CompletableFuture.runAsync(updateConversationTitleAsync(...))
  ├── 4. getOrCreateChatHistory(conversationId):
  │      Redis命中 → 返回；未命中 → MySQL查消息 → 反序列化为List<Message> → 回写Redis TTL 24h
  ├── 5. trimChatHistory(history, 20条 / 4096 Token 估算双重限制)：从 index=1 开始删；保留 index=0 SystemMessage
  ├── 6. buildMessages(systemPrompt + 历史 + 当前用户消息)
  │      systemPrompt 内附 RAG 规则：「回答必须基于参考资料，不能编造」
  ├── 7. streamAiResponse 包装重试（最多2次，Connection reset 等）
  │      Flux<String> 作为 SSE data 事件逐段发出
  │      累计流式字符串到 fullResponse
  └── 8. doOnComplete
         ├── saveAiResponse(conversationId, fullResponse)
         └── saveChatHistory(conversationId, 新历史) 更新 Redis 缓存

前端 fetch + reader.read() 循环：
  按 data: 行切片 → 逐字符追加 → 每字符 sleep(10ms) 打字机
  catch: ElMessage 弹提示 + 移除 AI 占位消息
  finally: isStreaming/isPaused 标志重置 + 消息状态 done
```

接口：
- `POST /api/chat/messages` - 同步
- `POST /api/chat/messages/stream` - SSE
- `POST /api/chat/messages/stream/json` - NDJSON
- `GET /api/chat/conversations/{id}/messages` - 会话消息列表（归属校验）

### 4.4 消息删除流程

```
DELETE /api/chat/messages/{id} 或  POST batch-delete
  → ChatController 注入 userId
    → ChatService.deleteMessage(s):
        1. message → conversation → user_id 链路归属校验，失败抛异常
        2. MessageRepository 删除（批量则 in (ids)）
        3. RedisTemplate.delete(chat:history:{conversationId})
           删除失败自动重试 2 次，间隔递增
```

接口：
- `DELETE /api/chat/messages/{id}`
- `POST /api/chat/messages/batch-delete`

### 4.5 RAG 知识库 + 检索增强流程

**加载（启动时 @PostConstruct 触发 KnowledgeBaseService.loadKnowledgeBase()）**

```
扫描 classpath:knowledge/**/*.md
  ├── 按文件内容 MD5 做 hash → Redis set(rag:loaded) 已存在则跳过（去重）
  ├── TextReader 读 Markdown → String 原文
  ├── TokenTextSplitter(800, 200, 5, 10000, true) 切 Document 列表
  ├── 每个 Document.metadata: tenant=asset, source=filename, chunk_index=N
  ├── vectorStore.add(documents)
  │    → EmbeddingModel (text-embedding-v2) 生成向量
  │    → Redis Vector Store: chat_knowledge_index / prefix rag:vector:
  └── 记录已处理 hash
```

**检索（每次 SSE 消息前 ChatService.retrieveRagContext(question)）**

```
SearchRequest.query(question)
  .withTopK(3)
  .withSimilarityThreshold(0.1)
  .withFilterExpression("tenant == 'asset'")
  → VectorStore.similaritySearch 返回 List<Document>

将检索到的 3 个文档 chunk 拼接为「参考资料」段落拼进 System prompt
若 qa_retrieved_documents 为空 -> 直接使用模型自身能力回答，不阻塞
```

---

## 5. 上下文缓存机制

### 5.1 策略：Cache-Aside（旁路缓存）
- **MySQL = 唯一数据源**；Redis 仅加速。
- **Key 格式**：`chat:history:{conversationId}`，JSON 存 List<Message>，TTL 24h。

### 5.2 读流程
```
getOrCreateChatHistory(conversationId)
  ├── try redisTemplate.opsForValue().get(key)
  │     → 命中：反序列化 → return
  │     → 未命中 / Redis 异常：降级
  └── messageRepository.findByConversationIdOrderByCreateTimeAsc(id)
        → 结果回写 Redis TTL 24h → return
```

### 5.3 写流程（聊天历史更新）
```
saveChatHistory(conversationId, newHistory)
  └── redisTemplate.opsForValue().set(key, json, Duration.ofHours(24))
```

消息/会话删除时：先写 MySQL → **删除** Redis 缓存（不是更新）。
删除失败最多重试 2 次，间隔递增。

### 5.4 trimChatHistory 双重限制
- 从 index=1（跳过 SystemMessage 索引 0）开始裁剪。
- 条件：`list.size() > 20` 或 `estimateTokens(list) > 4096`。
- estimateTokens 规则：中文 2 字符 = 1 Token、英文 4 字符 = 1 Token、每条消息额外 +4 Token。

### 5.5 一致性保障
| 机制 | 说明 |
|------|------|
| 写删缓存 + 重试 2 次 | 删除失败重试，间隔递增 |
| TTL 24h 自动过期 | 兜底最终一致性 |
| 读时回源降级 | Redis 异常不影响业务 |
| 写时覆盖 | 下一次消息发送自动重写缓存 |

### 5.6 触发缓存删除的操作
- 删除单条消息
- 批量删除消息
- 删除会话
- TTL 24h 过期

---

## 6. MCP 工具能力说明

### 6.1 filesystem
- **作用**: 仅允许在 `${workspaceFolder}`（springaichat 目录）下读写代码 / 配置 / 前端文件。
- **约束**: 绝对禁止跨目录读取修改其他项目或系统文件。

### 6.2 mysql-aichat
- **作用**: 连接 ai_chat_db（127.0.0.1:3306 root）做只读查询 / 描述表 / 数据核对。
- **约束**: 仅操作本项目库；禁止删表 / 改生产结构。

### 6.3 codegraph
- **作用**: 快速定位符号、调用链、包结构。
- **约束**: 只能分析本项目代码。

### 6.4 browser-console
- **作用**: 连接到当前浏览器实例，获取控制台错误 / SSE 请求日志。
- **约束**: 需要有前端开发服务器运行并已连接。

---

## 7. AI 代理行为约束规则（必须遵守）

### 7.1 文件访问
1. 仅允许修改 `springaichat` 项目目录内的代码 / 配置 / 前端源文件
2. 修改代码前先查阅本 AGENTS.md 的架构和字段定义，不要与实际代码冲突（例如 User 实体只有 4 个字段，禁止擅自加列写代码）

### 7.2 数据库操作
1. 仅使用 `mysql-aichat` MCP 服务操作 `ai_chat_db`
2. 建/改表优先通过 JPA 实体注解 + `ddl-auto=update`，不要手工直接 DDL
3. 数据修改必须与 Service 层归属校验逻辑保持一致（按 user_id 过滤）

### 7.3 代码修改
1. 严格分层：Controller → Service → Repository → Entity；禁止跨层
2. 新增类必须放入对应包，编码风格与现有保持一致（字段名 / 命名规范 / 响应结构 `{success, message, data}`）
3. 任何功能改动必须保证：
   - SSE 流式接口不阻塞首字（DB 操作尽量异步）
   - 删除操作清理 Redis 缓存并考虑重试
   - 敏感接口加归属校验（userId 链路校验）

### 7.4 安全
1. 密码：JWT 密钥 / DB 密码 / API Key 一律环境变量或 `${...}` 占位，**禁止硬编码进仓库**
2. JWT：解析只信任签名 + 过期时间，userId 不从请求体取
3. 跨域：CorsConfig 仅放行 5173，生产环境要改成实际域名
