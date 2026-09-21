# AtlasMind

## 第四课进度

已增加登录注册、路由保护和浅色侧边栏工作台。`/resume` 已接入用户简历数据库，支持姓名、目标岗位、个人简介、教育经历、实习经历、项目经历和专业技能；尚不支持文件上传解析。本课仍在进行中，详见 [第四课笔记](doc/04-web-foundation-notes.md)。下方第一至三课内容为历史教学记录。

这是 AtlasMind 个人知识与工作助手的课程练习项目，参考原 Plexus 的架构但使用独立品牌。第一节课只实现一条最小的直接聊天链路，用来理解 React、Spring Boot、Python Agent 和 SSE 之间的职责边界。

当前 Python 服务是教学用的假 Agent，不调用真实大模型。后续 Agent 课程会把它替换成独立的 LangChain/LangGraph 服务；原 Plexus 的 `plexus-core` 只作为架构参照。

## 第一课学到了什么

一次直接聊天请求经过下面的链路：

```text
React Web
  -> POST /api/agents/PlexusAgent/execute
Vite 代理
  -> Spring Boot :8200
Spring Controller / Service
  -> Python Agent :8100
Python Agent
  -> SSE thinking / chunk / final / done
Spring Boot
  -> SSE 原样转发
React Web
  -> 解析事件并更新页面状态
```

各层职责如下：

| 层 | 负责什么 | 不负责什么 |
|---|---|---|
| React | 输入、按钮、页面状态、SSE 解析、回答渲染 | 不执行 Agent，不直接调用模型工具 |
| Spring Boot | HTTP 入口、参数校验、统一错误、SSE 转发、取消下游订阅 | 不做大模型推理 |
| Python Agent | Agent 执行、模型推理、工具调用、产生事件 | 不负责浏览器页面状态 |

边界清晰的意义是：请求参数错误由 Java 尽早拦截，模型执行问题留在 Python Agent，页面显示问题留在 React；替换某一层时，不需要重写其他层。

## 请求和事件协议

前端发送的请求体是：

```json
{
  "query": "什么是 SSE？",
  "session_id": "web-demo",
  "stream": true
}
```

- `query`：用户本轮输入。
- `session_id`：业务会话标识，用来关联多轮对话；当前示例只传递它，还没有实现持久化记忆。
- `stream`：是否要求持续返回 SSE 事件。当前教学服务要求它为 `true`。

事件示例：

```text
event: thinking
data: {"text":"正在准备回答","session_id":"web-demo"}

event: chunk
data: {"content":"你问的是：什么是 SSE。","session_id":"web-demo"}

event: final
data: {"content":"你问的是：什么是 SSE。","session_id":"web-demo"}

event: done
data:

```

事件之间用空行分隔。网络传输的分块不一定刚好对应一个完整事件，所以 React 端先把文本放进 `buffer`，再按换行和空行组装事件。

| 事件 | 前端动作 |
|---|---|
| `thinking` | 显示 Agent 正在准备 |
| `chunk` | 把 `content` 追加到当前回答 |
| `final` | 用完整内容覆盖当前回答 |
| `done` | 把请求状态标记为完成，不提供正文 |
| HTTP `400` | 显示参数错误，例如 `query 不能为空` |
| HTTP `503` | 显示 Agent 暂不可用 |

## 用户主动停止

React 在发送请求时创建 `AbortController`，并把 `signal` 传给 `fetch`：

```text
点击停止
  -> React controller.abort()
  -> 浏览器关闭 HTTP 请求
  -> Spring WebFlux 取消 Python 下游订阅
  -> Python 写 SSE 时发现客户端断开
  -> Python 停止继续写事件
```

页面会保留已经收到的部分回答，并把状态显示为“已停止”。组件卸载时也会自动取消正在进行的请求。

## 代码目录

```text
plexus-personal/
├─ core/
│  ├─ app.py                 # 教学用 Python SSE Agent
│  └─ client.py              # Python SSE 客户端和手写解析器
├─ server-spring/
│  ├─ pom.xml                # Spring Boot WebFlux 和校验依赖
│  └─ src/main/java/com/plexus/personal/
│     ├─ ServerApplication.java
│     ├─ AgentController.java       # HTTP 接口和 SSE 响应类型
│     ├─ AgentService.java          # 转发到 Python Agent
│     ├─ CoreClientConfig.java      # WebClient 地址配置
│     ├─ ChatRequest.java            # query/session_id/stream
│     ├─ ApiError.java              # 统一错误结构
│     └─ GlobalExceptionHandler.java
├─ web/
│  ├─ src/main.tsx            # React 页面、请求、SSE 解析、停止操作
│  ├─ src/style.css           # 页面样式
│  ├─ vite.config.ts          # /api 代理到 Spring Boot
│  └─ package.json
├─ server/App.java            # 早期 JDK HttpServer 代理，仅作对照，不再使用
└─ tools/                     # 项目内的 Maven 工具包
```

