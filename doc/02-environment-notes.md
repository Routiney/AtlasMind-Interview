# 第二课：开发环境与最小启动记录

本课的目标是让第一课的最小链路可以重复启动，并确认三个服务的职责、端口和验收方式。个人项目最终会继续扩展为个人知识与工作助手；本课不实现用户注册、登录、JWT、PostgreSQL 用户表或用户领域模型，这些内容留给第三课。

## 环境基线

| 工具 | 已验证版本 | 用途 |
|---|---:|---|
| Java | 22.0.2 | Spring Boot 服务 |
| Maven | 3.9.16（项目自带） | Java 构建 |
| Python | 3.14.5 | 教学用 Agent Core |
| Node.js | 22.13.1 | Vite 前端 |
| npm | 10.9.2 | 前端依赖 |
| Docker | 29.8.0 | 后续容器化课程使用，本课不依赖 |

`JAVA_HOME` 用户级变量已设置为 `C:\Program Files\Java\jdk-22`。当前终端如果仍保留旧值，先执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 配置模板

Spring Boot 支持以下环境变量，并提供默认值：

```powershell
$env:SERVER_PORT = '8200'
$env:PLEXUS_CORE_BASE_URL = 'http://127.0.0.1:8100'
```

前端 Vite 代理默认指向 `http://127.0.0.1:8200`，本课无需额外配置。

## 启动顺序

1. Python Core：`127.0.0.1:8100`
2. Spring Boot：`127.0.0.1:8200`
3. Vite Web：`127.0.0.1:5173`

Python：

```powershell
cd D:\CodeX_project\project\plexus-personal
python core\app.py
```

Spring Boot：

```powershell
cd D:\CodeX_project\project\plexus-personal\server-spring
& "D:\CodeX_project\project\plexus-personal\tools\apache-maven-3.9.16\bin\mvn.cmd" -B -DskipTests package
java -jar target\server-spring-0.0.1-SNAPSHOT.jar
```

前端：

```powershell
cd D:\CodeX_project\project\plexus-personal\web
npm install
npm run dev -- --host=127.0.0.1
```

## 验收

检查 Spring：

```powershell
curl.exe -sS http://127.0.0.1:8200/agents/PlexusAgent/health
```

预期：`{"status":"ok","service":"server-spring"}`。

检查 Python Agent 列表：

```powershell
curl.exe -sS http://127.0.0.1:8100/agents
```

预期：`{"agents":["PlexusAgent"]}`。

检查完整 SSE 链路：

```powershell
curl.exe -sS -N --max-time 8 -X POST http://127.0.0.1:8200/agents/PlexusAgent/execute -H "Content-Type: application/json" --data-raw '{"query":"请检查完整链路","session_id":"lesson-2","stream":true}'
```

预期依次出现 `thinking`、`chunk`、`final`、`done`。浏览器打开 `http://127.0.0.1:5173` 后发送消息，也已验证可以正常显示流式回答。

## 下一课边界

第三课将在 Java 服务中加入用户域和认证域，完成注册、登录、JWT 和当前用户接口。本课的 `health` 接口、Agent 转发接口和现有 SSE 协议会作为认证之前的基础边界保留。
