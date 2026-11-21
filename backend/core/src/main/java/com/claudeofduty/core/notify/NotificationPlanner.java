package com.claudeofduty.core.notify;

import com.claudeofduty.core.alerting.AlertVariables;
import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.NotificationJob.Recipient;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Rule.ChannelTemplate;
import com.claudeofduty.core.model.Rule.Limit;
import com.claudeofduty.core.model.Rule.Route;
import com.claudeofduty.core.model.Rule.Target;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.notify.ChannelDirectory.AddressType;
import com.claudeofduty.core.port.Membership;
import com.claudeofduty.core.port.Repository;
import com.claudeofduty.core.port.Throttle;
import com.claudeofduty.core.template.Templates;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a rule's routing into concrete notification jobs for a new alert:
 * expands groups, picks the address each channel needs, renders the text and
 * applies the rule's throttling.
 */
public final class NotificationPlanner {

    public record Suppressed(String channel, String recipient, String reason) {
    }

    public record Plan(List<NotificationJob> jobs, List<Suppressed> suppressed) {
    }

    /** Someone who can be notified, whether an organization member or an outside contact. */
    private record Contact(String userId, String name, String email, String phone) {

        static Contact of(Member member) {
            return new Contact(member.userId(), member.name(), member.email(), member.phone());
        }

        String address(AddressType type) {
            return switch (type) {
                case PHONE -> phone;
                case EMAIL -> email;
                case USER -> userId;
            };
        }
    }

    private final ChannelDirectory channels;
    private final Repository<RecipientGroup> groups;
    private final Membership membership;
    private final Throttle throttle;
    private final String appUrl;
    private final Clock clock;

    public NotificationPlanner(ChannelDirectory channels, Repository<RecipientGroup> groups, Membership membership,
            Throttle throttle, String appUrl, Clock clock) {
        this.channels = channels;
        this.groups = groups;
        this.membership = membership;
        this.throttle = throttle;
        this.appUrl = appUrl;
        this.clock = clock;
    }

    public Plan plan(Alert alert, Rule rule, Source source) {
        return plan(alert, rule, source, true);
    }

    /** @param throttled false to ignore the rule's limits, for a resend that a person asked for */
    public Plan plan(Alert alert, Rule rule, Source source, boolean throttled) {
        List<NotificationJob> jobs = new ArrayList<>();
        List<Suppressed> suppressed = new ArrayList<>();
        Instant now = clock.instant();
        Map<String, Object> variables =
                AlertVariables.of(alert.alertId(), rule, source, alert.sampleEvent(), alert.count(), appUrl);

        recipients(rule).forEach((key, entry) -> {
            String channel = entry.channel();
            Recipient recipient = entry.recipient();
            String refusedBy = throttled ? refusedBy(rule, channel, recipient.address(), now) : null;
            if (refusedBy != null) {
                suppressed.add(new Suppressed(channel, recipient.address(), refusedBy));
                return;
            }
            AddressType type = channels.addressType(channel).orElseThrow();
            jobs.add(new NotificationJob(UUID.randomUUID().toString(), alert.orgId(), alert.alertId(), channel,
                    alert.severity(), recipient, subject(alert, rule, channel, type, variables),
                    body(alert, rule, channel, type, variables), 1, alert.timing().receivedAt()));
        });
        return new Plan(jobs, suppressed);
    }

    private record Addressed(String channel, Recipient recipient) {
    }

    /** One entry per channel and address, so someone reached through two groups is notified once. */
    private Map<String, Addressed> recipients(Rule rule) {
        Map<String, Addressed> recipients = new LinkedHashMap<>();
        List<Route> routing = rule.routing() == null ? List.of() : rule.routing();
        Map<String, Member> members = null;
        for (Route route : routing) {
            AddressType type = channels.addressType(route.channel()).orElse(null);
            if (type == null || route.target() == null) {
                continue;
            }
            if (members == null && !"address".equals(route.target().type())) {
                members = membership.members(rule.orgId()).stream()
                        .collect(Collectors.toMap(Member::userId, Function.identity()));
            }
            for (Contact contact : contacts(rule.orgId(), route.target(), type, members)) {
                String address = contact.address(type);
                if (address != null && !address.isBlank()) {
                    recipients.putIfAbsent(route.channel() + "|" + address,
                            new Addressed(route.channel(), new Recipient(address, contact.userId(), contact.name())));
                }
            }
        }
        return recipients;
    }

