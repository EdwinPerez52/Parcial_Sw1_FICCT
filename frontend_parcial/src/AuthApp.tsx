import { FormEvent, useEffect, useMemo, useState } from 'react';
import { ArrowRight, FolderOpen, LogIn, LogOut, Plus, ShieldCheck } from 'lucide-react';
import App from './App';
import { CurrentUser, ProjectSummary, authApi, diagramApi } from './api';

const messageOf = (cause: unknown) => cause instanceof Error ? cause.message : 'Ocurrió un error inesperado.';
const navigate = (path: string) => { window.location.assign(path); };

export default function AuthApp() {
  const [user, setUser] = useState<CurrentUser>();
  const [error, setError] = useState('');
  const path = window.location.pathname;
  const query = useMemo(() => new URLSearchParams(window.location.search), []);
  const joinToken = path.startsWith('/join/') ? decodeURIComponent(path.substring('/join/'.length)) : query.get('invitation') ?? '';

  useEffect(() => { void authApi.me().then(setUser).catch(cause => setError(messageOf(cause))); }, []);

  if (error) return <StatusPage title="No pudimos iniciar la aplicación" message={error} action={() => window.location.reload()} actionText="Reintentar" />;
  if (!user) return <StatusPage title="Cargando Collab Modeler" message="Comprobando tu sesión de forma segura…" busy />;
  if (path === '/verify' && query.get('token')) return <VerifyScreen token={query.get('token')!} />;
  if (path === '/reset-password' && query.get('token')) return <ResetScreen token={query.get('token')!} />;
  if (!user.authenticated) return <PublicAuth invitationToken={joinToken} forcedRegistration={path === '/register'} onAuthenticated={() => void authApi.me().then(setUser)} />;
  if (!user.verified) return <VerificationPending user={user} onLogout={() => logout(setUser)} />;
  if (joinToken) return <JoinScreen token={joinToken} />;
  const diagramId = query.get('diagram');
  if (diagramId) return <EditorLoader diagramId={diagramId} user={user} onLogout={() => logout(setUser)} />;
  return <Projects user={user} onLogout={() => logout(setUser)} />;
}

