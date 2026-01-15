# 1) Provider 설정
provider "aws" {
  region = "ap-northeast-2"
}

# 2) VPC 및 네트워크 설정
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  tags = { Name = "pin4u-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
}

resource "aws_subnet" "public_1" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.3.0/24"
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

# 3) 보안 그룹
resource "aws_security_group" "ec2" {
  name        = "pin4u-ec2-sg"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name        = "pin4u-rds-sg"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 4) IAM 설정 (SSM 및 ECR 접근 권한)
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

# [수정/추가] 4-1) SSH 키 페어를 AWS 클라우드에 업로드
# 이 로직이 있어야 AWS 콘솔에 키가 없어도 자동으로 등록됩니다.
resource "aws_key_pair" "portfolio" {
  key_name   = "portfolio-key"
  public_key = file("${path.module}/portfolio-key.pub")
}

# 5) EC2 인스턴스
resource "aws_instance" "app" {
  ami                    = "ami-0e9bfdb247cc8de84" # Ubuntu 22.04 LTS
  instance_type          = "t3.micro"
  subnet_id              = aws_subnet.public_1.id
  vpc_security_group_ids = [aws_security_group.ec2.id]

  # 위에서 선언한 aws_key_pair의 이름을 참조합니다.
  key_name               = aws_key_pair.portfolio.key_name

  iam_instance_profile   = aws_iam_instance_profile.ec2_profile.name
  monitoring             = true

  user_data = <<-EOF
              #!/bin/bash
              sudo apt-get update
              sudo apt-get install -y docker.io
              sudo systemctl start docker
              sudo systemctl enable docker
              sudo usermod -aG docker ubuntu
              EOF

  tags = { Name = "pin4u-app" }
}

resource "aws_eip" "app" {
  instance = aws_instance.app.id
}

# 6) RDS 설정 (PostgreSQL)
resource "aws_db_subnet_group" "rds" {
  name       = "pin4u-rds-subnet-group"
  subnet_ids = [aws_subnet.public_1.id, aws_subnet.public_2.id]
}

resource "aws_db_instance" "portfolio" {
  identifier           = "pin4u-db"
  engine               = "postgres"

  # [수정] engine_version을 "16"으로 변경하여 AWS가 최신 패치 버전을 자동으로 선택하게 함
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

# 7) 출력값
output "public_ip" { value = aws_eip.app.public_ip }
output "rds_endpoint" { value = aws_db_instance.portfolio.endpoint }
output "instance_id" { value = aws_instance.app.id }