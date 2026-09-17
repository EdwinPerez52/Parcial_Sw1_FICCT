import { describe, expect, it } from 'vitest';
import { DiagramModel, DiagramOperation } from './domain';
import { applyDiagramOperation } from './operationReducer';

describe('operation reconciliation', () => {
  it('applies remote operations in revision order without replacing unrelated local content', () => {
    const model: DiagramModel = { id: crypto.randomUUID(), name: 'M', revision: 4, classes: [], enumerations: [], associations: [], generalizations: [] };
    const first = { operationId: crypto.randomUUID(), baseRevision: 4, type: 'CLASS_CREATED', payload: {
      id: crypto.randomUUID(), kind: 'class', name: 'Cliente', attributes: [], position: { x: 0, y: 0 }, version: 1,
    } } satisfies DiagramOperation;
    const second = { operationId: crypto.randomUUID(), baseRevision: 5, type: 'CLASS_CREATED', payload: {
      id: crypto.randomUUID(), kind: 'class', name: 'Pedido', attributes: [], position: { x: 10, y: 10 }, version: 1,
    } } satisfies DiagramOperation;
    const result = applyDiagramOperation(applyDiagramOperation(model, first, 5), second, 6);
    expect(result.revision).toBe(6); expect(result.classes.map(value => value.name)).toEqual(['Cliente', 'Pedido']);
  });
});
