const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

// ====== 修改以下参数 ======
const tgzDir = 'E:\\github\\chat-master\\chat-cvcise\\packed-dependencies';
const registryUrl = 'http://localhost:8081/repository/npm-test/';
// 如果需要认证，请确保已通过 `npm login --registry` 登录
// =========================

function publishPackage(filePath) {
  return new Promise((resolve, reject) => {
    const cmd = `npm publish "${filePath}" --registry ${registryUrl}`;
    console.log(`正在上传: ${path.basename(filePath)}`);
    exec(cmd, (error, stdout, stderr) => {
      if (error) {
        console.error(`❌ 上传失败: ${path.basename(filePath)}\n${stderr}`);
        reject(error);
      } else {
        console.log(`✅ 上传成功: ${path.basename(filePath)}\n${stdout}`);
        resolve(stdout);
      }
    });
  });
}

async function main() {
  if (!fs.existsSync(tgzDir)) {
    console.error(`目录不存在: ${tgzDir}`);
    process.exit(1);
  }

  const files = fs.readdirSync(tgzDir)
    .filter(file => file.endsWith('.tgz'));

  if (files.length === 0) {
    console.log('未找到任何 .tgz 文件');
    return;
  }

  console.log(`共发现 ${files.length} 个包，开始上传...`);

  for (const file of files) {
    const fullPath = path.join(tgzDir, file);
    try {
      await publishPackage(fullPath);
    } catch (err) {
      console.error(`⚠️ 忽略失败包: ${file}`);
    }
  }

  console.log('✅ 所有包上传完成');
}

main();
