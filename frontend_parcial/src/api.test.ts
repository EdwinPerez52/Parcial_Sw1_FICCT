// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { assistantApi, authApi, diagramApi, previewXmi } from './api';

describe('cliente autenticado', () => {
  beforeEach(() => vi.restoreAllMocks());
  it('envía CSRF en mutaciones sin guardar tokens en storage', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true, email: 'ana@example.com', fullName: 'Ana', verified: true, platformAdmin: false, csrfToken: 'csrf-value' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    await authApi.me(); await diagramApi.create('Ventas');
    expect(new Headers(fetchMock.mock.calls[1][1]!.headers).get('X-XSRF-TOKEN')).toBe('csrf-value'); expect(localStorage.length).toBe(0);
  });
  it('traduce respuestas de autorización a errores claros', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ code: 'FORBIDDEN', detail: 'Solo el propietario.' }), { status: 403, headers: { 'Content-Type': 'application/json' } }));
    await expect(diagramApi.revokeShare('id')).rejects.toEqual(expect.objectContaining({ status: 403, code: 'FORBIDDEN', message: 'Solo el propietario.' }));
  });
  it('envía el XMI como multipart al endpoint autorizado del diagrama', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      diagram: { id: 'model', name: 'M', revision: 0, classes: [], enumerations: [], associations: [], generalizations: [], packages: [] }, warnings: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const file = new File(['<xmi:XMI/>'], 'model.xmi', { type: 'application/xml' });

    await previewXmi('diagram-id', file);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/diagrams/diagram-id/xmi/preview');
    expect(fetchMock.mock.calls[0][1]?.body).toBeInstanceOf(FormData);
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has('Content-Type')).toBe(false);
  });
  it('interpreta y confirma propuestas sin enviar el diagrama desde el cliente', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ proposalId: 'p1', provider: 'local-deterministic', requiresConfirmation: true, operation: {}, previewDiagram: {}, summary: 'CLASS_DELETED' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ operation: {}, diagram: {}, provider: 'local-deterministic' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await assistantApi.interpret('diagram-id', 'elimina la clase Persona');
    await assistantApi.apply('diagram-id', 'p1', true);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/diagrams/diagram-id/assistant/proposals');
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ instruction: 'elimina la clase Persona' });
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toEqual({ confirmed: true });
  });

  it('analyzeDiagramImage sends image as multipart and handles 500 error', async () => {
    const { analyzeDiagramImage } = await import('./api');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ classes: [], associations: [], warnings: [], confidence: 1 }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'INTERNAL_ERROR', detail: 'Error HTTP 500' }), { status: 500, headers: { 'Content-Type': 'application/json' } }));
    
    const image = new File(['fake-image'], 'diagram.jpg', { type: 'image/jpeg' });
    const response = await analyzeDiagramImage('diagram-1', image);
    
    expect(response).toEqual({ classes: [], associations: [], warnings: [], confidence: 1 });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/diagrams/diagram-1/image-preview');
    expect(fetchMock.mock.calls[0][1]?.body).toBeInstanceOf(FormData);
    expect((fetchMock.mock.calls[0][1]?.body as FormData).get('file')).toBe(image);
    
    await expect(analyzeDiagramImage('diagram-1', image)).rejects.toEqual(expect.objectContaining({ status: 500, message: 'Error HTTP 500' }));
  });

  it('assistantApi.transcribe sends audio as multipart with correct extension based on mime', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ text: 'texto de prueba' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    
    const audio = new Blob(['fake-audio'], { type: 'audio/webm' });
    const response = await assistantApi.transcribe('diagram-1', audio);
    
    expect(response).toBe('texto de prueba');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/diagrams/diagram-1/assistant/transcriptions');
    expect(fetchMock.mock.calls[0][1]?.body).toBeInstanceOf(FormData);
    
    const formData = fetchMock.mock.calls[0][1]?.body as FormData;
    const file = formData.get('file') as File;
    expect(file.name).toBe('voz.webm');
  });

  it('authApi.register envía la solicitud de creación de cuenta sin requerir invitación', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Cuenta creada y guardada exitosamente. Ya puedes iniciar sesión.' }), { status: 201, headers: { 'Content-Type': 'application/json' } }));

    const res = await authApi.register('Juan Pérez', 'juan@example.com', 'Password123', 'Password123');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/register');
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      fullName: 'Juan Pérez',
      email: 'juan@example.com',
      password: 'Password123',
      passwordConfirmation: 'Password123',
      invitationToken: null,
    });
    expect(res.message).toBe('Cuenta creada y guardada exitosamente. Ya puedes iniciar sesión.');
  });
});
