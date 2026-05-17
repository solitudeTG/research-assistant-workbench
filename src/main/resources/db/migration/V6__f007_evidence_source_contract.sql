alter table evidence_source
    add column if not exists source_type varchar(32) not null default 'paper',
    add column if not exists snippet text,
    add column if not exists strength varchar(32),
    add column if not exists relevance_score double precision not null default 0,
    add column if not exists feedback_score double precision not null default 0,
    add column if not exists citation_meta_json jsonb not null default '{}'::jsonb;

update evidence_source
set snippet = coalesce(snippet, quote, ''),
    strength = coalesce(strength, confidence, 'weak')
where snippet is null
   or strength is null;

alter table evidence_source
    alter column snippet set not null,
    alter column strength set not null;

alter table source_document
    add column if not exists indexed_document_id bigint;

create index if not exists idx_source_document_project_indexed_document_id
    on source_document(project_id, indexed_document_id)
    where indexed_document_id is not null;
