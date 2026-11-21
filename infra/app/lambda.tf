# Four functions running from the one jar built in backend/. They differ only
# in the handler class and in what their role may access.

locals {
  functions = {
    ingest    = { handler = "com.claudeofduty.lambda.IngestHandler", timeout = 10 }
    processor = { handler = "com.claudeofduty.lambda.ProcessorHandler", timeout = 60 }
    notifier  = { handler = "com.claudeofduty.lambda.NotifierHandler", timeout = 15 }
    # The API waits for the rule assistant's model, so it gets close to API Gateway's 30 s limit.
    api = { handler = "com.claudeofduty.lambda.ApiHandler", timeout = 29 }
  }

  # The API reports two host names, one for HTTP and one for WebSocket.
  events_hosts             = values(aws_appsync_api.events.dns)
  events_http_endpoint     = "https://${one([for host in local.events_hosts : host if !strcontains(host, "realtime")])}/event"
  events_realtime_endpoint = "wss://${one([for host in local.events_hosts : host if strcontains(host, "realtime")])}/event/realtime"

  lambda_environment = {
    TABLE_PREFIX                = "${var.name}-"
    STREAM_NAME                 = local.stream_name
    EVENTS_HTTP_ENDPOINT        = local.events_http_endpoint
    NOTIFICATION_QUEUE_URLS     = jsonencode({ for channel, queue in aws_sqs_queue.notifications : channel => queue.url })
    APP_URL                     = local.app_url
    SENDER_EMAIL                = var.notification_sender_email
    USER_POOL_ID                = aws_cognito_user_pool.this.id
    VAPID_PUBLIC_KEY_PARAMETER  = "${var.push_key_parameter_prefix}/vapid-public-key"
    VAPID_PRIVATE_KEY_PARAMETER = "${var.push_key_parameter_prefix}/vapid-private-key"
    VAPID_SUBJECT               = var.push_contact
    GEMINI_MODEL                = var.gemini_model
    GEMINI_API_KEY_PARAMETER    = var.gemini_api_key_parameter
    # Stops the JIT at the first tier, which makes cold starts noticeably shorter.
    # The processor stores alerts from 16 threads, so it keeps more idle
    # connections per host than the JDK's default of 5.
    JAVA_TOOL_OPTIONS = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Dhttp.maxConnections=32"
  }
}

resource "aws_cloudwatch_log_group" "lambda" {
  for_each = local.functions

  name              = "/aws/lambda/${var.name}-${each.key}"
  retention_in_days = var.log_retention_days
}

resource "aws_lambda_function" "this" {
  for_each = local.functions

  function_name = "${var.name}-${each.key}"
  role          = aws_iam_role.lambda[each.key].arn
  runtime       = "java21"
  handler       = "${each.value.handler}::handleRequest"
  memory_size   = var.lambda_memory_mb
  timeout       = each.value.timeout

  filename         = var.lambda_jar
  source_code_hash = filebase64sha256(var.lambda_jar)

  environment {
    variables = local.lambda_environment
  }

  # SnapStart: Lambda initializes the function once when a version is published
  # and starts new environments from that snapshot, instead of booting the JVM
  # and loading classes on every cold start.
  publish = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  depends_on = [aws_cloudwatch_log_group.lambda]
}

# SnapStart applies to published versions only, so every trigger invokes the
# function through this alias, which always points at the newest version.
resource "aws_lambda_alias" "live" {
  for_each = local.functions

  name             = "live"
  function_name    = aws_lambda_function.this[each.key].function_name
  function_version = aws_lambda_function.this[each.key].version
}
