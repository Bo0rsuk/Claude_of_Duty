# Non-Functional Requirements & Judging Criteria – Flexible Alerting & Notification Service

Hackathon 2026 – Software Engineering

## Flexibility
- [X] Easy to adopt by any team or system
- [X] Adding a new source needs config or minimal code
- [X] Adding a new schema needs config or minimal code
- [X] Adding a new channel needs config or minimal code
- [X] Adding a new rule type needs config or minimal code
- [X] Rules are expressive (any field, AND/OR, comparisons, set membership)

## Scalability
- [X] High volume of messages/alerts does not degrade service performance
- [X] Service can scale horizontally
- [X] State handled in a scalable way: rules
- [X] State handled in a scalable way: dedup windows
- [X] State handled in a scalable way: delivery status
- [ ] No obvious bottleneck
- [ ] No single point of failure

## Reliability
- [X] Delivery survives failures (retries)
- [X] No lost alerts when a channel is down

## UX
- [ ] UI usable by engineers, on-call support and non-engineers
- [ ] UI usable by someone who has never seen it
- [ ] Rule editor is understandable
- [X] Alerts are clear and actionable
- [X] Few commands and short time from fresh clone to working demo
- [ ] Clear README
- [ ] Short time for a new team to start getting alerts

## Throughput / Latency
- [X] Measure messages per second ingested and processed
- [X] Measure end-to-end latency from ingest to delivery (p50)
- [X] Measure end-to-end latency from ingest to delivery (p99)
- [X] Results are measured, not estimated
- [X] Load-test script created
- [ ] Load-test script and results included in the presentation

## Level of implementation
- [ ] Link to repo
- [ ] Clear README in repo
- [ ] Description of what has been mocked

## Innovation
- [X] Outstanding solution or additional functionality (e.g. auto-updating runbooks / knowledge base)

## Presentation & Submission
- [ ] Video of up to five minutes explaining and showcasing the solution (primary basis for judging in all categories)
- [ ] Describe how AI tools (e.g. Copilot, Claude) were used during development
- [ ] Describe how security and privacy concerns were addressed
- [ ] Upload video to Lockbox in a directory named with the team's name
- [ ] (Recommended) Also submit a text file with a link to the Git repository or a demo application
- [ ] Prepare for live finals (top five teams) and Q&A session
