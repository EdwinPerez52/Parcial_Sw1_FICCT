import { memo } from 'react';
import { Handle, NodeProps, Position } from '@xyflow/react';
import { KeyRound } from 'lucide-react';
import { ClassElement } from './domain';

export const ClassNode = memo(function ClassNode({ data, selected }: NodeProps) {
  const item = data as unknown as ClassElement & { foreignKeyNames?: string[] };
  return (
    <article className={`class-node ${selected ? 'selected' : ''}`}>
      <ConnectionHandles />
      <header>{item.name}</header>
      <div className="attributes">
        {item.attributes.length === 0 && <span className="empty">Sin atributos</span>}
        {item.attributes.map((attribute) => (
          <div className="attribute" key={attribute.id}>
            <span>{attribute.primaryKey && <KeyRound size={11} />} {attribute.name}{item.foreignKeyNames?.includes(attribute.name) && <em className="fk-badge">FK</em>}</span>
            <strong>{attribute.type}</strong>
          </div>
        ))}
      </div>
    </article>
  );
});

const SIDE_POINTS = [25, 50, 75];

function ConnectionHandles() {
  return <>
    {SIDE_POINTS.map(offset => <Handle key={`top-${offset}`} id={`top-${offset}`} type="source" position={Position.Top}
      className="uml-node-handle" style={{ left: `${offset}%` }} />)}
    {SIDE_POINTS.map(offset => <Handle key={`right-${offset}`} id={`right-${offset}`} type="source" position={Position.Right}
      className="uml-node-handle" style={{ top: `${offset}%` }} />)}
    {SIDE_POINTS.map(offset => <Handle key={`bottom-${offset}`} id={`bottom-${offset}`} type="source" position={Position.Bottom}
      className="uml-node-handle" style={{ left: `${offset}%` }} />)}
    {SIDE_POINTS.map(offset => <Handle key={`left-${offset}`} id={`left-${offset}`} type="source" position={Position.Left}
      className="uml-node-handle" style={{ top: `${offset}%` }} />)}
  </>;
}