function PublicAuth({ invitationToken, forcedRegistration, onAuthenticated }: { invitationToken: string; forcedRegistration: boolean; onAuthenticated: () => void }) {
  const [mode, setMode] = useState<'login' | 'register' | 'forgot'>(forcedRegistration ? 'register' : 'login');
  const [error, setError] = useState(new URLSearchParams(location.search).get('authError') ?? '');
  const [notice, setNotice] = useState('');
  const [invitationValid, setInvitationValid] = useState<boolean | undefined>(invitationToken ? undefined : false);
  useEffect(() => {
    if (invitationToken) void authApi.invitation(invitationToken).then(value => setInvitationValid(value.valid)).catch(() => setInvitationValid(false));
  }, [invitationToken]);
  const googleUrl = `/api/v1/auth/google${invitationToken ? `?invitationToken=${encodeURIComponent(invitationToken)}` : ''}`;

  const submitLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setError(''); const data = new FormData(event.currentTarget);
    try { await authApi.login(String(data.get('email')), String(data.get('password'))); onAuthenticated(); }
    catch (cause) { setError(messageOf(cause)); }
  };
  const submitRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setError(''); const data = new FormData(event.currentTarget);
    try {
      const result = await authApi.register(String(data.get('fullName')), String(data.get('email')), String(data.get('password')), String(data.get('confirmation')), invitationToken);
      setNotice(result.message); setMode('login');
    } catch (cause) { setError(messageOf(cause)); }
  };
  const forgot = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setError(''); const data = new FormData(event.currentTarget);
    try { setNotice((await authApi.forgot(String(data.get('email')))).message); }
    catch (cause) { setError(messageOf(cause)); }
  };

  return <main className="auth-layout">
    <section className="auth-hero">
      <div className="auth-logo">CM</div><p className="eyebrow">Modelado colaborativo seguro</p>
      <h1>Diseña, valida y comparte tus modelos UML.</h1>
      <p>Tu trabajo se guarda por revisiones y cada proyecto conserva sus permisos. No almacenamos credenciales ni tokens de sesión en el navegador.</p>
    </section>
    <section className="auth-card" aria-busy={Boolean(invitationToken) && invitationValid === undefined}>
      <h2>{mode === 'register' ? 'Completa tu invitación' : mode === 'forgot' ? 'Recupera tu contraseña' : 'Inicia sesión'}</h2>
      {invitationToken && invitationValid === false && <div className="form-error" role="alert">La invitación no existe, venció o fue revocada.</div>}
      {error && <div className="form-error" role="alert">{error}</div>}{notice && <div className="form-notice" role="status">{notice}</div>}
      {mode === 'login' && <>
        <a className="google-button" href={googleUrl}>Continuar con Google</a><div className="divider"><span>o con correo</span></div>
        <form className="auth-form" onSubmit={submitLogin}>
          <label>Correo electrónico<input name="email" type="email" autoComplete="email" required /></label>
          <label>Contraseña<input name="password" type="password" autoComplete="current-password" required /></label>
          <button className="primary" type="submit"><LogIn size={17} /> Entrar</button>
        </form>
        <button className="text-button" onClick={() => { setMode('forgot'); setError(''); }}>Olvidé mi contraseña</button>
        {invitationToken && invitationValid && <button className="text-button" onClick={() => { setMode('register'); setError(''); }}>Crear una cuenta con esta invitación</button>}
      </>}
      {mode === 'register' && <form className="auth-form" onSubmit={submitRegister}>
        <label>Nombre completo<input name="fullName" autoComplete="name" minLength={2} maxLength={180} required /></label>
        <label>Correo electrónico<input name="email" type="email" autoComplete="email" required /></label>
        <label>Contraseña<input name="password" type="password" autoComplete="new-password" minLength={10} required /><small>10 caracteres, mayúscula, minúscula y número.</small></label>
        <label>Confirma la contraseña<input name="confirmation" type="password" autoComplete="new-password" minLength={10} required /></label>
        <button className="primary" disabled={!invitationValid}>Registrarme</button>
        <button className="text-button" type="button" onClick={() => setMode('login')}>Ya tengo cuenta</button>
      </form>}
      {mode === 'forgot' && <form className="auth-form" onSubmit={forgot}>
        <p>Te enviaremos instrucciones si el correo corresponde a una cuenta verificada.</p>
        <label>Correo electrónico<input name="email" type="email" autoComplete="email" required /></label>
        <button className="primary">Enviar instrucciones</button>
        <button className="text-button" type="button" onClick={() => setMode('login')}>Volver</button>
      </form>}
    </section>
  </main>;
}

function VerifyScreen({ token }: { token: string }) {
  const [state, setState] = useState<'working' | 'done' | 'error'>('working'); const [message, setMessage] = useState('Verificando tu correo…');
  useEffect(() => { void authApi.verify(token).then(result => { setState('done'); setMessage('Tu correo quedó verificado.'); setTimeout(() => navigate(result.diagramId ? `/?diagram=${result.diagramId}` : '/'), 700); }).catch(cause => { setState('error'); setMessage(messageOf(cause)); }); }, [token]);
  return <StatusPage title={state === 'done' ? 'Cuenta verificada' : 'Verificación de correo'} message={message} busy={state === 'working'} action={state === 'error' ? () => navigate('/') : undefined} actionText="Volver al inicio" />;
}

function ResetScreen({ token }: { token: string }) {
  const [error, setError] = useState(''); const [done, setDone] = useState(false);
  const submit = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const data = new FormData(event.currentTarget); try { await authApi.reset(token, String(data.get('password')), String(data.get('confirmation'))); setDone(true); } catch (cause) { setError(messageOf(cause)); } };
  if (done) return <StatusPage title="Contraseña actualizada" message="Ya puedes iniciar sesión con tu nueva contraseña." action={() => navigate('/')} actionText="Iniciar sesión" />;
  return <main className="center-page"><section className="auth-card"><h2>Nueva contraseña</h2>{error && <div className="form-error" role="alert">{error}</div>}<form className="auth-form" onSubmit={submit}><label>Contraseña<input name="password" type="password" minLength={10} required /></label><label>Confirmación<input name="confirmation" type="password" minLength={10} required /></label><button className="primary">Guardar contraseña</button></form></section></main>;
}

