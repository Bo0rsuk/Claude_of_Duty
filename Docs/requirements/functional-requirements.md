# Functional Requirements – Flexible Alerting & Notification Service

Hackathon 2026 – Software Engineering

## 2.1 Ingest from many sources
- [X] Support at least two different source types (e.g. Kafka/RabbitMQ, HTTP/webhook endpoint, tailed log file, polled API)
    - [X] Source type 1 implemented: HTTP webhook (push, API Gateway -> Kinesis)
    - [X] Source type 2 implemented: Kafka + polled HTTP API (`http-pull`, also the log endpoints)
- [X] Handle messages arriving in different schemas (no single fixed format assumed)
- [ ] Handle all sample events from Appendix A
- [X] Adding a new source requires as little code as possible (config/plugin based)
- [X] Adding a new schema requires as little code as possible (config/mapping based)

## 2.2 Let teams define what they care about (rules)
- [X] Filters on any field (source, workflow, schema, severity, environment, message body values)
- [X] Filters on nested body values, e.g. `amount > 1,000,000`
- [X] Filters with set membership, e.g. `error.code in [TIMEOUT, REJECTED]`
- [X] Rules can be combined with AND/OR logic
- [X] Per-rule routing: who gets notified
- [X] Per-rule routing: on which channel(s)
- [X] Per-rule presentation: title template
- [X] Per-rule presentation: body template
- [X] Per-rule presentation: which fields to include
- [X] Per-rule presentation: severity / colour in the UI
- [X] Rules can be created and changed at runtime (via API, UI or live-reloaded config files)
- [X] Rule changes take effect without restarting the service

## 2.3 Deliver alerts on the preferred channel
- [X] Email channel
- [X] SMS channel
- [X] Phone call channel
- [X] UI channel
- [X] External channels may be simulated (mock SMTP, fake SMS/phone gateway that logs, sandbox account)
- [X] Adding a real provider is a small, isolated change (channel abstraction/adapter)
- [ ] Team or person can set channel preferences (e.g. critical → phone + SMS; warning → email; info → UI only)
- [X] Retries on delivery failure
- [X] No lost alerts when a channel is down (durable queue / persisted delivery status)
- [ ] (Optional) Working hours
- [ ] (Optional) On-call schedules

## 2.4 Service UI
- [X] Live feed of alerts for a team, updated in real time (push, not page refresh)
- [X] Acknowledge an alert
- [X] Resolve an alert
- [X] Manage rules
- [ ] Manage channel preferences
- [ ] Propose fixes / enhancements to the alerts
- [X] Each rule controls how its alerts look (see 2.2)

## 2.5 Don't flood people
- [X] Deduplication: same error 500 times in a minute = one alert with a counter, not 500
- [X] Throttling per rule or recipient
- [X] Grouping per rule or recipient

## 2.6 Help resolve alerts with runbooks
- [ ] Teams can attach runbooks/instructions:
    - [X] rules
    - [ ] schemas
    - [ ] alert types
- [X] Support multiple runbook formats:
    - [X] Markdown page
    - [X] link to wiki/Confluence
    - [X] checklist
    - [X] plain free text
- [ ] Handle the example runbook from Appendix B
- [X] When an alert fires, display the matching runbook and next steps
- [ ] Runbook steps are filled in with values from the message (e.g. workflow ID, service name)
- [ ] Full steps shown in UI and email
- [X] Short link shown in SMS
- [ ] Steps can be actions, not only text: retry a workflow
- [ ] Steps can be actions: restart a service
- [ ] Steps can be actions: call a webhook
- [ ] Steps can be actions: open a ticket
- [ ] Steps can be actions: escalate to another team
- [ ] Risky actions require human approval first (from the UI)
- [ ] Risky actions require human approval first (or by replying to the alert)
- [X] Record what was done and by whom
- [ ] Close the alert automatically when the problem goes away
- [X] Adding a new runbook is as easy as adding a rule
- [ ] Adding a new kind of action is as easy as adding a rule
- [ ] (Optional) Automatically record resolution steps so runbooks/knowledge base are auto-updated
