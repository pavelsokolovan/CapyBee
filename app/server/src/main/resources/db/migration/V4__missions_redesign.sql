alter table missions
    add column if not exists title_en varchar(255),
    add column if not exists title_pl varchar(255),
    add column if not exists time_hint_en varchar(64),
    add column if not exists time_hint_pl varchar(64),
    add column if not exists category varchar(32);

update missions
set
    title_en = coalesce(title_en, title),
    title_pl = coalesce(title_pl, title),
    time_hint_en = coalesce(time_hint_en, 'this takes 2 minutes'),
    time_hint_pl = coalesce(time_hint_pl, 'to zajmie 2 minuty')
where title_en is null
   or title_pl is null
   or time_hint_en is null
   or time_hint_pl is null;

create table if not exists mission_interactions (
    id uuid primary key default gen_random_uuid(),
    child_profile_id uuid not null references family_profiles(id) on delete cascade,
    mission_id uuid not null references missions(id) on delete cascade,
    action varchar(16) not null check (action in ('completed', 'skipped', 'undone')),
    created_at timestamptz not null default now()
);

create index if not exists idx_mission_interactions_profile_created
    on mission_interactions(child_profile_id, created_at desc);

insert into missions (
    id,
    code,
    title,
    title_en,
    title_pl,
    description,
    time_hint_en,
    time_hint_pl,
    category,
    active,
    created_at,
    updated_at
) values
    ('9c11b25e-a145-4da1-8d88-77e73aebf101', 'say_hi_someone_new_school', 'Say hi to someone new at school', 'Say hi to someone new at school', 'Powiedz "czesc" komus nowemu w szkole', 'Try saying hi to someone new at school.', 'this takes 1 minute', 'to zajmie 1 minute', 'social', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf102', 'ask_favorite_subject', 'Ask someone what their favorite subject is', 'Ask someone what their favorite subject is', 'Zapytaj kogos, jaki jest jego ulubiony przedmiot', 'Ask someone what their favorite subject is.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf103', 'sit_somewhere_new_lunch', 'Sit somewhere new at lunch', 'Sit somewhere new at lunch', 'Usiadz dzis w nowym miejscu na obiedzie', 'Sit somewhere new at lunch.', 'this takes 1 minute', 'to zajmie 1 minute', 'social', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf104', 'compliment_someone_small', 'Compliment someone on something small', 'Compliment someone on something small', 'Powiedz komus cos milego', 'Compliment someone on something small.', 'this takes 1 minute', 'to zajmie 1 minute', 'social', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf105', 'learn_one_new_word_local_language', 'Learn one new word in the local language', 'Learn one new word in the local language', 'Naucz sie jednego nowego slowa w lokalnym jezyku', 'Learn one new word in the local language.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'new_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf106', 'find_one_new_thing_near_home', 'Find one thing near your home you didn''t notice before', 'Find one thing near your home you didn''t notice before', 'Znajdz cos w okolicy, czego wczesniej nie zauwazyles', 'Find one thing near your home you did not notice before.', 'this takes 5 minutes', 'to zajmie 5 minut', 'new_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf107', 'try_new_food_here', 'Try one food you haven''t tried here yet', 'Try one food you haven''t tried here yet', 'Sprobuj jednego nowego jedzenia', 'Try one food you have not tried here yet.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'new_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf108', 'ask_teacher_question_after_class', 'Ask a teacher one question after class', 'Ask a teacher one question after class', 'Zadaj nauczycielowi jedno pytanie po lekcji', 'Ask a teacher one question after class.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf109', 'draw_old_street_memory', 'Draw or describe your old street from memory', 'Draw or describe your old street from memory', 'Narysuj lub opisz z pamieci swoja stara ulice', 'Draw or describe your old street from memory.', 'this takes 5 minutes', 'to zajmie 5 minut', 'old_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf110', 'write_smell_sound_home', 'Write down a smell or sound that reminds you of home', 'Write down a smell or sound that reminds you of home', 'Zapisz zapach lub dzwiek, ktory przypomina ci dom', 'Write down a smell or sound that reminds you of home.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'old_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf111', 'message_old_friend_hi', 'Message an old friend just to say hi', 'Message an old friend just to say hi', 'Napisz do starego przyjaciela, zeby sie przywitac', 'Message an old friend just to say hi.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'old_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf112', 'name_one_thing_easier_here', 'Name one thing that''s easier here than back home', 'Name one thing that''s easier here than back home', 'Znajdz jedna rzecz, ktora tu jest latwiejsza niz w domu', 'Name one thing that is easier here than back home.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'reflection', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf113', 'look_up_school_club', 'Look up one club or activity at your school', 'Look up one club or activity at your school', 'Sprawdz jedno kolko lub zajecia w szkole', 'Look up one club or activity at your school.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'exploration', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf114', 'ask_explain_game_rule', 'Ask someone to explain a game or rule you don''t know', 'Ask someone to explain a game or rule you don''t know', 'Popros kogos, zeby wytlumaczyl ci gre lub zasade', 'Ask someone to explain a game or rule you do not know.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf115', 'photo_today_okay', 'Take a photo of something that made today okay', 'Take a photo of something that made today okay', 'Zrob zdjecie czegos, co sprawilo, ze dzis bylo okej', 'Take a photo of something that made today okay.', 'this takes 1 minute', 'to zajmie 1 minute', 'reflection', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf116', 'wave_smile_two_days', 'Wave or smile at the same person two days in a row', 'Wave or smile at the same person two days in a row', 'Usmiechnij sie do tej samej osoby dwa dni z rzedu', 'Wave or smile at the same person two days in a row.', 'this takes 1 minute', 'to zajmie 1 minute', 'social', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf117', 'cook_something_from_home', 'Ask your family to cook something from home together', 'Ask your family to cook something from home together', 'Zaproponuj rodzinie ugotowanie czegos z domu', 'Ask your family to cook something from home together.', 'this takes 10 minutes', 'to zajmie 10 minut', 'old_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf118', 'find_popular_school_sport', 'Find out what sport is popular at your new school', 'Find out what sport is popular at your new school', 'Dowiedz sie, jaki sport jest popularny w nowej szkole', 'Find out what sport is popular at your new school.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'new_world', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf119', 'write_one_sentence_today_felt', 'Write one sentence about how today actually felt', 'Write one sentence about how today actually felt', 'Napisz jedno zdanie o tym, jak naprawde bylo dzis', 'Write one sentence about how today actually felt.', 'this takes 1 minute', 'to zajmie 1 minute', 'reflection', true, now(), now()),
    ('9c11b25e-a145-4da1-8d88-77e73aebf120', 'offer_help_classmate_small', 'Offer to help a classmate with something small', 'Offer to help a classmate with something small', 'Zaoferuj pomoc koledze lub kolezance w czyms drobnym', 'Offer to help a classmate with something small.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true, now(), now())
on conflict (code) do nothing;
