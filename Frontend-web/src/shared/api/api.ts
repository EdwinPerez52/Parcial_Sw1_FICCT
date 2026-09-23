import { Client } from '@stomp/stompjs';
import { DiagramModel, DiagramOperation } from '../../features/editor/domain';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details: Record<string, unknown> = {}) { super(message); }
}

let csrfToken = '';

export function getCsrfToken(): string {
  if (csrfToken) return csrfToken;
  if (typeof document !== 'undefined') {
    const match = document.cookie.match(/(^|;)\s*XSRF-TOKEN\s*=\s*([^;]+)/);
    if (match) return decodeURIComponent(match[2]);
  }
  return '';
}

async function raw<T>(url: string, options?: RequestInit): Promise<T> {
  const headers = new Headers(options?.headers);
  if (!(options?.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const token = getCsrfToken();
  if (token && options?.method && !['GET', 'HEAD'].includes(options.method)) headers.set('X-XSRF-TOKEN', token);
  let response: Response;
  try { response = await fetch(url, { credentials: 'include', ...options, headers }); }
  catch { throw new ApiError(0, 'NETWORK_ERROR', 'No se pudo conectar con el servidor.'); }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; code?: string };
    const fallback: Record<number, string> = {
      401: 'Tu sesión terminó. Inicia sesión nuevamente.', 403: 'No tienes permiso para realizar esta acción.',
      404: 'El recurso solicitado no existe.', 409: 'Hubo un conflicto; actualiza la página e inténtalo otra vez.',
    };
    throw new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail ?? fallback[response.status] ?? `Error HTTP ${response.status}`, problem as Record<string, unknown>);
  }
  return response.status === 204 ? undefined as T : response.json();
}

export interface CurrentUser {
  authenticated: boolean; email: string | null; fullName: string | null;
  verified: boolean; platformAdmin: boolean; csrfToken: string;
}
export interface ProjectSummary { id: string; name: string; role: 'OWNER' | 'EDITOR' | 'READER'; revision: number; updatedAt: string }
export interface MemberItem { id: string; displayName: string; role: 'OWNER' | 'EDITOR' | 'READER' | 'PENDING'; joinedAt: string }
export interface InvitationInfo { valid: boolean; email: string | null; diagramId: string | null; registrationAllowed: boolean }

export const authApi = {
  me: async () => { const me = await raw<CurrentUser>('/api/v1/auth/me'); csrfToken = me.csrfToken; return me; },
  login: async (email: string, password: string) => {
    const user = await raw<CurrentUser>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    csrfToken = user.csrfToken;
    return user;
  },
  register: (fullName: string, email: string, password: string, passwordConfirmation: string, invitationToken?: string) =>
    raw<{ message: string }>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify({ fullName, email, password, passwordConfirmation, invitationToken: invitationToken || null }) }),
  invitation: (token: string) => raw<InvitationInfo>(`/api/v1/auth/invitations/${encodeURIComponent(token)}`),
  verify: (token: string) => raw<{ verified: boolean; diagramId?: string }>('/api/v1/auth/verify', { method: 'POST', body: JSON.stringify({ token }) }),
  resendVerification: (email: string) => raw<{ message: string }>('/api/v1/auth/verification/request', { method: 'POST', body: JSON.stringify({ email }) }),
  forgot: (email: string) => raw<{ message: string }>('/api/v1/auth/password/forgot', { method: 'POST', body: JSON.stringify({ email }) }),
  reset: (token: string, password: string, passwordConfirmation: string) => raw<{ message: string }>('/api/v1/auth/password/reset', { method: 'POST', body: JSON.stringify({ token, password, passwordConfirmation }) }),
  logout: async () => { await raw<void>('/api/v1/auth/logout', { method: 'POST' }); csrfToken = ''; },
};

