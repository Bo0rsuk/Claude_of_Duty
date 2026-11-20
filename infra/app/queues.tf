# One queue per notification channel, each with a dead-letter queue. A job
# that fails three times moves to the dead-letter queue.

locals {
  channels = ["sms", "voice", "email", "ui"]
}

resource "aws_sqs_queue" "notifications_dlq" {
  for_each = toset(local.channels)

  name                      = "${var.name}-notifications-${each.key}-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "notifications" {
  for_each = toset(local.channels)

  name = "${var.name}-notifications-${each.key}"

  # Must exceed the notifier timeout, or a slow delivery would be picked up twice.
  visibility_timeout_seconds = 6 * local.functions.notifier.timeout

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notifications_dlq[each.key].arn
    maxReceiveCount     = 3
  })
}

resource "aws_lambda_event_source_mapping" "notifier" {
  for_each = aws_sqs_queue.notifications

  event_source_arn = each.value.arn
  function_name    = aws_lambda_function.this["notifier"].arn
  batch_size       = 10

  # Lets the notifier report which jobs of a batch failed, so only those are retried.
  function_response_types = ["ReportBatchItemFailures"]

  depends_on = [aws_iam_role_policy.lambda]
}
