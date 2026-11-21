package com.claudeofduty.lambda;

import com.claudeofduty.core.api.Page;
import com.claudeofduty.core.json.Json;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.Organization;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.port.AlertRepository;
import com.claudeofduty.core.port.Membership;
import com.claudeofduty.core.port.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** In-memory stand-ins for the DynamoDB adapters, used to test the API without AWS. */
final class InMemory {

    private InMemory() {
    }

    static final class Store<T> implements Repository<T> {
        private final Map<String, T> items = new LinkedHashMap<>();
        private final Function<T, String> orgOf;
        private final Function<T, String> idOf;

        Store(Function<T, String> orgOf, Function<T, String> idOf) {
            this.orgOf = orgOf;
            this.idOf = idOf;
        }

        @Override
        public List<T> list(String orgId) {
            return items.values().stream().filter(item -> orgOf.apply(item).equals(orgId)).toList();
        }

        @Override
        public Optional<T> find(String orgId, String id) {
            return Optional.ofNullable(items.get(orgId + "/" + id));
        }

        @Override
        public void save(T item) {
            items.put(orgOf.apply(item) + "/" + idOf.apply(item), item);
        }

        @Override
        public void delete(String orgId, String id) {
            items.remove(orgId + "/" + id);
        }
    }

    static final class Members implements Membership {
        private final Map<String, Member> members = new LinkedHashMap<>();
        private final Map<String, Organization> organizations = new LinkedHashMap<>();

        @Override
        public Optional<Member> findMember(String userId) {
            return Optional.ofNullable(members.get(userId));
        }

        @Override
        public List<Member> members(String orgId) {
            return members.values().stream().filter(member -> member.orgId().equals(orgId)).toList();
        }

        @Override
        public void saveMember(Member member) {
            members.put(member.userId(), member);
        }

        @Override
        public void removeMember(String orgId, String userId) {
            members.remove(userId);
        }

        @Override
        public Optional<Organization> findOrganization(String orgId) {
            return Optional.ofNullable(organizations.get(orgId));
        }

        @Override
        public Optional<Organization> findOrganizationByInviteCode(String inviteCode) {
            return organizations.values().stream()
                    .filter(organization -> inviteCode.equals(organization.inviteCode()))
                    .findFirst();
        }

        @Override
        public void saveOrganization(Organization organization) {
            organizations.put(organization.orgId(), organization);
        }
    }

    static final class Alerts implements AlertRepository {
        final Map<String, Alert> alerts = new LinkedHashMap<>();
        final List<TimelineEntry> entries = new ArrayList<>();
        final List<String> released = new ArrayList<>();

        @Override
        public Page<Alert> newest(String orgId, int limit, String cursor) {
            List<Alert> sorted = alerts.values().stream()
                    .filter(alert -> alert.orgId().equals(orgId))
                    .filter(alert -> cursor == null || alert.alertId().compareTo(cursor) < 0)
                    .sorted(Comparator.comparing(Alert::alertId).reversed())
                    .toList();
            List<Alert> page = sorted.stream().limit(limit).toList();
            return new Page<>(page, sorted.size() > limit ? page.get(page.size() - 1).alertId() : null);
        }

        @Override
        public Optional<Alert> find(String orgId, String alertId) {
            return Optional.ofNullable(alerts.get(alertId)).filter(alert -> alert.orgId().equals(orgId));
        }

        @Override
        public Optional<Alert> update(String orgId, String alertId, Map<String, Object> changes) {
            return find(orgId, alertId).map(alert -> {
                ObjectNode json = Json.convert(alert, ObjectNode.class);
                changes.forEach((field, value) -> json.set(field, Json.convert(value, JsonNode.class)));
                Alert updated = Json.convert(json, Alert.class);
                alerts.put(alertId, updated);
                return updated;
            });
        }

        @Override
        public void delete(String orgId, String alertId) {
            if (find(orgId, alertId).isPresent()) {
                alerts.remove(alertId);
                entries.removeIf(entry -> entry.alertId().equals(alertId));
            }
        }

        @Override
        public List<TimelineEntry> timeline(String alertId) {
            return entries.stream().filter(entry -> entry.alertId().equals(alertId)).toList();
        }

        @Override
        public void appendTimeline(TimelineEntry entry) {
            entries.add(entry);
        }

        @Override
        public void release(String fingerprint) {
            released.add(fingerprint);
        }
    }
}