export const diagramApi = {
  list: () => raw<ProjectSummary[]>('/api/v1/diagrams'),
  create: (name: string) => raw<DiagramModel>('/api/v1/diagrams', { method: 'POST', body: JSON.stringify({ name }) }),
  get: (id: string) => raw<DiagramModel>(`/api/v1/diagrams/${id}`),
  apply: (id: string, operation: DiagramOperation & { expectedElementVersion?: number }) =>
    raw<DiagramModel>(`/api/v1/diagrams/${id}/operations`, { method: 'POST', body: JSON.stringify(operation) }),
  operations: (id: string, since: number) => raw<RemoteOperation[]>(`/api/v1/diagrams/${id}/operations?since=${since}`),
  share: (id: string) => raw<{ token: string; path: string }>(`/api/v1/diagrams/${id}/share-link`, { method: 'POST' }),
  revokeShare: (id: string) => raw<void>(`/api/v1/diagrams/${id}/share-link`, { method: 'DELETE' }),
  members: (id: string) => raw<MemberItem[]>(`/api/v1/diagrams/${id}/members`),
  updateMember: (id: string, memberId: string, role: 'EDITOR' | 'READER') => raw<MemberItem>(`/api/v1/diagrams/${id}/members/${memberId}`, {
    method: 'PATCH', body: JSON.stringify({ role }),
  }),
  removeMember: (id: string, memberId: string) => raw<void>(`/api/v1/diagrams/${id}/members/${memberId}`, { method: 'DELETE' }),
  join: (token: string) => raw<{ diagramId: string }>(`/api/v1/join/${encodeURIComponent(token)}`, { method: 'POST' }),
  generationUrl: (id: string, versionId?: string) =>
    `/api/v1/diagrams/${id}/generation?groupId=com.generated&artifactId=generated-api${versionId ? `&versionId=${encodeURIComponent(versionId)}` : ''}`,
  mobileSpecUrl: (id: string, versionId?: string) =>
    `/api/v1/diagrams/${id}/mobile-spec${versionId ? `?versionId=${encodeURIComponent(versionId)}` : ''}`,
  flutterGenerationUrl: (id: string, versionId?: string) =>
    `/api/v1/diagrams/${id}/flutter-generation${versionId ? `?versionId=${encodeURIComponent(versionId)}` : ''}`,
  xmiExportUrl: (id: string) => `/api/v1/diagrams/${id}/xmi`,
};

export type GenerationJobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export interface GenerationArtifactLink { url: string; expiresAt: string }
export interface GenerationJob {
  id: string; diagramId: string; versionId: string; sourceRevision: number; requesterName: string;
  status: GenerationJobStatus; attempt: number; queuedAt: string; startedAt: string | null;
  completedAt: string | null; expiresAt: string | null; errorCode: string | null; errorMessage: string | null;
  backend: GenerationArtifactLink | null; mobileSpec: GenerationArtifactLink | null;
}
export const generationJobsApi = {
  create: (diagramId: string, idempotencyKey: string, request: { versionId?: string; groupId?: string; artifactId?: string } = {}) =>
    raw<GenerationJob>(`/api/v1/diagrams/${diagramId}/generation-jobs`, {
      method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(request),
    }),
  list: (diagramId: string) => raw<GenerationJob[]>(`/api/v1/diagrams/${diagramId}/generation-jobs`),
  get: (diagramId: string, jobId: string) => raw<GenerationJob>(`/api/v1/diagrams/${diagramId}/generation-jobs/${jobId}`),
  retry: (diagramId: string, jobId: string) => raw<GenerationJob>(`/api/v1/diagrams/${diagramId}/generation-jobs/${jobId}/retry`, { method: 'POST' }),
  agentSpec: (diagramId: string, jobId: string) => raw<unknown>(`/api/v1/diagrams/${diagramId}/generation-jobs/${jobId}/agent-spec`, { method: 'POST' }),
};

export interface AssistantProposal {
  proposalId: string;
  provider: string;
  requiresConfirmation: boolean;
  operation: DiagramOperation;
  previewDiagram: DiagramModel;
  summary: string;
}

export interface AppliedAssistantProposal { operation: DiagramOperation; diagram: DiagramModel; provider: string }

export const assistantApi = {
  interpret: (diagramId: string, instruction: string) => raw<AssistantProposal>(`/api/v1/diagrams/${diagramId}/assistant/proposals`, {
    method: 'POST', body: JSON.stringify({ instruction }),
  }),
  apply: (diagramId: string, proposalId: string, confirmed: boolean) => raw<AppliedAssistantProposal>(`/api/v1/diagrams/${diagramId}/assistant/proposals/${proposalId}/apply`, {
    method: 'POST', body: JSON.stringify({ confirmed }),
  }),
  transcribe: async (diagramId: string, audio: Blob): Promise<string> => {
    const form = new FormData();
    const extension = audio.type.startsWith('audio/mp4') ? 'mp4' : audio.type.startsWith('audio/ogg') ? 'ogg' : 'webm';
    form.append('file', audio, `voz.${extension}`);
    return (await raw<{ text: string }>(`/api/v1/diagrams/${diagramId}/assistant/transcriptions`, { method: 'POST', body: form })).text;
  },
};

