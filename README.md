# 企智通 (Enterprise RAG Assistant)

基于 Spring AI + Redis Vector Store 的企业智能知识问答系统，支持与大语言模型进行对话交互。

## 功能特性

- ✅ 用户注册与登录（JWT 认证）
- ✅ 用户个人信息管理（昵称、邮箱、手机、头像、性别、简介）
- ✅ 智能对话交互（支持阿里云百炼模型）
- ✅ 多轮对话历史管理（会话隔离）
- ✅ 对话上下文保持（Redis 缓存优化）
- ✅ SSE 流式输出（打字机效果，支持暂停/继续）
- ✅ 消息管理（单条删除、批量删除、右键菜单）
- ✅ 侧边栏折叠/展开（响应式布局）
- ✅ 缓存预热与一致性保障
- ✅ 双重上下文限制（消息数量 + Token 数量）
- ✅ 网络重试机制（Connection reset 等自动重试）
- ✅ RAG 检索增强（Redis Vector Store 向量检索）
- ✅ 知识库文档加载（Markdown 文档自动向量化存储）
- ✅ 语义文档分块（TokenTextSplitter 智能切分）

## 技术栈

- **后端框架**: Spring Boot 3.2.x
- **AI 框架**: Spring AI 1.0.0-M4
- **向量存储**: Redis Vector Store (RediSearch)
- **数据库**: MySQL 8.0+ + Redis 8.0+
- **安全框架**: Spring Security + JWT
- **前端**: Vue 3 + Vite + Element Plus
- **构建工具**: Maven

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 8.0+（推荐使用 Redis Stack，需包含 RediSearch 模块）
- Node.js 18+（前端）

### 数据库配置

1. 创建 MySQL 数据库：
```sql
CREATE DATABASE ai_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 确保 Redis Stack 服务运行在默认端口 6379（需要 RediSearch 模块支持向量检索）

### 环境变量配置

在启动前，需要配置以下环境变量：

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `DB_URL` | 数据库连接地址 | jdbc:mysql://localhost:3306/ai_chat_db |
| `DB_USERNAME` | 数据库用户名 | root |
| `MYSQL_PASSWORD` | 数据库密码 | （空） |
| `ALIYUN_API_KEY` | 阿里云百炼 API Key | （必填） |
| `JWT_SECRET` | JWT 密钥（至少32字符） | （必填） |

### RAG 配置说明

系统支持基于 Redis Vector Store 的 RAG（检索增强生成）功能，相关配置项：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `rag.enabled` | 是否启用 RAG 功能 | true |
| `rag.retrieval.top-k` | 向量检索返回的文档数 | 3 |
| `rag.retrieval.similarity-threshold` | 相似度阈值（0-1） | 0.1 |
| `rag.chunk.max-size` | 文档分块大小（Token） | 800 |
| `rag.chunk.overlap-size` | 分块重叠大小（Token） | 200 |
| `rag.knowledge.directory` | 知识库文档目录 | knowledge/ |

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

#### 方式三：使用启动脚本

```bash
# Windows
start-all.bat
```

### API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/login` | POST | 用户登录 |
| `/api/chat/conversations` | GET | 获取会话列表 |
| `/api/chat/conversations` | POST | 创建会话 |
| `/api/chat/conversations/{id}` | GET | 获取会话详情 |
| `/api/chat/conversations/{id}` | DELETE | 删除会话 |
| `/api/chat/conversations/{id}/messages` | GET | 获取会话消息 |
| `/api/chat/messages` | POST | 同步发送消息 |
| `/api/chat/messages/stream` | POST | 流式发送消息（SSE） |
| `/api/chat/messages/{id}` | DELETE | 删除单条消息 |
| `/api/chat/messages/batch-delete` | POST | 批量删除消息 |

### 项目结构

