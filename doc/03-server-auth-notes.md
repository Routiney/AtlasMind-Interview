# 第三课学习笔记：Java 用户与认证服务

这份笔记对应课程大纲中的第三课，记录 AtlasMind 后端用户域和认证域的实现、验证结果以及过程中形成的架构理解。原课程第三课的目标是用户、注册、登录、JWT 和当前用户接口；会话与聊天记录属于课程目录中的第八课，本项目已经提前做了部分数据库和后端实现，但不把它们混入第三课的核心验收。

课程原文：[03-server-auth.md](../../plexus/docs/course/03-server-auth.md)

## 1. 第三课目标与完成状态

| 目标 | 状态 |
|---|---|
| 按业务模块组织 Java 代码 | 已完成 |
| PostgreSQL 本地运行 | 已完成 |
| Flyway 版本化数据库结构 | 已完成 |
| 用户表和用户查询 | 已完成 |
| 注册用户 | 已完成 |
| BCrypt 密码哈希 | 已完成 |
| 登录并签发 JWT | 已完成 |
| 验证 JWT 并保护接口 | 已完成 |
| 当前用户接口 | 已完成 |
| React 完整登录状态、路由和工作台 | 留给第四课 |
| 会话列表 UI | 留给第八课 |

## 2. 当前后端架构

AtlasMind 的 Java 服务按业务模块组织，而不是把所有 Controller、Service、Repository 分别放进全局目录：

```text
server-spring/src/main/java/com/plexus/personal/
├─ auth/
│  ├─ api/
│  ├─ application/
│  └─ infrastructure/security/
├─ user/
│  ├─ api/
│  ├─ application/
│  ├─ domain/
│  └─ infrastructure/
├─ conversation/
│  ├─ api/
│  ├─ application/
│  ├─ domain/
│  └─ infrastructure/
├─ AgentController.java
├─ AgentService.java
├─ ChatService.java
└─ GlobalExceptionHandler.java
```

按模块组织的好处是，一个业务模块的接口、应用逻辑、领域模型和基础设施代码可以一起阅读和演进。第三课的核心模块是 `user` 和 `auth`；`conversation` 是为了提前验证聊天持久化而加入的后续课程内容。

## 3. 用户域的职责

用户域负责账号本身：

- 查询用户名。
- 创建用户。
- 保存 BCrypt 密码哈希。
- 判断账号是否启用。
- 为认证服务提供用户信息。

主要代码：

- [User.java](../server-spring/src/main/java/com/plexus/personal/user/domain/model/User.java)：不可变用户领域模型。
- [UserRepository.java](../server-spring/src/main/java/com/plexus/personal/user/domain/repository/UserRepository.java)：领域层需要的持久化能力。
- [UserService.java](../server-spring/src/main/java/com/plexus/personal/user/application/UserService.java)：注册流程和用户查询。
- [UserController.java](../server-spring/src/main/java/com/plexus/personal/user/api/UserController.java)：注册 HTTP 接口。

注册请求：

```http
POST /api/users
Content-Type: application/json
```

```json
{
  "username": "alice",
  "password": "secret123",
  "displayName": "Alice"
}
```

返回对象只包含公开信息，不返回密码哈希：

```json
{
  "id": 1,
  "username": "alice",
  "displayName": "Alice",
  "enabled": true,
  "createdAt": "2026-09-20T07:22:34Z"
}
```

## 4. 数据库设计

PostgreSQL 运行在 Docker 容器中：

```text
容器：plexus-personal-postgres
数据库：plexus_personal
用户：plexus
端口：5432
```

连接配置在 [application.properties](../server-spring/src/main/resources/application.properties)，支持环境变量覆盖：

```properties
DB_URL
DB_USERNAME
DB_PASSWORD
```

第一条 migration 是 [V1__create_users.sql](../server-spring/src/main/resources/db/migration/V1__create_users.sql)：

```text
users
├─ id
├─ username          唯一
├─ password_hash     BCrypt 哈希
├─ display_name
├─ enabled
├─ created_at
└─ updated_at
```

用户名有数据库唯一约束，应用层也会提前检查重复用户名。应用层检查用于返回友好错误，数据库约束用于处理并发情况下的最终一致性。

Flyway 随 Spring Boot 启动：

1. 扫描 `classpath:db/migration`。
2. 找到还没有执行的版本化 SQL。
3. 执行 SQL。
4. 把版本和结果记录到 `flyway_schema_history`。

## 5. 为什么使用 MyBatis

最初的用户 Repository 使用 `JdbcTemplate`，查询和 `ResultSet` 映射都写在 Java 中。它适合很少量的简单 SQL，但随着分页、动态条件和关联查询增加，代码会变得繁琐。

当前改为 MyBatis：

```text
UserRepository
    -> MyBatisUserRepository
        -> UserMapper
            -> UserMapper.xml
                -> PostgreSQL
```

主要代码：

- [UserMapper.java](../server-spring/src/main/java/com/plexus/personal/user/infrastructure/persistence/UserMapper.java)：定义 Mapper 方法。
- [UserMapper.xml](../server-spring/src/main/resources/mapper/user/UserMapper.xml)：保存 SQL 和字段映射。
- [UserRow.java](../server-spring/src/main/java/com/plexus/personal/user/infrastructure/persistence/UserRow.java)：数据库结果对象。
- [MyBatisUserRepository.java](../server-spring/src/main/java/com/plexus/personal/user/infrastructure/persistence/MyBatisUserRepository.java)：把数据库结果转换成领域 `User`。

