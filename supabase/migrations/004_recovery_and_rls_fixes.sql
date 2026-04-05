-- RLS recovery patches and policy fixes
-- Apply files 001 -> 002 -> 003 -> 004 in order.

-- Section: 20260429130000_full_system_recovery_baseline.sql

-- 0) Schema usage and helper for safe per-table operations.

grant usage on schema public to authenticated;

create or replace function public._spacer_table_exists(p_table text)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1
    from information_schema.tables
    where table_schema = 'public'
      and table_name = p_table
  );
$$;

-- 1) Profiles: signed-in users can view profiles; users edit own row.

do $$
begin
  if public._spacer_table_exists('profiles') then
    execute 'alter table public.profiles enable row level security';
    execute 'grant select, insert, update on table public.profiles to authenticated';
    execute 'grant select on table public.profiles to anon';

    execute 'drop policy if exists profiles_select_baseline_recovery on public.profiles';
    execute $p$
      create policy profiles_select_baseline_recovery
      on public.profiles
      for select
      to authenticated
      using (auth.uid() is not null)
    $p$;

    execute 'drop policy if exists profiles_insert_self_baseline_recovery on public.profiles';
    execute $p$
      create policy profiles_insert_self_baseline_recovery
      on public.profiles
      for insert
      to authenticated
      with check (id = auth.uid())
    $p$;

    execute 'drop policy if exists profiles_update_self_baseline_recovery on public.profiles';
    execute $p$
      create policy profiles_update_self_baseline_recovery
      on public.profiles
      for update
      to authenticated
      using (id = auth.uid())
      with check (id = auth.uid())
    $p$;
  end if;
end $$;

-- 2) app_events: discoverability + host write access.
--    Read scope:
--      * host (always)
--      * any signed-in user can read public-visibility events (matches client filter)
--      * invited user (via event_invites)
--      * member (via event_members)

do $$
begin
  if public._spacer_table_exists('app_events') then
    execute 'alter table public.app_events enable row level security';
    execute 'grant select, insert, update, delete on table public.app_events to authenticated';

    execute 'drop policy if exists app_events_select_baseline_recovery on public.app_events';
    execute $p$
      create policy app_events_select_baseline_recovery
      on public.app_events
      for select
      to authenticated
      using (
        auth.uid() is not null
        and (
          host_id = auth.uid()
          or visibility = 'public'
          or exists (
            select 1
            from public.event_invites ei
            where ei.event_id = app_events.id
              and ei.invitee_id = auth.uid()
          )
          or exists (
            select 1
            from public.event_members m
            where m.event_id = app_events.id
              and m.user_id = auth.uid()
          )
        )
      )
    $p$;

    execute 'drop policy if exists app_events_insert_host_baseline_recovery on public.app_events';
    execute $p$
      create policy app_events_insert_host_baseline_recovery
      on public.app_events
      for insert
      to authenticated
      with check (host_id = auth.uid())
    $p$;

    execute 'drop policy if exists app_events_update_host_baseline_recovery on public.app_events';
    execute $p$
      create policy app_events_update_host_baseline_recovery
      on public.app_events
      for update
      to authenticated
      using (host_id = auth.uid())
      with check (host_id = auth.uid())
    $p$;

    execute 'drop policy if exists app_events_delete_host_baseline_recovery on public.app_events';
    execute $p$
      create policy app_events_delete_host_baseline_recovery
      on public.app_events
      for delete
      to authenticated
      using (host_id = auth.uid())
    $p$;
  end if;
end $$;

-- 3) event_invites: host inserts; invitee selects + updates own row.

