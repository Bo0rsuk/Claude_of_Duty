# Created only when the domain is hosted in Route 53. With a domain kept at an
# external registrar, add an A record there pointing at the public_ip output.
resource "aws_route53_record" "datasource" {
  count = local.fqdn != null && var.route53_zone_id != null ? 1 : 0

  zone_id = var.route53_zone_id
  name    = local.fqdn
  type    = "A"
  ttl     = 60
  records = [aws_eip.this.public_ip]
}
