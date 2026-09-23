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

  it('crear una clase Producto (wording alternativo)', () => {
    expect(parseAssistantCommand('crear una clase Producto')).toEqual({ type: 'createClass', name: 'Producto' });
  });

  it('crea una clase producto (lowercase, preserva nombre)', () => {
    expect(parseAssistantCommand('crea una clase producto')).toEqual({ type: 'createClass', name: 'producto' });
  });

  it('elimina la clase Producto', () => {
    expect(parseAssistantCommand('elimina la clase Producto')).toEqual({ type: 'deleteClass', name: 'Producto' });
  });
});