do $$
begin
  if public._spacer_table_exists('event_invites') then
    execute 'alter table public.event_invites enable row level security';
    execute 'grant select, insert, update, delete on table public.event_invites to authenticated';

    execute 'drop policy if exists event_invites_select_baseline_recovery on public.event_invites';
    execute $p$
      create policy event_invites_select_baseline_recovery
      on public.event_invites
      for select
      to authenticated
      using (
        invitee_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_invites.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;

    execute 'drop policy if exists event_invites_insert_host_baseline_recovery on public.event_invites';
    execute $p$
      create policy event_invites_insert_host_baseline_recovery
      on public.event_invites
      for insert
      to authenticated
      with check (
        invitee_id <> auth.uid()
        and exists (
          select 1
          from public.app_events e
          where e.id = event_invites.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;

    execute 'drop policy if exists event_invites_update_baseline_recovery on public.event_invites';
    execute $p$
      create policy event_invites_update_baseline_recovery
      on public.event_invites
      for update
      to authenticated
      using (
        invitee_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_invites.event_id
            and e.host_id = auth.uid()
        )
      )
      with check (
        invitee_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_invites.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;

    execute 'drop policy if exists event_invites_delete_baseline_recovery on public.event_invites';
    execute $p$
      create policy event_invites_delete_baseline_recovery
      on public.event_invites
      for delete
      to authenticated
      using (
        invitee_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_invites.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;
  end if;
end $$;

-- 4) event_members: self row read/write; host manages all members.

do $$
begin
  if public._spacer_table_exists('event_members') then
    execute 'alter table public.event_members enable row level security';
    execute 'grant select, insert, update, delete on table public.event_members to authenticated';

    execute 'drop policy if exists event_members_select_baseline_recovery on public.event_members';
    execute $p$
      create policy event_members_select_baseline_recovery
      on public.event_members
      for select
      to authenticated
      using (
        user_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_members.event_id
            and (
              e.host_id = auth.uid()
              or e.visibility = 'public'
            )
        )
      )
    $p$;

    execute 'drop policy if exists event_members_write_baseline_recovery on public.event_members';
    execute $p$
      create policy event_members_write_baseline_recovery
      on public.event_members
      for all
      to authenticated
      using (
        user_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_members.event_id
            and e.host_id = auth.uid()
        )
      )
      with check (
        user_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_members.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;
  end if;
end $$;

-- 5) event_chat_rooms / event_chat_messages

do $$
begin
  if public._spacer_table_exists('event_chat_rooms') then
    execute 'alter table public.event_chat_rooms enable row level security';
    execute 'grant select, insert, update, delete on table public.event_chat_rooms to authenticated';

    execute 'drop policy if exists event_chat_rooms_baseline_recovery on public.event_chat_rooms';
    execute $p$
      create policy event_chat_rooms_baseline_recovery
      on public.event_chat_rooms
      for all
      to authenticated
      using (
        exists (
          select 1
          from public.app_events e
          where e.id = event_chat_rooms.event_id
            and (
              e.host_id = auth.uid()
              or exists (
                select 1
                from public.event_members m
                where m.event_id = e.id
                  and m.user_id = auth.uid()
              )
            )
        )
      )
      with check (
        exists (
          select 1
          from public.app_events e
          where e.id = event_chat_rooms.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;
  end if;
end $$;

do $$
begin
  if public._spacer_table_exists('event_chat_messages') then
    execute 'alter table public.event_chat_messages enable row level security';
    execute 'grant select, insert, update, delete on table public.event_chat_messages to authenticated';

    execute 'drop policy if exists event_chat_messages_select_baseline_recovery on public.event_chat_messages';
    execute $p$
      create policy event_chat_messages_select_baseline_recovery
      on public.event_chat_messages
      for select
      to authenticated
      using (
        exists (
          select 1
          from public.event_chat_rooms r
          join public.app_events e on e.id = r.event_id
          where r.id = event_chat_messages.room_id
            and (
              e.host_id = auth.uid()
              or exists (
                select 1
                from public.event_members m
                where m.event_id = e.id
                  and m.user_id = auth.uid()
              )
            )
        )
      )
    $p$;

    execute 'drop policy if exists event_chat_messages_insert_baseline_recovery on public.event_chat_messages';
    execute $p$
      create policy event_chat_messages_insert_baseline_recovery
      on public.event_chat_messages
      for insert
      to authenticated
      with check (
        sender_id = auth.uid()
        and exists (
          select 1
          from public.event_chat_rooms r
          join public.app_events e on e.id = r.event_id
          where r.id = event_chat_messages.room_id
            and (
              e.host_id = auth.uid()
              or exists (
                select 1
                from public.event_members m
                where m.event_id = e.id
                  and m.user_id = auth.uid()
              )
            )
        )
      )
    $p$;
  end if;
end $$;

-- 6) public_event_invites: any signed-in user reads; host inserts.

do $$
begin
  if public._spacer_table_exists('public_event_invites') then
    execute 'alter table public.public_event_invites enable row level security';
    execute 'grant select, insert, update, delete on table public.public_event_invites to authenticated';

    execute 'drop policy if exists public_event_invites_select_baseline_recovery on public.public_event_invites';
    execute $p$
      create policy public_event_invites_select_baseline_recovery
      on public.public_event_invites
      for select
      to authenticated
      using (auth.uid() is not null)
    $p$;

    execute 'drop policy if exists public_event_invites_insert_baseline_recovery on public.public_event_invites';
    execute $p$
      create policy public_event_invites_insert_baseline_recovery
      on public.public_event_invites
      for insert
      to authenticated
      with check (
        exists (
          select 1
          from public.app_events e
          where e.id = public_event_invites.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;
  end if;
end $$;

-- 7) event_availability: invitee writes own; host reads guest availability.

do $$
begin
  if public._spacer_table_exists('event_availability') then
    execute 'alter table public.event_availability enable row level security';
    execute 'grant select, insert, update, delete on table public.event_availability to authenticated';

    execute 'drop policy if exists event_availability_select_baseline_recovery on public.event_availability';
    execute $p$
      create policy event_availability_select_baseline_recovery
      on public.event_availability
      for select
      to authenticated
      using (
        user_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_availability.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;

    execute 'drop policy if exists event_availability_write_baseline_recovery on public.event_availability';
    execute $p$
      create policy event_availability_write_baseline_recovery
      on public.event_availability
      for all
      to authenticated
      using (user_id = auth.uid())
      with check (user_id = auth.uid())
    $p$;
  end if;
end $$;

-- 8) event_bring_item_claims (optional table)

do $$
begin
  if public._spacer_table_exists('event_bring_item_claims') then
    execute 'alter table public.event_bring_item_claims enable row level security';
    execute 'grant select, insert, update, delete on table public.event_bring_item_claims to authenticated';

    execute 'drop policy if exists event_bring_item_claims_baseline_recovery on public.event_bring_item_claims';
    execute $p$
      create policy event_bring_item_claims_baseline_recovery
      on public.event_bring_item_claims
      for all
      to authenticated
      using (
        user_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_bring_item_claims.event_id
            and e.host_id = auth.uid()
        )
        or exists (
          select 1
          from public.event_members m
          where m.event_id = event_bring_item_claims.event_id
            and m.user_id = auth.uid()
        )
      )
      with check (
        user_id = auth.uid()
        or exists (
          select 1
          from public.app_events e
          where e.id = event_bring_item_claims.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;
  end if;
end $$;

-- 9) event_live_status (optional table)

do $$
begin
  if public._spacer_table_exists('event_live_status') then
    execute 'alter table public.event_live_status enable row level security';
    execute 'grant select, insert, update, delete on table public.event_live_status to authenticated';

    execute 'drop policy if exists event_live_status_select_baseline_recovery on public.event_live_status';
    execute $p$
      create policy event_live_status_select_baseline_recovery
      on public.event_live_status
      for select
      to authenticated
      using (
        exists (
          select 1
          from public.app_events e
          where e.id = event_live_status.event_id
            and (
              e.host_id = auth.uid()
              or e.visibility = 'public'
              or exists (
                select 1
                from public.event_members m
                where m.event_id = e.id
                  and m.user_id = auth.uid()
              )
              or exists (
                select 1
                from public.event_invites ei
                where ei.event_id = e.id
                  and ei.invitee_id = auth.uid()
              )
            )
        )
      )
    $p$;

    execute 'drop policy if exists event_live_status_write_baseline_recovery on public.event_live_status';
    execute $p$
      create policy event_live_status_write_baseline_recovery
      on public.event_live_status
      for all
      to authenticated
      using (
        exists (
          select 1
          from public.app_events e
          where e.id = event_live_status.event_id
            and e.host_id = auth.uid()
        )
      )
      with check (
        exists (
          select 1
          from public.app_events e
          where e.id = event_live_status.event_id
            and e.host_id = auth.uid()
        )
      )
    $p$;
  end if;
end $$;

-- 10) user_notifications: recipient reads/updates; signed-in users insert
--     for any recipient (cross-user notifications are required by the app).

do $$
begin
  if public._spacer_table_exists('user_notifications') then
    execute 'alter table public.user_notifications enable row level security';
    execute 'grant select, insert, update, delete on table public.user_notifications to authenticated';

    execute 'drop policy if exists user_notifications_select_baseline_recovery on public.user_notifications';
    execute $p$
      create policy user_notifications_select_baseline_recovery
      on public.user_notifications
      for select
      to authenticated
      using (user_id = auth.uid())
    $p$;

    execute 'drop policy if exists user_notifications_insert_baseline_recovery on public.user_notifications';
    execute $p$
      create policy user_notifications_insert_baseline_recovery
      on public.user_notifications
      for insert
      to authenticated
      with check (auth.uid() is not null and user_id is not null)
    $p$;

    execute 'drop policy if exists user_notifications_update_baseline_recovery on public.user_notifications';
    execute $p$
      create policy user_notifications_update_baseline_recovery
      on public.user_notifications
      for update
      to authenticated
      using (user_id = auth.uid())
      with check (user_id = auth.uid())
    $p$;

    execute 'drop policy if exists user_notifications_delete_baseline_recovery on public.user_notifications';
    execute $p$
      create policy user_notifications_delete_baseline_recovery
      on public.user_notifications
      for delete
      to authenticated
      using (user_id = auth.uid())
    $p$;
  end if;
end $$;

-- 11) friend_requests: participants read; sender insert/update; either side delete.

do $$
begin
  if public._spacer_table_exists('friend_requests') then
    execute 'alter table public.friend_requests enable row level security';
    execute 'grant select, insert, update, delete on table public.friend_requests to authenticated';

    execute 'drop policy if exists friend_requests_select_baseline_recovery on public.friend_requests';
    execute $p$
      create policy friend_requests_select_baseline_recovery
      on public.friend_requests
      for select
      to authenticated
      using (sender_id = auth.uid() or receiver_id = auth.uid())
    $p$;

    execute 'drop policy if exists friend_requests_insert_baseline_recovery on public.friend_requests';
    execute $p$
      create policy friend_requests_insert_baseline_recovery
      on public.friend_requests
      for insert
      to authenticated
      with check (sender_id = auth.uid() and sender_id <> receiver_id)
    $p$;

    execute 'drop policy if exists friend_requests_update_baseline_recovery on public.friend_requests';
    execute $p$
      create policy friend_requests_update_baseline_recovery
      on public.friend_requests
      for update
      to authenticated
      using (sender_id = auth.uid() or receiver_id = auth.uid())
      with check (sender_id = auth.uid() or receiver_id = auth.uid())
    $p$;

    execute 'drop policy if exists friend_requests_delete_baseline_recovery on public.friend_requests';
    execute $p$
      create policy friend_requests_delete_baseline_recovery
      on public.friend_requests
      for delete
      to authenticated
      using (sender_id = auth.uid() or receiver_id = auth.uid())
    $p$;
  end if;
end $$;

-- 12) user_blocks: blocker manages own rows.

