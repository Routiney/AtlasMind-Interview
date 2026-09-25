# 第 5 课：Agent、Tools 与 MCP

## 单元 1：Agent 核心

本单元完成 AtlasMind Agent 的基础执行链：结构化简历上下文、字段专用 Prompt、LangChain `ChatPromptTemplate`、DeepSeek `ChatOpenAI` 和 SSE 流式输出。

教学从 [core/lesson_mvp.py](../core/lesson_mvp.py) 开始。这个文件保留 LangChain Agent 的最小链路：一个 Prompt、一个 ChatModel、三个 Tool 和一次 `invoke()`。完整服务入口在 [core/agent.py](../core/agent.py) 和 [core/app.py](../core/app.py) 中复用同一套 Agent。

```powershell
$env:DEEPSEEK_API_KEY = '替换为你的 DeepSeek API Key'
python core\lesson_mvp.py "我适合学习 Java 后端吗？"
```

最小链路可以按三层阅读：

1. `ChatPromptTemplate.from_messages(...)` 定义消息模板。
2. `ChatOpenAI(...)` 创建 LangChain 的模型适配器。
3. `create_agent(model=model, tools=[...])` 组装模型和工具循环，`agent.invoke(...)` 执行一次 Agent 调用。

理解这条链路后，再把固定问题替换成简历上下文、消息历史和 SSE 流式输出。

第二步是给 Prompt 增加变量。MVP 当前使用 `target_role`、`projects` 和 `question` 三个变量：模板只定义它们出现的位置，真实值在 `chain.invoke({...})` 时传入。目标岗位是比较基准，项目经历是能力证据。

```powershell
$env:MVP_TARGET_ROLE = 'Java 后端工程师'
$env:MVP_PROJECTS = '熟悉 Java、Spring Boot，有电商项目经验'
$env:DEEPSEEK_API_KEY = '替换为你的 DeepSeek API Key'
python core\lesson_mvp.py '我应该重点准备哪些面试内容？'
```

现在 MVP 已经包含目标岗位、项目经历、实习经历和专业技能四个字段。它们仍然是字符串，只用于理解 Prompt 变量；下一步会给这些字段增加系统级分析规则，让模型知道技能是自述，项目和实习是证据。

当前使用的是 LangChain 的 ChatModel、Prompt 和 Agent 接口：`core/prompts.py` 使用 `ChatPromptTemplate`、`MessagesPlaceholder` 和消息类型组织输入，`core/model.py` 使用 `langchain_openai.ChatOpenAI`，`core/agent.py` 使用 `create_agent` 绑定三个工具，`core/app.py` 使用 Agent 的消息流转换成 SSE。

### Agent loop：当前是什么，暂时不是什么

当前采用的是 LangChain `create_agent` 的标准模型工具循环，行为上属于 ReAct-like loop：

```text
用户输入
  -> 模型判断是否需要工具
  -> 需要工具：输出 tool call
  -> LangChain 执行工具
  -> 工具结果回到模型
  -> 模型继续回答，或再次调用工具
  -> 没有新的 tool call 时结束
```

这条聊天链路不是显式的 Plan-and-Solve：当前没有单独的 Planner 节点，模型在每一轮根据已有消息决定下一步。它适合处理连续对话和临时问题；复杂的“评估 -> 计划”任务已经单独放到下面的规划工作流中，避免把两种执行模式混在一个聊天循环里。

### 查询审查和 thinking 事件

Agent 请求在 [core/query_guard.py](../core/query_guard.py) 进入模型前先经过轻量审查：限制查询长度和控制字符，并拦截明显的提示词窃取或越权指令。审查失败不会调用模型，而是通过 SSE 返回 `error` 和 `done`，前端可以沿用普通请求的错误处理。

`thinking` 事件表示可展示的执行状态，例如准备上下文、调用某个工具、工具返回后继续分析。前端的“深度思考”开关通过 `deep_thinking` 请求字段控制这些事件：关闭时只返回正常回答流，开启时才推送过程状态。它不等于模型的隐藏思维链，也不会把 DeepSeek 的内部 reasoning 原文推送给浏览器。这样既能让用户知道 Agent 正在做什么，也保留了系统提示词和内部推理的边界。

聊天 Agent 仍可以沿用这套事件协议推送工具调用状态；面试规划接口目前返回结构化 JSON，后续接入规划页面时可以把各阶段直接映射为页面区块。

## 单元 3：MCP 工具

当前已经把公开搜索能力拆成了 [core/search_mcp_server.py](../core/search_mcp_server.py)：

```text
LangChain Agent
  -> mcp_search_tool 适配器
  -> MCP Client
  -> stdio
  -> search_mcp_server.py
  -> tools/call: search_public_web
```

