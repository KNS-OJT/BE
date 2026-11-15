# Production deployment (Neon)

`docker-compose.neon.yml` runs only the backend and connects it to the existing Neon database. It does not create a database, apply migrations, deploy the frontend, or terminate public HTTPS. The backend runs as the non-root image user, with a read-only root filesystem, dropped Linux capabilities, and a loopback-only published port. Put a TLS-enabled reverse proxy/load balancer in front of it; do not expose port 8080 to the Internet.

## Required server configuration

1. Use a supported Linux server with Docker Engine and the Docker Compose plugin. Point a DNS name at the server and configure the reverse proxy/load balancer to terminate HTTPS and proxy to `http://127.0.0.1:8080`. Forward the standard `X-Forwarded-*` headers only from that trusted proxy.
2. Provide the exact frontend origin(s), including scheme and port where applicable, for `CORS_ALLOWED_ORIGINS` (comma-separated, no wildcard). If frontend and API are same-origin through the proxy, use that origin.
3. Use Neon’s **direct** hostname (not `-pooler`), the existing `p03_app` database user, and the Neon database name. The app role password, production JWT signing secret (at least 32 random bytes), and Neon root CA PEM must be files readable by the Docker daemon on the server. Keep secret files outside the repository and do not put secret values in shell history or the Compose environment file. Compose file-backed secrets are read-only bind mounts: the container runs as UID/GID `10001`, so make the secret files owned by `root:10001` with mode `0440` and the secret directory searchable by group `10001` (for example `root:10001`, mode `0750`). The deployment account must be able to traverse the secret directory when Compose validates the source files. Keep `/etc/p03/deploy.env` readable only to the deployment administrator.
4. Create `/etc/p03/deploy.env` with non-secret settings and paths only:

   ```dotenv
   DB_HOST=<Neon direct hostname>
   DB_NAME=<Neon database name>
   CORS_ALLOWED_ORIGINS=https://<frontend-domain>
   DB_PASSWORD_FILE=/etc/p03/secrets/db_password
   JWT_SECRET_FILE=/etc/p03/secrets/jwt_secret
   NEON_ROOT_CERT_FILE=/etc/p03/secrets/neon-root.crt
   BACKEND_PORT=8080
   DB_POOL_SIZE=10
   ```

   Restrict `/etc/p03/deploy.env` to the deployment administrator (for example, mode `600`). Write secret files without adding a trailing newline; never commit them.

   For local Compose deployments, the source files must be readable by the container’s fixed GID `10001`; create the directory and set ownership/mode before copying the secrets:

   ```sh
   sudo install -d -o root -g 10001 -m 0750 /etc/p03/secrets
   sudo install -o root -g 10001 -m 0440 <secure-source>/db_password /etc/p03/secrets/db_password
   sudo install -o root -g 10001 -m 0440 <secure-source>/jwt_secret /etc/p03/secrets/jwt_secret
   sudo install -o root -g 10001 -m 0440 <secure-source>/neon-root.crt /etc/p03/secrets/neon-root.crt
   ```

   Generate the production JWT secret on the server without printing it:

   ```sh
   sudo sh -c 'umask 077; openssl rand -hex 48 | tr -d "\n" > /etc/p03/secrets/jwt_secret'
   sudo chown root:10001 /etc/p03/secrets/jwt_secret
   sudo chmod 0440 /etc/p03/secrets/jwt_secret
   ```

   Transfer the `p03_app` password through your approved secret-management channel; do not paste it into commands or chat. Keep the JWT secret stable across restarts, and rotate it deliberately (rotation invalidates existing tokens). `deploy.env` paths above must match the installed files.
5. Apply all unapplied SQL migrations to Neon **as a database administrator**, in filename order, before starting the new backend image. Do not run `db/init` against the existing Neon database. Do not validate the legacy goods-receipt constraint until its real source document/reference has been reconciled.
6. Deploy from the repository root:

   ```sh
   docker compose --env-file /etc/p03/deploy.env -f deploy/docker-compose.neon.yml config
   docker compose --env-file /etc/p03/deploy.env -f deploy/docker-compose.neon.yml up -d --build
   docker compose --env-file /etc/p03/deploy.env -f deploy/docker-compose.neon.yml ps
   curl --fail http://127.0.0.1:8080/actuator/health/readiness
   ```

   Check container logs and the reverse-proxy HTTPS URL before pointing the frontend at the API. Swagger/OpenAPI are disabled in `prod`; use the checked-in API contract or enable documentation only in a protected non-production environment.

## Backup, restore, monitoring, and credential operations

These controls must be configured and exercised in the actual Neon and hosting accounts; this repository cannot enable provider settings or verify a live restore.

### Backups and recovery

