import { create } from 'zustand';
import { ApiError, DiagramChannel, PresenceParticipant, RealtimeEvent, diagramApi, subscribeToDiagram } from './api';
import {
  Association, Attribute, ClassElement, DiagramModel, DiagramOperation, EnumerationElement,
  Generalization, Position, createId, normalizeDiagram, scalarTypes,
} from './domain';
import { applyDiagramOperation } from './operationReducer';

type SyncState = 'connecting' | 'online' | 'offline' | 'syncing' | 'error';
export interface QueuedOperation { operation: DiagramOperation; status: 'pending' | 'acknowledged' | 'rejected'; createdAt: string; localDiagram: DiagramModel; error?: string }
export interface SyncConflict { operationId: string; message: string; localDiagram: DiagramModel; serverDiagram: DiagramModel }
export interface HistoryEntry { undo: DiagramOperation; redo: DiagramOperation }

export interface DiagramState {
  diagram: DiagramModel; serverDiagram: DiagramModel; confirmedRevision: number; diagramId?: string;
  selectedIds: string[]; history: HistoryEntry[]; redoHistory: HistoryEntry[];
  pendingOperations: QueuedOperation[]; conflicts: SyncConflict[]; participants: PresenceParticipant[]; eventSequence: number;
  syncState: SyncState; lastError?: string;
  initialize: (diagramId: string) => Promise<() => void>;
  selectElements: (ids: string[]) => void;
  addClass: (name?: string) => ClassElement; renameClass: (id: string, name: string) => void;
  moveClass: (id: string, x: number, y: number) => void; moveSelected: (delta: Position) => void;
  deleteClass: (id: string) => void; deleteSelected: () => void;
  addAttribute: (classId: string, attribute: Omit<Attribute, 'id' | 'version'>) => Attribute;
  updateAttribute: (classId: string, attribute: Attribute) => void;
  reorderAttribute: (classId: string, attributeId: string, newIndex: number) => void;
  deleteAttribute: (classId: string, attributeId: string) => void;
  addEnumeration: (name?: string) => EnumerationElement; updateEnumeration: (value: EnumerationElement) => void;
  deleteEnumeration: (id: string) => void;
  addAssociation: (association: Omit<Association, 'id' | 'version'>) => Association;
  updateAssociation: (association: Association) => void; deleteAssociation: (id: string) => void;
  addGeneralization: (parentId: string, childId: string) => Generalization; deleteGeneralization: (id: string) => void;
  undo: () => void; redo: () => void;
  retryConflict: (operationId: string) => void; discardConflict: (operationId: string) => void;
  reapplyConflict: (operationId: string) => void;
  sendPresence: (kind: 'CURSOR' | 'SELECTION' | 'ACTIVITY' | 'HEARTBEAT', data?: { cursor?: Position; selection?: string[]; activity?: string }) => void;
  acceptAuthoritative: (diagram: DiagramModel) => void;
}

const emptyDiagram = (): DiagramModel => ({ id: createId(), name: 'Modelo de datos', revision: 0, classes: [], enumerations: [], associations: [], generalizations: [] });
const snapshot = (diagram: DiagramModel) => structuredClone(diagram);
const storageKey = (id: string) => `collab-modeler:pending:${id}`;
let draining = false;
let channel: DiagramChannel | undefined;
let heartbeat: ReturnType<typeof setInterval> | undefined;

