-- Profiles, social graph, privacy, and event availability foundations
-- Apply files 001 -> 002 -> 003 -> 004 in order.

-- Section: 20260420120000_event_availability_calendar_busy.sql

alter table if exists public.event_availability
  add column if not exists calendar_busy_overlaps_event boolean not null default false;

-- Section: 20260425165000_seed_searchable_profiles.sql
-- Supabase/Postgres only. Apply in the Supabase SQL editor or CLI.
-- Seeds real users/profiles so "Find People" returns non-dummy rows.
-- Idempotent: safe to run multiple times.

do $$
declare
  alex_id uuid := '7f7d6a91-0c6a-4b33-a6ef-6f778cb97801';
  sam_id uuid := 'f56d6d4d-208a-4cf8-976d-5f90a5f94652';
  river_id uuid := '11b9fbad-77e8-4b5a-9a95-26faa4ce6ec0';
begin
  -- Align auth signup trigger with current profiles schema (`name` over `full_name`).
  create or replace function public.handle_new_user()
  returns trigger
  language plpgsql
  security definer
  set search_path = public
  as $fn$
  begin
    insert into public.profiles (id, email, username, name)
    values (
      new.id,
      new.email,
      nullif(new.raw_user_meta_data ->> 'username', ''),
      coalesce(
        nullif(new.raw_user_meta_data ->> 'name', ''),
        nullif(new.raw_user_meta_data ->> 'full_name', ''),
        nullif(new.email, '')
      )
    )
    on conflict (id) do nothing;
    return new;
  end;
  $fn$;

  -- Seed auth users first (profiles.id has FK to auth.users.id).
  insert into auth.users (
    id,
    aud,
    role,
    email,
    encrypted_password,
    email_confirmed_at,
    raw_app_meta_data,
    raw_user_meta_data,
    created_at,
    updated_at
  ) values
    (
      alex_id,
      'authenticated',
      'authenticated',
      'alex.seed@spacer.app',
      crypt('SpacerSeed123!', gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"name":"Alex Seed","username":"alex_seed"}'::jsonb,
      now(),
      now()
    ),
    (
      sam_id,
      'authenticated',
      'authenticated',
      'sam.seed@spacer.app',
      crypt('SpacerSeed123!', gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"name":"Sam Seed","username":"sam_seed"}'::jsonb,
      now(),
      now()
    ),
    (
      river_id,
      'authenticated',
      'authenticated',
      'river.seed@spacer.app',
      crypt('SpacerSeed123!', gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"name":"River Seed","username":"river_seed"}'::jsonb,
      now(),
      now()
    )
  on conflict (id) do update
    set
      email = excluded.email,
      raw_user_meta_data = excluded.raw_user_meta_data,
      updated_at = now();

  -- Upsert profile details used by app search/public profile screens.
  insert into public.profiles (
    id,
    email,
    username,
    name,
    about_me,
    avatar_url
  ) values
    (
      alex_id,
      'alex.seed@spacer.app',
      'alex_seed',
      'Alex Seed',
      'Always up for coffee chats and city walks.',
      null
    ),
    (
      sam_id,
      'sam.seed@spacer.app',
      'sam_seed',
      'Sam Seed',
      'Planning game nights and casual weekend hangs.',
      null
    ),
    (
      river_id,
      'river.seed@spacer.app',
      'river_seed',
      'River Seed',
      'Looking for live music and art meetups.',
      null
    )
  on conflict (id) do update
    set
      email = excluded.email,
      username = excluded.username,
      name = excluded.name,
      about_me = excluded.about_me,
      avatar_url = excluded.avatar_url;
end $$;

-- Section: 20260425191500_social_friend_request_workflow.sql

create table if not exists public.friend_requests (
  sender_id uuid not null references auth.users(id) on delete cascade,
  receiver_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  responded_at timestamptz null,
  constraint friend_requests_sender_receiver_pk primary key (sender_id, receiver_id),
  constraint friend_requests_status_chk check (status in ('pending', 'accepted', 'declined'))
);

create index if not exists idx_friend_requests_receiver_status
  on public.friend_requests (receiver_id, status);

create index if not exists idx_friend_requests_sender_status
  on public.friend_requests (sender_id, status);

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'friend_requests_no_self_chk'
  ) then
    alter table public.friend_requests
      add constraint friend_requests_no_self_chk check (sender_id <> receiver_id);
  end if;
end $$;

create table if not exists public.user_blocks (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint user_blocks_pk primary key (blocker_id, blocked_id),
  constraint user_blocks_no_self_chk check (blocker_id <> blocked_id)
);

create index if not exists idx_user_blocks_blocked_id
  on public.user_blocks (blocked_id);

create table if not exists public.user_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reported_id uuid not null references auth.users(id) on delete cascade,
  reason text not null,
  created_at timestamptz not null default now(),
  constraint user_reports_no_self_chk check (reporter_id <> reported_id)
);

create index if not exists idx_user_reports_reported_id
  on public.user_reports (reported_id, created_at desc);

-- Section: 20260425223600_block_privacy_rls.sql

create or replace function public.is_blocked_between(user_a uuid, user_b uuid)
returns boolean
language sql
stable
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

grant execute on function public.is_blocked_between(uuid, uuid) to authenticated;

create or replace function public.event_host_id(p_event_id uuid)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select e.host_id
  from public.app_events e
  where e.id = p_event_id
  limit 1
$$;

grant execute on function public.event_host_id(uuid) to authenticated;

