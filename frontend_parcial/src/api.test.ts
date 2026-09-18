// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi, diagramApi, previewXmi } from './api';

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
});
