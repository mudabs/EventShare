# Operations Manual

## Monitoring

Prometheus scrapes the API, the worker, and RabbitMQ every 15 seconds. Grafana provisions a
Prometheus datasource and the EventShare Overview dashboard automatically at `/grafana`.

Key metrics already exposed:

API request rate and status mix from `http_server_requests_seconds_count`, p95 latency from
the histogram, and 5xx error rate. Upload throughput from `eventshare_media_uploaded_total`.
Worker throughput and failures from `eventshare_worker_processed_total` and
`eventshare_worker_failed_total`, with mean processing time from the timer. RabbitMQ queue
depth from `rabbitmq_queue_messages_ready`. JVM heap from `jvm_memory_used_bytes`.

Suggested alerts to add in Grafana or Alertmanager: sustained 5xx rate above a threshold,
API p95 latency above target, worker failure rate above zero for several minutes,
`eventshare.media.process` queue depth growing without draining (worker stalled), and DLQ
depth greater than zero (poison messages need triage).

## Backups

PostgreSQL is the only stateful component that must be backed up; R2 holds media durably and
should be protected with its own versioning and lifecycle policy.

Database. Schedule a nightly logical backup and copy it off the host (for example to R2 or
another provider):

```bash
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" \
  | gzip > backup-$(date +%F).sql.gz
```

Add this to cron and retain, for example, 7 daily and 4 weekly copies. Test restores
periodically; an untested backup is not a backup.

Object storage. Enable object versioning on the R2 bucket and a lifecycle rule that expires
old export ZIPs (the `events/*/exports/` prefix). Originals and thumbnails should be retained
for the life of the event.

Configuration. Keep `.env` in a secrets manager or encrypted store, not only on the VPS.

## Disaster recovery

Targets to set with stakeholders: an RPO equal to your backup interval (nightly means up to
24 hours of metadata loss) and an RTO based on rebuild time (typically under an hour on a
fresh VPS).

Recovery steps. Provision a new host with Docker. Clone the repository and restore `.env`.
Restore the database before starting the API so Flyway sees an existing schema:

```bash
docker compose up -d postgres
gunzip -c backup-YYYY-MM-DD.sql.gz | docker compose exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker compose up -d --build
```

Media in R2 is unaffected by a host loss because it is stored externally. Verify with
`/api/ping`, a test event creation, and a test upload.

## Runbooks

Worker backlog. If the media process queue grows, scale workers:
`docker compose up -d --scale worker=3`. Confirm processing time and failure rate in Grafana.

Dead-letter queue not empty. Inspect messages in the RabbitMQ management UI (the
`eventshare.media.dlq` queue). Common causes are unsupported image formats or corrupt
uploads; the corresponding media rows are marked FAILED. Fix the cause, then optionally
re-publish or re-trigger processing.

API will not start. Check `docker compose logs api`. A Flyway validation error means a schema
or migration mismatch; never edit an applied migration, add a new one. A datasource error
means PostgreSQL is not healthy yet; the API depends on the postgres healthcheck.

High latency. Check the API latency and JVM heap panels. Scale the API
(`docker compose up -d --scale api=2`) and confirm database connection pool headroom.

## Logs

`docker compose logs -f <service>`. Log rotation is configured per service (json-file, 10 MB
times 3 files). For long-term retention ship logs to an external aggregator.
