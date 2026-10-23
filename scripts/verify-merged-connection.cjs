const assert = require('node:assert/strict');
const base = 'http://127.0.0.1:14200';
async function call(path, token, body) {
  const response = await fetch(base+path, {method: body?'POST':'GET',headers:{...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});
  const text=await response.text();
  assert.ok(response.ok, `${path}: ${response.status} ${text.slice(0,400)}`);
  return text?JSON.parse(text):{};
}
(async()=>{
  const admin=await call('/api/login',null,{username:'john_trader',password:'mission123'});
  const staff=await call('/api/admin/session',admin.token);
  assert.ok(staff.permissions.includes('PERM_STAFF_ADMIN'));
  await call('/api/admin/accounts/101/history',admin.token);
  await call('/api/etl/run',admin.token,{});
  const analyst=await call('/api/login',null,{username:'jane_investor',password:'mission123'});
  const priya=await call('/api/admin/session',analyst.token);
  assert.ok(priya.permissions.includes('PERM_REPORTING'));
  const overview=await call('/api/analytics/overview?from=2024-01-01&to=2024-01-31&timezone=UTC',analyst.token);
  assert.ok(overview.datasetId);
  const customer=await call('/auth/login',null,{username:'bob_daytrader',password:'mission123'});
  const accounts=await call(`/api/accounts/user/${customer.userId}`,customer.token);
  assert.ok(accounts.length>0);
  await call(`/api/accounts/${accounts[0].accountId}/wallet/banks`,customer.token);
  const refreshed=await call('/auth/refresh',null,{refreshToken:customer.refreshToken});
  await call(`/api/accounts/user/${customer.userId}`,refreshed.token);
  await call('/auth/logout',null,{accessToken:refreshed.token});
  const denied=await fetch(base+`/api/accounts/user/${customer.userId}`,{headers:{Authorization:`Bearer ${refreshed.token}`}});
  assert.equal(denied.status,401);
  const rotated=await call('/api/refresh',null,{refreshToken:admin.refreshToken});
  await call('/api/admin/session',rotated.token);
  await call('/api/logout',null,{accessToken:rotated.token});
  console.log('PASS: real Angular proxy → customer/staff auth → PostgreSQL backend; admin history, ETL, analyst OLAP, customer accounts/wallet, refresh and logout.');
})().catch(error=>{console.error(error);process.exitCode=1;});
