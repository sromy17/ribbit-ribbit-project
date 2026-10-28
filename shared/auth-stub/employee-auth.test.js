const { before, after, test } = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const express = require("express");
const jwt = require("jsonwebtoken");
const { Pool } = require("pg");
const { createEmployeeAuth } = require("./employee-auth");

if (!process.env.TEST_DATABASE_URL) {
  throw new Error("Set TEST_DATABASE_URL to a disposable PostgreSQL database. Tests create and drop an isolated schema.");
}
const secret = "employee-auth-integration-test-secret-only";
const schema = `employee_auth_test_${process.pid}_${crypto.randomBytes(4).toString("hex")}`;
const adminPool = new Pool({ connectionString: process.env.TEST_DATABASE_URL });
const pool = new Pool({
  connectionString: process.env.TEST_DATABASE_URL, options: `-c search_path=${schema}`
});
let server;
let origin;
const hash = value => crypto.createHash("sha256").update(value).digest("hex");

async function post(endpoint, body) {
  const response = await fetch(`${origin}/auth/employee/${endpoint}`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
  });
  return { status: response.status, body: response.status === 204 ? null : await response.json() };
}
const login = (username = "admin_user", password = "AdminDemo123!") => post("login", { username, password });

before(async () => {
  await adminPool.query(`CREATE SCHEMA ${schema}`);
  const root = path.resolve(__dirname, "..", "..");
  await pool.query(await readFile(path.join(root, "basedSchema.sql"), "utf8"));
  await pool.query(await readFile(path.join(root, "insert-data.sql"), "utf8"));
  const migration = await readFile(path.join(root, "scripts", "employee-auth.sql"), "utf8");
  await pool.query(migration);
  await pool.query(migration);
  const app = express();
  app.use(express.json());
  app.use("/auth/employee", createEmployeeAuth({
    pool, secret, expiresInSeconds: 1200, refreshValidDays: 7
  }));
  server = await new Promise(resolve => {
    const listener = app.listen(0, "127.0.0.1", () => resolve(listener));
  });
  origin = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  if (server) { await new Promise(resolve => server.close(resolve)); }
  await pool.end();
  await adminPool.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
  await adminPool.end();
});

test("seeded admin and analyst authenticate with separate employee JWT identities", async () => {
  for (const [username, password, role] of [
    ["admin_user", "AdminDemo123!", "admin"], ["analyst_user", "AnalystDemo123!", "analyst"]
  ]) {
    const result = await login(username, password);
    assert.equal(result.status, 200);
    assert.equal(result.body.role, role);
    const claims = jwt.verify(result.body.token, secret);
    assert.equal(claims.principal_type, "employee");
    assert.equal(claims.sub, username);
    assert.equal(claims.eid, result.body.userId);
    assert.equal(claims.uid, undefined);
    assert.deepEqual(claims.roles, [role.toUpperCase()]);
    const saved = await pool.query(
      "SELECT accesstokenhash FROM access_tokens_employee WHERE accesstokenhash = $1", [hash(result.body.token)]
    );
    assert.equal(saved.rowCount, 1);
    assert.notEqual(saved.rows[0].accesstokenhash, result.body.token);
  }
});

test("rejects bad credentials, investors, unsupported staff roles, and invalid inputs", async () => {
  for (const [username, password] of [
    ["admin_user", "wrong"], ["missing", "password"], ["john_trader", "mission123"],
    ["support_staff", "password"]
  ]) {
    assert.equal((await login(username, password)).status, 401);
  }
  for (const body of [{}, { username: [], password: {} }, { username: "admin_user", password: "" }]) {
    assert.equal((await post("login", body)).status, 400);
  }
});

test("does not offer an employee registration endpoint", async () => {
  const response = await fetch(`${origin}/auth/employee/register`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "new_admin", password: "password", role: "ADMIN" })
  });
  assert.equal(response.status, 404);
  assert.equal((await pool.query("SELECT 1 FROM employees WHERE employeeusername = 'new_admin'")).rowCount, 0);
});

test("rotates refresh tokens once, revokes old access, and rejects concurrent reuse", async () => {
  const initial = (await login()).body;
  const results = await Promise.all([
    post("refresh", { refreshToken: initial.refreshToken }),
    post("refresh", { refreshToken: initial.refreshToken })
  ]);
  assert.deepEqual(results.map(result => result.status).sort(), [200, 401]);
  const replacement = results.find(result => result.status === 200).body;
  assert.equal(replacement.role, "admin");
  assert.notEqual(replacement.token, initial.token);
  assert.notEqual(replacement.refreshToken, initial.refreshToken);
  const old = await pool.query("SELECT isrevoked FROM access_tokens_employee WHERE accesstokenhash = $1", [hash(initial.token)]);
  assert.equal(old.rows[0].isrevoked, true);
});