    private List<Contact> contacts(String orgId, Target target, AddressType type, Map<String, Member> members) {
        return switch (String.valueOf(target.type())) {
            case "address" -> type == AddressType.USER
                    ? List.of()
                    : List.of(type == AddressType.EMAIL
                            ? new Contact(null, null, target.value(), null)
                            : new Contact(null, null, null, target.value()));
            case "user" -> members.containsKey(target.id()) ? List.of(Contact.of(members.get(target.id()))) : List.of();
            case "group" -> groups.find(orgId, target.id())
                    .map(group -> group.members() == null ? List.<RecipientGroup.GroupMember>of() : group.members())
                    .orElse(List.of())
                    .stream()
                    .map(member -> "user".equals(member.type())
                            ? (members.containsKey(member.userId()) ? Contact.of(members.get(member.userId())) : null)
                            : new Contact(null, member.name(), member.email(), member.phone()))
                    .filter(contact -> contact != null)
                    .toList();
            default -> List.of();
        };
    }

    /** @return which limit refused the notification, or null when it may be sent */
    private String refusedBy(Rule rule, String channel, String address, Instant now) {
        if (rule.throttle() == null) {
            return null;
        }
        Limit perRecipient = rule.throttle().perRecipient();
        if (perRecipient != null && !acquire("rcpt#" + rule.ruleId() + "#" + channel + "#" + address, perRecipient, now)) {
            return "perRecipient";
        }
        Limit perRule = rule.throttle().perRule();
        if (perRule != null && !acquire("rule#" + rule.ruleId(), perRule, now)) {
            return "perRule";
        }
        return null;
    }

    /** Fixed windows: the counter key carries the start of the window the moment falls into. */
    private boolean acquire(String key, Limit limit, Instant now) {
        if (limit.windowSeconds() <= 0) {
            return true;
        }
        long windowStart = now.getEpochSecond() / limit.windowSeconds() * limit.windowSeconds();
        return throttle.tryAcquire(key + "#" + windowStart, limit.max(),
                Instant.ofEpochSecond(windowStart + limit.windowSeconds()));
    }

    private static String subject(Alert alert, Rule rule, String channel, AddressType type, Map<String, Object> variables) {
        ChannelTemplate template = template(rule, channel);
        if (template != null && template.subject() != null) {
            return Templates.render(template.subject(), variables);
        }
        return type == AddressType.USER ? alert.title() : "[" + alert.severity() + "] " + alert.title();
    }

    /** SMS and email always carry the link to the alert, even when the rule's template leaves it out. */
    private static String body(Alert alert, Rule rule, String channel, AddressType type, Map<String, Object> variables) {
        ChannelTemplate template = template(rule, channel);
        String body = template != null && template.body() != null
                ? Templates.render(template.body(), variables)
                : defaultBody(alert, type);
        String alertUrl = String.valueOf(variables.get("alertUrl"));
        if (type == AddressType.USER || body.contains(alertUrl)) {
            return body;
        }
        return body + (type == AddressType.EMAIL ? "\n\n" : " ") + alertUrl;
    }

    private static String defaultBody(Alert alert, AddressType type) {
        return switch (type) {
            case PHONE -> "[" + alert.severity() + "] " + alert.title() + " - " + alert.body();
            case USER -> alert.body();
            case EMAIL -> alert.fields().isEmpty()
                    ? alert.body()
                    : alert.body() + "\n\n" + alert.fields().entrySet().stream()
                            .map(field -> field.getKey() + ": " + field.getValue())
                            .collect(Collectors.joining("\n"));
        };
    }

    private static ChannelTemplate template(Rule rule, String channel) {
        return rule.presentation() == null || rule.presentation().channels() == null
                ? null
                : rule.presentation().channels().get(channel);
    }
}
