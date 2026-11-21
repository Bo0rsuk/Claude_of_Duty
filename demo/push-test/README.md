# Push notification test page

A single page for checking push notifications on a real phone or browser before
the web application exists. It signs in, registers the device and logs the result.

Deploy it next to the frontend (run from the repository root):

```bash
terraform -chdir=infra/app output -json | python3 -c "
import json, sys
o = {k: v['value'] for k, v in json.load(sys.stdin).items()}
print(json.dumps({'apiUrl': o['api_url'], 'region': o['api_url'].split('.')[2], 'userPoolClientId': o['user_pool_client_id']}))
" > demo/push-test/config.json

BUCKET=$(terraform -chdir=infra/app output -raw frontend_deploy_command | sed -E 's|.*s3://([^ ]+).*|\1|')
aws s3 sync demo/push-test "s3://$BUCKET/push-test" --exclude README.md --cache-control no-cache
echo "$(terraform -chdir=infra/app output -raw frontend_url)/push-test/index.html"
```

Open the printed address, sign in and enable push. Then give a rule the route
`{ "channel": "push", "target": { "type": "user", "id": "<your user id>" } }`
(the page logs your user id) and trigger an alert of that rule.

On iPhone and iPad, push works only after the page was added to the home
screen and opened from there.
