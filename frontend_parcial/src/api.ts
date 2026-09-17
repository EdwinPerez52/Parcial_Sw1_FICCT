import { Client } from '@stomp/stompjs';
import { DiagramModel, DiagramOperation } from './domain';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details: Record<string, unknown> = {}) { super(message); }
}

let csrfToken = '';

async function raw<T>(url: string, options?: RequestInit): Promise<T> {
  const headers = new Headers(options?.headers);
  if (!(options?.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (csrfToken && options?.method && !['GET', 'HEAD'].includes(options.method)) headers.set('X-XSRF-TOKEN', csrfToken);
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
export interface InvitationInfo { valid: boolean; email: string | null; diagramId: string | null; registrationAllowed: boolean }

export const authApi = {
  me: async () => { const me = await raw<CurrentUser>('/api/v1/auth/me'); csrfToken = me.csrfToken; return me; },
  login: (email: string, password: string) => raw<CurrentUser>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  register: (fullName: string, email: string, password: string, passwordConfirmation: string, invitationToken: string) =>
    raw<{ message: string }>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify({ fullName, email, password, passwordConfirmation, invitationToken }) }),
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
  join: (token: string) => raw<{ diagramId: string }>(`/api/v1/join/${encodeURIComponent(token)}`, { method: 'POST' }),
  generationUrl: (id: string) => `/api/v1/diagrams/${id}/generation?groupId=com.generated&artifactId=generated-api`,
};

export interface RemoteOperation {
  id: string; diagramId: string; baseRevision: number; resultRevision: number; type: DiagramOperation['type'];
  payloadJson: string; authorName: string; createdAt: string;
}

export interface CommentItem {
  id: string; diagramId: string; targetType: 'DIAGRAM' | 'CLASS' | 'ATTRIBUTE' | 'ASSOCIATION' | 'ENUMERATION' | 'GENERALIZATION';
  targetId: string | null; parentCommentId: string | null; body: string; authorName: string; resolved: boolean;
  createdAt: string; updatedAt: string; resolvedAt: string | null;
}
export interface VersionItem { id: string; sourceRevision: number; label: string; authorName: string; createdAt: string; snapshot: DiagramModel }
export interface ActivityItem { id: string; eventType: string; summary: string; actorName: string; elementId: string | null; createdAt: string }

export const collaborationApi = {
  comments: (id: string) => raw<CommentItem[]>(`/api/v1/diagrams/${id}/comments`),
  comment: (id: string, input: { targetType: CommentItem['targetType']; targetId?: string; parentCommentId?: string; body: string }) =>
    raw<CommentItem>(`/api/v1/diagrams/${id}/comments`, { method: 'POST', body: JSON.stringify(input) }),
  resolveComment: (id: string, commentId: string, resolved: boolean) =>
    raw<CommentItem>(`/api/v1/diagrams/${id}/comments/${commentId}`, { method: 'PATCH', body: JSON.stringify({ resolved }) }),
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
}

export async function analyzeDiagramImage(file: File): Promise<ImageProposal> {
  const body = new FormData(); body.append('file', file);
  return raw<ImageProposal>('/api/v1/ai/image-preview', { method: 'POST', body });
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

export async function downloadGeneratedBackend(id: string): Promise<void> {
  const response = await fetch(diagramApi.generationUrl(id), { method: 'POST', credentials: 'include', headers: csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {} });
  if (!response.ok) throw new ApiError(response.status, `HTTP_${response.status}`, (await response.json().catch(() => ({}))).detail ?? 'No se pudo generar el backend');
  const href = URL.createObjectURL(await response.blob());
  const link = document.createElement('a'); link.href = href; link.download = 'generated-api.zip'; link.click(); URL.revokeObjectURL(href);
}