export interface XmiWarning { code: string; message: string; externalId?: string; elementType?: string }
export interface XmiImportPreview { diagram: DiagramModel; warnings: XmiWarning[] }

export async function previewXmi(id: string, file: File): Promise<XmiImportPreview> {
  const body = new FormData(); body.append('file', file);
  return raw<XmiImportPreview>(`/api/v1/diagrams/${id}/xmi/preview`, { method: 'POST', body });
}

export async function downloadXmi(id: string, name: string): Promise<void> {
  const response = await fetch(diagramApi.xmiExportUrl(id), { credentials: 'include' });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; code?: string };
    throw new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail ?? 'No se pudo exportar XMI');
  }
  const disposition = response.headers.get('content-disposition');
  const filename = extractFilename(disposition, `${name}.xmi`);
  triggerBlobDownload(await response.blob(), filename, 'application/xml');
}

export interface RemoteOperation {
  id: string; diagramId: string; baseRevision: number; resultRevision: number; type: DiagramOperation['type'];
  payloadJson: string; authorName: string; createdAt: string;
}

export interface CommentItem {
  id: string; diagramId: string; targetType: 'DIAGRAM' | 'CLASS' | 'ATTRIBUTE' | 'ASSOCIATION' | 'ENUMERATION' | 'GENERALIZATION';
  targetId: string | null; parentCommentId: string | null; body: string; authorName: string; resolved: boolean;
  createdAt: string; updatedAt: string; resolvedAt: string | null; version: number;
}
export interface VersionItem { id: string; sourceRevision: number; label: string; authorName: string; createdAt: string; snapshot: DiagramModel }
export interface ActivityItem { id: string; eventType: string; summary: string; actorName: string; elementId: string | null; createdAt: string }

export const collaborationApi = {
  comments: (id: string) => raw<CommentItem[]>(`/api/v1/diagrams/${id}/comments`),
  comment: (id: string, input: { targetType: CommentItem['targetType']; targetId?: string; parentCommentId?: string; body: string }) =>
    raw<CommentItem>(`/api/v1/diagrams/${id}/comments`, { method: 'POST', body: JSON.stringify(input) }),
  resolveComment: (id: string, commentId: string, resolved: boolean, expectedVersion: number) =>
    raw<CommentItem>(`/api/v1/diagrams/${id}/comments/${commentId}`, { method: 'PATCH', body: JSON.stringify({ resolved, expectedVersion }) }),
  versions: (id: string) => raw<VersionItem[]>(`/api/v1/diagrams/${id}/versions`),
  createVersion: (id: string, label: string) => raw<VersionItem>(`/api/v1/diagrams/${id}/versions`, { method: 'POST', body: JSON.stringify({ label }) }),
  restoreVersion: (id: string, versionId: string, expectedRevision: number) => raw<DiagramModel>(`/api/v1/diagrams/${id}/versions/${versionId}/restore`, {
    method: 'POST', body: JSON.stringify({ expectedRevision }),
  }),
  activity: (id: string) => raw<ActivityItem[]>(`/api/v1/diagrams/${id}/activity?limit=40`),
};

export interface ImageProposal {
  classes: Array<{ name: string; attributes: Array<{ name: string; type: string; primaryKey?: boolean; required?: boolean; unique?: boolean }> }>;
  associations: Array<{ source: string; target: string; sourceCardinality: '0..1' | '1' | '0..*' | '1..*'; targetCardinality: '0..1' | '1' | '0..*' | '1..*'; name?: string }>;
  warnings: string[];
  confidence: number;
}

export async function analyzeDiagramImage(diagramId: string, file: File): Promise<ImageProposal> {
  const body = new FormData(); body.append('file', file);
  return raw<ImageProposal>(`/api/v1/diagrams/${diagramId}/image-preview`, { method: 'POST', body });
}

