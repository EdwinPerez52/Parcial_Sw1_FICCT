import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Activity, CheckCircle2, Download, MessageSquare, RotateCcw, Save, Send, Users } from 'lucide-react';
import { ActivityItem, CommentItem, MemberItem, VersionItem, collaborationApi, diagramApi, downloadGeneratedBackend, downloadMobileSpec } from './api';
import { DiagramModel } from './domain';

const messageOf = (cause: unknown) => cause instanceof Error ? cause.message : 'No se pudo completar la acción.';

export function CommentsPanel({ diagramId, diagram, role, eventSequence }: { diagramId: string; diagram: DiagramModel; role: string; eventSequence: number }) {
  const [comments, setComments] = useState<CommentItem[]>([]); const [activity, setActivity] = useState<ActivityItem[]>([]);
  const [target, setTarget] = useState('DIAGRAM:'); const [error, setError] = useState(''); const [replyTo, setReplyTo] = useState<string>();
  const load = () => { void collaborationApi.comments(diagramId).then(setComments).catch(cause => setError(messageOf(cause))); void collaborationApi.activity(diagramId).then(setActivity).catch(() => undefined); };
  useEffect(load, [diagramId, eventSequence]);
  const options = useMemo(() => [
    { value: 'DIAGRAM:', label: 'Diagrama completo' },
    ...diagram.classes.flatMap(item => [{ value: `CLASS:${item.id}`, label: `Clase: ${item.name}` }, ...item.attributes.map(attribute => ({ value: `ATTRIBUTE:${attribute.id}`, label: `Atributo: ${item.name}.${attribute.name}` }))]),
    ...diagram.associations.map(item => ({ value: `ASSOCIATION:${item.id}`, label: `Asociación: ${item.name || item.id.slice(0, 8)}` })),
    ...diagram.generalizations.map(item => ({ value: `GENERALIZATION:${item.id}`, label: `Generalización: ${item.id.slice(0, 8)}` })),
  ], [diagram]);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const [targetType, targetId] = target.split(':');
    try { await collaborationApi.comment(diagramId, { targetType: targetType as CommentItem['targetType'], targetId: targetId || undefined, body: String(data.get('body')) }); form.reset(); load(); }
    catch (cause) { setError(messageOf(cause)); }
  };
  const reply = async (event: FormEvent<HTMLFormElement>, parentCommentId: string) => {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    try { await collaborationApi.comment(diagramId, { targetType: 'DIAGRAM', parentCommentId, body: String(data.get('body')) }); setReplyTo(undefined); load(); }
    catch (cause) { setError(messageOf(cause)); }
  };
  const roots = comments.filter(item => !item.parentCommentId);
  return <div className="collaboration-panel">
    <form className="comment-form" onSubmit={submit}><label>Comentar sobre<select value={target} onChange={event => setTarget(event.target.value)}>{options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label><textarea name="body" maxLength={4000} placeholder="Escribe un comentario…" required /><button><Send size={14} /> Publicar</button></form>
    {error && <div className="validation-message" role="alert">{error}</div>}
    <section className="thread-list"><h3><MessageSquare size={15} /> Conversaciones</h3>{roots.length === 0 && <p className="empty-copy">Todavía no hay comentarios.</p>}{roots.map(root => <article className={root.resolved ? 'thread resolved' : 'thread'} key={root.id}>
      <header><strong>{root.authorName}</strong><time>{new Date(root.createdAt).toLocaleString()}</time></header><p>{root.body}</p><small>{options.find(value => value.value === `${root.targetType}:${root.targetId ?? ''}`)?.label ?? root.targetType}</small>
      <div className="thread-actions"><button type="button" onClick={() => setReplyTo(replyTo === root.id ? undefined : root.id)}>Responder</button>{role !== 'READER' && <button type="button" onClick={() => void collaborationApi.resolveComment(diagramId, root.id, !root.resolved, root.version).then(load).catch(cause => setError(messageOf(cause)))}><CheckCircle2 size={13} /> {root.resolved ? 'Reabrir' : 'Resolver'}</button>}</div>
      {comments.filter(item => item.parentCommentId === root.id).map(item => <div className="reply" key={item.id}><strong>{item.authorName}</strong><p>{item.body}</p><time>{new Date(item.createdAt).toLocaleString()}</time></div>)}
      {replyTo === root.id && <form className="reply-form" onSubmit={event => void reply(event, root.id)}><input name="body" placeholder="Responder…" required maxLength={4000} /><button><Send size={13} /></button></form>}
    </article>)}</section>
    <section className="activity-list"><h3><Activity size={15} /> Actividad reciente</h3>{activity.map(item => <div key={item.id}><span><strong>{item.actorName}</strong> {item.summary.toLowerCase()}</span><time>{new Date(item.createdAt).toLocaleString()}</time></div>)}</section>
  </div>;
}