do $$
begin
  if public._spacer_table_exists('user_blocks') then
    execute 'alter table public.user_blocks enable row level security';
    execute 'grant select, insert, delete on table public.user_blocks to authenticated';

    execute 'drop policy if exists user_blocks_select_baseline_recovery on public.user_blocks';
    execute $p$
      create policy user_blocks_select_baseline_recovery
      on public.user_blocks
      for select
      to authenticated
      using (blocker_id = auth.uid() or blocked_id = auth.uid())
    $p$;

    execute 'drop policy if exists user_blocks_insert_baseline_recovery on public.user_blocks';
    execute $p$
      create policy user_blocks_insert_baseline_recovery
      on public.user_blocks
      for insert
      to authenticated
      with check (blocker_id = auth.uid() and blocker_id <> blocked_id)
    $p$;

    execute 'drop policy if exists user_blocks_delete_baseline_recovery on public.user_blocks';
    execute $p$
      create policy user_blocks_delete_baseline_recovery
      on public.user_blocks
      for delete
      to authenticated
      using (blocker_id = auth.uid())
    $p$;
  end if;
end $$;

-- 13) Per-user availability tables: self read/write only.

do $$
declare
  t text;
begin
  foreach t in array array[
    'user_availability_preferences',
    'user_weekly_availability_windows',
    'user_specific_availability'
  ]
  loop
    if public._spacer_table_exists(t) then
      execute format('alter table public.%I enable row level security', t);
      execute format('grant select, insert, update, delete on table public.%I to authenticated', t);

      execute format('drop policy if exists %I_self_baseline_recovery on public.%I', t, t);
      execute format($p$
        create policy %I_self_baseline_recovery
        on public.%I
        for all
        to authenticated
        using (user_id = auth.uid())
        with check (user_id = auth.uid())
      $p$, t, t);
    end if;
  end loop;