export interface PresenceParticipant {
  sessionId: string; userId: string; displayName: string; cursor?: { x: number; y: number };
  selection: string[]; activity?: string; lastSeen: string;
}
export type RealtimeEvent =
  | { type: 'OPERATION_APPLIED'; payload: { operation: DiagramOperation; diagram: DiagramModel } }
  | { type: 'MODEL_RESTORED'; payload: DiagramModel }
  | { type: 'PRESENCE'; payload: { participants: PresenceParticipant[] } }
  | { type: 'COMMENT_CREATED' | 'COMMENT_UPDATED' | 'ACTIVITY'; payload: unknown };
export interface DiagramChannel {
  close: () => void;
  presence: (message: { kind: 'JOIN' | 'HEARTBEAT' | 'CURSOR' | 'SELECTION' | 'ACTIVITY' | 'LEAVE'; cursor?: { x: number; y: number }; selection?: string[]; activity?: string }) => void;
}

export function subscribeToDiagram(id: string, handlers: {
  receive: (event: RealtimeEvent) => void; connected: () => void; disconnected: () => void;
}): DiagramChannel {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const client = new Client({
    brokerURL: `${protocol}//${location.host}/ws`, reconnectDelay: 1500,
    heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe(`/topic/diagrams/${id}`, message => handlers.receive(JSON.parse(message.body) as RealtimeEvent));
      handlers.connected();
      client.publish({ destination: `/app/diagrams/${id}/presence`, body: JSON.stringify({ kind: 'JOIN', selection: [] }) });
    },
    onDisconnect: handlers.disconnected,
    onWebSocketClose: handlers.disconnected,
    onStompError: handlers.disconnected,
  });
  client.activate();
  return {
    close: () => {
      if (client.connected) client.publish({ destination: `/app/diagrams/${id}/presence`, body: JSON.stringify({ kind: 'LEAVE' }) });
      void client.deactivate();
    },
    presence: message => {
      if (client.connected) client.publish({ destination: `/app/diagrams/${id}/presence`, body: JSON.stringify(message) });
    },
  };
}

function extractFilename(disposition: string | null, fallback: string): string {
  if (!disposition) return fallback;
  // 1. RFC 5987 / RFC 6266 filename*=UTF-8''filename.ext
  const utf8Match = disposition.match(/filename\*\s*=\s*UTF-8''([^;\r\n]+)/i);
  if (utf8Match && utf8Match[1]) {
    try {
      const decoded = decodeURIComponent(utf8Match[1].trim().replace(/^["']|["']$/g, ''));
      if (decoded) return decoded;
    } catch {
      // ignore URI malformed and fallback
    }
  }
  // 2. Standard filename="filename.ext" or filename=filename.ext
  const standardMatch = disposition.match(/filename\s*=\s*"?([^";\r\n]+)"?/i);
  if (standardMatch && standardMatch[1]) {
    const trimmed = standardMatch[1].trim();
    if (trimmed) return trimmed;
  }
  return fallback;
}

export function triggerBlobDownload(data: Blob | ArrayBuffer, filename: string, mimeType = 'application/zip'): string {
  let safeName = filename;
  if (mimeType === 'application/zip' && !safeName.toLowerCase().endsWith('.zip')) {
    safeName += '.zip';
  } else if (mimeType === 'application/json' && !safeName.toLowerCase().endsWith('.json')) {
    safeName += '.json';
  } else if (mimeType === 'application/xml' && !safeName.toLowerCase().endsWith('.xmi') && !safeName.toLowerCase().endsWith('.xml')) {
    safeName += '.xmi';
  }

  const blob = data instanceof Blob
    ? (data.type === mimeType ? data : new Blob([data], { type: mimeType }))
    : new Blob([data], { type: mimeType });

  const href = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = href;
  link.download = safeName;
  link.style.position = 'fixed';
  link.style.left = '-9999px';
  link.style.top = '-9999px';
  link.style.opacity = '0';
  document.body.appendChild(link);

  // Directly invoke click() to trigger the browser's native download activation behavior
  link.click();

  setTimeout(() => {
    if (link.parentNode) {
      link.parentNode.removeChild(link);
    }
  }, 1000);

  // Keep object URL alive for 60 seconds so browser download manager finishes saving file
  setTimeout(() => {
    URL.revokeObjectURL(href);
  }, 60000);

  return safeName;
}

export async function downloadGeneratedBackend(id: string, versionId?: string): Promise<string> {
  const token = getCsrfToken();
  const headers: Record<string, string> = {};
  if (token) headers['X-XSRF-TOKEN'] = token;
  const response = await fetch(diagramApi.generationUrl(id, versionId), {
    method: 'GET',
    credentials: 'include',
    headers
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; code?: string; elementId?: string; [key: string]: unknown };
    throw new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail ?? 'No se pudo generar el backend', problem);
  }
  const disposition = response.headers.get('content-disposition');
  let filename = extractFilename(disposition, 'generated-api.zip');
  if (!filename.toLowerCase().endsWith('.zip')) filename += '.zip';
  const blob = await response.blob();
  return triggerBlobDownload(blob, filename, 'application/zip');
}

export async function downloadMobileSpec(id: string, versionId?: string): Promise<string> {
  const token = getCsrfToken();
  const headers: Record<string, string> = {};
  if (token) headers['X-XSRF-TOKEN'] = token;
  const response = await fetch(diagramApi.mobileSpecUrl(id, versionId), {
    method: 'GET',
    credentials: 'include',
    headers
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; code?: string; elementId?: string; [key: string]: unknown };
    throw new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail ?? 'No se pudo descargar modeler-mobile-spec.json', problem);
  }
  const disposition = response.headers.get('content-disposition');
  let filename = extractFilename(disposition, 'modeler-mobile-spec.json');
  if (!filename.toLowerCase().endsWith('.json')) filename += '.json';
  const blob = await response.blob();
  return triggerBlobDownload(blob, filename, 'application/json');
}

