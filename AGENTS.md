# AGENTS.md

## 1. 项目基础概述

### 1.1 项目信息
- **项目名称**: springaichat
- **项目用途**: Spring Boot + Spring AI 智能对话助手，支持多用户会话隔离、历史记录、上下文记忆和流式输出
- **技术栈**:
  - Java 17
  - Spring Boot 3.2.10
  - Spring AI 1.0.0-M4
  - Spring Security + JWT
  - Spring Data JPA
  - Spring Data Redis
  - MySQL 8.0+
  - Vue 3 + Vite + Element Plus
  - Maven 构建工具
- **MCP工具**:
  - `mysql-aichat`: MySQL数据库访问
  - `filesystem`: 文件系统访问
  - `codegraph`: 代码分析

### 1.2 项目核心业务目标
1. 多用户认证与授权（注册、登录、JWT令牌）
2. 会话管理（创建、查询、删除会话）
3. 消息交互（发送消息、接收AI响应）
4. SSE流式输出（打字机效果实时响应）
5. 消息管理（单条删除、批量删除）
6. 上下文记忆（Redis缓存聊天历史）

---

## 2. 数据库完整设计

### 2.1 所有数据表清单
| 表名 | 业务用途 |
|------|----------|
| `user` | 用户信息表，存储用户账号密码 |
| `conversation` | 会话信息表，存储用户的聊天会话 |
| `message` | 消息信息表，存储会话中的消息内容 |

### 2.2 表结构详细说明

#### 表：user（用户表）

| 字段名 | 类型 | 是否为空 | 键类型 | 默认值 | 注释 |
|--------|------|----------|--------|--------|------|
| `id` | bigint | NO | PRIMARY KEY | - | 用户ID，自增主键 |
| `username` | varchar(50) | NO | UNIQUE KEY | - | 用户名，唯一 |
| `password` | varchar(100) | NO | - | - | 密码（BCrypt加密） |
| `create_time` | datetime | YES | - | CURRENT_TIMESTAMP | 创建时间 |

**业务作用**: 存储系统用户的基本信息，用于认证和授权。

---

#### 表：conversation（会话表）

| 字段名 | 类型 | 是否为空 | 键类型 | 默认值 | 注释 |
|--------|------|----------|--------|--------|------|
| `id` | bigint | NO | PRIMARY KEY | - | 会话ID，自增主键 |
| `user_id` | bigint | NO | MULTIPLE KEY | - | 用户ID，关联user表 |
| `title` | varchar(100) | YES | - | 新对话 | 会话标题 |
| `create_time` | datetime | YES | - | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | datetime | YES | - | CURRENT_TIMESTAMP on update | 更新时间 |

**业务作用**: 存储用户的聊天会话，支持会话隔离。

---

#### 表：message（消息表）

| 字段名 | 类型 | 是否为空 | 键类型 | 默认值 | 注释 |
|--------|------|----------|--------|--------|------|
| `id` | bigint | NO | PRIMARY KEY | - | 消息ID，自增主键 |
| `conversation_id` | bigint | NO | MULTIPLE KEY | - | 会话ID，关联conversation表 |
| `role` | varchar(20) | NO | - | - | 角色（user/assistant） |
| `content` | text | NO | - | - | 消息内容 |
| `create_time` | datetime | YES | - | CURRENT_TIMESTAMP | 创建时间 |

**业务作用**: 存储会话中的消息内容，包括用户提问和AI回答。

### 2.3 表关联关系

```
user (1) ────(*) conversation (1) ────(*) message
          │                        │
          │─── userId (外键)       │─── conversation_id (外键)
          │                        │
          └─── 一对多关系           └─── 一对多关系
```

| 关系 | 主表 | 从表 | 外键字段 | 关系类型 |
|------|------|------|----------|----------|
| 用户-会话 | user | conversation | `user_id` | 一对多 |
| 会话-消息 | conversation | message | `conversation_id` | 一对多 |

---

## 3. 代码分层架构说明

### 3.1 包分层结构

```
src/main/java/com/example/springaichat/
├── config/           # 配置类（安全、跨域、JWT、Redis、AI）
├── controller/       # REST控制器（认证、聊天）
├── dto/              # 数据传输对象（请求/响应）
├── entity/           # JPA实体类（User、Conversation、Message）
├── exception/        # 全局异常处理
├── repository/       # 数据访问层（JPA Repository）
├── service/          # 业务逻辑层（认证服务、聊天服务）
├── util/             # 工具类（JWT工具）
└── SpringAiChatApplication.java  # 启动类
```

### 3.2 各层职责说明

| 层级 | 包名 | 核心类 | 职责 |
|------|------|--------|------|
| 控制层 | controller | `AuthController`、`ChatController` | 处理HTTP请求，参数校验，调用Service |
| 业务层 | service | `AuthService`、`ChatService` | 核心业务逻辑，事务管理，AI调用 |
| 数据层 | repository | `UserRepository`、`ConversationRepository`、`MessageRepository` | 数据库CRUD操作 |
| 实体层 | entity | `User`、`Conversation`、`Message` | JPA实体，映射数据库表 |
| 传输层 | dto | `LoginRequest`、`MessageRequest`、`MessageResponse` 等 | 请求/响应数据结构 |
| 配置层 | config | `SecurityConfig`、`JwtAuthenticationFilter`、`CorsConfig`、`RedisConfig`、`OpenAiChatConfig` | Spring配置、安全、跨域、缓存、AI模型 |
| 工具层 | util | `JwtUtil` | JWT令牌生成与验证 |
| 异常层 | exception | `GlobalExceptionHandler` | 全局异常处理 |

