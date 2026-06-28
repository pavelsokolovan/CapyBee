import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export interface UserProfile {
  authenticated: boolean;
  id?: string;
  email?: string;
  displayName?: string;
  avatarUrl?: string;
}

export interface CheckIn {
  id: string;
  mood: string;
  note: string;
  createdAt: string;
}

interface ChildProfile {
  id: string;
  nickname: string;
  birthYear?: number;
  preferredLocale: 'en' | 'pl';
  avatarSeed?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

interface Mission {
  id: string;
  code: string;
  title: string;
  description: string;
  active: boolean;
}

interface MissionCompletion {
  id: string;
  missionId: string;
  missionCode: string;
  title: string;
  profileId: string;
  completedAt: string;
  note?: string;
}

interface FriendshipEntry {
  id: string;
  personLabel: string;
  stage: string;
  note?: string;
  createdAt: string;
  updatedAt: string;
}

interface MemoryEntry {
  id: string;
  worldType: 'old_world' | 'new_world';
  title?: string;
  textContent?: string;
  mediaUrl?: string;
  isFavorite: boolean;
  createdAt: string;
  updatedAt: string;
}

const moodEmoji: Record<string, string> = {
  heavy: '😔',
  okay: '😐',
  good: '😊'
};

const stageOptions = ['noticed', 'was_nice', 'talked', 'want_to_know_better'];

type TabKey = 'home' | 'missions' | 'friendships' | 'memories' | 'profile';

const copy = {
  en: {
    homeTitle: 'How was today?',
    checkInSaved: 'Thanks for sharing. One small step is enough for today.',
    profileSetupTitle: 'Create child profile',
    profileSetupHint: 'Use a nickname. Real names are optional and not required.',
    save: 'Save',
    saving: 'Saving...',
    setUp: 'Set up profile',
    checkInPlaceholder: 'Optional note',
    missions: 'Missions',
    friendships: 'Friendships',
    memories: 'Memories',
    profile: 'Profile',
    home: 'Home',
    oldWorld: 'Old World',
    newWorld: 'New World',
    language: 'Language',
    noItems: 'Nothing here yet.'
  },
  pl: {
    homeTitle: 'Jak dziś było?',
    checkInSaved: 'Dzięki, że to zapisałeś. Jeden mały krok na dziś wystarczy.',
    profileSetupTitle: 'Utwórz profil dziecka',
    profileSetupHint: 'Użyj ksywki. Prawdziwe imię nie jest wymagane.',
    save: 'Zapisz',
    saving: 'Zapisywanie...',
    setUp: 'Utwórz profil',
    checkInPlaceholder: 'Opcjonalna notatka',
    missions: 'Misje',
    friendships: 'Relacje',
    memories: 'Wspomnienia',
    profile: 'Profil',
    home: 'Start',
    oldWorld: 'Stary świat',
    newWorld: 'Nowy świat',
    language: 'Język',
    noItems: 'Na razie nic tu nie ma.'
  }
};

export function AuthenticatedHome({ user }: { user: UserProfile }) {
  const [activeTab, setActiveTab] = useState<TabKey>('home');
  const [profile, setProfile] = useState<ChildProfile | null>(null);
  const [profileMissing, setProfileMissing] = useState(false);
  const [locale, setLocale] = useState<'en' | 'pl'>('en');

  const [mood, setMood] = useState('okay');
  const [note, setNote] = useState('');
  const [checkIns, setCheckIns] = useState<CheckIn[]>([]);
  const [checkInFeedback, setCheckInFeedback] = useState('');
  const [checkInLoading, setCheckInLoading] = useState(false);

  const [missions, setMissions] = useState<Mission[]>([]);
  const [missionCompletions, setMissionCompletions] = useState<MissionCompletion[]>([]);
  const [missionNote, setMissionNote] = useState('');
  const [completingMissionId, setCompletingMissionId] = useState<string | null>(null);

  const [friendships, setFriendships] = useState<FriendshipEntry[]>([]);
  const [friendLabel, setFriendLabel] = useState('');
  const [friendStage, setFriendStage] = useState(stageOptions[0]);
  const [friendNote, setFriendNote] = useState('');

  const [worldType, setWorldType] = useState<'old_world' | 'new_world'>('old_world');
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [memoryTitle, setMemoryTitle] = useState('');
  const [memoryText, setMemoryText] = useState('');
  const [memoryFavorite, setMemoryFavorite] = useState(false);

  const [setupNickname, setSetupNickname] = useState('');
  const [setupBirthYear, setSetupBirthYear] = useState('');
  const [setupLocale, setSetupLocale] = useState<'en' | 'pl'>('en');
  const [setupAvatarSeed, setSetupAvatarSeed] = useState('sunny-bee');
  const [setupLoading, setSetupLoading] = useState(false);

  const text = copy[locale];

  useEffect(() => {
    initialize();
  }, []);

  useEffect(() => {
    if (profile) {
      setLocale(profile.preferredLocale);
    }
  }, [profile]);

  useEffect(() => {
    if (profileMissing) {
      return;
    }
    fetchCheckIns();
    fetchMissions();
    fetchMissionCompletions();
    fetchFriendships();
    fetchMemories(worldType);
  }, [profileMissing]);

  useEffect(() => {
    if (!profileMissing) {
      fetchMemories(worldType);
    }
  }, [worldType]);

  const redirectToLogin = () => {
    window.location.href = '/oauth2/authorization/google';
  };

  const request = async <T,>(path: string, init?: RequestInit): Promise<T> => {
    const res = await fetch(path, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
      ...init
    });

    if (res.status === 401) {
      redirectToLogin();
      throw new Error('Unauthorized');
    }

    if (!res.ok) {
      throw new Error(`Request failed: ${res.status}`);
    }

    if (res.status === 204) {
      return undefined as T;
    }

    return (await res.json()) as T;
  };

