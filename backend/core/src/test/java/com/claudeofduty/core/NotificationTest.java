package com.claudeofduty.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.claudeofduty.core.model.Alert;
import com.claudeofduty.core.model.Level;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.Member.Role;
import com.claudeofduty.core.model.NormalizedEvent;
import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.model.Organization;
import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.RecipientGroup.GroupMember;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Rule.ChannelTemplate;
import com.claudeofduty.core.model.Rule.Delivery;
import com.claudeofduty.core.model.Rule.Limit;
import com.claudeofduty.core.model.Rule.Presentation;
import com.claudeofduty.core.model.Rule.Route;
import com.claudeofduty.core.model.Rule.Target;
import com.claudeofduty.core.model.Rule.Throttle;
import com.claudeofduty.core.model.Rule.Window;
import com.claudeofduty.core.model.Severity;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.model.TimelineEntry;
import com.claudeofduty.core.notify.ChannelDirectory;
import com.claudeofduty.core.notify.NotificationChannel;
import com.claudeofduty.core.notify.NotificationDelivery;
import com.claudeofduty.core.notify.NotificationDispatcher;
import com.claudeofduty.core.notify.NotificationPlanner;
import com.claudeofduty.core.notify.PermanentDeliveryException;
import com.claudeofduty.core.notify.NotificationPlanner.Plan;
import com.claudeofduty.core.notify.NotificationPlanner.Suppressed;
import com.claudeofduty.core.port.Membership;
import com.claudeofduty.core.port.Repository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-11-20T21:00:10.000Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-11-20T21:00:09.400Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ALERT_URL = "https://app.example.pl/alerts/alert-1";

    private static final Member ANNA = new Member("org-1", "anna", Role.ADMIN, "Anna Nowak", "anna@example.pl", "+48600100200", NOW);
    private static final Member BOB = new Member("org-1", "bob", Role.USER, "Bob", "bob@example.pl", null, NOW);
    private static final RecipientGroup CLOUD_ENGS = new RecipientGroup("grp-1", "org-1", "cloud_engs", List.of(
            new GroupMember("user", "anna", null, null, null),
            new GroupMember("user", "bob", null, null, null),
            new GroupMember("user", "left-the-company", null, null, null),
            new GroupMember("contact", null, "NOC", "noc@example.pl", "+48221002000")), NOW, NOW);

    private final Map<String, Integer> counters = new HashMap<>();
    private final List<NotificationJob> queued = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();
    private final List<TimelineEntry> timeline = new ArrayList<>();

    private final NotificationPlanner planner = new NotificationPlanner(
            ChannelDirectory.withDefaults(), new Groups(), new Members(),
            (key, max, windowEnd) -> counters.merge(key, 1, Integer::sum) <= max,
            "https://app.example.pl", CLOCK);

    @Test
    void groupIsExpandedToTheAddressEachChannelNeeds() {
        Plan plan = planner.plan(alert(), rule(null, null,
                route("sms", "group", "grp-1"), route("email", "group", "grp-1"), route("ui", "group", "grp-1")), source());

        // Bob has no phone, the outside contact has no account, the departed member is skipped.
        assertEquals(List.of("sms +48600100200", "sms +48221002000",
                "email anna@example.pl", "email bob@example.pl", "email noc@example.pl",
                "ui anna", "ui bob"), addresses(plan));
        assertTrue(plan.suppressed().isEmpty());
        NotificationJob sms = plan.jobs().get(0);
        assertEquals("Anna Nowak", sms.recipient().name());
        assertEquals("anna", sms.recipient().userId());
        assertEquals(Severity.CRITICAL, sms.severity());
        assertEquals(1, sms.attempt());
        assertEquals(RECEIVED_AT, sms.receivedAt());
    }

    @Test
    void someoneReachedTwiceIsNotifiedOncePerChannel() {
        Plan plan = planner.plan(alert(), rule(null, null,
                route("email", "group", "grp-1"), route("email", "user", "anna"),
                route("email", "address", "anna@example.pl"), route("email", "address", "oncall@example.pl"),
                route("sms", "address", "+48500600700"), route("ui", "address", "nobody"),
                route("pigeon", "user", "anna"), route("email", "group", "no-such-group")), source());

        assertEquals(List.of("email anna@example.pl", "email bob@example.pl", "email noc@example.pl",
                "email oncall@example.pl", "sms +48500600700"), addresses(plan));
    }

    @Test
    void defaultTextsFitTheChannelAndAlwaysLinkToTheAlert() {
        Plan plan = planner.plan(alert(), rule(null, null,
                route("sms", "user", "anna"), route("email", "user", "anna"), route("ui", "user", "anna")), source());

        NotificationJob sms = plan.jobs().get(0);
        assertEquals("[CRITICAL] payment-api: DB_TIMEOUT - Timeout on payment-api-1 " + ALERT_URL, sms.body());

        NotificationJob email = plan.jobs().get(1);
        assertEquals("[CRITICAL] payment-api: DB_TIMEOUT", email.subject());
        assertEquals("Timeout on payment-api-1\n\nservice: payment-api\nhttp.status: 503\n\n" + ALERT_URL, email.body());

        NotificationJob ui = plan.jobs().get(2);
        assertEquals("payment-api: DB_TIMEOUT", ui.subject());
        assertEquals("Timeout on payment-api-1", ui.body());
    }

    @Test
    void ruleTemplatesOverrideTheTextPerChannel() {
        Presentation presentation = new Presentation("{{service}}: {{code}}", "{{message}}", List.of(), Map.of(
                "sms", new ChannelTemplate(null, "[{{severity}}] {{service}} {{code}} x{{count}}"),
                "email", new ChannelTemplate("{{ruleName}} from {{sourceName}}", "See {{alertUrl}} for {{code}}")));

        Plan plan = planner.plan(alert(), rule(presentation, null, route("sms", "user", "anna"), route("email", "user", "anna")),
                source());

        assertEquals("[CRITICAL] payment-api DB_TIMEOUT x7 " + ALERT_URL, plan.jobs().get(0).body());
        assertEquals("Payment database timeouts from Payments API", plan.jobs().get(1).subject());
        assertEquals("See " + ALERT_URL + " for DB_TIMEOUT", plan.jobs().get(1).body());
    }

    @Test
    void throttlingHoldsBackNotificationsOverTheLimit() {
        Rule perRecipient = rule(null, new Throttle(null, new Limit(2, 300)), route("sms", "user", "anna"));
        assertEquals(1, planner.plan(alert(), perRecipient, source()).jobs().size());
        assertEquals(1, planner.plan(alert(), perRecipient, source()).jobs().size());
        Plan third = planner.plan(alert(), perRecipient, source());
        assertTrue(third.jobs().isEmpty());
        assertEquals(List.of(new Suppressed("sms", "+48600100200", "perRecipient")), third.suppressed());

        counters.clear();
        Rule perRule = rule(null, new Throttle(new Limit(2, 300), null), route("email", "group", "grp-1"));
        Plan plan = planner.plan(alert(), perRule, source());
        assertEquals(List.of("email anna@example.pl", "email bob@example.pl"), addresses(plan));
        assertEquals(List.of(new Suppressed("email", "noc@example.pl", "perRule")), plan.suppressed());
        // The counter is tied to the 5-minute window the moment falls into.
        assertEquals(List.of("rule#rule-1#" + NOW.getEpochSecond() / 300 * 300), List.copyOf(counters.keySet()));
    }

    @Test
    void outsideTheDeliveryWindowRecipientsAreReachedOnTheFallbackChannelOrNotAtAll() {
        Route[] routing = {route("sms", "group", "grp-1"), route("ui", "user", "anna"), route("sms", "address", "+48500600700")};
        // The clock reads 21:00 UTC, which is 22:00 in Warsaw.
        Rule daytime = rule(null, null, new Delivery(new Window("08:00", "20:00", "Europe/Warsaw"), "email"), routing);

        Plan plan = planner.plan(alert(), daytime, source());

        // Everyone the routing names, once, by email. The bare phone number has no email address.
        assertEquals(List.of("email anna@example.pl", "email bob@example.pl", "email noc@example.pl"), addresses(plan));
        assertTrue(plan.suppressed().isEmpty());

        // The window is read in its own time zone, and may run over midnight.
        Rule evening = rule(null, null, new Delivery(new Window("21:30", "06:00", "Europe/Warsaw"), "email"), routing);
        assertEquals(List.of("sms +48600100200", "sms +48221002000", "ui anna", "sms +48500600700"),
                addresses(planner.plan(alert(), evening, source())));
        Rule utc = rule(null, null, new Delivery(new Window("21:30", "06:00", null), "email"), routing);
        assertEquals(3, planner.plan(alert(), utc, source()).jobs().size());

        // Without a fallback channel nothing is sent, and the timeline says why.
        Rule silent = rule(null, null, new Delivery(new Window("08:00", "20:00", "Europe/Warsaw"), null), routing);
        Plan held = planner.plan(alert(), silent, source());
        assertTrue(held.jobs().isEmpty());
        assertEquals(new Suppressed("sms", "+48600100200", "deliveryWindow"), held.suppressed().get(0));
        assertEquals(4, held.suppressed().size());

        // A resend that a person asked for goes out on the rule's own channels at any hour.
        assertEquals(4, planner.plan(alert(), silent, source(), false).jobs().size());
    }

    @Test
    void dispatcherQueuesJobsAndRecordsWhatWasHeldBackOrLost() {
        Rule rule = rule(null, new Throttle(new Limit(1, 300), null), route("sms", "user", "anna"), route("email", "user", "anna"));
        NotificationDispatcher dispatcher = new NotificationDispatcher(planner, (job, delay) -> {
            if (job.channel().equals("sms")) {
                throw new IllegalStateException("queue is gone");
            }
            queued.add(job);
        }, timeline::add, CLOCK);

        dispatcher.alertCreated(alert(), rule, source());

        assertTrue(queued.isEmpty());
        assertEquals(List.of(TimelineEntry.Type.NOTIFICATION_SUPPRESSED, TimelineEntry.Type.NOTIFICATION_FAILED),
                timeline.stream().map(TimelineEntry::type).toList());
        assertEquals("perRule", timeline.get(0).detail().get("reason"));
        assertEquals(true, timeline.get(1).detail().get("final"));
        assertEquals("alert-1", timeline.get(1).alertId());
    }

    @Test
    void deliveryRecordsSuccessWithLatencyAndProviderDetails() {
        NotificationDelivery delivery = delivery(job -> Map.of("messageId", "sns-123"));

        assertTrue(delivery.deliver(job(1), 1));

        TimelineEntry entry = timeline.get(0);
        assertEquals(TimelineEntry.Type.NOTIFICATION_SENT, entry.type());
        assertEquals(Map.of("channel", "sms", "recipient", "+48600100200", "attempt", 1,
                "deliveredAt", "2026-11-20T21:00:10.000Z", "latencyMs", 600L, "messageId", "sns-123"), entry.detail());
    }

    @Test
    void failedDeliveryIsRecordedAndLeftForTheQueueToRetry() {
        NotificationDelivery delivery = delivery(job -> {
            throw new IllegalStateException("Phone number is not verified");
        });

        assertFalse(delivery.deliver(job(2), 3));

        TimelineEntry entry = timeline.get(0);
        assertEquals(TimelineEntry.Type.NOTIFICATION_FAILED, entry.type());
        // Third try of the second round.
        assertEquals(6, entry.detail().get("attempt"));
        assertEquals("Phone number is not verified", entry.detail().get("error"));
        assertEquals(false, entry.detail().get("final"));

        assertFalse(delivery(Map.of()).deliver(job(1), 1));
        assertEquals("No such channel: sms", timeline.get(1).detail().get("error"));
    }

    @Test
    void failureThatRetryingCannotFixIsFinalAndTakenOffTheQueue() {
        NotificationDelivery delivery = delivery(job -> {
            throw new PermanentDeliveryException("User has no devices registered for push");
        });

        assertTrue(delivery.deliver(job(1), 1));

        TimelineEntry entry = timeline.get(0);
        assertEquals(TimelineEntry.Type.NOTIFICATION_FAILED, entry.type());
        assertEquals(true, entry.detail().get("final"));
        assertEquals("User has no devices registered for push", entry.detail().get("error"));
    }

    @Test
    void pushGoesToTheUserAndAResendIgnoresThrottling() {
        Rule rule = rule(null, new Throttle(new Limit(1, 300), null), route("push", "group", "grp-1"));
        NotificationDispatcher dispatcher = new NotificationDispatcher(planner, (job, delay) -> queued.add(job),
                timeline::add, CLOCK);

        dispatcher.alertCreated(alert(), rule, source());
        // The limit of one notification per window was used up by Anna; Bob was held back.
        assertEquals(List.of("push anna"), queued.stream().map(j -> j.channel() + " " + j.recipient().address()).toList());
        assertEquals(List.of(TimelineEntry.Type.NOTIFICATION_SUPPRESSED), timeline.stream().map(TimelineEntry::type).toList());
        assertEquals("payment-api: DB_TIMEOUT", queued.get(0).subject());
        assertEquals("Timeout on payment-api-1", queued.get(0).body());

        queued.clear();
        assertEquals(2, dispatcher.resend(alert(), rule, source()));
        assertEquals(List.of("anna", "bob"), queued.stream().map(j -> j.recipient().address()).toList());
        assertEquals(1, timeline.size());
    }

    @Test
    void deadLetteredJobGetsMoreRoundsAndThenAFinalFailure() {
        NotificationDelivery delivery = delivery(job -> Map.of());

        delivery.retryOrGiveUp(job(1));
        delivery.retryOrGiveUp(job(2));
        assertEquals(List.of(2, 3), queued.stream().map(NotificationJob::attempt).toList());
        assertEquals(List.of(30, 60), delays);
        assertTrue(timeline.isEmpty());

        delivery.retryOrGiveUp(job(3));
        assertEquals(2, queued.size());
        assertEquals(TimelineEntry.Type.NOTIFICATION_FAILED, timeline.get(0).type());
        assertEquals(true, timeline.get(0).detail().get("final"));
        assertEquals(9, timeline.get(0).detail().get("attempt"));
    }

    private NotificationDelivery delivery(NotificationChannel sms) {
        return delivery(Map.of("sms", sms));
    }

    private NotificationDelivery delivery(Map<String, NotificationChannel> channels) {
        return new NotificationDelivery(channels, (job, delay) -> {
            queued.add(job);
            delays.add(delay);
        }, timeline::add, CLOCK);
    }

    private static NotificationJob job(int attempt) {
        return new NotificationJob("n-1", "org-1", "alert-1", "sms", Severity.CRITICAL,
                new NotificationJob.Recipient("+48600100200", "anna", "Anna Nowak"), null, "text", attempt, RECEIVED_AT);
    }

    private static List<String> addresses(Plan plan) {
        return plan.jobs().stream().map(job -> job.channel() + " " + job.recipient().address()).toList();
    }

    private static Route route(String channel, String targetType, String idOrValue) {
        return new Route(channel, "address".equals(targetType)
                ? new Target(targetType, null, idOrValue)
                : new Target(targetType, idOrValue, null));
    }

    private static Rule rule(Presentation presentation, Throttle throttle, Route... routing) {
        return rule(presentation, throttle, null, routing);
    }

    private static Rule rule(Presentation presentation, Throttle throttle, Delivery delivery, Route... routing) {
        return new Rule("rule-1", "org-1", "src-1", "Payment database timeouts", true, null, Severity.CRITICAL,
                List.of(routing), presentation, List.of(), throttle, null, NOW, NOW, null, delivery);
    }

    private static Source source() {
        return new Source("src-1", "org-1", "Payments API", true, null, null, null, NOW, NOW);
    }

    private static Alert alert() {
        Map<String, Object> eventFields = new java.util.LinkedHashMap<>();
        eventFields.put("service", "payment-api");
        eventFields.put("code", "DB_TIMEOUT");
        NormalizedEvent event = new NormalizedEvent("evt-1", "org-1", "src-1", NOW, RECEIVED_AT, Level.ERROR,
                "Timeout", eventFields, "raw");
        Map<String, Object> shown = new java.util.LinkedHashMap<>();
        shown.put("service", "payment-api");
        shown.put("http.status", 503);
        return new Alert("alert-1", "org-1", "rule-1", "Payment database timeouts", "src-1", "fp", Severity.CRITICAL,
                Alert.Status.OPEN, "payment-api: DB_TIMEOUT", "Timeout on payment-api-1", shown, 7, NOW, NOW, event,
                null, null, null, null, null, null, new Alert.Timing(RECEIVED_AT, NOW), null);
    }

    private static final class Groups implements Repository<RecipientGroup> {
        @Override
        public List<RecipientGroup> list(String orgId) {
            return List.of(CLOUD_ENGS);
        }

        @Override
        public Optional<RecipientGroup> find(String orgId, String id) {
            return id.equals(CLOUD_ENGS.groupId()) ? Optional.of(CLOUD_ENGS) : Optional.empty();
        }

        @Override
        public void save(RecipientGroup item) {
        }

        @Override
        public void delete(String orgId, String id) {
        }
    }

    private static final class Members implements Membership {
        @Override
        public Optional<Member> findMember(String userId) {
            return members("org-1").stream().filter(member -> member.userId().equals(userId)).findFirst();
        }

        @Override
        public List<Member> members(String orgId) {
            return List.of(ANNA, BOB);
        }

        @Override
        public void saveMember(Member member) {
        }

        @Override
        public void removeMember(String orgId, String userId) {
        }

        @Override
        public Optional<Organization> findOrganization(String orgId) {
            return Optional.empty();
        }

        @Override
        public Optional<Organization> findOrganizationByInviteCode(String inviteCode) {
            return Optional.empty();
        }

        @Override
        public void saveOrganization(Organization organization) {
        }
    }
}