end $$;

-- 14) DM tables: participant access only.

do $$
begin
  if public._spacer_table_exists('dm_conversations') then
    execute 'alter table public.dm_conversations enable row level security';
    execute 'grant select, insert, update, delete on table public.dm_conversations to authenticated';

    execute 'drop policy if exists dm_conversations_baseline_recovery on public.dm_conversations';
    execute $p$
      create policy dm_conversations_baseline_recovery
      on public.dm_conversations
      for all
      to authenticated
      using (user_a = auth.uid() or user_b = auth.uid())
      with check (user_a = auth.uid() or user_b = auth.uid())
    $p$;
  end if;

  if public._spacer_table_exists('dm_messages') then
    execute 'alter table public.dm_messages enable row level security';
    execute 'grant select, insert, update, delete on table public.dm_messages to authenticated';

    execute 'drop policy if exists dm_messages_select_baseline_recovery on public.dm_messages';
    execute $p$
      create policy dm_messages_select_baseline_recovery
      on public.dm_messages
      for select
      to authenticated
      using (
        exists (
          select 1
          from public.dm_conversations c
          where c.id = dm_messages.conversation_id
            and (c.user_a = auth.uid() or c.user_b = auth.uid())
        )
      )
    $p$;

    execute 'drop policy if exists dm_messages_insert_baseline_recovery on public.dm_messages';
    execute $p$
      create policy dm_messages_insert_baseline_recovery
      on public.dm_messages
      for insert
      to authenticated
      with check (
        sender_id = auth.uid()
        and exists (
          select 1
          from public.dm_conversations c
          where c.id = dm_messages.conversation_id
            and (c.user_a = auth.uid() or c.user_b = auth.uid())
        )
      )
    $p$;
  end if;