```
enterprise-rag/
├── src/main/java/com/example/springaichat/
│   ├── config/         # 配置类（安全、跨域、JWT、Redis、AI、RAG）
│   ├── controller/     # REST API 控制层
│   ├── service/        # 业务逻辑层（含 RAG 知识库服务）
│   ├── repository/     # 数据访问层
│   ├── entity/         # 数据库实体
│   ├── dto/            # 数据传输对象
│   ├── exception/      # 全局异常处理
│   ├── util/           # 工具类
│   └── SpringAiChatApplication.java
├── src/main/resources/
│   ├── application.properties # 应用配置
│   └── knowledge/              # RAG 知识库文档目录（Markdown 格式）
├── frontend/           # 前端代码
│   ├── src/
│   │   ├── api/        # API配置（config.js）
│   │   ├── router/     # 路由配置
│   │   ├── utils/      # 工具类（axios配置）
│   │   ├── views/      # 页面（Chat、Login）
│   │   ├── App.vue     # 根组件
│   │   ├── main.js     # 入口文件
│   │   └── style.css   # 全局样式
│   └── package.json
├── pom.xml             # Maven 配置
├── start-all.bat       # 启动脚本（Windows）
└── README.md           # 项目说明
```

## 配置说明

### application.properties 主要配置项

```properties
# 服务器配置
server.port=8080
spring.application.name=enterprise-rag

# Spring AI 配置（阿里云百炼）
spring.ai.openai.api-key=${ALIYUN_API_KEY}
spring.ai.openai.base-url=https://dashscope.aliyuncs.com/compatible-mode
spring.ai.openai.chat.options.model=qwen-turbo
spring.ai.openai.chat.options.temperature=0.7
spring.ai.openai.chat.options.max-tokens=2048
spring.ai.openai.embedding.options.model=text-embedding-v2

# Redis Vector Store 配置（RAG）
spring.ai.vectorstore.redis.uri=redis://localhost:6379
spring.ai.vectorstore.redis.index=chat_knowledge_index
spring.ai.vectorstore.redis.initialize-schema=true
spring.ai.vectorstore.redis.prefix=rag:vector:

# 聊天配置
chat.max-history-size=20
chat.max-message-length=4000
chat.max-tokens=4096
chat.cache-expire-hours=24

# RAG 配置
rag.enabled=true
rag.retrieval.top-k=3
rag.retrieval.similarity-threshold=0.1
rag.chunk.max-size=800
rag.chunk.overlap-size=200
rag.knowledge.directory=knowledge/
```

## 核心功能说明

### SSE 流式输出

- 后端使用 `Flux<String>` 返回流式数据
- 前端使用 `fetch` + `ReadableStream` 逐字接收
- 支持暂停/继续功能（点击 AI 消息右下角按钮）
- 打字机效果（默认 30ms/字符，可配置）

### 上下文缓存机制

- **缓存策略**: Cache-Aside 模式，MySQL 为唯一数据源
- **缓存分层**: Redis 存储活跃上下文，MySQL 存储完整历史
- **双重限制**: 最多 20 条消息 + 最多 4096 Token
- **缓存预热**: 首次打开旧会话时自动从数据库加载
- **一致性保障**: 重试机制 + 24h 过期 + 读时回源 + 写时覆盖

### 消息删除

- 右键点击消息弹出菜单
- 支持单条删除和批量删除
- 删除时同步清理 Redis 缓存（带重试机制）

### RAG 检索增强

- **知识库加载**: 启动时自动从 `classpath:knowledge/` 目录加载 Markdown 文档
- **语义分块**: 使用 `TokenTextSplitter` 按 Token 智能切分文档（默认 800 Token/块，200 Token 重叠）
- **向量存储**: 使用 Redis Vector Store 存储文档向量，自动创建 RediSearch 索引
- **检索增强**: 提问时自动从向量库检索相关知识，增强 AI 回答的准确性和可信度
- **配置开关**: 通过 `rag.enabled` 配置项控制 RAG 功能启用/禁用

## 开发指南

### 添加新功能

1. 创建实体类（entity）
2. 创建 Repository 接口
3. 创建 Service 层
4. 创建 Controller 层
5. 添加 DTO（请求/响应对象）
6. 更新前端页面

### 代码规范

- 遵循 Spring Boot 最佳实践
- 使用 DTO 隔离数据库实体
- 统一异常处理
- 禁止硬编码敏感信息

## 部署说明

### 生产环境部署

1. 配置环境变量
2. 使用 `mvn clean package` 打包
3. 使用以下命令启动：
```bash
java -jar enterprise-rag-1.0.0.jar --spring.profiles.active=prod
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