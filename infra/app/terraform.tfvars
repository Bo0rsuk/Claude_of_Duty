# Settings for the application infrastructure. Loaded automatically by
# terraform plan/apply. No secrets belong in this file: it is committed.

region = "eu-central-1"
name   = "cod"

# Where the links in SMS and email lead. Empty uses the CloudFront URL of the
# hosted frontend (see the frontend_url output).
app_url = ""

# Address that alert emails are sent from. An address on
# notification_sender_domain needs no mailbox. Any other address gets a
# confirmation link from SES and is likely to land in spam, because the mail
# is not signed by its domain.
notification_sender_email = "krzysztofsmogorzewski50@gmail.com"

# Domain to verify in SES so that mail is DKIM-signed. After apply, add the
# three CNAME records from `terraform output ses_dkim_records` at the DNS provider.
notification_sender_domain = ""

# Recipients to verify while the SES account is in the sandbox, where mail
# can go to verified addresses only. Each gets a confirmation link.
notification_verified_recipients = []

# Push notifications. The key pair is not here: create it once with
#   java -cp backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.VapidKeys
push_contact = "mailto:alerts@waw-out.pl"

# Real-time channels admit a user only to their own organization's channels.
events_channel_isolation = true

# Rule assistant. The API key is not here: it is an SSM SecureString parameter
# created by hand, see gemini_api_key_parameter in variables.tf.
gemini_model             = "gemini-flash-lite-latest"
gemini_api_key_parameter = "/cod/gemini-api-key"

# Origins allowed to call the API from a browser. Narrow it down to app_url
# before the demo.
cors_allowed_origins = ["*"]

# The Kinesis stream is the only resource billed by the hour (about 1 USD a
# day). Set to false and apply to delete it while nobody is working; set back
# to true and apply to bring it back. Tables, users and settings are kept.
stream_enabled = true

# The connector worker pulls from HTTP and Kafka sources. It is one EC2
# instance (about 0.3 USD a day). Set to false to stop paying for it; webhook
# sources keep working without it.
worker_enabled       = true
worker_instance_type = "t3.micro"

lambda_memory_mb     = 1024
processor_batch_size = 100

# The account allows 10 concurrent Lambda executions in total (the default for
# new accounts). Ingest, processor, notifier and API share them, so the
# background consumers are kept small to leave room for ingest. Raise these
# two once the account's quota is increased.
processor_parallelization_factor = 1
notifier_max_concurrency         = 2
