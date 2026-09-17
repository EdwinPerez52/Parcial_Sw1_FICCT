// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PropertyPanel } from './PropertyPanel';
import { useDiagramStore } from './store';

describe('PropertyPanel', () => {
  afterEach(cleanup);
  beforeEach(() => {
    useDiagramStore.setState({
      diagram: {
        id: crypto.randomUUID(), name: 'Ventas', revision: 0,
        classes: [{
          id: '11111111-1111-4111-8111-111111111111', kind: 'class', name: 'Producto',
          attributes: [], position: { x: 0, y: 0 }, version: 1,
        }],
        enumerations: [], associations: [], generalizations: [],
      },
      diagramId: undefined, selectedIds: ['11111111-1111-4111-8111-111111111111'],
      history: [], syncState: 'offline', lastError: undefined,
    });
  });

  it('creates an attribute with its UML constraints', () => {
    render(<PropertyPanel report={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Nombre del atributo'), { target: { value: 'codigo' } });
    fireEvent.change(screen.getByLabelText('Tipo del atributo'), { target: { value: 'Long' } });
    fireEvent.click(screen.getByLabelText('PK'));
    fireEvent.click(screen.getByLabelText('Requerido'));
    fireEvent.click(screen.getByRole('button', { name: /Agregar atributo/i }));
    expect(useDiagramStore.getState().diagram.classes[0].attributes[0]).toMatchObject({
      name: 'codigo', type: 'Long', primaryKey: true, required: true,
    });
  });

  it('shows multi-selection controls', () => {
    useDiagramStore.setState({ selectedIds: [
      '11111111-1111-4111-8111-111111111111',
      '22222222-2222-4222-8222-222222222222',
    ] });
    render(<PropertyPanel report={vi.fn()} />);
    expect(screen.getByText('2 elementos seleccionados')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Eliminar selección/i })).toBeTruthy();
  });
});
