# The connector worker: one small instance running backend/worker as a
# service. It pulls from the HTTP and Kafka sources stored in DynamoDB and
# writes what it reads to the stream. It needs outbound access only.

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ssm_parameter" "amazon_linux" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_vpc" "worker" {
  cidr_block           = "10.43.0.0/24"
  enable_dns_hostnames = true

  tags = { Name = "${var.name}-worker" }
}

resource "aws_internet_gateway" "worker" {
  vpc_id = aws_vpc.worker.id

  tags = { Name = "${var.name}-worker" }
}

resource "aws_subnet" "worker" {
  vpc_id                  = aws_vpc.worker.id
  cidr_block              = aws_vpc.worker.cidr_block
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = { Name = "${var.name}-worker" }
}

resource "aws_route_table" "worker" {
  vpc_id = aws_vpc.worker.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.worker.id
  }

  tags = { Name = "${var.name}-worker" }
}

resource "aws_route_table_association" "worker" {
  subnet_id      = aws_subnet.worker.id
  route_table_id = aws_route_table.worker.id
}

resource "aws_security_group" "worker" {
  name        = "${var.name}-worker"
  description = "Connector worker: nothing in, everything out"
  vpc_id      = aws_vpc.worker.id

  tags = { Name = "${var.name}-worker" }
}

resource "aws_vpc_security_group_egress_rule" "worker" {
  security_group_id = aws_security_group.worker.id
  description       = "Sources to pull from and AWS APIs"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# The instance downloads the jar from here when it boots.
resource "aws_s3_bucket" "artifacts" {
  bucket_prefix = "${var.name}-artifacts-"
  force_destroy = true
}

resource "aws_s3_object" "worker_jar" {
  bucket      = aws_s3_bucket.artifacts.id
  key         = "worker.jar"
  source      = var.worker_jar
  source_hash = filemd5(var.worker_jar)
}

resource "aws_iam_role" "worker" {
  name = "${var.name}-worker"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

# Shell access through SSM Session Manager instead of SSH.
resource "aws_iam_role_policy_attachment" "worker_ssm" {
  role       = aws_iam_role.worker.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "worker" {
  name = "access"
  role = aws_iam_role.worker.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = ["${aws_s3_bucket.artifacts.arn}/${aws_s3_object.worker_jar.key}"]
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Scan"]
        Resource = [aws_dynamodb_table.this["sources"].arn]
      },
      {
        Effect   = "Allow"
        Action   = ["kinesis:PutRecord", "kinesis:PutRecords"]
        Resource = ["arn:aws:kinesis:${var.region}:*:stream/${local.stream_name}"]
      },
    ]
  })
}

resource "aws_iam_instance_profile" "worker" {
  name = "${var.name}-worker"
  role = aws_iam_role.worker.name
}

resource "aws_instance" "worker" {
  count = var.worker_enabled ? 1 : 0

  ami                    = data.aws_ssm_parameter.amazon_linux.value
  instance_type          = var.worker_instance_type
  subnet_id              = aws_subnet.worker.id
  vpc_security_group_ids = [aws_security_group.worker.id]
  iam_instance_profile   = aws_iam_instance_profile.worker.name

  # The jar's checksum is part of the script, so a new build replaces the instance.
  user_data = <<-EOT
    #!/bin/bash
    set -euo pipefail
    # worker.jar ${filemd5(var.worker_jar)}
    dnf install -y java-21-amazon-corretto-headless
    mkdir -p /opt/cod
    aws s3 cp s3://${aws_s3_bucket.artifacts.id}/${aws_s3_object.worker_jar.key} /opt/cod/worker.jar

    cat > /etc/systemd/system/cod-worker.service <<UNIT
    [Unit]
    Description=Claude of Duty connector worker
    After=network-online.target
    Wants=network-online.target

    [Service]
    Environment=AWS_REGION=${var.region}
    Environment=STREAM_NAME=${local.stream_name}
    Environment=TABLE_PREFIX=${var.name}-
    ExecStart=/usr/bin/java -Xmx384m -jar /opt/cod/worker.jar
    Restart=always
    RestartSec=5

    [Install]
    WantedBy=multi-user.target
    UNIT

    systemctl daemon-reload
    systemctl enable --now cod-worker
  EOT

  user_data_replace_on_change = true

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 8
    encrypted   = true
  }

  tags = { Name = "${var.name}-worker" }

  lifecycle {
    # A newer Amazon Linux image alone should not replace a running worker.
    ignore_changes = [ami]
  }

  depends_on = [aws_s3_object.worker_jar, aws_iam_role_policy.worker, aws_route_table_association.worker]
}
