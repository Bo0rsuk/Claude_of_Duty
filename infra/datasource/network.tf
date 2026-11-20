# A small dedicated network: the mocks play the role of external systems,
# so they stay apart from the application's own infrastructure.

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "this" {
  cidr_block           = "10.42.0.0/24"
  enable_dns_hostnames = true

  tags = { Name = var.name }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = var.name }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = aws_vpc.this.cidr_block
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "this" {
  name        = var.name
  description = "Mock data sources: HTTP(S) and Kafka in, everything out"
  vpc_id      = aws_vpc.this.id

  tags = { Name = var.name }
}

locals {
  ingress_rules = merge(
    { for cidr in var.http_ingress_cidrs : "http-${cidr}" => { port = 80, cidr = cidr } },
    { for cidr in var.http_ingress_cidrs : "https-${cidr}" => { port = 443, cidr = cidr } },
    { for cidr in var.kafka_ingress_cidrs : "kafka-${cidr}" => { port = 9092, cidr = cidr } },
  )
}

resource "aws_vpc_security_group_ingress_rule" "this" {
  for_each = local.ingress_rules

  security_group_id = aws_security_group.this.id
  description       = each.key
  cidr_ipv4         = each.value.cidr
  from_port         = each.value.port
  to_port           = each.value.port
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.this.id
  description       = "All outbound traffic"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
