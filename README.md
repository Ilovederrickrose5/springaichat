# 企智通 (Enterprise RAG Assistant)

基于 **Spring Boot 3 + Spring AI + Redis Vector Store** 的企业智能知识问答系统。支持多用户会话隔离、JWT 双 Token 认证、SSE 流式对话、上下文记忆、Markdown 渲染与 RAG 知识库检索增强。

> artifactId：`enterprise-rag`　│　后端端口：`8080`　│　前端端口：`5173`

---

## 1. 功能特性

### 认证与安全
- 注册 / 登录 / 刷新 / 登出，密码 BCrypt 加密
- JWT 双 Token：access 30 分钟 + refresh 7 天
- Token Rotation：每次刷新都换发新 refresh，旧 Token 立即失效
- 登出双销毁：删除 Redis 中绑定的 refresh + access `jti` 拉黑（TTL = 剩余有效期）
- 前端 401 无感刷新（并发请求队列 + `isRefreshing` 锁）
- 业务层数据隔离：所有会话 / 消息查询强制带 `user_id` 条件，userId 只从 Token 解析

### 会话与消息
- 会话创建、列表（按更新时间倒序）、详情、删除（级联删除消息 + 清理缓存）
- 消息同步发送、SSE 流式发送、NDJSON 流式发送
- 单条删除 / 批量删除 / 右键菜单 / 多选
- 会话标题自动生成（首条用户消息截取）

### 流式对话体验
- 打字机效果（默认 10ms/字符，可调）
- AI 气泡内「暂停 / 继续」控制
- `AbortController` 支持中途取消请求
- 滚动锁定：用户向上翻阅历史时不强制滚到底部
- **实时 Markdown 渲染**：流式输出过程中即解析标题、加粗、列表、表格、代码块，节流 50ms 防止逐字符解析拖慢打字机
- 代码高亮（highlight.js，按需加载 common 语言包）+ DOMPurify 消毒防 XSS
- 网络抖动自动重试（Connection reset 等，最多 2 次）

### 上下文与性能
- Cache-Aside 缓存：`chat:history:{conversationId}`，TTL 24h
- 双重裁剪：最多 20 条消息 + 累计 4096 Token（保留 SystemMessage）
- 首字延迟优化：用户消息与会话标题异步落库，不阻塞模型调用
- 缓存删除失败重试 2 次（间隔递增）

### RAG 知识库
- 启动时自动扫描 `classpath:knowledge/**/*.md`，按内容 MD5 去重
- TokenTextSplitter 分块：800 Token / 块，200 Token 重叠
- `text-embedding-v2` 向量化 → Redis Vector Store（RediSearch 余弦相似度）
- 检索 topK=3、相似度阈值 0.30
- 检索结果拼入 SystemMessage；无命中时退化为模型自身能力回答
- 提示词约束：只依据内部资料作答、禁止编造、不输出「参考资料 / 来源」等引用字眼

---

## 2. 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Java 17、Spring Boot 3.2.10 |
| AI 框架 | Spring AI 1.0.0-M4（OpenAI 兼容模式） |
| 大模型 | 阿里云百炼 `qwen-plus`（聊天）、`text-embedding-v2`（1536 维嵌入） |
| 安全 | Spring Security + JJWT 0.12.5（双 Token） |
| 持久层 | Spring Data JPA + MySQL 8.0（HikariCP） |
| 缓存 / 向量库 | Spring Data Redis + Redis Stack（RediSearch） |
| 前端 | Vue 3.4 + Vite 5 + Element Plus 2.6 + Vue Router 4 + Axios |
| Markdown | marked 18 + marked-highlight + highlight.js 11 + DOMPurify 3 |
| 构建 / 运行 | Maven 3.8+、Node.js 18+、Docker Compose（MySQL + Redis） |

---

## 3. 系统架构

```
┌──────────────────────────── 浏览器 (Vue 3 + Element Plus) ───────────────────────────┐
│  Login.vue ──登录──▶ accessToken/refreshToken (localStorage)                          │
│  Chat.vue  ──fetch + ReadableStream──▶ 解析 SSE / 打字机 / 实时 Markdown / 暂停 / 取消  │
│         ▲                          │                                                  │
│         └── axios 拦截器：401 → 无感刷新 → 重放请求（并发队列）                        │
└────────────────────────────────────┼─────────────────────────────────────────────────┘
                                     │ HTTP / SSE
┌────────────────────────────────────▼─────────────────────────────────────────────────┐
│                        Spring Boot 3.2.10 (:8080)                                    │
│  Controller  AuthController / ChatController                                         │
│      │                                                                               │
│  Filter      JwtAuthenticationFilter（签名 + type=access + jti 黑名单）               │
│      │                                                                               │
│  Service     AuthService / TokenStoreService / ChatService / KnowledgeBaseService     │
│      │              │                    │                    │                       │
│  Repository   User / Conversation / Message                                           │
└──────┼──────────────┼────────────────────┼────────────────────┼───────────────────────┘
       │              │                    │                    │
   MySQL 8.0      Redis 8.0          Redis Vector Store    DashScope API
   (ai_chat_db)   (会话缓存/JWT)      (chat_knowledge_index) (qwen-plus / embedding)
```

