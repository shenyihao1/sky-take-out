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

## 启用、禁用员工账号

### 接口与功能

`POST /admin/employee/status/{status}?id={员工ID}`，携带登录得到的 `token` 请求头，无需 JSON 请求体。现有管理端按钮通过 Nginx 的 `/api/employee/status/{status}` 调用这个接口。

- `status=0`：禁用账号；之后使用该账号登录会返回“账号被锁定”。
- `status=1`：启用账号；恢复正常登录。
- 状态实际变化时更新 `status`、`update_time` 和 `update_user`，操作人取自 JWT 拦截器设置的 `BaseContext`，不接受客户端指定操作人。
- 重复提交相同状态返回成功，不重复写入；用户名、密码和其他员工资料保持原样。
- 仅接受状态 `0/1` 与正整数员工 ID；员工不存在、修改管理员 `admin` 或更新未命中记录时均不返回成功。数据库更新条件也排除了 `admin`，防止绕过页面保护。

示例：禁用员工 7：`POST /admin/employee/status/0?id=7`；重新启用：`POST /admin/employee/status/1?id=7`。

成功响应为 HTTP 200、`{"code":1,"msg":null,"data":null}`。本接口的业务校验失败响应为 HTTP 400、`{"code":0,"msg":"具体原因","data":null}`；缺失或类型错误的请求参数由 Spring 返回 HTTP 400。因为现有前端只根据 HTTP 200 判断状态修改成功，这样可以避免失败时错误显示“账号状态更改成功”。当前前端对 HTTP 400 显示通用请求错误，具体原因可查看响应的 `msg`。此调整仅作用于状态修改接口。

### 修改文件与原因

| 文件 | 作用 |
| --- | --- |
| [EmployeeController.java](sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java) | 接收路径中的状态和查询参数中的员工 ID，返回适配现有前端的成功或失败响应。 |
| [EmployeeService.java](sky-server/src/main/java/com/sky/service/EmployeeService.java) | 声明状态切换方法，保持现有 Controller → Service → Mapper 分层。 |
| [EmployeeServiceImpl.java](sky-server/src/main/java/com/sky/service/impl/EmployeeServiceImpl.java) | 校验参数、操作人及目标账号，保护管理员，记录操作信息并检查更新结果。 |
| [EmployeeMapper.java](sky-server/src/main/java/com/sky/mapper/EmployeeMapper.java) | 按 ID 查询目标账号，并使用参数绑定只更新状态和操作信息。 |
| [EmployeeStatusTest.java](sky-server/src/test/java/com/sky/EmployeeStatusTest.java) | 验证接口、业务校验、状态与登录联动，以及真实 MyBatis 参数绑定。 |

### 测试与适用范围

```sh
mvn -pl sky-server -am test -Dtest=EmployeeStatusTest,EmployeePageQueryTest,WebMvcDateFormatTest -Dsurefire.failIfNoSpecifiedTests=false
```

测试模拟数据库访问，不写入真实员工数据；MyBatis 参数绑定测试使用模拟的 JDBC Statement。页面验收时，重启后端，用已登录账号在员工管理页面切换普通员工状态，检查列表状态、最后操作时间，并分别验证禁用和启用后的登录行为。

本功能沿用项目现有鉴权方式：禁用会阻止后续登录，但不会主动撤销已经签发且未过期的 JWT。立即踢下线需要额外的令牌失效或请求状态校验机制，不在本次范围内。

实现前的全量测试已有 6 项失败，均来自 `GlobalExceptionHandlerTest`（重复账号提示与预期不一致、空异常消息等）。这些与本功能无关，本次保留原有 SQL 异常处理与测试。