function persisted(id: string): QueuedOperation[] {
  try { return (JSON.parse(localStorage.getItem(storageKey(id)) ?? '[]') as QueuedOperation[]).filter(value => value.status !== 'acknowledged'); }
  catch { return []; }
}
function persist(id: string, queue: QueuedOperation[]) {
  try { localStorage.setItem(storageKey(id), JSON.stringify(queue.filter(value => value.status !== 'acknowledged'))); } catch { /* optional storage */ }
}
function replay(server: DiagramModel, queue: QueuedOperation[]): DiagramModel {
  return queue.filter(value => value.status === 'pending').reduce((value, item) => applyDiagramOperation(value, item.operation), snapshot(server));
}
async function recoverRemote(id: string, current: DiagramModel): Promise<DiagramModel> {
  try {
    const operations = await diagramApi.operations(id, current.revision); let next = snapshot(current);
    for (const remote of operations.sort((a, b) => a.resultRevision - b.resultRevision)) {
      if (remote.resultRevision !== next.revision + 1) return normalizeDiagram(await diagramApi.get(id));
      next = applyDiagramOperation(next, { operationId: remote.id, baseRevision: remote.baseRevision,
        type: remote.type, payload: JSON.parse(remote.payloadJson) as unknown }, remote.resultRevision);
    }
    return next;
  } catch { return normalizeDiagram(await diagramApi.get(id)); }
}
function withBase(value: DiagramOperation, baseRevision: number): DiagramOperation {
  if (value.type !== 'BATCH') return { ...value, baseRevision };
  const payload = value.payload as { operations: DiagramOperation[] };
  return { ...value, baseRevision, payload: { operations: payload.operations.map(item => withBase(item, baseRevision)) } };
}
function enqueue(operationValue: DiagramOperation) {
  const state = useDiagramStore.getState(); const id = state.diagramId; if (!id) return;
  const queue = [...state.pendingOperations, { operation: operationValue, status: 'pending' as const,
    createdAt: new Date().toISOString(), localDiagram: snapshot(state.diagram) }];
  persist(id, queue); useDiagramStore.setState({ pendingOperations: queue, syncState: navigator.onLine ? 'syncing' : 'offline' });
  void drainQueue();
}
async function drainQueue() {
  if (draining) return; draining = true;
  try {
    while (true) {
      const state = useDiagramStore.getState(); const id = state.diagramId;
      const item = state.pendingOperations.find(value => value.status === 'pending');
      if (!id || !item || !navigator.onLine) break;
      try {
        useDiagramStore.setState({ syncState: 'syncing' });
        const remote = normalizeDiagram(await diagramApi.apply(id, withBase(item.operation, state.serverDiagram.revision)));
        const queue = useDiagramStore.getState().pendingOperations.map(value => value.operation.operationId === item.operation.operationId
          ? { ...value, status: 'acknowledged' as const } : value);
        persist(id, queue); useDiagramStore.setState({ serverDiagram: remote, confirmedRevision: remote.revision,
          diagram: replay(remote, queue), pendingOperations: queue, syncState: 'online', lastError: undefined });
      } catch (cause) {
        const error = cause instanceof Error ? cause : new Error('No se pudo guardar la operación');
        if (cause instanceof ApiError && cause.status === 409) {
          const current = useDiagramStore.getState(); const authoritative = await recoverRemote(id, current.serverDiagram);
          const queue = current.pendingOperations.map(value => value.operation.operationId === item.operation.operationId
            ? { ...value, status: 'rejected' as const, error: error.message } : value);
          persist(id, queue); useDiagramStore.setState({ serverDiagram: authoritative, confirmedRevision: authoritative.revision,
            diagram: replay(authoritative, queue), pendingOperations: queue,
            conflicts: [...current.conflicts, { operationId: item.operation.operationId, message: error.message,
              localDiagram: item.localDiagram, serverDiagram: authoritative }], syncState: 'error', lastError: error.message });
          continue;
        }
        if (cause instanceof ApiError && [401, 403, 404].includes(cause.status)) {
          const current = useDiagramStore.getState();
          const queue = current.pendingOperations.map(value => value.operation.operationId === item.operation.operationId
            ? { ...value, status: 'rejected' as const, error: error.message } : value);
          persist(id, queue); useDiagramStore.setState({
            diagram: replay(current.serverDiagram, queue), pendingOperations: queue,
            syncState: 'error', lastError: error.message,
          });
          continue;
        }
        useDiagramStore.setState({ syncState: 'offline', lastError: error.message }); break;
      }
    }
  } finally { draining = false; }
}

function operation(type: DiagramOperation['type'], baseRevision: number, payload: unknown, expectedElementVersion?: number): DiagramOperation {
  return { type, operationId: createId(), baseRevision, expectedElementVersion, payload };
}
function withRevision(value: DiagramModel): DiagramModel { return { ...value, revision: value.revision + 1 }; }
function assertName(name: string, label: string) { if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) throw new Error(`Nombre de ${label} inválido`); }
function assertAttributeType(diagram: DiagramModel, type: string) {
  if (!scalarTypes.includes(type as (typeof scalarTypes)[number]) && !diagram.enumerations.some(item => item.name === type || item.id === type)) throw new Error(`Tipo de atributo no soportado: ${type}`);
}
function commit(before: DiagramModel, next: DiagramModel, op: DiagramOperation, remember = true) {
  const optimistic = withRevision(next);
  const inverse = remember ? transition(optimistic, before) : undefined;
  useDiagramStore.setState(state => ({ diagram: optimistic, history: remember && inverse ? [...state.history, { undo: inverse, redo: op }] : state.history,
    redoHistory: remember ? [] : state.redoHistory, lastError: undefined })); enqueue(op);
}

