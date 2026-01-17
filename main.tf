# 1) Provider 설정
provider "aws" {
  region = "ap-northeast-2"
}

# 2) VPC 및 네트워크 설정
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true # perf-test-lab 정합성
  tags = { Name = "pin4u-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
}

resource "aws_subnet" "public_1" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.3.0/24" # perf-test-lab 정합성
  availability_zone       = "ap-northeast-2a"
  map_public_ip_on_launch = true
}

resource "aws_subnet" "public_2" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.4.0/24"
  availability_zone       = "ap-northeast-2c"
  map_public_ip_on_launch = true
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
}

resource "aws_route_table_association" "public_1" {
  subnet_id      = aws_subnet.public_1.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_2" {
  subnet_id      = aws_subnet.public_2.id
  route_table_id = aws_route_table.public.id
}

# 3) 보안 그룹 (perf-test-lab 방식)
resource "aws_security_group" "ec2" {
  name_prefix = "pin4u-ec2-sg-" # 이름 중복 방지
  description = "Security group for EC2"
  vpc_id      = aws_vpc.main.id

  # App Port
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow Spring Boot application"
  }

  # SSH
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Prometheus & Grafana (perf-test-lab 정합성)
  ingress {
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "rds" {
  name_prefix = "pin4u-rds-sg-"
  description = "Security group for RDS"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 5432 # Postgres Port
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

# 4) IAM 설정 (perf-test-lab 정합성)
resource "aws_iam_role" "ec2_role" {
  name = "pin4u-ec2-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

# ECR 정책
resource "aws_iam_role_policy" "ecr_policy" {
  name = "pin4u-ecr-policy"
  role = aws_iam_role.ec2_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ]
      Resource = "*"
    }]
  })
}

# CloudWatch & SSM 정책
resource "aws_iam_role_policy_attachment" "ssm_policy" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_role_policy_attachment" "cw_agent" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

resource "aws_iam_instance_profile" "ec2_profile" {
  name = "pin4u-ec2-profile"
  role = aws_iam_role.ec2_role.name
}

# 4-1) SSH 키 페어
resource "aws_key_pair" "portfolio" {
  key_name   = "portfolio-key"
  public_key = file("${path.module}/portfolio-key.pub")
}

# 5) EC2 인스턴스
resource "aws_instance" "app" {
  ami                    = "ami-0e9bfdb247cc8de84" # Ubuntu 22.04 LTS
  instance_type          = "t3.micro" #
  subnet_id              = aws_subnet.public_1.id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  key_name               = aws_key_pair.portfolio.key_name
  iam_instance_profile   = aws_iam_instance_profile.ec2_profile.name
  monitoring             = true

  root_block_device {
    volume_size = 30
    volume_type = "gp3" #
  }

  # [중요] perf-test-lab의 user_data와 100% 동일하게 구성 (패키지 설치 순서 준수)
  # [추가] 네트워크 물리 보정 (MTU 1500 / Ethtool) 및 Swap 설정
  user_data = <<-EOF
              #!/bin/bash
              set -e

              # 1. Swap 메모리 설정 (OOM 방지)
              fallocate -l 2G /swapfile
              chmod 600 /swapfile
              mkswap /swapfile
              swapon /swapfile
              echo '/swapfile none swap sw 0 0' | tee -a /etc/fstab

              # 2. 네트워크 물리 보정 (T3 인스턴스 패킷 드랍 방지)
              # ethtool 설치
              apt-get update
              apt-get install -y ethtool
              # 현재 인터페이스 찾기 (ens5 등)
              IFACE=$(ip route | grep default | awk '{print $5}')
              # MTU를 1500으로 강제 고정 (Jumbo Frame 방지)
              ip link set dev $IFACE mtu 1500
              # 하드웨어 체크섬 오프로딩 끄기 (ENA 드라이버 버그 방지)
              ethtool -K $IFACE tx off rx off tso off gso off gro off lro off

              # 3. SSM Agent 설치
              sudo snap install amazon-ssm-agent --classic
              sudo systemctl start snap.amazon-ssm-agent.amazon-ssm-agent.service
              sudo systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service

              # 4. AWS CLI 설치
              curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
              unzip awscliv2.zip
              sudo ./aws/install

              # 5. Docker 설치
              sudo apt-get install -y ca-certificates curl gnupg
              sudo install -m 0755 -d /etc/apt/keyrings
              curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
              sudo chmod a+r /etc/apt/keyrings/docker.gpg
              echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
              sudo apt-get update
              sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
              sudo usermod -aG docker ubuntu
              sudo systemctl start docker
              sudo systemctl enable docker

              # 6. CloudWatch Agent 설치
              sudo wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
              sudo dpkg -i amazon-cloudwatch-agent.deb
              sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc/
              sudo bash -c 'cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json' << 'EOT'
              {
                "agent": { "metrics_collection_interval": 60, "run_as_user": "root" },
                "metrics": {
                  "append_dimensions": { "InstanceId": "$${aws:InstanceId}", "InstanceType": "$${aws:InstanceType}" },
                  "metrics_collected": {
                    "mem": { "measurement": ["mem_used_percent"], "metrics_collection_interval": 60 },
                    "swap": { "measurement": ["swap_used_percent"], "metrics_collection_interval": 60 }
                  }
                }
              }
              EOT
              sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
              sudo systemctl start amazon-cloudwatch-agent
              sudo systemctl enable amazon-cloudwatch-agent
              EOF

  tags = { Name = "pin4u-app" }
}

# 탄력적 IP (이것을 새로 생성해야 합니다!)
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  tags = { Name = "pin4u-app-eip" }
}

# 6) RDS 설정
resource "aws_db_subnet_group" "rds" {
  name       = "pin4u-rds-subnet-group"
  subnet_ids = [aws_subnet.public_1.id, aws_subnet.public_2.id]
}

resource "aws_db_instance" "portfolio" {
  identifier           = "pin4u-db"
  engine               = "postgres"
  engine_version       = "16"
  instance_class       = "db.t3.micro"
  allocated_storage    = 20
  db_name              = "pin4u_be"
  username             = "pin4u"
  password             = var.db_password
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.rds.name
  skip_final_snapshot    = true
  publicly_accessible    = true
  monitoring_interval    = 0
}

# 8) ECR 리포지토리
resource "aws_ecr_repository" "backend" {
  name                 = "backend-portfolio"
  image_tag_mutability = "MUTABLE"
  force_delete         = true
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository_policy" "backend_policy" {
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "AllowPushPull"
      Effect = "Allow"
      Principal = { AWS = "*" }
      Action = [
        "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:BatchCheckLayerAvailability",
        "ecr:PutImage", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload"
      ]
    }]
  })
}

# 7) 출력값
output "public_ip" { value = aws_eip.app.public_ip }
output "rds_endpoint" { value = aws_db_instance.portfolio.endpoint }
output "instance_id" { value = aws_instance.app.id }
output "ecr_repository_url" { value = aws_ecr_repository.backend.repository_url }