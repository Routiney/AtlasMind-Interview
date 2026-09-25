# AtlasMind Interview

AtlasMind Interview 是一个面向求职者的 AI 面试辅助系统。它把简历档案、岗位目标、面试对话和职业规划放在同一个工作台中，帮助用户完成简历准备、岗位分析、面试练习和阶段性学习规划。

项目参考 Plexus 的分层思路，但围绕“面试辅助”场景重新设计了领域模型和 Agent 工作流。

## 当前能力

- 用户注册、登录、JWT 鉴权和当前用户信息。
- 简历档案维护：目标岗位、个人简介、教育经历、实习经历、项目经历和专业技能。
- 面试会话管理：创建、分页查询、重命名、删除会话，分页加载历史消息。
- AI 面试问答：支持简历上下文、会话历史、深度思考状态和流式回答。
- 会话记忆：读取、刷新和清除会话摘要及结构化事实，减少长对话重复传递上下文。
- 职业规划：通过 Planner、Task Summarizer、Report Writer 工作流生成能力评估、岗位方向、学习计划和面试重点。
- MCP 工具接入：将公开搜索、简历证据分析、岗位匹配、岗位方向推荐、学习计划和面试题生成等能力接入 Agent。
- 前端工作台：登录、路由保护、简历编辑、会话侧栏、Markdown 回答渲染、流式状态展示和请求取消。

## 技术架构

```text
React + Vite
    │ REST / SSE
    ▼
Spring Boot WebFlux
    ├─ JWT 鉴权与用户数据隔离
    ├─ 简历、会话、消息、记忆和职业规划 API
    ├─ PostgreSQL / MyBatis / Flyway
    └─ 转发 Agent 请求并持久化会话结果
    │ HTTP
    ▼
Python Agent Service
    ├─ LangChain Agent
    ├─ Planner -> Task Summarizer -> Report Writer
    ├─ MCP Client / MCP Server
    └─ SSE 事件流
```

聊天链路使用统一的 SSE 事件协议：

```text
thinking -> chunk -> final -> done
```

`thinking` 用于展示工具或模型处理状态，`chunk` 用于增量渲染回答，`final` 提供完整回答，`done` 标记本轮传输结束。浏览器主动停止请求时，Spring 会取消下游订阅，Python 服务停止继续写入事件。

## 目录结构

```text
.
├─ core/                         # Python Agent 和 MCP 能力
│  ├─ app.py                     # HTTP/SSE 服务入口
│  ├─ agent.py                   # LangChain Interview Agent
│  ├─ planning_workflow.py       # 职业规划多阶段工作流
│  ├─ mcp_tools.py               # MCP 工具适配器
│  ├─ career_mcp_server.py       # 求职分析 MCP Server
│  ├─ search_mcp_server.py       # 公开搜索 MCP Server
│  └─ test_*.py                  # Agent、工具、记忆和工作流测试
├─ server-spring/                # Spring Boot API 和数据层
│  ├─ src/main/java/...          # 用户、认证、简历、会话和规划领域
│  └─ src/main/resources/        # Flyway migration 与 MyBatis mapper
├─ web/                          # React/Vite 面试工作台
├─ doc/                          # 课程笔记、架构记录和验收记录
├─ docker-compose.yml            # PostgreSQL 本地开发环境
└─ requirements.txt              # Python 依赖
```

## 本地启动

### 1. 启动 PostgreSQL

```powershell
docker compose up -d postgres
```

默认连接信息：数据库 `plexus_personal`，用户 `plexus`，密码 `plexus`，端口 `5432`。Spring Boot 启动时会自动执行 Flyway migration。

### 2. 启动 Python Agent

建议使用 Python 虚拟环境：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

配置模型和可选搜索服务：

```powershell
$env:DEEPSEEK_API_KEY = "你的 DeepSeek API Key"
$env:TAVILY_API_KEY = "你的 Tavily API Key"
python core\app.py
```

Python 服务默认监听 `http://127.0.0.1:8100`。如果只验证本地工具逻辑，可以不配置真实模型密钥，直接运行测试。

### 3. 启动 Spring Boot

使用 IDEA 内置 Maven 或本机 Maven 执行：

```powershell
cd server-spring
mvn -B -DskipTests package
java -jar target\server-spring-0.0.1-SNAPSHOT.jar
```

Spring Boot 默认监听 `http://127.0.0.1:8200`，通过 `PLEXUS_CORE_BASE_URL` 指向 Python 服务。

### 4. 启动 React

```powershell
cd web
npm install
npm run dev -- --host=127.0.0.1
```

打开 <http://127.0.0.1:5173> 即可进入面试工作台。

## 常用配置

| 环境变量 | 默认值 | 作用 |
|---|---|---|
| `DEEPSEEK_API_KEY` | 无 | DeepSeek 模型调用凭证 |
| `TAVILY_API_KEY` | 无 | 公开搜索工具凭证 |
| `PLEXUS_CORE_BASE_URL` | `http://127.0.0.1:8100` | Spring 调用 Python Agent 的地址 |
| `SERVER_PORT` | `8200` | Spring Boot 端口 |
| `DB_URL` | 本地 PostgreSQL | 数据库连接地址 |
| `ATLAS_JWT_SECRET` | 开发默认值 | JWT 签名密钥，部署时必须替换 |

`core/.env`、前端本地环境文件和运行日志已加入 `.gitignore`，不要把真实密钥提交到仓库。

## 验证

运行 Python 单元测试：

```powershell
python -m unittest discover -s core -p "test_*.py"
```

构建前端：

```powershell
cd web
npm run build
```

构建后端：

```powershell
cd server-spring
mvn -B -DskipTests package
```

## 当前边界

当前版本已经完成核心全链路和 Agent 工作流，但以下能力仍属于后续迭代方向：

- 简历文件上传、PDF/Word 解析和结构化导入。
- 面向面试知识库的文档切分、向量检索、引用回溯和检索效果评测。
- 面试回答质量、检索召回率、首 token 延迟和并发稳定性等可量化性能测试。
- 生产环境所需的模型降级、异步任务队列、可观测性和更细粒度的权限策略。

## 学习记录

项目的架构拆解、环境配置、认证实现、前端工作台和 Agent 实现过程记录在 [`doc/`](doc/) 目录中。代码以可运行的面试辅助系统为主，课程笔记用于解释各模块的设计取舍和验收过程。