test("refresh checks expiry and the employee's current database role", async () => {
  const expired = (await login()).body;
  await pool.query(
    "UPDATE refresh_tokens_employee SET dateexpires = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE refreshtokenhash = $1",
    [hash(expired.refreshToken)]
  );
  assert.equal((await post("refresh", { refreshToken: expired.refreshToken })).status, 401);
  const changed = (await login()).body;
  await pool.query("UPDATE employees SET employeeroles = 'SUPPORT' WHERE employeeusername = 'admin_user'");
  try {
    assert.equal((await post("refresh", { refreshToken: changed.refreshToken })).status, 401);
  } finally {
    await pool.query("UPDATE employees SET employeeroles = 'ADMIN' WHERE employeeusername = 'admin_user'");
  }
});

test("logout revokes both stored tokens and prevents session renewal", async () => {
  const session = (await login()).body;
  assert.equal((await post("logout", { accessToken: session.token })).status, 204);
  assert.equal((await post("refresh", { refreshToken: session.refreshToken })).status, 401);
  const stored = await pool.query(
    "SELECT isrevoked FROM access_tokens_employee WHERE accesstokenhash = $1", [hash(session.token)]
  );
  assert.equal(stored.rows[0].isrevoked, true);
  assert.equal((await post("logout", { accessToken: "invalid-token" })).status, 401);
});

test("reapplying the migration preserves real passwords and existing investor data", async () => {
  await pool.query("UPDATE employees SET employeehashedsaltedpassword = 'custom-hash' WHERE employeeusername = 'analyst_user'");
  try {
    await pool.query(await readFile(path.resolve(__dirname, "..", "..", "scripts", "employee-auth.sql"), "utf8"));
    const employee = await pool.query("SELECT employeehashedsaltedpassword FROM employees WHERE employeeusername = 'analyst_user'");
    assert.equal(employee.rows[0].employeehashedsaltedpassword, "custom-hash");
    assert.equal((await pool.query("SELECT * FROM users")).rowCount, 5);
    assert.equal((await pool.query("SELECT * FROM accounts")).rowCount, 5);
  } finally {
    await pool.query(
      "UPDATE employees SET employeehashedsaltedpassword = $1 WHERE employeeusername = 'analyst_user'",
      ["$2a$10$5aBDMtqcyF2rqep.RtCnI.vProRwMEidkf//iG.Qb8l0Vo/AOIT.a"]
    );
  }
});

test("upgrades the legacy placeholder employee without resetting investor data", async () => {
  await pool.query("DROP TABLE access_tokens_employee");
  await pool.query("DROP TABLE refresh_tokens_employee");
  await pool.query("DELETE FROM employees WHERE employeeusername = 'analyst_user'");
  await pool.query(
    "UPDATE employees SET employeesalt = 'emp_salt1', employeehashedsaltedpassword = 'emp_hashed_1' WHERE employeeusername = 'admin_user'"
  );
  await pool.query(await readFile(path.resolve(__dirname, "..", "..", "scripts", "employee-auth.sql"), "utf8"));
  assert.equal((await login()).status, 200);
  assert.equal((await login("analyst_user", "AnalystDemo123!")).status, 200);
  assert.equal((await pool.query("SELECT * FROM accounts")).rowCount, 5);
  assert.equal((await pool.query("SELECT * FROM trade_request")).rowCount, 7);
});

test("a token storage failure rolls back the entire session and reports an error", async t => {
  const loggedError = t.mock.method(console, "error", () => {});
  const before = await pool.query("SELECT COUNT(*) FROM refresh_tokens_employee");
  await pool.query(`
    CREATE FUNCTION fail_access_token() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN RAISE EXCEPTION 'intentional test failure'; END; $$;
    CREATE TRIGGER reject_test_token BEFORE INSERT ON access_tokens_employee
    FOR EACH ROW EXECUTE FUNCTION fail_access_token();
  `);
  try {
    assert.equal((await login()).status, 500);
    const after = await pool.query("SELECT COUNT(*) FROM refresh_tokens_employee");
    assert.equal(after.rows[0].count, before.rows[0].count);
    assert.equal(loggedError.mock.callCount(), 1);
  } finally {
    await pool.query("DROP TRIGGER reject_test_token ON access_tokens_employee; DROP FUNCTION fail_access_token()");
  }
});
