create extension if not exists vector;

create table if not exists research_document (
    id bigserial primary key,
    title varchar(500) not null,
    original_file_name varchar(500) not null,
    storage_path varchar(1000) not null,
    status varchar(32) not null,
    failure_stage varchar(32),
    parse_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists document_chunk (
    id bigserial primary key,
    document_id bigint not null references research_document(id) on delete cascade,
    chunk_index integer not null,
    content text not null,
    token_count integer not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_document_chunk_document_id on document_chunk(document_id);
create index if not exists idx_document_chunk_fts
    on document_chunk using gin (to_tsvector('simple', content));

create table if not exists chat_session (
    id bigserial primary key,
    session_key varchar(128) not null unique,
    current_task text,
    rolling_summary text,
    salient_facts_json jsonb not null default '[]'::jsonb,
    compressed_rounds_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists chat_message (
    id bigserial primary key,
    session_id bigint not null references chat_session(id) on delete cascade,
    role varchar(32) not null,
    content text not null,
    answer_mode varchar(32),
    created_at timestamptz not null default now()
);

create index if not exists idx_chat_message_session_id on chat_message(session_id);

create table if not exists retrieval_trace (
    id bigserial primary key,
    session_id bigint references chat_session(id) on delete set null,
    query_text text not null,
    filters_json jsonb not null default '{}'::jsonb,
    top_chunks_json jsonb not null default '[]'::jsonb,
    rerank_result_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now()
);
