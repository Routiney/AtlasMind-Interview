# 第八节课学习笔记：Redis 会话缓存与 Kafka 文档异步处理

本节把第七节的 Redis、Kafka 方案落到了代码中。重点不是为了给项目增加两个中间件，而是区分三类数据和三类职责：

- PostgreSQL 保存用户、会话、消息、文档元数据和最终业务结果。
- Redis 保存可以快速读取、可以过期或可以重建的热点数据和任务短状态。
- Kafka 保存异步任务事件，让慢任务脱离 HTTP 请求，并支持重试、消费组和 offset 恢复。

本节完成了聊天历史的 Redis 读取缓存，以及文档解析、切分、Embedding 的 Kafka 异步处理。

## 1. 为什么文档处理适合接入 Kafka

一次文档处理包含多个慢步骤：

```text
Tika 文本提取
  -> 文本质量检查
  -> Agent 质量审核（可选）
  -> 文本切分
  -> 每个 chunk 生成 Embedding
  -> 保存 document_chunks
```

这些步骤对同一个文档仍然是有先后顺序的，但“多个文档之间”没有必要互相等待。例如用户连续上传三个文档时，可以先快速完成三个文件的落盘和任务登记，再由消费者逐个或并行处理。

因此 Kafka 的价值不在于把一条线强行拆成没有顺序的步骤，而在于：

1. 上传接口不再同步等待 Tika 和 Embedding 完成。
2. 多个文档可以排队，消费者可以通过分区和并发数扩展处理能力。
3. 消费失败可以重试，服务重启后可以根据 offset 继续消费。
4. 任务事件和业务状态可以分开，后续可以增加独立的通知、审计或统计消费者。

聊天记忆刷新目前仍使用 Spring 的进程内异步执行器，因为它是单个会话的低频后台刷新，不足以单独引入 Kafka。后续如果需要跨实例调度、失败重试和独立扩容，再把它改为 Kafka 任务也比较自然。

## 2. 本节实现后的整体链路

```text
浏览器上传文件
  -> Spring 校验文件并落盘
  -> PostgreSQL 写入 documents(status=PENDING)
  -> Kafka: DocumentUploaded
  -> Kafka: DocumentParseRequested
  -> DocumentProcessingConsumer 消费解析任务
       -> Tika 提取文本
       -> 质量检测和可选 Agent 审核
       -> 切分文本
       -> 生成 Embedding
       -> PostgreSQL 更新文档和 chunks
       -> Redis 写入任务进度
  -> 成功: DocumentIndexed
  -> 失败: DocumentIndexFailed，并按策略重试
```

上传接口在事件成功写入 Kafka 后返回文档元数据，此时文档通常还是 `PENDING`。前端通过文档列表中的状态看到后续的 `READY` 或 `FAILED`。

相关实现位置：

- `server-spring/src/main/java/com/plexus/personal/document/application/DocumentService.java`
- `server-spring/src/main/java/com/plexus/personal/document/application/DocumentProcessingService.java`
- `server-spring/src/main/java/com/plexus/personal/document/infrastructure/messaging/DocumentProcessingConsumer.java`

## 3. Kafka 的概念如何映射到项目

### 3.1 Topic

文档事件使用主题：

```text
atlas.document-events.v1
```

`v1` 表示事件契约的第一版。后续如果字段含义发生不兼容变化，可以新增 `v2`，而不是让旧消费者猜测新格式。

### 3.2 Event

统一事件结构如下：

```json
{
  "eventId": "uuid",
  "eventType": "DocumentParseRequested",
  "version": 1,
  "aggregateId": "document-id",
  "userId": 101,
  "createdAt": "2026-09-29T10:00:00+08:00",
  "payload": {
    "storageKey": "101/file.pdf",
    "sha256": "..."
  }
}
```

字段含义：

| 字段 | 作用 |
|---|---|
| `eventId` | 事件唯一 ID，用于重复消费判断 |
| `eventType` | 区分上传、解析请求、索引成功和失败 |
| `version` | 事件结构版本 |
| `aggregateId` | 业务聚合 ID，本项目中是 `documentId` |
| `userId` | 做用户隔离和权限校验 |
| `createdAt` | 记录事件产生时间 |
| `payload` | 事件类型相关的附加数据 |

当前定义的事件类型为：

```text
DocumentUploaded
DocumentParseRequested
DocumentIndexed
DocumentIndexFailed
```

