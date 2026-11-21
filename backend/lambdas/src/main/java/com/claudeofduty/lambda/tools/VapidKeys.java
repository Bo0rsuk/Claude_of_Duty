package com.claudeofduty.lambda.tools;

import com.claudeofduty.aws.AwsClients;
import com.claudeofduty.aws.SignedHttp;
import com.claudeofduty.aws.SsmSecret;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.push.Vapid;
import java.net.URI;
import java.util.Map;

/**
 * Creates the server's key pair for push notifications and stores it in SSM
 * Parameter Store. Run once per environment. Running it again changes nothing
 * and prints the existing public key, because replacing the keys would
 * silently invalidate every device that already subscribed.
 *
 * <pre>java -cp backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.VapidKeys [prefix]</pre>
 */
public final class VapidKeys {

    private static final Map<String, String> HEADERS = Map.of(
            "content-type", "application/x-amz-json-1.1",
            "x-amz-target", "AmazonSSM.PutParameter");

    private VapidKeys() {
    }

    public static void main(String[] args) {
        String prefix = args.length > 0 ? args[0] : "/cod";
        String publicName = prefix + "/vapid-public-key";
        String privateName = prefix + "/vapid-private-key";
        SignedHttp http = AwsClients.signedHttp();

        try {
            String existing = new SsmSecret(http, AwsClients.region(), publicName).get();
            System.out.println("Keys already exist. Public key: " + existing);
            return;
        } catch (IllegalStateException notCreatedYet) {
            // Expected on the first run.
        }
        Vapid.Keys keys = Vapid.Keys.generate();
        put(http, privateName, keys.privateKey(), "SecureString");
        put(http, publicName, keys.publicKey(), "String");
        System.out.println("Stored " + privateName + " and " + publicName + ". Public key: " + keys.publicKey());
    }

    private static void put(SignedHttp http, String name, String value, String type) {
        SignedHttp.Response response = http.post(
                URI.create("https://ssm." + AwsClients.region().id() + ".amazonaws.com/"), "ssm", HEADERS,
                Json.write(Map.of("Name", name, "Value", value, "Type", type, "Overwrite", false)));
        if (!response.successful()) {
            throw new IllegalStateException("Could not store " + name + ": HTTP " + response.status() + " " + response.body());
        }
    }
}
