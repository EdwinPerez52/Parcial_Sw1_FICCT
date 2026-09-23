import { FormEvent, useEffect, useState } from 'react';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import { Attribute, ConnectionPoint, RelationType, cardinalities, connectionPoints, createId, scalarTypes } from './domain';
import { useDiagramStore } from './store';

const blankAttribute = (): Omit<Attribute, 'id' | 'version'> => ({
  name: 'nuevoAtributo', type: 'String', primaryKey: false, required: false, unique: false,
});

export function PropertyPanel({ report }: { report: (message: string) => void }) {
  const diagram = useDiagramStore(s => s.diagram);
  const selectedIds = useDiagramStore(s => s.selectedIds);
  const addAttribute = useDiagramStore(s => s.addAttribute);
  const updateAttribute = useDiagramStore(s => s.updateAttribute);
  const reorderAttribute = useDiagramStore(s => s.reorderAttribute);
  const deleteAttribute = useDiagramStore(s => s.deleteAttribute);
  const renameClass = useDiagramStore(s => s.renameClass);
  const deleteClass = useDiagramStore(s => s.deleteClass);
  const addAssociation = useDiagramStore(s => s.addAssociation);
  const addJoinClass = useDiagramStore(s => s.addJoinClass);
  const updateAssociation = useDiagramStore(s => s.updateAssociation);
  const deleteAssociation = useDiagramStore(s => s.deleteAssociation);
  const addGeneralization = useDiagramStore(s => s.addGeneralization);
  const deleteGeneralization = useDiagramStore(s => s.deleteGeneralization);
  const deleteSelected = useDiagramStore(s => s.deleteSelected);

  const selectedId = selectedIds.length === 1 ? selectedIds[0] : undefined;
  const selectedClass = diagram.classes.find(item => item.id === selectedId);
  const selectedAssociation = diagram.associations.find(item => item.id === selectedId);
  const selectedGeneralization = diagram.generalizations.find(item => item.id === selectedId);
  const [className, setClassName] = useState('');
  const [attribute, setAttribute] = useState(blankAttribute);
  const [sourceId, setSourceId] = useState('');
  const [targetId, setTargetId] = useState('');
  const [relationName, setRelationName] = useState('');
  const [relationType, setRelationType] = useState<RelationType>('ASSOCIATION');
  const [sourceCardinality, setSourceCardinality] = useState<'0..1' | '1' | '0..*' | '1..*'>('1');
  const [targetCardinality, setTargetCardinality] = useState<'0..1' | '1' | '0..*' | '1..*'>('0..*');
  const [owningSide, setOwningSide] = useState<'SOURCE' | 'TARGET'>('SOURCE');
  const [sourceHandle, setSourceHandle] = useState<ConnectionPoint>('right-50');
  const [targetHandle, setTargetHandle] = useState<ConnectionPoint>('left-50');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    setClassName(selectedClass?.name ?? '');
    if (selectedAssociation) {
      setSourceId(selectedAssociation.sourceId); setTargetId(selectedAssociation.targetId);
      setRelationName(selectedAssociation.name ?? ''); setRelationType(selectedAssociation.relationType ?? 'ASSOCIATION'); setSourceCardinality(selectedAssociation.sourceCardinality);
      setTargetCardinality(selectedAssociation.targetCardinality); setOwningSide(selectedAssociation.owningSide);
      setSourceHandle(selectedAssociation.sourceHandle ?? 'right-50'); setTargetHandle(selectedAssociation.targetHandle ?? 'left-50');
    }
  }, [selectedClass, selectedAssociation]);

  const safe = (action: () => void, success: string) => {
    try { action(); setNotice(success); report(success); }
    catch (cause) {
      const message = cause instanceof Error ? cause.message : 'La operación no es válida';
      setNotice(message); report(message);
    }
  };

  const submitAttribute = (event: FormEvent) => {
    event.preventDefault();
    if (!selectedClass) return;
    safe(() => {
      addAttribute(selectedClass.id, attribute);
      setAttribute(blankAttribute());
    }, 'Atributo creado.');
  };

  const saveAssociation = () => {
    if (!selectedAssociation) return;
    if (sourceCardinality.endsWith('*') && targetCardinality.endsWith('*')) {
      safe(() => { addJoinClass(sourceId, targetId, relationName || undefined, selectedAssociation.id); },
        'Relación muchos a muchos reemplazada por una clase intermedia.');
      return;
    }
    safe(() => updateAssociation({
      ...selectedAssociation, sourceId, targetId, name: relationName || undefined,
      sourceCardinality, targetCardinality, owningSide, relationType, sourceHandle, targetHandle,
    }), 'Asociación actualizada.');
  };

  const createAssociation = (event: FormEvent) => {
    event.preventDefault();
    if (!sourceId || !targetId) { setNotice('Selecciona las dos clases de la asociación.'); return; }
    if (sourceCardinality.endsWith('*') && targetCardinality.endsWith('*')) {
      safe(() => { addJoinClass(sourceId, targetId, relationName || undefined); }, 'Clase intermedia creada con claves primarias y foráneas.');
      return;
    }
    safe(() => addAssociation({
      sourceId, targetId, name: relationName || undefined, sourceCardinality, targetCardinality,
      sourceRole: '', targetRole: '', owningSide, relationType, sourceHandle, targetHandle,
    }), 'Asociación creada.');
  };

  const createGeneralization = () => {
    if (!sourceId || !targetId) { setNotice('Selecciona padre e hija para la herencia.'); return; }
    safe(() => addGeneralization(sourceId, targetId), 'Herencia creada.');
  };

  return (
    <div className="properties">
      <div className="properties-heading">
        <strong>Propiedades UML</strong>
        <small>{selectedIds.length > 1 ? `${selectedIds.length} elementos seleccionados` : 'Edición semántica'}</small>
      </div>
      {notice && <div className="inline-validation" role="status">{notice}</div>}

      {selectedIds.length > 1 && <section>
        <p>Usa las flechas del teclado para mover el grupo o Supr para eliminarlo.</p>
        <button className="danger" onClick={() => {
          if (window.confirm(`¿Eliminar ${selectedIds.length} elementos?`)) safe(deleteSelected, 'Selección eliminada.');
        }}><Trash2 size={14} /> Eliminar selección</button>
      </section>}

      {selectedClass && <section>
        <h3>Clase</h3>
        <label>Nombre<input value={className} onChange={event => setClassName(event.target.value)} /></label>
        <button onClick={() => safe(() => renameClass(selectedClass.id, className), 'Clase actualizada.')}>Guardar nombre</button>
        <h3>Atributos</h3>
        {selectedClass.attributes.map((item, index) => <AttributeEditor key={item.id} value={item}
          onSave={value => safe(() => updateAttribute(selectedClass.id, value), 'Atributo actualizado.')}
          onDelete={() => {
            if (window.confirm(`¿Eliminar el atributo ${item.name}?`)) safe(() => deleteAttribute(selectedClass.id, item.id), 'Atributo eliminado.');
          }}
          onMove={delta => reorderAttribute(selectedClass.id, item.id, index + delta)} />)}
        <form className="compact-form" onSubmit={submitAttribute}>
          <input aria-label="Nombre del atributo" value={attribute.name} onChange={event => setAttribute({ ...attribute, name: event.target.value })} />
          <select aria-label="Tipo del atributo" value={attribute.type} onChange={event => setAttribute({ ...attribute, type: event.target.value })}>
            {scalarTypes.map(type => <option key={type}>{type}</option>)}
          </select>
          <div className="check-row">
            <label><input type="checkbox" checked={attribute.primaryKey} onChange={event => setAttribute({ ...attribute, primaryKey: event.target.checked })} /> PK</label>
            <label><input type="checkbox" checked={attribute.required} onChange={event => setAttribute({ ...attribute, required: event.target.checked })} /> Requerido</label>
            <label><input type="checkbox" checked={attribute.unique} onChange={event => setAttribute({ ...attribute, unique: event.target.checked })} /> Único</label>
          </div>
          <button type="submit"><Plus size={14} /> Agregar atributo</button>
        </form>
        <button className="danger" onClick={() => {
          if (window.confirm(`¿Eliminar la clase ${selectedClass.name} y sus relaciones?`)) deleteClass(selectedClass.id);
        }}><Trash2 size={14} /> Eliminar clase</button>
      </section>}

      {selectedAssociation && <section>
        <h3>Asociación</h3>
        <RelationFields classes={diagram.classes} values={{ sourceId, targetId, relationName, sourceCardinality, targetCardinality, owningSide, relationType, sourceHandle, targetHandle }}
          setters={{ setSourceId, setTargetId, setRelationName, setSourceCardinality, setTargetCardinality, setOwningSide, setRelationType, setSourceHandle, setTargetHandle }} />
        <label>Rol origen<input value={selectedAssociation.sourceRole ?? ''} onChange={event => updateAssociation({ ...selectedAssociation, sourceRole: event.target.value })} /></label>
        <label>Rol destino<input value={selectedAssociation.targetRole ?? ''} onChange={event => updateAssociation({ ...selectedAssociation, targetRole: event.target.value })} /></label>
        <button onClick={saveAssociation}>Guardar asociación</button>
        <button className="danger" onClick={() => {
          if (window.confirm('¿Eliminar esta asociación?')) deleteAssociation(selectedAssociation.id);
        }}><Trash2 size={14} /> Eliminar</button>
      </section>}

      {selectedGeneralization && <section>
        <h3>Herencia</h3>
        <p>{diagram.classes.find(item => item.id === selectedGeneralization.childId)?.name} hereda de {diagram.classes.find(item => item.id === selectedGeneralization.parentId)?.name}.</p>
        <button className="danger" onClick={() => {
          if (window.confirm('¿Eliminar esta herencia?')) deleteGeneralization(selectedGeneralization.id);
        }}><Trash2 size={14} /> Eliminar herencia</button>
      </section>}

      {!selectedClass && !selectedAssociation && !selectedGeneralization && selectedIds.length < 2 && <section>
        <h3>Crear relación</h3>
        <form className="compact-form" onSubmit={createAssociation}>
          <RelationFields classes={diagram.classes} values={{ sourceId, targetId, relationName, sourceCardinality, targetCardinality, owningSide, relationType, sourceHandle, targetHandle }}
            setters={{ setSourceId, setTargetId, setRelationName, setSourceCardinality, setTargetCardinality, setOwningSide, setRelationType, setSourceHandle, setTargetHandle }} />
          <button type="submit">{sourceCardinality.endsWith('*') && targetCardinality.endsWith('*') ? 'Crear clase intermedia' : 'Crear relación'}</button>
          <button type="button" onClick={createGeneralization}>Crear herencia (origen padre)</button>
        </form>
        <p className="hint">Selecciona un nodo o enlace para editarlo. Ctrl/Cmd permite selección múltiple.</p>
      </section>}
    </div>
  );
}

