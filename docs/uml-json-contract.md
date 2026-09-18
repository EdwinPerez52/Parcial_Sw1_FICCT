# Contrato JSON del modelo UML

La instantánea autoritativa se expone bajo `/api/v1/diagrams/{id}`. Todo elemento direccionable tiene UUID estable y `version`; la instantánea completa tiene `revision`. Los nombres de tipos deben ser identificadores y son únicos sin distinguir mayúsculas.

Tipos escalares: `String`, `Text`, `Integer`, `Long`, `Decimal`, `Boolean`, `Date`, `DateTime`, `UUID` y `Binary`. Un atributo también puede referenciar una enumeración por nombre o UUID. `required: false` significa anulable.

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "name": "Ventas",
  "revision": 8,
  "classes": [{
    "id": "20000000-0000-4000-8000-000000000001",
    "name": "Pedido",
    "attributes": [{
      "id": "30000000-0000-4000-8000-000000000001",
      "name": "id",
      "type": "UUID",
      "primaryKey": true,
      "required": true,
      "unique": true,
      "version": 1
    }],
    "position": { "x": 120, "y": 80 },
    "version": 3
  }],
  "enumerations": [{
    "id": "40000000-0000-4000-8000-000000000001",
    "name": "EstadoPedido",
    "values": [{ "id": "50000000-0000-4000-8000-000000000001", "name": "PENDIENTE", "version": 1 }],
    "position": { "x": 480, "y": 80 },
    "version": 1
  }],
  "associations": [{
    "id": "60000000-0000-4000-8000-000000000001",
    "sourceId": "20000000-0000-4000-8000-000000000001",
    "targetId": "20000000-0000-4000-8000-000000000002",
    "sourceCardinality": "1",
    "targetCardinality": "0..*",
    "name": "pedidos",
    "sourceRole": "pedido",
    "targetRole": "lineas",
    "owningSide": "TARGET",
    "version": 2
  }],
  "generalizations": [{
    "id": "70000000-0000-4000-8000-000000000001",
    "parentId": "20000000-0000-4000-8000-000000000001",
    "childId": "20000000-0000-4000-8000-000000000002",
    "version": 1
  }],
  "packages": [{
    "id": "80000000-0000-4000-8000-000000000001",
    "name": "Ventas",
    "parentId": null,
    "memberIds": ["20000000-0000-4000-8000-000000000001"],
    "version": 1
  }]
}
```

## Operaciones

Toda escritura contiene `operationId`, `baseRevision`, `type`, `payload` y, al modificar o eliminar un elemento existente, `expectedElementVersion`.

- Clases: `CLASS_CREATED`, `CLASS_RENAMED`, `CLASS_MOVED`, `CLASS_DELETED`.
- Atributos: `ATTRIBUTE_CREATED`, `ATTRIBUTE_UPDATED`, `ATTRIBUTE_REORDERED`, `ATTRIBUTE_DELETED`.
- Asociaciones: `ASSOCIATION_CREATED`, `ASSOCIATION_UPDATED`, `ASSOCIATION_DELETED`.
- Enumeraciones: `ENUMERATION_CREATED`, `ENUMERATION_UPDATED`, `ENUMERATION_DELETED`.
- Herencia: `GENERALIZATION_CREATED`, `GENERALIZATION_DELETED`.
- Paquetes UML: `PACKAGE_CREATED`, `PACKAGE_UPDATED`, `PACKAGE_DELETED`. `memberIds` contiene clases, enumeraciones o asociaciones y un elemento solo puede pertenecer a un paquete.
- Lote atómico: `BATCH`, cuyo `payload.operations` contiene operaciones completas.

Una revisión base antigua se acepta cuando la versión del elemento coincide, lo que permite converger cambios independientes. Una versión incompatible responde 409 con `code`, `currentRevision`, `elementId` y `actualElementVersion`. Repetir un `operationId` devuelve el estado actual sin volver a aplicar la escritura.

## Intercambio XMI 2.1

La importación también reconoce el XML nativo de paquete de Enterprise Architect (`Package` con tablas `t_object`, `t_attribute`, `t_connector` y `t_diagramobjects`). Este formato se convierte al mismo modelo semántico y genera una advertencia porque omite metadatos y estilo visual propietarios.

- `POST /api/v1/diagrams/{id}/xmi/preview` recibe un `multipart/form-data` con la parte `file`, exige rol `OWNER` o `EDITOR` y no modifica el diagrama.
- La respuesta contiene `diagram` y advertencias estructuradas `{code, message, externalId, elementType}`. El límite es 5 MB, se rechazan DTD, entidades externas, versiones distintas de 2.1, más de 20 000 elementos y profundidad mayor a 64.
- La confirmación usa el endpoint normal de operaciones con un único `BATCH`; por ello consume una revisión, participa en el control optimista y se deshace mediante otro lote compensatorio.
- `GET /api/v1/diagrams/{id}/xmi` exporta el modelo semántico. Se preservan paquetes anidados, clases, propiedades y tipos, enumeraciones, asociaciones con roles/cardinalidades/lado navegable y generalizaciones. Las extensiones visuales propietarias de Enterprise Architect no se reproducen.
- Las propiedades de clase se presentan en Enterprise Architect como `nombre: Tipo`, sin multiplicidad ni `{bag}`. Las restricciones `primaryKey`, `required` y `unique` se conservan en atributos XML del espacio de nombres de Collab Modeler para permitir su reimportación sin ensuciar el compartimento visual.
