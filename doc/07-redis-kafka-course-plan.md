# 下一阶段课程：Redis、Kafka 与面试事件系统

本阶段在知识库和 Agent 基础上继续扩展。目标是把当前同步请求链路改造成可缓存、可异步处理、可追踪的面试事件系统，同时保留现有 Spring Boot、Python Agent 和 PostgreSQL 的职责边界。

## 课程目标

完成后能够解释并实现：

- Redis 在缓存、幂等、短期状态和任务进度中的职责。
- Kafka Producer、Consumer、Topic、Partition、Consumer Group 和 offset 的关系。
- 文档解析/Embedding 任务为什么适合异步事件驱动。
- 如何使用事件 ID、业务键和唯一约束保证重复消息不重复处理。
- 如何把事件状态落库，并让前端查询任务进度。

## 推荐顺序

### 第一部分：Redis 基础设施

先为 AtlasMind 增加 Redis 本地开发环境和 Spring Boot 连接配置，练习：

- Agent 请求短期状态和限流计数。
- 文档处理任务进度缓存。
- 会话记忆读取缓存及失效策略。
- 幂等键和短期去重。

Redis 只保存可以重建或有明确过期时间的数据，用户、会话、文档元数据和最终业务结果仍以 PostgreSQL 为准。

### 第二部分：Kafka 事件总线

为文档处理和面试训练定义事件：

```text
DocumentUploaded
DocumentParseRequested
DocumentIndexed
DocumentIndexFailed
InterviewQuestionGenerated
AnswerSubmitted
AnswerEvaluated
```

先实现一个 Producer 和一个 Consumer，使用明确的事件版本、事件 ID、aggregate ID、user ID 和 createdAt。第一阶段只要求单机开发环境、一个 Topic 和一个 Consumer Group。

### 第三部分：Redis + Kafka 协作

以文档知识库为主线改造：

```text
上传文档
  -> PostgreSQL 写入元数据
  -> Kafka 发布 DocumentParseRequested
  -> Consumer 执行解析、切分和 Embedding
  -> Redis 写入进度
  -> PostgreSQL 更新 READY / FAILED
  -> 前端查询任务状态
```

重点练习事务边界、失败重试、消费幂等和状态回放。不要在本阶段引入 WebSocket 广播；前端先使用轮询或 SSE 查询任务进度，WebSocket 仍属于独立课程边界。

## 验收标准

- Redis 和 Kafka 可以通过 Docker Compose 启动并健康检查。
- 同一个 `event_id` 重复投递时，业务结果只写入一次。
- Consumer 处理失败后会记录失败状态，不会把任务伪装成成功。
- 重启 Consumer 后可以从 Kafka offset 继续处理。
- Redis 中的进度键有 TTL，过期后可以从 PostgreSQL 和事件记录恢复。
- 用户 A 不能读取用户 B 的任务状态或事件数据。
- 至少有一组重复投递、消费失败和 Consumer 重启的可重复验收命令。

## 暂不进入的内容

- Kafka 集群扩容和跨机房部署。
- Redis Cluster、持久化调优和复杂分布式锁。
- Exactly-once 的宣传性实现；本项目先采用“至少一次投递 + 业务幂等”。
- WebSocket 多人协作广播。
- 压测、吞吐量调优和生产监控告警。
