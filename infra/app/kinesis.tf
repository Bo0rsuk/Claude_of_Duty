# The stream every connector writes raw records to. On-demand mode scales the
# shards with the traffic, so a load spike needs no capacity planning.
#
# A stream cannot be stopped, only deleted, hence the stream_enabled switch.
# While it is off the ingest endpoint answers with an error.
locals {
  stream_name = "${var.name}-events"
}

resource "aws_kinesis_stream" "events" {
  count = var.stream_enabled ? 1 : 0

  name             = local.stream_name
  retention_period = 24

  stream_mode_details {
    stream_mode = "ON_DEMAND"
  }
}

# Enhanced fan-out pushes records to the processor in about 70 ms. Without it
# Lambda polls each shard once a second, which would dominate the latency.
resource "aws_kinesis_stream_consumer" "processor" {
  count = var.stream_enabled ? 1 : 0

  name       = "${var.name}-processor"
  stream_arn = aws_kinesis_stream.events[0].arn
}

resource "aws_lambda_event_source_mapping" "processor" {
  count = var.stream_enabled ? 1 : 0

  event_source_arn  = aws_kinesis_stream_consumer.processor[0].arn
  function_name     = aws_lambda_function.this["processor"].arn
  starting_position = "LATEST"

  batch_size                         = var.processor_batch_size
  maximum_batching_window_in_seconds = 0
  parallelization_factor             = var.processor_parallelization_factor

  # A failing batch is split in half and retried, so one bad record is
  # isolated instead of blocking its shard.
  bisect_batch_on_function_error = true
  maximum_retry_attempts         = 3

  depends_on = [aws_iam_role_policy_attachment.processor_stream]
}
