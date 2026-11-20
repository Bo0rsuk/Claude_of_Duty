# Tables from Docs/contracts/data-model.md. All keys are strings.

locals {
  tables = {
    orgs           = { hash_key = "orgId", indexes = { "inviteCode-index" = "inviteCode" } }
    members        = { hash_key = "orgId", range_key = "userId", indexes = { "userId-index" = "userId" } }
    sources        = { hash_key = "orgId", range_key = "sourceId" }
    rules          = { hash_key = "orgId", range_key = "ruleId" }
    groups         = { hash_key = "orgId", range_key = "groupId" }
    runbooks       = { hash_key = "orgId", range_key = "runbookId" }
    alerts         = { hash_key = "orgId", range_key = "alertId" }
    alert-timeline = { hash_key = "alertId", range_key = "entryId" }
    dedup          = { hash_key = "fingerprint" }
    counters       = { hash_key = "counterKey", ttl_attribute = "expiresAt" }
    recent-events  = { hash_key = "sourceId", range_key = "eventKey", ttl_attribute = "expiresAt" }
  }
}

resource "aws_dynamodb_table" "this" {
  for_each = local.tables

  name         = "${var.name}-${each.key}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = each.value.hash_key
  range_key    = try(each.value.range_key, null)

  dynamic "attribute" {
    for_each = toset(compact(concat(
      [each.value.hash_key, try(each.value.range_key, null)],
      values(try(each.value.indexes, {})),
    )))
    content {
      name = attribute.value
      type = "S"
    }
  }

  dynamic "global_secondary_index" {
    for_each = try(each.value.indexes, {})
    content {
      name            = global_secondary_index.key
      hash_key        = global_secondary_index.value
      projection_type = "ALL"
    }
  }

  dynamic "ttl" {
    for_each = try([each.value.ttl_attribute], [])
    content {
      enabled        = true
      attribute_name = ttl.value
    }
  }
}
