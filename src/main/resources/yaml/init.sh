#!/bin/bash
echo 开始安装docker-----------------
yum install -y docker
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": ["https://5fzd283a.mirror.aliyuncs.com"]
}
EOF

systemctl enable docker --now
systemctl status docker
echo docker安装完成-----------------
echo 开始安装k8s--------------------
cat <<EOF | tee /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=http://mirrors.aliyun.com/kubernetes/yum/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=0
repo_gpgcheck=0
gpgkey=http://mirrors.aliyun.com/kubernetes/yum/doc/yum-key.gpg
       http://mirrors.aliyun.com/kubernetes/yum/doc/rpm-package-key.gpg
exclude=kubelet kubeadm kubectl
EOF
yum install -y kubelet-1.20.9 kubeadm-1.20.9 kubectl-1.20.9 --disableexclude=kubernetes
systemctl enable kubelet --now
yum -y install bash-completion
source /usr/share/bash-completion/bash_completion
source <(kubectl completion bash)
echo "source <(kubectl completion bash)" >> ~/.bashrc
echo 安装k8s完成--------------------
echo 开始启动k8s--------------------
echo "10.0.4.9 cluster-endpoint" >> /etc/hosts
echo "10.0.4.9 test" >> /etc/hosts
kubeadm init \
--apiserver-advertise-address=10.0.4.9 \
--control-plane-endpoint=cluster-endpoint \
--image-repository registry.cn-hangzhou.aliyuncs.com/lfy_k8s_images \
--kubernetes-version v1.20.9 \
--service-cidr=100.120.0.0/16 \
--pod-network-cidr=192.168.0.0/16
mkdir -p $HOME/.kube
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config
export KUBECONFIG=/etc/kubernetes/admin.conf
kubectl apply -f https://docs.projectcalico.org/archive/v3.14/manifests/calico.yaml
kubectl describe node test |grep Taints
kubectl taint nodes --all node-role.kubernetes.io/master-
kubectl label nodes test node-role.kubernetes.io/worker=
kubectl get nodes
sed -i "26i  - --service-node-port-range=1-65535" /etc/kubernetes/manifests/kube-apiserver.yaml
kubecet restart kubelet
echo k8s启动完成---------------------
echo 开始启动项目
ls /root/*.yaml | xargs -n1 kubectl apply -f
kubectl get pod
