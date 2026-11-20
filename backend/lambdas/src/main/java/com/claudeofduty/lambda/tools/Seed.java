package com.claudeofduty.lambda.tools;

import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.lambda.aws.AwsClients;
import com.claudeofduty.lambda.aws.ItemCodec;
import com.claudeofduty.lambda.aws.Tables;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Loads demo data into DynamoDB, standing in for the UI until the API exists.
 * Reads sources.json and rules.json, each an array of objects in the contract shape.
 *
 * <pre>java -cp backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.Seed demo/seed</pre>
 */
public final class Seed {

    private static final Map<String, Class<?>> FILES = Map.of("sources", Source.class, "rules", Rule.class);

    private Seed() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: Seed <directory with sources.json and rules.json>");
            System.exit(2);
        }
        try (DynamoDbClient dynamoDb = AwsClients.dynamoDb()) {
            for (Map.Entry<String, Class<?>> file : FILES.entrySet()) {
                String table = Tables.name(file.getKey());
                for (JsonNode element : Json.tree(Files.readString(Path.of(args[0], file.getKey() + ".json")))) {
                    // Mapping onto the model first rejects files that do not follow the contract.
                    Object item = Json.read(element.toString(), file.getValue());
                    dynamoDb.putItem(request -> request.tableName(table).item(ItemCodec.toItem(item)));
                    System.out.println("Put into " + table + ": " + element.path("name").asText());
                }
            }
        }
    }
}
