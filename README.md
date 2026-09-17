# 企智通 (Enterprise RAG Assistant)

基于 Spring AI + Redis Vector Store 的企业智能知识问答系统，支持与大语言模型进行对话交互。

## 功能特性

- ✅ 用户注册与登录（JWT 双 Token 方案：access 30 分钟 + refresh 7 天，支持无感刷新 + 登出黑名单）
- ✅ 智能对话交互（支持阿里云百炼 qwen-turbo 模型）
- ✅ 多轮对话历史管理（按用户会话隔离）
- ✅ 对话上下文保持（Redis 缓存 + 双重限制）
- ✅ SSE 流式输出（打字机效果，支持暂停/继续）
- ✅ 消息管理（单条删除、批量删除、右键菜单）
- ✅ 侧边栏折叠/展开（响应式布局）
- ✅ 缓存预热与一致性保障（Cache-Aside + 重试 + 过期回源）
- ✅ 双重上下文限制（消息数量 20 条 + Token 数量 4096）
- ✅ 网络重试机制（Connection reset 等场景自动重试最多 2 次）
- ✅ 首字响应延迟优化（用户消息与会话标题更新异步写库）
- ✅ RAG 检索增强（Redis Vector Store 余弦相似度检索）
- ✅ 知识库文档加载（启动时自动扫描 classpath:knowledge/ Markdown 文档，Hash 去重）
- ✅ 语义文档分块（TokenTextSplitter：800 Token/块，200 Token 重叠）
- ✅ 租户级检索过滤（元数据 tenant=asset）
- ✅ 业务层数据隔离（userId 从 Token 解析，防止越权访问）

## 技术栈

- **后端框架**: Spring Boot 3.2.10
- **AI 框架**: Spring AI 1.0.0-M4
- **向量存储**: Redis Vector Store (RediSearch 模块)
- **数据库**: MySQL 8.0+（持久化） + Redis 8.0+（缓存 + 向量）
- **安全框架**: Spring Security + JWT
- **前端**: Vue 3 + Vite + Element Plus
- **构建工具**: Maven 3.8+

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 8.0+（需要 RediSearch 模块支持向量检索，Redis Stack 或自定义加载）
- Node.js 18+（前端）

### 数据库配置

1. 创建 MySQL 数据库：
```sql
CREATE DATABASE ai_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 确保 Redis 服务运行在 `localhost:6379`，并已加载 RediSearch 模块（向量索引初始化依赖 `FT.*` 命令）

### 环境变量配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `DB_URL` | 数据库连接地址 | jdbc:mysql://localhost:3306/ai_chat_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8 |
| `DB_USERNAME` | 数据库用户名 | root |
| `MYSQL_PASSWORD` | 数据库密码 | （空） |
| `ALIYUN_API_KEY` | 阿里云百炼兼容模式 API Key | （必填，生产环境必须配置） |
| `JWT_SECRET` | JWT 签名密钥（至少 32 字符） | （必填，生产环境必须配置） |

### RAG 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `rag.enabled` | 是否启用 RAG 检索增强功能 | true |
| `rag.retrieval.top-k` | 向量检索返回文档数 | 3 |
| `rag.retrieval.similarity-threshold` | 余弦相似度阈值（0-1，中文建议 0.4~0.5） | 0.45 |
| `rag.chunk.max-size` | 文档分块大小（Token） | 800 |
| `rag.chunk.overlap-size` | 分块重叠大小（Token） | 200 |
| `rag.knowledge.directory` | 知识库 Markdown 文档目录（classpath） | knowledge/ |
| `rag.knowledge.default-tenant` | 知识库元数据默认租户，用于检索过滤 | asset |

### 启动方式

#### 方式一：使用 Maven（开发模式）

```bash
# 后端启动
mvn spring-boot:run

# 前端启动（另一个终端）
cd frontend
npm install
npm run dev
```

#### 方式二：打包运行

```bash
# 构建后端
mvn clean package

