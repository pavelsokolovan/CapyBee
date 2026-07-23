create table if not exists mission_child_states (
    id uuid primary key default gen_random_uuid(),
    child_profile_id uuid not null references family_profiles(id) on delete cascade,
    mission_id uuid not null references missions(id) on delete cascade,
    last_actioned_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_mission_child_state unique (child_profile_id, mission_id)
);

create index if not exists idx_mission_child_states_profile_action
    on mission_child_states(child_profile_id, last_actioned_at);
