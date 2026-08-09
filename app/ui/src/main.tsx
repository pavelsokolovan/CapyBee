import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

// Wake the Fly machine in the background immediately on load.
fetch('/api/auth-status', { credentials: 'include', cache: 'no-store' }).catch(() => {});

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
