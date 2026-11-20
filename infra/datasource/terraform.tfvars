# Settings for the mock data source instance. Loaded automatically by
# terraform plan/apply. No secrets belong in this file: it is committed.

region = "eu-central-1"

# The mocks are served at https://datasource.waw-out.pl.
domain_name = "waw-out.pl"
subdomain   = "datasource"

# Hosted zone of waw-out.pl in Route 53. Leave commented out when DNS is
# managed elsewhere and add the A record for "datasource" there yourself,
# pointing at the public_ip output.
# route53_zone_id = "Z0123456789ABCDEFGHIJ"

instance_type     = "t3.small"
events_per_second = 2

# Ingest endpoint of the application that receives the pushed webhooks.
# Empty disables the webhook source.
webhook_url = ""
