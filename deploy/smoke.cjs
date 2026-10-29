// Run only against an isolated test deployment: this creates a user and account.
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const base = process.argv[2] || 'http://127.0.0.1:8081';
const authBase = process.argv[3] || 'http://127.0.0.1:4000';
async function call(path, token, body) {
  const response = await fetch((path.startsWith('/auth/') ? authBase + path.slice(5) : base + path), {
    method: body ? 'POST' : 'GET',
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(15000)
  });
  assert.ok(response.ok, `${path}: HTTP ${response.status}`);
  return response;
}
async function main() {
  assert.equal((await (await call('/auth/health')).json()).status, 'ok');
  assert.equal((await (await call('/actuator/health')).json()).status, 'UP');
  const username = `container-${crypto.randomUUID().slice(0, 8)}`;
  const password = crypto.randomBytes(24).toString('hex');
  await call('/auth/register', null, { username, password, firstName: 'Container', lastName: 'Verification' });
  const login = await (await call('/auth/login', null, { username, password })).json();
  assert.ok(login.token);
  const account = await (await call('/accounts', login.token, { userId: login.userId, accountType: 'INVESTMENT', currency: 'USD' })).json();
  assert.ok(account.accountId);
  const accounts = await (await call(`/accounts/user/${login.userId}`, login.token)).json();
  assert.ok(accounts.some(item => item.accountId === account.accountId));
  // A customer must remain forbidden from staff endpoints behind the gateway.
  const staff = await fetch(base + '/api/admin/orders?from=2026-01-01&to=2026-12-31', { headers: { Authorization: `Bearer ${login.token}` } });
  assert.equal(staff.status, 403);
  console.log('PASS: backend/auth routing, database health, registration, login, account persistence, and staff access isolation. No quote API calls made.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