export async function downloadGeneratedFlutter(id: string, versionId?: string, appTitle?: string): Promise<string> {
  const token = getCsrfToken();
  const headers: Record<string, string> = {};
  if (token) headers['X-XSRF-TOKEN'] = token;
  const url = diagramApi.flutterGenerationUrl(id, versionId);
  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
    headers
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; code?: string; elementId?: string; [key: string]: unknown };
    throw new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail ?? 'No se pudo generar el proyecto Flutter', problem);
  }
  const cleanName = (appTitle || 'flutter-app').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  const disposition = response.headers.get('content-disposition');
  let filename = extractFilename(disposition, `${cleanName || 'app'}-mobile.zip`);
  if (!filename.toLowerCase().endsWith('.zip')) filename += '.zip';
  const blob = await response.blob();
  return triggerBlobDownload(blob, filename, 'application/zip');
}

// =============================================================================
// Local Agent API — communicates with the local agent on 127.0.0.1:9876
// =============================================================================

const AGENT_BASE = 'http://127.0.0.1:9876';

export interface AgentStatus {
  agent: string;
  version: string;
  ready: boolean;
  flutterAvailable: boolean;
  flutterVersion: string | null;
  adbAvailable: boolean;
  adbVersion: string | null;
  androidHome: string | null;
  devices: AgentDevice[];
}

export interface AgentDevice {
  id: string;
  name: string;
  status: string;
}

export interface AgentSseEvent {
  type: string;
  data: Record<string, unknown>;
}