---

## 4. 目录结构

```
springaichat/
├── src/main/java/com/example/springaichat/
│   ├── config/
│   │   ├── SecurityConfig              # 路由权限、无状态会话、BCrypt
│   │   ├── JwtAuthenticationFilter     # 解析 access Token + 黑名单校验
│   │   ├── CorsConfig                  # 仅放行 5173
│   │   ├── RedisConfig                 # RedisTemplate 序列化
│   │   ├── OpenAiChatConfig            # ChatClient（@Primary，qwen-plus）
│   │   └── RagConfig                   # JedisPooled + RedisVectorStore（@ConditionalOnProperty）
│   ├── controller/
│   │   ├── AuthController              # 注册 / 登录 / 刷新 / 登出
│   │   └── ChatController              # 会话 CRUD、消息 CRUD、SSE、NDJSON
│   ├── service/
│   │   ├── AuthService                 # 双 Token 签发、refresh rotation、登出
│   │   ├── TokenStoreService           # Redis：refresh 绑定 + access 黑名单
│   │   ├── ChatService                 # 聊天主流程、缓存、RAG、异步落库、流式重试
│   │   └── KnowledgeBaseService        # 知识库加载 / 分块 / 向量化 / Hash 去重
│   ├── repository/                     # User / Conversation / Message
│   ├── entity/                         # User / Conversation / Message
│   ├── dto/                            # 8 个请求/响应 DTO
│   ├── exception/GlobalExceptionHandler
│   ├── util/JwtUtil                    # 双 Token 生成 / 解析 / 类型校验
│   └── SpringAiChatApplication.java
├── src/main/resources/
│   ├── application.properties
│   └── knowledge/                      # RAG 知识库（4 篇 Markdown）
│       ├── 01_approval_flow.md         # 资产审批流程
│       ├── 02_rbac_roles.md            # 角色与权限
│       ├── 03_depreciation_methods.md  # 折旧方法
│       └── 04_system_faq.md            # 系统常见问题
├── frontend/
│   ├── src/
│   │   ├── api/config.js               # API 基础路径
│   │   ├── router/index.js             # 懒加载路由 + 登录守卫
│   │   ├── utils/
│   │   │   ├── axios.js                # JWT 注入 + 401 无感刷新队列
│   │   │   └── auth.js                 # 公共 refreshToken()、Token 存取工具
│   │   ├── composables/useStreamChat.js# SSE 流解析、打字机、暂停、取消
│   │   ├── components/
│   │   │   ├── ConversationList.vue    # 会话列表
│   │   │   ├── MessageItem.vue         # 消息气泡 + Markdown 渲染 + 右键菜单
│   │   │   ├── ContextMenu.vue         # 右键菜单
│   │   │   └── InputArea.vue           # 输入框 + 发送
│   │   └── views/
│   │       ├── Chat.vue                # 主页面：状态编排、滚动控制、业务方法
│   │       └── Login.vue               # 登录 / 注册
│   ├── vite.config.js                  # manualChunks 分包
│   └── package.json
├── docker-compose.yml                  # MySQL(3307) + Redis Stack(6379)
├── start-all.bat                       # Windows 一键启动脚本
├── AGENTS.md                           # 开发约定
└── README.md
```

---

## 5. 快速开始

### 5.1 环境要求

- JDK 17+（推荐 21，编译目标仍为 17）
- Maven 3.8+
- Node.js 18+
- Docker Desktop（用于 MySQL 与 Redis Stack）

### 5.2 启动 MySQL 与 Redis

```powershell
cd d:\CodeProjects\springaichat
docker compose up -d
```

| 服务 | 容器名 | 端口映射 | 说明 |
|------|--------|----------|------|
| MySQL 8.0 | `springaichat-mysql` | `3307 → 3306` | 库 `ai_chat_db`，root 密码默认 `123456`，数据卷持久化 |
| Redis Stack | `springaichat-redis` | `6379 → 6379` | 含 RediSearch，缓存 + 向量库共用 |

> 必须使用 **Redis Stack**（或自行加载 RediSearch 模块）。普通 `redis:8` 镜像没有 `FT.*` 命令，向量索引无法创建。

### 5.3 环境变量

