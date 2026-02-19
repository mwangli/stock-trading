# Docker 安装指南

## Ubuntu 24.04 环境

---

## 1. 一键安装

```bash
# 安装 Docker Engine
curl -fsSL https://get.docker.com | sh

# 启动并设置开机自启
sudo systemctl start docker
sudo systemctl enable docker

# 安装 Docker Compose
sudo apt install docker-compose-plugin

# 验证安装
docker --version
docker compose version
```

---

## 2. 配置镜像加速

```bash
# 创建配置目录
sudo mkdir -p /etc/docker

# 配置腾讯云镜像加速
sudo tee /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.mirrors.ustc.edu.cn",
    "https://registry.docker-cn.com"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
EOF

# 重启 Docker
sudo systemctl daemon-reload
sudo systemctl restart docker
```

---

## 3. 验证安装

```bash
# 验证 Docker
docker --version
# 输出: Docker version 26.x.x

# 验证 Docker Compose
docker compose version
# 输出: Docker Compose version v2.x.x

# 运行测试容器
docker run hello-world
```

---

## 4. 配置用户权限

```bash
# 将当前用户添加到 docker 组
sudo usermod -aG docker $USER

# 刷新组权限
newgrp docker

# 验证
docker ps
```

---

## 5. Docker Compose 常用命令

| 命令 | 说明 |
|------|------|
| `docker compose up -d` | 后台启动服务 |
| `docker compose down` | 停止并删除服务 |
| `docker compose ps` | 查看服务状态 |
| `docker compose logs -f` | 查看日志 |
| `docker compose restart` | 重启服务 |
| `docker compose build` | 构建镜像 |
| `docker compose pull` | 拉取镜像 |

---

## 6. 卸载 Docker

```bash
# 停止服务
sudo systemctl stop docker

# 卸载 Docker
sudo apt remove -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 删除数据目录
sudo rm -rf /var/lib/docker
sudo rm -rf /var/lib/containerd

# 删除镜像加速配置
sudo rm /etc/docker/daemon.json
```

---

## 7. 常见问题

### 7.1 Docker 启动失败

```bash
# 查看错误日志
sudo journalctl -u docker -n 50

# 检查配置
sudo dockerd --debug
```

### 7.2 端口占用

```bash
# 查看端口占用
sudo netstat -tlnp | grep <port>

# 杀死占用进程
sudo kill -9 <pid>
```

### 7.3 磁盘空间不足

```bash
# 清理未使用的镜像
docker image prune -a

# 清理构建缓存
docker builder prune

# 清理日志
sudo truncate -s 0 /var/lib/docker/containers/*/*.log
```
