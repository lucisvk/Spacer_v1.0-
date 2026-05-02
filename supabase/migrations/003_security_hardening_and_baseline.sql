-- Security hardening, RLS baseline, and policy consolidation
-- Apply files 001 -> 002 -> 003 -> 004 in order.

-- Section: 20260429102000_security_hardening_linter_fixes.sql

create or replace function public.is_blocked_between(user_a uuid, user_b uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.user_blocks ub
    where
      (ub.blocker_id = user_a and ub.blocked_id = user_b)
      or
      (ub.blocker_id = user_b and ub.blocked_id = user_a)
  );
$$;

create or replace function public.is_event_member_active(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.event_members m
    where m.event_id = p_event_id
      and m.user_id = p_user_id
      and m.status = 'active'
  );
$$;

create or replace function public.can_access_event_chat(p_room_id uuid, p_user_id uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.event_chat_rooms r
    join public.app_events e on e.id = r.event_id
    where r.id = p_room_id
      and (
        e.host_id = p_user_id
        or public.is_event_member_active(r.event_id, p_user_id)
      )
      and not public.is_blocked_between(p_user_id, e.host_id)
  );
$$;

create or replace function public.can_post_event_chat(p_room_id uuid, p_user_id uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.event_chat_rooms r
    join public.app_events e on e.id = r.event_id
    where r.id = p_room_id
      and not public.is_blocked_between(p_user_id, e.host_id)
      and (
        (r.chat_mode = 'all_members' and (e.host_id = p_user_id or public.is_event_member_active(r.event_id, p_user_id)))
        or (r.chat_mode = 'host_cohosts_only' and public.is_event_host_or_cohost(r.event_id, p_user_id))
      )
  );
$$;

create or replace function public.is_dm_participant(p_conversation_id uuid, p_user_id uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.dm_conversations c
    where c.id = p_conversation_id
      and p_user_id in (c.user_a, c.user_b)
  );
$$;