# 运行
java -jar target/enterprise-rag-1.0.0.jar
```

#### 方式三：使用启动脚本（Windows）

```bat
start-all.bat
```

### API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/login` | POST | 用户登录，返回 `accessToken + refreshToken + expiresIn`（兼容旧字段 `token`） |
| `/api/auth/refresh` | POST | 用 refresh 换取新的 access + 新 refresh（Token Rotation） |
| `/api/auth/logout` | POST | 登出（需登录态）：access 拉黑 + 删除绑定的 refresh |
| `/api/chat/conversations` | GET | 获取当前用户的会话列表（按更新时间倒序） |
| `/api/chat/conversations` | POST | 创建会话（title 可选，默认「新对话」） |
| `/api/chat/conversations/{id}` | GET | 获取单个会话详情（验证归属） |
| `/api/chat/conversations/{id}` | DELETE | 删除会话及所有消息，并删除 Redis 缓存 |
| `/api/chat/conversations/{id}/messages` | GET | 获取会话的消息列表（验证归属） |
| `/api/chat/messages` | POST | 同步发送消息并等待完整回复 |
| `/api/chat/messages/stream` | POST | 流式发送消息（SSE，`Content-Type: text/event-stream`） |
| `/api/chat/messages/stream/json` | POST | 流式发送消息（NDJSON 格式，`Content-Type: application/x-ndjson`） |
| `/api/chat/messages/{id}` | DELETE | 删除单条消息（验证归属 + 删除 Redis 缓存） |
| `/api/chat/messages/batch-delete` | POST | 批量删除消息（消息归属校验 + 批量缓存清理重试） |

### 项目结构

```
enterprise-rag/
├── src/main/java/com/example/springaichat/
│   ├── config/
│   │   ├── SecurityConfig              # Spring Security + CORS + 放行 OPTIONS
│   │   ├── JwtAuthenticationFilter     # JWT 校验过滤器，解析 userId 写入 SecurityContext
│   │   ├── CorsConfig                  # 允许 5173 跨域、允许 Authorization 头
│   │   ├── RedisConfig                 # RedisTemplate 序列化配置
│   │   ├── OpenAiChatConfig            # ChatClient Bean（@Primary，qwen-turbo），仅挂 SimpleLoggerAdvisor；RAG 检索由 ChatService 手动执行
│   │   └── RagConfig                   # VectorStore（RAG 开关）；检索统一由 ChatService 手动执行并带 tenant 过滤
│   ├── controller/
│   │   ├── AuthController              # 注册、登录、刷新双 Token、登出拉黑
│   │   └── ChatController              # 会话 CRUD、消息 CRUD、SSE、NDJSON
│   ├── service/
│   │   ├── AuthService                 # 认证业务：双 Token 签发 + refresh rotation + 登出
│   │   ├── TokenStoreService           # Redis：refresh 绑定 + access 黑名单（TTL 剩余有效期）
│   │   ├── ChatService                 # 核心聊天逻辑 + 缓存 + RAG 检索 + 异步落库 + 流式重试
│   │   └── KnowledgeBaseService        # 知识库启动加载 + 分块 + 向量化 + Hash 去重
│   ├── repository/
│   │   ├── UserRepository
│   │   ├── ConversationRepository
│   │   └── MessageRepository
│   ├── entity/
│   │   ├── User                        # id / username / password / createTime
│   │   ├── Conversation                # id / userId / title / createTime / updateTime
│   │   └── Message                     # id / conversationId / role / content / createTime
│   ├── dto/
│   │   ├── LoginRequest / LoginResponse  # LoginResponse 含 accessToken/refreshToken/expiresIn（兼容旧 token 字段）
│   │   ├── RefreshTokenRequest
│   │   ├── RegisterRequest
│   │   ├── MessageRequest / MessageResponse
│   │   ├── ConversationResponse
│   │   └── BatchDeleteRequest
│   ├── exception/  GlobalExceptionHandler
│   ├── util/       JwtUtil              # 双 Token：type 声明 + jti 指纹 + 独立过期时间
│   └── SpringAiChatApplication.java
├── src/main/resources/
│   ├── application.properties          # 应用主配置
│   └── knowledge/                      # RAG 知识库目录（*.md，启动自动加载）
├── frontend/
│   ├── src/
│   │   ├── api/config.js
│   │   ├── router/index.js             # /  → Login.vue ; /chat → Chat.vue
│   │   ├── utils/axios.js              # 拦截器自动注入 Authorization 头
│   │   ├── views/
│   │   │   ├── Chat.vue                # 主聊天页面（SSE 流、打字机、右键菜单、批量删除）
│   │   │   └── Login.vue               # 登录 / 注册
│   │   ├── App.vue
│   │   ├── main.js
│   │   └── style.css
│   └── package.json
├── pom.xml                             # Maven 配置（artifactId: enterprise-rag）
├── start-all.bat                       # Windows 一键启动脚本（Redis → 后端 → 前端）
├── AGENTS.md                           # 开发者/Agent 工作指引
└── README.md                           # 项目说明
```

