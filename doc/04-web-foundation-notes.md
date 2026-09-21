# 第四课：React 前端基础（进行中）

## 本小节：受控表单与服务器简历档案

- `/resume` 当前实现姓名、目标岗位、个人简介、教育经历、实习经历、项目经历和专业技能的受控表单。
- `value` 来自 React state；`onChange` 通过 setter 更新状态并触发重新渲染。
- 对象更新使用 `setDraft(previous => ({ ...previous, [field]: value }))`，保留其他字段。
- `form onSubmit` 处理提交；`preventDefault()` 阻止浏览器刷新页面。
- 页面通过带 Bearer Token 的 `GET /api/resume` 加载当前用户档案，通过 `PUT /api/resume` 保存。
- `saved` 是上次成功保存的快照，支持判断未保存修改和撤销修改。
- 后端通过 `user_id` 唯一约束保证一个用户一条简历档案，Flyway migration 为 `V3__create_resume_profiles.sql`。
- 后端校验长度和必填字段，前端显示加载、保存和错误状态。

## 本小节：统一 JSON 请求层

- `web/src/apiClient.ts` 统一处理 JSON 请求、请求体序列化、Bearer Token 和错误解析。
- `authApi.ts` 只描述认证与简历接口，不再重复拼接 `fetch` 细节。
- `ChatDemo` 的 SSE 请求继续使用原生 `fetch`，因为它需要通过 `ReadableStream` 逐块解析事件。

## 待完成
### 加载与提交状态

简历页使用 loading、success、empty、error 区分请求状态；读取失败只显示错误和重试，不显示可保存的空表单。空响应仅在简历读取接口显式允许。卸载时取消读取请求，保存期间禁用表单并用 ref 防止重复提交；保存失败保留输入内容。


共享侧边栏布局、认证生命周期边界与完整验收仍需继续。求职档案暂不支持文件上传或简历解析。
下一课按课程顺序接入 LangChain InterviewManagerAgent。
