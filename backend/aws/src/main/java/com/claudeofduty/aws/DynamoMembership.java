package com.claudeofduty.aws;

import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.Organization;
import com.claudeofduty.core.port.Membership;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/** Organizations and members in DynamoDB, with indexes for the lookups by user and by invite code. */
public final class DynamoMembership implements Membership {

    private static final String USER_INDEX = "userId-index";
    private static final String INVITE_CODE_INDEX = "inviteCode-index";

    private final DynamoDbClient dynamoDb;

    public DynamoMembership(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public Optional<Member> findMember(String userId) {
        return queryOne(Tables.MEMBERS, USER_INDEX, "userId", userId, Member.class);
    }

    @Override
    public List<Member> members(String orgId) {
        return dynamoDb.queryPaginator(request -> request
                        .tableName(Tables.MEMBERS)
                        .keyConditionExpression("orgId = :orgId")
                        .expressionAttributeValues(Map.of(":orgId", AttributeValue.fromS(orgId))))
                .items()
                .stream()
                .map(item -> ItemCodec.fromItem(item, Member.class))
                .toList();
    }

    @Override
    public void saveMember(Member member) {
        dynamoDb.putItem(request -> request.tableName(Tables.MEMBERS).item(ItemCodec.toItem(member)));
    }

    @Override
    public void removeMember(String orgId, String userId) {
        dynamoDb.deleteItem(request -> request.tableName(Tables.MEMBERS).key(Map.of(
                "orgId", AttributeValue.fromS(orgId), "userId", AttributeValue.fromS(userId))));
    }

    @Override
    public Optional<Organization> findOrganization(String orgId) {
        GetItemResponse response = dynamoDb.getItem(request -> request
                .tableName(Tables.ORGS)
                .key(Map.of("orgId", AttributeValue.fromS(orgId))));
        return response.hasItem()
                ? Optional.of(ItemCodec.fromItem(response.item(), Organization.class))
                : Optional.empty();
    }

    @Override
    public Optional<Organization> findOrganizationByInviteCode(String inviteCode) {
        return queryOne(Tables.ORGS, INVITE_CODE_INDEX, "inviteCode", inviteCode, Organization.class);
    }

    @Override
    public void saveOrganization(Organization organization) {
        dynamoDb.putItem(request -> request.tableName(Tables.ORGS).item(ItemCodec.toItem(organization)));
    }

    private <T> Optional<T> queryOne(String table, String index, String attribute, String value, Class<T> type) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return dynamoDb.query(request -> request
                        .tableName(table)
                        .indexName(index)
                        .keyConditionExpression("#key = :value")
                        .expressionAttributeNames(Map.of("#key", attribute))
                        .expressionAttributeValues(Map.of(":value", AttributeValue.fromS(value)))
                        .limit(1))
                .items()
                .stream()
                .findFirst()
                .map(item -> ItemCodec.fromItem(item, type));
    }
}
