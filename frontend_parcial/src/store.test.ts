import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramModel } from './domain';
import { useDiagramStore } from './store';

const base = (): DiagramModel => ({
  id: crypto.randomUUID(), name: 'Prueba', revision: 0,
  classes: [], enumerations: [], associations: [], generalizations: [], packages: [],
});

describe('diagram store', () => {
  beforeEach(() => {
    const diagram = base();
    useDiagramStore.setState({ diagram, serverDiagram: diagram, confirmedRevision: 0, diagramId: undefined, selectedIds: [],
      history: [], redoHistory: [], pendingOperations: [], conflicts: [], participants: [], eventSequence: 0,
      syncState: 'offline', lastError: undefined });
  });

  it('creates and edits classes and attributes without mutating previous snapshots', () => {
    const original = useDiagramStore.getState().diagram;
    const created = useDiagramStore.getState().addClass('Producto');
    useDiagramStore.getState().addAttribute(created.id, {
      name: 'id', type: 'UUID', primaryKey: true, required: true, unique: true,
    });
    const current = useDiagramStore.getState().diagram;
    expect(original.classes).toHaveLength(0);
    expect(current.classes[0].attributes[0]).toMatchObject({ name: 'id', primaryKey: true, version: 1 });
    expect(current.revision).toBe(2);
  });

  it('moves a multi-selection as one atomic local revision', () => {
    const first = useDiagramStore.getState().addClass('Primera');
    const second = useDiagramStore.getState().addClass('Segunda');
    const revision = useDiagramStore.getState().diagram.revision;
    useDiagramStore.getState().selectElements([first.id, second.id]);
    useDiagramStore.getState().moveSelected({ x: 10, y: -5 });
    const current = useDiagramStore.getState().diagram;
    expect(current.revision).toBe(revision + 1);
    expect(current.classes.find(item => item.id === first.id)?.position.x).toBe(first.position.x + 10);
    expect(current.classes.find(item => item.id === second.id)?.position.y).toBe(second.position.y - 5);
  });

  it('does not update the store when React Flow reports the same selection again', () => {
    const item = useDiagramStore.getState().addClass('Producto');
    useDiagramStore.getState().selectElements([item.id]);
    const selectionBefore = useDiagramStore.getState().selectedIds;

    useDiagramStore.getState().selectElements([item.id]);

    expect(useDiagramStore.getState().selectedIds).toBe(selectionBefore);
  });

  it('deletes selected classes and their dangling links', () => {
    const customer = useDiagramStore.getState().addClass('Cliente');
    const order = useDiagramStore.getState().addClass('Pedido');
    useDiagramStore.getState().addAssociation({
      sourceId: customer.id, targetId: order.id, sourceCardinality: '1', targetCardinality: '0..*',
      owningSide: 'SOURCE', sourceRole: '', targetRole: '',
    });
    useDiagramStore.getState().selectElements([customer.id]);
    useDiagramStore.getState().deleteSelected();
    expect(useDiagramStore.getState().diagram.classes.map(item => item.name)).toEqual(['Pedido']);
    expect(useDiagramStore.getState().diagram.associations).toHaveLength(0);
  });

  it('supports enumerations and inheritance', () => {
    const person = useDiagramStore.getState().addClass('Persona');
    const student = useDiagramStore.getState().addClass('Estudiante');
    const status = useDiagramStore.getState().addEnumeration('Estado');
    useDiagramStore.getState().updateEnumeration({
      ...status, values: [{ id: crypto.randomUUID(), name: 'ACTIVO', version: 1 }],
    });
    const enumerationOperation = useDiagramStore.getState().history.at(-1)?.redo;
    expect(enumerationOperation?.type).toBe('BATCH');
    expect((enumerationOperation?.payload as { operations: Array<{ type: string }> }).operations[0].type).toBe('ENUMERATION_VALUE_CREATED');
    useDiagramStore.getState().addGeneralization(person.id, student.id);
    expect(useDiagramStore.getState().diagram.enumerations[0].values[0].name).toBe('ACTIVO');
    expect(useDiagramStore.getState().diagram.generalizations[0]).toMatchObject({ parentId: person.id, childId: student.id });
  });

  it('undoes and redoes with compensating domain operations', () => {
    useDiagramStore.getState().addClass('Producto');
    expect(useDiagramStore.getState().diagram.classes).toHaveLength(1);
    useDiagramStore.getState().undo();
    expect(useDiagramStore.getState().diagram.classes).toHaveLength(0);
    expect(useDiagramStore.getState().redoHistory).toHaveLength(1);
    useDiagramStore.getState().redo();
    expect(useDiagramStore.getState().diagram.classes[0].name).toBe('Producto');
  });

  it('records an applied assistant operation in undo history', () => {
    const before = useDiagramStore.getState().diagram;
    const item = { id: crypto.randomUUID(), kind: 'class' as const, name: 'Producto', attributes: [], position: { x: 10, y: 20 }, version: 1 };
    const operation = { operationId: crypto.randomUUID(), baseRevision: 0, type: 'CLASS_CREATED' as const, payload: item };
    useDiagramStore.getState().acceptAssistant(operation, { ...before, revision: 1, classes: [item] });

    expect(useDiagramStore.getState().diagram.classes[0].name).toBe('Producto');
    expect(useDiagramStore.getState().history.at(-1)?.redo).toEqual(operation);
    expect(useDiagramStore.getState().history.at(-1)?.undo.type).toBe('BATCH');
  });

  it('applies an XMI preview as one batch and undoes the complete import', () => {
    const classId = crypto.randomUUID(); const enumerationId = crypto.randomUUID(); const packageId = crypto.randomUUID();
    useDiagramStore.getState().replaceFromImport({
      ...base(), name: 'Importado',
      classes: [{ id: classId, kind: 'class', name: 'Pedido', position: { x: 10, y: 20 }, version: 1,
        attributes: [{ id: crypto.randomUUID(), name: 'estado', type: 'Estado', primaryKey: false, required: true, unique: false, version: 1 }] }],
      enumerations: [{ id: enumerationId, kind: 'enumeration', name: 'Estado', position: { x: 300, y: 20 }, version: 1,
        values: [{ id: crypto.randomUUID(), name: 'NUEVO', version: 1 }] }],
      packages: [{ id: packageId, name: 'Ventas', memberIds: [classId, enumerationId], version: 1 }],
    });

    expect(useDiagramStore.getState().history).toHaveLength(1);
    expect(useDiagramStore.getState().history[0].redo.type).toBe('BATCH');
    expect(useDiagramStore.getState().diagram.classes[0].name).toBe('Pedido');
    expect(useDiagramStore.getState().diagram.packages[0].memberIds).toHaveLength(2);

    useDiagramStore.getState().undo();

    expect(useDiagramStore.getState().diagram.classes).toHaveLength(0);
    expect(useDiagramStore.getState().diagram.enumerations).toHaveLength(0);
    expect(useDiagramStore.getState().diagram.packages).toHaveLength(0);
  });

  it('undoes only the local action after an unrelated remote change arrives', () => {
    const local = useDiagramStore.getState().addClass('Local');
    const afterLocal = useDiagramStore.getState().diagram;
    const remote = {
      id: crypto.randomUUID(), kind: 'class' as const, name: 'Remota', attributes: [],
      position: { x: 400, y: 100 }, version: 1,
    };
    useDiagramStore.getState().acceptAuthoritative({
      ...afterLocal, revision: afterLocal.revision + 1, classes: [...afterLocal.classes, remote],
    });

    useDiagramStore.getState().undo();

    expect(useDiagramStore.getState().diagram.classes.map(item => item.name)).toEqual(['Remota']);
    expect(useDiagramStore.getState().diagram.classes.some(item => item.id === local.id)).toBe(false);
  });
});
