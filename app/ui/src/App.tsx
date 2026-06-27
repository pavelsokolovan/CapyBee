import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { AuthenticatedHome, UserProfile } from './AuthenticatedHome';

const highlights = [
  {
    title: 'Google sign-in',
    description: 'Simple account entry with Google OAuth so the app can keep a secure identity from day one.'
  },
  {
    title: 'First page',
    description: 'A calm landing screen that explains the app and gives one clear way to begin.'
  },
  {
    title: 'Data storage',
    description: 'User profile data, check-ins, and mission history stored in PostgreSQL tables.'
  },
  {
    title: 'Fly.io ready',
    description: 'One container, one deployment target, and environment-driven configuration.'
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
            CapyBee gives children a calm place to sign in with Google, land on a welcoming first page,
            and begin storing useful personal data in the app.
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
            The landing page introduces the mood of the app, then hands the user off to sign-in without
            extra clutter.
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
          <span>Next tables and screens</span>
          <h2>Where the app can grow from here</h2>
        </div>
        <div className="roadmap-list">
          <div>
            <strong>Auth bootstrap</strong>
            <p>Create a user record on first Google sign-in and link it to the external identity.</p>
          </div>
          <div>
            <strong>Personal data</strong>
            <p>Store check-ins, mission progress, and lightweight profile data in PostgreSQL.</p>
          </div>
          <div>
            <strong>Deployment</strong>
            <p>Build the UI into the backend image so Fly.io can run the full app as one service.</p>
          </div>
        </div>
      </section>
    </main>
  );
}

export default App;
