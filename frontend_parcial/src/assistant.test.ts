import { describe, expect, it } from 'vitest';
import { parseAssistantCommand } from './assistant';

describe('parseAssistantCommand', () => {
  it('crea una clase desde español natural', () => {
    expect(parseAssistantCommand('crea una clase Producto')).toEqual({ type: 'createClass', name: 'Producto' });
  });

  it('crea un atributo tipado', () => {
    expect(parseAssistantCommand('agrega atributo precio tipo decimal a Producto')).toEqual({
      type: 'addAttribute', attributeName: 'precio', attributeType: 'Decimal', className: 'Producto',
    });
  });

  it('marca instrucciones desconocidas', () => {
    expect(parseAssistantCommand('haz algo')).toMatchObject({ type: 'unknown' });
  });
});