| 变量 | 说明 | 默认值 / 备注 |
|------|------|---------------|
| `ALIYUN_API_KEY` | 阿里云百炼 API Key | 必填，`sk-` 开头；缺失会导致 embedding 401，启动失败 |
| `JWT_SECRET` | JWT 签名密钥 | 至少 32 字符；生产环境必须替换默认值 |
| `DB_URL` | JDBC 连接串 | 默认 `jdbc:mysql://localhost:3307/ai_chat_db?...` |
| `DB_USERNAME` | 数据库用户名 | 默认 `root` |
| `MYSQL_PASSWORD` | 数据库密码 | 默认 `123456`（与 docker-compose 一致） |
| `SPRING_PROFILES_ACTIVE` | 运行环境 | 默认 `dev` |

Windows 临时会话注入（避免读不到系统变量）：

```powershell
$env:ALIYUN_API_KEY = [Environment]::GetEnvironmentVariable('ALIYUN_API_KEY','User')
$env:ALIYUN_API_KEY.Length   # 应输出 117 左右的长度
```

### 5.4 启动后端与前端

```powershell
# 终端 1：后端
mvn spring-boot:run

# 终端 2：前端
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 注册账号后即可对话。

首次启动会自动扫描并向量化 `knowledge/` 下的 Markdown，需要有效的 API Key；后续启动按内容 Hash 跳过已入库文档。

### 5.5 Windows 一键脚本

```bat
start-all.bat
```

脚本会清理 8080 / 5173 端口占用、启动 Redis、再拉起后端与前端。

> 注意：脚本使用的是名为 `redis-stack` 的独立容器，与 `docker-compose.yml` 的 `springaichat-redis` 会争用 6379 端口，二者择一使用。

---

## 6. 关键配置（application.properties）

```properties
server.port=8080

# MySQL（Docker 映射到 3307）
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/ai_chat_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8}
spring.datasource.password=${MYSQL_PASSWORD:123456}
spring.jpa.hibernate.ddl-auto=update

# Redis（缓存 + 向量库）
spring.data.redis.host=localhost
spring.data.redis.port=6379

# 阿里云百炼（OpenAI 兼容）
spring.ai.openai.base-url=https://dashscope.aliyuncs.com/compatible-mode
spring.ai.openai.chat.options.model=qwen-plus
spring.ai.openai.embedding.options.model=text-embedding-v2

# Redis Vector Store
spring.ai.vectorstore.redis.index=chat_knowledge_index
spring.ai.vectorstore.redis.prefix=rag:vector:
spring.ai.vectorstore.redis.initialize-schema=true

# JWT 双 Token
jwt.access-expiration=1800000      # 30 分钟
jwt.refresh-expiration=604800000   # 7 天

# 聊天上下文
chat.max-history-size=20
chat.max-message-length=4000
chat.max-tokens=4096
chat.cache-expire-hours=24

# RAG
rag.enabled=true
rag.retrieval.top-k=3
rag.retrieval.similarity-threshold=0.30
rag.chunk.max-size=800
rag.chunk.overlap-size=200
rag.knowledge.directory=knowledge/
```

---

## 7. 数据库设计

JPA `ddl-auto=update` 自动建表，共 3 张表：

```
user (1) ──user_id──▶ conversation (1) ──conversation_id──▶ message
```

| 表 | 字段 |
|----|------|
| `user` | `id` PK、`username` UNIQUE、`password`(BCrypt)、`create_time` |
| `conversation` | `id` PK、`user_id`(INDEX)、`title`、`create_time`、`update_time` |
| `message` | `id` PK、`conversation_id`(INDEX)、`role`(user/assistant)、`content`(TEXT)、`create_time` |

---

## 8. 核心实现说明

### 8.1 JWT 双 Token

| Token | 有效期 | 用途 | 存储 |
|-------|--------|------|------|
| access | 30 分钟 | 调用业务接口 | localStorage `accessToken` → `Authorization: Bearer` |
| refresh | 7 天 | 仅用于换取新 access | localStorage + Redis `jwt:refresh:{username}` |

- Token 内含 `type` 声明与 `jti`；过滤器只接受 `type=access`
- refresh 时校验 Redis 绑定值，随后**换发新 refresh 覆盖旧值**（Rotation）
- 登出时删除 refresh 绑定，并把 access 的 `jti` 写入 `jwt:blacklist:{jti}`，TTL = 剩余有效期
- 前端 `axios.js`：401 时用 `pendingRequests` 队列挂起并发请求，只发起一次 refresh，成功后重放

### 8.2 SSE 流式链路

```
Chat.vue → useStreamChat.sendStream()
  → POST /api/chat/messages/stream (fetch + ReadableStream)
     → ChatController.streamMessage → ChatService.streamMessage
        1. 归属校验
        2. 异步：保存用户消息 / 更新会话标题（不阻塞首字）
        3. 读取 Redis 历史（miss 回源 MySQL 并回写）
        4. 双重裁剪（20 条 / 4096 Token）
        5. 检索 RAG → 拼 SystemMessage
        6. ChatClient.stream() → Flux<String>，失败最多重试 2 次
        7. doOnComplete：保存 AI 回答 + 回写 Redis
  → 前端按事件切分，重拼多行 data、逐字符打字机、实时 Markdown
