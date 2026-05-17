alter table assistant_answer
    add column if not exists run_id varchar(128);

create index if not exists idx_assistant_answer_run_id
    on assistant_answer(run_id);
