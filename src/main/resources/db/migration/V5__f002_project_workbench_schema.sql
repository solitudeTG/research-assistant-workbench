create table if not exists research_project (
    id varchar(64) primary key,
    topic varchar(500) not null,
    summary text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_research_project_set_updated_at
before update on research_project
for each row
execute function set_updated_at();

create table if not exists research_session (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    title varchar(500) not null,
    status varchar(32) not null default 'continue',
    working_memory_id varchar(64),
    last_message_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_research_session_project_id_id unique (project_id, id)
);

create trigger trg_research_session_set_updated_at
before update on research_session
for each row
execute function set_updated_at();

create index if not exists idx_research_session_project_id
    on research_session(project_id, updated_at desc);

create table if not exists source_document (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    type varchar(32) not null,
    title varchar(500) not null,
    uri text,
    status varchar(32) not null,
    failure_stage varchar(64),
    error_message text,
    deposited_knowledge_count integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_source_document_project_id_id unique (project_id, id)
);

create trigger trg_source_document_set_updated_at
before update on source_document
for each row
execute function set_updated_at();

create index if not exists idx_source_document_project_id
    on source_document(project_id, updated_at desc);

create table if not exists assistant_answer (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    session_id varchar(64),
    question text,
    answer text,
    answer_mode varchar(32),
    evidence_state varchar(32),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_assistant_answer_project_id_id unique (project_id, id),
    constraint fk_assistant_answer_project_session
        foreign key (project_id, session_id)
        references research_session(project_id, id)
        on delete set null (session_id)
);

create trigger trg_assistant_answer_set_updated_at
before update on assistant_answer
for each row
execute function set_updated_at();

create index if not exists idx_assistant_answer_project_id
    on assistant_answer(project_id, created_at desc);
create index if not exists idx_assistant_answer_session_id
    on assistant_answer(session_id, created_at desc);

create table if not exists evidence_source (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    answer_id varchar(64) not null,
    source_id varchar(64),
    quote text,
    locator_json jsonb not null default '{}'::jsonb,
    confidence varchar(32),
    created_at timestamptz not null default now(),
    constraint fk_evidence_source_project_answer
        foreign key (project_id, answer_id)
        references assistant_answer(project_id, id)
        on delete cascade,
    constraint fk_evidence_source_project_source
        foreign key (project_id, source_id)
        references source_document(project_id, id)
        on delete set null (source_id)
);

create index if not exists idx_evidence_source_project_id
    on evidence_source(project_id, created_at desc);
create index if not exists idx_evidence_source_answer_id
    on evidence_source(answer_id, created_at desc);
create index if not exists idx_evidence_source_source_id
    on evidence_source(source_id);

create table if not exists knowledge_candidate (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    session_id varchar(64),
    answer_id varchar(64),
    source_id varchar(64),
    status varchar(32) not null default 'pending',
    content text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_knowledge_candidate_project_id_id unique (project_id, id),
    constraint fk_knowledge_candidate_project_session
        foreign key (project_id, session_id)
        references research_session(project_id, id)
        on delete set null (session_id),
    constraint fk_knowledge_candidate_project_answer
        foreign key (project_id, answer_id)
        references assistant_answer(project_id, id)
        on delete set null (answer_id),
    constraint fk_knowledge_candidate_project_source
        foreign key (project_id, source_id)
        references source_document(project_id, id)
        on delete set null (source_id)
);

create trigger trg_knowledge_candidate_set_updated_at
before update on knowledge_candidate
for each row
execute function set_updated_at();

create index if not exists idx_knowledge_candidate_project_id
    on knowledge_candidate(project_id, created_at desc);
create index if not exists idx_knowledge_candidate_session_id
    on knowledge_candidate(session_id, created_at desc);
create index if not exists idx_knowledge_candidate_answer_id
    on knowledge_candidate(answer_id, created_at desc);
create index if not exists idx_knowledge_candidate_source_id
    on knowledge_candidate(source_id);

create table if not exists knowledge_entry (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    candidate_id varchar(64),
    section varchar(128) not null default 'default',
    title varchar(500) not null,
    content text not null,
    archived boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_knowledge_entry_project_candidate
        foreign key (project_id, candidate_id)
        references knowledge_candidate(project_id, id)
        on delete set null (candidate_id)
);

create trigger trg_knowledge_entry_set_updated_at
before update on knowledge_entry
for each row
execute function set_updated_at();

create index if not exists idx_knowledge_entry_project_id
    on knowledge_entry(project_id, updated_at desc);

create table if not exists stream_event_record (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    session_id varchar(64),
    run_id varchar(128) not null,
    event_type varchar(128) not null,
    payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint fk_stream_event_record_project_session
        foreign key (project_id, session_id)
        references research_session(project_id, id)
        on delete set null (session_id)
);

create index if not exists idx_stream_event_record_project_id
    on stream_event_record(project_id, created_at);
create index if not exists idx_stream_event_record_session_id
    on stream_event_record(session_id, created_at);
create index if not exists idx_stream_event_record_run_id
    on stream_event_record(run_id, created_at);

create table if not exists answer_feedback (
    id varchar(64) primary key,
    project_id varchar(64) not null references research_project(id) on delete cascade,
    answer_id varchar(64) not null,
    feedback_score integer not null,
    note text,
    created_at timestamptz not null default now(),
    constraint fk_answer_feedback_project_answer
        foreign key (project_id, answer_id)
        references assistant_answer(project_id, id)
        on delete cascade
);

create index if not exists idx_answer_feedback_project_id
    on answer_feedback(project_id, created_at desc);
create index if not exists idx_answer_feedback_answer_id
    on answer_feedback(answer_id, created_at desc);
