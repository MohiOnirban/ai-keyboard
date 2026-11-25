begin;

create schema if not exists keyboard_private;
revoke all on schema keyboard_private from public;

create table if not exists keyboard_private.ledger_lock (
    singleton boolean primary key default true check (singleton)
);

insert into keyboard_private.ledger_lock default values on conflict do nothing;

create table if not exists keyboard_private.sessions (
    id uuid primary key,
    token_digest text unique not null,
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create table if not exists keyboard_private.rate_windows (
    scope text not null,
    key text not null,
    minute_start timestamptz not null,
    count integer not null check (count > 0),
    primary key (scope, key, minute_start)
);

create table if not exists keyboard_private.generations (
    session_id uuid not null references keyboard_private.sessions (id),
    request_id uuid not null,
    started_at timestamptz not null,
    lease_until timestamptz not null,
    state text not null check (state in ('pending', 'succeeded', 'failed')),
    reserved_micros bigint not null check (reserved_micros >= 0),
    billed_micros bigint check (billed_micros >= 0),
    input_tokens integer check (input_tokens >= 0),
    output_tokens integer check (output_tokens >= 0),
    latency_ms integer check (latency_ms >= 0),
    primary key (session_id, request_id),
    check (lease_until > started_at),
    check ((state = 'pending') = (latency_ms is null)),
    check (state != 'pending' or billed_micros is null)
);

create index if not exists generations_started_idx
    on keyboard_private.generations (started_at);

create index if not exists generations_session_started_idx
    on keyboard_private.generations (session_id, started_at);

create index if not exists generations_pending_idx
    on keyboard_private.generations (lease_until, session_id)
    where state = 'pending';

create index if not exists rate_windows_started_idx
    on keyboard_private.rate_windows (minute_start);

alter table keyboard_private.ledger_lock enable row level security;
alter table keyboard_private.sessions enable row level security;
alter table keyboard_private.rate_windows enable row level security;
alter table keyboard_private.generations enable row level security;

revoke all on all tables in schema keyboard_private from public;

do $$
declare
    client_role name;
begin
    for client_role in
        select rolname from pg_roles where rolname in ('anon', 'authenticated', 'authenticator', 'service_role')
    loop
        execute format('revoke all on schema keyboard_private from %I', client_role);
        execute format('revoke all on all tables in schema keyboard_private from %I', client_role);
    end loop;

    if not exists (select from pg_roles where rolname = 'keyboard_api') then
        create role keyboard_api nologin;
    end if;
end
$$;

grant usage on schema keyboard_private to keyboard_api;
grant select, update on keyboard_private.ledger_lock to keyboard_api;
grant select, insert, delete, update (revoked_at) on keyboard_private.sessions to keyboard_api;
grant select, insert, delete, update (count) on keyboard_private.rate_windows to keyboard_api;
grant select, insert, delete, update (
    state, billed_micros, input_tokens, output_tokens, latency_ms
) on keyboard_private.generations to keyboard_api;

-- This isolated role exists for the Fastify service. No policies grant app-client access.
drop policy if exists backend_access on keyboard_private.ledger_lock;
create policy backend_access on keyboard_private.ledger_lock to keyboard_api using (true) with check (true);
drop policy if exists backend_access on keyboard_private.sessions;
create policy backend_access on keyboard_private.sessions to keyboard_api using (true) with check (true);
drop policy if exists backend_access on keyboard_private.rate_windows;
create policy backend_access on keyboard_private.rate_windows to keyboard_api using (true) with check (true);
drop policy if exists backend_access on keyboard_private.generations;
create policy backend_access on keyboard_private.generations to keyboard_api using (true) with check (true);

commit;