function AttributeEditor({ value, onSave, onDelete, onMove }: {
  value: Attribute; onSave: (value: Attribute) => void; onDelete: () => void; onMove: (delta: number) => void;
}) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  return <div className="attribute-editor">
    <input value={draft.name} aria-label={`Nombre de ${value.name}`} onChange={event => setDraft({ ...draft, name: event.target.value })} />
    <select value={draft.type} onChange={event => setDraft({ ...draft, type: event.target.value })}>
      {scalarTypes.map(type => <option key={type}>{type}</option>)}
    </select>
    <div className="check-row">
      <label><input type="checkbox" checked={draft.primaryKey} onChange={event => setDraft({ ...draft, primaryKey: event.target.checked })} />PK</label>
      <label><input type="checkbox" checked={draft.required} onChange={event => setDraft({ ...draft, required: event.target.checked })} />Req.</label>
      <label><input type="checkbox" checked={draft.unique} onChange={event => setDraft({ ...draft, unique: event.target.checked })} />Único</label>
    </div>
    <div className="row-actions">
      <button type="button" title="Subir" onClick={() => onMove(-1)}><ArrowUp size={13} /></button>
      <button type="button" title="Bajar" onClick={() => onMove(1)}><ArrowDown size={13} /></button>
      <button type="button" onClick={() => onSave(draft)}>Guardar</button>
      <button type="button" className="icon-danger" title="Eliminar atributo" onClick={onDelete}><Trash2 size={13} /></button>
    </div>
  </div>;
}

