# One EC2 instance running the mock data sources from mocks/datasource
# with docker compose. The files are shipped through cloud-init, so a change
# to the mocks replaces the instance on the next apply. The Elastic IP and the
# DNS name stay the same.

locals {
  mock_dir   = "${path.module}/../../mocks/datasource"
  remote_dir = "/opt/datasource"

  fqdn = var.domain_name == null ? null : "${var.subdomain}.${var.domain_name}"

  # Hostname in URLs and in the address the Kafka broker advertises to clients.
  public_host = coalesce(local.fqdn, aws_eip.this.public_ip)

  mock_files = [
    for file in fileset(local.mock_dir, "**") : file
    if !can(regex("__pycache__|\\.pyc$|\\.env|README\\.md$", file))
  ]

  compose_env = {
    # A hostname makes Caddy obtain a TLS certificate, ":80" keeps plain HTTP.
    SITE_ADDRESS      = coalesce(local.fqdn, ":80")
    PUBLIC_HOST       = local.public_host
    EVENTS_PER_SECOND = var.events_per_second
    WEBHOOK_URL       = var.webhook_url
  }

  cloud_config = yamlencode({
    write_files = concat(
      [
        for file in local.mock_files : {
          path    = "${local.remote_dir}/${file}"
          content = file("${local.mock_dir}/${file}")
        }
      ],
      [
        {
          path    = "${local.remote_dir}/.env"
          content = join("\n", [for key, value in local.compose_env : "${key}=${value}"])
        }
      ]
    )
    runcmd = [
      "curl -fsSL https://get.docker.com | sh",
      "docker compose --project-directory ${local.remote_dir} up --detach --build",
    ]
  })
}

data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

resource "aws_eip" "this" {
  domain = "vpc"

  tags = { Name = var.name }

  depends_on = [aws_internet_gateway.this]
}

resource "aws_instance" "this" {
  ami                    = data.aws_ssm_parameter.ubuntu_ami.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.this.id]
  iam_instance_profile   = aws_iam_instance_profile.this.name

  # Gzipped, because EC2 caps user data at 16 KB.
  user_data_base64            = base64gzip("#cloud-config\n${local.cloud_config}")
  user_data_replace_on_change = true

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  tags = { Name = var.name }

  lifecycle {
    # A newer Ubuntu image alone should not replace a running instance.
    ignore_changes = [ami]
  }
}

resource "aws_eip_association" "this" {
  allocation_id = aws_eip.this.id
  instance_id   = aws_instance.this.id
}

# No SSH: shell access goes through SSM Session Manager.
resource "aws_iam_role" "this" {
  name = var.name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "this" {
  name = var.name
  role = aws_iam_role.this.name
}
