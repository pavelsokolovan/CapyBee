create table family_profiles (
    id uuid primary key,
    parent_user_id uuid not null references users(id) on delete cascade,
    nickname varchar(80) not null,
    birth_year int,
    preferred_locale varchar(16) not null default 'en',
    avatar_seed varchar(64),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_family_profiles_parent_user_id on family_profiles(parent_user_id);

create table friendship_entries (
    id uuid primary key,
    profile_id uuid not null references family_profiles(id) on delete cascade,
    person_label varchar(120) not null,
    stage varchar(32) not null,
    note text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_friendship_entries_profile_id_created_at
    on friendship_entries(profile_id, created_at desc);

create table memory_entries (
    id uuid primary key,
    profile_id uuid not null references family_profiles(id) on delete cascade,
    world_type varchar(16) not null,
    title varchar(120),
    text_content text,
    media_url varchar(512),
    is_favorite boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_memory_entries_profile_world_created
    on memory_entries(profile_id, world_type, created_at desc);
