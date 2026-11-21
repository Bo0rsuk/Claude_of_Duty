package com.claudeofduty.lambda.tools;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.Organization;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Runbook;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.aws.AwsClients;
import com.claudeofduty.aws.ItemCodec;
import com.claudeofduty.aws.Tables;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Loads demo data into DynamoDB, standing in for the UI until the API exists.
 * Reads orgs.json, sources.json, rules.json, runbooks.json, members.json and groups.json from a
 * directory. Each is an array of objects in the contract shape; a missing file is skipped.
 * Seeded members are people to show in lists and route to. They have no account and cannot sign in.
 *
 * <pre>java -cp backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.Seed demo/seed</pre>
 */
public final class Seed {

    /** File name (without .json) and table suffix, mapped to the type its entries must have. */
    private static final Map<String, Class<?>> FILES = Map.of(
            "orgs", Organization.class, "sources", Source.class, "rules", Rule.class, "runbooks", Runbook.class,
            "members", Member.class, "groups", RecipientGroup.class);

    private Seed() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: Seed <directory with orgs.json, sources.json, rules.json, runbooks.json, members.json, groups.json>");
            System.exit(2);
        }
        try (DynamoDbClient dynamoDb = AwsClients.dynamoDb()) {
            for (Map.Entry<String, Class<?>> file : FILES.entrySet()) {
                String table = Tables.name(file.getKey());
                Path path = Path.of(args[0], file.getKey() + ".json");
                if (!Files.exists(path)) {
                    continue;
                }
                for (JsonNode element : Json.tree(Files.readString(path))) {
                    // Mapping onto the model first rejects files that do not follow the contract.
                    Object item = Json.read(element.toString(), file.getValue());
                    dynamoDb.putItem(request -> request.tableName(table).item(ItemCodec.toItem(item)));
                    System.out.println("Put into " + table + ": "
                            + element.path("name").asText(element.path("title").asText()));
                }
            }
        }
    }
}
