-- EncisoOMR · bootstrap schema v1 — all application data private per authenticated owner.
-- Project: lsbwuwpvesdkwrwffjkc  (only this isolated Supabase project)
create extension if not exists pgcrypto;

create table if not exists public.omr_workspaces (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 120),
  created_at timestamptz not null default now(),
  unique (id, owner_id)
);

create table if not exists public.omr_exams (
  id uuid primary key default gen_random_uuid(),
  workspace_id uuid not null,
  owner_id uuid not null,
  title text not null check (char_length(btrim(title)) between 1 and 200),
  instructions text not null default '',
  status text not null default 'draft' check (status in ('draft', 'ready', 'archived')),
  settings jsonb not null default '{}'::jsonb check (jsonb_typeof(settings) = 'object'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, workspace_id, owner_id),
  foreign key (workspace_id, owner_id) references public.omr_workspaces(id, owner_id) on delete cascade
);

create table if not exists public.omr_students (
  id uuid primary key default gen_random_uuid(),
  workspace_id uuid not null,
  owner_id uuid not null,
  external_id text,
  full_name text not null check (char_length(btrim(full_name)) between 1 and 200),
  attributes jsonb not null default '{}'::jsonb check (jsonb_typeof(attributes) = 'object'),
  created_at timestamptz not null default now(),
  unique (id, workspace_id, owner_id),
  foreign key (workspace_id, owner_id) references public.omr_workspaces(id, owner_id) on delete cascade
);

create unique index if not exists omr_students_external_id_unique
  on public.omr_students (workspace_id, lower(external_id))
  where external_id is not null and btrim(external_id) <> '';

create table if not exists public.omr_answer_keys (
  exam_id uuid not null,
  workspace_id uuid not null,
  owner_id uuid not null,
  item_number int not null check (item_number between 1 and 1000),
  answer text,
  points numeric(10,3) not null default 1 check (points >= 0),
  penalty numeric(10,3) not null default 0 check (penalty >= 0),
  is_voided boolean not null default false,
  metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(metadata) = 'object'),
  primary key (exam_id, item_number),
  foreign key (exam_id, workspace_id, owner_id)
    references public.omr_exams(id, workspace_id, owner_id) on delete cascade
);

create table if not exists public.omr_scans (
  id uuid primary key default gen_random_uuid(),
  exam_id uuid not null,
  student_id uuid,
  workspace_id uuid not null,
  owner_id uuid not null,
  image_path text,
  status text not null default 'pending' check (status in ('pending', 'review', 'graded', 'error')),
  detected_answers jsonb not null default '{}'::jsonb check (jsonb_typeof(detected_answers) = 'object'),
  reviewed_answers jsonb not null default '{}'::jsonb check (jsonb_typeof(reviewed_answers) = 'object'),
  scan_metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(scan_metadata) = 'object'),
  score numeric(12,3),
  scanned_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (exam_id, workspace_id, owner_id)
    references public.omr_exams(id, workspace_id, owner_id) on delete cascade,
  foreign key (student_id, workspace_id, owner_id)
    references public.omr_students(id, workspace_id, owner_id) on delete set null (student_id)
);

create index if not exists omr_scans_exam_idx on public.omr_scans(exam_id, scanned_at desc);
create index if not exists omr_scans_student_idx on public.omr_scans(student_id) where student_id is not null;

create table if not exists public.omr_sync_events (
  id uuid primary key default gen_random_uuid(),
  workspace_id uuid not null,
  owner_id uuid not null,
  device_id text not null check (char_length(device_id) between 1 and 128),
  local_event_id text not null check (char_length(local_event_id) between 1 and 128),
  kind text not null,
  payload jsonb not null default '{}'::jsonb check (jsonb_typeof(payload) = 'object'),
  received_at timestamptz not null default now(),
  unique (owner_id, device_id, local_event_id),
  foreign key (workspace_id, owner_id)
    references public.omr_workspaces(id, owner_id) on delete cascade
);

alter table public.omr_workspaces enable row level security;
alter table public.omr_exams enable row level security;
alter table public.omr_students enable row level security;
alter table public.omr_answer_keys enable row level security;
alter table public.omr_scans enable row level security;
alter table public.omr_sync_events enable row level security;

create policy omr_workspace_own on public.omr_workspaces
  for all to authenticated using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
create policy omr_exam_own on public.omr_exams
  for all to authenticated using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
create policy omr_student_own on public.omr_students
  for all to authenticated using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
create policy omr_key_own on public.omr_answer_keys
  for all to authenticated using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
create policy omr_scan_own on public.omr_scans
  for all to authenticated using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
create policy omr_sync_own on public.omr_sync_events
  for all to authenticated using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

grant select, insert, update, delete on
  public.omr_workspaces, public.omr_exams, public.omr_students,
  public.omr_answer_keys, public.omr_scans, public.omr_sync_events
  to authenticated;

-- WebP images are private; path prefix MUST be auth.uid() to avoid cross-account reads.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('omr-scans', 'omr-scans', false, 3145728, array['image/webp'])
on conflict (id) do nothing;

create policy omr_scan_file_read on storage.objects for select to authenticated
using (bucket_id = 'omr-scans' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy omr_scan_file_insert on storage.objects for insert to authenticated
with check (bucket_id = 'omr-scans' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy omr_scan_file_update on storage.objects for update to authenticated
using (bucket_id = 'omr-scans' and (storage.foldername(name))[1] = (select auth.uid())::text)
with check (bucket_id = 'omr-scans' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy omr_scan_file_delete on storage.objects for delete to authenticated
using (bucket_id = 'omr-scans' and (storage.foldername(name))[1] = (select auth.uid())::text);
