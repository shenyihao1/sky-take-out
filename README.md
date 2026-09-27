# 苍穹外卖

## 新增员工：重复账号提示

### 功能与修改原因

调用 `POST /admin/employee` 新增员工时，如果登录账号 `username` 已存在，数据库的唯一索引 `idx_username` 会拒绝插入。原有异常处理仅覆盖业务异常，未处理的数据库异常会导致 HTTP 500，前端无法显示明确的重复账号提示。

现在将这类异常转换为项目现有的业务错误响应。例如重复提交账号 `zhangsan` 时，HTTP 状态为 `200`，响应内容为：

```json
{
  "code": 0,
  "msg": "用户 zhangsan 已存在",
  "data": null
}
```

这里 HTTP 200 表示接口已返回可识别的业务响应，`code: 0` 表示新增失败，不代表数据库插入成功。现有前端会在业务码不为 `1` 时显示 `msg`，因此无需修改前端。

此功能针对登录账号 `username`，不是员工主键 `id` 或身份证号 `idNumber`。当前新增 SQL 不写入主键 `id`。

### 修改文件与处理方式

| 文件 | 功能及原因 |
| --- | --- |
| [GlobalExceptionHandler.java](sky-server/src/main/java/com/sky/handler/GlobalExceptionHandler.java) | 捕获 SQL 完整性约束异常。仅当 MySQL 错误码为 `1062` 且冲突索引是 `idx_username` 时，提取重复账号并返回友好提示，避免将其他约束错误误报为用户重复。 |
| [GlobalExceptionHandlerTest.java](sky-server/src/test/java/com/sky/handler/GlobalExceptionHandlerTest.java) | 验证提示文本、HTTP 与业务码、Spring 包装异常的处理，以及原有成功响应和业务异常响应。 |

处理细节：

- 兼容 `idx_username` 和带限定名前缀的索引名称，例如 `employee.idx_username`。
- Spring 可沿异常原因链找到 `SQLIntegrityConstraintViolationException`；测试覆盖其被 `DuplicateKeyException` 包装的情况。
- 其他唯一键冲突、外键错误、空消息或不匹配的错误文本统一返回 `code: 0`、`msg: "未知错误"`，详细信息保留在服务端日志中。
- 继续依赖数据库唯一约束防止重复写入，包括并发提交；未增加先查询再插入的检查。
- 未修改数据库结构、前端、依赖版本或其他业务代码。

### 验证方式

使用项目兼容的 JDK 8 和 Maven，在仓库根目录运行：

```sh
mvn -pl sky-server -am test -Dtest=GlobalExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false
```

共 10 项测试：3 种索引名称、Spring 包装异常、其他唯一键冲突、外键失败、空异常消息、不匹配的异常消息、原有业务异常响应和新增成功响应。

测试使用 MockMvc 和模拟服务，不连接真实数据库，不写入员工数据，也不覆盖 JWT 鉴权。真实页面验证需重启更新后的后端，并使用有效登录态提交一个已存在的账号，预期显示“用户 xx 已存在”。
