output "api_url" {
  description = "Base URL of the REST API."
  value       = aws_apigatewayv2_api.this.api_endpoint
}

output "ingest_url" {
  description = "Webhook URL pattern. Fill in the organization, the source and its ingest token."
  value       = "${aws_apigatewayv2_api.this.api_endpoint}/ingest/{orgId}/{sourceId}?token={ingestToken}"
}

output "user_pool_id" {
  description = "Cognito user pool."
  value       = aws_cognito_user_pool.this.id
}

output "user_pool_client_id" {
  description = "Cognito client of the web application."
  value       = aws_cognito_user_pool_client.web.id
}

output "events_http_endpoint" {
  description = "AppSync Events endpoint the Lambdas publish to."
  value       = local.events_http_endpoint
}

output "events_realtime_endpoint" {
  description = "AppSync Events WebSocket endpoint the browser subscribes to."
  value       = local.events_realtime_endpoint
}

output "stream_name" {
  description = "Kinesis stream that connectors write raw records to."
  value       = local.stream_name
}

output "frontend_env" {
  description = "Paste into frontend/.env."
  value       = <<-EOT
    VITE_AWS_REGION=${var.region}
    VITE_API_URL=${aws_apigatewayv2_api.this.api_endpoint}
    VITE_USER_POOL_ID=${aws_cognito_user_pool.this.id}
    VITE_USER_POOL_CLIENT_ID=${aws_cognito_user_pool_client.web.id}
    VITE_EVENTS_HTTP_ENDPOINT=${local.events_http_endpoint}
    VITE_EVENTS_REALTIME_ENDPOINT=${local.events_realtime_endpoint}
  EOT
}