上传时先发送 `DocumentUploaded` 作为领域事件，再发送真正触发处理的 `DocumentParseRequested`。当前消费者只处理后者，前者可以供以后增加审计或通知消费者使用。

### 3.3 Partition、Key 和 Consumer Group

发送消息时使用 `documentId` 作为 Kafka key：

```text
key = aggregateId = documentId
```

这样同一个文档的事件会尽量保持在同一个分区中，方便维持该文档的事件顺序。

当前本地环境默认：

```text
分区数：1
消费者组：atlas-document-processor
消费者并发：1
```

想要同时处理更多文档时，需要同时增加 Topic 分区数和 `KAFKA_LISTENER_CONCURRENCY`。只有增加消费者线程而没有足够分区时，Kafka 不会真正提供更多并行消费能力。

## 4. Consumer 的处理过程

`DocumentProcessingConsumer` 收到消息后按以下顺序执行：

1. 使用 Jackson 反序列化事件。
2. 忽略当前消费者不负责的事件类型。
3. 根据 `userId + eventId` 查询 Redis 幂等标记。
4. 从 PostgreSQL 读取属于该用户的文档记录。
5. 使用文档的安全存储路径读取文件。
6. 调用 `DocumentProcessingService` 完成解析、切分和向量写入。
7. 成功时写入 Redis 成功进度，发送 `DocumentIndexed`，再记录消费完成标记。
8. 异常时写入失败进度，发送 `DocumentIndexFailed`，抛出异常交给 Kafka 错误处理器重试。

处理服务通过进度回调报告这些阶段：

```text
PARSING         10%
QUALITY_REVIEW  25%
CHUNKING        45%
EMBEDDING       50% - 95%
INDEXED         100%
```

当前错误处理器使用固定间隔重试两次：

```text
第一次失败 -> 等待 1 秒 -> 重试
第二次失败 -> 等待 1 秒 -> 重试
仍然失败   -> 记录失败并提交该消息的最终处理结果
```

这属于“至少一次投递”模型。消费者可能因为进程在业务写入后、提交 offset 前崩溃而再次收到消息，所以代码需要幂等设计。

## 5. Redis 中保存了什么

Redis 的 key 不是一个大而模糊的“记忆对象”，而是按用途拆分的数据结构。

| Key | Redis 类型 | Value | TTL | 用途 |
|---|---|---|---|---|
| `atlas:chat-memory:{userId}:{conversationId}` | String | `ConversationMemory` 的 JSON | 24 小时 | 会话摘要和结构化事实的热点缓存 |
| `atlas:chat-history:{userId}:{conversationId}` | List | 每条 `Message` 一个 JSON 元素，头部是最新消息 | 7 天 | Agent 构造多轮上下文的最近消息 |
| `atlas:document-progress:{userId}:{documentId}` | Hash | `status`、`stage`、`percent`、`message`、`updatedAt` | 24 小时 | 文档异步处理进度 |
| `atlas:kafka:document-event-done:{userId}:{eventId}` | String | `1` | 7 天 | Kafka 事件消费幂等标记 |

### 5.1 聊天历史缓存

聊天消息仍然先写 PostgreSQL。写入成功后，把新消息放入 Redis List 的头部：

```text
LPUSH atlas:chat-history:101:9001 newest-message-json
LTRIM atlas:chat-history:101:9001 0 999
EXPIRE atlas:chat-history:101:9001 7d
```

构造 Agent 上下文时：

1. 先查 Redis List。
2. 命中时直接使用消息历史，不再查 SQL。
3. 未命中时查询 PostgreSQL 最近 1000 条消息，并回填 Redis。

所以 Redis 是加速层，PostgreSQL 仍然是消息的持久化来源。Redis 故障或 key 过期不会丢失消息，只会触发 SQL 回源。

### 5.2 会话长期记忆缓存

`ConversationMemory` 包含摘要、facts、版本号和覆盖到的消息 ID。它用 String 保存完整 JSON，读取时反序列化成领域对象。更新或清除 PostgreSQL 记忆后，会同步更新或删除 Redis 缓存。

长期记忆刷新仍然由 `ConversationMemoryRefreshService` 在后台调用 Core Agent。Redis 只缓存刷新后的结果，并没有把 Redis 当作长期事实数据库。

### 5.3 文档进度和幂等

进度 Hash 示例：

```text
HGETALL atlas:document-progress:101:42

status    RUNNING
stage     EMBEDDING
percent   72
message   正在建立向量索引
updatedAt 2026-09-29T10:01:20Z
```

