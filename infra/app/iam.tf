# One role per function, each allowed only what that function does.

locals {
  table_arns = [for table in aws_dynamodb_table.this : table.arn]
  # Tables and their indexes.
  table_resources = concat(local.table_arns, [for arn in local.table_arns : "${arn}/index/*"])

  dynamodb_read  = ["dynamodb:GetItem", "dynamodb:BatchGetItem", "dynamodb:Query", "dynamodb:Scan"]
  dynamodb_write = ["dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:BatchWriteItem"]

  publish_events = {
    Effect   = "Allow"
    Action   = ["appsync:EventPublish"]
    Resource = ["${aws_appsync_api.events.api_arn}/channelNamespace/*"]
  }

  read_write_tables = {
    Effect   = "Allow"
    Action   = concat(local.dynamodb_read, local.dynamodb_write)
    Resource = local.table_resources
  }

  enqueue_notifications = {
    Effect   = "Allow"
    Action   = ["sqs:SendMessage"]
    Resource = [for queue in aws_sqs_queue.notifications : queue.arn]
  }

  lambda_statements = {
    ingest = [
      {
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem"]
        Resource = [aws_dynamodb_table.this["sources"].arn]
      },
      {
        Effect = "Allow"
        Action = ["kinesis:PutRecord", "kinesis:PutRecords"]
        # Addressed by name so the permission survives pausing the stream.
        Resource = ["arn:aws:kinesis:${var.region}:*:stream/${local.stream_name}"]
      },
    ]

    processor = [
      local.read_write_tables,
      local.publish_events,
      local.enqueue_notifications,
    ]

    notifier = [
      {
        Effect = "Allow"
        Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:SendMessage"]
        Resource = concat(
          [for queue in aws_sqs_queue.notifications : queue.arn],
          [for queue in aws_sqs_queue.notifications_dlq : queue.arn],
        )
      },
      local.read_write_tables,
      local.publish_events,
      {
        # The key pair that push messages are signed with.
        Effect   = "Allow"
        Action   = ["ssm:GetParameter"]
        Resource = ["arn:aws:ssm:${var.region}:*:parameter${var.push_key_parameter_prefix}/vapid-*"]
      },
      {
        # The phone provider's account, read when a call is placed.
        Effect   = "Allow"
        Action   = ["ssm:GetParameter"]
        Resource = ["arn:aws:ssm:${var.region}:*:parameter${var.twilio_parameter_prefix}-*"]
      },
      {
        # SMS goes to phone numbers, which have no ARN to scope to.
        Effect   = "Allow"
        Action   = ["sns:Publish", "ses:SendEmail"]
        Resource = ["*"]
      },
    ]

    api = [
      local.read_write_tables,
      local.publish_events,
      local.enqueue_notifications,
      {
        # The rule assistant's API key and the public key browsers subscribe to push with.
        # Only the names are known to Terraform; the values are created outside it.
        Effect = "Allow"
        Action = ["ssm:GetParameter"]
        Resource = [
          "arn:aws:ssm:${var.region}:*:parameter${var.gemini_api_key_parameter}",
          "arn:aws:ssm:${var.region}:*:parameter${var.push_key_parameter_prefix}/vapid-public-key",
        ]
      },
      {
        # Records a user's organization in Cognito when they create, join or leave one,
        # and creates the account of a person an admin adds by email.
        Effect   = "Allow"
        Action   = ["cognito-idp:AdminUpdateUserAttributes", "cognito-idp:AdminCreateUser"]
        Resource = [aws_cognito_user_pool.this.arn]
      },
    ]
  }
}

resource "aws_iam_role" "lambda" {
  for_each = local.functions

  name = "${var.name}-${each.key}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  for_each = local.functions

  role       = aws_iam_role.lambda[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Reading the stream through the enhanced fan-out consumer.
resource "aws_iam_role_policy_attachment" "processor_stream" {
  role       = aws_iam_role.lambda["processor"].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaKinesisExecutionRole"
}

resource "aws_iam_role_policy" "lambda" {
  for_each = local.functions

  name = "access"
  role = aws_iam_role.lambda[each.key].id

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = local.lambda_statements[each.key]
  })
}
