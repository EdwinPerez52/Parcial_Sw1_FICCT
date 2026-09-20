const { execSync } = require('child_process');
const crypto = require('crypto');

const raw = execSync('docker exec 1erparcialsw1_2-2026-postgres-1 psql -U modeler -d modeler -t -A -c "select model_json from diagrams where name = \'prueba\';"').toString().trim();
const doc = JSON.parse(raw);

console.log('Original classes:', doc.classes.map(c => c.name));

for (const cls of doc.classes) {
  let hasPk = cls.attributes.some(a => a.primaryKey);
  if (!hasPk) {
    console.log(`Adding primary key to class: ${cls.name}`);
    cls.attributes.unshift({
      id: crypto.randomUUID(),
      name: 'id',
      type: 'UUID',
      primaryKey: true,
      required: true,
      unique: true,
      version: 1
    });
  }
}

const fs = require('fs');
fs.writeFileSync('scratch/updated_prueba.json', JSON.stringify(doc));

// Pipe into psql
execSync('docker cp scratch/updated_prueba.json 1erparcialsw1_2-2026-postgres-1:/tmp/updated_prueba.json');
execSync('docker exec 1erparcialsw1_2-2026-postgres-1 psql -U modeler -d modeler -c "update diagrams set model_json = pg_read_file(\'/tmp/updated_prueba.json\') where name = \'prueba\';"');

console.log('Successfully updated diagram prueba with valid primary keys!');