## 配置说明

### application.properties 主要配置项

```properties
# 服务器
server.port=8080
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}
spring.application.name=enterprise-rag

# MySQL（Hikari 连接池）
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/ai_chat_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${MYSQL_PASSWORD:}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Redis（缓存 + 向量库共用）
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0

# Spring AI - 阿里云百炼（兼容 OpenAI 接口）
spring.ai.openai.api-key=${ALIYUN_API_KEY:sk-your-api-key-here}
spring.ai.openai.base-url=https://dashscope.aliyuncs.com/compatible-mode
spring.ai.openai.chat.options.model=qwen-turbo
spring.ai.openai.chat.options.temperature=0.7
spring.ai.openai.chat.options.max-tokens=2048
spring.ai.openai.embedding.options.model=text-embedding-v2

# Redis Vector Store
spring.ai.vectorstore.redis.uri=redis://localhost:6379
spring.ai.vectorstore.redis.index=chat_knowledge_index
spring.ai.vectorstore.redis.initialize-schema=true
spring.ai.vectorstore.redis.prefix=rag:vector:

# JWT（双 Token 方案）
jwt.secret=${JWT_SECRET:your-production-secret-key-must-be-at-least-32-characters-long}
jwt.access-expiration=1800000          # access Token：30 分钟
jwt.refresh-expiration=604800000        # refresh Token：7 天
jwt.redis.refresh-prefix=jwt:refresh:   # Redis 存 refresh Token（按 username 绑定）
jwt.redis.blacklist-prefix=jwt:blacklist:  # Redis 存登出后 access 的 jti 黑名单（TTL = 剩余有效期）

# 聊天上下文
chat.max-history-size=20
chat.max-message-length=4000
chat.max-tokens=4096
chat.cache-expire-hours=24

# RAG
rag.enabled=true
rag.retrieval.top-k=3
rag.retrieval.similarity-threshold=0.45
rag.chunk.max-size=800
rag.chunk.overlap-size=200
rag.knowledge.directory=knowledge/
rag.knowledge.default-tenant=asset
```

## 核心功能说明

### SSE 流式输出与首字延迟优化

- 后端：ChatService 以 `Flux<String>` 返回流式数据，对 `Connection reset` 等网络抖动实现最多 2 次自动重试
- 前端：`fetch` + `ReadableStream.getReader()` 逐段接收，按字符追加实现打字机效果（默认 10ms/字符，`typingSpeed` 可调）
- UI：AI 消息气泡右下角提供「暂停 / 继续」控制按钮，流式状态同步显示
- **首字优化**：用户消息 `saveUserMessageAsync` 和会话标题 `updateConversationTitleAsync` 均通过 `CompletableFuture.runAsync` 异步写入 MySQL，主流程直接开始调用 AI 模型流式接口，用户不用等数据库
- 断连处理：try-catch 包住读取循环，捕获异常弹提示 + 清理占位消息，finally 重置 `isStreaming` 标志

### 上下文缓存机制（Cache-Aside）

- 读取：先查 Redis `chat:history:{conversationId}`，miss 则查 MySQL → 回写 Redis（TTL 24h）
- 写入：先写 MySQL → 删除 Redis 缓存（删除失败最多重试 2 次，间隔递增）
- 双重限制：trimChatHistory 按「最多 20 条」+「累计最多 4096 Token」双重裁剪，从索引 1 开始删除最早消息（索引 0 是 SystemMessage 保留）
- Token 估算：中文 ≈ 2 字符/Token，英文 ≈ 4 字符/Token，消息角色元数据加 4 Token
- 一致性兜底：缓存 24h 自动过期；Redis 读异常则降级走 MySQL

### 消息删除

- 右键菜单触发单条删除；多选触发批量删除
- 删除前先通过 `消息 → 会话 → user_id` 链路校验归属，不匹配直接拒绝，防止越权
- 删除成功后同步清理 Redis 聊天历史缓存（带重试）

### RAG 检索增强

