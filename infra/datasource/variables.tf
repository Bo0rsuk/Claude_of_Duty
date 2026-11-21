variable "region" {
  description = "AWS region for the mock data source instance."
  type        = string
  default     = "eu-central-1"
}

variable "name" {
  description = "Name prefix for all resources."
  type        = string
  default     = "cod-datasource"
}

variable "instance_type" {
  description = "EC2 instance type. The Kafka broker alone is given 1 GB of memory."
  type        = string
  default     = "t3.small"
}

variable "domain_name" {
  description = "Root domain, e.g. \"example.pl\". When null the mocks are served over plain HTTP on the public IP."
  type        = string
  default     = null
}

variable "subdomain" {
  description = "Subdomain of domain_name that points at the instance."
  type        = string
  default     = "datasource"
}

variable "frontend_domain" {
  description = "Domain of the web application that this instance forwards to frontend_origin, e.g. \"example.pl\". For a root domain whose DNS cannot point at CloudFront directly. Null disables it."
  type        = string
  default     = null
}

variable "frontend_origin" {
  description = "Host that serves the web application: the CloudFront domain from the frontend_url output of infra/app, without https://. Required with frontend_domain."
  type        = string
  default     = null
}

variable "route53_zone_id" {
  description = "Route 53 hosted zone of domain_name. When null no DNS record is created and the A record has to be added at the registrar."
  type        = string
  default     = null
}

variable "http_ingress_cidrs" {
  description = "CIDR blocks allowed to reach the HTTP and HTTPS endpoints."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "kafka_ingress_cidrs" {
  description = "CIDR blocks allowed to reach the Kafka broker on port 9092. The broker has no authentication."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "events_per_second" {
  description = "Events generated per second by each mock source."
  type        = number
  default     = 2
}

variable "incident_interval_seconds" {
  description = "Seconds between failures (ERROR or FATAL events) on every mock source. With a value above 0 the regular traffic holds no failures, so alerts appear at this steady pace. 0 mixes failures into the traffic at random."
  type        = number
  default     = 0
}

variable "webhook_url" {
  description = "URL that receives the pushed webhook events. Empty disables the webhook source."
  type        = string
  default     = ""
}
