create table if not exists l3_memory_promotion_state (
    id bigserial primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    source_memory_entry_id bigint not null references memory_entry(id) on delete cascade,
    hit_count integer not null default 0,
    last_score double precision not null default 0,
    last_session_id varchar(64),
    last_run_id varchar(128),
    last_answer_id varchar(64),
    promotion_reason varchar(128) not null default 'l3_memory_repeated_hit',
    candidate_id varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_l3_memory_promotion_project_memory unique (project_id, source_memory_entry_id),
    constraint fk_l3_memory_promotion_candidate
        foreign key (project_id, candidate_id)
        references knowledge_candidate(project_id, id)
        on delete set null (candidate_id)
);

create trigger trg_l3_memory_promotion_state_set_updated_at
before update on l3_memory_promotion_state
for each row
execute function set_updated_at();

create index if not exists idx_l3_memory_promotion_project_updated
    on l3_memory_promotion_state(project_id, updated_at desc);

create table if not exists l3_memory_promotion_hit (
    id bigserial primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    source_memory_entry_id bigint not null references memory_entry(id) on delete cascade,
    hit_identity varchar(256) not null,
    run_id varchar(128),
    answer_id varchar(64),
    score double precision not null default 0,
    created_at timestamptz not null default now(),
    constraint uq_l3_memory_promotion_hit_identity
        unique (project_id, source_memory_entry_id, hit_identity)
);

create index if not exists idx_l3_memory_promotion_hit_project_memory
    on l3_memory_promotion_hit(project_id, source_memory_entry_id, created_at desc);

alter table knowledge_candidate
    add column if not exists source_kind varchar(32) not null default 'answer',
    add column if not exists source_memory_entry_id bigint references memory_entry(id) on delete set null,
    add column if not exists promotion_hit_count integer not null default 0,
    add column if not exists promotion_last_score double precision not null default 0,
    add column if not exists promotion_reason varchar(128),
    add column if not exists decay_reason varchar(128);

update knowledge_candidate
set source_kind = 'answer'
where source_kind is null
   or source_kind not in ('answer', 'l3_memory');

alter table knowledge_candidate
    drop constraint if exists chk_knowledge_candidate_source_kind,
    add constraint chk_knowledge_candidate_source_kind
        check (source_kind in ('answer', 'l3_memory'));

create index if not exists idx_knowledge_candidate_l3_memory_source
    on knowledge_candidate(project_id, source_memory_entry_id, status)
    where source_kind = 'l3_memory'
      and source_memory_entry_id is not null;

create unique index if not exists uq_knowledge_candidate_pending_l3_memory_source
    on knowledge_candidate(project_id, source_memory_entry_id)
    where source_kind = 'l3_memory'
      and source_memory_entry_id is not null
      and status = 'pending';

alter table knowledge_candidate
    drop constraint if exists chk_knowledge_candidate_status,
    add constraint chk_knowledge_candidate_status
        check (status in ('pending', 'accepted', 'edited_accepted', 'marked_unverified', 'ignored', 'decayed'));
