import { Association, Attribute, ClassElement, DiagramModel, DiagramOperation, EnumerationElement, Generalization } from './domain';

const clone = <T>(value: T): T => structuredClone(value);

export function applyDiagramOperation(diagram: DiagramModel, operation: DiagramOperation, resultRevision = diagram.revision + 1): DiagramModel {
  const next = applyWithoutRevision(clone(diagram), operation);
  return { ...next, revision: resultRevision };
}

function applyWithoutRevision(diagram: DiagramModel, operation: DiagramOperation): DiagramModel {
  const payload = operation.payload as Record<string, unknown>;
  switch (operation.type) {
    case 'CLASS_CREATED': {
      const value = clone(payload as unknown as ClassElement);
      return { ...diagram, classes: [...diagram.classes, { ...value, kind: 'class', version: 1, attributes: value.attributes.map(item => ({ ...item, version: 1 })) }] };
    }
    case 'CLASS_RENAMED': return updateClass(diagram, String(payload.id), value => ({ ...value, name: String(payload.name), version: value.version + 1 }));
    case 'CLASS_MOVED': return updateClass(diagram, String(payload.id), value => ({ ...value, position: { x: Number(payload.x), y: Number(payload.y) }, version: value.version + 1 }));
    case 'CLASS_DELETED': {
      const id = String(payload.id);
      return { ...diagram, classes: diagram.classes.filter(value => value.id !== id),
        associations: diagram.associations.filter(value => value.sourceId !== id && value.targetId !== id),
        generalizations: diagram.generalizations.filter(value => value.parentId !== id && value.childId !== id) };
    }
    case 'ATTRIBUTE_CREATED': {
      const classId = String(payload.classId); const attribute = clone(payload.attribute as Attribute);
      return updateClass(diagram, classId, value => ({ ...value, version: value.version + 1,
        attributes: [...value.attributes, { ...attribute, version: 1 }] }));
    }
    case 'ATTRIBUTE_UPDATED': {
      const classId = String(payload.classId); const attribute = clone(payload.attribute as Attribute);
      return updateClass(diagram, classId, value => ({ ...value, version: value.version + 1,
        attributes: value.attributes.map(old => old.id === attribute.id ? { ...attribute, version: old.version + 1 } : old) }));
    }
    case 'ATTRIBUTE_REORDERED': {
      const classId = String(payload.classId); const attributeId = String(payload.attributeId); const index = Number(payload.newIndex);
      return updateClass(diagram, classId, value => {
        const attributes = [...value.attributes]; const old = attributes.findIndex(item => item.id === attributeId);
        if (old < 0) throw new Error('Atributo no encontrado durante la reconciliación');
        const [moved] = attributes.splice(old, 1); attributes.splice(index, 0, moved);
        return { ...value, version: value.version + 1, attributes };
      });
    }
    case 'ATTRIBUTE_DELETED': return updateClass(diagram, String(payload.classId), value => ({ ...value, version: value.version + 1,
      attributes: value.attributes.filter(item => item.id !== String(payload.id)) }));
    case 'ASSOCIATION_CREATED': return { ...diagram, associations: [...diagram.associations, { ...(clone(payload) as unknown as Association), version: 1 }] };
    case 'ASSOCIATION_UPDATED': {
      const value = clone(payload) as unknown as Association;
      return { ...diagram, associations: diagram.associations.map(old => old.id === value.id ? { ...value, version: old.version + 1 } : old) };
    }
    case 'ASSOCIATION_DELETED': return { ...diagram, associations: diagram.associations.filter(value => value.id !== String(payload.id)) };
    case 'ENUMERATION_CREATED': {
      const value = clone(payload) as unknown as EnumerationElement;
      return { ...diagram, enumerations: [...diagram.enumerations, { ...value, kind: 'enumeration', version: 1,
        values: value.values.map(item => ({ ...item, version: 1 })) }] };
    }
    case 'ENUMERATION_UPDATED': {
      const value = clone(payload) as unknown as EnumerationElement;
      return { ...diagram, enumerations: diagram.enumerations.map(old => old.id === value.id ? { ...value, kind: 'enumeration', version: old.version + 1,
        values: value.values.map(item => ({ ...item, version: old.values.find(candidate => candidate.id === item.id)?.version ?? 1 })) } : old) };
    }
    case 'ENUMERATION_VALUE_CREATED': {
      const enumerationId = String(payload.enumerationId); const value = clone(payload.value) as { id: string; name: string; version: number };
      return { ...diagram, enumerations: diagram.enumerations.map(old => old.id === enumerationId
        ? { ...old, version: old.version + 1, values: [...old.values, { ...value, version: 1 }] } : old) };
    }
    case 'ENUMERATION_VALUE_UPDATED': {
      const enumerationId = String(payload.enumerationId); const value = clone(payload.value) as { id: string; name: string; version: number };
      return { ...diagram, enumerations: diagram.enumerations.map(old => old.id === enumerationId
        ? { ...old, version: old.version + 1, values: old.values.map(entry => entry.id === value.id ? { ...value, version: entry.version + 1 } : entry) } : old) };
    }
    case 'ENUMERATION_VALUE_DELETED': {
      const enumerationId = String(payload.enumerationId);
      return { ...diagram, enumerations: diagram.enumerations.map(old => old.id === enumerationId
        ? { ...old, version: old.version + 1, values: old.values.filter(entry => entry.id !== String(payload.id)) } : old) };
    }
    case 'ENUMERATION_DELETED': return { ...diagram, enumerations: diagram.enumerations.filter(value => value.id !== String(payload.id)) };
    case 'GENERALIZATION_CREATED': return { ...diagram, generalizations: [...diagram.generalizations, { ...(clone(payload) as unknown as Generalization), version: 1 }] };
    case 'GENERALIZATION_DELETED': return { ...diagram, generalizations: diagram.generalizations.filter(value => value.id !== String(payload.id)) };
    case 'BATCH': return ((payload.operations as DiagramOperation[]) ?? []).reduce(applyWithoutRevision, diagram);
    case 'MODEL_RESTORED': {
      const model = clone(payload.model as DiagramModel);
      return { ...model, id: diagram.id, name: diagram.name, revision: diagram.revision };
    }
  }
}

function updateClass(diagram: DiagramModel, id: string, update: (value: ClassElement) => ClassElement): DiagramModel {
  if (!diagram.classes.some(value => value.id === id)) throw new Error('Clase no encontrada durante la reconciliación');
  return { ...diagram, classes: diagram.classes.map(value => value.id === id ? update(value) : value) };
}
