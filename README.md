# OES Backend

OES 后端是基于 Spring Boot 3 和 Java 21 的在线考试系统服务端，负责认证授权、课程/班级/人员管理、考试会话、试卷导入、成绩统计以及 AI 辅助批改等能力。

## 技术栈

- Java 21
- Spring Boot 3.4.x
- Spring Security + JWT
- Spring Data JPA / Hibernate
- MySQL
- Redis
- Springdoc OpenAPI / Swagger UI
- Maven Wrapper

## 目录结构

```text
src/main/java/cn/edu/sdu/java/server
├── configs        # 安全、JWT、拦截器等配置
├── controllers    # REST API 控制器
├── models         # JPA 实体
├── payload        # 请求/响应对象
├── repositorys    # 数据访问层
├── services       # 业务服务
└── util           # 通用工具类
```

## 运行配置

主要配置文件：

```text
src/main/resources/application.yml
```

关键配置：

- 服务端口：`22222`
- 数据库：MySQL，当前在 `spring.datasource` 中配置
- Redis：默认 `localhost:6379`
- 附件目录：`attach.folder`
- JWT：`security.jwt`
- Swagger UI：`http://localhost:22222/swagger-ui.html`

AI 配置初始化样例：

```text
src/main/resources/apiconfig.jsonl
```

AI 批改缓存：

```text
ai-answer-cache.jsonl
```

实际运行时，AI API 配置优先读取数据库表 `ai_provider_config`。

## 启动与构建

在后端目录执行：

```powershell
cd D:\OES-course\JavaBackend\java-server
.\mvnw.cmd spring-boot:run
```

打包：

```powershell
.\mvnw.cmd clean package -DskipTests
```

构建产物：

```text
target/java-server-1.0.1-SNAPSHOT.jar
```

## 主要接口模块

- `/api/auth/**`：登录、注册、认证相关接口
- `/api/base/**`：基础菜单、字典、文件、个人基础功能
- `/api/admin/**`：管理员课程、班级、教师、学生、AI API 配置等管理接口
- `/api/teacher/**`：教师试卷、批改、统计、成绩管理接口
- `/api/student/**`：学生试卷、考试会话、草稿保存、提交、成绩查询接口

## 考试核心状态

学生维度的考试状态由 `student_exam_attempt` 记录：

- `未开始`：当前时间早于考试开始时间
- `未做`：考试已开放但学生尚未进入
- `草稿`：学生已进入考试或保存过答案
- `已结束`：学生提交、考试到期或后端自动关闭草稿

答案记录保存在 `student_exam_record`。

## 试卷上传格式

教师端支持上传：

- CSV
- Markdown
- JSON

CSV 表头固定：

```csv
type,content,optionA,optionB,optionC,optionD,answer,score
```

JSON 顶层字段：

```json
{
  "title": "Java 模拟考试",
  "courseId": 1,
  "startTime": "2026-06-02 15:00:00",
  "endTime": "2026-06-02 16:30:00",
  "status": "OPEN",
  "questions": []
}
```

时间会规范化为：

```text
yyyy-MM-dd HH:mm:ss
```

## AI 批改配置

配置字段：

- `name`：教师端显示名称
- `provider`：厂商标识，例如 `mock`、`openai`、`deepseek`、`custom`
- `enabled`：是否启用
- `endpoint`：Chat Completions 接口地址
- `apiKey`：直接保存的 API Key
- `apiKeyEnv`：环境变量名
- `model`：模型名
- `temperature`：建议 `0.0`
- `maxTokens`：建议不少于 `500`
- `timeoutSeconds`：默认 `30`

当前远程 AI 调用按 OpenAI Chat Completions 兼容格式发送。

## 验证建议

```powershell
.\mvnw.cmd package -DskipTests
```

服务启动后检查：

- `http://localhost:22222/swagger-ui.html`
- 前端是否能登录
- 学生是否能获取试卷、保存草稿、提交
- 教师是否能查看已结束试卷、AI 批改、查看统计
