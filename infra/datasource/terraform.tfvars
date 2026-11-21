# Settings for the mock data source instance. Loaded automatically by
# terraform plan/apply. No secrets belong in this file: it is committed.

region = "eu-central-1"

# The mocks are served at https://datasource.squawk.com.pl.
domain_name = "squawk.com.pl"
subdomain   = "datasource"

# The web application is reached at https://squawk.com.pl. This instance
# takes those requests and passes them on to CloudFront (frontend_url output
# of infra/app).
frontend_domain = "squawk.com.pl"
frontend_origin = "d3v62e73kgsdj9.cloudfront.net"

# DNS is managed at home.pl: add the A records from the dns_records output
# there. Set this only if the domain moves to a Route 53 hosted zone.
# route53_zone_id = "Z0123456789ABCDEFGHIJ"

instance_type     = "t3.small"
events_per_second = 2

# Ingest endpoint of the application that receives the pushed webhooks.
# Empty disables the webhook source.
webhook_url = ""