### 3.3 全局配置文件解读

#### application.yml 关键配置

| 配置项 | 说明 |
|--------|------|
| `server.port` | 服务端口：8080 |
| `spring.datasource.*` | MySQL数据源配置，密码通过环境变量 `${MYSQL_PASSWORD}` 注入 |
| `spring.jpa.hibernate.ddl-auto` | 自动更新表结构：update |
| `spring.data.redis.*` | Redis缓存配置（localhost:6379） |
| `spring.ai.openai.*` | Spring AI配置（阿里云百炼兼容OpenAI接口） |
| `jwt.secret` | JWT密钥（生产环境需替换为强随机密钥） |
| `jwt.expiration` | JWT过期时间：86400000ms（24小时） |
| `chat.max-history-size` | 最大历史消息数：20 |
| `chat.max-message-length` | 最大消息长度：4000字符 |
| `chat.cache-expire-hours` | Redis缓存过期时间：24小时 |

---

## 4. 核心业务流程

### 4.1 用户认证流程

```
用户请求 → AuthController → AuthService → UserRepository → MySQL
    │              │              │                    │
    │              │              └──→ PasswordEncoder (密码加密/验证)
    │              │              │
    │              │              └──→ JwtUtil (生成令牌)
    │              │
    │              └──→ 返回 JWT Token
    │
    └──→ 前端存储Token，后续请求携带Authorization头
```

**接口链路**:
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录

### 4.2 聊天会话流程

```
用户请求 → ChatController → ChatService → AI模型 → 返回流式响应
             │                │              │
             │                │              └──→ Spring AI OpenAI客户端
             │                │
             │                ├──→ ConversationRepository (会话CRUD)
             │                ├──→ MessageRepository (消息CRUD)
             │                └──→ RedisTemplate (缓存聊天历史)
```

**接口链路**:
- `POST /api/chat/conversations` - 创建会话
- `GET /api/chat/conversations` - 获取所有会话
- `GET /api/chat/conversations/{id}` - 获取会话详情
- `DELETE /api/chat/conversations/{id}` - 删除会话

### 4.3 消息发送流程（流式输出）

```
前端发送消息 → POST /api/chat/messages/stream
                    │
                    ▼
              ChatController.streamMessage()
                    │
                    ▼
              ChatService.streamMessage()
                    │
                    ├── 1. 验证会话归属
                    ├── 2. 保存用户消息到数据库
                    ├── 3. 获取Redis缓存的聊天历史
                    ├── 4. 调用AI模型stream方法
                    ├── 5. Flux流式返回内容片段
                    │
                    ▼
              前端EventSource接收 → 逐字追加显示（打字机效果）
                    │
                    ▼
              流结束 → 保存完整AI响应到数据库
```

**接口链路**:
- `POST /api/chat/messages` - 同步发送消息
- `POST /api/chat/messages/stream` - 流式发送消息（SSE）
- `GET /api/chat/conversations/{id}/messages` - 获取会话消息

### 4.4 消息删除流程

```
用户请求删除 → ChatController → ChatService → MessageRepository → MySQL
                    │                │                    │
                    │                └──→ 验证消息归属（会话→用户）
                    │
                    ▼
              返回删除结果
```

**接口链路**:
- `DELETE /api/chat/messages/{id}` - 删除单条消息
- `POST /api/chat/messages/batch-delete` - 批量删除消息

---

## 5. MCP工具能力说明

### 5.1 filesystem（文件系统服务）
- **作用**: 读取、修改项目文件
- **约束**: 仅访问当前项目目录 `${workspaceFolder}`，禁止跨项目访问

### 5.2 mysql-aichat（MySQL数据库服务）
- **作用**: 访问项目专属数据库 `ai_chat_db`
- **连接配置**:
  - 主机：127.0.0.1
  - 端口：3306
  - 用户名：root
  - 数据库：ai_chat_db
- **约束**: 仅操作本项目数据库，不连接其他项目数据库

### 5.3 codegraph（代码分析服务）
- **作用**: 代码符号搜索、调用链分析、代码结构浏览
- **约束**: 仅分析当前项目代码

---

## 6. AI代理行为约束规则

### 6.1 文件访问约束
1. 仅允许读取、修改当前项目 `springaichat` 内的代码文件
2. 禁止跨目录访问其他项目文件
3. 修改代码前先查阅 AGENTS.md 确认业务逻辑和架构

### 6.2 数据库操作约束
1. 仅使用 `mysql-aichat` 服务连接本项目数据库 `ai_chat_db`
2. 禁止连接其他项目的数据库
3. 数据库操作需遵循原有表结构和关联关系

### 6.3 代码修改约束
1. 所有代码修改遵循项目原有编码规范
2. 保持分层架构清晰，不跨层调用
3. 新增功能需与现有模块风格一致
4. 修改前需验证不破坏现有功能

### 6.4 安全约束
1. 禁止在代码中硬编码敏感信息（密码、API Key）
2. 敏感配置需通过环境变量注入
3. 遵循 Spring Security 权限控制规则