create or replace function public.is_event_invitee(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.event_invites ei
    where ei.event_id = p_event_id
      and ei.invitee_id = p_user_id
  )
$$;

grant execute on function public.is_event_invitee(uuid, uuid) to authenticated;

alter table if exists public.profiles enable row level security;
alter table if exists public.app_events enable row level security;
alter table if exists public.event_invites enable row level security;
alter table if exists public.friend_requests enable row level security;
alter table if exists public.user_blocks enable row level security;

drop policy if exists profiles_select_visible_non_blocked on public.profiles;
create policy profiles_select_visible_non_blocked
on public.profiles
for select
to authenticated
using (
  auth.uid() is not null
  and not public.is_blocked_between(auth.uid(), id)
);

drop policy if exists profiles_update_own_row on public.profiles;
create policy profiles_update_own_row
on public.profiles
for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

drop policy if exists app_events_select_non_blocked_access on public.app_events;
create policy app_events_select_non_blocked_access
on public.app_events
for select
to authenticated
using (
  auth.uid() is not null
  and not public.is_blocked_between(auth.uid(), host_id)
  and (
    host_id = auth.uid()
    or visibility = 'public'
    or public.is_event_invitee(app_events.id, auth.uid())
  )
);

drop policy if exists app_events_insert_host_only on public.app_events;
create policy app_events_insert_host_only
on public.app_events
for insert
to authenticated
with check (
  auth.uid() = host_id
);

drop policy if exists app_events_update_host_only on public.app_events;
create policy app_events_update_host_only
on public.app_events
for update
to authenticated
using (auth.uid() = host_id)
with check (auth.uid() = host_id);

drop policy if exists app_events_delete_host_only on public.app_events;
create policy app_events_delete_host_only
on public.app_events
for delete
to authenticated
using (auth.uid() = host_id);

drop policy if exists event_invites_select_host_or_invitee_non_blocked on public.event_invites;
create policy event_invites_select_host_or_invitee_non_blocked
on public.event_invites
for select
to authenticated
using (
  auth.uid() is not null
  and (
    invitee_id = auth.uid()
    or public.event_host_id(event_invites.event_id) = auth.uid()
  )
  and not public.is_blocked_between(public.event_host_id(event_invites.event_id), invitee_id)
);

drop policy if exists event_invites_insert_host_only_non_blocked on public.event_invites;
create policy event_invites_insert_host_only_non_blocked
on public.event_invites
for insert
to authenticated
with check (
  invitee_id <> auth.uid()
  and public.event_host_id(event_invites.event_id) = auth.uid()
  and not public.is_blocked_between(auth.uid(), event_invites.invitee_id)
);

drop policy if exists event_invites_update_invitee_only_non_blocked on public.event_invites;
create policy event_invites_update_invitee_only_non_blocked
on public.event_invites
for update
to authenticated
using (
  invitee_id = auth.uid()
  and not public.is_blocked_between(auth.uid(), public.event_host_id(event_invites.event_id))
)
with check (
  invitee_id = auth.uid()
  and not public.is_blocked_between(auth.uid(), public.event_host_id(event_invites.event_id))
);

drop policy if exists event_invites_delete_host_or_invitee on public.event_invites;
create policy event_invites_delete_host_or_invitee
on public.event_invites
for delete
to authenticated
using (
  invitee_id = auth.uid()
  or public.event_host_id(event_invites.event_id) = auth.uid()
);

drop policy if exists friend_requests_select_non_blocked_participants on public.friend_requests;
create policy friend_requests_select_non_blocked_participants
on public.friend_requests
for select
to authenticated
using (
  auth.uid() in (sender_id, receiver_id)
  and not public.is_blocked_between(sender_id, receiver_id)
);

drop policy if exists friend_requests_insert_sender_non_blocked on public.friend_requests;
create policy friend_requests_insert_sender_non_blocked
on public.friend_requests
for insert
to authenticated
with check (
  sender_id = auth.uid()
  and sender_id <> receiver_id
  and not public.is_blocked_between(sender_id, receiver_id)
);

drop policy if exists friend_requests_update_receiver_non_blocked on public.friend_requests;
create policy friend_requests_update_receiver_non_blocked
on public.friend_requests
for update
to authenticated
using (
  receiver_id = auth.uid()
  and not public.is_blocked_between(sender_id, receiver_id)
)
with check (
  receiver_id = auth.uid()
  and not public.is_blocked_between(sender_id, receiver_id)
);

drop policy if exists friend_requests_delete_participants_non_blocked on public.friend_requests;
create policy friend_requests_delete_participants_non_blocked
on public.friend_requests
for delete
to authenticated
using (
  auth.uid() in (sender_id, receiver_id)
  and not public.is_blocked_between(sender_id, receiver_id)
);

drop policy if exists user_blocks_select_own_blocks on public.user_blocks;
create policy user_blocks_select_own_blocks
on public.user_blocks
for select
to authenticated
using (blocker_id = auth.uid());

drop policy if exists user_blocks_insert_own_blocks on public.user_blocks;
create policy user_blocks_insert_own_blocks
on public.user_blocks
for insert
to authenticated
with check (
  blocker_id = auth.uid()
  and blocker_id <> blocked_id
);

drop policy if exists user_blocks_delete_own_blocks on public.user_blocks;
create policy user_blocks_delete_own_blocks
on public.user_blocks
for delete
to authenticated
using (blocker_id = auth.uid());
