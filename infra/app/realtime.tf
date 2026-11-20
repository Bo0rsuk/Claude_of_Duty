# AppSync Events: WebSocket pub/sub for the live feed. Browsers connect and
# subscribe with their Cognito token, Lambdas publish over HTTP with IAM.
resource "aws_appsync_api" "events" {
  name = "${var.name}-events"

  event_config {
    auth_provider {
      auth_type = "AMAZON_COGNITO_USER_POOLS"

      cognito_config {
        aws_region   = var.region
        user_pool_id = aws_cognito_user_pool.this.id
      }
    }

    auth_provider {
      auth_type = "AWS_IAM"
    }

    connection_auth_mode {
      auth_type = "AMAZON_COGNITO_USER_POOLS"
    }

    default_subscribe_auth_mode {
      auth_type = "AMAZON_COGNITO_USER_POOLS"
    }

    default_publish_auth_mode {
      auth_type = "AWS_IAM"
    }
  }
}

# Channels from Docs/contracts/api.md: /alerts/{orgId} and /notifications/{orgId}/{userId}.
resource "aws_appsync_channel_namespace" "this" {
  for_each = toset(["alerts", "notifications"])

  api_id = aws_appsync_api.events.api_id
  name   = each.key
}
