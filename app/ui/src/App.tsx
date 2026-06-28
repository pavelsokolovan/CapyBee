import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { AuthenticatedHome, UserProfile } from './AuthenticatedHome';

const highlights = [
  {
    title: 'Google sign-in',
    description: 'Parent login starts with Google OAuth and opens a private family space.'
  },
  {
    title: 'Child profile setup',
    description: 'Create one child profile with nickname and language preference in a few taps.'
  },
  {
    title: 'Daily support loop',
    description: 'Check-ins, missions, friendship notes, and memories are all stored safely for one family.'
  },
  {
    title: 'Mobile-first experience',
    description: 'Core flows are optimized for phones so children can use CapyBee in short, calm sessions.'
  }
];

const stats = [
  { label: 'Stack', value: 'Spring Boot + React' },
  { label: 'Auth', value: 'Google OAuth' },
  { label: 'Database', value: 'PostgreSQL' },
  { label: 'Deploy', value: 'Fly.io' }
];

function App() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const res = await fetch('/api/auth-status');
      if (res.ok) {
        const data = await res.json();
        setUser(data);
      }
    } catch (err) {
      console.error('Failed to check auth status:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div style={{ padding: '40px', textAlign: 'center' }}>Loading...</div>;
  }

  if (user?.authenticated) {
    return <AuthenticatedHome user={user} />;
  }

  return (
    <main className="page-shell">
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">CapyBee concept scaffold</span>
          <h1>Gentle software for a harder transition.</h1>
          <p className="lead">
            CapyBee helps children settle into a new country with gentle daily check-ins,
            tiny real-world missions, and private spaces for old and new memories.
          </p>
          <div className="hero-actions">
            <a className="primary-button" href="/oauth2/authorization/google">
              Continue with Google
            </a>
            <a className="secondary-button" href="#overview">
              View app overview
            </a>
          </div>
        </div>

        <motion.aside
          className="hero-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
        >
          <div className="hero-card-badge">First screen</div>
          <h2>Welcome to your hive.</h2>
          <p>
            A safe first release for families: simple setup, warm tone, and one small step at a time.
          </p>
          <div className="hero-card-grid">
            {stats.map((stat) => (
              <div key={stat.label} className="stat-chip">
                <span>{stat.label}</span>
                <strong>{stat.value}</strong>
              </div>
            ))}
          </div>
        </motion.aside>
      </section>

      <section className="overview" id="overview">
        <div className="section-heading">
          <span>Built for the first milestone</span>
          <h2>What the scaffold already supports</h2>
        </div>
        <div className="feature-grid">
          {highlights.map((item, index) => (
            <motion.article
              key={item.title}
              className="feature-card"
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.45, delay: index * 0.08 }}
            >
              <span className="feature-index">0{index + 1}</span>
              <h3>{item.title}</h3>
              <p>{item.description}</p>
            </motion.article>
          ))}
        </div>
      </section>

      <section className="roadmap">
        <div className="section-heading compact">
          <span>First release shape</span>
          <h2>What happens after sign-in</h2>
        </div>
        <div className="roadmap-list">
          <div>
            <strong>Profile setup</strong>
            <p>Parent creates one child profile using nickname and language preference.</p>
          </div>
          <div>
            <strong>Daily flow</strong>
            <p>Child can check in, finish a mission, and track tiny social progress privately.</p>
          </div>
          <div>
            <strong>Two worlds</strong>
            <p>Old World and New World memories are stored side-by-side, never replacing each other.</p>
          </div>
        </div>
      </section>
    </main>
  );
}

export default App;