end $$;

-- 15) user_reports: reporter inserts; owner reads.

do $$
begin
  if public._spacer_table_exists('user_reports') then
    execute 'alter table public.user_reports enable row level security';
    execute 'grant select, insert on table public.user_reports to authenticated';

    execute 'drop policy if exists user_reports_select_baseline_recovery on public.user_reports';
    execute $p$
      create policy user_reports_select_baseline_recovery
      on public.user_reports
      for select
      to authenticated
      using (reporter_id = auth.uid())
    $p$;

    execute 'drop policy if exists user_reports_insert_baseline_recovery on public.user_reports';
    execute $p$
      create policy user_reports_insert_baseline_recovery
      on public.user_reports
      for insert
      to authenticated
      with check (reporter_id = auth.uid())
    $p$;
  end if;
end $$;

-- 16) account_deletion_requests: self only.

do $$
begin
  if public._spacer_table_exists('account_deletion_requests') then
    execute 'alter table public.account_deletion_requests enable row level security';
    execute 'grant select, insert, update, delete on table public.account_deletion_requests to authenticated';

    execute 'drop policy if exists account_deletion_requests_baseline_recovery on public.account_deletion_requests';
    execute $p$
      create policy account_deletion_requests_baseline_recovery
      on public.account_deletion_requests
      for all
      to authenticated
      using (user_id = auth.uid())
      with check (user_id = auth.uid())
    $p$;
  end if;
