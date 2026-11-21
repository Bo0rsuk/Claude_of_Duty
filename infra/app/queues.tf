# One queue per notification channel, each with a dead-letter queue.
#
# A job that fails three times moves to the dead-letter queue. The notifier
# also consumes the dead-letter queues: it sends such a job back for another
# round or, after the last round, records the final failure in the alert timeline.

locals {
  channels = ["sms", "voice", "email", "ui", "push"]
}

resource "aws_sqs_queue" "notifications_dlq" {
  for_each = toset(local.channels)

  name                      = "${var.name}-notifications-${each.key}-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "notifications" {
  for_each = toset(local.channels)

  name = "${var.name}-notifications-${each.key}"

  # Also the pause before a failed job is retried. It must exceed the notifier
  # timeout, or a slow delivery would be picked up twice.
  visibility_timeout_seconds = 2 * local.functions.notifier.timeout

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notifications_dlq[each.key].arn
    maxReceiveCount     = 3
  })
}

resource "aws_lambda_event_source_mapping" "notifier" {
  for_each = aws_sqs_queue.notifications

  event_source_arn = each.value.arn
  function_name    = aws_lambda_alias.live["notifier"].arn
  batch_size       = 10

  # Keeps a busy channel from taking Lambda concurrency away from ingest and processing.
  scaling_config {
    maximum_concurrency = var.notifier_max_concurrency
  }

  # Lets the notifier report which jobs of a batch failed, so only those are retried.
  function_response_types = ["ReportBatchItemFailures"]

  depends_on = [aws_iam_role_policy.lambda]
}

resource "aws_lambda_event_source_mapping" "notifier_dead_letters" {
  for_each = aws_sqs_queue.notifications_dlq

  event_source_arn        = each.value.arn
  function_name           = aws_lambda_alias.live["notifier"].arn
  batch_size              = 10
  function_response_types = ["ReportBatchItemFailures"]

  scaling_config {
    maximum_concurrency = var.notifier_max_concurrency
  }

  depends_on = [aws_iam_role_policy.lambda]
}
