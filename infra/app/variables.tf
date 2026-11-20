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
  description = "Concurrent processor invocations per shard (1-10)."
  type        = number
  default     = 2
}

variable "app_url" {
  description = "Public URL of the web application. Used for the links in SMS and email."
  type        = string
  default     = "http://localhost:5173"
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