export const agentApi = {
  /** Check if the local agent is running and get SDK status. */
  checkStatus: async (): Promise<AgentStatus> => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3000);
    try {
      const res = await fetch(`${AGENT_BASE}/api/status`, { signal: controller.signal });
      if (!res.ok) throw new Error(`Agent status HTTP ${res.status}`);
      return res.json();
    } catch {
      throw new ApiError(0, 'AGENT_UNAVAILABLE', 'El agente local no está activo. Ejecuta: pnpm agent:start');
    } finally {
      clearTimeout(timeoutId);
    }
  },

  /** List connected devices via the agent. */
  listDevices: async (): Promise<AgentDevice[]> => {
    try {
      const res = await fetch(`${AGENT_BASE}/api/devices`);
      if (!res.ok) throw new Error(`Devices HTTP ${res.status}`);
      const data = await res.json();
      return data.devices || [];
    } catch {
      throw new ApiError(0, 'AGENT_UNAVAILABLE', 'No se pudo obtener la lista de dispositivos del agente local');
    }
  },

  /** Legacy endpoint retained for older servers. New flows use generationJobsApi.agentSpec. */
  fetchAgentSpec: async (diagramId: string, versionId?: string): Promise<unknown> => {
    const token = getCsrfToken();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['X-XSRF-TOKEN'] = token;
    const url = `/api/v1/diagrams/${diagramId}/agent-spec${versionId ? `?versionId=${encodeURIComponent(versionId)}` : ''}`;
    const res = await fetch(url, { method: 'POST', credentials: 'include', headers });
    if (!res.ok) {
      const problem = await res.json().catch(() => ({})) as { detail?: string; code?: string };
      throw new ApiError(res.status, problem.code ?? `HTTP_${res.status}`, problem.detail ?? 'No se pudo obtener la especificación para el agente');
    }
    return res.json();
  },

  /**
   * Send the spec bundle to the local agent for generation.
   * Returns an EventSource-like reader for SSE progress events.
   */
  generate: (agentSpecBundle: unknown, outputDir?: string): { close: () => void; onEvent: (handler: (event: AgentSseEvent) => void) => void } => {
    const body = JSON.stringify({ ...(agentSpecBundle as Record<string, unknown>), outputDir });
    let onEventHandler: (event: AgentSseEvent) => void = () => {};
    const notify = (event: AgentSseEvent) => { onEventHandler(event); };
    const controller = new AbortController();

    // Use fetch with streaming for SSE
    void (async () => {
      try {
        const res = await fetch(`${AGENT_BASE}/api/generate`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Origin': window.location.origin },
          body,
          signal: controller.signal,
        });

        if (!res.ok || !res.body) {
          const errText = await res.text().catch(() => 'Error desconocido');
          notify({ type: 'error', data: { message: errText } });
          return;
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          // Parse SSE events from the buffer
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          let currentEvent = '';
          let currentData = '';

          for (const line of lines) {
            if (line.startsWith('event: ')) {
              currentEvent = line.slice(7).trim();
            } else if (line.startsWith('data: ')) {
              currentData = line.slice(6);
              if (currentEvent && currentData) {
                try {
                  notify({ type: currentEvent, data: JSON.parse(currentData) });
                } catch {
                  notify({ type: currentEvent, data: { raw: currentData } });
                }
                currentEvent = '';
                currentData = '';
              }
            }
          }
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          notify({ type: 'error', data: { message: err instanceof Error ? err.message : 'Error de conexión con el agente' } });
        }
      }
    })();

    return {
      close: () => controller.abort(),
      onEvent: (h) => { onEventHandler = h; },
    };
  },

  /**
   * Execute flutter run or flutter build on the generated project.
   * Returns an SSE reader for progress events.
   */
  run: (projectDir: string, action: 'flutter-run' | 'flutter-build-apk', deviceId?: string, apiBaseUrl?: string): { close: () => void; onEvent: (handler: (event: AgentSseEvent) => void) => void } => {
    const body = JSON.stringify({ projectDir, action, deviceId, apiBaseUrl });
    let onEventHandler: (event: AgentSseEvent) => void = () => {};
    const notify = (event: AgentSseEvent) => { onEventHandler(event); };
    const controller = new AbortController();

    void (async () => {
      try {
        const res = await fetch(`${AGENT_BASE}/api/run`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Origin': window.location.origin },
          body,
          signal: controller.signal,
        });

        if (!res.ok || !res.body) {
          const errText = await res.text().catch(() => 'Error desconocido');
          notify({ type: 'error', data: { message: errText } });
          return;
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          let currentEvent = '';
          let currentData = '';

          for (const line of lines) {
            if (line.startsWith('event: ')) {
              currentEvent = line.slice(7).trim();
            } else if (line.startsWith('data: ')) {
              currentData = line.slice(6);
              if (currentEvent && currentData) {
                try {
                  notify({ type: currentEvent, data: JSON.parse(currentData) });
                } catch {
                  notify({ type: currentEvent, data: { raw: currentData } });
                }
                currentEvent = '';
                currentData = '';
              }
            }
          }
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          notify({ type: 'error', data: { message: err instanceof Error ? err.message : 'Error de conexión con el agente' } });
        }
      }
    })();

    return {
      close: () => controller.abort(),
      onEvent: (h) => { onEventHandler = h; },
    };
  },
};
