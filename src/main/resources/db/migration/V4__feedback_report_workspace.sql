create table if not exists message_feedback (
    id bigserial primary key,
    message_id bigint not null,
    feedback_score integer not null,
    note text,
    chunk_ids_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_message_feedback_message_id on message_feedback(message_id, created_at desc);
