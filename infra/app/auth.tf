# Users sign up and log in with their email address. Organizations, invite
# codes and roles live in DynamoDB, not in Cognito.
resource "aws_cognito_user_pool" "this" {
  name = var.name

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  # The user's organization, written by the API when the user creates or
  # joins one. It is part of the ID token, and the real-time channels only
  # admit a subscriber to the channels of that organization.
  schema {
    name                = "orgId"
    attribute_data_type = "String"
    mutable             = true

    string_attribute_constraints {
      min_length = 1
      max_length = 64
    }
  }

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = false
  }
}

# Public client for the browser application: no secret, SRP login.
# Password login is also allowed so that scripts (curl, the load test) can get
# a token with one call instead of implementing SRP.
resource "aws_cognito_user_pool_client" "web" {
  name         = "${var.name}-web"
  user_pool_id = aws_cognito_user_pool.this.id

  generate_secret     = false
  explicit_auth_flows = ["ALLOW_USER_SRP_AUTH", "ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]

  # custom:orgId is readable, so it lands in the ID token, but not writable:
  # a user must not be able to put themselves into another organization.
  read_attributes  = ["email", "email_verified", "name", "phone_number", "custom:orgId"]
  write_attributes = ["email", "name", "phone_number"]
}