事件完成后写入幂等 key。相同 `eventId` 再次到达时，消费者发现完成标记就直接返回，不再重复处理。

这个标记是短期保护措施，不是永久业务记录。真正的文档状态、原文、质量信息和 chunk 仍保存在 PostgreSQL。

## 6. 为什么没有把整个处理过程存进 Kafka

Kafka 保存的是“任务事件”，不是文档全文、Embedding 结果或每个中间变量。原因有三个：

- 大文本和向量属于业务数据，最终需要按文档查询，适合放在 PostgreSQL 或向量数据库。
- Kafka 消息应该保持小而稳定，事件只携带文档 ID、用户 ID 和存储定位等必要信息。
- 处理失败时可以根据 `aggregateId` 回到数据库和文件存储重建任务。

因此本项目采用：

```text
Kafka = 任务分发和事件记录
Redis = 热点状态和短期幂等
PostgreSQL = 业务事实和最终结果
文件系统 = 原始文档内容
```

## 7. 本地启动和验收命令

### 7.1 启动基础设施

```powershell
docker compose up -d postgres redis kafka
docker compose ps
```

期望 `postgres`、`redis` 和 `kafka` 都是 `healthy` 或正常运行状态。

### 7.2 检查 Topic

```powershell
docker compose exec -T kafka kafka-topics.sh `
  --bootstrap-server 127.0.0.1:9092 `
  --describe `
  --topic atlas.document-events.v1
```

当前本地验收结果是 1 个分区、1 个副本。

### 7.3 验证 Kafka 的生产和消费

下面使用 `DocumentUploaded` 做消息通路验证。它不会要求数据库中存在对应文档，消费者会识别后忽略该事件：

```powershell
'{"eventId":"verify-uploaded-1","eventType":"DocumentUploaded","version":1,"aggregateId":"1","userId":101,"createdAt":"2026-09-29T10:00:00+08:00","payload":{}}' |
  docker compose exec -T kafka kafka-console-producer.sh `
    --bootstrap-server 127.0.0.1:9092 `
    --topic atlas.document-events.v1

docker compose exec -T kafka kafka-console-consumer.sh `
  --bootstrap-server 127.0.0.1:9092 `
  --topic atlas.document-events.v1 `
  --from-beginning `
  --max-messages 1 `
  --timeout-ms 5000
```

### 7.4 验证 Spring 服务

```powershell
cd server-spring
mvn -B test
mvn -B -DskipTests package
java -jar target/server-spring-0.0.1-SNAPSHOT.jar
```

启动日志应能看到消费者加入：

```text
groupId=atlas-document-processor
partitions assigned: [atlas.document-events.v1-0]
```

健康检查：

```powershell
curl.exe http://127.0.0.1:8200/agents/PlexusAgent/health
```

当前验证结果：

- Kafka、Redis、PostgreSQL 容器健康。
- Kafka Topic 可以创建、生产和消费消息。
- Spring 服务可以连接 Kafka 并加入消费者组。
- Redis 缓存测试 4 个全部通过。
- Maven 打包成功。
- `git diff --check` 没有发现空白错误。

## 8. 当前边界和验收时要主动说明的内容

当前实现适合本地学习和单机验收，还没有宣称生产级可靠性：

1. Kafka 是单节点、单分区、单副本配置，主要用于本地开发。
2. 采用至少一次投递和业务幂等，没有实现 Exactly-once。
3. 还没有 Outbox，因此 PostgreSQL 写入成功但 Kafka 发布失败时需要由业务代码标记失败；生产环境可用 Outbox 补强一致性。
4. 还没有死信队列，超过重试次数的消息需要后续补充人工处理或隔离方案。
5. Redis 进度有 TTL，过期后应以 PostgreSQL 文档状态为准。
6. 当前质量审核未得到可靠规范文本时，文档会保留 `NEEDS_REVIEW` 和原始文本；是否阻断后续索引属于下一步产品决策。
7. 当前没有单独的进度查询 API，现有文档状态仍以 PostgreSQL 的 `PENDING`、`READY`、`NEEDS_REVIEW`、`FAILED` 为主。

后续可以按这个顺序增强：

```text
Outbox
  -> 死信队列和人工重放
  -> 进度查询 API / SSE
  -> 多分区和并发压测
  -> 文档事件审计表
  -> 把聊天记忆刷新也改成可重试的 Kafka 任务
```

