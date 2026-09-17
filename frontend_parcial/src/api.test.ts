// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi, diagramApi } from './api';

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
});
