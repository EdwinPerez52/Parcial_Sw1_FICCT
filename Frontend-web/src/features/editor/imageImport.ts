import { ImageProposal } from '../../shared/api/api';
import { DiagramModel, ScalarType, createId, scalarTypes, cardinalities } from './domain';

const validName = (name: string) => /^[A-Za-z_][A-Za-z0-9_]{0,79}$/.test(name);

/** Builds a candidate in memory. The caller commits it through one BATCH transition. */
export function buildImageImport(current: DiagramModel, proposal: ImageProposal): DiagramModel {
  const names = new Set(current.classes.map(item => item.name.toLowerCase()));
  const ids = new Map<string, string>();
  const classes = proposal.classes.map((candidate, index) => {
    const name = candidate.name.trim();
    if (!validName(name) || names.has(name.toLowerCase())) throw new Error(`Nombre de clase inválido o repetido: ${name}`);
    names.add(name.toLowerCase());
    const id = createId(); ids.set(name.toLowerCase(), id);
    const attributeNames = new Set<string>();
    const attributes = candidate.attributes.map(attribute => {
      const attributeName = attribute.name.trim();
      if (!validName(attributeName) || attributeNames.has(attributeName.toLowerCase()) || !scalarTypes.includes(attribute.type as ScalarType))
        throw new Error(`Atributo inválido en ${name}: ${attributeName}`);
      attributeNames.add(attributeName.toLowerCase());
      return { id: createId(), name: attributeName, type: attribute.type, primaryKey: attribute.primaryKey ?? false,
        required: attribute.required ?? false, unique: attribute.unique ?? false, version: 1 };
    });
    return { id, kind: 'class' as const, name, attributes, position: { x: 160 + (index % 4) * 240, y: 120 + Math.floor(index / 4) * 200 }, version: 1 };
  });
  const associations = proposal.associations.map(link => {
    const sourceId = ids.get(link.source.trim().toLowerCase()); const targetId = ids.get(link.target.trim().toLowerCase());
    if (!sourceId || !targetId || !cardinalities.includes(link.sourceCardinality) || !cardinalities.includes(link.targetCardinality))
      throw new Error(`Relación inválida: ${link.source} → ${link.target}`);
    return { id: createId(), sourceId, targetId, sourceCardinality: link.sourceCardinality,
      targetCardinality: link.targetCardinality, name: link.name?.trim() || undefined, sourceRole: link.sourceRole?.trim() ?? '',
      targetRole: link.targetRole?.trim() ?? '', owningSide: link.owningSide ?? 'SOURCE' as const, version: 1 };
  });
  return { ...current, classes: [...current.classes, ...classes], associations: [...current.associations, ...associations] };
}

export async function optimizeImageForAnalysis(file: File): Promise<File> {
  if (typeof window === 'undefined' || typeof document === 'undefined' || !file.type.startsWith('image/')) {
    return file;
  }
  try {
    return await new Promise<File>(resolve => {
      const img = new Image();
      const url = URL.createObjectURL(file);
      img.onload = () => {
        URL.revokeObjectURL(url);
        const maxSide = 1920;
        let { width, height } = img;
        if (width <= maxSide && height <= maxSide && file.size <= 1_500_000) {
          resolve(file);
          return;
        }
        if (width > maxSide || height > maxSide) {
          if (width > height) {
            height = Math.round((height * maxSide) / width);
            width = maxSide;
          } else {
            width = Math.round((width * maxSide) / height);
            height = maxSide;
          }
        }
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) { resolve(file); return; }
        ctx.drawImage(img, 0, 0, width, height);
        canvas.toBlob(blob => {
          if (!blob) { resolve(file); return; }
          const optimized = new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' });
          resolve(optimized);
        }, 'image/jpeg', 0.85);
      };
      img.onerror = () => {
        URL.revokeObjectURL(url);
        resolve(file);
      };
      img.src = url;
    });
  } catch {
    return file;
  }
}
