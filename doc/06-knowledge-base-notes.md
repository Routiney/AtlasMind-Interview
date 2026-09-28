# 第六节课学习笔记：面试知识库与文档上传

本节在前五节的认证、用户隔离、聊天 Agent 和全栈工作台基础上，完成了面试知识库的单用户基础闭环。目标不是一次性完成生产级文档平台，而是理解文件进入系统后如何变成可检索的知识上下文，并最终注入 LangChain Agent。

## 1. 本节完成状态

| 能力 | 状态 |
|---|---|
| 文档上传和用户隔离 | 已完成 |
| 本地文件存储 | 已完成 |
| `documents` 元数据表 | 已完成 |
| PDF / DOCX / Markdown / TXT 文本提取 | 已完成（依赖可提取文本） |
| 文本质量检测 | 已完成基础规则 |
| Agent 文档审核接口 | 已完成接口和调用链 |
| 文本切分和重叠窗口 | 已完成 |
| 阿里百炼 Embedding 接入 | 已完成，可配置 |
| 本地 Embedding 回退 | 已完成 |
| 向量持久化和余弦检索 | 已完成 |
| 检索结果注入 Agent | 已完成 |
| 来源标记 | 已完成基础文本引用 |
| 独立前端来源卡片 | 尚未完成 |
| OCR 扫描件识别 | 尚未完成 |
| 异步队列和大规模优化 | 尚未完成 |

## 2. 总体链路

当前链路如下：

```text
浏览器上传文件
  -> Spring JWT 校验和上传边界检查
  -> 本地文件存储 + documents 元数据
  -> Apache Tika 提取文本
  -> 文本质量检测
  -> 正常文本或 Agent 审核后的 normalized_text
  -> 按窗口切分 document_chunks
  -> EmbeddingService 生成向量
  -> 保存 chunk 和 embedding
  -> 用户提问时按 user_id 检索
  -> CoreChatRequest.knowledge_context
  -> Python Prompt
  -> LangChain Agent 回答并标注来源
```

浏览器不直接访问文件系统、数据库或 Python Agent。Spring 仍然是认证、权限、文档 API 和 Agent 转发的边界。

## 3. 上传和元数据

文档模块位于 `server-spring/src/main/java/com/plexus/personal/document/`，主要职责包括：

- 只允许 PDF、DOCX、Markdown 和 TXT；老式 DOC 暂不接收，避免上传白名单超出当前解析和验收边界。
- 默认限制文件大小为 10 MB，可通过 `ATLAS_DOCUMENT_MAX_BYTES` 调整。
- 原始文件名只用于展示，实际存储使用 UUID storage key。
- 文件属于当前 JWT 用户，所有列表、详情、删除和重建索引操作都检查 `user_id`。
- 文档保存在本地 `data/documents/{userId}/{uuid}.ext`。

数据库迁移：

- `V8__create_documents.sql`：文档元数据。
- `V9__create_document_chunks.sql`：切分片段。
- `V10__add_chunk_embeddings.sql`：片段向量。
- `V11__add_document_quality_review.sql`：原文、规范化文本和质量审核信息。

`documents` 保存原始文件定位、文件大小、媒体类型、扩展名、SHA-256、状态和失败原因；质量审核阶段还保存：

- `raw_text`：解析器原始输出。
- `normalized_text`：审核后的文本，审核未完成时不覆盖原文。
- `quality_status`：例如 `PASS` 或 `SUSPECT`。
- `quality_score`：基础质量评分。
- `review_result`：审核 Agent 的结构化结果。

文档状态包括：

```text
PENDING -> READY
         -> NEEDS_REVIEW
         -> FAILED
```

后续如果引入异步任务，可以扩展为 `PARSING`、`CHUNKING`、`EMBEDDING` 和 `INDEXED`。

## 4. Apache Tika 和文本提取

Apache Tika 是 Apache 基金会的文档内容提取工具包，负责识别文件类型、提取文字和读取元数据。当前处理代码在 `DocumentProcessingService` 中使用：

```java
String raw = tika.parseToString(file.toFile()).trim();
```

当前格式行为：

- TXT：直接提取文字。
- Markdown：读取原始文本，不生成新的 Markdown 文件。
- DOCX：提取可识别的正文文本；老式 DOC 当前不在上传白名单中。
- PDF：提取 PDF 内已有的文字层。
- 扫描 PDF 或图片：当前没有 OCR，通常会提取为空或进入质量异常处理。

乱码不一定是 Tika 的单一缺陷，常见原因是 PDF 内部缺少正确的字体到 Unicode 映射。解析器拿到的字符已经错误时，后面的模型不能可靠地恢复原文。

## 5. 文本质量检测和 Agent 审核

基础质量检测位于 `DocumentProcessingService`，目前检查：

- Unicode replacement character（`�`）。
- 控制字符。
- 乱码比例和整体质量分数。

如果文本可疑，Spring 调用 Core：

```http
POST /documents/review
```

Core 的审核提示词要求模型：

