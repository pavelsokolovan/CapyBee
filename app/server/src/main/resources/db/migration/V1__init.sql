create table users (
    id uuid primary key,
    google_subject varchar(255) not null unique,
    email varchar(255) not null unique,
    display_name varchar(255) not null,
    avatar_url varchar(512),
    locale varchar(64),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table check_ins (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    mood varchar(64) not null,
    note text,
    created_at timestamptz not null
);

create table missions (
    id uuid primary key,
    code varchar(128) not null unique,
    title varchar(255) not null,
    description text not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table mission_completions (
    id uuid primary key,
    mission_id uuid not null references missions(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    completed_at timestamptz not null,
    note text
);
