# Upload2Nexus 项目

## 项目简介

Upload2Nexus是一个用于将本地Maven仓库中的JAR和POM文件批量上传到Nexus仓库的Java工具。它支持多线程并发上传，能够自动处理文件路径解析，并支持快照版本筛选。

## 主要特点

- 多线程并发上传，提高传输效率
- 自动解析Maven本地仓库的目录结构
- 支持选择性上传快照版本或正式版本
- 实时显示上传进度和统计信息
- 自动处理大文件上传，优化传输性能
- 支持HTTP/2协议

## 使用说明

### 运行方式

```bash
java -jar upload2nexus.jar <repositoryPath> <nexusUrl> <username> <password> <isSnapshots>
```

### 参数说明

1. `repositoryPath`: Maven本地仓库路径，例如：`C:\Users\Administrator\.m2\repository\`
2. `nexusUrl`: Nexus仓库URL，例如：`http://localhost:8081/repository/maven2-test/`
3. `username`: Nexus仓库访问用户名
4. `password`: Nexus仓库访问密码
5. `isSnapshots`: 是否只上传快照版本（true/false）

### 配置参数

程序支持以下可调整的配置参数：

| 参数名 | 默认值 | 说明 |
|--------|--------|--------|
| threadPoolSize | CPU核心数×2 | 上传线程池大小，最大值20 |
| maxFileSize | 1GB | 单个文件大小限制 |
| QUEUE_SIZE | 1000 | 上传任务队列大小 |

## 注意事项

1. 确保Nexus服务器可以正常访问，并且提供的用户名和密码正确
2. 上传前检查本地仓库路径是否正确
3. 程序会自动过滤掉sources.jar文件，只处理正常的jar包和pom文件
4. 对于大文件上传，程序会自动优化缓冲区大小：
   - 小于1MB的文件使用64KB缓冲区
   - 1-10MB的文件使用512KB缓冲区
   - 大于10MB的文件使用1MB缓冲区

## 运行示例

```bash
java -jar upload2nexus.jar "C:\Users\Administrator\.m2\repository" "http://localhost:8081/repository/maven2-test/" "admin" "admin123" "false"
```

## 输出信息

程序运行时会显示以下信息：
- 上传进度
- 已上传文件数量
- 总上传大小
- 运行时间
- 错误信息（如果有）