MCP Server 通过标准 `tools/list` 暴露工具名称、描述和输入 Schema，通过 `tools/call` 执行搜索。Agent 仍然使用 LangChain Tool 接口，但 Tool 内部已经通过 MCP Client 调用独立 Server，因此工具协议和进程边界已经标准化。

默认搜索模式是 MCP。排查本地环境时可以设置 `ATLASMIND_SEARCH_TOOL_MODE=local`，回退到进程内的 LangChain Tool。当前 MVP 每次调用都会创建一个短生命周期 stdio MCP 会话，后续工具增多后再改成持久 MCP Client 和远程 Streamable HTTP Server。

现在又增加了 [core/career_mcp_server.py](../core/career_mcp_server.py)，暴露一组面试助手专用工具：

- `analyze_resume_evidence`：区分技能自述和项目、实习证据。
- `match_resume_to_job`：计算技能匹配、证据强度和技能缺口。
- `recommend_job_directions`：根据简历证据推荐岗位方向。
- `generate_learning_plan`：根据岗位和缺口生成阶段化计划。
- `generate_interview_questions`：根据真实经历生成可追问面试题。

这些工具默认通过 MCP 调用；设置 `ATLASMIND_CAREER_TOOL_MODE=local` 可以切换为进程内 LangChain Tool。工具返回结构化结果，后续 Plan-and-Solve Planner 可以按“证据分析 -> 岗位匹配 -> 学习计划 -> 面试题”的顺序组合它们。

## 单元 4：职业规划的 TODO 并行研究工作流

