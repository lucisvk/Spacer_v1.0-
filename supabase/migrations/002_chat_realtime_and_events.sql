-- Event chat, DM threads, realtime, and notifications
-- Apply files 001 -> 002 -> 003 -> 004 in order.

-- Section: 20260426081000_profile_presence_status.sql

alter table if exists public.profiles
  add column if not exists presence_status text not null default 'offline',
  add column if not exists presence_updated_at timestamptz not null default now();

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'profiles_presence_status_chk'
  ) then
    alter table public.profiles
      add constraint profiles_presence_status_chk
      check (presence_status in ('online', 'busy', 'inactive', 'offline'));
  end if;
end $$;

update public.profiles
set presence_status = 'offline'
where presence_status is null;

-- Section: 20260426100000_chat_dm_cohost.sql

alter table if exists public.app_events
  add column if not exists bring_items text null;

create table if not exists public.event_members (
  event_id uuid not null references public.app_events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'attendee',
  status text not null default 'active',
  added_by uuid null references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint event_members_pk primary key (event_id, user_id),
  constraint event_members_role_chk check (role in ('host', 'cohost', 'attendee')),
  constraint event_members_status_chk check (status in ('active', 'removed'))
);

create index if not exists idx_event_members_user_role_status
  on public.event_members (user_id, role, status);

create index if not exists idx_event_members_event_role_active
  on public.event_members (event_id, role)
  where status = 'active';

insert into public.event_members (event_id, user_id, role, status, added_by)
select e.id, e.host_id, 'host', 'active', e.host_id
from public.app_events e
on conflict (event_id, user_id) do update
set role = excluded.role,
    status = excluded.status,
    added_by = excluded.added_by;

insert into public.event_members (event_id, user_id, role, status, added_by)
select i.event_id, i.invitee_id, 'attendee', 'active', e.host_id
from public.event_invites i
join public.app_events e on e.id = i.event_id
where i.status = 'accepted'
on conflict (event_id, user_id) do update
set role = excluded.role,
    status = excluded.status;

create table if not exists public.event_chat_rooms (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null unique references public.app_events(id) on delete cascade,
  chat_mode text not null default 'all_members',
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint event_chat_rooms_mode_chk check (chat_mode in ('all_members', 'host_cohosts_only', 'disabled'))
);

create table if not exists public.event_chat_messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.event_chat_rooms(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz null
);

create index if not exists idx_event_chat_messages_room_created
  on public.event_chat_messages (room_id, created_at desc);

create table if not exists public.dm_conversations (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references auth.users(id) on delete cascade,
  user_b uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  last_message_at timestamptz null,
  constraint dm_conversations_no_self_chk check (user_a <> user_b)
);

create unique index if not exists dm_conversations_user_pair_uniq
  on public.dm_conversations (least(user_a, user_b), greatest(user_a, user_b));

create table if not exists public.dm_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.dm_conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz null
);

create index if not exists idx_dm_messages_conversation_created
  on public.dm_messages (conversation_id, created_at desc);

create or replace function public.is_event_host_or_cohost(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.event_members m
    where m.event_id = p_event_id
      and m.user_id = p_user_id
      and m.status = 'active'
      and m.role in ('host', 'cohost')
  );
$$;

