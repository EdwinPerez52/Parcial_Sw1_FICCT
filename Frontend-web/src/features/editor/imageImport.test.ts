import { describe, expect, it } from 'vitest';
import { buildImageImport } from './imageImport';
import { DiagramModel } from './domain';

const empty: DiagramModel = { id: crypto.randomUUID(), name: 'Test', revision: 0, classes: [], associations: [], enumerations: [], generalizations: [], packages: [] };
const proposal = { classes: [{ name: 'Product', attributes: [{ name: 'id', type: 'UUID', primaryKey: true }] }, { name: 'Order', attributes: [] }],
  associations: [{ source: 'Product', target: 'Order', sourceCardinality: '1' as const, targetCardinality: '0..*' as const }], warnings: [], confidence: 0.9 };

describe('photograph import', () => {
  it('does not change the source before confirmation and creates valid references', () => {
    const candidate = buildImageImport(empty, { ...proposal, associations: [{ ...proposal.associations[0],
      sourceRole: 'cliente', targetRole: 'pedidos', owningSide: 'TARGET' as const }] });
    expect(empty.classes).toHaveLength(0);
    expect(candidate.classes).toHaveLength(2);
    expect(candidate.associations[0].sourceId).toBe(candidate.classes[0].id);
    expect(candidate.associations[0]).toMatchObject({ sourceRole: 'cliente', targetRole: 'pedidos', owningSide: 'TARGET' });
  });
  it('rejects invalid relations before touching the model', () => {
    expect(() => buildImageImport(empty, { ...proposal, associations: [{ ...proposal.associations[0], target: 'Missing' }] })).toThrow();
    expect(empty.classes).toHaveLength(0);
  });

  it('rejects duplicate class names', () => {
    const duplicateProposal = {
      ...proposal,
      classes: [
        { name: 'Product', attributes: [] },
        { name: 'product', attributes: [] }
      ]
    };
    expect(() => buildImageImport(empty, duplicateProposal)).toThrowError(/repetido/);
  });

  it('rejects invalid attribute types', () => {
    const invalidTypeProposal = {
      ...proposal,
      classes: [
        { name: 'Product', attributes: [{ name: 'id', type: 'InvalidType' as any, primaryKey: true }] }
      ]
    };
    expect(() => buildImageImport(empty, invalidTypeProposal)).toThrowError(/Atributo inválido/);
  });

  it('correctly links multiple classes with associations', () => {
    const multiProposal = {
      ...proposal,
      classes: [
        { name: 'A', attributes: [] },
        { name: 'B', attributes: [] },
        { name: 'C', attributes: [] }
      ],
      associations: [
        { source: 'A', target: 'B', sourceCardinality: '1' as const, targetCardinality: '0..*' as const },
        { source: 'B', target: 'C', sourceCardinality: '1' as const, targetCardinality: '1' as const }
      ]
    };
    const candidate = buildImageImport(empty, multiProposal);
    expect(candidate.classes).toHaveLength(3);
    expect(candidate.associations).toHaveLength(2);
    
    const idA = candidate.classes.find(c => c.name === 'A')?.id;
    const idB = candidate.classes.find(c => c.name === 'B')?.id;
    const idC = candidate.classes.find(c => c.name === 'C')?.id;
    
    expect(candidate.associations[0].sourceId).toBe(idA);
    expect(candidate.associations[0].targetId).toBe(idB);
    
    expect(candidate.associations[1].sourceId).toBe(idB);
    expect(candidate.associations[1].targetId).toBe(idC);
  });
});