`UserRow` 和 `User` 分开，是为了避免数据库映射模型直接污染领域模型：

```text
数据库记录 -> UserRow -> User
```

Service 只依赖 `UserRepository`，所以以后更换 MyBatis、JPA 或其他持久化技术时，应用层不需要跟着改。

## 6. 认证域的职责

认证域负责登录身份，而不是用户资料：

- 接收用户名和密码。
- 查询用户。
- 使用 BCrypt 校验密码。
- 判断用户是否启用。
- 签发 JWT。
- 验证后续请求的 JWT。

主要代码：

- [AuthController.java](../server-spring/src/main/java/com/plexus/personal/auth/api/AuthController.java)
- [AuthService.java](../server-spring/src/main/java/com/plexus/personal/auth/application/AuthService.java)
- [JwtTokenService.java](../server-spring/src/main/java/com/plexus/personal/auth/infrastructure/security/JwtTokenService.java)
- [JwtAuthenticationWebFilter.java](../server-spring/src/main/java/com/plexus/personal/auth/infrastructure/security/JwtAuthenticationWebFilter.java)

登录接口：

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "alice",
  "password": "secret123"
}
```

成功后返回：

```json
{
  "tokenType": "Bearer",
  "accessToken": "<JWT>",
  "expiresIn": 3600
}
```

JWT 当前包含：

```text
sub       用户 id
username  用户名
iat       签发时间
exp       过期时间
```

请求验证流程：

```text
Authorization: Bearer <JWT>
    -> JwtAuthenticationWebFilter
    -> 验证 HMAC 签名
    -> 验证过期时间
    -> 提取 userId 和 username
    -> 写入 WebFlux exchange attributes
    -> Controller 使用当前用户身份
```

当前保护的接口包括：

```text
GET  /api/auth/me
POST /agents/PlexusAgent/execute
访问 /api/conversations 的接口
```

健康检查保持公开：

```text
GET /agents/PlexusAgent/health
```

## 7. 统一错误

[GlobalExceptionHandler.java](../server-spring/src/main/java/com/plexus/personal/GlobalExceptionHandler.java) 将常见异常转换成统一响应：

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "用户名或密码错误"
}
```

当前主要错误：

| 场景 | HTTP 状态 | code |
|---|---:|---|
| 参数校验失败 | 400 | `VALIDATION_ERROR` |
| 用户名重复 | 409 | `USERNAME_ALREADY_EXISTS` |
| 用户名或密码错误 | 401 | `INVALID_CREDENTIALS` |
| 没有或无效 JWT | 401 | HTTP 状态响应 |
| Agent 不可用 | 503 | `AGENT_UNAVAILABLE` |
| 会话不存在或不属于当前用户 | 404 | `CONVERSATION_NOT_FOUND` |

## 8. 验收记录

使用项目自带 Java 和 Maven：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$env:Path = "$env:JAVA_HOME\bin;D:\CodeX_project\project\plexus-personal\tools\apache-maven-3.9.16\bin;$env:Path"
cd D:\CodeX_project\project\plexus-personal\server-spring
& 'D:\CodeX_project\project\plexus-personal\tools\apache-maven-3.9.16\bin\mvn.cmd' -B -DskipTests package
```

结果：

```text
BUILD SUCCESS
```

已经实际验证过：

- 注册用户返回 `201 Created`。
- 数据库保存的是 60 位 BCrypt 哈希，不保存明文密码。
- 正确密码登录返回 JWT。
- 错误密码返回 `401`。
- 无 JWT 调用受保护 Agent 返回 `401`。
- 有效 JWT 可以通过 Java 认证并继续访问 Python Agent。
- 完整 SSE 仍按 `thinking -> chunk -> final -> done` 返回。
- 聊天请求可以自动创建会话，并保存 `USER` 和 `ASSISTANT` 消息。
- React 构建、Java 打包、Python 语法检查均已通过。

临时测试用户、会话和消息在验收后都已清理，PostgreSQL 容器保持运行。

## 9. 这节课的边界

按照原课程顺序，第三课到用户认证后就可以结束。当前项目提前实现了会话和聊天记录的后端基础：

- `conversation`、`message` 数据库表属于后续会话课程。
- 会话 Repository、Service 和 API 属于后续会话课程。
- React 会话列表不属于第三课。
- React 登录页、用户状态和路由保护属于第四课。
- 会话列表 UI 和历史消息属于第八课“会话与聊天记录”。

因此当前继续学习时，下一节应该回到第四课，整理前端登录状态、路由和工作台外壳；不要先做会话列表 UI。

## 10. 当前仍需改进

- JWT 目前只保存在 React 内存中，刷新页面后需要重新登录。
- 默认 JWT secret 只适合本地开发，部署时必须通过 `ATLAS_JWT_SECRET` 注入。
- JWT 过滤器目前是轻量 WebFlux `WebFilter`，后续可以演进为更完整的权限模型。
- JDBC/MyBatis 都是阻塞式数据库访问；当前规模可以使用，未来高并发时需要评估线程池或 R2DBC。
- 当前还没有自动化测试，验收主要是 Maven 构建、真实 PostgreSQL、HTTP 请求和 SSE 冒烟测试。