这一部分参考 Hello-Agents 第十四章的 TODO 驱动深度研究路线：先拆任务，再把相互独立的子问题分发给专门 Agent，最后统一整合报告。职业规划不是直接让一个 Agent 输出长答案，而是把“简历证据、岗位匹配、岗位信息研究、学习优先级和面试准备”变成可观察的研究任务。参考：[Hello-Agents 第十四章](https://github.com/datawhalechina/hello-agents/blob/main/docs/chapter14/%E7%AC%AC%E5%8D%81%E5%9B%9B%E7%AB%A0%20%E8%87%AA%E5%8A%A8%E5%8C%96%E6%B7%B1%E5%BA%A6%E7%A0%94%E7%A9%B6%E6%99%BA%E8%83%BD%E4%BD%93.md)。

工具本身不负责开放式思考。它们只执行可复现的能力，例如提取简历证据、计算技能匹配和搜索公开岗位信息；“如何解释证据、如何排序缺口、如何安排学习阶段”由三个显式的模型角色完成。

当前实现位于 [core/planning_workflow.py](../core/planning_workflow.py)，使用 LangGraph `StateGraph + Send` 实现 map-reduce 式 fan-out/fan-in：

```text
结构化简历
  -> Planner：生成 3-5 个 ResearchTask / TODO
  -> Send：把每个 TODO 分发到独立执行分支
       ├─ Resume Evidence Auditor
       ├─ Role Fit Analyst
       ├─ Market Research Agent
       ├─ Learning Prioritization Agent
       └─ Interview Readiness Agent
  -> fan-in：按 task_id 合并各分支的 TaskSummary
  -> Report Writer：整合能力评估和学习计划
  -> 结构化 InterviewPlanningResult
```

每个 TODO 都由专门的子 Agent 系统提示词负责：它们共享确定性工具结果，但关注点不同，不再由一个通用 Summarizer 串行处理所有任务。角色提示词在 `AgentPromptSpec` 中按“使命、工作步骤、证据规则、禁止事项”组织：简历证据 Agent 负责审计事实强度，岗位匹配 Agent 负责逐项对照，市场研究 Agent 负责约束外部来源，学习优先级 Agent 负责时间预算和验收产出，面试准备 Agent 负责真实经历的深挖主题。LangGraph 的并行分支使用按 `task_id` 合并的 reducer，最终报告阶段再按 Planner 的 TODO 顺序恢复展示顺序。Planner、专门子 Agent 和 Report Writer 都使用 `with_structured_output(..., method="function_calling")`，结果会经过 Pydantic schema 校验，不把自由文本直接当成研究任务、证据摘要或学习计划。模型提示词要求区分简历事实、合理推断和证据限制；信息不足时输出限制或澄清任务，不能自行补全。

规划接口是 `POST /api/agents/PlexusAgent/plan`，Spring 根据当前 JWT 用户读取结构化简历，再代理到 Core 的 `POST /agents/PlexusAgent/plan`。请求可包含 `job_description`、`weeks`、`hours_per_week` 和 `research_market`。它返回 `research_plan`、`task_summaries`、`assessment`、`learning_plan` 和证据边界。工作台的“职业规划”页面展示这些结构化结果。

普通聊天仍使用 `POST /api/agents/PlexusAgent/execute`，不会自动切换到规划工作流。当前规划模块已经支持可选的公开岗位研究和任务摘要，但还没有计划执行打卡、历史版本持久化和 Reviewer 节点；这些属于下一阶段的闭环能力。

在 MVP 中，`InterviewManagerAgent` 是业务层 Agent 外壳：它持有 Prompt chain，并暴露面向求职场景的 `invoke(...)` 接口。后续 Tools 和执行循环都挂在这个外壳上，而不是让 Web 层直接调用 ChatModel。

工作台的“添加简历上下文”按钮会发送 `include_resume=true`。Spring 使用当前 JWT 用户查询 `resume_profiles`，将目标岗位、个人简介、教育经历、实习经历、项目经历和专业技能整理成结构化档案，再与当前 Agent 请求一起转发给 Python Core。

附带简历时，各字段承担不同的分析职责：目标岗位是匹配基准；项目和实习是能力证据；技能是需要被经历交叉验证的自述；教育经历用于相关资格判断；个人简介用于判断用户定位和表达质量。模型会根据当前问题选择相关字段，用户要求完整分析时才进行跨字段综合。

项目中已有数据库会话和消息代码，但本单元不展开会话记录的数据模型、分页、持久化和聊天记录 UI；这些内容留到第 6 课。这里的消息上下文只服务于 Agent 输入组装。

## DeepSeek 配置

当前默认使用 DeepSeek。DeepSeek 官方提供 OpenAI 兼容接口，因此仍然使用 LangChain 的 `ChatOpenAI`，只切换 API 地址、密钥和模型名。

```powershell
$env:ATLASMIND_PROVIDER = 'deepseek'
$env:DEEPSEEK_API_KEY = '替换为你的 DeepSeek API Key'
$env:DEEPSEEK_MODEL = 'deepseek-flash'
python core\app.py
```

可选配置：

- `ATLASMIND_PROVIDER`：模型提供商，默认 `deepseek`，也支持 `openai`。
- `DEEPSEEK_MODEL`：DeepSeek 模型名称，默认 `deepseek-flash`。
- `DEEPSEEK_BASE_URL`：DeepSeek API 地址，默认 `https://api.deepseek.com`。
- `DEEPSEEK_THINKING`：默认 `disabled`；设为 `enabled` 时开启思考模式。
- `DEEPSEEK_REASONING_EFFORT`：思考模式强度，默认 `high`。
- `ATLASMIND_TEMPERATURE`：采样温度，默认 `0.2`。
- `ATLASMIND_MODEL_TIMEOUT`：模型请求超时时间，默认 `60` 秒。

未配置当前提供商对应的 API Key 时，Core 返回 `503`，不会输出教学用固定答案。

## 单元衔接

单元 1 的 Agent 能理解用户和简历任务后，单元 2 为它绑定 LangChain Tools；Tools 稳定后，单元 3 再把可复用能力封装成 MCP Server，并让 Agent 调用 MCP 工具。

## 单元 2：第一个 `search_tool`

当前 MVP 已加入 [core/search_tool.py](../core/search_tool.py)：

- `@tool` 把普通 Python 函数转换为 LangChain Tool。
- `Annotated` 和 `Field` 生成 `query` 参数 schema。
- Tavily 返回标题、URL 和摘要，供模型继续组织回答。
- API Key 缺失、网络失败和返回格式异常都会转成工具错误，不让整个 Agent 进程崩溃。
- `create_agent(model=model, tools=[search_tool])` 负责模型与工具之间的调用循环；完整项目当前在 `core/agent.py` 中绑定 `parse_resume`、`analyze_job_description` 和 `search_tool`。

配置搜索服务：

```powershell
$env:DEEPSEEK_API_KEY = '替换为你的 DeepSeek API Key'
$env:TAVILY_API_KEY = '替换为你的 Tavily API Key'
python core\lesson_mvp.py '请搜索当前 Java 后端岗位常见要求，并结合我的项目经历分析差距'
```

普通简历分析不需要搜索；只有模型判断问题需要外部资料或用户明确要求搜索时才调用工具。

第二个工具是 [core/job_tool.py](../core/job_tool.py) 中的 `analyze_job_description`。它不调用外部服务，只把岗位描述提取成技能、职责和级别线索，返回结构化字典。模型负责决定何时调用它，并把提取结果和用户简历进行匹配解释。

第三个工具是 [core/resume_tool.py](../core/resume_tool.py) 中的 `parse_resume`。它接收原始简历文本，按字段标题提取结构化档案。当前数据库中已经是结构化简历时无需调用它；它用于用户粘贴或上传原始文本的入口。

接入依据：[DeepSeek 官方首次调用文档](https://api-docs.deepseek.com/zh-cn/)。
