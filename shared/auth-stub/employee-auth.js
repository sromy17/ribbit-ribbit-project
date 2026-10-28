const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");

const hashToken = token => crypto.createHash("sha256").update(token, "utf8").digest("hex");
const supportedRole = role => role === "ADMIN" || role === "ANALYST";

function createEmployeeAuth({ pool, secret, expiresInSeconds, refreshValidDays }) {
  const router = express.Router();

  async function storeSession(client, employee) {
    const refreshToken = crypto.randomBytes(32).toString("base64url");
    const refresh = await client.query(
      `INSERT INTO refresh_tokens_employee (employeeid, refreshtokenhash, dateexpires)
       VALUES ($1, $2, $3) RETURNING refreshtokenid`,
      [employee.employeeid, hashToken(refreshToken),
        new Date(Date.now() + refreshValidDays * 86400000)]
    );
    const token = jwt.sign({
      sub: employee.employeeusername,
      eid: Number(employee.employeeid),
      principal_type: "employee",
      roles: [employee.employeeroles]
    }, secret, { algorithm: "HS256", expiresIn: expiresInSeconds, jwtid: crypto.randomUUID() });
    await client.query(
      `INSERT INTO access_tokens_employee (refreshtokenid, accesstokenhash, dateexpires)
       VALUES ($1, $2, $3)`,
      [refresh.rows[0].refreshtokenid, hashToken(token), new Date(Date.now() + expiresInSeconds * 1000)]
    );
    return { body: {
      token, refreshToken, tokenType: "Bearer", expiresInSeconds,
      userId: Number(employee.employeeid), username: employee.employeeusername,
      firstName: "", lastName: "", role: employee.employeeroles.toLowerCase()
    } };
  }

  async function transaction(res, operation) {
    let client;
    try {
      client = await pool.connect();
      await client.query("BEGIN");
      const result = await operation(client);
      await client.query("COMMIT");
      return res.status(result.status || 200).send(result.body);
    } catch (error) {
      console.error("Employee authentication failed", error);
      if (client) {
        try {
          await client.query("ROLLBACK");
        } catch (rollbackError) {
          console.error("Employee authentication rollback failed", rollbackError);
        }
      }
      if (!res.headersSent) {
        return res.status(500).json({ message: "Employee authentication failed" });
      }
    } finally {
      client?.release();
    }
  }

  router.post("/login", async (req, res) => {
    const { username, password } = req.body || {};
    if (typeof username !== "string" || !username.trim()
        || typeof password !== "string" || !password) {
      return res.status(400).json({ message: "username and password are required" });
    }
    return transaction(res, async client => {
      const result = await client.query(
        `SELECT employeeid, employeeusername, employeehashedsaltedpassword, employeeroles
         FROM employees WHERE employeeusername = $1`, [username.trim()]
      );
      const employee = result.rows[0];
      if (!employee || !supportedRole(employee.employeeroles)
          || !await bcrypt.compare(password, employee.employeehashedsaltedpassword)) {
        return { status: 401, body: { message: "Invalid username or password" } };
      }
      return storeSession(client, employee);
    });
  });

  router.post("/refresh", async (req, res) => {
    const refreshToken = req.body?.refreshToken;
    if (typeof refreshToken !== "string" || !refreshToken.trim()) {
      return res.status(400).json({ message: "refreshToken is required" });
    }
    return transaction(res, async client => {
      const result = await client.query(
        `SELECT rt.refreshtokenid, rt.isrevoked, rt.dateexpires,
                e.employeeid, e.employeeusername, e.employeeroles
         FROM refresh_tokens_employee rt
         JOIN employees e ON e.employeeid = rt.employeeid
         WHERE rt.refreshtokenhash = $1 FOR UPDATE OF rt`, [hashToken(refreshToken.trim())]
      );
      const session = result.rows[0];
      if (!session || session.isrevoked || new Date(session.dateexpires) <= new Date()
          || !supportedRole(session.employeeroles)) {
        return { status: 401, body: { message: "Invalid or expired employee refresh token" } };
      }
      await client.query(
        "UPDATE refresh_tokens_employee SET isrevoked = TRUE WHERE refreshtokenid = $1",
        [session.refreshtokenid]
      );
      await client.query(
        "UPDATE access_tokens_employee SET isrevoked = TRUE WHERE refreshtokenid = $1",
        [session.refreshtokenid]
      );
      return storeSession(client, session);
    });
  });

  router.post("/logout", async (req, res) => {
    const token = req.body?.accessToken;
    if (typeof token !== "string" || !token) {
      return res.status(400).json({ message: "accessToken is required" });
    }
    try {
      const claims = jwt.verify(token, secret, { algorithms: ["HS256"], ignoreExpiration: true });
      if (claims.principal_type !== "employee") {
        return res.status(401).json({ message: "Invalid employee access token" });
      }
    } catch (error) {
      if (!(error instanceof jwt.JsonWebTokenError)) {
        throw error;
      }
      return res.status(401).json({ message: "Invalid employee access token" });
    }
    return transaction(res, async client => {
      const result = await client.query(
        `UPDATE refresh_tokens_employee SET isrevoked = TRUE
         WHERE refreshtokenid IN (
           SELECT refreshtokenid FROM access_tokens_employee WHERE accesstokenhash = $1
         ) RETURNING refreshtokenid`, [hashToken(token)]
      );
      if (!result.rowCount) {
        return { status: 401, body: { message: "Invalid employee access token" } };
      }
      await client.query(
        "UPDATE access_tokens_employee SET isrevoked = TRUE WHERE refreshtokenid = $1",
        [result.rows[0].refreshtokenid]
      );
      return { status: 204 };
    });
  });

  return router;
}

module.exports = { createEmployeeAuth };
