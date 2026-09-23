import { FormEvent, memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Background, Connection, ConnectionMode, Controls, Edge, MiniMap, Node, ReactFlow, useNodesState } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { AlertCircle, Bot, Camera, CheckCircle2, CheckSquare, Cloud, Download, FileUp, History, ListTree, Loader2, LogOut, MessageSquare, Mic, Monitor, Play, Plus, Redo2, Share2, Smartphone, Sparkles, Trash2, Undo2, Users, Wifi, X, XCircle } from 'lucide-react';
import { ClassNode } from '../features/editor/ClassNode';
import { UmlEdge } from '../features/editor/UmlEdge';
import { PropertyPanel } from '../features/editor/PropertyPanel';
import { useDiagramStore } from '../features/editor/store';
import { AgentDevice, AgentSseEvent, AgentStatus, ApiError, AssistantProposal, GenerationJob, ImageProposal, XmiImportPreview, agentApi, analyzeDiagramImage, assistantApi, diagramApi, downloadXmi, generationJobsApi, previewXmi } from '../shared/api/api';
import { SpeechSession, SpeechStatus } from '../features/editor/speech';
import { buildImageImport, optimizeImageForAnalysis } from '../features/editor/imageImport';
import { RelationType, cardinalities, scalarTypes } from '../features/editor/domain';
import { CommentsPanel, MembersPanel, VersionsPanel } from '../features/editor/CollaborationPanel';

const nodeTypes = { classNode: ClassNode };
const edgeTypes = { uml: UmlEdge };

function sameModelNodes(current: Node[], model: Node[]) {
  if (current.length !== model.length) return false;
  return current.every((node, index) => {
    const expected = model[index];
    const currentData = node.data as { version?: number; foreignKeyNames?: string[] };
    const expectedData = expected.data as { version?: number; foreignKeyNames?: string[] };
    return node.id === expected.id && node.position.x === expected.position.x && node.position.y === expected.position.y
      && currentData.version === expectedData.version
      && (currentData.foreignKeyNames ?? []).join('|') === (expectedData.foreignKeyNames ?? []).join('|');
  });
}

const ParticipantsAvatars = memo(function ParticipantsAvatars() {
  const participants = useDiagramStore(s => s.participants);
  return (
    <div className="avatars" title={participants.map(value => value.displayName).join(', ')}>
      {participants.slice(0, 4).map(value => (
        <span key={value.sessionId}>
          {value.displayName.split(/\s+/).map(part => part[0]).join('').slice(0, 2).toUpperCase()}
        </span>
      ))}
      <b><Users size={14} /> {participants.length} en línea</b>
    </div>
  );
});

const RemoteCursorsOverlay = memo(function RemoteCursorsOverlay({ userName }: { userName: string }) {
  const participants = useDiagramStore(s => s.participants);
  const remote = useMemo(
    () => participants.filter(value => value.cursor && value.displayName !== userName),
    [participants, userName]
  );
  return (
    <div className="remote-cursors" aria-hidden>
      {remote.map(value => (
        <div
          className="remote-cursor"
          key={value.sessionId}
          style={{ left: `${value.cursor!.x * 100}%`, top: `${value.cursor!.y * 100}%` }}
        >
          <span /> <b>{value.displayName}</b>
        </div>
      ))}
    </div>
  );
});

