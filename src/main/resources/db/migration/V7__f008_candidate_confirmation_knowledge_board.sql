alter table knowledge_candidate
    add column if not exists title varchar(500),
    add column if not exists statement text,
    add column if not exists suggested_section varchar(64),
    add column if not exists source_types_json jsonb not null default '[]'::jsonb,
    add column if not exists evidence_source_ids_json jsonb not null default '[]'::jsonb;

update knowledge_candidate
set title = coalesce(title, nullif(metadata_json->>'title', ''), left(content, 120)),
    statement = coalesce(statement, content),
    suggested_section = coalesce(nullif(suggested_section, ''), nullif(metadata_json->>'suggestedSection', ''), 'confirmed_finding')
where title is null
   or statement is null
   or suggested_section is null;

update knowledge_candidate
set status = 'pending'
where status not in ('pending', 'accepted', 'edited_accepted', 'marked_unverified', 'ignored');

update knowledge_candidate
set suggested_section = 'confirmed_finding'
where suggested_section not in ('core_concept', 'method_route', 'confirmed_finding', 'open_question');

alter table knowledge_candidate
    alter column title set default '',
    alter column statement set default '',
    alter column suggested_section set default 'confirmed_finding',
    alter column title set not null,
    alter column statement set not null,
    alter column suggested_section set not null;

create or replace function set_knowledge_candidate_f008_defaults()
returns trigger as $$
begin
    new.title = coalesce(nullif(new.title, ''), left(coalesce(new.content, ''), 120));
    new.statement = coalesce(nullif(new.statement, ''), coalesce(new.content, ''));
    if new.suggested_section is null
       or new.suggested_section not in ('core_concept', 'method_route', 'confirmed_finding', 'open_question') then
        new.suggested_section = 'confirmed_finding';
    end if;
    if new.status is null
       or new.status not in ('pending', 'accepted', 'edited_accepted', 'marked_unverified', 'ignored') then
        new.status = 'pending';
    end if;
    return new;
end;
$$ language plpgsql;

drop trigger if exists trg_knowledge_candidate_f008_defaults on knowledge_candidate;
create trigger trg_knowledge_candidate_f008_defaults
before insert on knowledge_candidate
for each row
execute function set_knowledge_candidate_f008_defaults();

alter table knowledge_candidate
    drop constraint if exists chk_knowledge_candidate_status,
    add constraint chk_knowledge_candidate_status
        check (status in ('pending', 'accepted', 'edited_accepted', 'marked_unverified', 'ignored')),
    drop constraint if exists chk_knowledge_candidate_suggested_section,
    add constraint chk_knowledge_candidate_suggested_section
        check (suggested_section in ('core_concept', 'method_route', 'confirmed_finding', 'open_question'));

alter table knowledge_entry
    add column if not exists evidence_status varchar(32) not null default 'confirmed',
    add column if not exists source_candidate_id varchar(64),
    add column if not exists evidence_source_ids_json jsonb not null default '[]'::jsonb;

update knowledge_entry
set section = 'current_candidates'
where section not in ('current_candidates', 'core_concept', 'method_route', 'confirmed_finding', 'open_question');

update knowledge_entry
set evidence_status = 'confirmed'
where evidence_status not in ('confirmed', 'unverified', 'needs_review');

update knowledge_entry
set source_candidate_id = coalesce(source_candidate_id, candidate_id)
where source_candidate_id is null
  and candidate_id is not null;

alter table knowledge_entry
    alter column section set default 'current_candidates',
    alter column evidence_status set default 'confirmed',
    drop constraint if exists chk_knowledge_entry_evidence_status,
    add constraint chk_knowledge_entry_evidence_status
        check (evidence_status in ('confirmed', 'unverified', 'needs_review')),
    drop constraint if exists chk_knowledge_entry_section,
    add constraint chk_knowledge_entry_section
        check (section in ('current_candidates', 'core_concept', 'method_route', 'confirmed_finding', 'open_question'));

alter table knowledge_entry
    drop constraint if exists fk_knowledge_entry_project_source_candidate,
    add constraint fk_knowledge_entry_project_source_candidate
        foreign key (project_id, source_candidate_id)
        references knowledge_candidate(project_id, id)
        on delete set null (source_candidate_id);

create index if not exists idx_knowledge_entry_project_section_active
    on knowledge_entry(project_id, section, updated_at desc)
    where archived = false;

create unique index if not exists uq_knowledge_entry_active_source_candidate
    on knowledge_entry(project_id, source_candidate_id)
    where archived = false
      and source_candidate_id is not null;
