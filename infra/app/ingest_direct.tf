# Direct ingest: API Gateway writes webhook payloads straight to the stream,
# with no Lambda in between. Ingest then takes none of the account's Lambda
# concurrency, and its capacity is that of API Gateway and Kinesis.
#
# This needs a REST API (not the HTTP API used for the rest), because only REST
# APIs can reshape a request with a mapping template. The template wraps the
# payload in an envelope with the source, the sender's token and the time of
# arrival. The processor checks the token when it reads the record, since
# nothing in front of the stream can.
#
# The Lambda ingest route on the HTTP API stays as an alternative: it answers
# 404 for a wrong token at once, which this route cannot do.

locals {
  # Velocity template. "$$" is Terraform's escape for a literal "$" before "{".
  ingest_envelope_template = <<-EOT
    #set($q = '"')
    #set($token = $input.params('token'))
    #if($token == "")#set($token = $input.params('X-Ingest-Token'))#end
    #set($payload = $util.escapeJavaScript($input.body).replaceAll("\\'", "'"))
    #set($envelope = "{$${q}recordId$${q}:$${q}$context.requestId$${q},$${q}orgId$${q}:$${q}$util.escapeJavaScript($input.params('orgId'))$${q},$${q}sourceId$${q}:$${q}$util.escapeJavaScript($input.params('sourceId'))$${q},$${q}token$${q}:$${q}$util.escapeJavaScript($token)$${q},$${q}receivedAtMillis$${q}:$context.requestTimeEpoch,$${q}payload$${q}:$${q}$payload$${q}}")
    {
      "StreamName": "${local.stream_name}",
      "PartitionKey": "$context.requestId",
      "Data": "$util.base64Encode($envelope)"
    }
  EOT

  # Senders use different content types; each needs the template, or API Gateway would pass the body through unwrapped.
  ingest_content_types = ["application/json", "text/plain", "application/octet-stream", "application/x-www-form-urlencoded"]
}

resource "aws_api_gateway_rest_api" "ingest" {
  name        = "${var.name}-ingest"
  description = "Webhook ingest written directly to Kinesis"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_resource" "ingest" {
  rest_api_id = aws_api_gateway_rest_api.ingest.id
  parent_id   = aws_api_gateway_rest_api.ingest.root_resource_id
  path_part   = "ingest"
}

resource "aws_api_gateway_resource" "ingest_org" {
  rest_api_id = aws_api_gateway_rest_api.ingest.id
  parent_id   = aws_api_gateway_resource.ingest.id
  path_part   = "{orgId}"
}

resource "aws_api_gateway_resource" "ingest_source" {
  rest_api_id = aws_api_gateway_rest_api.ingest.id
  parent_id   = aws_api_gateway_resource.ingest_org.id
  path_part   = "{sourceId}"
}

resource "aws_api_gateway_method" "ingest" {
  rest_api_id   = aws_api_gateway_rest_api.ingest.id
  resource_id   = aws_api_gateway_resource.ingest_source.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "ingest" {
  rest_api_id             = aws_api_gateway_rest_api.ingest.id
  resource_id             = aws_api_gateway_resource.ingest_source.id
  http_method             = aws_api_gateway_method.ingest.http_method
  type                    = "AWS"
  integration_http_method = "POST"
  uri                     = "arn:aws:apigateway:${var.region}:kinesis:action/PutRecord"
  credentials             = aws_iam_role.ingest_direct.arn
  passthrough_behavior    = "NEVER"

  request_parameters = {
    "integration.request.header.Content-Type" = "'application/x-amz-json-1.1'"
  }

  request_templates = { for content_type in local.ingest_content_types : content_type => local.ingest_envelope_template }
}

resource "aws_api_gateway_method_response" "accepted" {
  rest_api_id = aws_api_gateway_rest_api.ingest.id
  resource_id = aws_api_gateway_resource.ingest_source.id
  http_method = aws_api_gateway_method.ingest.http_method
  status_code = "202"
}

resource "aws_api_gateway_method_response" "unavailable" {
  rest_api_id = aws_api_gateway_rest_api.ingest.id
  resource_id = aws_api_gateway_resource.ingest_source.id
  http_method = aws_api_gateway_method.ingest.http_method
  status_code = "503"
}

# Kinesis accepted the record.
resource "aws_api_gateway_integration_response" "accepted" {
  rest_api_id = aws_api_gateway_rest_api.ingest.id
  resource_id = aws_api_gateway_resource.ingest_source.id
  http_method = aws_api_gateway_method.ingest.http_method
  status_code = aws_api_gateway_method_response.accepted.status_code

  response_templates = {
    "application/json" = "{\"recordId\": \"$context.requestId\"}"
  }

  depends_on = [aws_api_gateway_integration.ingest]
}

# Kinesis refused it, e.g. it is throttling or the stream is paused.
resource "aws_api_gateway_integration_response" "unavailable" {
  rest_api_id       = aws_api_gateway_rest_api.ingest.id
  resource_id       = aws_api_gateway_resource.ingest_source.id
  http_method       = aws_api_gateway_method.ingest.http_method
  status_code       = aws_api_gateway_method_response.unavailable.status_code
  selection_pattern = "[45]\\d{2}"

  response_templates = {
    "application/json" = "{\"error\": {\"code\": \"UNAVAILABLE\", \"message\": \"The event could not be accepted, try again\"}}"
  }

  depends_on = [aws_api_gateway_integration.ingest]
}

resource "aws_api_gateway_deployment" "ingest" {
  rest_api_id = aws_api_gateway_rest_api.ingest.id

  # A REST API serves a snapshot, so a change to anything below needs a new deployment.
  triggers = {
    configuration = sha1(jsonencode([
      aws_api_gateway_integration.ingest.request_templates,
      aws_api_gateway_integration.ingest.uri,
      aws_api_gateway_integration_response.accepted.response_templates,
      aws_api_gateway_integration_response.unavailable.selection_pattern,
      aws_api_gateway_method.ingest.id,
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "ingest" {
  rest_api_id   = aws_api_gateway_rest_api.ingest.id
  deployment_id = aws_api_gateway_deployment.ingest.id
  stage_name    = "v1"
}

resource "aws_iam_role" "ingest_direct" {
  name = "${var.name}-ingest-direct"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "apigateway.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "ingest_direct" {
  name = "put-records"
  role = aws_iam_role.ingest_direct.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["kinesis:PutRecord"]
      Resource = ["arn:aws:kinesis:${var.region}:*:stream/${local.stream_name}"]
    }]
  })
}
