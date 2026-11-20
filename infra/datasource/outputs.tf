locals {
  base_url = local.fqdn == null ? "http://${aws_eip.this.public_ip}" : "https://${local.fqdn}"
}

output "public_ip" {
  description = "Elastic IP of the instance. Point the DNS A record here when the domain is not in Route 53."
  value       = aws_eip.this.public_ip
}

output "dns_record" {
  description = "Record to add at the DNS provider of the domain. Null when Terraform manages it in Route 53 or no domain is set."
  value = local.fqdn == null || var.route53_zone_id != null ? null : {
    zone  = var.domain_name
    name  = var.subdomain
    type  = "A"
    value = aws_eip.this.public_ip
    ttl   = 300
  }
}

output "base_url" {
  description = "Base URL of the mock data sources."
  value       = local.base_url
}

output "endpoints" {
  description = "Where each mock source can be read."
  value = {
    index       = "${local.base_url}/"
    json_events = "${local.base_url}/api/events"
    app_logs    = "${local.base_url}/logs"
    access_logs = "${local.base_url}/logs/access"
    kafka_rest  = "${local.base_url}/kafka/topics"
    burst       = "POST ${local.base_url}/scenarios/burst?count=500"
  }
}

output "kafka_bootstrap_servers" {
  description = "Bootstrap address for native Kafka clients."
  value       = "${local.public_host}:9092"
}

output "ssm_session_command" {
  description = "Opens a shell on the instance."
  value       = "aws ssm start-session --region ${var.region} --target ${aws_instance.this.id}"
}
