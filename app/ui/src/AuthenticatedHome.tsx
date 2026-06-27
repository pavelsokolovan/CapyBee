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

export function AuthenticatedHome({ user }: { user: UserProfile }) {
  const [mood, setMood] = useState('okay');
  const [note, setNote] = useState('');
  const [checkIns, setCheckIns] = useState<CheckIn[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchCheckIns();
  }, []);

  const redirectToLogin = () => {
    window.location.href = '/oauth2/authorization/google';
  };

  const fetchCheckIns = async () => {
    try {
      const res = await fetch('/api/check-ins', { credentials: 'include' });
      if (res.status === 401) {
        redirectToLogin();
        return;
      }
      if (res.ok) {
        const data = await res.json();
        setCheckIns(data);
      }
    } catch (err) {
      console.error('Failed to fetch check-ins:', err);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const res = await fetch('/api/check-ins', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mood, note })
      });

      if (res.status === 401) {
        redirectToLogin();
        return;
      }

      if (res.ok) {
        setNote('');
        await fetchCheckIns();
      }
    } catch (err) {
      console.error('Failed to create check-in:', err);
    } finally {
      setLoading(false);
    }
  };

  const moodEmoji = {
    heavy: '😔',
    okay: '😐',
    good: '😊'
  } as Record<string, string>;

  return (
    <main className="authenticated-shell">
      <header className="app-header">
        <div className="header-content">
          <h1>Welcome back, {user.displayName}!</h1>
          <div className="user-profile">
            {user.avatarUrl && (
              <img src={user.avatarUrl} alt={user.displayName} className="avatar" />
            )}
            <div>
              <p className="user-email">{user.email}</p>
                <a href="/logout" className="logout-link">
                Log out
              </a>
            </div>
          </div>
        </div>
      </header>

      <section className="check-in-form-section">
        <div className="form-container">
          <h2>How are you today?</h2>
          <form onSubmit={handleSubmit} className="check-in-form">
            <div className="mood-selector">
              {Object.entries(moodEmoji).map(([value, emoji]) => (
                <label key={value} className={`mood-option ${mood === value ? 'active' : ''}`}>
                  <input
                    type="radio"
                    name="mood"
                    value={value}
                    checked={mood === value}
                    onChange={(e) => setMood(e.target.value)}
                  />
                  <span className="mood-emoji">{emoji}</span>
                  <span className="mood-label">{value}</span>
                </label>
              ))}
            </div>

            <textarea
              className="check-in-note"
              placeholder="Add a note (optional)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={3}
            />

            <button type="submit" className="submit-button" disabled={loading}>
              {loading ? 'Saving...' : 'Save check-in'}
            </button>
          </form>
        </div>
      </section>

      <section className="check-in-history">
        <h2>Your check-ins</h2>
        {checkIns.length === 0 ? (
          <p className="empty-state">No check-ins yet. Start with one above!</p>
        ) : (
          <div className="check-in-list">
            {checkIns.map((checkIn, index) => (
              <motion.div
                key={checkIn.id}
                className="check-in-item"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
              >
                <div className="check-in-header">
                  <span className="check-in-mood">{moodEmoji[checkIn.mood] || '🤷'}</span>
                  <span className="check-in-time">
                    {new Date(checkIn.createdAt).toLocaleString()}
                  </span>
                </div>
                {checkIn.note && <p className="check-in-note-text">{checkIn.note}</p>}
              </motion.div>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
