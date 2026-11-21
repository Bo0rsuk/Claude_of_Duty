package com.claudeofduty.lambda.api;

import com.claudeofduty.core.model.RecipientGroup;
import com.claudeofduty.core.model.Rule;
import com.claudeofduty.core.model.Runbook;
import com.claudeofduty.core.model.Source;
import com.claudeofduty.core.notify.ChannelDirectory;
import com.claudeofduty.core.notify.NotificationDispatcher;
import com.claudeofduty.core.notify.NotificationPlanner;
import com.claudeofduty.core.parser.ParserRegistry;
import com.claudeofduty.core.port.AlertPublisher;
import com.claudeofduty.core.port.AlertRepository;
import com.claudeofduty.core.port.Membership;
import com.claudeofduty.core.port.NotificationQueue;
import com.claudeofduty.core.port.PushSubscriptions;
import com.claudeofduty.core.port.RecentEvents;
import com.claudeofduty.core.port.Repository;
import com.claudeofduty.core.port.TextModel;
import com.claudeofduty.core.port.Throttle;
import com.claudeofduty.core.port.UserDirectory;
import com.claudeofduty.core.rules.Operators;
import com.claudeofduty.core.service.AlertService;
import com.claudeofduty.core.service.ConfigService;
import com.claudeofduty.core.service.OrganizationService;
import com.claudeofduty.core.service.Policies;
import com.claudeofduty.core.service.PreviewService;
import com.claudeofduty.core.service.PushService;
import com.claudeofduty.core.service.RuleAssistantService;
import com.claudeofduty.core.service.RulePreviewService;
import java.time.Clock;
import java.util.function.Supplier;

/** Everything the routes call into, built from the storage and service ports. */
public record Services(
        Membership membership,
        OrganizationService organizations,
        AlertService alerts,
        ConfigService<Source> sources,
        ConfigService<Rule> rules,
        ConfigService<RecipientGroup> groups,
        ConfigService<Runbook> runbooks,
        PreviewService preview,
        RulePreviewService rulePreviews,
        RuleAssistantService assistant,
        PushService push) {

    /** What the API depends on outside its own code. Production uses AWS adapters, tests use in-memory ones. */
    public record Ports(
            Repository<Source> sources,
            Repository<Rule> rules,
            Repository<RecipientGroup> groups,
            Repository<Runbook> runbooks,
            Membership membership,
            UserDirectory users,
            AlertRepository alerts,
            AlertPublisher publisher,
            RecentEvents recentEvents,
            TextModel model,
            PushSubscriptions pushSubscriptions,
            Supplier<String> pushPublicKey,
            Throttle throttle,
            NotificationQueue notificationQueue,
            String appUrl) {
    }

    public static Services create(Ports ports, Clock clock) {
        ParserRegistry parsers = ParserRegistry.withDefaults();
        Operators operators = Operators.withDefaults();
        RulePreviewService rulePreviews = new RulePreviewService(ports.sources(), ports.recentEvents(), operators);
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                new NotificationPlanner(ChannelDirectory.withDefaults(), ports.groups(), ports.membership(),
                        ports.throttle(), ports.appUrl(), clock),
                ports.notificationQueue(), ports.alerts()::appendTimeline, clock);
        return new Services(
                ports.membership(),
                new OrganizationService(ports.membership(), ports.users(), clock),
                new AlertService(ports.alerts(), ports.runbooks(), ports.membership(), ports.publisher(),
                        new AlertService.Notifications(ports.rules(), ports.sources(), dispatcher), clock),
                new ConfigService<>(ports.sources(), Source.class, "sourceId",
                        Policies.sources(parsers, ports.rules()), clock),
                new ConfigService<>(ports.rules(), Rule.class, "ruleId", Policies.rules(operators, ports.sources()), clock),
                new ConfigService<>(ports.groups(), RecipientGroup.class, "groupId", Policies.groups(), clock),
                new ConfigService<>(ports.runbooks(), Runbook.class, "runbookId", Policies.runbooks(), clock),
                new PreviewService(parsers, clock),
                rulePreviews,
                new RuleAssistantService(rulePreviews, ports.model(), operators),
                new PushService(ports.pushSubscriptions(), ports.pushPublicKey(), clock));
    }
}
