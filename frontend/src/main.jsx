import React from 'react';
import ReactDOM from 'react-dom/client';

// Bootstrap CSS – loaded before our own styles so our overrides win
import 'bootstrap/dist/css/bootstrap.min.css';
import './index.css';

import App from './App';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
