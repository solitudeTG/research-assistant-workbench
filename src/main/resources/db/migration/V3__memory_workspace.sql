alter table chat_session
    add column if not exists last_deposited_message_id bigint not null default 0,
    add column if not exists last_deposit_at timestamptz;

create table if not exists global_knowledge_note (
    note_type varchar(64) primary key,
    content text not null default '',
    updated_at timestamptz not null default now()
);

create trigger trg_global_knowledge_note_set_updated_at
before update on global_knowledge_note
for each row
execute function set_updated_at();

create table if not exists memory_entry (
    id bigserial primary key,
    session_id bigint references chat_session(id) on delete set null,
    source_kind varchar(32) not null,
    topic varchar(256) not null,
    summary text not null,
    key_findings_json jsonb not null default '[]'::jsonb,
    open_questions_json jsonb not null default '[]'::jsonb,
    keywords_json jsonb not null default '[]'::jsonb,
    source_message_start_id bigint not null,
    source_message_end_id bigint not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_memory_entry_set_updated_at
before update on memory_entry
for each row
execute function set_updated_at();

create index if not exists idx_memory_entry_session_id on memory_entry(session_id, created_at desc);
create index if not exists idx_memory_entry_source_end on memory_entry(source_message_end_id desc);
create index if not exists idx_memory_entry_fts
    on memory_entry using gin (
        to_tsvector(
            'simple',
            coalesce(topic, '') || ' ' ||
            coalesce(summary, '') || ' ' ||
            coalesce(key_findings_json::text, '') || ' ' ||
            coalesce(open_questions_json::text, '')
        )
    );
