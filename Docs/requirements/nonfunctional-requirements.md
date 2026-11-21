# Non-Functional Requirements & Judging Criteria – Flexible Alerting & Notification Service

Hackathon 2026 – Software Engineering

## Flexibility
- [ ] Easy to adopt by any team or system
- [ ] Adding a new source needs config or minimal code
- [ ] Adding a new schema needs config or minimal code
- [ ] Adding a new channel needs config or minimal code
- [ ] Adding a new rule type needs config or minimal code
- [ ] Rules are expressive (any field, AND/OR, comparisons, set membership)

## Scalability
- [ ] High volume of messages/alerts does not degrade service performance
- [ ] Service can scale horizontally
- [ ] State handled in a scalable way: rules
- [ ] State handled in a scalable way: dedup windows
- [ ] State handled in a scalable way: delivery status
- [ ] No obvious bottleneck
- [ ] No single point of failure

## Reliability
- [ ] Delivery survives failures (retries)
- [ ] No lost alerts when a channel is down

## UX
- [ ] UI usable by engineers, on-call support and non-engineers
- [ ] UI usable by someone who has never seen it
- [ ] Rule editor is understandable
- [ ] Alerts are clear and actionable
- [ ] Few commands and short time from fresh clone to working demo
- [ ] Clear README
- [ ] Short time for a new team to start getting alerts

## Throughput / Latency
- [ ] Measure messages per second ingested and processed
- [ ] Measure end-to-end latency from ingest to delivery (p50)
- [ ] Measure end-to-end latency from ingest to delivery (p99)
- [ ] Results are measured, not estimated
- [ ] Load-test script created
- [ ] Load-test script and results included in the presentation

## Level of implementation
- [ ] Link to repo
- [ ] Clear README in repo
- [ ] Description of what has been mocked

## Innovation
- [ ] Outstanding solution or additional functionality (e.g. auto-updating runbooks / knowledge base)

## Presentation & Submission
- [ ] Video of up to five minutes explaining and showcasing the solution (primary basis for judging in all categories)
- [ ] Describe how AI tools (e.g. Copilot, Claude) were used during development
- [ ] Describe how security and privacy concerns were addressed
- [ ] Upload video to Lockbox in a directory named with the team's name
- [ ] (Recommended) Also submit a text file with a link to the Git repository or a demo application
- [ ] Prepare for live finals (top five teams) and Q&A session
