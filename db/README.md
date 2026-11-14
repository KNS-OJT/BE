# P03 – Database (PostgreSQL 14+)

Inventory & Purchase Request Management × Secure Procurement Workflow.
Covers SRS BR-01..BR-24 and SecRS IA-IN01..IA-IN08.

## Files

| File | Purpose |
|---|---|
| `01_schema.sql` | Tables, constraints, workflow triggers, stock posting, audit log, hash chains, views |
| `02_roles.sql` | Least-privilege accounts: `p03_app`, `p03_readonly`, `p03_backup` (change passwords) |
| `03_seed.sql` | Demo data (fictional). Password for all demo users: `Demo@12345` |
| `migrations/V20261114_01__computer_components_catalog.sql` | Idempotent data migration adding PC components and minimum-stock levels |
| `04_security_tests.sql` | 65 DB-level tests (SEC-TC-301/302, BR-01..22, tamper detection) |
| `05_concurrency_test.sh` | SEC-TC-303: two concurrent stock issues |
| `06_backup_restore.sh` | IA-IN08: encrypted backup (age) + checksum, restore + integrity verification |
| `07_app_role_tests.sql` | Permission and fail-closed tests, run as `p03_app` |
| `schema.dbml` | Same design for dbdiagram.io |
| `ERD_P03_v2.png/.svg/.pdf` | ERD |

## Run

```bash
createdb p03
psql -d p03 -v ON_ERROR_STOP=1 -f 01_schema.sql
psql -d p03 -v ON_ERROR_STOP=1 -f 02_roles.sql
psql -d p03 -v ON_ERROR_STOP=1 -f 03_seed.sql
psql -d p03 -f 04_security_tests.sql          # expect: PASS 65
psql -h localhost -U p03_app -d p03 -f 07_app_role_tests.sql   # expect: ALL PASS (changes are rolled back)
./05_concurrency_test.sh                        # expect: SEC-TC-303: PASS
```
With Docker Compose, mount the `.sql` files into `/docker-entrypoint-initdb.d/` (they run in name order).

For an existing database, apply each unapplied script in `migrations/` once, in filename order, using the intended database connection. The component-catalog migration is idempotent, archives the fictional starter products without deleting their document/ledger history, and creates zero-stock balances through the existing product trigger. Its `NOT VALID` receipt constraint enforces the document reference on new/updated receipts while preserving the existing legacy receipt that is missing a reference; validate that constraint after the source document is reconciled.

## Rules for the backend (important)

1. **Connect as `p03_app`**, never as `postgres`.
2. **Every business request runs in one transaction and sets the actor first:**
   ```sql
   BEGIN;
   SET LOCAL app.current_user_id = '<user_id from JWT>';
   SET LOCAL app.client_ip = '<client ip>';
   -- business statements
   COMMIT;
   ```
   Spring: execute `SELECT set_config('app.current_user_id', :uid, true)` at the start of each `@Transactional` service method (or in an aspect).
   EF Core: `await db.Database.ExecuteSqlRawAsync("SELECT set_config('app.current_user_id', {0}, true)", uid)` right after `BeginTransactionAsync()`.
3. **Never update stock directly.** Set the document to `CONFIRMED` (with `confirmed_by` = current user). Triggers check the rules, write the ledger and update the balance.
4. Never send `status`, `reviewed_by`, `confirmed_by`, `system_quantity` from client input without mapping them in the service (DTOs, no entity binding). The DB rejects spoofed values anyway (IA-IN07).
5. Log login/denied events with `SELECT fn_log_security_event('ACCESS_DENIED','DENIED', NULL, 'purchase_requests', :id, :reason, :jsonDetails)` **in a separate transaction**, so the log survives when the business transaction rolls back.
6. Map DB errors to HTTP: SQLSTATE `42501` → 403, `P0409` → 409 (invalid state, insufficient stock, over-receipt), `23505` → 409, `23514` → 400. Messages start with the rule ID (e.g. `BR-05: insufficient stock...`) so the frontend can show a clear message.
7. Enums are PostgreSQL enum types. Hibernate 6: `@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Npgsql: `dataSource.MapEnum<...>()`.

8. A business transaction without `app.current_user_id` is refused (fail closed). Only login bookkeeping on `users` may run without it. Creating users, locking them and changing roles need a Manager as actor.

## Status flows

- PR: `DRAFT → SUBMITTED → APPROVED | REJECTED`, `DRAFT|SUBMITTED → CANCELED`
- PO: `DRAFT → ORDERED → PARTIALLY_RECEIVED → RECEIVED` (receiving states set automatically), `DRAFT|ORDERED → CANCELED`
- Goods receipt / stock issue / stock adjustment: `DRAFT → CONFIRMED | CANCELED`

## IA verification queries

```sql
SELECT * FROM fn_verify_stock_ledger();              -- empty = ledger not tampered
SELECT * FROM fn_verify_audit_log();                 -- empty = audit log not tampered
SELECT * FROM v_stock_reconciliation WHERE difference <> 0;   -- empty = balance = ledger
SELECT * FROM audit_logs ORDER BY chain_seq DESC LIMIT 50;
```
