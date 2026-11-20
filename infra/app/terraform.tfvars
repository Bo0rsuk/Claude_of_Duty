# Settings for the application infrastructure. Loaded automatically by
# terraform plan/apply. No secrets belong in this file: it is committed.

region = "eu-central-1"
name   = "cod"

# Public URL of the web application, used for the links in SMS and email.
# Change it once the frontend is hosted.
app_url = "http://localhost:5173"

# Origins allowed to call the API from a browser. Narrow it down to app_url
# before the demo.
cors_allowed_origins = ["*"]

# The Kinesis stream is the only resource billed by the hour (about 1 USD a
# day). Set to false and apply to delete it while nobody is working; set back
# to true and apply to bring it back. Tables, users and settings are kept.
stream_enabled = true

lambda_memory_mb                 = 1024
processor_batch_size             = 100
processor_parallelization_factor = 2