create or replace function public.is_event_member_active(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
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

grant execute on function public.is_event_host_or_cohost(uuid, uuid) to authenticated;
grant execute on function public.is_event_member_active(uuid, uuid) to authenticated;
grant execute on function public.can_access_event_chat(uuid, uuid) to authenticated;
grant execute on function public.can_post_event_chat(uuid, uuid) to authenticated;
grant execute on function public.is_dm_participant(uuid, uuid) to authenticated;
grant execute on function public.can_create_dm_between(uuid, uuid) to authenticated;

alter table if exists public.event_members enable row level security;
alter table if exists public.event_chat_rooms enable row level security;
alter table if exists public.event_chat_messages enable row level security;
alter table if exists public.dm_conversations enable row level security;
alter table if exists public.dm_messages enable row level security;

drop policy if exists event_members_select_visible on public.event_members;
create policy event_members_select_visible
on public.event_members
for select
to authenticated
using (
  auth.uid() = user_id
  or public.is_event_host_or_cohost(event_id, auth.uid())
);

drop policy if exists event_members_manage_host_cohost on public.event_members;
create policy event_members_manage_host_cohost
on public.event_members
for all
to authenticated
using (public.is_event_host_or_cohost(event_id, auth.uid()))
with check (public.is_event_host_or_cohost(event_id, auth.uid()));

drop policy if exists event_chat_rooms_select_visible on public.event_chat_rooms;
create policy event_chat_rooms_select_visible
on public.event_chat_rooms
for select
to authenticated
using (public.can_access_event_chat(id, auth.uid()));

drop policy if exists event_chat_rooms_insert_host_only on public.event_chat_rooms;
create policy event_chat_rooms_insert_host_only
on public.event_chat_rooms
for insert
to authenticated
with check (public.is_event_host_or_cohost(event_id, auth.uid()) and created_by = auth.uid());

drop policy if exists event_chat_rooms_update_host_cohost on public.event_chat_rooms;
create policy event_chat_rooms_update_host_cohost
on public.event_chat_rooms
for update
to authenticated
using (public.is_event_host_or_cohost(event_id, auth.uid()))
with check (public.is_event_host_or_cohost(event_id, auth.uid()));

drop policy if exists event_chat_messages_select_visible on public.event_chat_messages;
create policy event_chat_messages_select_visible
on public.event_chat_messages
for select
to authenticated
using (public.can_access_event_chat(room_id, auth.uid()));

drop policy if exists event_chat_messages_insert_allowed on public.event_chat_messages;
create policy event_chat_messages_insert_allowed
on public.event_chat_messages
for insert
to authenticated
with check (
  sender_id = auth.uid()
  and public.can_post_event_chat(room_id, auth.uid())
);

drop policy if exists event_chat_messages_delete_owner_or_host on public.event_chat_messages;
create policy event_chat_messages_delete_owner_or_host
on public.event_chat_messages
for delete
to authenticated
using (
  sender_id = auth.uid()
  or exists (
    select 1
    from public.event_chat_rooms r
    where r.id = room_id
      and public.is_event_host_or_cohost(r.event_id, auth.uid())
  )
);

drop policy if exists dm_conversations_select_participants on public.dm_conversations;
create policy dm_conversations_select_participants
on public.dm_conversations
for select
to authenticated
using (auth.uid() in (user_a, user_b));

drop policy if exists dm_conversations_insert_allowed on public.dm_conversations;
create policy dm_conversations_insert_allowed
on public.dm_conversations
for insert
to authenticated
with check (
  auth.uid() in (user_a, user_b)
  and public.can_create_dm_between(user_a, user_b)
);

drop policy if exists dm_conversations_update_participants on public.dm_conversations;
create policy dm_conversations_update_participants
on public.dm_conversations
for update
to authenticated
using (auth.uid() in (user_a, user_b))
with check (auth.uid() in (user_a, user_b));

drop policy if exists dm_messages_select_participants on public.dm_messages;
create policy dm_messages_select_participants
on public.dm_messages
for select
to authenticated
using (public.is_dm_participant(conversation_id, auth.uid()));

drop policy if exists dm_messages_insert_participants on public.dm_messages;
create policy dm_messages_insert_participants
on public.dm_messages
for insert
to authenticated
with check (
  sender_id = auth.uid()
  and public.is_dm_participant(conversation_id, auth.uid())
);

drop policy if exists dm_messages_delete_sender on public.dm_messages;
create policy dm_messages_delete_sender
on public.dm_messages
for delete
to authenticated
using (sender_id = auth.uid());

-- Section: 20260426103000_realtime_broadcast_auth.sql

alter table if exists realtime.messages enable row level security;

drop policy if exists realtime_messages_select_chat_scoped on realtime.messages;
create policy realtime_messages_select_chat_scoped
on realtime.messages
for select
to authenticated
using (
  (
    split_part(realtime.topic(), ':', 1) = 'event_chat'
    and exists (
      select 1
      from public.event_chat_rooms r
      where r.id = split_part(realtime.topic(), ':', 2)::uuid
        and public.can_access_event_chat(r.id, auth.uid())
    )
  )
  or
  (
    split_part(realtime.topic(), ':', 1) = 'dm_chat'
    and public.is_dm_participant(split_part(realtime.topic(), ':', 2)::uuid, auth.uid())
  )
);

create or replace function public.broadcast_event_chat_message_changes()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  topic text;
begin
  topic := 'event_chat:' || coalesce(new.room_id, old.room_id)::text;
  perform realtime.broadcast_changes(
    topic,
    TG_OP,
    TG_OP,
    TG_TABLE_NAME,
    TG_TABLE_SCHEMA,
    NEW,
    OLD
  );
  return coalesce(NEW, OLD);
end;
$$;

drop trigger if exists trg_broadcast_event_chat_messages on public.event_chat_messages;
create trigger trg_broadcast_event_chat_messages
after insert or update or delete on public.event_chat_messages
for each row
execute function public.broadcast_event_chat_message_changes();

create or replace function public.broadcast_dm_message_changes()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  topic text;
begin
  topic := 'dm_chat:' || coalesce(new.conversation_id, old.conversation_id)::text;
  perform realtime.broadcast_changes(
    topic,
    TG_OP,
    TG_OP,
    TG_TABLE_NAME,
    TG_TABLE_SCHEMA,
    NEW,
    OLD
  );
  return coalesce(NEW, OLD);
end;
$$;

drop trigger if exists trg_broadcast_dm_messages on public.dm_messages;
create trigger trg_broadcast_dm_messages
after insert or update or delete on public.dm_messages
for each row
execute function public.broadcast_dm_message_changes();

-- Section: 20260426113000_event_bring_item_claims.sql

create table if not exists public.event_bring_item_claims (
  event_id uuid not null references public.app_events(id) on delete cascade,
  item_key text not null,
  item_label text not null,
  claimed_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint event_bring_item_claims_pk primary key (event_id, item_key),
  constraint event_bring_item_claims_item_key_chk check (length(trim(item_key)) > 0),
  constraint event_bring_item_claims_item_label_chk check (length(trim(item_label)) > 0)
);

create index if not exists idx_event_bring_item_claims_event
  on public.event_bring_item_claims (event_id);

create index if not exists idx_event_bring_item_claims_claimed_by
  on public.event_bring_item_claims (claimed_by);

alter table if exists public.event_bring_item_claims enable row level security;

drop policy if exists event_bring_item_claims_select_visible on public.event_bring_item_claims;
create policy event_bring_item_claims_select_visible
on public.event_bring_item_claims
for select
to authenticated
using (
  public.is_event_member_active(event_id, auth.uid())
  or public.is_event_host_or_cohost(event_id, auth.uid())
);

drop policy if exists event_bring_item_claims_insert_member on public.event_bring_item_claims;
create policy event_bring_item_claims_insert_member
on public.event_bring_item_claims
for insert
to authenticated
with check (
  claimed_by = auth.uid()
  and (
    public.is_event_member_active(event_id, auth.uid())
    or public.is_event_host_or_cohost(event_id, auth.uid())
  )
);

drop policy if exists event_bring_item_claims_delete_owner_or_host on public.event_bring_item_claims;
create policy event_bring_item_claims_delete_owner_or_host
on public.event_bring_item_claims
for delete
to authenticated
using (
  claimed_by = auth.uid()
  or public.is_event_host_or_cohost(event_id, auth.uid())
);

-- Section: 20260426150000_realtime_topic_uuid_guard.sql

drop policy if exists realtime_messages_select_chat_scoped on realtime.messages;

create policy realtime_messages_select_chat_scoped
on realtime.messages
for select
to authenticated
using (
  (
    split_part(realtime.topic(), ':', 1) = 'event_chat'
    and split_part(realtime.topic(), ':', 2) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    and exists (
      select 1
      from public.event_chat_rooms r
      where r.id = split_part(realtime.topic(), ':', 2)::uuid
        and public.can_access_event_chat(r.id, auth.uid())
    )
  )
  or
  (
    split_part(realtime.topic(), ':', 1) = 'dm_chat'
    and split_part(realtime.topic(), ':', 2) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    and public.is_dm_participant(split_part(realtime.topic(), ':', 2)::uuid, auth.uid())
  )
);

-- Section: 20260426161000_user_notifications_rls.sql

create table if not exists public.user_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  body text not null,
  created_at timestamptz not null default now(),
  read_at timestamptz null
);