- In the Neon project, confirm the plan's automated backup/PITR retention and recovery window meet the agreed recovery point objective (RPO) and recovery time objective (RTO). Record the selected retention and test the provider's point-in-time restore procedure into a separate project/database. Never test a restore over the production database.
- Keep an independent encrypted logical backup as well as Neon PITR. The repository script is `db/tests/06_backup_restore.sh`; it uses `pg_dump`, `age`, and SHA-256, and its restore mode checks audit/stock hash chains and stock reconciliation, exiting with an error on failure. Install compatible PostgreSQL client tools and `age` on a restricted operations host, not in the application container. Provision a dedicated read-only `p03_backup` database role with the privileges required by `pg_dump`; do not use `p03_app` or the database owner for scheduled backup jobs.
- Generate an `age` key pair on the operations host. Store the public recipient in the scheduler configuration and keep the private identity in a separate, access-controlled secret store/offline recovery location. Do not store either key with the database credentials or encrypted backup files.
- Store `PGHOST` as the Neon direct hostname, `PGDATABASE` as the database name, `PGUSER=p03_backup`, and PostgreSQL TLS options (`PGSSLMODE=verify-full`, with the Neon CA configured for libpq). Supply the database password through a mode-`0600` `PGPASSFILE` or the host's secret manager; never put it in a command, crontab, or repository file.
- Schedule `db/tests/06_backup_restore.sh backup` at the agreed frequency with `AGE_RECIPIENT` and the database connection settings. Set `BACKUP_DIR` to encrypted storage outside the web root and outside the database host; choose `RETENTION_DAYS` to meet the retention policy. Monitor job exit status, latest successful backup age, available storage, and checksum creation. Forward job logs to the operations log service.
- At least quarterly, restore a recent backup to a newly created isolated database using a separately protected `AGE_IDENTITY_FILE` and an administrator account. Confirm the script exits successfully, review its row counts and integrity results, then execute the application smoke tests against the restored instance. Record the restore duration and update the RTO evidence. Keep the restore target isolated and remove it only through the approved cleanup procedure.

### Monitoring and alerts

- Configure an external HTTPS uptime check for the public API and a restricted server-side check of `http://127.0.0.1:8080/actuator/health/readiness`. Readiness now includes the database health check; liveness intentionally does not, so a Neon outage does not trigger a restart loop. The endpoints expose no component details.
- Alert on failed readiness/HTTPS checks, repeated container restarts, sustained HTTP 5xx responses, elevated latency, disk/memory pressure, database connection exhaustion, failed backup jobs, and a backup older than the agreed RPO. Monitor Neon provider alerts/usage and the server/container metrics through the hosting or monitoring provider.
- Forward Docker JSON logs to a managed, access-controlled log service with retention and alerting. Compose's local rotating logs are only a short local buffer, not durable monitoring. Restrict log access and retention because request records include user IDs and client IPs; never log tokens, passwords, or request bodies.
- Verify alerts by deliberately testing the monitoring path in a staging environment (for example, stop the test service or cause a test check to fail); do not disrupt production to test alert delivery. Keep alert ownership and escalation contacts documented.

### Secret rotation and Neon network restrictions

- Maintain an inventory and owner for the `p03_app` password, JWT signing key, Neon CA, and backup encryption identity. Rotate through the approved secret manager/change process, not by editing committed files. For DB password rotation, schedule a maintenance window: change the role password through a secure interactive admin session, atomically replace the server secret file with correct ownership/mode, recreate the backend, and verify readiness plus authenticated API calls. Have a rollback plan that does not expose the new or old password. Do not assume both credentials work simultaneously for one PostgreSQL role.
- Rotate the JWT key with a planned logout window: generate a new high-entropy key, replace the secret file, recreate the backend, and verify login. All outstanding JWTs signed with the old key become invalid. For Neon CA updates, obtain the CA from the trusted provider channel and verify TLS from the deployed host before removing the old trust material.
- In Neon, enable IP restrictions only after confirming the hosting provider's stable outbound/NAT IP addresses. Allow only those database egress addresses, retain a separately controlled administrative path if needed, and confirm the backend can still connect with certificate verification enabled. A server's public inbound API IP is not necessarily its outbound database source IP.

## Before accepting production traffic

- Verify the container becomes healthy and logs show a successful startup against Neon with `sslmode=verify-full` and `channelBinding=require`. The CA PEM is mounted as a Compose secret and passed explicitly to pgJDBC for certificate-chain and hostname verification.
- From outside the server, verify HTTPS, allowed frontend origins, preflight `OPTIONS`, login, and the frontend’s required read/write workflows. Test authorization with each role and confirm the API port is not publicly reachable.
- Complete the backup/restore, monitoring, secret-rotation, and network-restriction runbook above in the actual hosting account; these provider-level controls cannot be configured or verified from this repository.
- Reconcile the legacy confirmed receipt that lacks its document reference before validating `ck_gr_document_reference`.
