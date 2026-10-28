# 本地文件夹 + Nginx 图片上传

## 实现说明
POST /admin/common/upload，multipart/form-data 的字段名为 file，沿用管理员 JWT 鉴权。
Controller 修复 Result<string>、缺少 import 和空返回，改为 Result<String>。
LocalUploadService 检查空文件并截取文件后缀，支持 JPG/JPEG/PNG/GIF；仅按后缀判断类型，不解析图片内容。大小限制由下方 Spring multipart 配置控制。
服务自动创建目录，使用 UUID + 后缀构建新文件名，通过 transferTo 保存文件，返回 Nginx 访问 URL；保存失败时抛出“文件上传失败”业务异常。
成功返回 {"code":1,"msg":null,"data":"http://localhost/images/随机文件名.png"}。
业务错误通过现有全局异常处理器返回 code=0。文件上传不写数据库，业务保存时再保存返回的 URL。

## Spring Boot 配置
在现有 application.yml 的 spring 节点下合并（不要重复声明 spring）：
```yaml
servlet:
  multipart:
    max-file-size: 2MB
    max-request-size: 3MB
```
在现有 sky 节点下添加：
```yaml
upload:
  path: E:/sky-upload/images
  base-url: http://localhost/images
```
也可设置环境变量 SKY_UPLOAD_PATH、SKY_UPLOAD_BASE_URL、SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=2MB、SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=3MB。
未配置时目录为启动工作目录下 uploads，URL 为 http://localhost/images；部署建议使用明确的绝对路径。

## Nginx 配置
合并到现有 server，保留原有前端与 /api/ 代理配置：
```nginx
client_max_body_size 3m;
location ^~ /images/ {
    alias E:/sky-upload/images/;
    autoindex off;
    add_header X-Content-Type-Options nosniff always;
}
```
alias 必须和上传目录一致，末尾保留斜杠。base-url 必须是浏览器能访问的 Nginx 地址（远程部署不能填 localhost）。
后端账户需要目录写权限，Nginx 账户需要读取权限。Linux 示例 /srv/sky-upload/images/。
执行 nginx -t 后再 nginx -s reload。多实例需共享文件目录；容器需持久化挂载。

## 验证
```bash
curl -X POST http://localhost:8080/admin/common/upload -H "token: 替换为登录令牌" -F "file=@test.png"
```
检查 data URL 能显示图片，并测试空文件、不支持的后缀、超限文件（配置 multipart 后）、同名多次上传。不要把上传目录提交到 Git。
Spring multipart 超限在进入 Controller 前被拒绝；Nginx 超限返回 HTTP 413。