```

**一个易错点**：Spring 会把含换行的文本编码成多行 `data:`，前端必须把同一事件内的多行用 `\n` 重新拼接，否则标题、列表等 Markdown 语法会失效。

### 8.3 上下文缓存

- 读：Redis 命中直接返回；未命中查 MySQL 并回写（TTL 24h）
- 写：流式结束后更新缓存
- 删：删除消息 / 会话时**删除**缓存并重试 2 次
- 裁剪：`size > 20` 或估算 Token > 4096 时，从 index=1 开始删除最早消息（index=0 为 SystemMessage）
- Token 估算：中文 ≈ 2 字符/Token，英文 ≈ 4 字符/Token，每条消息 +4

### 8.4 RAG

**加载**（启动时）：扫描 `knowledge/**/*.md` → 内容 MD5 去重 → TokenTextSplitter(800/200) → 附 metadata（`source`、`chunk_index`）→ `vectorStore.add()` 写入 `chat_knowledge_index`。

**检索**（每次提问）：`SearchRequest` topK=3、similarity=0.30，无租户过滤（当前 Redis 索引 schema 只有 `content` 与 `embedding` 字段，元数据不参与检索）。

**提示词**：要求以内部资料为依据、禁止编造；资料不足时说明「暂时没有查到相关内容」；**不输出**「参考资料」「来源」等引用字眼。

### 8.5 前端工程化

- 路由懒加载：`() => import('../views/Chat.vue')`
- `manualChunks`：`vendor-vue` / `vendor-element` / `vendor-axios` / `vendor-others`
- Chat.vue 拆分：`ConversationList`、`MessageItem`、`ContextMenu`、`InputArea` + `useStreamChat` composable
- Markdown 渲染：marked + marked-highlight + highlight.js（`lib/common` 按需）+ DOMPurify；流式期间 50ms 节流渲染
- 代码高亮样式：`highlight.js/styles/github.css`

---

## 9. API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/register` | POST | 注册 |
| `/api/auth/login` | POST | 登录，返回 `accessToken + refreshToken + expiresIn`（兼容 `token`） |
| `/api/auth/refresh` | POST | 用 refresh 换新双 Token（Rotation） |
| `/api/auth/logout` | POST | 登出（access 拉黑 + 删除 refresh 绑定） |
| `/api/chat/conversations` | GET | 会话列表 |
| `/api/chat/conversations` | POST | 创建会话 |
| `/api/chat/conversations/{id}` | GET | 会话详情（归属校验） |
| `/api/chat/conversations/{id}` | DELETE | 删除会话 + 消息 + 缓存 |
| `/api/chat/conversations/{id}/messages` | GET | 消息列表 |
| `/api/chat/messages` | POST | 同步发送消息 |
| `/api/chat/messages/stream` | POST | SSE 流式（`text/event-stream`） |
| `/api/chat/messages/stream/json` | POST | NDJSON 流式 |
| `/api/chat/messages/{id}` | DELETE | 删除单条消息 |
| `/api/chat/messages/batch-delete` | POST | 批量删除消息 |

统一响应结构：`{ success, message, data }`；流式接口直接返回事件流。

---

## 10. 常见问题

| 现象 | 原因 / 解决 |
|------|-------------|
| 启动时报 `cannot retry due to server authentication` | `ALIYUN_API_KEY` 无效或未注入，embedding 初始化 401 |
| 聊天返回 429 | 免费额度限流；本项目默认 `qwen-plus`，仍触发可稍后重试 |
| 回答「暂时没有查到相关内容」 | 知识库无匹配内容，可降低 `rag.retrieval.similarity-threshold` 或补充 `knowledge/` 文档 |
| Redis 报 `unknown command 'FT.CREATE'` | 使用了普通 Redis 镜像，需换成 Redis Stack |
| 前端连不上后端 / 跨域 | 后端需运行在 8080，CorsConfig 仅放行 5173 |

---

## 11. 已知限制

- Redis Vector Store 检索结果不返回 `source` 元数据，因此不展示引用来源文件
- 知识库仅在启动时增量加载（按内容 Hash 去重），运行期新增文档需重启
- `start-all.bat` 与 `docker-compose.yml` 都会占用 6379，需择一
- 无单元测试 / 集成测试覆盖

---

## 12. 许可证

MIT License
