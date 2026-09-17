import { NodeProps } from '@xyflow/react';
import { EnumerationElement } from './domain';

export function EnumerationNode({ data, selected }: NodeProps) {
  const item = data as unknown as EnumerationElement;
  return (
    <article className={`class-node enumeration-node ${selected ? 'selected' : ''}`}>
      <header><small>&lt;&lt;enumeration&gt;&gt;</small>{item.name}</header>
      <div className="attributes">
        {item.values.length === 0 && <span className="empty">Sin valores</span>}
        {item.values.map(value => <div className="attribute" key={value.id}><span>{value.name}</span></div>)}
      </div>
    </article>
  );
}
