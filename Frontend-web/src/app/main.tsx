import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Moon, Sun } from 'lucide-react';
import AuthApp from './AuthApp';
import './styles.css';

function Root() {
  const [theme, setTheme] = useState<'light' | 'dark'>(() => localStorage.getItem('collab-modeler:theme') === 'dark' ? 'dark' : 'light');
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('collab-modeler:theme', theme);
  }, [theme]);
  return <>
    <AuthApp />
    <button className="theme-toggle" type="button" aria-label={theme === 'dark' ? 'Activar modo claro' : 'Activar modo oscuro'}
      title={theme === 'dark' ? 'Modo claro' : 'Modo oscuro'} onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
      {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
    </button>
  </>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><Root /></StrictMode>);