export default function App({ projectId, userName, role, onBack, onLogout }: { projectId: string; userName: string; role: string; onBack: () => void; onLogout: () => void }) {
  const canEdit = role === 'OWNER' || role === 'EDITOR';

  const diagram = useDiagramStore(s => s.diagram);
  const diagramId = useDiagramStore(s => s.diagramId);
  const syncState = useDiagramStore(s => s.syncState);
  const lastError = useDiagramStore(s => s.lastError);
  const selectedIds = useDiagramStore(s => s.selectedIds);
  const conflicts = useDiagramStore(s => s.conflicts);
  const pendingOperations = useDiagramStore(s => s.pendingOperations);
  const eventSequence = useDiagramStore(s => s.eventSequence);

  const initialize = useDiagramStore(s => s.initialize);
  const selectElements = useDiagramStore(s => s.selectElements);
  const addClass = useDiagramStore(s => s.addClass);
  const moveClass = useDiagramStore(s => s.moveClass);
  const moveSelected = useDiagramStore(s => s.moveSelected);
  const addAssociation = useDiagramStore(s => s.addAssociation);
  const addGeneralization = useDiagramStore(s => s.addGeneralization);
  const deleteSelected = useDiagramStore(s => s.deleteSelected);
  const undo = useDiagramStore(s => s.undo);
  const redo = useDiagramStore(s => s.redo);
  const retryConflict = useDiagramStore(s => s.retryConflict);
  const discardConflict = useDiagramStore(s => s.discardConflict);
  const reapplyConflict = useDiagramStore(s => s.reapplyConflict);
  const sendPresence = useDiagramStore(s => s.sendPresence);
  const acceptAuthoritative = useDiagramStore(s => s.acceptAuthoritative);
  const replaceFromImport = useDiagramStore(s => s.replaceFromImport);
  const acceptAssistant = useDiagramStore(s => s.acceptAssistant);

  const [command, setCommand] = useState('');
  const [speechStatus, setSpeechStatus] = useState<SpeechStatus>({ phase: 'idle' });
  const speechSession = useRef<SpeechSession | undefined>(undefined);
  const [assistantMessage, setAssistantMessage] = useState('Prueba: “crea una clase Producto”');
  const [assistantProposal, setAssistantProposal] = useState<AssistantProposal>();
  const [assistantBusy, setAssistantBusy] = useState(false);
  const [panel, setPanel] = useState<'properties' | 'assistant' | 'comments' | 'history' | 'members'>('properties');
  const [connectionType, setConnectionType] = useState<RelationType | 'GENERALIZATION'>('ASSOCIATION');
  const [imageProposal, setImageProposal] = useState<ImageProposal>();
  const [analyzingImage, setAnalyzingImage] = useState(false);
  const [xmiPreview, setXmiPreview] = useState<XmiImportPreview>();
  const [analyzingXmi, setAnalyzingXmi] = useState(false);
  const [generatingBackend, setGeneratingBackend] = useState(false);
  const [generatingFlutter, setGeneratingFlutter] = useState(false);
  const [generationJob, setGenerationJob] = useState<GenerationJob | null>(null);
  const [agentModalOpen, setAgentModalOpen] = useState(false);
  const [agentStatus, setAgentStatus] = useState<AgentStatus | null>(null);
  const [agentPhase, setAgentPhase] = useState<'checking' | 'fetching' | 'generating' | 'ready' | 'running' | 'done' | 'error'>('checking');
  const [agentProgress, setAgentProgress] = useState<string[]>([]);
  const [agentDevices, setAgentDevices] = useState<AgentDevice[]>([]);
  const [agentOutputDir, setAgentOutputDir] = useState<string>('');
  const [agentError, setAgentError] = useState<string>('');
  const [agentSelectedDevice, setAgentSelectedDevice] = useState<string>('');
  const [agentApiUrl, setAgentApiUrl] = useState<string>('http://localhost:8080');
  const agentStreamRef = useRef<{ close: () => void } | null>(null);
  const [generationFeedback, setGenerationFeedback] = useState<{
    type: 'success' | 'error' | 'info';
    title: string;
    message: string;
    downloadUrl?: string;
    downloadName?: string;
    elementId?: string;
    canAutoFixPk?: boolean;
  } | null>(null);
  const imageInput = useRef<HTMLInputElement>(null);
  const xmiInput = useRef<HTMLInputElement>(null);
  const lastCursorSent = useRef(0);

  useEffect(() => {
    let dispose: () => void = () => undefined;
    void initialize(projectId).then(callback => { dispose = callback; });
    return () => dispose();
  }, [initialize, projectId]);

  useEffect(() => () => speechSession.current?.stop(), []);

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
    if (!diagramId || generatingBackend) return;
    setGeneratingBackend(true);
    setGenerationFeedback(null);
    try {
      // Reuse the key for this immutable revision so a retry after a lost HTTP
      // response returns the original job instead of doing expensive work twice.
      const storageKey = `collab-modeler:generation:${diagramId}:${diagram.revision}`;
      const idempotencyKey = localStorage.getItem(storageKey) || crypto.randomUUID();
      localStorage.setItem(storageKey, idempotencyKey);
      const job = await generationJobsApi.create(diagramId, idempotencyKey, { groupId: 'com.generated', artifactId: 'generated-api' });
      setGenerationJob(job);
      setGenerationFeedback({
        type: 'info',
        title: 'Generación encolada',
        message: `Trabajo ${job.id.slice(0, 8)} vinculado a la revisión ${job.sourceRevision}. Puedes cerrar el navegador: continuará en segundo plano.`,
      });
      setAssistantMessage('Generación encolada.');
    } catch (cause) {
      const err = cause instanceof ApiError ? cause : new ApiError(0, 'UNKNOWN', cause instanceof Error ? cause.message : 'No se pudo generar el backend');
      const elementId = (err.details?.elementId as string | undefined);
      const isPkError = err.code === 'INVALID_PRIMARY_KEY' || err.message.toLowerCase().includes('clave primaria');

      if (elementId) {
        selectElements([elementId]);
        setPanel('properties');
      }

      setGenerationFeedback({
        type: 'error',
        title: 'Error al generar backend',
        message: err.message,
        elementId,
        canAutoFixPk: isPkError,
      });
      setAssistantMessage(`Error: ${err.message}`);
    } finally {
      setGeneratingBackend(false);
    }
  };

  useEffect(() => {
    if (!diagramId || !generationJob || ['SUCCEEDED', 'FAILED'].includes(generationJob.status)) return;
    const timer = window.setInterval(() => {
      void generationJobsApi.get(diagramId, generationJob.id).then(job => {
        setGenerationJob(job);
        if (job.status === 'SUCCEEDED') {
          setGenerationFeedback({ type: 'success', title: 'Artefactos listos', message: `Backend y especificación móvil de la revisión ${job.sourceRevision}. Los enlaces expiran pronto.`, downloadUrl: job.backend?.url, downloadName: 'Backend ZIP' });
        } else if (job.status === 'FAILED') {
          setGenerationFeedback({ type: 'error', title: 'La generación falló', message: job.errorMessage || 'Puedes reintentar el trabajo sin crear otro.' });
        }
      }).catch(() => undefined);
    }, 1500);
    return () => window.clearInterval(timer);
  }, [diagramId, generationJob]);

  const autoFixMissingPks = useCallback(() => {
    const childIds = new Set(diagram.generalizations.map(g => g.childId));
    let fixedCount = 0;
    for (const cls of diagram.classes) {
      if (childIds.has(cls.id)) continue;
      const hasPk = cls.attributes.some(a => a.primaryKey);
      if (!hasPk) {
        const candidate = cls.attributes.find(a => /^(id|codigo|code|ci|nro|nroe|identificador)$/i.test(a.name));
        if (candidate) {
          useDiagramStore.getState().updateAttribute(cls.id, {
            ...candidate,
            primaryKey: true,
            required: true,
          });
          fixedCount++;
        } else {
          useDiagramStore.getState().addAttribute(cls.id, {
            name: 'id',
            type: 'Integer',
            primaryKey: true,
            required: true,
            unique: true,
          });
          fixedCount++;
        }
      }
    }
    setGenerationFeedback({
      type: 'info',
      title: 'Claves primarias asignadas',
      message: `Se actualizaron ${fixedCount} clase(s) con su clave primaria. Reintentando generación…`,
    });
    setTimeout(() => {
      void generate();
    }, 400);
  }, [diagram, diagramId]);

  const generateFlutter = async () => {
    if (!diagramId || generatingFlutter) return;
    setGeneratingFlutter(true);
    try {
      if (!generationJob || generationJob.status !== 'SUCCEEDED') { await generate(); setGenerationFeedback({ type: 'info', title: 'Preparando la especificación móvil', message: 'Cuando el trabajo termine, abre el agente local para generar Flutter en esta computadora.' }); }
      else await openAgentModal();
    } catch (cause) {
      const err = cause instanceof ApiError ? cause : new ApiError(0, 'UNKNOWN', cause instanceof Error ? cause.message : 'No se pudo generar la app Flutter');
      const elementId = (err.details?.elementId as string | undefined);
      const isPkError = err.code === 'INVALID_PRIMARY_KEY' || err.message.toLowerCase().includes('clave primaria');

      if (elementId) {
        selectElements([elementId]);
        setPanel('properties');
      }

      setGenerationFeedback({
        type: 'error',
        title: 'Error al generar app Flutter',
        message: err.message,
        elementId,
        canAutoFixPk: isPkError,
      });
      setAssistantMessage(`Error: ${err.message}`);
    } finally {
      setGeneratingFlutter(false);
    }
  };

  // =========================================================================
  // Agent modal — live Flutter generation and device execution
  // =========================================================================
  const openAgentModal = async () => {
    if (!diagramId) return;
    setAgentModalOpen(true);
    setAgentPhase('checking');
    setAgentProgress([]);
    setAgentError('');
    setAgentOutputDir('');
    setAgentDevices([]);
    setAgentSelectedDevice('');

    try {
      const status = await agentApi.checkStatus();
      setAgentStatus(status);
      setAgentDevices(status.devices);
      if (status.devices.length > 0) setAgentSelectedDevice(status.devices[0].id);

      if (!status.flutterAvailable) {
        setAgentError('Flutter SDK no encontrado. Instálalo y agrega flutter a PATH.');
        setAgentPhase('error');
        return;
      }

      setAgentPhase('fetching');
      setAgentProgress(prev => [...prev, '✓ Agente local activo']);
      setAgentProgress(prev => [...prev, `Flutter ${status.flutterVersion || 'disponible'}`]);
      if (status.adbAvailable) setAgentProgress(prev => [...prev, `ADB ${status.adbVersion || 'disponible'}`]);

      // Fetch agent-spec bundle from backend
      const latest = generationJob?.status === 'SUCCEEDED' ? generationJob : (await generationJobsApi.list(diagramId)).find(job => job.status === 'SUCCEEDED');
      if (!latest) throw new ApiError(409, 'GENERATION_NOT_READY', 'Primero espera a que termine un trabajo de generación.');
      const bundle = await generationJobsApi.agentSpec(diagramId, latest.id);
      setAgentProgress(prev => [...prev, '✓ Especificación firmada obtenida del backend']);

      // Send to agent for generation
      setAgentPhase('generating');
      const stream = agentApi.generate(bundle);
      agentStreamRef.current = stream;

      stream.onEvent((event: AgentSseEvent) => {
        if (event.type === 'progress') {
          const msg = (event.data.message as string) || JSON.stringify(event.data);
          setAgentProgress(prev => [...prev, msg]);
          if (event.data.outputDir) setAgentOutputDir(event.data.outputDir as string);
        } else if (event.type === 'devices') {
          const devs = (event.data.devices as AgentDevice[]) || [];
          setAgentDevices(devs);
          if (devs.length > 0 && !agentSelectedDevice) setAgentSelectedDevice(devs[0].id);
        } else if (event.type === 'output') {
          const text = (event.data.data as string) || '';
          if (text.trim()) setAgentProgress(prev => [...prev, text.trim().slice(0, 200)]);
        } else if (event.type === 'done') {
          setAgentPhase('ready');
          setAgentOutputDir((event.data.outputDir as string) || agentOutputDir);
          setAgentProgress(prev => [...prev, '✓ Proyecto Flutter generado exitosamente']);
        } else if (event.type === 'error') {
          setAgentPhase('error');
          setAgentError((event.data.message as string) || 'Error desconocido');
        }
      });
    } catch (cause) {
      setAgentPhase('error');
      setAgentError(cause instanceof Error ? cause.message : 'No se pudo conectar con el agente local');
    }
  };

  const agentRunOnDevice = (action: 'flutter-run' | 'flutter-build-apk') => {
    if (!agentOutputDir) return;
    setAgentPhase('running');
    setAgentProgress(prev => [...prev, `--- Ejecutando ${action} ---`]);

    const stream = agentApi.run(agentOutputDir, action, agentSelectedDevice || undefined, agentApiUrl);
    agentStreamRef.current = stream;

    stream.onEvent((event: AgentSseEvent) => {
      if (event.type === 'progress' || event.type === 'output') {
        const msg = (event.data.message as string) || (event.data.data as string) || '';
        if (msg.trim()) setAgentProgress(prev => [...prev, msg.trim().slice(0, 300)]);
      } else if (event.type === 'done') {
        setAgentPhase('done');
        const code = event.data.exitCode as number;
        setAgentProgress(prev => [...prev, code === 0 ? '✓ Ejecución completada exitosamente' : `✗ Proceso terminó con código ${code}`]);
      } else if (event.type === 'error') {
        setAgentPhase('error');
        setAgentError((event.data.message as string) || 'Error de ejecución');
      }
    });
  };

  const closeAgentModal = () => {
    agentStreamRef.current?.close();
    agentStreamRef.current = null;
    setAgentModalOpen(false);
  };

  const inspectImage = async (file?: File) => {
    if (!file || !diagramId) return;
    if (file.size > 10_000_000) { setAssistantMessage('La fotografía supera el límite de 10 MB.'); return; }
    setAnalyzingImage(true);
    setAssistantMessage('Optimizando y analizando fotografía con IA…');
    try {
      const prepared = await optimizeImageForAnalysis(file);
      setImageProposal(await analyzeDiagramImage(diagramId, prepared));
      setAssistantMessage('Corrige la propuesta antes de incorporarla.');
    }
    catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo analizar la fotografía.'); }
    finally { setAnalyzingImage(false); if (imageInput.current) imageInput.current.value = ''; }
  };

  const acceptImageProposal = () => {
    if (!imageProposal) return;
    try {
      if (pendingOperations.some(value => value.status !== 'acknowledged')) throw new Error('Espera a que terminen de sincronizarse los cambios pendientes.');
      replaceFromImport(buildImageImport(diagram, imageProposal));
      setImageProposal(undefined);
      setAssistantMessage('Fotografía incorporada en un único lote. Puedes deshacerla con Ctrl+Z.');
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

  const modelNodes = useMemo<Node[]>(() => [
    ...diagram.classes.map(item => ({
      id: item.id, type: 'classNode', position: item.position,
      data: { ...item, foreignKeyNames: diagram.associations.filter(link => link.targetId === item.id)
        .map(link => `${diagram.classes.find(owner => owner.id === link.sourceId)?.name ?? ''}_id`) },
    })),
  ], [diagram.classes, diagram.associations]);
  const [nodes, setNodes, onNodesChange] = useNodesState(modelNodes);
  const draggingNode = useRef(false);
  useEffect(() => {
    if (!draggingNode.current) setNodes(current => sameModelNodes(current, modelNodes) ? current : modelNodes);
  }, [modelNodes, setNodes]);

  const edges = useMemo<Edge[]>(() => [
    ...diagram.associations.map(item => ({
      id: item.id, source: item.sourceId, target: item.targetId,
      sourceHandle: item.sourceHandle ?? 'right-50', targetHandle: item.targetHandle ?? 'left-50', type: 'uml',
      data: { kind: 'association', name: item.name, sourceCardinality: item.sourceCardinality,
        targetCardinality: item.targetCardinality, relationType: item.relationType },
    })),
    ...diagram.generalizations.map(item => ({
      id: item.id, source: item.childId, target: item.parentId, type: 'uml',
      sourceHandle: 'top-50', targetHandle: 'bottom-50', data: { kind: 'generalization', relationType: 'GENERALIZATION' },
    })),
  ], [diagram.associations, diagram.generalizations]);

  const handleSelectionChange = useCallback(({ nodes: selectedNodes, edges: selectedEdges }: { nodes: Node[]; edges: Edge[] }) => {
    selectElements([...selectedNodes, ...selectedEdges].map(item => item.id));
  }, [selectElements]);

  const applyAssistantProposal = async (proposal: AssistantProposal, confirmed: boolean) => {
    if (!diagramId) return;
    const before = diagram;
    setAssistantBusy(true);
    try {
      const result = await assistantApi.apply(diagramId, proposal.proposalId, confirmed);
      acceptAssistant(result.operation, result.diagram, before); setAssistantProposal(undefined);
      setAssistantMessage(`Cambio aplicado mediante ${result.provider}. Puedes deshacerlo.`);
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo aplicar la propuesta.'); }
    finally { setAssistantBusy(false); }
  };

  const submitInstruction = async (instruction: string) => {
    if (!diagramId || !instruction.trim()) return;
    if (pendingOperations.some(value => value.status !== 'acknowledged')) {
      setAssistantMessage('Espera a que terminen de sincronizarse los cambios pendientes.'); return;
    }
    setAssistantBusy(true);
    try {
      const proposal = await assistantApi.interpret(diagramId, instruction.trim());
      if (proposal.requiresConfirmation) { setAssistantProposal(proposal); setAssistantMessage('Revisa la previsualización antes de confirmar.'); }
      else await applyAssistantProposal(proposal, false);
    } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'Operación inválida.'); }
    finally { setAssistantBusy(false); }
    setCommand('');
  };

  const executeCommand = (event: FormEvent) => { event.preventDefault(); void submitInstruction(command); };

  const startSpeech = () => {
    if (speechStatus.phase === 'recording') { speechSession.current?.finish(); return; }
    speechSession.current?.stop();
    const hasBrowserSpeech = typeof window !== 'undefined' && ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window);
    speechSession.current = new SpeechSession(setSpeechStatus, value => {
      setCommand(value);
      void submitInstruction(value).finally(() => setSpeechStatus({ phase: 'idle' }));
    }, audio => diagramId ? assistantApi.transcribe(diagramId, audio) : Promise.reject(new Error('El diagrama no está disponible.')), hasBrowserSpeech);
    void speechSession.current.start();
  };

  const connect = useCallback((connection: Connection) => {
    if (connection.source && connection.target && connection.source !== connection.target
      && diagram.classes.some(item => item.id === connection.source)
      && diagram.classes.some(item => item.id === connection.target)) {
      if (connectionType === 'GENERALIZATION') {
        addGeneralization(connection.target, connection.source);
        return;
      }
      addAssociation({
        sourceId: connection.source, targetId: connection.target,
        sourceCardinality: '1', targetCardinality: '0..*',
        sourceRole: '', targetRole: '', owningSide: 'SOURCE', relationType: connectionType,
        sourceHandle: (connection.sourceHandle ?? 'right-50') as import('../features/editor/domain').ConnectionPoint,
        targetHandle: (connection.targetHandle ?? 'left-50') as import('../features/editor/domain').ConnectionPoint,
      });
    }
  }, [addAssociation, addGeneralization, connectionType, diagram.classes]);

  const handleNodeDragStop = useCallback((_: unknown, node: Node) => {
    draggingNode.current = false;
    const currentDiagram = useDiagramStore.getState().diagram;
    const currentSelectedIds = useDiagramStore.getState().selectedIds;
    const original = currentDiagram.classes.find(item => item.id === node.id);
    const previousPosition = original?.position;
    if (!previousPosition) return;
    if (previousPosition.x === node.position.x && previousPosition.y === node.position.y) return;

    // Edges can be selected along with a node. Only other selected nodes should
    // turn this into a group move, otherwise dragging one table would move every
    // selected table just because an edge is selected too.
    const selectedNodeCount = currentDiagram.classes
      .filter(item => currentSelectedIds.includes(item.id)).length;
    if (selectedNodeCount > 1) moveSelected({ x: node.position.x - previousPosition.x, y: node.position.y - previousPosition.y });
    else if (original) moveClass(node.id, node.position.x, node.position.y);
  }, [moveClass, moveSelected]);

  const handlePaneClick = useCallback(() => selectElements([]), [selectElements]);
  const handleEdgeClick = useCallback((_: unknown, edge: Edge) => {
    selectElements([edge.id]);
    setPanel('properties');
  }, [selectElements]);

  return (
    <main className="app-shell">
      <header className="topbar">
        <button className="brand brand-button" onClick={onBack} aria-label="Volver a proyectos"><span>CM</span><div><strong>Collab Modeler</strong><small>{diagram.name}</small></div></button>
        <div className={`sync ${syncState}`} title={lastError}><Cloud size={15} /> {syncState === 'online' ? 'Guardado' : syncState === 'syncing' ? 'Sincronizando' : 'Modo local'} · revisión {diagram.revision}</div>
        <div className="actions">
          <ParticipantsAvatars />
          {role === 'OWNER' && <button className="secondary" onClick={share} disabled={!diagramId}><Share2 size={16} /> Crear/rotar enlace</button>}
          {role === 'OWNER' && <button className="secondary" onClick={revokeShare} disabled={!diagramId}><XCircle size={16} /> Revocar</button>}
          <button className="secondary" onClick={() => void exportXmi()}
            disabled={!diagramId || pendingOperations.some(value => value.status !== 'acknowledged')}
            title={pendingOperations.some(value => value.status !== 'acknowledged') ? 'Espera a que terminen de sincronizarse los cambios' : 'Exportar XMI 2.1'}>
            <Download size={16} /> Exportar XMI
          </button>
          <button className="primary" onClick={generate} disabled={!diagramId || generatingBackend}>
            {generatingBackend ? <><Loader2 size={16} className="spinning" /> Generando backend…</> : 'Generar backend'}
          </button>
          <button className="secondary" onClick={generateFlutter} disabled={!diagramId || generatingFlutter} title="Generar Flutter localmente desde la especificación firmada">
            {generatingFlutter ? <><Loader2 size={16} className="spinning" /> Preparando…</> : <><Smartphone size={16} /> App Flutter local</>}
          </button>
          <button className="primary agent-button" onClick={openAgentModal} disabled={!diagramId} title="Generar Flutter en vivo y ejecutar en dispositivo móvil">
            <Monitor size={16} /> Generar App Móvil
          </button>
          <button className="icon-button" onClick={onLogout} title="Cerrar sesión"><LogOut size={18} /></button>
        </div>
      </header>

      {generationFeedback && (
        <div className={`app-notification ${generationFeedback.type}`} role="alert">
          <div className="notification-icon">
            {generationFeedback.type === 'error' && <AlertCircle size={20} />}
            {generationFeedback.type === 'success' && <CheckCircle2 size={20} />}
            {generationFeedback.type === 'info' && <Loader2 size={20} className="spinning" />}
          </div>
          <div className="notification-body">
            <strong>{generationFeedback.title}</strong>
            <p>{generationFeedback.message}</p>
            {generationFeedback.type === 'success' && generationFeedback.downloadUrl && (
              <div className="notification-actions">
                <a
                  href={generationFeedback.downloadUrl}
                  download={generationFeedback.downloadName || 'generated-api.zip'}
                  className="action-pill secondary"
                  style={{ textDecoration: 'none' }}
                >
                  <Download size={14} /> Descargar archivo de nuevo ({generationFeedback.downloadName || 'ZIP'})
                </a>
              </div>
            )}
            {generationJob?.status === 'FAILED' && diagramId && (
              <div className="notification-actions">
                <button type="button" className="action-pill primary" onClick={() => void generationJobsApi.retry(diagramId, generationJob.id).then(setGenerationJob).catch(cause => setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo reintentar.'))}>Reintentar trabajo</button>
              </div>
            )}
            {generationFeedback.canAutoFixPk && (
              <div className="notification-actions">
                <button type="button" className="action-pill primary" onClick={autoFixMissingPks}>
                  <Sparkles size={14} /> Asignar Claves Primarias (PK) automáticamente y reintentar
                </button>
              </div>
            )}
          </div>
          <button type="button" className="notification-close" onClick={() => setGenerationFeedback(null)} aria-label="Cerrar aviso">
            <X size={16} />
          </button>
        </div>
      )}

      {canEdit && <aside className="toolbar">
        <button title="Nueva clase" onClick={() => { try { addClass(); } catch (cause) { setAssistantMessage(String(cause)); } }}><Plus /></button>
        <select className="relation-tool" aria-label="Tipo de relación al conectar clases" value={connectionType}
          onChange={event => setConnectionType(event.target.value as RelationType | 'GENERALIZATION')}>
          <option value="ASSOCIATION">Asociación</option><option value="GENERALIZATION">Herencia</option>
          <option value="AGGREGATION">Agregación</option><option value="COMPOSITION">Composición</option>
          <option value="DEPENDENCY">Dependencia</option>
        </select>
        <div className="separator" />
        <button title="Seleccionar todas las clases (Ctrl+A)" aria-label="Seleccionar todas las clases" onClick={() =>
          selectElements(diagram.classes.map(item => item.id))
        } disabled={!diagram.classes.length}><CheckSquare /></button>
        <button title="Eliminar selección (Supr)" aria-label="Eliminar selección" onClick={() => {
          if (!selectedIds.length) return;
          if (window.confirm(`¿Eliminar ${selectedIds.length} elemento(s) seleccionado(s)?`)) {
            try { deleteSelected(); } catch (cause) { setAssistantMessage(cause instanceof Error ? cause.message : 'No se pudo eliminar.'); }
          }
        }} disabled={!selectedIds.length}><Trash2 /></button>
        <div className="separator" />
        <button className="toolbar-image-action" title="Analizar fotografía" aria-label="Analizar fotografía" onClick={() => imageInput.current?.click()} disabled={analyzingImage}><Camera /><span>Fotografía</span></button>
        <input ref={imageInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={event => void inspectImage(event.target.files?.[0])} />
        <button title="Importar XMI 2.1" onClick={() => xmiInput.current?.click()} disabled={analyzingXmi}><FileUp /></button>
        <input ref={xmiInput} type="file" accept=".xmi,.xml,application/xml,text/xml" hidden onChange={event => void inspectXmi(event.target.files?.[0])} />
        <div className="separator" />
        <button title="Deshacer (Ctrl+Z)" onClick={undo}><Undo2 /></button>
        <button title="Rehacer (Ctrl+Mayús+Z)" onClick={redo}><Redo2 /></button>
      </aside>}

      <section className="canvas" onMouseMove={event => {
        const now = Date.now(); if (now - lastCursorSent.current < 100) return; lastCursorSent.current = now;
        const rect = event.currentTarget.getBoundingClientRect();
        sendPresence('CURSOR', {
          cursor: { x: (event.clientX - rect.left) / rect.width, y: (event.clientY - rect.top) / rect.height },
          selection: useDiagramStore.getState().selectedIds,
        });
      }}>
        <ReactFlow
          nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} fitView selectionOnDrag
          connectionMode={ConnectionMode.Loose}
          onNodesChange={onNodesChange}
          onNodeDragStart={() => { draggingNode.current = true; }}
          nodesDraggable={canEdit} nodesConnectable={canEdit}
          multiSelectionKeyCode={['Control', 'Meta']}
          onConnect={canEdit ? connect : undefined}
          onEdgeClick={handleEdgeClick}
          onSelectionChange={handleSelectionChange}
          onNodeDragStop={canEdit ? handleNodeDragStop : undefined}
          onPaneClick={handlePaneClick}
        >
          <Background gap={20} color="#d7dce5" />
          <MiniMap nodeColor={() => '#4f46e5'} pannable zoomable />
          <Controls />
        </ReactFlow>
        <RemoteCursorsOverlay userName={userName} />
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
            <button className="assistant-image-action" type="button" onClick={() => imageInput.current?.click()} disabled={analyzingImage}><Camera size={17} /> {analyzingImage ? 'Analizando fotografía…' : 'Analizar fotografía'}</button>
          </div>
          <form className="command-box" onSubmit={executeCommand}>
            <input value={command} disabled={assistantBusy} onChange={event => setCommand(event.target.value)} placeholder="Escribe una instrucción…" />
            <button type="button" disabled={assistantBusy || ['permission', 'transcribing'].includes(speechStatus.phase)} aria-label={speechStatus.phase === 'recording' ? 'Transcribir grabación' : 'Grabar voz'} title={speechStatus.phase === 'recording' ? 'Transcribir grabación' : 'Grabar voz'} onClick={startSpeech}><Mic size={18} /> {speechStatus.phase === 'recording' ? 'Transcribir' : 'Voz'}</button>
            <button type="submit" disabled={assistantBusy}>{assistantBusy ? 'Validando…' : 'Enviar'}</button>
          </form>
          {speechStatus.phase !== 'idle' && <div className="validation-message" role="status">{speechStatus.message} {speechStatus.phase === 'error' && <button onClick={startSpeech}>Reintentar voz</button>}</div>}
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
            <span>{assistantProposal.previewDiagram.classes.length} clases</span>
            <span>{assistantProposal.previewDiagram.associations.length} relaciones</span><span>{assistantProposal.previewDiagram.generalizations.length} herencias</span>
          </div>
          <footer><button className="secondary" disabled={assistantBusy} onClick={() => setAssistantProposal(undefined)}>Cancelar</button><button className="primary" disabled={assistantBusy} onClick={() => void applyAssistantProposal(assistantProposal, true)}>Confirmar y aplicar</button></footer>
        </section>
      </div>}

      {imageProposal && <div className="modal-backdrop">
        <section className="proposal-modal" role="dialog" aria-modal="true" aria-labelledby="image-preview-title">
          <h2 id="image-preview-title">Propuesta detectada</h2>
          <p>La fotografía todavía no modificó el diagrama.</p>
          <p>Confianza del análisis: {Math.round(imageProposal.confidence * 100)} %</p>
          <div className="proposal-grid">
            {imageProposal.classes.map((item, classIndex) => <article key={classIndex}>
              <label>Clase <input aria-label={`Nombre de clase ${classIndex + 1}`} value={item.name} onChange={event => setImageProposal(current => current && ({ ...current, classes: current.classes.map((candidate, index) => index === classIndex ? { ...candidate, name: event.target.value } : candidate), associations: current.associations.map(link => ({ ...link, source: link.source === item.name ? event.target.value : link.source, target: link.target === item.name ? event.target.value : link.target })) }))} /></label>
              <button type="button" className="secondary" onClick={() => setImageProposal(current => current && ({ ...current, classes: current.classes.filter((_, index) => index !== classIndex), associations: current.associations.filter(link => link.source !== item.name && link.target !== item.name) }))}>Quitar clase</button>
              {item.attributes.map((attribute, attributeIndex) => <div key={attributeIndex} className="proposal-edit-row">
                <input aria-label={`Atributo ${attributeIndex + 1} de ${item.name}`} value={attribute.name} onChange={event => setImageProposal(current => current && ({ ...current, classes: current.classes.map((candidate, index) => index === classIndex ? { ...candidate, attributes: candidate.attributes.map((value, i) => i === attributeIndex ? { ...value, name: event.target.value } : value) } : candidate) }))} />
                <select aria-label={`Tipo de ${attribute.name}`} value={attribute.type} onChange={event => setImageProposal(current => current && ({ ...current, classes: current.classes.map((candidate, index) => index === classIndex ? { ...candidate, attributes: candidate.attributes.map((value, i) => i === attributeIndex ? { ...value, type: event.target.value } : value) } : candidate) }))}>{scalarTypes.map(type => <option key={type}>{type}</option>)}</select>
                <label><input type="checkbox" checked={attribute.primaryKey ?? false} onChange={event => setImageProposal(current => current && ({ ...current, classes: current.classes.map((candidate, index) => index === classIndex ? { ...candidate, attributes: candidate.attributes.map((value, i) => i === attributeIndex ? { ...value, primaryKey: event.target.checked } : value) } : candidate) }))} /> PK</label>
                <button type="button" className="secondary" onClick={() => setImageProposal(current => current && ({ ...current, classes: current.classes.map((candidate, index) => index === classIndex ? { ...candidate, attributes: candidate.attributes.filter((_, i) => i !== attributeIndex) } : candidate) }))}>Quitar</button>
              </div>)}
              <button type="button" className="secondary" onClick={() => setImageProposal(current => current && ({ ...current, classes: current.classes.map((candidate, index) => index === classIndex ? { ...candidate, attributes: [...candidate.attributes, { name: 'nuevoAtributo', type: 'String', primaryKey: false, required: false, unique: false }] } : candidate) }))}>Agregar atributo</button>
            </article>)}
          </div>
          <button type="button" className="secondary" onClick={() => setImageProposal(current => current && ({ ...current, classes: [...current.classes, { name: 'NuevaClase', attributes: [] }] }))}>Agregar clase</button>
          <h3>Relaciones</h3>
          {imageProposal.associations.map((link, index) => <div className="proposal-edit-row" key={index}>
            <select aria-label={`Origen de relación ${index + 1}`} value={link.source} onChange={event => setImageProposal(current => current && ({ ...current, associations: current.associations.map((value, i) => i === index ? { ...value, source: event.target.value } : value) }))}>{imageProposal.classes.map((item, i) => <option key={i} value={item.name}>{item.name}</option>)}</select>
            <select aria-label={`Cardinalidad origen ${index + 1}`} value={link.sourceCardinality} onChange={event => setImageProposal(current => current && ({ ...current, associations: current.associations.map((value, i) => i === index ? { ...value, sourceCardinality: event.target.value as typeof value.sourceCardinality } : value) }))}>{cardinalities.map(value => <option key={value}>{value}</option>)}</select>
            <select aria-label={`Destino de relación ${index + 1}`} value={link.target} onChange={event => setImageProposal(current => current && ({ ...current, associations: current.associations.map((value, i) => i === index ? { ...value, target: event.target.value } : value) }))}>{imageProposal.classes.map((item, i) => <option key={i} value={item.name}>{item.name}</option>)}</select>
            <select aria-label={`Cardinalidad destino ${index + 1}`} value={link.targetCardinality} onChange={event => setImageProposal(current => current && ({ ...current, associations: current.associations.map((value, i) => i === index ? { ...value, targetCardinality: event.target.value as typeof value.targetCardinality } : value) }))}>{cardinalities.map(value => <option key={value}>{value}</option>)}</select>
            <input aria-label={`Nombre de relación ${index + 1}`} value={link.name ?? ''} onChange={event => setImageProposal(current => current && ({ ...current, associations: current.associations.map((value, i) => i === index ? { ...value, name: event.target.value } : value) }))} />
            <button type="button" className="secondary" onClick={() => setImageProposal(current => current && ({ ...current, associations: current.associations.filter((_, i) => i !== index) }))}>Quitar</button>
          </div>)}
          {imageProposal.classes.length > 1 && <button type="button" className="secondary" onClick={() => setImageProposal(current => current && ({ ...current, associations: [...current.associations, { source: current.classes[0].name, target: current.classes[1].name, sourceCardinality: '1', targetCardinality: '0..*' }] }))}>Agregar relación</button>}
          {imageProposal.warnings?.map(warning => <p className="warning" key={warning}>{warning}</p>)}
          <footer><button className="secondary" onClick={() => setImageProposal(undefined)}>Cancelar</button><button className="primary" disabled={!imageProposal.classes.length} onClick={acceptImageProposal}>Confirmar importación</button></footer>
        </section>
      </div>}

      {xmiPreview && <div className="modal-backdrop">
        <section className="proposal-modal" role="dialog" aria-modal="true" aria-labelledby="xmi-preview-title">
          <h2 id="xmi-preview-title">Vista previa XMI 2.1</h2>
          <p>Al confirmar se reemplazará el contenido del diagrama en una sola revisión versionada.</p>
          <div className="xmi-summary">
            <span><strong>{xmiPreview.diagram.packages.length}</strong> paquetes</span>
            <span><strong>{xmiPreview.diagram.classes.length}</strong> clases</span>
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

      {agentModalOpen && <div className="modal-backdrop">
        <section className="proposal-modal agent-modal" role="dialog" aria-modal="true" aria-labelledby="agent-modal-title">
          <header className="agent-modal-header">
            <h2 id="agent-modal-title"><Monitor size={20} /> Generar App Móvil en Vivo</h2>
            <button className="icon-button" onClick={closeAgentModal}><X size={18} /></button>
          </header>

          <div className="agent-modal-body">
            {/* Progress log */}
            <div className="agent-log" aria-live="polite">
              {agentProgress.map((msg, i) => (
                <div key={i} className={`agent-log-line ${msg.startsWith('✓') ? 'success' : msg.startsWith('✗') ? 'error' : msg.startsWith('---') ? 'separator' : ''}`}>{msg}</div>
              ))}
              {(agentPhase === 'checking' || agentPhase === 'fetching' || agentPhase === 'generating' || agentPhase === 'running') && (
                <div className="agent-log-line loading"><Loader2 size={14} className="spinning" /> {agentPhase === 'checking' ? 'Verificando agente local…' : agentPhase === 'fetching' ? 'Obteniendo especificación del backend…' : agentPhase === 'generating' ? 'Generando proyecto Flutter…' : 'Ejecutando en dispositivo…'}</div>
              )}
            </div>

            {agentError && <div className="agent-error"><AlertCircle size={16} /> {agentError}</div>}

            {/* SDK status summary */}
            {agentStatus && <div className="agent-sdk-status">
              <span className={agentStatus.flutterAvailable ? 'ok' : 'missing'}>Flutter {agentStatus.flutterVersion || (agentStatus.flutterAvailable ? '✓' : '✗')}</span>
              <span className={agentStatus.adbAvailable ? 'ok' : 'missing'}>ADB {agentStatus.adbVersion || (agentStatus.adbAvailable ? '✓' : '✗')}</span>
              <span>{agentDevices.length} dispositivo(s)</span>
            </div>}

            {/* Device selection + run controls — visible when generation is ready, running, or done */}
            {(agentPhase === 'ready' || agentPhase === 'running' || agentPhase === 'done') && <div className="agent-controls">
              <div className="agent-field">
                <label htmlFor="agent-device">Dispositivo:</label>
                <select id="agent-device" value={agentSelectedDevice} onChange={e => setAgentSelectedDevice(e.target.value)}>
                  {agentDevices.length === 0 && <option value="">Ningún dispositivo conectado</option>}
                  {agentDevices.map(d => <option key={d.id} value={d.id}>{d.name} ({d.id}) — {d.status}</option>)}
                  <option value="chrome">Chrome (web)</option>
                </select>
              </div>
              <div className="agent-field">
                <label htmlFor="agent-api-url"><Wifi size={14} /> API URL:</label>
                <input id="agent-api-url" type="text" value={agentApiUrl} onChange={e => setAgentApiUrl(e.target.value)} placeholder="http://localhost:8080" />
                <small>USB: usa localhost:8080 (adb reverse automático). Wi-Fi: usa la IP de tu PC.</small>
              </div>
              <div className="agent-actions">
                <button className="primary" onClick={() => agentRunOnDevice('flutter-run')} disabled={agentPhase === 'running' || !agentSelectedDevice}>
                  <Play size={16} /> Ejecutar en dispositivo
                </button>
                <button className="secondary" onClick={() => agentRunOnDevice('flutter-build-apk')} disabled={agentPhase === 'running'}>
                  <Download size={16} /> Compilar APK release
                </button>
              </div>
              {agentOutputDir && <p className="agent-output-path">Proyecto generado en: <code>{agentOutputDir}</code></p>}
            </div>}
          </div>

          <footer>
            <button className="secondary" onClick={closeAgentModal}>Cerrar</button>
          </footer>
        </section>
      </div>}
    </main>
  );
}
