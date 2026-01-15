# Provider 설정
provider "aws" {
  region = "ap-northeast-2"
}

# VPC 및 네트워크 설정
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

# 보안 그룹 (사용자 요청에 따라 0.0.0.0/0 유지)
resource "aws_security_group" "ec2" {
  name   = "pin4u-ec2-sg"
  vpc_id = aws_vpc.main.id
  ingress { from_port = 8080; to_port = 8080; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
  ingress { from_port = 22; to_port = 22; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

resource "aws_security_group" "rds" {
  name   = "pin4u-rds-sg"
  vpc_id = aws_vpc.main.id
  ingress { from_port = 5432; to_port = 5432; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
}

# RDS 서브넷 그룹
resource "aws_db_subnet_group" "rds" {
  name       = "pin4u-rds-subnet-group"
  subnet_ids = [aws_subnet.public_1.id, aws_subnet.public_2.id]
}

# RDS 인스턴스 (PostgreSQL 설정 반영)
resource "aws_db_instance" "portfolio" {
  identifier           = "pin4u-db"
  engine               = "postgres"             # PostgreSQL 엔진
  engine_version       = "16.3"                 # 버전 고정
  instance_class       = "db.t3.micro"
  allocated_storage    = 20
  db_name              = "pin4u_be"             # DB 이름 변경
  username             = "pin4u"                # build.gradle 계정 반영
  password             = var.db_password
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.rds.name
  skip_final_snapshot    = true
  publicly_accessible    = true                 # 테스트용 공개
  monitoring_interval    = 60                   # 성능 측정용 모니터링 활성화
}

# 출력값
output "rds_endpoint" { value = aws_db_instance.portfolio.endpoint }