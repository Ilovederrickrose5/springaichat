# Spring AI Chat

基于 Spring Boot + Spring AI 构建的智能聊天助手应用，支持与大语言模型进行对话交互。

## 技术栈

- **后端框架**: Spring Boot 3.2.x
- **AI 框架**: Spring AI 1.0.0-M4
- **数据库**: MySQL 8.0+ + Redis 7.0+
- **安全框架**: Spring Security + JWT
- **前端**: Vue 3 + Vite
- **构建工具**: Maven

## 功能特性

- ✅ 用户注册与登录（JWT 认证）
- ✅ 智能对话交互
- ✅ 多轮对话历史管理
- ✅ 对话上下文保持
- ✅ 支持阿里云百炼模型
- ✅ 消息缓存优化

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.0+
- Node.js 18+（前端）

### 数据库配置

1. 创建 MySQL 数据库：
```sql
CREATE DATABASE ai_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 确保 Redis 服务运行在默认端口 6379

### 环境变量配置

在启动前，需要配置以下环境变量：

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `DB_URL` | 数据库连接地址 | jdbc:mysql://localhost:3306/ai_chat_db |
| `DB_USERNAME` | 数据库用户名 | root |
| `DB_PASSWORD` | 数据库密码 | （空） |
| `ALIYUN_API_KEY` | 阿里云百炼 API Key | （必填） |
| `JWT_SECRET` | JWT 密钥（至少32字符） | （必填） |

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
java -jar target/springaichat-1.0.0.jar
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
| `/api/chat` | POST | 发送消息 |
| `/api/chat/history` | GET | 获取对话历史 |

### 项目结构

```
springaichat/
├── src/main/java/com/example/springaichat/
│   ├── controller/     # REST API 控制层
│   ├── service/        # 业务逻辑层
│   ├── repository/     # 数据访问层
│   ├── entity/         # 数据库实体
│   ├── dto/            # 数据传输对象
│   ├── config/         # 配置类
│   ├── util/           # 工具类
│   └── SpringAiChatApplication.java
├── src/main/resources/
│   └── application.yml # 应用配置
├── frontend/           # 前端代码
├── pom.xml             # Maven 配置
└── README.md           # 项目说明
```

## 配置说明

### application.yml 主要配置项

```yaml
# 服务器配置
server:
  port: 8080

# Spring AI 配置（阿里云百炼）
spring:
  ai:
    openai:
      api-key: ${ALIYUN_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      chat:
        options:
          model: qwen-turbo
          temperature: 0.7
          max-tokens: 2048
```

## 开发指南

### 添加新功能

1. 创建实体类（entity）
2. 创建 Repository 接口
3. 创建 Service 层
4. 创建 Controller 层
5. 添加 DTO（请求/响应对象）

### 代码规范

- 遵循 Spring Boot 最佳实践
- 使用 Lombok 简化代码
- 统一异常处理
- 使用 DTO 隔离数据库实体

## 部署说明

### 生产环境部署

1. 配置环境变量
2. 使用 `mvn clean package` 打包
3. 使用以下命令启动：
```bash
java -jar springaichat-1.0.0.jar --spring.profiles.active=prod
```

### Docker 部署（可选）

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/springaichat-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！