insert into missions (id, code, title, description, active, created_at, updated_at)
values
    ('1f0ea95e-d6e4-4a40-aa9c-8eeebc8fe001', 'say_hi_once', 'Say hi to one person', 'Try saying hi to one person today.', true, now(), now()),
    ('1f0ea95e-d6e4-4a40-aa9c-8eeebc8fe002', 'sit_one_table_closer', 'Sit one table closer', 'At lunch, try sitting one table closer to someone.', true, now(), now()),
    ('1f0ea95e-d6e4-4a40-aa9c-8eeebc8fe003', 'find_one_good_thing', 'Find one good thing', 'Notice one thing about your new school that feels okay.', true, now(), now()),
    ('1f0ea95e-d6e4-4a40-aa9c-8eeebc8fe004', 'draw_old_and_new_home', 'Draw old and new home', 'Draw one memory from your old home and one thing from your new home.', true, now(), now()),
    ('1f0ea95e-d6e4-4a40-aa9c-8eeebc8fe005', 'ask_about_one_club', 'Ask about one club', 'Find out if your school has one club that sounds interesting.', true, now(), now())
on conflict (code) do nothing;
