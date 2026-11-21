# Functional Requirements – Flexible Alerting & Notification Service

Hackathon 2026 – Software Engineering

## 2.1 Ingest from many sources
- [ ] Support at least two different source types (e.g. Kafka/RabbitMQ, HTTP/webhook endpoint, tailed log file, polled API)
    - [ ] Source type 1 implemented: ______
    - [ ] Source type 2 implemented: ______
- [ ] Handle messages arriving in different schemas (no single fixed format assumed)
- [ ] Handle all sample events from Appendix A
- [ ] Adding a new source requires as little code as possible (config/plugin based)
- [ ] Adding a new schema requires as little code as possible (config/mapping based)

## 2.2 Let teams define what they care about (rules)
- [ ] Filters on any field (source, workflow, schema, severity, environment, message body values)
- [ ] Filters on nested body values, e.g. `amount > 1,000,000`
- [ ] Filters with set membership, e.g. `error.code in [TIMEOUT, REJECTED]`
- [ ] Rules can be combined with AND/OR logic
- [ ] Per-rule routing: who gets notified
- [ ] Per-rule routing: on which channel(s)
- [ ] Per-rule presentation: title template
- [ ] Per-rule presentation: body template
- [ ] Per-rule presentation: which fields to include
- [ ] Per-rule presentation: severity / colour in the UI
- [ ] Rules can be created and changed at runtime (via API, UI or live-reloaded config files)
- [ ] Rule changes take effect without restarting the service

## 2.3 Deliver alerts on the preferred channel
- [ ] Email channel
- [ ] SMS channel
- [ ] Phone call channel
- [ ] UI channel
- [ ] External channels may be simulated (mock SMTP, fake SMS/phone gateway that logs, sandbox account)
- [ ] Adding a real provider is a small, isolated change (channel abstraction/adapter)
- [ ] Team or person can set channel preferences (e.g. critical → phone + SMS; warning → email; info → UI only)
- [ ] Retries on delivery failure
- [ ] No lost alerts when a channel is down (durable queue / persisted delivery status)
- [ ] (Optional) Working hours
- [ ] (Optional) On-call schedules

## 2.4 Service UI
- [ ] Live feed of alerts for a team, updated in real time (push, not page refresh)
- [ ] Acknowledge an alert
- [ ] Resolve an alert
- [ ] Manage rules
- [ ] Manage channel preferences
- [ ] Propose fixes / enhancements to the alerts
- [ ] Each rule controls how its alerts look (see 2.2)

## 2.5 Don't flood people
- [ ] Deduplication: same error 500 times in a minute = one alert with a counter, not 500
- [ ] Throttling per rule or recipient
- [ ] Grouping per rule or recipient

## 2.6 Help resolve alerts with runbooks
- [ ] Teams can attach runbooks/instructions:
    - [ ] rules
    - [ ] schemas
    - [ ] alert types
- [ ] Support multiple runbook formats:
    - [ ] Markdown page
    - [ ] link to wiki/Confluence
    - [ ] checklist
    - [ ] plain free text
- [ ] Handle the example runbook from Appendix B
- [ ] When an alert fires, display the matching runbook and next steps
- [ ] Runbook steps are filled in with values from the message (e.g. workflow ID, service name)
- [ ] Full steps shown in UI and email
- [ ] Short link shown in SMS
- [ ] Steps can be actions, not only text: retry a workflow
- [ ] Steps can be actions: restart a service
- [ ] Steps can be actions: call a webhook
- [ ] Steps can be actions: open a ticket
- [ ] Steps can be actions: escalate to another team
- [ ] Risky actions require human approval first (from the UI)
- [ ] Risky actions require human approval first (or by replying to the alert)
- [ ] Record what was done and by whom
- [ ] Close the alert automatically when the problem goes away
- [ ] Adding a new runbook is as easy as adding a rule
- [ ] Adding a new kind of action is as easy as adding a rule
- [ ] (Optional) Automatically record resolution steps so runbooks/knowledge base are auto-updated