function VerificationPending({ user, onLogout }: { user: CurrentUser; onLogout: () => void }) {
  const [message, setMessage] = useState(''); const [error, setError] = useState('');
  return <StatusPage title="Verifica tu correo" message={`Enviamos un enlace a ${user.email}. Debes verificarlo antes de editar.`}>
    {error && <div className="form-error" role="alert">{error}</div>}{message && <div className="form-notice">{message}</div>}
    <button className="primary" onClick={() => void authApi.resendVerification(user.email!).then(value => setMessage(value.message)).catch(cause => setError(messageOf(cause)))}>Reenviar correo</button>
    <button className="text-button" onClick={onLogout}>Cerrar sesión</button>
  </StatusPage>;
}

function JoinScreen({ token }: { token: string }) {
  const [error, setError] = useState('');
  useEffect(() => { void diagramApi.join(token).then(result => navigate(`/?diagram=${result.diagramId}`)).catch(cause => setError(messageOf(cause))); }, [token]);
  return <StatusPage title="Abriendo invitación" message={error || 'Validando el enlace y tus permisos…'} busy={!error} action={error ? () => navigate('/') : undefined} actionText="Ir a mis proyectos" />;
}

function Projects({ user, onLogout }: { user: CurrentUser; onLogout: () => void }) {
  const [projects, setProjects] = useState<ProjectSummary[]>(); const [error, setError] = useState(''); const [name, setName] = useState('');
  const load = () => void diagramApi.list().then(setProjects).catch(cause => setError(messageOf(cause)));
  useEffect(load, []);
  const create = async (event: FormEvent) => { event.preventDefault(); setError(''); try { const diagram = await diagramApi.create(name); navigate(`/?diagram=${diagram.id}`); } catch (cause) { setError(messageOf(cause)); } };
  return <main className="projects-page"><header className="projects-header"><div><span className="auth-logo small">CM</span><strong>Collab Modeler</strong></div><div><span>{user.fullName}</span><button className="secondary" onClick={onLogout}><LogOut size={16} /> Salir</button></div></header><section className="projects-content"><p className="eyebrow">Espacio de trabajo</p><h1>Mis proyectos</h1><form className="new-project" onSubmit={create}><input value={name} onChange={event => setName(event.target.value)} placeholder="Nombre del nuevo diagrama" maxLength={180} required /><button className="primary"><Plus size={17} /> Crear</button></form>{error && <div className="form-error" role="alert">{error}</div>}{!projects ? <p>Cargando proyectos…</p> : projects.length === 0 ? <div className="empty-projects"><FolderOpen /><h2>Aún no tienes diagramas</h2><p>Crea el primero o abre un enlace de invitación.</p></div> : <div className="project-grid">{projects.map(project => <button key={project.id} onClick={() => navigate(`/?diagram=${project.id}`)}><span className="project-icon"><FolderOpen /></span><strong>{project.name}</strong><small>{project.role === 'OWNER' ? 'Propietario' : project.role === 'EDITOR' ? 'Editor' : 'Lector'} · revisión {project.revision}</small><ArrowRight /></button>)}</div>}</section></main>;
}

function EditorLoader({ diagramId, user, onLogout }: { diagramId: string; user: CurrentUser; onLogout: () => void }) {
  const [project, setProject] = useState<ProjectSummary>(); const [error, setError] = useState('');
  useEffect(() => { void diagramApi.list().then(items => { const found = items.find(item => item.id === diagramId); if (!found) setError('No tienes acceso a este diagrama.'); else setProject(found); }).catch(cause => setError(messageOf(cause))); }, [diagramId]);
  if (error) return <StatusPage title="No se pudo abrir el diagrama" message={error} action={() => navigate('/')} actionText="Ir a mis proyectos" />;
  if (!project) return <StatusPage title="Cargando diagrama" message="Comprobando permisos…" busy />;
  return <App projectId={diagramId} userName={user.fullName!} role={project.role} onBack={() => navigate('/')} onLogout={onLogout} />;
}

function StatusPage({ title, message, busy, action, actionText, children }: { title: string; message: string; busy?: boolean; action?: () => void; actionText?: string; children?: React.ReactNode }) {
  return <main className="center-page"><section className="status-card">{busy ? <div className="spinner" aria-label="Cargando" /> : <ShieldCheck size={42} />}<h1>{title}</h1><p>{message}</p>{children}{action && <button className="primary" onClick={action}>{actionText}</button>}</section></main>;
}

async function logout(setUser: (user: CurrentUser) => void) {
  try { await authApi.logout(); } finally { setUser({ authenticated: false, email: null, fullName: null, verified: false, platformAdmin: false, csrfToken: '' }); navigate('/'); }
}
