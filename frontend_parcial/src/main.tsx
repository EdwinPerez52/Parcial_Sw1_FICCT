import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import AuthApp from './AuthApp';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode><AuthApp /></StrictMode>,
);
