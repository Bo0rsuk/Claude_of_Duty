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
#
# The handlers run on every subscription, after AppSync has verified the
# Cognito token. They admit a subscriber only to the channels of the
# organization named in the token (custom:orgId), and for notifications only
# to the subscriber's own channel. A wildcard such as /alerts/* never matches.
# Publishing is not affected: only the Lambdas can publish, through IAM.
locals {
  channel_handlers = {
    alerts = <<-EOT
      import { util } from '@aws-appsync/utils'

      export function onSubscribe(ctx) {
        const claims = (ctx.identity && ctx.identity.claims) || {}
        const orgId = claims['custom:orgId']
        const segments = ctx.info.channel.segments
        if (!orgId || segments.length !== 2 || segments[1] !== orgId) {
          util.unauthorized()
        }
      }
    EOT

    notifications = <<-EOT
      import { util } from '@aws-appsync/utils'

      export function onSubscribe(ctx) {
        const claims = (ctx.identity && ctx.identity.claims) || {}
        const orgId = claims['custom:orgId']
        const segments = ctx.info.channel.segments
        if (!orgId || segments.length !== 3 || segments[1] !== orgId || segments[2] !== ctx.identity.sub) {
          util.unauthorized()
        }
      }
    EOT
  }
}

resource "aws_appsync_channel_namespace" "this" {
  for_each = local.channel_handlers

  api_id        = aws_appsync_api.events.api_id
  name          = each.key
  code_handlers = var.events_channel_isolation ? each.value : null
}