  const initialize = async () => {
    try {
      const profileData = await request<ChildProfile>('/api/child-profile');
      setProfile(profileData);
      setProfileMissing(false);
    } catch (error) {
      try {
        const response = await fetch('/api/child-profile', { credentials: 'include' });
        if (response.status === 404) {
          setProfileMissing(true);
          return;
        }
        if (response.status === 401) {
          redirectToLogin();
          return;
        }
      } catch (fallbackError) {
        console.error(fallbackError);
      }
      console.error(error);
    }
  };

  const fetchCheckIns = async () => {
    try {
      const data = await request<CheckIn[]>('/api/check-ins');
      setCheckIns(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchMissions = async () => {
    try {
      const data = await request<Mission[]>('/api/missions?active=true');
      setMissions(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchMissionCompletions = async () => {
    try {
      const data = await request<MissionCompletion[]>('/api/missions/completions');
      setMissionCompletions(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchFriendships = async () => {
    try {
      const data = await request<FriendshipEntry[]>('/api/friendships');
      setFriendships(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchMemories = async (targetWorld: 'old_world' | 'new_world') => {
    try {
      const data = await request<MemoryEntry[]>(`/api/memories?worldType=${targetWorld}`);
      setMemories(data);
    } catch (error) {
      console.error(error);
    }
  };

  const submitCheckIn = async (event: React.FormEvent) => {
    event.preventDefault();
    setCheckInLoading(true);
    setCheckInFeedback('');

    try {
      await request<CheckIn>('/api/check-ins', {
        method: 'POST',
        body: JSON.stringify({ mood, note })
      });
      setNote('');
      await fetchCheckIns();
      const responses = {
        heavy: {
          en: "That sounds hard. I'm here with you.",
          pl: 'To brzmi ciężko. Jestem obok.'
        },
        okay: {
          en: 'Thanks for checking in. Small steps are enough.',
          pl: 'Dzięki za check-in. Małe kroki wystarczą.'
        },
        good: {
          en: 'I am glad there was a lighter moment today.',
          pl: 'Fajnie, że dziś był lżejszy moment.'
        }
      } as const;
      setCheckInFeedback(responses[mood as 'heavy' | 'okay' | 'good'][locale]);
    } catch (error) {
      console.error(error);
    } finally {
      setCheckInLoading(false);
    }
  };

  const createProfile = async (event: React.FormEvent) => {
    event.preventDefault();
    setSetupLoading(true);
    try {
      const payload = {
        nickname: setupNickname,
        birthYear: setupBirthYear ? Number(setupBirthYear) : null,
        preferredLocale: setupLocale,
        avatarSeed: setupAvatarSeed
      };

      const created = await request<ChildProfile>('/api/child-profile', {
        method: 'POST',
        body: JSON.stringify(payload)
      });

      setProfile(created);
      setProfileMissing(false);
      setLocale(created.preferredLocale);
      await fetchCheckIns();
      await fetchMissions();
      await fetchMissionCompletions();
      await fetchFriendships();
      await fetchMemories(worldType);
    } catch (error) {
      console.error(error);
    } finally {
      setSetupLoading(false);
    }
  };

  const updateProfile = async (changes: Partial<ChildProfile>) => {
    try {
      const updated = await request<ChildProfile>('/api/child-profile', {
        method: 'PATCH',
        body: JSON.stringify({
          nickname: changes.nickname,
          birthYear: changes.birthYear,
          preferredLocale: changes.preferredLocale,
          avatarSeed: changes.avatarSeed,
          active: changes.active
        })
      });
      setProfile(updated);
      setLocale(updated.preferredLocale);
    } catch (error) {
      console.error(error);
    }
  };

  const completeMission = async (missionId: string) => {
    try {
      setCompletingMissionId(missionId);
      await request<MissionCompletion>(`/api/missions/${missionId}/completions`, {
        method: 'POST',
        body: JSON.stringify({ note: missionNote })
      });
      setMissionNote('');
      await fetchMissionCompletions();
    } catch (error) {
      console.error(error);
    } finally {
      setCompletingMissionId(null);
    }
  };

  const addFriendship = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await request<FriendshipEntry>('/api/friendships', {
        method: 'POST',
        body: JSON.stringify({ personLabel: friendLabel, stage: friendStage, note: friendNote })
      });
      setFriendLabel('');
      setFriendNote('');
      await fetchFriendships();
    } catch (error) {
      console.error(error);
    }
  };

  const removeFriendship = async (id: string) => {
    try {
      await request<void>(`/api/friendships/${id}`, { method: 'DELETE' });
      await fetchFriendships();
    } catch (error) {
      console.error(error);
    }
  };

  const addMemory = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await request<MemoryEntry>('/api/memories', {
        method: 'POST',
        body: JSON.stringify({
          worldType,
          title: memoryTitle,
          textContent: memoryText,
          isFavorite: memoryFavorite
        })
      });
      setMemoryTitle('');
      setMemoryText('');
      setMemoryFavorite(false);
      await fetchMemories(worldType);
    } catch (error) {
      console.error(error);
    }
  };

  const toggleFavorite = async (entry: MemoryEntry) => {
    try {
      await request<MemoryEntry>(`/api/memories/${entry.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ isFavorite: !entry.isFavorite })
      });
      await fetchMemories(worldType);
    } catch (error) {
      console.error(error);
    }
  };

  const deleteMemory = async (id: string) => {
    try {
      await request<void>(`/api/memories/${id}`, { method: 'DELETE' });
      await fetchMemories(worldType);
    } catch (error) {
      console.error(error);
    }
  };

  const totalProgress = checkIns.length + missionCompletions.length + friendships.length + memories.length;

  if (profileMissing) {
    return (
      <main className="app-shell">
        <section className="panel profile-setup-panel">
          <h1>{text.profileSetupTitle}</h1>
          <p>{text.profileSetupHint}</p>

          <form className="stack-form" onSubmit={createProfile}>
            <label>
              Nickname
              <input value={setupNickname} onChange={(event) => setSetupNickname(event.target.value)} required />
            </label>

            <label>
              Birth year
              <input
                type="number"
                value={setupBirthYear}
                onChange={(event) => setSetupBirthYear(event.target.value)}
                placeholder="2014"
              />
            </label>

            <label>
              {text.language}
              <select
                value={setupLocale}
                onChange={(event) => setSetupLocale(event.target.value as 'en' | 'pl')}
              >
                <option value="en">English</option>
                <option value="pl">Polski</option>
              </select>
            </label>

            <label>
              Avatar seed
              <input value={setupAvatarSeed} onChange={(event) => setSetupAvatarSeed(event.target.value)} />
            </label>

            <button type="submit" className="primary-button" disabled={setupLoading}>
              {setupLoading ? text.saving : text.setUp}
            </button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <header className="auth-topbar panel">
        <div className="auth-user">
          {user.avatarUrl ? <img src={user.avatarUrl} alt={user.displayName} className="avatar" /> : null}
          <div>
            <h1>{profile ? profile.nickname : user.displayName}</h1>
            <p>{user.email}</p>
          </div>
        </div>
        <div className="auth-actions">
          <label className="locale-control">
            {text.language}
            <select
              value={locale}
              onChange={(event) => {
                const nextLocale = event.target.value as 'en' | 'pl';
                setLocale(nextLocale);
                if (profile) {
                  updateProfile({ preferredLocale: nextLocale });
                }
              }}
            >
              <option value="en">EN</option>
              <option value="pl">PL</option>
            </select>
          </label>
          <a href="/logout" className="secondary-button">Log out</a>
        </div>
      </header>

      <section className="content-area">
        {activeTab === 'home' ? (
          <>
            <section className="panel">
              <h2>{text.homeTitle}</h2>
              <form className="stack-form" onSubmit={submitCheckIn}>
                <div className="mood-selector compact">
                  {Object.entries(moodEmoji).map(([value, emoji]) => (
                    <label key={value} className={`mood-option ${mood === value ? 'active' : ''}`}>
                      <input
                        type="radio"
                        name="mood"
                        value={value}
                        checked={mood === value}
                        onChange={(event) => setMood(event.target.value)}
                      />
                      <span className="mood-emoji">{emoji}</span>
                      <span className="mood-label">{value}</span>
                    </label>
                  ))}
                </div>
                <textarea
                  className="check-in-note"
                  rows={3}
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder={text.checkInPlaceholder}
                />
                <button type="submit" className="primary-button" disabled={checkInLoading}>
                  {checkInLoading ? text.saving : text.save}
                </button>
              </form>
              {checkInFeedback ? <p className="status-line">{checkInFeedback}</p> : null}
            </section>

            <section className="panel">
              <h3>Honeycomb progress</h3>
              <div className="honeycomb-grid" aria-label="Progress map">
                {Array.from({ length: 18 }).map((_, index) => (
                  <span
                    key={index}
                    className={`honeycomb-cell ${index < totalProgress ? 'filled' : ''}`}
                    title={index < totalProgress ? 'Filled' : 'Empty'}
                  />
                ))}
              </div>
            </section>

            <section className="panel">
              <h3>Recent check-ins</h3>
              {checkIns.length === 0 ? <p>{text.noItems}</p> : (
                <div className="list-stack">
                  {checkIns.map((entry, index) => (
                    <motion.article
                      key={entry.id}
                      className="list-card"
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: index * 0.04 }}
                    >
                      <div className="line-between">
                        <strong>{moodEmoji[entry.mood] ?? '🙂'} {entry.mood}</strong>
                        <span>{new Date(entry.createdAt).toLocaleDateString(locale)}</span>
                      </div>
                      {entry.note ? <p>{entry.note}</p> : null}
                    </motion.article>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'missions' ? (
          <>
            <section className="panel">
              <h2>{text.missions}</h2>
              <label>
                Completion note
                <input value={missionNote} onChange={(event) => setMissionNote(event.target.value)} />
              </label>
              <div className="list-stack">
                {missions.map((mission) => (
                  <article key={mission.id} className="list-card">
                    <h3>{mission.title}</h3>
                    <p>{mission.description}</p>
                    <button
                      className="primary-button"
                      onClick={() => completeMission(mission.id)}
                      disabled={completingMissionId === mission.id}
                    >
                      {completingMissionId === mission.id ? text.saving : 'Mark complete'}
                    </button>
                  </article>
                ))}
              </div>
            </section>

            <section className="panel">
              <h3>Completion history</h3>
              {missionCompletions.length === 0 ? <p>{text.noItems}</p> : (
                <div className="list-stack">
                  {missionCompletions.map((entry) => (
                    <article key={entry.id} className="list-card">
                      <div className="line-between">
                        <strong>{entry.title}</strong>
                        <span>{new Date(entry.completedAt).toLocaleDateString(locale)}</span>
                      </div>
                      {entry.note ? <p>{entry.note}</p> : null}
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'friendships' ? (
          <>
            <section className="panel">
              <h2>{text.friendships}</h2>
              <form className="stack-form" onSubmit={addFriendship}>
                <label>
                  Person
                  <input value={friendLabel} onChange={(event) => setFriendLabel(event.target.value)} required />
                </label>
                <label>
                  Stage
                  <select value={friendStage} onChange={(event) => setFriendStage(event.target.value)}>
                    {stageOptions.map((stage) => (
                      <option key={stage} value={stage}>{stage}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Note
                  <input value={friendNote} onChange={(event) => setFriendNote(event.target.value)} />
                </label>
                <button className="primary-button" type="submit">Add entry</button>
              </form>
            </section>

            <section className="panel">
              {friendships.length === 0 ? <p>{text.noItems}</p> : (
                <div className="list-stack">
                  {friendships.map((entry) => (
                    <article key={entry.id} className="list-card">
                      <div className="line-between">
                        <strong>{entry.personLabel}</strong>
                        <span>{entry.stage}</span>
                      </div>
                      {entry.note ? <p>{entry.note}</p> : null}
                      <button className="danger-button" onClick={() => removeFriendship(entry.id)}>Delete</button>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'memories' ? (
          <>
            <section className="panel">
              <h2>{text.memories}</h2>
              <div className="segment-control">
                <button
                  className={worldType === 'old_world' ? 'segment active' : 'segment'}
                  onClick={() => setWorldType('old_world')}
                >
                  {text.oldWorld}
                </button>
                <button
                  className={worldType === 'new_world' ? 'segment active' : 'segment'}
                  onClick={() => setWorldType('new_world')}
                >
                  {text.newWorld}
                </button>
              </div>
              <form className="stack-form" onSubmit={addMemory}>
                <label>
                  Title
                  <input value={memoryTitle} onChange={(event) => setMemoryTitle(event.target.value)} />
                </label>
                <label>
                  Story
                  <textarea
                    rows={3}
                    className="check-in-note"
                    value={memoryText}
                    onChange={(event) => setMemoryText(event.target.value)}
                    required
                  />
                </label>
                <label className="checkbox-inline">
                  <input
                    type="checkbox"
                    checked={memoryFavorite}
                    onChange={(event) => setMemoryFavorite(event.target.checked)}
                  />
                  Favorite memory
                </label>
                <button className="primary-button" type="submit">Add memory</button>
              </form>
            </section>

            <section className="panel">
              {memories.length === 0 ? <p>{text.noItems}</p> : (
                <div className="list-stack">
                  {memories.map((entry) => (
                    <article key={entry.id} className="list-card">
                      <div className="line-between">
                        <strong>{entry.title || 'Memory'}</strong>
                        <button className="ghost-button" onClick={() => toggleFavorite(entry)}>
                          {entry.isFavorite ? '★' : '☆'}
                        </button>
                      </div>
                      {entry.textContent ? <p>{entry.textContent}</p> : null}
                      <button className="danger-button" onClick={() => deleteMemory(entry.id)}>Delete</button>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'profile' && profile ? (
          <section className="panel">
            <h2>{text.profile}</h2>
            <form
              className="stack-form"
              onSubmit={(event) => {
                event.preventDefault();
                updateProfile(profile);
              }}
            >
              <label>
                Nickname
                <input
                  value={profile.nickname}
                  onChange={(event) => setProfile({ ...profile, nickname: event.target.value })}
                />
              </label>

              <label>
                Birth year
                <input
                  type="number"
                  value={profile.birthYear ?? ''}
                  onChange={(event) => setProfile({
                    ...profile,
                    birthYear: event.target.value ? Number(event.target.value) : undefined
                  })}
                />
              </label>

              <label>
                {text.language}
                <select
                  value={profile.preferredLocale}
                  onChange={(event) => setProfile({ ...profile, preferredLocale: event.target.value as 'en' | 'pl' })}
                >
                  <option value="en">English</option>
                  <option value="pl">Polski</option>
                </select>
              </label>

              <label>
                Avatar seed
                <input
                  value={profile.avatarSeed ?? ''}
                  onChange={(event) => setProfile({ ...profile, avatarSeed: event.target.value })}
                />
              </label>

              <label className="checkbox-inline">
                <input
                  type="checkbox"
                  checked={profile.active}
                  onChange={(event) => setProfile({ ...profile, active: event.target.checked })}
                />
                Profile active
              </label>

              <button className="primary-button" type="submit">{text.save}</button>
            </form>
          </section>
        ) : null}
      </section>

      <nav className="bottom-nav">
        {([
          ['home', text.home],
          ['missions', text.missions],
          ['friendships', text.friendships],
          ['memories', text.memories],
          ['profile', text.profile]
        ] as Array<[TabKey, string]>).map(([key, label]) => (
          <button
            key={key}
            className={activeTab === key ? 'nav-item active' : 'nav-item'}
            onClick={() => setActiveTab(key)}
          >
            {label}
          </button>
        ))}
      </nav>
    </main>
  );
}
