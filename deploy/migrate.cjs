const fs = require('node:fs');
const { Pool } = require('/app/shared/auth-stub/node_modules/pg');
const common = { host: 'postgres', port: 5432, user: process.env.POSTGRES_USER, password: process.env.POSTGRES_PASSWORD, connectionTimeoutMillis: 10000 };
const source = new Pool({ ...common, database: process.env.POSTGRES_DB });
const report = new Pool({ ...common, database: process.env.POSTGRES_OLAP_DB });
async function main() {
  if (process.env.POSTGRES_DB === process.env.POSTGRES_OLAP_DB) throw new Error("OLTP and OLAP database names must differ");
  for (const name of fs.readdirSync('/app/migrations').filter(name => name.endsWith('.sql')).sort()) {
    await source.query(fs.readFileSync(`/app/migrations/${name}`, 'utf8'));
    console.log(`Applied ${name}`);
  }
  await require('/app/scripts/local-instrument-catalog.cjs')(source);
  for (const table of ['instruments_info', 'trade_facts', 'holdings_info']) {
    await report.query(`ALTER TABLE ${table} ALTER COLUMN instrumentname TYPE VARCHAR(255)`);
  }
  await report.query('ALTER TABLE fill_facts ALTER COLUMN fee DROP NOT NULL');
  console.log('Database preparation complete; no demo accounts or transactions loaded.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; })
  .finally(async () => { await source.end(); await report.end(); });