export const useDiagramStore = create<DiagramState>((set, get) => ({
  diagram: emptyDiagram(), serverDiagram: emptyDiagram(), confirmedRevision: 0, selectedIds: [], history: [], redoHistory: [],
  pendingOperations: [], conflicts: [], participants: [], eventSequence: 0, syncState: 'connecting',
  initialize: async requestedId => {
    try {
      const serverDiagram = normalizeDiagram(await diagramApi.get(requestedId)); const queue = persisted(requestedId);
      set({ diagram: replay(serverDiagram, queue), serverDiagram, confirmedRevision: serverDiagram.revision, diagramId: requestedId,
        pendingOperations: queue, conflicts: queue.filter(value => value.status === 'rejected').map(value => ({
          operationId: value.operation.operationId, message: value.error ?? 'Cambio rechazado por el servidor',
          localDiagram: value.localDiagram, serverDiagram,
        })), participants: [], syncState: 'connecting', lastError: undefined });
      channel?.close(); if (heartbeat) clearInterval(heartbeat);
      channel = subscribeToDiagram(requestedId, {
        receive: (event: RealtimeEvent) => {
          if (event.type === 'PRESENCE') set({ participants: event.payload.participants });
          else if (event.type === 'OPERATION_APPLIED') {
            const remote = normalizeDiagram(event.payload.diagram); const state = get();
            if (remote.revision <= state.serverDiagram.revision) return;
            const ownId = event.payload.operation.operationId;
            const updatedQueue = state.pendingOperations.map(value => value.operation.operationId === ownId ? { ...value, status: 'acknowledged' as const } : value);
            persist(requestedId, updatedQueue); set({ serverDiagram: remote, confirmedRevision: remote.revision,
              pendingOperations: updatedQueue, diagram: replay(remote, updatedQueue), eventSequence: state.eventSequence + 1 });
          } else if (event.type === 'MODEL_RESTORED') {
            const remote = normalizeDiagram(event.payload); const state = get();
            set({ serverDiagram: remote, confirmedRevision: remote.revision, diagram: replay(remote, state.pendingOperations), eventSequence: state.eventSequence + 1 });
          } else set(state => ({ eventSequence: state.eventSequence + 1 }));
        },
        connected: () => { set({ syncState: 'online' }); void recoverRemote(requestedId, get().serverDiagram).then(remote => {
          const state = get(); set({ serverDiagram: remote, confirmedRevision: remote.revision, diagram: replay(remote, state.pendingOperations) }); void drainQueue();
        }); },
        disconnected: () => set({ syncState: 'offline' }),
      });
      heartbeat = setInterval(() => channel?.presence({ kind: 'HEARTBEAT', selection: get().selectedIds }), 20000);
      const online = () => void drainQueue(); const offline = () => set({ syncState: 'offline' });
      window.addEventListener('online', online); window.addEventListener('offline', offline); void drainQueue();
      return () => { channel?.close(); channel = undefined; if (heartbeat) clearInterval(heartbeat); window.removeEventListener('online', online); window.removeEventListener('offline', offline); };
    } catch (cause) { set({ syncState: 'offline', lastError: cause instanceof Error ? cause.message : 'API no disponible' }); return () => undefined; }
  },
  selectElements: selectedIds => {
    // React Flow reports the current selection again after receiving controlled
    // nodes. Updating Zustand with that same value triggers another render and
    // creates an infinite React Flow <-> store update cycle.
    const currentSelection = get().selectedIds;
    const unchanged = currentSelection.length === selectedIds.length
      && currentSelection.every((id, index) => id === selectedIds[index]);
    if (unchanged) return;

    set({ selectedIds });
    channel?.presence({ kind: 'SELECTION', selection: selectedIds });
  },
  addClass: (name = 'NuevaClase') => {
    assertName(name, 'clase'); const before = get().diagram;
    if ([...before.classes, ...before.enumerations].some(item => item.name.toLowerCase() === name.toLowerCase())) throw new Error(`Ya existe el tipo ${name}`);
    const item: ClassElement = { id: createId(), kind: 'class', name, attributes: [], position: { x: 160 + before.classes.length * 35, y: 120 + before.classes.length * 30 }, version: 1 };
    commit(before, { ...before, classes: [...before.classes, item] }, operation('CLASS_CREATED', before.revision, item)); set({ selectedIds: [item.id] }); return item;
  },
  renameClass: (id, name) => {
    assertName(name, 'clase'); const before = get().diagram; const item = before.classes.find(value => value.id === id); if (!item) return;
    if ([...before.classes, ...before.enumerations].some(value => value.id !== id && value.name.toLowerCase() === name.toLowerCase())) throw new Error(`Ya existe el tipo ${name}`);
    commit(before, { ...before, classes: before.classes.map(value => value.id === id ? { ...value, name, version: value.version + 1 } : value) }, operation('CLASS_RENAMED', before.revision, { id, name }, item.version));
  },
  moveClass: (id, x, y) => {
    const before = get().diagram; const item = before.classes.find(value => value.id === id); if (!item || (item.position.x === x && item.position.y === y)) return;
    commit(before, { ...before, classes: before.classes.map(value => value.id === id ? { ...value, position: { x, y }, version: value.version + 1 } : value) }, operation('CLASS_MOVED', before.revision, { id, x, y }, item.version));
  },
  moveSelected: delta => {
    const before = get().diagram; const selected = new Set(get().selectedIds);
    const selectedClasses = before.classes.filter(item => selected.has(item.id)); const selectedEnums = before.enumerations.filter(item => selected.has(item.id)); if (!selectedClasses.length && !selectedEnums.length) return;
    const nested = selectedClasses.map(item => operation('CLASS_MOVED', before.revision, { id: item.id, x: item.position.x + delta.x, y: item.position.y + delta.y }, item.version));
    selectedEnums.forEach(item => nested.push(operation('ENUMERATION_UPDATED', before.revision, { ...item, position: { x: item.position.x + delta.x, y: item.position.y + delta.y } }, item.version)));
    const next = { ...before, classes: before.classes.map(item => selected.has(item.id) ? { ...item, position: { x: item.position.x + delta.x, y: item.position.y + delta.y }, version: item.version + 1 } : item),
      enumerations: before.enumerations.map(item => selected.has(item.id) ? { ...item, position: { x: item.position.x + delta.x, y: item.position.y + delta.y }, version: item.version + 1 } : item) };
    commit(before, next, operation('BATCH', before.revision, { operations: nested }));
  },
  deleteClass: id => {
    const before = get().diagram; const item = before.classes.find(value => value.id === id); if (!item) return;
    commit(before, { ...before, classes: before.classes.filter(value => value.id !== id), associations: before.associations.filter(link => link.sourceId !== id && link.targetId !== id),
      generalizations: before.generalizations.filter(link => link.parentId !== id && link.childId !== id) }, operation('CLASS_DELETED', before.revision, { id }, item.version));
    set({ selectedIds: get().selectedIds.filter(value => value !== id) });
  },
  deleteSelected: () => {
    const before = get().diagram; const selected = new Set(get().selectedIds);
    const enumNames = new Set(before.enumerations.filter(item => selected.has(item.id)).flatMap(item => [item.name, item.id]));
    if (before.classes.filter(item => !selected.has(item.id)).some(item => item.attributes.some(attribute => enumNames.has(attribute.type)))) throw new Error('No se puede eliminar una enumeración utilizada');
    const nested: DiagramOperation[] = [];
    before.associations.filter(item => selected.has(item.id)).forEach(item => nested.push(operation('ASSOCIATION_DELETED', before.revision, { id: item.id }, item.version)));
    before.generalizations.filter(item => selected.has(item.id)).forEach(item => nested.push(operation('GENERALIZATION_DELETED', before.revision, { id: item.id }, item.version)));
    before.enumerations.filter(item => selected.has(item.id)).forEach(item => nested.push(operation('ENUMERATION_DELETED', before.revision, { id: item.id }, item.version)));
    before.classes.filter(item => selected.has(item.id)).forEach(item => nested.push(operation('CLASS_DELETED', before.revision, { id: item.id }, item.version))); if (!nested.length) return;
    const classIds = new Set(before.classes.filter(item => selected.has(item.id)).map(item => item.id));
    commit(before, { ...before, classes: before.classes.filter(item => !selected.has(item.id)), enumerations: before.enumerations.filter(item => !selected.has(item.id)),
      associations: before.associations.filter(item => !selected.has(item.id) && !classIds.has(item.sourceId) && !classIds.has(item.targetId)),
      generalizations: before.generalizations.filter(item => !selected.has(item.id) && !classIds.has(item.parentId) && !classIds.has(item.childId)) }, operation('BATCH', before.revision, { operations: nested })); set({ selectedIds: [] });
  },
  addAttribute: (classId, input) => {
    assertName(input.name, 'atributo'); const before = get().diagram; assertAttributeType(before, input.type); const owner = before.classes.find(item => item.id === classId); if (!owner) throw new Error('Clase no encontrada');
    if (owner.attributes.some(item => item.name.toLowerCase() === input.name.toLowerCase())) throw new Error(`Ya existe el atributo ${input.name}`);
    const attribute: Attribute = { ...input, id: createId(), version: 1 };
    commit(before, { ...before, classes: before.classes.map(item => item.id === classId ? { ...item, version: item.version + 1, attributes: [...item.attributes, attribute] } : item) },
      operation('ATTRIBUTE_CREATED', before.revision, { classId, attribute }, owner.version)); return attribute;
  },
  updateAttribute: (classId, attribute) => {
    assertName(attribute.name, 'atributo'); const before = get().diagram; assertAttributeType(before, attribute.type); const owner = before.classes.find(item => item.id === classId); const old = owner?.attributes.find(item => item.id === attribute.id); if (!owner || !old) return;
    if (owner.attributes.some(item => item.id !== attribute.id && item.name.toLowerCase() === attribute.name.toLowerCase())) throw new Error(`Ya existe el atributo ${attribute.name}`);
    const updated = { ...attribute, version: old.version + 1 };
    commit(before, { ...before, classes: before.classes.map(item => item.id === classId ? { ...item, version: item.version + 1, attributes: item.attributes.map(value => value.id === old.id ? updated : value) } : item) },
      operation('ATTRIBUTE_UPDATED', before.revision, { classId, attribute }, old.version));
  },
  reorderAttribute: (classId, attributeId, newIndex) => {
    const before = get().diagram; const owner = before.classes.find(item => item.id === classId); if (!owner) return; const values = [...owner.attributes]; const oldIndex = values.findIndex(item => item.id === attributeId);
    if (oldIndex < 0 || newIndex < 0 || newIndex >= values.length || oldIndex === newIndex) return; const [moved] = values.splice(oldIndex, 1); values.splice(newIndex, 0, moved);
    commit(before, { ...before, classes: before.classes.map(item => item.id === classId ? { ...item, version: item.version + 1, attributes: values } : item) },
      operation('ATTRIBUTE_REORDERED', before.revision, { classId, attributeId, newIndex }, owner.version));
  },
  deleteAttribute: (classId, attributeId) => {
    const before = get().diagram; const owner = before.classes.find(item => item.id === classId); const old = owner?.attributes.find(item => item.id === attributeId); if (!owner || !old) return;
    commit(before, { ...before, classes: before.classes.map(item => item.id === classId ? { ...item, version: item.version + 1, attributes: item.attributes.filter(value => value.id !== attributeId) } : item) },
      operation('ATTRIBUTE_DELETED', before.revision, { classId, id: attributeId }, old.version));
  },
  addEnumeration: (name = 'NuevaEnumeracion') => {
    assertName(name, 'enumeración'); const before = get().diagram;
    if ([...before.classes, ...before.enumerations].some(item => item.name.toLowerCase() === name.toLowerCase())) throw new Error(`Ya existe el tipo ${name}`);
    const item: EnumerationElement = { id: createId(), kind: 'enumeration', name, values: [], position: { x: 220 + before.enumerations.length * 35, y: 180 + before.enumerations.length * 30 }, version: 1 };
    commit(before, { ...before, enumerations: [...before.enumerations, item] }, operation('ENUMERATION_CREATED', before.revision, item)); set({ selectedIds: [item.id] }); return item;
  },
  updateEnumeration: value => {
    assertName(value.name, 'enumeración'); const before = get().diagram; const old = before.enumerations.find(item => item.id === value.id); if (!old) return;
    if ([...before.classes, ...before.enumerations].some(item => item.id !== value.id && item.name.toLowerCase() === value.name.toLowerCase())) throw new Error(`Ya existe el tipo ${value.name}`);
    const names = value.values.map(item => item.name.toLowerCase()); if (new Set(names).size !== names.length) throw new Error('Los valores no pueden repetirse'); value.values.forEach(item => assertName(item.name, 'valor'));
    const nested: DiagramOperation[] = []; let enumerationVersion = old.version;
    if (old.name !== value.name || old.position.x !== value.position.x || old.position.y !== value.position.y) {
      nested.push(operation('ENUMERATION_UPDATED', before.revision, { ...old, name: value.name, position: value.position }, enumerationVersion)); enumerationVersion++;
    }
    for (const previous of old.values) {
      const desired = value.values.find(item => item.id === previous.id);
      if (!desired) { nested.push(operation('ENUMERATION_VALUE_DELETED', before.revision, { enumerationId: old.id, id: previous.id }, previous.version)); enumerationVersion++; }
      else if (desired.name !== previous.name) { nested.push(operation('ENUMERATION_VALUE_UPDATED', before.revision, { enumerationId: old.id, value: desired }, previous.version)); enumerationVersion++; }
    }
    for (const desired of value.values.filter(item => !old.values.some(previous => previous.id === item.id))) {
      nested.push(operation('ENUMERATION_VALUE_CREATED', before.revision, { enumerationId: old.id, value: desired }, enumerationVersion)); enumerationVersion++;
    }
    if (!nested.length) return;
    const updated = { ...value, version: enumerationVersion, values: value.values.map(item => {
      const previous = old.values.find(candidate => candidate.id === item.id);
      return { ...item, version: previous ? previous.version + (previous.name === item.name ? 0 : 1) : 1 };
    }) };
    const batch = operation('BATCH', before.revision, { operations: nested });
    commit(before, { ...before, enumerations: before.enumerations.map(item => item.id === value.id ? updated : item) }, batch);
  },
  deleteEnumeration: id => {
    const before = get().diagram; const old = before.enumerations.find(item => item.id === id); if (!old) return;
    if (before.classes.some(item => item.attributes.some(attribute => attribute.type === old.name || attribute.type === old.id))) throw new Error('No se puede eliminar una enumeración utilizada');
    commit(before, { ...before, enumerations: before.enumerations.filter(item => item.id !== id) }, operation('ENUMERATION_DELETED', before.revision, { id }, old.version)); set({ selectedIds: get().selectedIds.filter(value => value !== id) });
  },
  addAssociation: input => {
    const before = get().diagram; if (!before.classes.some(item => item.id === input.sourceId) || !before.classes.some(item => item.id === input.targetId)) throw new Error('La asociación referencia una clase inexistente');
    if (before.associations.some(item => item.sourceId === input.sourceId && item.targetId === input.targetId && (item.name ?? '').toLowerCase() === (input.name ?? '').toLowerCase())) throw new Error('La asociación ya existe');
    const item: Association = { ...input, id: createId(), version: 1 }; commit(before, { ...before, associations: [...before.associations, item] }, operation('ASSOCIATION_CREATED', before.revision, item)); set({ selectedIds: [item.id] }); return item;
  },
  updateAssociation: value => {
    const before = get().diagram; const old = before.associations.find(item => item.id === value.id); if (!old) return;
    if (before.associations.some(item => item.id !== value.id && item.sourceId === value.sourceId && item.targetId === value.targetId && (item.name ?? '').toLowerCase() === (value.name ?? '').toLowerCase())) throw new Error('La asociación ya existe');
    const updated = { ...value, version: old.version + 1 }; commit(before, { ...before, associations: before.associations.map(item => item.id === value.id ? updated : item) }, operation('ASSOCIATION_UPDATED', before.revision, value, old.version));
  },
  deleteAssociation: id => {
    const before = get().diagram; const old = before.associations.find(item => item.id === id); if (!old) return;
    commit(before, { ...before, associations: before.associations.filter(item => item.id !== id) }, operation('ASSOCIATION_DELETED', before.revision, { id }, old.version)); set({ selectedIds: get().selectedIds.filter(value => value !== id) });
  },
  addGeneralization: (parentId, childId) => {
    if (parentId === childId) throw new Error('Una clase no puede heredarse a sí misma'); const before = get().diagram;
    if (!before.classes.some(item => item.id === parentId) || !before.classes.some(item => item.id === childId)) throw new Error('La herencia referencia una clase inexistente');
    if (before.generalizations.some(item => item.parentId === parentId && item.childId === childId)) throw new Error('La herencia ya existe');
    const parentsOf = (id: string) => before.generalizations.filter(item => item.childId === id).map(item => item.parentId); const pending = [parentId]; const visited = new Set<string>();
    while (pending.length) { const current = pending.pop()!; if (current === childId) throw new Error('La herencia produciría un ciclo'); if (!visited.has(current)) { visited.add(current); pending.push(...parentsOf(current)); } }
    const item: Generalization = { id: createId(), parentId, childId, version: 1 }; commit(before, { ...before, generalizations: [...before.generalizations, item] }, operation('GENERALIZATION_CREATED', before.revision, item)); set({ selectedIds: [item.id] }); return item;
  },
  deleteGeneralization: id => {
    const before = get().diagram; const old = before.generalizations.find(item => item.id === id); if (!old) return;
    commit(before, { ...before, generalizations: before.generalizations.filter(item => item.id !== id) }, operation('GENERALIZATION_DELETED', before.revision, { id }, old.version)); set({ selectedIds: get().selectedIds.filter(value => value !== id) });
  },
  undo: () => {
    const state = get(); const entry = state.history.at(-1); if (!entry) return;
    const compensating = withBase(structuredClone(entry.undo), state.diagram.revision);
    const next = applyDiagramOperation(state.diagram, compensating);
    set({ diagram: next, history: state.history.slice(0, -1), redoHistory: [...state.redoHistory, entry], selectedIds: [] }); enqueue(compensating);
  },
  redo: () => {
    const state = get(); const entry = state.redoHistory.at(-1); if (!entry) return;
    const compensating = rebaseOperation(entry.redo, state.diagram);
    const next = applyDiagramOperation(state.diagram, compensating);
    const inverse = transition(next, state.diagram);
    if (!inverse) return;
    set({ diagram: next, history: [...state.history, { undo: inverse, redo: compensating }], redoHistory: state.redoHistory.slice(0, -1), selectedIds: [] }); enqueue(compensating);
  },
  retryConflict: operationId => {
    const state = get(); const queue = state.pendingOperations.map(value => value.operation.operationId === operationId ? { ...value, status: 'pending' as const, error: undefined } : value);
    if (state.diagramId) persist(state.diagramId, queue); set({ pendingOperations: queue, conflicts: state.conflicts.filter(value => value.operationId !== operationId), diagram: replay(state.serverDiagram, queue), lastError: undefined }); void drainQueue();
  },
  discardConflict: operationId => {
    const state = get(); const queue = state.pendingOperations.filter(value => value.operation.operationId !== operationId);
    if (state.diagramId) persist(state.diagramId, queue); set({ pendingOperations: queue, conflicts: state.conflicts.filter(value => value.operationId !== operationId), diagram: replay(state.serverDiagram, queue), lastError: undefined, syncState: 'online' });
  },
  reapplyConflict: operationId => {
    const state = get(); const item = state.pendingOperations.find(value => value.operation.operationId === operationId); if (!item) return;
    const queue = state.pendingOperations.filter(value => value.operation.operationId !== operationId); const rebased = rebaseOperation(item.operation, state.serverDiagram);
    try { const next = applyDiagramOperation(replay(state.serverDiagram, queue), rebased); if (state.diagramId) persist(state.diagramId, queue);
      set({ diagram: next, pendingOperations: queue, conflicts: state.conflicts.filter(value => value.operationId !== operationId), lastError: undefined }); enqueue(rebased);
    } catch { set({ lastError: 'El elemento ya no existe; descarta el cambio o edítalo nuevamente.' }); }
  },
  sendPresence: (kind, data = {}) => channel?.presence({ kind, ...data }),
  acceptAuthoritative: value => { const remote = normalizeDiagram(value); const state = get(); set({ serverDiagram: remote, confirmedRevision: remote.revision, diagram: replay(remote, state.pendingOperations), eventSequence: state.eventSequence + 1 }); },
}));

