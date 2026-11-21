variable "region" {
  description = "AWS region for the application."
  type        = string
}

variable "name" {
  description = "Name prefix for all resources. DynamoDB tables are named <name>-<entity>, which the contracts fix as cod-<entity>."
  type        = string
  default     = "cod"
}

variable "stream_enabled" {
  description = "Whether the Kinesis stream exists. It is the only resource here billed by the hour, so set this to false to pause the pipeline when nobody is working. Everything else is kept."
  type        = bool
  default     = true
}

variable "lambda_jar" {
  description = "Path to the jar built by `./mvnw package` in backend/. All functions run from this one file."
  type        = string
  default     = "../../backend/lambdas/target/lambdas.jar"
}

variable "worker_enabled" {
  description = "Whether the connector worker instance runs. It is billed by the hour, so set this to false when HTTP and Kafka sources are not needed."
  type        = bool
  default     = true
}

variable "worker_instance_type" {
  description = "EC2 instance type of the connector worker."
  type        = string
  default     = "t3.micro"
}

variable "worker_jar" {
  description = "Path to the worker jar built by `./mvnw package` in backend/."
  type        = string
  default     = "../../backend/worker/target/worker.jar"
}

variable "lambda_memory_mb" {
  description = "Memory for each function. More memory also means more CPU, which shortens Java cold starts."
  type        = number
  default     = 1024
}

variable "processor_batch_size" {
  description = "Maximum number of stream records handed to the processor in one invocation."
  type        = number
  default     = 100
}

variable "processor_parallelization_factor" {
  description = "Concurrent processor invocations per shard (1-10). The stream has 4 shards, so this times 4 is the processor's share of the account's Lambda concurrency."
  type        = number
  default     = 1
}

variable "notifier_max_concurrency" {
  description = "Concurrent notifier invocations per channel queue (2 or more)."
  type        = number
  default     = 2
}

variable "app_url" {
  description = "Public URL of the web application, used for the links in SMS and email. Empty means the CloudFront URL created here; set it only for a custom domain or local development."
  type        = string
  default     = ""
}

variable "notification_sender_email" {
  description = "Address that alert emails are sent from. Empty disables the email channel. An address on notification_sender_domain needs no mailbox; any other address gets a confirmation link from SES."
  type        = string
  default     = ""
}

variable "notification_sender_domain" {
  description = "Domain to verify in SES, e.g. \"example.pl\". Mail from a verified domain is DKIM-signed, which keeps it out of spam folders. Requires the DNS records from the ses_dkim_records output."
  type        = string
  default     = ""
}

variable "notification_verified_recipients" {
  description = "Recipient addresses to verify in SES. Needed only while the SES account is in the sandbox, where mail can go to verified addresses only."
  type        = list(string)
  default     = []
}

variable "events_channel_isolation" {
  description = "Whether real-time channels admit a subscriber only to their own organization's channels. Turn off only to debug the live feed: without it any signed-in user can subscribe to any channel."
  type        = bool
  default     = true
}

variable "push_key_parameter_prefix" {
  description = "Prefix of the SSM parameters holding the push notification key pair (<prefix>/vapid-public-key and <prefix>/vapid-private-key). Create them once with: java -cp backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.VapidKeys"
  type        = string
  default     = "/cod"
}

variable "push_contact" {
  description = "Contact that push services (Google, Apple, Mozilla) get with every push message, as a mailto: or https: URL."
  type        = string
  default     = "mailto:alerts@waw-out.pl"
}

variable "gemini_model" {
  description = "Google Gemini model used by the rule assistant. The smallest tier is enough: the task is turning one sentence into a few conditions."
  type        = string
  default     = "gemini-flash-lite-latest"
}

variable "gemini_api_key_parameter" {
  description = "Name of the SSM SecureString parameter holding the Gemini API key. Create it by hand so the key stays out of the repository and the Terraform state: aws ssm put-parameter --name /cod/gemini-api-key --type SecureString --value <key>"
  type        = string
  default     = "/cod/gemini-api-key"
}

variable "cors_allowed_origins" {
  description = "Origins allowed to call the API from a browser."
  type        = list(string)
  default     = ["*"]
}

variable "log_retention_days" {
  description = "How long CloudWatch keeps the function logs."
  type        = number
  default     = 7
}
