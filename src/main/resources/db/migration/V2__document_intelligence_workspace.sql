alter table research_document
    add column if not exists total_chunks integer not null default 0,
    add column if not exists total_tokens integer not null default 0;

alter table document_chunk
    add column if not exists feedback_score double precision not null default 0,
    add column if not exists section_label varchar(128),
    add column if not exists section_path varchar(256);

create table if not exists document_analysis (
    document_id bigint primary key references research_document(id) on delete cascade,
    abstract_text text,
    structured_summary text,
    methods_json jsonb not null default '[]'::jsonb,
    contributions_json jsonb not null default '[]'::jsonb,
    keywords_json jsonb not null default '[]'::jsonb,
    outline_json jsonb not null default '[]'::jsonb,
    generated_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_document_analysis_set_updated_at
before update on document_analysis
for each row
execute function set_updated_at();

create index if not exists idx_document_chunk_feedback_score on document_chunk(feedback_score desc);
