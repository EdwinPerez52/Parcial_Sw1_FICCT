import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Background, Connection, Controls, Edge, MarkerType, MiniMap, Node, ReactFlow } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Bot, Braces, Camera, Cloud, Download, FileUp, History, ListTree, LogOut, MessageSquare, Mic, Plus, Redo2, Share2, Undo2, Users, XCircle } from 'lucide-react';
import { ClassNode } from './ClassNode';
import { EnumerationNode } from './EnumerationNode';
import { PropertyPanel } from './PropertyPanel';
import { useDiagramStore } from './store';
import { AssistantProposal, ImageProposal, XmiImportPreview, analyzeDiagramImage, assistantApi, diagramApi, downloadGeneratedBackend, downloadXmi, previewXmi } from './api';
import { dictate } from './speech';
import { CommentsPanel, MembersPanel, VersionsPanel } from './CollaborationPanel';

const nodeTypes = { classNode: ClassNode, enumerationNode: EnumerationNode };

export default function App({ projectId, userName, role, onBack, onLogout }: { projectId: string; userName: string; role: string; onBack: () => void; onLogout: () => void }) {
  const canEdit = role === 'OWNER' || role === 'EDITOR';
  const store = useDiagramStore();
  const {
    diagram, diagramId, syncState, lastError, initialize, selectedIds, selectElements,
    addClass, addEnumeration, updateEnumeration, moveClass, moveSelected, addAssociation, addAttribute,
    deleteClass, deleteSelected, undo, redo, participants, conflicts, pendingOperations, eventSequence,
    retryConflict, discardConflict, reapplyConflict, sendPresence, acceptAuthoritative,
    replaceFromImport, acceptAssistant,
  } = store;
  const [command, setCommand] = useState('');
  const [assistantMessage, setAssistantMessage] = useState('Prueba: “crea una clase Producto”');
  const [assistantProposal, setAssistantProposal] = useState<AssistantProposal>();
  const [assistantBusy, setAssistantBusy] = useState(false);
  const [panel, setPanel] = useState<'properties' | 'assistant' | 'comments' | 'history' | 'members'>('properties');
  const [imageProposal, setImageProposal] = useState<ImageProposal>();
  const [analyzingImage, setAnalyzingImage] = useState(false);
  const [xmiPreview, setXmiPreview] = useState<XmiImportPreview>();
  const [analyzingXmi, setAnalyzingXmi] = useState(false);
  const imageInput = useRef<HTMLInputElement>(null);
  const xmiInput = useRef<HTMLInputElement>(null);
  const lastCursorSent = useRef(0);

  useEffect(() => {
    let dispose: () => void = () => undefined;
    void initialize(projectId).then(callback => { dispose = callback; });
    return () => dispose();
  }, [initialize, projectId]);

  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target?.matches('input, textarea, select, [contenteditable=true]')) return;
      if (!canEdit) return;
      if ((event.key === 'Delete' || event.key === 'Backspace') && selectedIds.length) {
        event.preventDefault();
        if (window.confirm(`¿Eliminar ${selectedIds.length} elemento(s) seleccionado(s)?`)) {
          try { deleteSelected(); } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo eliminar.'); }
        }
      } else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
        event.preventDefault();
        selectElements([...diagram.classes, ...diagram.enumerations].map(item => item.id));
      } else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
        event.preventDefault(); event.shiftKey ? redo() : undo();
      } else if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key) && selectedIds.length) {
        event.preventDefault();
        const step = event.shiftKey ? 20 : 5;
        moveSelected({
          x: event.key === 'ArrowLeft' ? -step : event.key === 'ArrowRight' ? step : 0,
          y: event.key === 'ArrowUp' ? -step : event.key === 'ArrowDown' ? step : 0,
        });
      }
    };
    window.addEventListener('keydown', keydown);
    return () => window.removeEventListener('keydown', keydown);
  }, [canEdit, deleteSelected, diagram.classes, diagram.enumerations, moveSelected, redo, selectElements, selectedIds, undo]);

  const share = async () => {
    if (!diagramId) return;
    try {
      const link = await diagramApi.share(diagramId);
      await navigator.clipboard.writeText(`${location.origin}${link.path}`);
      setAssistantMessage('Enlace de edición copiado.');
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo compartir.'); }
  };

  const revokeShare = async () => {
    if (!diagramId || !window.confirm('¿Revocar el enlace actual?')) return;
    try { await diagramApi.revokeShare(diagramId); setAssistantMessage('El enlace compartido fue revocado.'); }
    catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo revocar el enlace.'); }
  };

  const generate = async () => {
    if (!diagramId) return;
    try { await downloadGeneratedBackend(diagramId); setAssistantMessage('Backend generado desde la revisión actual.'); }
    catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo generar.'); }
  };

  const inspectImage = async (file?: File) => {
    if (!file) return;
    setAnalyzingImage(true);
    try { setImageProposal(await analyzeDiagramImage(file)); setAssistantMessage('Revisa la propuesta antes de incorporarla.'); }
    catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo analizar la fotografía.'); }
    finally { setAnalyzingImage(false); }
  };

  const acceptImageProposal = () => {
    if (!imageProposal) return;
    const imported = new Map<string, string>();
    try {
      imageProposal.classes.forEach(candidate => {
        const created = addClass(candidate.name); imported.set(candidate.name.toLowerCase(), created.id);
        candidate.attributes.forEach(attribute => addAttribute(created.id, {
          ...attribute,
          primaryKey: attribute.primaryKey ?? false,
          required: attribute.required ?? false,
          unique: attribute.unique ?? false,
        }));
      });
      imageProposal.associations.forEach(link => {
        const sourceId = imported.get(link.source.toLowerCase());
        const targetId = imported.get(link.target.toLowerCase());
        if (sourceId && targetId) addAssociation({
          sourceId, targetId, sourceCardinality: link.sourceCardinality,
          targetCardinality: link.targetCardinality, name: link.name,
          sourceRole: '', targetRole: '', owningSide: 'SOURCE',
        });
      });
      setImageProposal(undefined);
      setAssistantMessage('La propuesta fue incorporada; ya puedes corregirla.');
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'La propuesta contiene datos inválidos.'); }
  };

  const inspectXmi = async (file?: File) => {
    if (!file || !diagramId) return;
    if (file.size > 5_000_000) { setAssistantMessage('El XMI supera el límite de 5 MB.'); return; }
    setAnalyzingXmi(true);
    try {
      setXmiPreview(await previewXmi(diagramId, file));
      setAssistantMessage('Revisa el contenido XMI antes de reemplazar el modelo actual.');
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo leer el archivo XMI.'); }
    finally { setAnalyzingXmi(false); if (xmiInput.current) xmiInput.current.value = ''; }
  };

  const acceptXmi = () => {
    if (!xmiPreview) return;
    try {
      replaceFromImport(xmiPreview.diagram);
      setXmiPreview(undefined);
      setAssistantMessage('Importación XMI aplicada como un único lote. Puedes deshacerla con Ctrl+Z.');
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'El XMI no se pudo incorporar.'); }
  };

  const exportXmi = async () => {
    if (!diagramId) return;
    try { await downloadXmi(diagramId, diagram.name); setAssistantMessage('XMI 2.1 exportado.'); }
    catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo exportar XMI.'); }
  };

  const nodes = useMemo<Node[]>(() => [
    ...diagram.classes.map(item => ({
      id: item.id, type: 'classNode', position: item.position,
      data: item as unknown as Record<string, unknown>, selected: selectedIds.includes(item.id),
    })),
    ...diagram.enumerations.map(item => ({
      id: item.id, type: 'enumerationNode', position: item.position,
      data: item as unknown as Record<string, unknown>, selected: selectedIds.includes(item.id),
    })),
  ], [diagram.classes, diagram.enumerations, selectedIds]);

  const edges = useMemo<Edge[]>(() => [
    ...diagram.associations.map(item => ({
      id: item.id, source: item.sourceId, target: item.targetId,
      label: `${item.sourceRole ? item.sourceRole + ' ' : ''}${item.sourceCardinality} — ${item.targetCardinality}${item.targetRole ? ' ' + item.targetRole : ''}`,
      markerEnd: { type: MarkerType.ArrowClosed }, selected: selectedIds.includes(item.id),
      data: { kind: 'association', name: item.name },
    })),
    ...diagram.generalizations.map(item => ({
      id: item.id, source: item.childId, target: item.parentId, type: 'straight',
      label: 'hereda', markerEnd: { type: MarkerType.ArrowClosed, color: '#475569' },
      style: { stroke: '#475569', strokeWidth: 2 }, selected: selectedIds.includes(item.id),
      data: { kind: 'generalization' },
    })),
  ], [diagram.associations, diagram.generalizations, selectedIds]);

  const handleSelectionChange = useCallback(({ nodes: selectedNodes, edges: selectedEdges }: { nodes: Node[]; edges: Edge[] }) => {
    selectElements([...selectedNodes, ...selectedEdges].map(item => item.id));
  }, [selectElements]);

  const applyAssistantProposal = async (proposal: AssistantProposal, confirmed: boolean) => {
    if (!diagramId) return;
    const before = store.diagram;
    setAssistantBusy(true);
    try {
      const result = await assistantApi.apply(diagramId, proposal.proposalId, confirmed);
      acceptAssistant(result.operation, result.diagram, before); setAssistantProposal(undefined);
      setAssistantMessage(`Cambio aplicado mediante ${result.provider}. Puedes deshacerlo.`);
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo aplicar la propuesta.'); }
    finally { setAssistantBusy(false); }
  };

  const executeCommand = async (event: FormEvent) => {
    event.preventDefault();
    if (!diagramId || !command.trim()) return;
    if (pendingOperations.some(value => value.status !== 'acknowledged')) {
      setAssistantMessage('Espera a que terminen de sincronizarse los cambios pendientes.'); return;
    }
    setAssistantBusy(true);
    try {
      const proposal = await assistantApi.interpret(diagramId, command.trim());
      if (proposal.requiresConfirmation) { setAssistantProposal(proposal); setAssistantMessage('Revisa la previsualización antes de confirmar.'); }
      else await applyAssistantProposal(proposal, false);
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'Operación inválida.'); }
    finally { setAssistantBusy(false); }
    setCommand('');
  };

  const connect = useCallback((connection: Connection) => {
    if (connection.source && connection.target && connection.source !== connection.target
      && diagram.classes.some(item => item.id === connection.source)
      && diagram.classes.some(item => item.id === connection.target)) {
      addAssociation({
        sourceId: connection.source, targetId: connection.target,
        sourceCardinality: '1', targetCardinality: '0..*',
        sourceRole: '', targetRole: '', owningSide: 'SOURCE',
      });
    }
  }, [addAssociation, diagram.classes]);

  const handleNodeDragStop = useCallback((_: unknown, node: Node) => {
    const original = diagram.classes.find(item => item.id === node.id);
    const originalEnumeration = diagram.enumerations.find(item => item.id === node.id);
    const previousPosition = original?.position ?? originalEnumeration?.position;
    if (!previousPosition) return;
    if (selectedIds.length > 1) moveSelected({ x: node.position.x - previousPosition.x, y: node.position.y - previousPosition.y });
    else if (original) moveClass(node.id, node.position.x, node.position.y);
    else if (originalEnumeration) updateEnumeration({ ...originalEnumeration, position: node.position });
  }, [diagram.classes, diagram.enumerations, moveClass, moveSelected, selectedIds.length, updateEnumeration]);

  const handlePaneClick = useCallback(() => selectElements([]), [selectElements]);

  return (
    <main className="app-shell">
      <header className="topbar">
        <button className="brand brand-button" onClick={onBack} aria-label="Volver a proyectos"><span>CM</span><div><strong>Collab Modeler</strong><small>{diagram.name}</small></div></button>
        <div className={`sync ${syncState}`} title={lastError}><Cloud size={15} /> {syncState === 'online' ? 'Guardado' : syncState === 'syncing' ? 'Sincronizando' : 'Modo local'} · revisión {diagram.revision}</div>
        <div className="actions">
          <div className="avatars" title={participants.map(value => value.displayName).join(', ')}>{participants.slice(0, 4).map(value => <span key={value.sessionId}>{value.displayName.split(/\s+/).map(part => part[0]).join('').slice(0, 2).toUpperCase()}</span>)}<b><Users size={14} /> {participants.length} en línea</b></div>
          {role === 'OWNER' && <button className="secondary" onClick={share} disabled={!diagramId}><Share2 size={16} /> Crear/rotar enlace</button>}
          {role === 'OWNER' && <button className="secondary" onClick={revokeShare} disabled={!diagramId}><XCircle size={16} /> Revocar</button>}
          <button className="secondary" onClick={() => void exportXmi()}
            disabled={!diagramId || pendingOperations.some(value => value.status !== 'acknowledged')}
            title={pendingOperations.some(value => value.status !== 'acknowledged') ? 'Espera a que terminen de sincronizarse los cambios' : 'Exportar XMI 2.1'}>
            <Download size={16} /> Exportar XMI
          </button>
          <button className="primary" onClick={generate} disabled={!diagramId}>Generar backend</button>
          <button className="icon-button" onClick={onLogout} title="Cerrar sesión"><LogOut size={18} /></button>
        </div>
      </header>

      {canEdit && <aside className="toolbar">
        <button title="Nueva clase" onClick={() => { try { addClass(); } catch (cause) { setAssistantMessage(String(cause)); } }}><Plus /></button>
        <button title="Nueva enumeración" onClick={() => { try { addEnumeration(); } catch (cause) { setAssistantMessage(String(cause)); } }}><Braces /></button>
        <button title="Importar fotografía" onClick={() => imageInput.current?.click()} disabled={analyzingImage}><Camera /></button>
        <input ref={imageInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={event => void inspectImage(event.target.files?.[0])} />
        <button title="Importar XMI 2.1" onClick={() => xmiInput.current?.click()} disabled={analyzingXmi}><FileUp /></button>
        <input ref={xmiInput} type="file" accept=".xmi,.xml,application/xml,text/xml" hidden onChange={event => void inspectXmi(event.target.files?.[0])} />
        <div className="separator" />
        <button title="Deshacer (Ctrl+Z)" onClick={undo}><Undo2 /></button>
        <button title="Rehacer (Ctrl+Mayús+Z)" onClick={redo}><Redo2 /></button>
      </aside>}

      <section className="canvas" onMouseMove={event => {
        const now = Date.now(); if (now - lastCursorSent.current < 80) return; lastCursorSent.current = now;
        const rect = event.currentTarget.getBoundingClientRect();
        sendPresence('CURSOR', { cursor: { x: (event.clientX - rect.left) / rect.width, y: (event.clientY - rect.top) / rect.height }, selection: selectedIds });
      }}>
        <ReactFlow
          nodes={nodes} edges={edges} nodeTypes={nodeTypes} fitView selectionOnDrag
          nodesDraggable={canEdit} nodesConnectable={canEdit}
          multiSelectionKeyCode={['Control', 'Meta']}
          onConnect={canEdit ? connect : undefined}
          onSelectionChange={handleSelectionChange}
          onNodeDragStop={canEdit ? handleNodeDragStop : undefined}
          onPaneClick={handlePaneClick}
        >
          <Background gap={20} color="#d7dce5" />
          <MiniMap nodeColor={node => node.type === 'enumerationNode' ? '#0891b2' : '#4f46e5'} pannable zoomable />
          <Controls />
        </ReactFlow>
        <div className="remote-cursors" aria-hidden>{participants.filter(value => value.cursor && value.displayName !== userName).map(value => <div className="remote-cursor" key={value.sessionId} style={{ left: `${value.cursor!.x * 100}%`, top: `${value.cursor!.y * 100}%` }}><span /> <b>{value.displayName}</b></div>)}</div>
      </section>

      <aside className="right-panel">
        <div className="panel-tabs">
          <button className={panel === 'properties' ? 'active' : ''} onClick={() => setPanel('properties')}><ListTree size={16} /> Propiedades</button>
          {canEdit && <button className={panel === 'assistant' ? 'active' : ''} onClick={() => setPanel('assistant')}><Bot size={16} /> Asistente</button>}
          <button className={panel === 'comments' ? 'active' : ''} onClick={() => setPanel('comments')}><MessageSquare size={16} /> Comentarios</button>
          <button className={panel === 'history' ? 'active' : ''} onClick={() => setPanel('history')}><History size={16} /> Hitos</button>
          <button className={panel === 'members' ? 'active' : ''} onClick={() => setPanel('members')}><Users size={16} /> Miembros</button>
        </div>
        {panel === 'properties' ? (canEdit ? <PropertyPanel report={setAssistantMessage} /> : <div className="assistant-body"><p>Modo de solo lectura. Puedes seleccionar elementos para inspeccionar el diagrama.</p></div>) : panel === 'comments' && diagramId ? <CommentsPanel diagramId={diagramId} diagram={diagram} role={role} eventSequence={eventSequence} /> : panel === 'history' && diagramId ? <VersionsPanel diagramId={diagramId} diagram={diagram} pendingCount={pendingOperations.filter(value => value.status !== 'acknowledged').length} eventSequence={eventSequence} accept={acceptAuthoritative} canEdit={canEdit} /> : panel === 'members' && diagramId ? <MembersPanel diagramId={diagramId} owner={role === 'OWNER'} /> : <>
          <div className="panel-title"><Bot size={19} /><div><strong>Asistente de diseño</strong><small>Operaciones verificadas</small></div></div>
          <div className="assistant-body">
            <div className="assistant-bubble">{assistantMessage}</div>
            <div className="suggestions">
              <button onClick={() => setCommand('crea una clase Producto')}>Crear clase Producto</button>
              <button onClick={() => setCommand('agrega atributo precio tipo decimal a Producto')}>Agregar precio a Producto</button>
            </div>
          </div>
          <form className="command-box" onSubmit={executeCommand}>
            <input value={command} disabled={assistantBusy} onChange={event => setCommand(event.target.value)} placeholder="Escribe una instrucción…" />
            <button type="button" disabled={assistantBusy} title="Dictar" onClick={() => dictate(setCommand, setAssistantMessage)}><Mic size={18} /></button>
            <button type="submit" disabled={assistantBusy}>{assistantBusy ? 'Validando…' : 'Enviar'}</button>
          </form>
        </>}
        {lastError && <div className="validation-message" role="alert">{lastError}</div>}
      </aside>

      {conflicts[0] && <div className="conflict-banner" role="alertdialog" aria-label="Conflicto de edición"><strong>Tu cambio entró en conflicto</strong><p>{conflicts[0].message}</p><div className="conflict-compare"><span><b>Local</b> revisión {conflicts[0].localDiagram.revision}</span><span><b>Servidor</b> revisión {conflicts[0].serverDiagram.revision}</span></div><footer><button onClick={() => retryConflict(conflicts[0].operationId)}>Reintentar</button><button onClick={() => discardConflict(conflicts[0].operationId)}>Descartar</button><button className="primary" onClick={() => reapplyConflict(conflicts[0].operationId)}>Reaplicar sobre servidor</button></footer></div>}

      {assistantProposal && <div className="modal-backdrop">
        <section className="proposal-modal" role="alertdialog" aria-modal="true" aria-labelledby="assistant-preview-title">
          <h2 id="assistant-preview-title">Confirmar cambio del asistente</h2>
          <p>Esta operación elimina elementos o modifica el diagrama de forma masiva. Todavía no se aplicó ningún cambio.</p>
          <div className="snapshot-summary">
            <span>{assistantProposal.summary}</span><span>Proveedor: {assistantProposal.provider}</span>
            <span>{assistantProposal.previewDiagram.classes.length} clases</span><span>{assistantProposal.previewDiagram.enumerations.length} enumeraciones</span>
            <span>{assistantProposal.previewDiagram.associations.length} relaciones</span><span>{assistantProposal.previewDiagram.generalizations.length} herencias</span>
          </div>
          <footer><button className="secondary" disabled={assistantBusy} onClick={() => setAssistantProposal(undefined)}>Cancelar</button><button className="primary" disabled={assistantBusy} onClick={() => void applyAssistantProposal(assistantProposal, true)}>Confirmar y aplicar</button></footer>
        </section>
      </div>}

      {imageProposal && <div className="modal-backdrop">
        <section className="proposal-modal">
          <h2>Propuesta detectada</h2>
          <p>La fotografía todavía no modificó el diagrama.</p>
          <div className="proposal-grid">
            {imageProposal.classes.map(item => <article key={item.name}><strong>{item.name}</strong><span>{item.attributes.map(attribute => `${attribute.name}: ${attribute.type}`).join(', ') || 'Sin atributos'}</span></article>)}
          </div>
          {imageProposal.warnings?.map(warning => <p className="warning" key={warning}>{warning}</p>)}
          <footer><button className="secondary" onClick={() => setImageProposal(undefined)}>Cancelar</button><button className="primary" onClick={acceptImageProposal}>Incorporar y corregir</button></footer>
        </section>
      </div>}

      {xmiPreview && <div className="modal-backdrop">
        <section className="proposal-modal" role="dialog" aria-modal="true" aria-labelledby="xmi-preview-title">
          <h2 id="xmi-preview-title">Vista previa XMI 2.1</h2>
          <p>Al confirmar se reemplazará el contenido del diagrama en una sola revisión versionada.</p>
          <div className="xmi-summary">
            <span><strong>{xmiPreview.diagram.packages.length}</strong> paquetes</span>
            <span><strong>{xmiPreview.diagram.classes.length}</strong> clases</span>
            <span><strong>{xmiPreview.diagram.enumerations.length}</strong> enumeraciones</span>
            <span><strong>{xmiPreview.diagram.associations.length}</strong> asociaciones</span>
            <span><strong>{xmiPreview.diagram.generalizations.length}</strong> generalizaciones</span>
          </div>
          <div className="proposal-grid">
            {xmiPreview.diagram.classes.map(item => <article key={item.id}><strong>{item.name}</strong><span>{item.attributes.map(attribute => `${attribute.name}: ${attribute.type}`).join(', ') || 'Sin atributos'}</span></article>)}
          </div>
          {xmiPreview.warnings.length > 0 && <section className="xmi-warnings"><h3>Advertencias ({xmiPreview.warnings.length})</h3>{xmiPreview.warnings.map((warning, index) => <p className="warning" key={`${warning.code}-${warning.externalId ?? index}`}><strong>{warning.code}</strong> · {warning.message}</p>)}</section>}
          <footer><button className="secondary" onClick={() => setXmiPreview(undefined)}>Cancelar</button><button className="primary" onClick={acceptXmi}>Confirmar importación</button></footer>
        </section>
      </div>}
    </main>
  );
}
