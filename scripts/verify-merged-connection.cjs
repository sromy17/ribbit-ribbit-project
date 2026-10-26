const assert = require('node:assert/strict');
const base = process.env.MERGE_UI_URL || 'http://127.0.0.1:14200';
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
  if (process.env.MERGE_DEMO === 'true') {
    const demo=await call('/api/analytics/overview?from=2026-09-01&to=2026-09-30&timezone=UTC',analyst.token);
    assert.equal(Number(demo.data.fill_count),6);
    assert.equal(Number(demo.data.executed_orders),5);
    assert.equal(Number(demo.data.active_clients),3);
    assert.equal(Number(demo.data.submitted_orders),6);
    assert.equal(Number(demo.data.accepted_orders),5);
    for(const [metric,expected] of Object.entries({grossNotional:{GBP:1516,USD:61100},fees:{GBP:2.9,USD:6},netCash:{GBP:-1518.9,USD:-61106}})) {
      for(const [currency,amount] of Object.entries(expected)) assert.equal(Number(demo.data[metric].find(row=>row.currency===currency).amount),amount);
    }
    const ny=await call('/api/analytics/overview?from=2026-09-01&to=2026-09-30&timezone=America%2FNew_York',analyst.token);
    assert.equal(Number(ny.data.fill_count),5);
    assert.equal(Number(ny.data.executed_orders),4);
    assert.equal(Number(ny.data.submitted_orders),6);
    assert.equal(Number(ny.data.grossNotional.find(row=>row.currency==='GBP').amount),1112);
    const request={from:'2026-09-01',to:'2026-09-30',timezone:'UTC',idempotencyKey:'demo-check-'+crypto.randomUUID(),datasetId:demo.datasetId};
    const issued=await call('/api/analytics/reports',analyst.token,request);
    const reopened=await call('/api/analytics/reports/'+issued.reportId,analyst.token);
    assert.equal(Number(reopened.snapshot.overview.fill_count),6);
    assert.equal(reopened.snapshot['client-segments'].availability,'WITHHELD');
    const amendment=await call('/api/analytics/reports',analyst.token,{...request,idempotencyKey:'demo-amend-'+crypto.randomUUID(),amendsReportId:issued.reportId,amendmentReason:'Synthetic demo verification; source unchanged.'});
    assert.equal(amendment.version,2);
    assert.equal(amendment.amendsReportId,issued.reportId);
    const original=await call('/api/analytics/reports/'+issued.reportId,analyst.token);
    assert.equal(original.version,1);
    console.log('PASS: September demo totals independently checked: 6 fills, 5 executed orders, 3 active clients; GBP/USD turnover, fees and cash reconcile.');
  }
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
