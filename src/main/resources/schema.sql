create table if not exists triagem_execution (
    id varchar(36) primary key,
    executed_at timestamptz not null,
    execution_type varchar(20) not null,
    log_count integer not null,
    execution_time_ms bigint not null,
    cost_usd numeric(18, 10) not null
);

create table if not exists triagem_result (
    id bigserial primary key,
    execution_id varchar(36) not null references triagem_execution(id) on delete cascade,
    result_order integer not null,
    log text not null,
    team varchar(50) not null,
    runbook varchar(255) not null,
    chunk_number integer not null default 1,
    execution_time_ms bigint not null,
    cost_usd numeric(18, 10) not null,
    raw_response text not null
);

alter table triagem_result
    add column if not exists chunk_number integer not null default 1;

alter table triagem_result
    add column if not exists request_number integer not null default 1;

create index if not exists idx_triagem_execution_executed_at
    on triagem_execution (executed_at desc);

create index if not exists idx_triagem_result_execution_id
    on triagem_result (execution_id);

create table if not exists newsletter_generation (
    execution_code varchar(36) primary key,
    username varchar(100) not null,
    requested_date date not null,
    script text not null,
    status varchar(20) not null,
    audio_path text,
    error text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_newsletter_generation_created_at
    on newsletter_generation (created_at desc);
