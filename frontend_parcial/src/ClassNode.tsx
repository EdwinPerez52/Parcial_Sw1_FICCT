import { memo } from 'react';
import { Handle, NodeProps, Position } from '@xyflow/react';
import { KeyRound } from 'lucide-react';
import { ClassElement } from './domain';

export const ClassNode = memo(function ClassNode({ data, selected }: NodeProps) {
  const item = data as unknown as ClassElement;
  return (
    <article className={`class-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <header>{item.name}</header>
      <div className="attributes">
        {item.attributes.length === 0 && <span className="empty">Sin atributos</span>}
        {item.attributes.map((attribute) => (
          <div className="attribute" key={attribute.id}>
            <span>{attribute.primaryKey && <KeyRound size={11} />} {attribute.name}</span>
            <strong>{attribute.type}</strong>
          </div>
        ))}
      </div>
      <Handle type="source" position={Position.Right} />
    </article>
  );
});