function rebaseOperation(value: DiagramOperation, diagram: DiagramModel): DiagramOperation {
  const op = { ...structuredClone(value), operationId: createId(), baseRevision: diagram.revision };
  if (op.type === 'BATCH') {
    let working = diagram; const children = ((op.payload as { operations: DiagramOperation[] }).operations).map(child => {
      const next = rebaseOperation(child, working); working = applyDiagramOperation(working, next, working.revision); return next;
    }); op.payload = { operations: children }; return op;
  }
  const payload = op.payload as Record<string, unknown>;
  const attributePayload = payload.attribute as Record<string, unknown> | undefined;
  const id = String(op.type === 'ATTRIBUTE_UPDATED' ? attributePayload?.id :
    op.type === 'ATTRIBUTE_DELETED' ? payload.id : payload.id ?? payload.classId ?? '');
  const found = diagram.classes.find(item => item.id === id) ?? diagram.enumerations.find(item => item.id === id)
    ?? diagram.associations.find(item => item.id === id) ?? diagram.generalizations.find(item => item.id === id)
    ?? diagram.classes.flatMap(item => item.attributes).find(item => item.id === id);
  if (op.expectedElementVersion !== undefined && found) op.expectedElementVersion = found.version;
  return op;
}

function transition(from: DiagramModel, target: DiagramModel): DiagramOperation | undefined {
  let working = snapshot(from); const operations: DiagramOperation[] = [];
  const push = (value: DiagramOperation) => { operations.push(value); working = applyDiagramOperation(working, value, working.revision); };
  const make = (type: DiagramOperation['type'], payload: unknown, expected?: number) => operation(type, from.revision, payload, expected);
  working.associations.filter(item => !target.associations.some(value => value.id === item.id)).forEach(item => push(make('ASSOCIATION_DELETED', { id: item.id }, item.version)));
  working.generalizations.filter(item => !target.generalizations.some(value => value.id === item.id)).forEach(item => push(make('GENERALIZATION_DELETED', { id: item.id }, item.version)));
  working.classes.filter(item => !target.classes.some(value => value.id === item.id)).forEach(item => push(make('CLASS_DELETED', { id: item.id }, item.version)));
  target.enumerations.filter(item => !working.enumerations.some(value => value.id === item.id)).forEach(item => push(make('ENUMERATION_CREATED', item)));
  target.enumerations.forEach(item => { const old = working.enumerations.find(value => value.id === item.id); if (old && semantic(old) !== semantic(item)) push(make('ENUMERATION_UPDATED', item, old.version)); });
  target.classes.filter(item => !working.classes.some(value => value.id === item.id)).forEach(item => push(make('CLASS_CREATED', item)));
  target.classes.forEach(item => {
    let old = working.classes.find(value => value.id === item.id); if (!old) return;
    if (old.name !== item.name) { push(make('CLASS_RENAMED', { id: item.id, name: item.name }, old.version)); old = working.classes.find(value => value.id === item.id)!; }
    if (old.position.x !== item.position.x || old.position.y !== item.position.y) { push(make('CLASS_MOVED', { id: item.id, ...item.position }, old.version)); old = working.classes.find(value => value.id === item.id)!; }
    old.attributes.filter(attribute => !item.attributes.some(value => value.id === attribute.id)).forEach(attribute => push(make('ATTRIBUTE_DELETED', { classId: item.id, id: attribute.id }, attribute.version)));
    item.attributes.forEach(attribute => { const current = working.classes.find(value => value.id === item.id)!.attributes.find(value => value.id === attribute.id); if (!current) {
      const owner = working.classes.find(value => value.id === item.id)!; push(make('ATTRIBUTE_CREATED', { classId: item.id, attribute }, owner.version));
    } else if (semantic(current) !== semantic(attribute)) push(make('ATTRIBUTE_UPDATED', { classId: item.id, attribute }, current.version)); });
    item.attributes.forEach((attribute, index) => { const owner = working.classes.find(value => value.id === item.id)!; const actual = owner.attributes.findIndex(value => value.id === attribute.id);
      if (actual !== index) push(make('ATTRIBUTE_REORDERED', { classId: item.id, attributeId: attribute.id, newIndex: index }, owner.version)); });
  });
  working.enumerations.filter(item => !target.enumerations.some(value => value.id === item.id)).forEach(item => push(make('ENUMERATION_DELETED', { id: item.id }, item.version)));
  target.associations.forEach(item => { const old = working.associations.find(value => value.id === item.id); if (!old) push(make('ASSOCIATION_CREATED', item)); else if (semantic(old) !== semantic(item)) push(make('ASSOCIATION_UPDATED', item, old.version)); });
  target.generalizations.filter(item => !working.generalizations.some(value => value.id === item.id)).forEach(item => push(make('GENERALIZATION_CREATED', item)));
  return operations.length ? operation('BATCH', from.revision, { operations }) : undefined;
}
function semantic(value: unknown) { return JSON.stringify(value, (key, item) => key === 'version' || key === 'revision' ? undefined : item); }
