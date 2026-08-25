create table session_restore_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null unique,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    last_used_at timestamptz
);

create index idx_session_restore_tokens_user on session_restore_tokens (user_id);