create or replace function public.can_create_dm_between(p_user_a uuid, p_user_b uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select
    p_user_a <> p_user_b
    and not public.is_blocked_between(p_user_a, p_user_b)
    and (
      exists (
        select 1
        from public.friend_requests f
        where f.status = 'accepted'
          and (
            (f.sender_id = p_user_a and f.receiver_id = p_user_b)
            or (f.sender_id = p_user_b and f.receiver_id = p_user_a)
          )
      )
      or exists (
        select 1
        from public.event_members ma
        join public.event_members mb on mb.event_id = ma.event_id
        where ma.user_id = p_user_a
          and mb.user_id = p_user_b
          and ma.status = 'active'
          and mb.status = 'active'
      )
    );
$$;

create or replace function public.touch_event_live_status_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop policy if exists "notifications insert authenticated" on public.user_notifications;
drop policy if exists user_notifications_insert_authenticated on public.user_notifications;

create policy user_notifications_insert_own
on public.user_notifications
for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "profile photos public read" on storage.objects;

do $$
declare
  fn text;
begin
  for fn in
    select unnest(array[
      'public.broadcast_dm_message_changes()',
      'public.broadcast_event_chat_message_changes()',
      'public.handle_new_user()'
    ])
  loop
    if to_regprocedure(fn) is not null then
      execute format('revoke execute on function %s from public, anon, authenticated', fn);
    end if;
  end loop;

  for fn in
    select unnest(array[
      'public.cancel_hosted_event(uuid)',
      'public.event_host_id(uuid)',
      'public.is_app_event_host(uuid,uuid)',
      'public.is_event_invitee(uuid,uuid)',
      'public.schedule_conflicts_for_friends(timestamptz,timestamptz,uuid[],text)',
      'public.user_is_invited_to_event(uuid,uuid)'
    ])
  loop
    if to_regprocedure(fn) is not null then
      execute format('revoke execute on function %s from public, anon', fn);
      execute format('grant execute on function %s to authenticated', fn);
    end if;
  end loop;
end;
$$;

-- Section: 20260429103500_reduce_authenticated_definer_surface.sql

do $$
declare
  fn text;
begin
  for fn in
    select unnest(array[
      'public.is_app_event_host(uuid,uuid)',
      'public.user_is_invited_to_event(uuid,uuid)'
    ])
  loop
    if to_regprocedure(fn) is not null then
      execute format('revoke execute on function %s from authenticated, anon, public', fn);
    end if;
  end loop;

  for fn in
    select unnest(array[
      'public.cancel_hosted_event(uuid)',
      'public.schedule_conflicts_for_friends(timestamptz,timestamptz,uuid[],text)'
    ])
  loop
    if to_regprocedure(fn) is not null then
      execute format('revoke execute on function %s from anon, public', fn);
      execute format('grant execute on function %s to authenticated', fn);
    end if;
  end loop;
end;
$$;

-- Section: 20260429104500_safe_schema_cleanup_and_guards.sql

do $$
declare
  has_table boolean;
  row_count bigint;
  dependent_count bigint;
begin
  select to_regclass('public.event_location_open_hours') is not null into has_table;

  if has_table then
    execute 'select count(*) from public.event_location_open_hours' into row_count;

    select count(*)
      into dependent_count
    from pg_depend d
    join pg_class c on c.oid = d.refobjid
    where c.relname = 'event_location_open_hours'
      and c.relnamespace = 'public'::regnamespace
      and d.deptype in ('n', 'a', 'i');

    if row_count = 0 then
      begin
        execute 'drop policy if exists event_location_open_hours_select_visible on public.event_location_open_hours';
        execute 'drop policy if exists event_location_open_hours_manage_host on public.event_location_open_hours';
        execute 'drop table if exists public.event_location_open_hours';
        raise notice 'Dropped unused table public.event_location_open_hours (empty).';
      exception when others then
        raise notice 'Skipped drop for public.event_location_open_hours due to dependency/permission issue: %', sqlerrm;
      end;
    else
      raise notice 'Retained public.event_location_open_hours because it contains % row(s).', row_count;
    end if;
  else
    raise notice 'public.event_location_open_hours does not exist; nothing to clean.';
  end if;
end;
$$;

do $$
declare
  missing text := '';
begin
  if to_regclass('public.profiles') is null then
    missing := missing || ' profiles';
  end if;
  if to_regclass('public.app_events') is null then
    missing := missing || ' app_events';
  end if;
  if to_regclass('public.event_invites') is null then
    missing := missing || ' event_invites';
  end if;
  if to_regclass('public.event_availability') is null then
    missing := missing || ' event_availability';
  end if;
  if to_regclass('public.public_event_invites') is null then
    missing := missing || ' public_event_invites';
  end if;
  if to_regclass('public.account_deletion_requests') is null then
    missing := missing || ' account_deletion_requests';
  end if;

  if missing <> '' then
    raise notice 'Schema drift detected. Missing core table(s):% . Backfill from baseline migration/export before further cleanup.', missing;
  else
    raise notice 'Core table presence check passed.';
  end if;
end;
$$;

-- Section: 20260429110000_baseline_core_schema_backfill.sql

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text null,
  username text null,
  name text null,
  avatar_url text null,
  about_me text null,
  presence_status text null default 'offline',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists idx_profiles_username_unique
  on public.profiles (lower(username))
  where username is not null;

create table if not exists public.app_events (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text null,
  host_id uuid not null references auth.users(id) on delete cascade,
  starts_at timestamptz not null,
  ends_at timestamptz null,
  location text null,
  visibility text not null default 'public' check (visibility in ('public', 'invite_only')),
  category text null,
  max_attendees integer null check (max_attendees is null or max_attendees > 0),
  bring_items text null,
  venue_lat double precision null,
  venue_lng double precision null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_app_events_host_id on public.app_events(host_id);
create index if not exists idx_app_events_starts_at_desc on public.app_events(starts_at desc);

create table if not exists public.event_invites (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.app_events(id) on delete cascade,
  invitee_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (event_id, invitee_id)
);

create index if not exists idx_event_invites_invitee_status
  on public.event_invites(invitee_id, status);
create index if not exists idx_event_invites_event_id
  on public.event_invites(event_id);

create table if not exists public.event_availability (
  event_id uuid not null references public.app_events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  preset_slots text not null default '',
  notes text null,
  calendar_busy_overlaps_event boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (event_id, user_id)
);

create index if not exists idx_event_availability_event_id on public.event_availability(event_id);
create index if not exists idx_event_availability_user_id on public.event_availability(user_id);

create table if not exists public.public_event_invites (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.app_events(id) on delete cascade unique,
  created_at timestamptz not null default now()
);

create table if not exists public.account_deletion_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  reason text null,
  status text not null default 'pending',
  created_at timestamptz not null default now()
);

create index if not exists idx_account_deletion_requests_user_id
  on public.account_deletion_requests(user_id, created_at desc);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_profiles_touch_updated_at on public.profiles;
create trigger trg_profiles_touch_updated_at
before update on public.profiles
for each row execute procedure public.touch_updated_at();

drop trigger if exists trg_app_events_touch_updated_at on public.app_events;
create trigger trg_app_events_touch_updated_at
before update on public.app_events
for each row execute procedure public.touch_updated_at();

drop trigger if exists trg_event_invites_touch_updated_at on public.event_invites;
create trigger trg_event_invites_touch_updated_at
before update on public.event_invites
for each row execute procedure public.touch_updated_at();

drop trigger if exists trg_event_availability_touch_updated_at on public.event_availability;
create trigger trg_event_availability_touch_updated_at
before update on public.event_availability
for each row execute procedure public.touch_updated_at();

create or replace function public.cancel_hosted_event(p_event_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  if not exists (
    select 1
    from public.app_events e
    where e.id = p_event_id
      and e.host_id = v_uid
  ) then
    raise exception 'Only host can cancel this event';
  end if;

  delete from public.app_events
  where id = p_event_id
    and host_id = v_uid;
end;
$$;

grant execute on function public.cancel_hosted_event(uuid) to authenticated;

alter table public.profiles enable row level security;
alter table public.app_events enable row level security;
alter table public.event_invites enable row level security;
alter table public.event_availability enable row level security;
alter table public.public_event_invites enable row level security;
alter table public.account_deletion_requests enable row level security;

drop policy if exists public_event_invites_select_baseline on public.public_event_invites;
create policy public_event_invites_select_baseline
on public.public_event_invites
for select
to authenticated
using (true);

drop policy if exists public_event_invites_manage_host_baseline on public.public_event_invites;
create policy public_event_invites_manage_host_baseline
on public.public_event_invites
for all
to authenticated
using (
  exists (
    select 1
    from public.app_events e
    where e.id = public_event_invites.event_id
      and e.host_id = auth.uid()
  )
)
with check (
  exists (
    select 1
    from public.app_events e
    where e.id = public_event_invites.event_id
      and e.host_id = auth.uid()
  )
);

drop policy if exists event_availability_select_member_baseline on public.event_availability;
create policy event_availability_select_member_baseline
on public.event_availability
for select
to authenticated
using (
  user_id = auth.uid()
  or exists (
    select 1
    from public.event_members m
    where m.event_id = event_availability.event_id
      and m.user_id = auth.uid()
      and m.status = 'active'
      and m.role in ('host', 'cohost')
  )
);

drop policy if exists event_availability_upsert_self_baseline on public.event_availability;
create policy event_availability_upsert_self_baseline
on public.event_availability
for all
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists account_deletion_requests_insert_self_baseline on public.account_deletion_requests;
create policy account_deletion_requests_insert_self_baseline
on public.account_deletion_requests
for insert
to authenticated
with check (user_id = auth.uid());

drop policy if exists account_deletion_requests_select_self_baseline on public.account_deletion_requests;
create policy account_deletion_requests_select_self_baseline
on public.account_deletion_requests
for select
to authenticated
using (user_id = auth.uid());

-- Section: 20260429111500_final_linter_search_path_sync.sql

create or replace function public.is_event_host_or_cohost(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
security invoker
set search_path = public
as $$
  select exists (
    select 1
    from public.app_events e
    where e.id = p_event_id
      and e.host_id = p_user_id
  )
  or exists (
    select 1
    from public.event_members m
    where m.event_id = p_event_id
      and m.user_id = p_user_id
      and m.status = 'active'
      and m.role in ('host', 'cohost')
  );
$$;

grant execute on function public.is_event_host_or_cohost(uuid, uuid) to authenticated;
