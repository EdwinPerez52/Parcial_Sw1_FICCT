export const scalarTypes = ['String', 'Text', 'Integer', 'Long', 'Decimal', 'Boolean', 'Date', 'DateTime', 'UUID', 'Binary'] as const;
export type ScalarType = typeof scalarTypes[number];
export const cardinalities = ['0..1', '1', '0..*', '1..*'] as const;
export type Cardinality = typeof cardinalities[number];
export type OwningSide = 'SOURCE' | 'TARGET';

export interface Position { x: number; y: number }

export interface Attribute {
  id: string;
  name: string;
  type: ScalarType | string;
  primaryKey: boolean;
  required: boolean;
  unique: boolean;
  version: number;
}

export interface ClassElement {
  id: string;
  kind: 'class';
  name: string;
  attributes: Attribute[];
  position: Position;
  version: number;
}

export interface EnumerationValue {
  id: string;
  name: string;
  version: number;
}

export interface EnumerationElement {
  id: string;
  kind: 'enumeration';
  name: string;
  values: EnumerationValue[];
  position: Position;
  version: number;
}

export interface Association {
  id: string;
  sourceId: string;
  targetId: string;
  sourceCardinality: Cardinality;
  targetCardinality: Cardinality;
  name?: string;
  sourceRole?: string;
  targetRole?: string;
  owningSide: OwningSide;
  version: number;
}

export interface Generalization {
  id: string;
  parentId: string;
  childId: string;
  version: number;
}

export interface DiagramModel {
  id: string;
  name: string;
  revision: number;
  classes: ClassElement[];
  enumerations: EnumerationElement[];
  associations: Association[];
  generalizations: Generalization[];
}

export interface DiagramOperation {
  operationId: string;
  baseRevision: number;
  expectedElementVersion?: number;
  type:
    | 'CLASS_CREATED' | 'CLASS_RENAMED' | 'CLASS_MOVED' | 'CLASS_DELETED'
    | 'ATTRIBUTE_CREATED' | 'ATTRIBUTE_UPDATED' | 'ATTRIBUTE_REORDERED' | 'ATTRIBUTE_DELETED'
    | 'ASSOCIATION_CREATED' | 'ASSOCIATION_UPDATED' | 'ASSOCIATION_DELETED'
    | 'ENUMERATION_CREATED' | 'ENUMERATION_UPDATED' | 'ENUMERATION_DELETED'
    | 'GENERALIZATION_CREATED' | 'GENERALIZATION_DELETED'
    | 'BATCH' | 'MODEL_RESTORED';
  payload: unknown;
}

export const createId = () => crypto.randomUUID();

export function normalizeDiagram(value: Partial<DiagramModel> & Pick<DiagramModel, 'id' | 'name' | 'revision'>): DiagramModel {
  return {
    ...value,
    classes: (value.classes ?? []).map(item => ({
      ...item,
      kind: 'class',
      position: item.position ?? { x: 0, y: 0 },
      attributes: (item.attributes ?? []).map(attribute => ({
        ...attribute,
        primaryKey: attribute.primaryKey ?? false,
        required: attribute.required ?? false,
        unique: attribute.unique ?? false,
        version: attribute.version || 1,
      })),
    })),
    enumerations: (value.enumerations ?? []).map(item => ({
      ...item, kind: 'enumeration', version: item.version || 1,
      values: (item.values ?? []).map(entry => ({ ...entry, version: entry.version || 1 })),
    })),
    associations: (value.associations ?? []).map(item => ({ ...item, owningSide: item.owningSide ?? 'SOURCE' })),
    generalizations: value.generalizations ?? [],
  } as DiagramModel;
}