export function VersionsPanel({ diagramId, diagram, pendingCount, eventSequence, accept, canEdit }: { diagramId: string; diagram: DiagramModel; pendingCount: number; eventSequence: number; accept: (value: DiagramModel) => void; canEdit: boolean }) {
  const [versions, setVersions] = useState<VersionItem[]>([]); const [preview, setPreview] = useState<VersionItem>(); const [error, setError] = useState('');
  const load = () => void collaborationApi.versions(diagramId).then(setVersions).catch(cause => setError(messageOf(cause)));
  useEffect(load, [diagramId, eventSequence]);
  const createVersion = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const form = event.currentTarget; const label = String(new FormData(form).get('label')); try { await collaborationApi.createVersion(diagramId, label); form.reset(); load(); } catch (cause) { setError(messageOf(cause)); } };
  const restore = async () => { if (!preview || pendingCount) return; try { const restored = await collaborationApi.restoreVersion(diagramId, preview.id, diagram.revision); accept(restored); setPreview(undefined); load(); } catch (cause) { setError(messageOf(cause)); } };
  return <div className="versions-panel">{canEdit && <form className="version-form" onSubmit={createVersion}><input name="label" maxLength={180} placeholder="Nombre del hito" required /><button><Save size={14} /> Guardar hito</button></form>}
    {pendingCount > 0 && <p className="pending-note">Sincroniza o resuelve {pendingCount} cambio(s) antes de restaurar.</p>}{error && <div className="validation-message">{error}</div>}
    <div className="version-list">{versions.length === 0 && <p className="empty-copy">No hay hitos guardados.</p>}{versions.map(item => <button key={item.id} onClick={() => setPreview(item)}><strong>{item.label}</strong><span>Revisión {item.sourceRevision} · {item.authorName}</span><time>{new Date(item.createdAt).toLocaleString()}</time></button>)}</div>
    {preview && <div className="modal-backdrop"><section className="proposal-modal"><h2>{canEdit ? 'Restaurar' : 'Vista previa de'} “{preview.label}”</h2><p>{canEdit ? 'Se creará una revisión nueva; no se borrará el historial anterior.' : 'Tu rol permite consultar este hito, pero no restaurarlo.'}</p><div className="snapshot-summary"><span>{preview.snapshot.classes.length} clases</span><span>{preview.snapshot.associations.length} asociaciones</span><span>{preview.snapshot.generalizations.length} herencias</span></div><footer><button type="button" className="secondary" onClick={() => setPreview(undefined)}>Cerrar</button><button type="button" className="secondary" onClick={() => void downloadGeneratedBackend(diagramId, preview.id)}><Download size={15} /> Descargar backend (ZIP)</button><button type="button" className="secondary" onClick={() => void downloadMobileSpec(diagramId, preview.id)}><Download size={15} /> Descargar spec móvil</button>{canEdit && <button className="primary" disabled={pendingCount > 0} onClick={() => void restore()}><RotateCcw size={15} /> Restaurar como nueva revisión</button>}</footer></section></div>}
  </div>;
}

export function MembersPanel({ diagramId, owner }: { diagramId: string; owner: boolean }) {
  const [members, setMembers] = useState<MemberItem[]>([]); const [error, setError] = useState('');
  const load = () => void diagramApi.members(diagramId).then(setMembers).catch(cause => setError(messageOf(cause)));
  useEffect(load, [diagramId]);
  const update = async (member: MemberItem, role: 'EDITOR' | 'READER') => {
    try { await diagramApi.updateMember(diagramId, member.id, role); load(); } catch (cause) { setError(messageOf(cause)); }
  };
  const remove = async (member: MemberItem) => {
    if (!window.confirm(`¿Quitar a ${member.displayName} del diagrama?`)) return;
    try { await diagramApi.removeMember(diagramId, member.id); load(); } catch (cause) { setError(messageOf(cause)); }
  };
  return <div className="collaboration-panel"><h3><Users size={15} /> Miembros</h3>{error && <div className="validation-message" role="alert">{error}</div>}
    <div className="version-list">{members.map(member => <article className="thread" key={member.id}><header><strong>{member.displayName}</strong><span>{member.role}</span></header>
      {owner && member.role !== 'OWNER' && <div className="thread-actions"><select aria-label={`Rol de ${member.displayName}`} value={member.role === 'PENDING' ? 'READER' : member.role} onChange={event => void update(member, event.target.value as 'EDITOR' | 'READER')}><option value="EDITOR">Editor</option><option value="READER">Lector</option></select><button type="button" onClick={() => void remove(member)}>Quitar</button></div>}
    </article>)}</div></div>;
}
