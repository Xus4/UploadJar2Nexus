# Upload JAR to Nexus

一个用于将本地Maven仓库中的JAR和POM文件批量上传到Nexus仓库的Java工具，同时支持将NPM包（.tgz文件）上传到Nexus NPM仓库。

## 功能特点

### Maven包上传
- 支持多线程并发上传，提高上传效率
- 自动解析Maven本地仓库的目录结构
- 支持快照版本（SNAPSHOT）筛选
- 自动处理文件路径解析，包括groupId、artifactId和version
- 提供详细的上传进度和统计信息
- 支持大文件上传，默认限制为1GB
- 优化的内存使用，动态调整缓冲区大小

### NPM包上传
- 支持批量上传本地.tgz格式的NPM包
- 自动处理NPM包的发布流程
- 支持自定义NPM仓库地址
- 提供详细的上传状态反馈

## 使用方法

### Maven包上传

#### 命令行运行

```bash
java -jar upload-jar2-nexus.jar <repositoryPath> <nexusUrl> <username> <password> <isSnapshots>
```

### NPM包上传
#### 前置准备

1. 生成NPM包文件：
```bash
# 使用PowerShell脚本生成所有.tgz文件
.\process_tgz.ps1
```

#### 配置和运行

1. 修改配置参数：
```javascript
// 设置.tgz文件所在目录
const tgzDir = '你的NPM包目录路径';
// 设置Nexus NPM仓库地址
const registryUrl = 'http://your-nexus-host:port/repository/npm-repository/';
```

2. 运行脚本：
```bash
node UploadTgz2NexusRunner.js
```

注意：使用NPM包上传功能前，请确保已通过 `npm login --registry=你的仓库地址` 完成仓库认证。

### 参数说明

1. `repositoryPath`: Maven本地仓库路径，例如：`C:\Users\Administrator\.m2\repository`
2. `nexusUrl`: Nexus仓库URL，例如：`http://localhost:8081/repository/maven2-test/`
3. `username`: Nexus仓库访问用户名
4. `password`: Nexus仓库访问密码
5. `isSnapshots`: 是否只上传快照版本（true/false）

### 示例

```bash
java -jar upload-jar2-nexus.jar "C:\Users\Administrator\.m2\repository" "http://localhost:8081/repository/maven2-test/" "admin" "admin123" "false"
```

## 运行环境要求

### JDK要求

- JDK版本：JDK 8或更高版本
- 运行时环境：需要配置JAVA_HOME环境变量
- 内存要求：建议至少1GB可用内存

### 系统兼容性

- 支持Windows/Linux/MacOS等主流操作系统
- 需要网络连接以访问Nexus仓库

## 配置说明

### 默认配置

- 线程池大小：CPU核心数的2倍（最大20线程）
- 上传任务队列大小：1000
- 单个文件大小限制：1GB
- HTTP连接超时：10秒

### 文件过滤

- 自动过滤sources.jar文件
- 根据isSnapshots参数决定是否只处理快照版本

## 注意事项

1. 确保有足够的磁盘空间和内存
2. 检查Nexus仓库的访问权限
3. 确保网络连接稳定
4. 大文件上传时注意调整Nexus服务器的上传限制

## 运行日志

程序运行时会输出详细的日志信息，包括：

- 上传进度
- 文件大小统计
- 总计上传文件数
- 运行时间统计
- 错误信息（如果有）