1. **加载**：应用启动时 `KnowledgeBaseService` 扫描 `classpath:knowledge/` 下所有 Markdown 文件，用文件路径 Hash 去重，避免重复向量化
2. **分块**：`TokenTextSplitter(chunkMaxSize=800, overlapSize=200)` 按 Token 级切分，相邻块 200 Token 接力覆盖语义边界
3. **打标**：每个 Document 附带 metadata：`tenant=asset`、`source=文件名`、`chunk_index`
4. **向量化**：`EmbeddingModel` 调用阿里云 `text-embedding-v2` 生成向量
5. **存储**：写入 Redis Vector Store，RediSearch 索引名 `chat_knowledge_index`，Key 前缀 `rag:vector:`
6. **检索**：用户提问时 `ChatService.retrieveRagContext` 手动调用 `RedisVectorStore.similaritySearch`，topK=3，相似度阈值=0.45，并按 `tenant == 'asset'` 过滤（唯一检索入口，不依赖框架 Advisor 自动检索）
7. **生成**：检索到的文档片段拼进 SystemMessage，要求模型回答必须基于参考资料，不能编造；无检索结果时退回纯模型回答

### JWT 双 Token 认证方案

原 24h 单 Token 已升级为「access + refresh」双 Token 机制：

| Token | 有效期 | 作用 | 存储位置 |
|---|---|---|---|
| access | 30 分钟 | 调用业务接口的「短期凭证」 | 前端 localStorage `accessToken`，请求头 `Authorization: Bearer xxx` |
| refresh | 7 天 | 只用来「换新 access」，绝不允许直接调业务接口 | 前端 localStorage `refreshToken` + 后端 Redis（`jwt:refresh:{username}`） |

**安全机制**：
1. **类型隔离**：Token 内部带 `type=access|refresh` 声明，JwtAuthenticationFilter 显式拒绝拿 refresh 调 `/api/chat/**`
2. **Token Rotation**：每次 `/api/auth/refresh` 成功都会**生成新的 refresh Token** 覆盖 Redis 绑定，旧 refresh 立即失效（防窃取后反复使用）
3. **并发登录踢旧**：同一用户重新登录，Redis `jwt:refresh:{username}` 覆盖旧值 → 旧端 refresh 必失败
4. **登出双销毁**：`/api/auth/logout` 执行两步：
   - 删除该 username 绑定的 refresh Token（后续 refresh 必失败）
   - 该 access 的 `jti` 写进 Redis 黑名单，TTL = 该 Token 剩余有效期（30 分钟内即使 access 未过期也作废）
5. **Redis 故障兜底**：黑名单查询失败时「保守放行」（比全站登不上更可接受）；refresh/删除失败返回明确错误码

**前端无感刷新流程**：
```
业务请求 → 后端返回 401
   │
   ▼
有 refreshToken？
   ├── 否 → 清本地 → 跳 /login
   └── 是 → isRefreshing 锁，POST /api/auth/refresh
              ├── 成功 → 存新双 token → 重放失败请求 + 队列里并发的失败请求
              └── 失败 → 清本地 → 跳 /login
```
SSE fetch 401 同样在 Chat.vue 里做了 inline refresh 兜底（成功提示用户重发，失败跳登录）。

### 业务层数据隔离

- JWT 过滤器解析出 userId → 存入 SecurityContext
- Controller 层 `@AuthenticationPrincipal User user` 取用户，所有查询条件 `WHERE user_id = ?`
- 敏感操作（删会话、删消息）先查归属再执行，userId 来自 Token 不是请求体，前端无法伪造

## 开发指南

### 添加新功能

1. 在 `entity` 创建/调整 JPA 实体（注意字段非空与长度约束）
2. 在 `repository` 继承 `JpaRepository`
3. 在 `service` 写业务逻辑，注意数据隔离
4. 在 `controller` 对外暴露 REST API，统一返回结构 `{ success, message, data }`
5. 在 `dto` 增加对应请求/响应对象
6. 同步修改前端页面与路由

### 代码规范

- 分层清晰：Controller → Service → Repository → Entity；不跨层调用
- 数据传输一律通过 DTO，不直接把 Entity 返回前端
- `GlobalExceptionHandler` 统一异常处理，避免堆栈泄漏
- 敏感配置走环境变量，禁止硬编码

## 部署说明

### 生产环境部署

```bash
# 打包
mvn clean package -DskipTests

# 启动（必填环境变量）
export ALIYUN_API_KEY="sk-xxxx"
export JWT_SECRET="至少32字符强随机密钥"
export MYSQL_PASSWORD="your-db-password"
java -jar target/enterprise-rag-1.0.0.jar --spring.profiles.active=prod
```

### Docker 部署（可选）

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/enterprise-rag-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
