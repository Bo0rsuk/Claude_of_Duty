# Email (SES) and SMS (SNS) settings for the notifier.

locals {
  sender_on_own_domain = (
    var.notification_sender_domain != "" &&
    endswith(var.notification_sender_email, "@${var.notification_sender_domain}")
  )

  # Addresses SES has to confirm by a link: the sender, unless it lives on the
  # verified domain, and the recipients used while the account is in the sandbox.
  verified_addresses = toset(compact(concat(
    local.sender_on_own_domain ? [] : [var.notification_sender_email],
    var.notification_verified_recipients,
  )))
}

# Verifying a whole domain lets the notifier send from any address on it, and
# signs the mail with DKIM so that receivers such as Gmail accept it. The
# records to add at the DNS provider are in the ses_dkim_records output.
resource "aws_sesv2_email_identity" "sender_domain" {
  count = var.notification_sender_domain == "" ? 0 : 1

  email_identity = var.notification_sender_domain
}

resource "aws_sesv2_email_identity" "address" {
  for_each = local.verified_addresses

  email_identity = each.value
}

# SNS reports what happened to each SMS after accepting it. Without this the
# only signal is the message id, which says nothing about delivery.
resource "aws_sns_sms_preferences" "this" {
  default_sms_type                      = "Transactional"
  delivery_status_iam_role_arn          = aws_iam_role.sms_delivery_logs.arn
  delivery_status_success_sampling_rate = 100
}

resource "aws_iam_role" "sms_delivery_logs" {
  name = "${var.name}-sms-delivery-logs"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "sns.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "sms_delivery_logs" {
  name = "write-logs"
  role = aws_iam_role.sms_delivery_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:PutMetricFilter",
        "logs:PutRetentionPolicy",
      ]
      Resource = "*"
    }]
  })
}