真正需要重点阅读的文件是：

- [React 请求与 SSE 解析](web/src/main.tsx)
- [Spring Controller](server-spring/src/main/java/com/plexus/personal/AgentController.java)
- [Spring 转发 Service](server-spring/src/main/java/com/plexus/personal/AgentService.java)
- [Python SSE 服务](core/app.py)
- [第一课个人学习笔记与架构图](doc/01-architecture-notes.md)
- [第二课环境、启动和验收记录](doc/02-environment-notes.md)
- [第一课课程大纲](../plexus/docs/course/01-architecture.md)

## 启动第一课代码

需要三个终端。先启动 Python，再启动 Spring，最后启动前端。

终端一：

```powershell
cd D:\CodeX_project\project\plexus-personal
python core\app.py
```

终端二：

```powershell
cd D:\CodeX_project\project\plexus-personal\server-spring
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$env:Path = "$env:JAVA_HOME\bin;D:\CodeX_project\project\plexus-personal\tools\apache-maven-3.9.16\bin;$env:Path"
& "D:\CodeX_project\project\plexus-personal\tools\apache-maven-3.9.16\bin\mvn.cmd" -B -DskipTests package
java -jar target\server-spring-0.0.1-SNAPSHOT.jar
```

终端三：

```powershell
cd D:\CodeX_project\project\plexus-personal\web
npm install
npm run dev -- --host=127.0.0.1
```

打开 [http://localhost:5173](http://localhost:5173)。Vite 的参数要写成 `--host=127.0.0.1`，否则某些 Vite 版本会把 `127.0.0.1` 当作项目目录。

## 第二课环境与最小启动

第二课已验证 Java 22.0.2、项目自带 Maven 3.9.16、Python 3.14.5、Node.js 22.13.1、npm 10.9.2 和 Docker 29.8.0。用户级 `JAVA_HOME` 指向 `C:\Program Files\Java\jdk-22`。如果当前终端仍读取旧值，先执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Spring Boot 支持 `SERVER_PORT` 和 `PLEXUS_CORE_BASE_URL` 环境变量，默认值分别为 `8200` 和 `http://127.0.0.1:8100`。完整记录见 [第二课环境记录](doc/02-environment-notes.md)。

最小检查接口如下：

```powershell
curl.exe -sS http://127.0.0.1:8200/agents/PlexusAgent/health
curl.exe -sS http://127.0.0.1:8100/agents
```

预期分别返回 Spring 服务状态和 `PlexusAgent` 列表。第一课的 SSE 请求和前端页面也已重新验收通过。

## 手工验收

正常请求应依次看到 `thinking`、多个 `chunk`、`final` 和 `done`：

```powershell
curl.exe -sS -N --max-time 6 `
  -X POST http://127.0.0.1:5173/api/agents/PlexusAgent/execute `
  -H "Content-Type: application/json" `
  --data-raw '{"query":"什么是 SSE？","session_id":"manual-1","stream":true}'
```

空 `query` 应返回统一错误：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "query 不能为空"
}
```

生成过程中点击页面的“停止”，应保留部分回答并显示“已停止”；Spring 日志会出现 `agent SSE cancelled by client`，Python 日志会出现 `client disconnected; stop streaming`。

## 第一课范围

第一课已经完成服务边界、SSE 事件、React 状态、请求取消、架构图、时序图和 5 个核心用例的整理。下面这些内容属于后续课程，本 README 不把它们当作当前已完成能力：

- 真实 LangChain/LangGraph Agent；
- 登录、JWT 和 PostgreSQL；
- 文件上传、向量检索和知识库；
- WebSocket 协作空间和 Kafka 事件系统。

第二课完成了环境版本、配置默认值、端口、启动顺序、健康检查和最小链路验收。第三课将在这些边界上实现 Java 用户域与认证域，包括注册、登录、JWT 和当前用户接口。
