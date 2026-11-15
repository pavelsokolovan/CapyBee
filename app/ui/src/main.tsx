import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

// The preview is absent from ordinary production builds and never changes gameplay routing.
const artPreview = (import.meta.env.DEV || import.meta.env.MODE === 'art-preview') &&
  (location.pathname === '/__art' || new URLSearchParams(location.search).get('preview') === 'art');
const Gallery = artPreview ? React.lazy(() => import('./components/game/art/GameArtGallery')) : null;

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    {Gallery ? <React.Suspense fallback={null}><Gallery /></React.Suspense> : <App />}
  </React.StrictMode>
);
