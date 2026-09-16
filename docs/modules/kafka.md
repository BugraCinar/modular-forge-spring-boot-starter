# Kafka application events

Optional module: `dev.modularforge.kafka`. Default: disabled. No HTTP routes, tables, scheduled jobs or database-specific dependencies. Public contract: `shared.events.AccountEvent`, published through Spring `ApplicationEventPublisher`. Core features do not import the Kafka package.

## Enable

Provision the topic `modular-forge.account-events.v1` before enabling the module. Production: replicated brokers, replication factor 3 and `min.insync.replicas=2`, appropriate retention and producer ACL limited to this topic. The application does not create topics or administer the cluster.

```dotenv
MODULE_KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=broker.example.com:9093
KAFKA_SECURITY_PROTOCOL=SSL
KAFKA_EVENTS_TOPIC=modular-forge.account-events.v1
```

TLS uses the JVM trust store; install the broker CA there. For authenticated clusters, use `SASL_SSL`, set `KAFKA_SASL_MECHANISM` (e.g. SCRAM-SHA-512) and supply `KAFKA_SASL_JAAS_CONFIG` from your secret manager. Never commit credentials. `PLAINTEXT` is an explicit local-development setting only. The disabled module creates no Kafka producer or connection and requires no broker address.

## Events and extension

The initial integrated event is `account.email-changed`, emitted by `EmailChangeService.confirm` for both users and admins. It contains exactly `eventId` (UUID), `schemaVersion` (1), `type`, `occurredAt` (UTC ISO timestamp), `accountRole` and `accountId`. It contains no email, name, password or token. The Kafka key is `role:id`, preventing user/admin ID collisions and keeping a given account on the same partition while the partition count is unchanged.

For another application event, add a factory to the shared event contract and call `events.publishEvent(...)` inside the owning service's database transaction. Add a regression test for that workflow. Keep payloads minimal and version schema changes explicitly. Do not publish events from a request controller before persistence succeeds.

External consumers use String deserialization then parse the JSON against schema version 1, with their own group ID. Process idempotently using `eventId`; commit offsets only after successful processing. Configure bounded retry and a restricted dead-letter topic in the consuming service. This module does not start a dummy consumer or expose a public publish endpoint. An in-process consumer belongs in its own optional module and must not import this producer implementation.

## Delivery contract

Spring's transactional listener runs only after a successful commit, for both JPA and MongoDB transactions. Rolled-back transactions and calls outside a transaction do not send messages. Producer acknowledgements are `all` with idempotence enabled; send admission is bounded to 1 second and delivery timeout to 30 seconds. Success/failure increments the Micrometer `app.kafka.events` counter tagged `outcome=sent|failed`; failures produce sanitized warnings. Management HTTP exposure is unchanged.

**Delivery is best effort, not a durable outbox or exactly-once database-to-Kafka delivery.** A crash after database commit, unavailable broker or exhausted delivery timeout can lose an event. Producer idempotence does not close that gap. The committed account change remains successful on broker failure. Do not use this channel as the sole source for financial actions, security enforcement or mandatory email delivery. Those require a transactional outbox per persistence provider and an idempotent consumer. Existing email delivery stays as-is. Alert on failed events and broker health.

## Removal and migration

Disable `MODULE_KAFKA_ENABLED`, redeploy, then drain retained topic data with existing consumers if needed. No SQL/Mongo schema migration is required. Physical removal: delete `dev.modularforge.kafka`, its tests and boundary/disabled-context assertions; remove the `spring-kafka` and test-only `spring-kafka-test` dependencies, configuration/env entries, catalog entry and this guide. Keep the shared event contract and core publication calls if another adapter uses them; with no listener they are harmless. Topic deletion is a separate administrator action subject to retention requirements.

## References

- [Spring Kafka sending messages](https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html)
- [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)

## Dependency audit note (2026-09-16)

The Boot-managed Kafka client is 4.2.1. Dependency-Check reports CVE-2026-41115 (CVSS 4.3). [Apache's advisory](https://kafka.apache.org/community/cve-list/) identifies a broker consumer-group ACL documentation discrepancy and calls for reviewing GROUP DESCRIBE/READ privileges; it does not prescribe a client code fix. This module is a producer, grants no group permissions and ships no broker at runtime. Operators must still review broker ACLs. The finding remains visible in the report; it is not suppressed. Runtime dependency scanning uses the existing CVSS 7 failure threshold.