create index if not exists idx_user_notifications_user_created
  on public.user_notifications (user_id, created_at desc);

alter table public.user_notifications enable row level security;

drop policy if exists user_notifications_select_own on public.user_notifications;
create policy user_notifications_select_own
on public.user_notifications
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_notifications_update_own on public.user_notifications;
create policy user_notifications_update_own
on public.user_notifications
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists user_notifications_insert_authenticated on public.user_notifications;
create policy user_notifications_insert_authenticated
on public.user_notifications
for insert
to authenticated
with check (true);

-- Section: 20260426170000_user_availability_and_location_hours.sql

create table if not exists public.user_availability_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  calendar_provider text null,
  calendar_connected boolean not null default false,
  show_to_friends_only boolean not null default true,
  auto_decline_conflicts boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.user_weekly_availability_windows (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  day_of_week int not null check (day_of_week between 1 and 7),
  starts_at time not null,
  ends_at time not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_user_weekly_windows_user_day
  on public.user_weekly_availability_windows(user_id, day_of_week);

create table if not exists public.user_specific_availability (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  is_available boolean not null default true,
  note text null,
  created_at timestamptz not null default now()
);

create index if not exists idx_user_specific_availability_user_starts
  on public.user_specific_availability(user_id, starts_at);

create table if not exists public.event_location_open_hours (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.app_events(id) on delete cascade,
  day_of_week int not null check (day_of_week between 1 and 7),
  opens_at time not null,
  closes_at time not null,
  is_closed boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_event_location_open_hours_event_day
  on public.event_location_open_hours(event_id, day_of_week);

alter table public.user_availability_preferences enable row level security;
alter table public.user_weekly_availability_windows enable row level security;
alter table public.user_specific_availability enable row level security;
alter table public.event_location_open_hours enable row level security;

drop policy if exists user_availability_preferences_select_own on public.user_availability_preferences;
create policy user_availability_preferences_select_own
on public.user_availability_preferences
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_availability_preferences_upsert_own on public.user_availability_preferences;
create policy user_availability_preferences_upsert_own
on public.user_availability_preferences
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists user_weekly_windows_select_own on public.user_weekly_availability_windows;
create policy user_weekly_windows_select_own
on public.user_weekly_availability_windows
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_weekly_windows_manage_own on public.user_weekly_availability_windows;
create policy user_weekly_windows_manage_own
on public.user_weekly_availability_windows
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists user_specific_availability_select_own on public.user_specific_availability;
create policy user_specific_availability_select_own
on public.user_specific_availability
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_specific_availability_manage_own on public.user_specific_availability;
create policy user_specific_availability_manage_own
on public.user_specific_availability
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists event_location_open_hours_select_visible on public.event_location_open_hours;
create policy event_location_open_hours_select_visible
on public.event_location_open_hours
for select
to authenticated
using (
  exists (
    select 1
    from public.app_events ev
    where ev.id = event_location_open_hours.event_id
  )
);

drop policy if exists event_location_open_hours_manage_host on public.event_location_open_hours;
create policy event_location_open_hours_manage_host
on public.event_location_open_hours
for all
to authenticated
using (
  exists (
    select 1
    from public.app_events ev
    where ev.id = event_location_open_hours.event_id
      and ev.host_id = auth.uid()
  )
)
with check (
  exists (
    select 1
    from public.app_events ev
    where ev.id = event_location_open_hours.event_id
      and ev.host_id = auth.uid()
  )
);

-- Section: 20260427103000_fix_host_permissions_and_schedule_conflicts.sql

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

create or replace function public.schedule_conflicts_for_friends(
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_friend_ids uuid[],
  p_tz text
)
returns table(user_id uuid, conflict_reason text)
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  uid uuid;
  v_tz text;
  v_local_start timestamp without time zone;
  v_local_end timestamp without time zone;
  v_dow int;
  v_start_time time;
  v_end_time time;
  busy_overlap boolean;
  weekly_overlap boolean;
  has_weekly_for_day boolean;
  range_end timestamptz;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  v_tz := coalesce(nullif(trim(p_tz), ''), 'UTC');

  if p_friend_ids is null then
    return;
  end if;

  range_end := coalesce(p_ends_at, p_starts_at + interval '1 hour');

  foreach uid in array p_friend_ids
  loop
    if uid is null then
      continue;
    end if;

    if not exists (
      select 1
      from public.friend_requests fr
      where fr.status = 'accepted'
        and (
          (fr.sender_id = auth.uid() and fr.receiver_id = uid)
          or (fr.receiver_id = auth.uid() and fr.sender_id = uid)
        )
    ) then
      continue;
    end if;

    select exists (
      select 1
      from public.user_specific_availability sa
      where sa.user_id = uid
        and sa.is_available = false
        and tstzrange(sa.starts_at, sa.ends_at, '[)') &&
            tstzrange(p_starts_at, range_end, '[)')
    ) into busy_overlap;

    if busy_overlap then
      user_id := uid;
      conflict_reason := 'Marked busy on their calendar';
      return next;
      continue;
    end if;

    v_local_start := p_starts_at at time zone v_tz;
    v_local_end := range_end at time zone v_tz;

    if (v_local_end::date <> v_local_start::date) then
      continue;
    end if;

    v_dow := extract(isodow from v_local_start)::int;
    v_start_time := v_local_start::time;
    v_end_time := v_local_end::time;

    select exists (
      select 1
      from public.user_weekly_availability_windows w
      where w.user_id = uid
        and w.day_of_week = v_dow
    ) into has_weekly_for_day;

    if not has_weekly_for_day then
      continue;
    end if;

    select exists (
      select 1
      from public.user_weekly_availability_windows w
      where w.user_id = uid
        and w.day_of_week = v_dow
        and v_end_time > w.starts_at
        and v_start_time < w.ends_at
    ) into weekly_overlap;

    if not weekly_overlap then
      user_id := uid;
      conflict_reason := 'Outside their usual weekly availability';
      return next;
    end if;

  end loop;
end;
$$;

grant execute on function public.schedule_conflicts_for_friends(timestamptz, timestamptz, uuid[], text) to authenticated;

drop policy if exists event_chat_messages_insert_allowed on public.event_chat_messages;

create policy event_chat_messages_insert_allowed on public.event_chat_messages
for insert to authenticated
with check (
  sender_id = auth.uid()
  and (
    public.can_post_event_chat(room_id, auth.uid())
    or (
      exists (
        select 1
        from public.event_chat_rooms r
        join public.app_events e on e.id = r.event_id
        where r.id = room_id
          and e.host_id = auth.uid()
      )
      and body like '[Schedule] %'
    )
  )
);

-- Section: 20260427232000_public_event_capacity_limit.sql

alter table if exists public.app_events
  add column if not exists max_attendees integer null
  check (max_attendees is null or max_attendees > 0);

-- Section: 20260428230000_event_live_tracking.sql

alter table if exists public.app_events
  add column if not exists venue_lat double precision null,
  add column if not exists venue_lng double precision null;

create table if not exists public.event_live_status (
  event_id uuid not null references public.app_events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  lat double precision null,
  lng double precision null,
  sharing_enabled boolean not null default true,
  arrived_at timestamptz null,
  updated_at timestamptz not null default now(),
  primary key (event_id, user_id)
);

create index if not exists event_live_status_event_idx
  on public.event_live_status (event_id);

create index if not exists event_live_status_event_updated_idx
  on public.event_live_status (event_id, updated_at desc);

alter table public.event_live_status enable row level security;

drop policy if exists event_live_status_select_members on public.event_live_status;
create policy event_live_status_select_members on public.event_live_status
for select to authenticated
using (
  exists (
    select 1
    from public.event_members m
    where m.event_id = event_live_status.event_id
      and m.user_id = auth.uid()
      and m.status = 'active'
  )
);

drop policy if exists event_live_status_write_self on public.event_live_status;
create policy event_live_status_write_self on public.event_live_status
for all to authenticated
using (
  user_id = auth.uid()
  and exists (
    select 1
    from public.event_members m
    where m.event_id = event_live_status.event_id
      and m.user_id = auth.uid()
      and m.status = 'active'
  )
)
with check (
  user_id = auth.uid()
  and exists (
    select 1
    from public.event_members m
    where m.event_id = event_live_status.event_id
      and m.user_id = auth.uid()
      and m.status = 'active'
  )
);

create or replace function public.touch_event_live_status_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_event_live_status_updated_at on public.event_live_status;
create trigger trg_event_live_status_updated_at
before update on public.event_live_status
for each row execute procedure public.touch_event_live_status_updated_at();
