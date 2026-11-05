import React, { useState } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { ErrorBoundary } from './components/ErrorBoundary';
import './styles.css';

/**
 * After a person signs out, the whole tree is rebuilt on the root URL: no page state, router state or cached data of
 * the last person survives, and nothing reloads (so no white flash and no second start-up probe).
 */
function Root() {
  const [signedOut, setSignedOut] = useState(0);
  return (
    <BrowserRouter key={signedOut}>
      <App
        key={signedOut}
        initialMe={signedOut > 0 ? null : undefined}
        onSignedOut={() => {
          window.history.replaceState(null, '', '/');
          setSignedOut((n) => n + 1);
        }}
      />
    </BrowserRouter>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <Root />
    </ErrorBoundary>
  </React.StrictMode>,
);