type RelationValues = {
  sourceId: string; targetId: string; relationName: string;
  sourceCardinality: '0..1' | '1' | '0..*' | '1..*';
  targetCardinality: '0..1' | '1' | '0..*' | '1..*';
  owningSide: 'SOURCE' | 'TARGET';
  relationType: RelationType;
  sourceHandle: ConnectionPoint; targetHandle: ConnectionPoint;
};

function RelationFields({ classes, values, setters }: {
  classes: Array<{ id: string; name: string }>; values: RelationValues;
  setters: {
    setSourceId: (value: string) => void; setTargetId: (value: string) => void;
    setRelationName: (value: string) => void;
    setSourceCardinality: (value: RelationValues['sourceCardinality']) => void;
    setTargetCardinality: (value: RelationValues['targetCardinality']) => void;
    setOwningSide: (value: RelationValues['owningSide']) => void;
    setRelationType: (value: RelationType) => void;
    setSourceHandle: (value: ConnectionPoint) => void; setTargetHandle: (value: ConnectionPoint) => void;
  };
}) {
  return <>
    <h4>Datos de la relación</h4>
    <label>Tipo de relación<select value={values.relationType} onChange={event => setters.setRelationType(event.target.value as RelationType)}>
      <option value="ASSOCIATION">Asociación</option><option value="DEPENDENCY">Dependencia</option>
      <option value="AGGREGATION">Agregación</option><option value="COMPOSITION">Composición</option>
    </select></label>
    <label>Nombre<input value={values.relationName} onChange={event => setters.setRelationName(event.target.value)} placeholder="opcional" /></label>
    <label>Lado propietario<select value={values.owningSide} onChange={event => setters.setOwningSide(event.target.value as RelationValues['owningSide'])}><option value="SOURCE">Origen</option><option value="TARGET">Destino</option></select></label>
    <h4>Extremos y cardinalidades</h4>
    <label>Clase origen<select value={values.sourceId} onChange={event => setters.setSourceId(event.target.value)}><option value="">Seleccionar…</option>{classes.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}</select></label>
    <label>Cardinalidad origen<select value={values.sourceCardinality} onChange={event => setters.setSourceCardinality(event.target.value as RelationValues['sourceCardinality'])}>{cardinalities.map(value => <option key={value}>{value}</option>)}</select></label>
    <label>Punto de conexión origen<select value={values.sourceHandle} onChange={event => setters.setSourceHandle(event.target.value as ConnectionPoint)}>{connectionPoints.map(value => <option key={value} value={value}>{connectionPointLabel(value)}</option>)}</select></label>
    <label>Clase destino<select value={values.targetId} onChange={event => setters.setTargetId(event.target.value)}><option value="">Seleccionar…</option>{classes.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}</select></label>
    <label>Cardinalidad destino<select value={values.targetCardinality} onChange={event => setters.setTargetCardinality(event.target.value as RelationValues['targetCardinality'])}>{cardinalities.map(value => <option key={value}>{value}</option>)}</select></label>
    <label>Punto de conexión destino<select value={values.targetHandle} onChange={event => setters.setTargetHandle(event.target.value as ConnectionPoint)}>{connectionPoints.map(value => <option key={value} value={value}>{connectionPointLabel(value)}</option>)}</select></label>
  </>;
}

function connectionPointLabel(value: ConnectionPoint) {
  const [side, offset] = value.split('-');
  const names: Record<string, string> = { top: 'Superior', right: 'Derecha', bottom: 'Inferior', left: 'Izquierda' };
  return `${names[side]} ${offset}%`;
}
