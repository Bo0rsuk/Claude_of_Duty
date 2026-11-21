package com.claudeofduty.core.service;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.port.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Create, read, replace and delete for one kind of configuration object:
 * sources, rules, recipient groups and runbooks all go through this class.
 *
 * The server owns the id, the organization and the timestamps, so whatever the
 * client sends for those fields is overwritten.
 */
public final class ConfigService<T> {

    private final Repository<T> repository;
    private final Class<T> type;
    private final String idField;
    private final EntityPolicy<T> policy;
    private final Clock clock;

    public ConfigService(Repository<T> repository, Class<T> type, String idField, EntityPolicy<T> policy, Clock clock) {
        this.repository = repository;
        this.type = type;
        this.idField = idField;
        this.policy = policy;
        this.clock = clock;
    }

    public List<T> list(Caller caller) {
        return repository.list(caller.orgId());
    }

    public T get(Caller caller, String id) {
        return repository.find(caller.orgId(), id).orElseThrow(() -> notFound(id));
    }

    public T create(Caller caller, JsonNode body) {
        return save(caller, UUID.randomUUID().toString(), body, null);
    }

    public T replace(Caller caller, String id, JsonNode body) {
        return save(caller, id, body, get(caller, id));
    }

    public void delete(Caller caller, String id) {
        get(caller, id);
        repository.delete(caller.orgId(), id);
        policy.afterDelete(caller.orgId(), id);
    }

    private T save(Caller caller, String id, JsonNode body, T existing) {
        if (body == null || !body.isObject()) {
            throw ApiException.invalid("The request body must be a JSON object");
        }
        String now = Json.timestamp(clock.instant());
        ObjectNode object = body.deepCopy();
        object.put(idField, id);
        object.put("orgId", caller.orgId());
        object.put("createdAt", existing == null ? now : Json.convert(existing, JsonNode.class).path("createdAt").asText(now));
        object.put("updatedAt", now);
        object.put("updatedBy", caller.userId());
        policy.applyDefaults(object, existing);

        T item = convert(object);
        policy.validate(item);
        repository.save(item);
        return item;
    }

    private T convert(ObjectNode object) {
        try {
            return Json.convert(object, type);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("The body does not have the shape of a " + type.getSimpleName() + ": "
                    + firstLine(e.getMessage()));
        }
    }

    private ApiException notFound(String id) {
        return ApiException.notFound(type.getSimpleName() + " " + id + " does not exist");
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.lines().findFirst().orElse("");
    }
}
