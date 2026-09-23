import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramModel } from './domain';
import { useDiagramStore } from './store';
import { buildImageImport } from './imageImport';

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

  it('imports a photograph in one BATCH and undoes the whole import once', () => {
    const before = useDiagramStore.getState().diagram;
    const target = buildImageImport(before, { classes: [
      { name: 'Product', attributes: [{ name: 'id', type: 'UUID', primaryKey: true }] },
      { name: 'Order', attributes: [] },
    ], associations: [{ source: 'Product', target: 'Order', sourceCardinality: '1', targetCardinality: '0..*' }], warnings: [], confidence: 0.9 });
    expect(useDiagramStore.getState().diagram).toBe(before);
    useDiagramStore.getState().replaceFromImport(target);
    const state = useDiagramStore.getState();
    expect(state.diagram.revision).toBe(1);
    expect(state.history.at(-1)?.redo.type).toBe('BATCH');
    expect(state.diagram.associations).toHaveLength(1);
    state.undo();
    expect(useDiagramStore.getState().diagram.classes).toHaveLength(0);
    expect(useDiagramStore.getState().diagram.associations).toHaveLength(0);
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

  it('numbers new classes even after an earlier class has been renamed', () => {
    const first = useDiagramStore.getState().addClass();
    expect(first.name).toBe('NuevaClase1');
    expect(useDiagramStore.getState().addClass().name).toBe('NuevaClase2');
    useDiagramStore.getState().renameClass(first.id, 'Producto');
    expect(useDiagramStore.getState().addClass().name).toBe('NuevaClase3');
  });

  it('creates a many-to-many join class with two primary keys and two relations', () => {
    const source = useDiagramStore.getState().addClass('Venta');
    const target = useDiagramStore.getState().addClass('Producto');
    useDiagramStore.getState().addAttribute(source.id, {
      name: 'numero', type: 'Long', primaryKey: true, required: true, unique: true,
    });
    useDiagramStore.getState().addAttribute(target.id, {
      name: 'codigo', type: 'UUID', primaryKey: true, required: true, unique: true,
    });
    const join = useDiagramStore.getState().addJoinClass(source.id, target.id);
    expect(join.name).toBe('Venta_Producto');
    expect(join.attributes.map(item => [item.name, item.primaryKey])).toEqual([
      ['Venta_numero', true], ['Producto_codigo', true],
    ]);
    expect(join.attributes.map(item => item.type)).toEqual(['Long', 'UUID']);
    expect(useDiagramStore.getState().diagram.associations.filter(item => item.targetId === join.id)).toHaveLength(2);
    expect(useDiagramStore.getState().history.at(-1)?.redo.type).toBe('BATCH');
    const old = useDiagramStore.getState().addAssociation({ sourceId: source.id, targetId: target.id,
      sourceCardinality: '1', targetCardinality: '0..*', owningSide: 'SOURCE' });
    const second = useDiagramStore.getState().addJoinClass(source.id, target.id, undefined, old.id);
    expect(useDiagramStore.getState().diagram.associations.some(item => item.id === old.id)).toBe(false);
    expect(useDiagramStore.getState().diagram.associations.filter(item => item.targetId === second.id)).toHaveLength(2);
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

  it('selects every movable node so all tables can be moved or deleted together', () => {
    const first = useDiagramStore.getState().addClass('Primera');
    const second = useDiagramStore.getState().addClass('Segunda');

    useDiagramStore.getState().selectElements([first.id, second.id]);
    useDiagramStore.getState().moveSelected({ x: 25, y: 10 });

    const state = useDiagramStore.getState();
    expect(state.diagram.classes.map(item => item.position.x)).toEqual([first.position.x + 25, second.position.x + 25]);
    state.deleteSelected();
    expect(useDiagramStore.getState().diagram.classes).toHaveLength(0);
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
      owningSide: 'SOURCE', sourceRole: '', targetRole: '', sourceHandle: 'bottom-75', targetHandle: 'top-25',
    });
    expect(useDiagramStore.getState().diagram.associations[0]).toMatchObject({
      sourceHandle: 'bottom-75', targetHandle: 'top-25',
    });
    useDiagramStore.getState().selectElements([customer.id]);
    useDiagramStore.getState().deleteSelected();
    expect(useDiagramStore.getState().diagram.classes.map(item => item.name)).toEqual(['Pedido']);
    expect(useDiagramStore.getState().diagram.associations).toHaveLength(0);
  });

  it('supports inheritance', () => {
    const person = useDiagramStore.getState().addClass('Persona');
    const student = useDiagramStore.getState().addClass('Estudiante');
    useDiagramStore.getState().addGeneralization(person.id, student.id);
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
    const classId = crypto.randomUUID(); const class2Id = crypto.randomUUID(); const packageId = crypto.randomUUID();
    useDiagramStore.getState().replaceFromImport({
      ...base(), name: 'Importado',
      classes: [
        { id: classId, kind: 'class', name: 'Pedido', position: { x: 10, y: 20 }, version: 1,
          attributes: [{ id: crypto.randomUUID(), name: 'total', type: 'Decimal', primaryKey: false, required: true, unique: false, version: 1 }] },
        { id: class2Id, kind: 'class', name: 'Detalle', position: { x: 300, y: 20 }, version: 1, attributes: [] },
      ],
      packages: [{ id: packageId, name: 'Ventas', memberIds: [classId, class2Id], version: 1 }],
    });

    expect(useDiagramStore.getState().history).toHaveLength(1);
    expect(useDiagramStore.getState().history[0].redo.type).toBe('BATCH');
    expect(useDiagramStore.getState().diagram.classes[0].name).toBe('Pedido');
    expect(useDiagramStore.getState().diagram.packages[0].memberIds).toHaveLength(2);

    useDiagramStore.getState().undo();

    expect(useDiagramStore.getState().diagram.classes).toHaveLength(0);
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