end $$;

-- 17) Storage: Profile_photos bucket
--     The app reads via the public CDN URL, which doesn't require an RLS
--     SELECT policy. We re-add a *narrow* read policy so any signed
--     URL or list-by-prefix request still works for objects in this
--     bucket only (no broad cross-bucket access). Listing without
--     prefix/owner is still implicitly restricted because Postgrest
--     uses the same policy + qualifier.

do $$
begin
  if exists (
    select 1
    from information_schema.tables
    where table_schema = 'storage' and table_name = 'objects'
  ) then
    execute 'drop policy if exists "profile photos public read" on storage.objects';
    execute 'drop policy if exists profile_photos_select_baseline_recovery on storage.objects';
    execute $p$
      create policy profile_photos_select_baseline_recovery
      on storage.objects
      for select
      to anon, authenticated
      using (bucket_id = 'Profile_photos')
    $p$;

    execute 'drop policy if exists profile_photos_insert_baseline_recovery on storage.objects';
    execute $p$
      create policy profile_photos_insert_baseline_recovery
      on storage.objects
      for insert
      to authenticated
      with check (bucket_id = 'Profile_photos')
    $p$;

    execute 'drop policy if exists profile_photos_update_baseline_recovery on storage.objects';
    execute $p$
      create policy profile_photos_update_baseline_recovery
      on storage.objects
      for update
      to authenticated
      using (bucket_id = 'Profile_photos')
      with check (bucket_id = 'Profile_photos')
    $p$;
  end if;
end $$;

-- 18) Make sure cancel_hosted_event RPC is callable by signed-in users.

do $$
begin
  if to_regprocedure('public.cancel_hosted_event(uuid)') is not null then
    execute 'revoke execute on function public.cancel_hosted_event(uuid) from public, anon';
    execute 'grant execute on function public.cancel_hosted_event(uuid) to authenticated';
  end if;
end $$;

-- 19) Cleanup helper.

drop function if exists public._spacer_table_exists(text);

-- Section: 20260429131500_fix_rls_infinite_recursion.sql

drop policy if exists app_events_select_authenticated_restore on public.app_events;
drop policy if exists app_events_select_public_listing       on public.app_events;

-- Section: 20260429132000_restore_rls_helper_function_execute.sql

do $$
begin
  if to_regprocedure('public.user_is_invited_to_event(uuid,uuid)') is not null then
    execute 'grant execute on function public.user_is_invited_to_event(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.is_event_invitee(uuid,uuid)') is not null then
    execute 'grant execute on function public.is_event_invitee(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.is_app_event_host(uuid,uuid)') is not null then
    execute 'grant execute on function public.is_app_event_host(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.event_host_id(uuid)') is not null then
    execute 'grant execute on function public.event_host_id(uuid) to authenticated';
  end if;
  if to_regprocedure('public.is_event_host_or_cohost(uuid,uuid)') is not null then
    execute 'grant execute on function public.is_event_host_or_cohost(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.is_blocked_between(uuid,uuid)') is not null then
    execute 'grant execute on function public.is_blocked_between(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.is_event_member_active(uuid,uuid)') is not null then
    execute 'grant execute on function public.is_event_member_active(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.can_access_event_chat(uuid,uuid)') is not null then
    execute 'grant execute on function public.can_access_event_chat(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.can_post_event_chat(uuid,uuid)') is not null then
    execute 'grant execute on function public.can_post_event_chat(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.is_dm_participant(uuid,uuid)') is not null then
    execute 'grant execute on function public.is_dm_participant(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.can_create_dm_between(uuid,uuid)') is not null then
    execute 'grant execute on function public.can_create_dm_between(uuid,uuid) to authenticated';
  end if;
  if to_regprocedure('public.cancel_hosted_event(uuid)') is not null then
    execute 'grant execute on function public.cancel_hosted_event(uuid) to authenticated';
  end if;
  if to_regprocedure('public.schedule_conflicts_for_friends(timestamptz,timestamptz,uuid[],text)') is not null then
    execute 'grant execute on function public.schedule_conflicts_for_friends(timestamptz,timestamptz,uuid[],text) to authenticated';
  end if;
end $$;
