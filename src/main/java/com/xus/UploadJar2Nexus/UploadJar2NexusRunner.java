package com.xus.UploadJar2Nexus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用于将JAR和POM文件上传到Nexus仓库的主类
 * 支持多线程并发上传，自动处理文件路径解析，支持快照版本筛选
 */
public class UploadJar2NexusRunner {
    // 线程池执行器，用于并发处理文件上传任务
    private ExecutorService executorService;
    // HTTP客户端，用于执行上传请求
    private CloseableHttpClient httpClient;
    // 当前活跃的上传任务计数器
    private final AtomicInteger activeUploads = new AtomicInteger(0);
    // 上传任务队列大小，可根据系统内存调整
    private static final int QUEUE_SIZE = 1000;
    // 已成功上传的文件计数器
    private static final AtomicInteger totalUploadedFiles = new AtomicInteger(0);
    // 上传速率监控（字节/秒）
    private static final AtomicLong uploadSpeed = new AtomicLong(0);
    // 上传失败的文件计数
    private static final AtomicInteger failedUploads = new AtomicInteger(0);
    // HTTP连接池配置
    private static final int MAX_CONNECTIONS = 200;
    private static final int MAX_PER_ROUTE = 20;
    // 缓冲区大小（8KB）
    private static final int BUFFER_SIZE = 8 * 1024;

    private static final Logger logger = LoggerFactory.getLogger(UploadJar2NexusRunner.class);
    // 已上传文件的总大小（字节）
    private static AtomicLong totalUploadSize = new AtomicLong(0);
    // 上传开始时间（毫秒）
    private static long startTime = 0;

    // Maven本地仓库路径，默认使用用户目录下的.m2/repository
    public String repositoryPath = "C:\\Users\\luyim\\.m2\\repository";
    // Nexus仓库URL，根据实际部署情况修改
    public String nexusUrl = "http://localhost:8081/repository/maven2-test/";
    // Nexus仓库访问用户名
    public String username = "admin";
    // Nexus仓库访问密码
    public String password = "admin123";
    // 是否只上传快照版本，true表示只上传带有SNAPSHOT的版本
    public boolean isSnapshots = false;
    // 上传线程池大小，默认为CPU核心数的2倍，可根据网络带宽和系统资源调整
    private int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    // 线程池最大大小限制，防止创建过多线程
    private static final int MAX_POOL_SIZE = 20;
    // 单个文件大小限制，默认1GB，可根据Nexus服务器配置调整
    private long maxFileSize = 1024 * 1024 * 1024;

    public UploadJar2NexusRunner() {
        // 确保线程池大小在合理范围内
        threadPoolSize = Math.min(threadPoolSize, MAX_POOL_SIZE);
        initHttpClient();
    }

    private void initHttpClient() {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
            new UsernamePasswordCredentials(username, password)
        );
        
        org.apache.http.client.config.RequestConfig requestConfig = org.apache.http.client.config.RequestConfig.custom()
            .setConnectTimeout(30000)
            .setSocketTimeout(30000)
            .setConnectionRequestTimeout(30000)
            .build();

        httpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credentialsProvider)
            .setMaxConnTotal(MAX_CONNECTIONS)
            .setMaxConnPerRoute(MAX_PER_ROUTE)
            .setDefaultRequestConfig(requestConfig)
            .setKeepAliveStrategy((response, context) -> 30 * 1000)
            .build();
    }

    public UploadJar2NexusRunner(String repositoryPath, String nexusUrl, String username, String password, boolean isSnapshots) {
        if (repositoryPath == null || repositoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository path cannot be null or empty");
        }
        if (nexusUrl == null || nexusUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Nexus URL cannot be null or empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        // 确保repositoryPath以文件分隔符结尾
        this.repositoryPath = repositoryPath.endsWith(File.separator) ? repositoryPath : repositoryPath + File.separator;
        this.nexusUrl = nexusUrl;
        this.username = username;
        this.password = password;
        this.isSnapshots = isSnapshots;
        
        // 初始化HTTP客户端
        threadPoolSize = Math.min(threadPoolSize, MAX_POOL_SIZE);
        initHttpClient();
    }

    /**
     * 启动上传进程的主方法
     * @param args 命令行参数，按顺序包括：
     *            1. repositoryPath - Maven本地仓库路径
     *            2. nexusUrl - Nexus仓库URL
     *            3. username - Nexus访问用户名
     *            4. password - Nexus访问密码
     *            5. isSnapshots - 是否只上传快照版本（true/false）
     */
    public static void main(String[] args) {
        UploadJar2NexusRunner runner = new UploadJar2NexusRunner();

        if (args.length < 5) {
            logger.info("Usage: java -jar jar-file.jar <repositoryPath> <nexusUrl> <username> <password> <isSnapshots>");
            //return;
        } else {
            runner = new UploadJar2NexusRunner(
                args[0], 
                args[1],
                args[2],
                args[3],
                Boolean.valueOf(args[4])
            );
        }
        
        runner.start();
    }

    /**
     * 启动上传进程
     */
    public void start() {
        if (repositoryPath == null || repositoryPath.trim().isEmpty()) {
            logger.error("Repository path is not set");
            return;
        }

        // 打印当前配置参数
        logger.info("当前配置参数：");
        logger.info("本地仓库路径: {}", repositoryPath);
        logger.info("Nexus仓库URL: {}", nexusUrl);
        logger.info("用户名: {}", username);
        logger.info("是否只上传快照版本: {}", isSnapshots);
        logger.info("线程池大小: {}", threadPoolSize);
        logger.info("单个文件大小限制: {} MB", maxFileSize / (1024 * 1024));

        try {
            startTime = System.currentTimeMillis();
            // 优化线程池配置
            executorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,  // 保持核心和最大线程数相同以避免线程数波动
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                (r, executor) -> {
                    try {
                        // 使用阻塞提交以避免任务被拒绝
                        executor.getQueue().put(r);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RejectedExecutionException("Task " + r.toString() +
                            " rejected from " + executor.toString(), e);
                    }
                }
            );

            File repositoryDir = new File(repositoryPath);
            logger.info("Repository path: {}", repositoryPath);
            if (!repositoryDir.exists() || !repositoryDir.isDirectory()) {
                logger.error("Invalid repository path: {}", repositoryPath);
                return;
            }

            logger.info("Starting upload process from repository: {}", repositoryPath);
            logger.debug("Nexus URL: {}", nexusUrl);
            logger.debug("Thread pool size: {}", threadPoolSize);
            extractJarInfo(repositoryDir);
            
            // Wait for all upload tasks to complete
            while (activeUploads.get() > 0) {
                Thread.sleep(1000);
                logger.debug("Waiting for {} active uploads to complete", activeUploads.get());
            }
            
            executorService.shutdown();
            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
                logger.warn("Timeout while waiting for uploads to complete");
            }
            
            double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
            double totalSizeMB = totalUploadSize.get() / (1024.0 * 1024.0);
            logger.info("Upload process completed - Total Files: {}, Total Size: {} MB in {} seconds",
                    totalUploadedFiles.get(),
                    String.format("%.2f", totalSizeMB),
                    String.format("%.2f", totalTime));
        } catch (InterruptedException e) {
            logger.error("Upload process interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdownNow();
            }
            if (httpClient != null) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    logger.error("Error closing httpClient", e);
                }
            }
        }
    }

    /**
     * 递归遍历目录提取JAR和POM文件
     * 会自动过滤掉sources.jar文件，只处理正常的jar包和pom文件
     * 根据isSnapshots配置决定是否只处理快照版本
     * 
     * @param dir 要搜索的目录，通常是Maven本地仓库目录
     */
    private void extractJarInfo(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    extractJarInfo(file); // Recursively process subdirectories
                } else if (file.getName().endsWith(".jar") && (!file.getName().endsWith("-sources.jar"))) {
                    processArtifactFile(file, "jar");
                } else if (file.getName().endsWith(".pom")) {
                    processArtifactFile(file, "pom");
                }
            }
        }
    }

    /**
     * 从文件路径中提取版本号
     * Maven仓库的目录结构：groupId/artifactId/version/files
     * 版本号是文件所在目录的父目录名
     * 
     * @param file 需要处理的文件
     * @return 提取出的版本号
     */
    private String getVersionFromFileName(File file) {
        String filePath = file.getAbsolutePath();
        String[] paths = filePath.split(File.separator.replace("\\", "\\\\"));
        return paths[paths.length - 2];
    }

    /**
     * 从文件路径中提取构件ID（artifactId）
     * Maven仓库的目录结构：groupId/artifactId/version/files
     * artifactId是版本号目录的父目录名
     * 
     * @param file 需要处理的文件
     * @return 提取出的构件ID
     */
    private String getArtifactIdFromFileName(File file) {
        String filePath = file.getAbsolutePath();
        String[] paths = filePath.split(File.separator.replace("\\", "\\\\"));
        return paths[paths.length - 3];
    }

    /**
     * 从文件路径中提取组ID（groupId）
     * Maven仓库的目录结构：groupId/artifactId/version/files
     * groupId是由repositoryPath到artifactId之间的路径，以点号分隔
     * 
     * @param file 需要处理的文件
     * @return 提取出的组ID，例如：org.springframework.boot
     */
    private String getGroupId(File file) {
        String filePath = file.getAbsolutePath();
        // 确保repositoryPath以文件分隔符结尾再进行替换
        String normalizedRepoPath = repositoryPath.endsWith(File.separator) ? repositoryPath : repositoryPath + File.separator;
        filePath = filePath.replace(normalizedRepoPath, "");
        String[] paths = filePath.split(File.separator.replace("\\", "\\\\"));
        int len = paths.length;
        String groupId = "";
        for (int i = 0; i < len - 3; i++) {
            groupId = groupId + paths[i] + ".";
        }
        groupId = groupId.substring(0, groupId.length() - 1);
        return groupId;
    }

    /**
     * 处理构件文件（JAR或POM）
     * 根据isSnapshots配置过滤版本，提交上传任务到线程池
     * 
     * @param file 要处理的文件（JAR或POM文件）
     * @param type 文件类型，"jar"或"pom"
     */
    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.2f KB", bytesPerSecond / 1024.0);
        } else {
            return String.format("%.2f MB", bytesPerSecond / (1024.0 * 1024.0));
        }
    }

    private void processArtifactFile(File file, String type) {
        if (isSnapshots) {
            if (!file.getName().toLowerCase().contains("snapshot")) {
                return;
            }
        } else {
            if (file.getName().toLowerCase().contains("snapshot")) {
                return;
            }
        }
        
        String version = getVersionFromFileName(file);
        String artifactId = getArtifactIdFromFileName(file);
        String groupId = getGroupId(file);

        logger.debug("Submitting upload task for: {}", file.getAbsolutePath());
        executorService.submit(() -> {
            activeUploads.incrementAndGet();
            try {
                if ("jar".equals(type)) {
                    uploadArtifactToNexus(groupId, artifactId, version, file, "jar");
                } else if ("pom".equals(type)) {
                    uploadArtifactToNexus(groupId, artifactId, version, file, "pom");
                }
            } catch (IOException e) {
                logger.error("Failed to upload {} file: {}", type.toUpperCase(), file.getName(), e);
            } finally {
                activeUploads.decrementAndGet();
            }
        });
    }

    /**
     * 上传构件到Nexus仓库
     * 根据Maven规范构建上传URL，格式为：
     * {nexusUrl}/{groupId}/{artifactId}/{version}/{artifactId}-{version}.{type}
     * 
     * @param groupId 构件的组ID
     * @param artifactId 构件ID
     * @param version 版本号
     * @param file 要上传的文件
     * @param type 文件类型（jar/pom）
     */
    private void uploadArtifactToNexus(String groupId, String artifactId, String version, File file, String type)
            throws IOException {
        // 移除URL中的多余空格并规范化URL格式
        String baseUrl = nexusUrl.trim();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        String url = baseUrl + groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/" + artifactId
                + "-" + version + "." + type;

        uploadFile(url.trim(), file);
    }

    /**
     * 使用HTTP PUT方法上传文件到指定URL
     * @param fileUrl 文件应该上传到的完整URL
     * @param file 要上传的文件
     * @throws IOException 如果在上传过程中发生I/O错误
     */
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private void uploadFile(String fileUrl, File file) throws IOException {
        if (file.length() > maxFileSize) {
            logger.error("文件 {} 超过允许的最大大小 {} MB", 
                file.getName(), maxFileSize / (1024 * 1024));
            return;
        }

        long startUploadTime = System.currentTimeMillis();
        int retryCount = 0;
        IOException lastException = null;

        // 读取文件内容到内存中，创建可重复使用的HttpEntity
        byte[] fileContent;
        try (FileInputStream fis = new FileInputStream(file)) {
            fileContent = new byte[(int) file.length()];
            try (java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis, BUFFER_SIZE)) {
                int bytesRead = bis.read(fileContent);
                if (bytesRead != file.length()) {
                    throw new IOException("Failed to read complete file content");
                }
            }
        }

        while (retryCount < MAX_RETRIES) {
            try {
                HttpPut request = new HttpPut(fileUrl);
                request.setHeader("Content-Type", "application/java-archive");
                
                // 使用ByteArrayEntity确保请求可重复
                org.apache.http.entity.ByteArrayEntity entity = new org.apache.http.entity.ByteArrayEntity(fileContent);
                request.setEntity(entity);
                
                HttpResponse response = httpClient.execute(request);
                int responseCode = response.getStatusLine().getStatusCode();
                
                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    long fileSize = file.length();
                    long currentTotal = totalUploadSize.addAndGet(fileSize);
                    double totalSizeMB = currentTotal / (1024.0 * 1024.0);
                    int fileCount = totalUploadedFiles.incrementAndGet();
                    
                    // 计算上传速度
                    long uploadTime = System.currentTimeMillis() - startUploadTime;
                    long speed = uploadTime > 0 ? (fileSize * 1000 / uploadTime) : 0;
                    uploadSpeed.set(speed);
                    
                    double elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0;
                    logger.info("已上传 {} ({}) - 速度: {}/s, 总计: {} MB, 文件数: {} 总用时: {} 秒",
                            file.getName(),
                            String.format("%.2f MB", fileSize / (1024.0 * 1024.0)),
                            formatSpeed(speed),
                            String.format("%.2f", totalSizeMB),
                            fileCount,
                            String.format("%.2f", elapsedTime));
                    return;
                } else {
                    lastException = new IOException("上传失败，HTTP状态码: " + responseCode);
                }
            } catch (IOException e) {
                lastException = e;
                logger.warn("上传重试 {}/{} 失败: {} - {}", 
                    retryCount + 1, MAX_RETRIES, file.getName(), e.getMessage());
            }

            retryCount++;
            if (retryCount < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("上传被中断", ie);
                }
            }
        }

        failedUploads.incrementAndGet();
        if (lastException != null) {
            logger.error("上传文件最终失败: {} - {}", file.getName(), lastException.getMessage());
            throw lastException;
        } else {
            throw new IOException("上传文件失败，已达到最大重试次数");
        }
    }
    }