import { ScalarType } from './domain';

export type AssistantIntent =
  | { type: 'createClass'; name: string }
  | { type: 'addAttribute'; className: string; attributeName: string; attributeType: ScalarType }
  | { type: 'deleteClass'; name: string }
  | { type: 'unknown'; message: string };

const typeNames: Record<string, ScalarType> = {
  texto: 'String', string: 'String', entero: 'Integer', integer: 'Integer',
  largo: 'Long', long: 'Long', decimal: 'Decimal', booleano: 'Boolean',
  fecha: 'Date', date: 'Date', uuid: 'UUID', binario: 'Binary',
};

export function parseAssistantCommand(raw: string): AssistantIntent {
  const command = raw.trim();
  const create = command.match(/(?:crea|crear|agrega|agregar) (?:una )?clase (?:llamada |que se llame )?([A-Za-z_][A-Za-z\d_]*)/i);
  if (create) return { type: 'createClass', name: create[1] };

  const attribute = command.match(/(?:agrega|agregar|crea|crear) (?:un )?atributo ([A-Za-z_][A-Za-z\d_]*) (?:de tipo |tipo )([A-Za-z]+) (?:a|en) (?:la clase )?([A-Za-z_][A-Za-z\d_]*)/i);
  if (attribute) {
    const attributeType = typeNames[attribute[2].toLowerCase()];
    if (attributeType) return { type: 'addAttribute', attributeName: attribute[1], attributeType, className: attribute[3] };
  }

  const remove = command.match(/(?:elimina|eliminar|borra|borrar) (?:la )?clase ([A-Za-z_][A-Za-z\d_]*)/i);
  if (remove) return { type: 'deleteClass', name: remove[1] };
  return { type: 'unknown', message: 'No pude convertir el mensaje en una operación válida.' };
}