- 修复确定的乱码、异常空格和明显断行。
- 不补写原文没有的事实、数字、专有名词或技术内容。
- 无法确认的内容保留原样并写入 `warnings`。
- 返回 `normalized_text`、`warnings` 和 `confidence`。

审核失败时，系统保留 `raw_text`，文档标记为 `NEEDS_REVIEW`，不会静默用模型猜测结果覆盖原文。这是文档知识库的重要事实边界：模型清洗结果不是原始事实，必须保留可追溯性。

## 6. Chunk 切分

当前按约 1200 个字符切分，并保留约 180 个字符重叠窗口：

```text
chunk 0: A B C D E
chunk 1:         E F G H I
```

每个 chunk 保存：

- `document_id`
- `user_id`
- `chunk_index`
- `content`
- `embedding`

`document_id` 和 `chunk_index` 保留了原文归属和顺序，重叠窗口减少句子被切断造成的上下文损失。

当前命中一个 chunk 时不会自动加载它的前后邻居；这是后续可以提升长文档回答完整性的方向。

## 7. Embedding 和向量检索

Embedding 抽象位于 `EmbeddingService`，支持两种模式：

### 阿里百炼模式

```powershell
$env:ATLAS_EMBEDDING_PROVIDER = "dashscope"
$env:DASHSCOPE_API_KEY = "你的密钥"
$env:ATLAS_EMBEDDING_MODEL = "text-embedding-v4"
$env:ATLAS_EMBEDDING_DIMENSIONS = "1024"
```

请求使用阿里百炼的 OpenAI 兼容接口：

```text
https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
```

### 本地回退模式

默认使用本地哈希向量，避免没有模型密钥时无法运行。中文使用字符和二元组生成 token，避免连续中文被当成一个词导致检索为空。

当前向量保存在 `document_chunks.embedding`，查询时对问题生成向量，再计算余弦相似度，返回前 5 个片段。

检索接口：

```http
GET /api/documents/search?q=Java面试&limit=5
```

注意：切换 Embedding 模型或维度后，旧文档需要重新索引：

```http
POST /api/documents/{id}/reindex
```

## 8. Agent 注入和来源引用

普通聊天请求在 Spring 的 `ChatService` 中先执行检索：

```java
List<Map<String, Object>> knowledge =
    knowledgeSearchService.search(userId, request.query(), 5);
```

检索结果进入 `CoreChatRequest.knowledge_context`，Python Core 继续传给 `build_chat_messages`，最后形成：

```text
<knowledge_context>
[kb-agent-live.txt#6] 文档片段内容
</knowledge_context>
```

提示词要求模型使用资料时标注：

```text
[来源: kb-agent-live.txt#6]
```

这条链路是“Spring 预检索后注入上下文”，不是 Agent 自己决定调用检索工具。后续可以把知识库检索包装成 LangChain Tool，让 Agent 根据问题决定是否检索。

## 9. 前端知识库页面

知识库页面在：

- `web/src/KnowledgePage.tsx`
- `web/src/knowledge.css`

当前页面支持：

- 拖拽上传和文件选择。
- PDF、Word、Markdown、TXT 格式提示。
- 全部文档、可用于问答数量和总大小统计。
- 文档名称搜索。
- 全部、已就绪、需处理状态筛选。
- 重新索引和删除。
- 上传中、加载中、成功和失败反馈。
- 文档状态和失败原因展示。

侧边栏中已经完成的四个模块不再显示 `NEW` 或 `规划中`：面试助手、简历档案、职业规划和知识库。模拟面试和复盘记录仍保留规划提示。

## 10. 已验证结果

已验证的基础链路：


1. PostgreSQL Docker 容器可以启动并通过 healthcheck。
2. Flyway 已执行到 V11。
3. 文档上传返回正确的元数据和 `READY` 状态。
4. Tika 可以处理普通文本 PDF 和 TXT 测试文件。
5. 检索结果包含正确的 `document_id`、文件名、chunk 内容和相似度。
6. DashScope `text-embedding-v4` 接口返回 200。
7. Qwen Agent 能回答测试问题：

```text
蓝色火箭项目使用了 17 个分片 [来源: kb-agent-live.txt#6]。
```

8. Spring Boot 构建成功，前端 `npm run build` 成功，Python 文件检查通过。

## 11. 当前边界和下一步

当前还没有完成：

- 扫描 PDF 和图片 OCR。
- PDF 页码、段落坐标和可点击来源定位。
- 前端独立来源引用卡片。
- 命中 chunk 的邻居扩展和章节恢复。
- 混合检索、Rerank 和过滤条件。
- 异步解析和 Embedding 队列。
- 大规模向量数据库和索引优化。
- Recall@K、MRR、首 token 延迟和并发评测。

推荐后续顺序：

```text
OCR fallback
  -> 页码和坐标元数据
  -> 相邻 chunk 扩展
  -> 前端来源卡片
  -> LangChain 检索工具
  -> 混合检索和 Rerank
  -> 异步任务与效果评测
```
