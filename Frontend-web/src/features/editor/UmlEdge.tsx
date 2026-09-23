import { BaseEdge, EdgeLabelRenderer, EdgeProps, getBezierPath } from '@xyflow/react';
import { useDiagramStore } from './store';

type UmlEdgeData = {
  name?: string;
  sourceCardinality?: string;
  targetCardinality?: string;
  relationType?: string;
};

export function UmlEdge({ id, sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition, selected, data }: EdgeProps) {
  const selectElements = useDiagramStore(state => state.selectElements);
  const details = (data ?? {}) as UmlEdgeData;
  const type = details.relationType ?? 'ASSOCIATION';
  const [path, labelX, labelY] = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
  const markerId = `uml-marker-${id}`;
  const markerStart = type === 'AGGREGATION' || type === 'COMPOSITION' ? `url(#${markerId})` : undefined;
  const markerEnd = type === 'DEPENDENCY' || type === 'GENERALIZATION' ? `url(#${markerId})` : undefined;
  const color = selected ? '#4f46e5' : '#475569';
  const label = (value: string | undefined, x: number, y: number, className: string) => value ?
    <button type="button" className={`uml-edge-label nodrag nopan ${className}`} title="Editar esta relación"
      onClick={event => { event.stopPropagation(); selectElements([id]); }}
      style={{ transform: `translate(-50%, -50%) translate(${x}px, ${y}px)` }}>{value}</button> : null;

  return <>
    <defs>
      {type === 'AGGREGATION' || type === 'COMPOSITION' ?
        <marker id={markerId} markerWidth="16" markerHeight="12" refX="1" refY="6" orient="auto-start-reverse" markerUnits="userSpaceOnUse">
          <path d="M1 6 L8 1 L15 6 L8 11 Z" fill={type === 'COMPOSITION' ? color : 'white'} stroke={color} strokeWidth="1.5" />
        </marker> :
        <marker id={markerId} markerWidth="15" markerHeight="15" refX="13" refY="7.5" orient="auto" markerUnits="userSpaceOnUse">
          <path d={type === 'GENERALIZATION' ? 'M1 1 L13 7.5 L1 14 Z' : 'M1 2 L13 7.5 L1 13'} fill={type === 'GENERALIZATION' ? 'white' : 'none'} stroke={color} strokeWidth="1.5" />
        </marker>}
    </defs>
    <BaseEdge id={id} path={path} markerStart={markerStart} markerEnd={markerEnd} interactionWidth={24}
      style={{ stroke: color, strokeWidth: selected ? 2.5 : 1.8, strokeDasharray: type === 'DEPENDENCY' ? '5 4' : undefined }} />
    <EdgeLabelRenderer>
      {label(details.sourceCardinality, sourceX * .84 + targetX * .16, sourceY * .84 + targetY * .16 - 12, 'endpoint')}
      {label(details.name, labelX, labelY - 14, 'relation-name')}
      {label(details.targetCardinality, sourceX * .16 + targetX * .84, sourceY * .16 + targetY * .84 - 12, 'endpoint')}
    </EdgeLabelRenderer>
  </>;
}
