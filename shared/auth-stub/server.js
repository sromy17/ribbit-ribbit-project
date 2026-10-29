const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");
const dotenv = require("dotenv");
const { Pool } = require("pg");

dotenv.config();

const app = express();
app.use(express.json());

const PORT = Number(process.env.AUTH_STUB_PORT || 4000);
const SHARED_SECRET = process.env.JWT_SHARED_SECRET;
const JWT_EXPIRATION_SECONDS = Number(process.env.JWT_EXPIRATION_SECONDS || 1200);
const REFRESH_TOKEN_VALID_DAYS = Number(process.env.REFRESH_TOKEN_VALID_DAYS || 7);
const DB_URL = process.env.DB_OLTP_URL || process.env.DB_URL;
const DB_USER = process.env.DB_OLTP_USER || process.env.DB_USER;
const DB_PASSWORD = process.env.DB_OLTP_PASSWORD || process.env.DB_PASSWORD;
const DB_SSL = (process.env.DB_SSL || "false").toLowerCase() === "true";

function extractBearerToken(authHeader) {
  if (typeof authHeader !== "string") {
    return undefined;
  }

  const match = authHeader.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : undefined;
}

function generateRefreshTokenValue() {
  return crypto.randomBytes(32).toString("base64url");
}

function hashToken(token) {
  return crypto.createHash("sha256").update(token, "utf8").digest("hex");
}

async function allocateId(table, column) {
  const kinds = {refresh_tokens: "REFRESH_TOKEN", access_tokens: "ACCESS_TOKEN"};
  const kind = kinds[table];
  if (!kind) throw new Error("Unsupported token ID allocation");
  await pool.query("CREATE TABLE IF NOT EXISTS financial_id_allocations (kind VARCHAR(20) PRIMARY KEY, last_id BIGINT NOT NULL)");
  await pool.query("INSERT INTO financial_id_allocations(kind,last_id) VALUES($1,0) ON CONFLICT DO NOTHING", [kind]);
  const result = await pool.query(`UPDATE financial_id_allocations SET last_id=GREATEST(last_id,(SELECT COALESCE(MAX(${column}),0) FROM ${table}))+1 WHERE kind=$1 RETURNING last_id`, [kind]);
  return Number(result.rows[0].last_id);
}

function normalizeDbUrl(url) {
  if (!url) {
    return undefined;
  }

  if (url.startsWith("jdbc:")) {
    return url.substring("jdbc:".length);
  }

  return url;
}

function createPoolConfig() {
  const config = {
    ssl: DB_SSL ? {
      rejectUnauthorized: true,
      ...(process.env.DB_SSL_CA ? {
        ca: require('node:fs').readFileSync(process.env.DB_SSL_CA, 'utf8')
      } : {})
    } : false
  };

  const normalized = normalizeDbUrl(DB_URL);
  if (normalized) {
    const parsed = new URL(normalized);
    config.host = parsed.hostname;
    config.port = parsed.port ? Number(parsed.port) : 5432;
    config.database = parsed.pathname.replace(/^\//, "");

    if (!DB_USER && parsed.username) {
      config.user = parsed.username;
    }

    if (!DB_PASSWORD && parsed.password) {
      config.password = parsed.password;
    }
  }

  if (typeof DB_USER === "string" && DB_USER.trim().length > 0) {
    config.user = DB_USER;
  }

  if (typeof DB_PASSWORD === "string" && DB_PASSWORD.trim().length > 0) {
    config.password = DB_PASSWORD;
  }

  return config;
}

const poolConfig = createPoolConfig();

const pool = new Pool(poolConfig);

if (!SHARED_SECRET || SHARED_SECRET.length < 32) {
  throw new Error("JWT_SHARED_SECRET must be set and at least 32 characters long.");
}

if (typeof DB_USER === "string" && DB_USER.trim().length > 0) {
  if (typeof DB_PASSWORD !== "string" || DB_PASSWORD.length === 0) {
    throw new Error("DB_PASSWORD must be set when DB_USER is provided.");
  }
}

function issueToken(user) {
  const payload = {
    sub: user.username,
    uid: user.userId,
    roles: user.roles
  };

  return jwt.sign(payload, SHARED_SECRET, {
    algorithm: "HS256",
    jwtid: crypto.randomUUID(),
    expiresIn: JWT_EXPIRATION_SECONDS
  });
}

async function allocateRefreshToken() {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const candidate = generateRefreshTokenValue();
    const existing = await pool.query(
      "SELECT 1 FROM refresh_tokens WHERE refreshtokenhash = $1",
      [hashToken(candidate)]
    );
    if (existing.rowCount === 0) {
      return candidate;
    }
  }

  throw new Error("Unable to allocate a refresh token");
}

async function storeTokenPair(userId, username, refreshToken) {
  const refreshTokenId = await allocateId("refresh_tokens", "refreshtokenid");
  const now = new Date();
  const refreshExpires = new Date(now.getTime() + REFRESH_TOKEN_VALID_DAYS * 24 * 60 * 60 * 1000);
  await pool.query(
    `INSERT INTO refresh_tokens
      (refreshtokenid, userid, refreshtokenhash, dateissued, dateexpires, isrevoked)
     VALUES ($1, $2, $3, $4, $5, FALSE)`,
    [refreshTokenId, userId, hashToken(refreshToken), now, refreshExpires]
  );

  const accessToken = issueToken({
    userId,
    username,
    roles: ["MISSION_OPERATOR"]
  });
  const accessTokenId = await allocateId("access_tokens", "accesstokenid");
  await pool.query(
    `INSERT INTO access_tokens
      (accesstokenid, refreshtokenid, accesstokenhash, dateissued, dateexpires, isrevoked)
     VALUES ($1, $2, $3, $4, $5, FALSE)`,
    [
      accessTokenId,
      refreshTokenId,
      hashToken(accessToken),
      now,
      new Date(now.getTime() + JWT_EXPIRATION_SECONDS * 1000)
    ]
  );

  return { accessToken, refreshToken, refreshTokenId };
}

async function generateUserId(client) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const candidate = Math.floor(Math.random() * 900000) + 100000;
    const exists = await client.query(
      "SELECT 1 FROM users WHERE userid = $1",
      [candidate]
    );

    if (exists.rowCount === 0) {
      return candidate;
    }
  }

  throw new Error("Unable to allocate a user id");
}

async function ensureDatabaseConnection() {
  const connection = await pool.connect();
  try {
    await connection.query("SELECT 1");
  } finally {
    connection.release();
  }
}

app.get("/health", async (_req, res) => {
  try {
    await pool.query("SELECT 1");
    res.json({ status: "ok" });
  } catch {
    res.status(503).json({ status: "unavailable" });
  }
});

app.post("/register", async (req, res) => {
  const { username, password, firstName, lastName } = req.body || {};

  if (!username || !password || !firstName || !lastName) {
    return res.status(400).json({
      message: "username, password, firstName, and lastName are required"
    });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const existing = await client.query(
      "SELECT userid FROM users WHERE username = $1",
      [username]
    );
    if (existing.rowCount > 0) {
      await client.query("ROLLBACK");
      return res.status(409).json({ message: "Username already exists" });
    }

    const nextUserId = await generateUserId(client);
    const passwordHash = await bcrypt.hash(password, 10);

    await client.query(
      `INSERT INTO users
        (userid, username, salt, userhashedsaltedpassword, first_name, last_name, registered_at_utc)
       VALUES ($1, $2, $3, $4, $5, $6, CURRENT_TIMESTAMP)`,
      [nextUserId, username, "bcrypt", passwordHash, firstName, lastName]
    );

    await client.query("COMMIT");
    return res.status(201).json({
      userId: nextUserId,
      username,
      firstName,
      lastName,
      message: "User registered. Login to receive a JWT."
    });
  } catch (error) {
    await client.query("ROLLBACK");
    console.error("Registration error", error);
    return res.status(500).json({ message: "Registration failed" });
  } finally {
    client.release();
  }
});

app.post("/login", async (req, res) => {
  const { username, password } = req.body || {};

  if (!username || !password) {
    return res.status(400).json({ message: "username and password are required" });
  }

  try {
    const result = await pool.query(
      `SELECT userid, username, first_name, last_name, userhashedsaltedpassword
       FROM users WHERE username = $1`,
      [username]
    );

    if (result.rowCount === 0) {
      return res.status(401).json({ message: "Invalid username or password" });
    }

    const user = result.rows[0];
    const storedPassword = user.userhashedsaltedpassword;
    const passwordMatches = await bcrypt.compare(password, storedPassword);

    if (!passwordMatches) {
      return res.status(401).json({ message: "Invalid username or password" });
    }

    const refreshToken = await allocateRefreshToken();
    const tokenPair = await storeTokenPair(
      Number(user.userid),
      user.username,
      refreshToken
    );

    return res.json({
      token: tokenPair.accessToken,
      tokenType: "Bearer",
      expiresInSeconds: JWT_EXPIRATION_SECONDS,
      refreshToken,
      userId: Number(user.userid),
      username: user.username,
      firstName: user.first_name,
      lastName: user.last_name
    });
  } catch (error) {
    console.error("Login error", error);
    return res.status(500).json({ message: "Login failed" });
  }
});

app.post("/refresh", async (req, res) => {
  const refreshToken = typeof req.body?.refreshToken === "string"
    ? req.body.refreshToken.trim()
    : "";

  if (!refreshToken) {
    return res.status(400).json({ message: "refreshToken is required" });
  }

  try {
    const result = await pool.query(
      `SELECT rt.refreshtokenid, rt.userid, rt.dateexpires, rt.isrevoked, u.username
       FROM refresh_tokens rt
       INNER JOIN users u ON u.userid = rt.userid
       WHERE rt.refreshtokenhash = $1`,
      [hashToken(refreshToken)]
    );

    if (result.rowCount === 0) {
      return res.status(401).json({ message: "Invalid refresh token" });
    }

    const session = result.rows[0];
    if (session.isrevoked || new Date(session.dateexpires) <= new Date()) {
      await pool.query(
        "UPDATE refresh_tokens SET isrevoked = TRUE WHERE refreshtokenid = $1",
        [session.refreshtokenid]
      );
      return res.status(401).json({ message: "Refresh token expired" });
    }

    await pool.query(
      "UPDATE refresh_tokens SET isrevoked = TRUE WHERE refreshtokenid = $1",
      [session.refreshtokenid]
    );
    const newRefreshToken = await allocateRefreshToken();
    const tokenPair = await storeTokenPair(
      Number(session.userid),
      session.username,
      newRefreshToken
    );

    return res.json({
      token: tokenPair.accessToken,
      tokenType: "Bearer",
      expiresInSeconds: JWT_EXPIRATION_SECONDS,
      refreshToken: newRefreshToken
    });
  } catch (error) {
    console.error("Refresh error", error);
    return res.status(500).json({ message: "Token refresh failed" });
  }
});

app.post("/logout", async (req, res) => {
  const tokenFromHeader = extractBearerToken(req.headers.authorization);
  const tokenFromBody = typeof req.body?.accessToken === "string" ? req.body.accessToken.trim() : "";
  const token = tokenFromBody || tokenFromHeader;

  if (!token) {
    return res.status(400).json({ message: "accessToken is required" });
  }

  try {
    jwt.verify(token, SHARED_SECRET, {
      algorithms: ["HS256"],
      ignoreExpiration: true
    });

    const accessTokenHash = hashToken(token);
    const result = await pool.query(
      `SELECT at.accesstokenid, at.refreshtokenid
       FROM access_tokens at
       WHERE at.accesstokenhash = $1
         AND at.isrevoked = FALSE
         AND at.dateexpires > CURRENT_TIMESTAMP`,
      [accessTokenHash]
    );
    if (result.rowCount === 0) {
      return res.status(401).json({ message: "Invalid access token" });
    }

    const accessTokenRecord = result.rows[0];
    await pool.query(
      "UPDATE access_tokens SET isrevoked = TRUE WHERE accesstokenid = $1",
      [accessTokenRecord.accesstokenid]
    );
    await pool.query(
      "UPDATE refresh_tokens SET isrevoked = TRUE WHERE refreshtokenid = $1",
      [accessTokenRecord.refreshtokenid]
    );
    return res.status(204).send();
  } catch (error) {
    return res.status(401).json({ message: "Invalid access token" });
  }
});

ensureDatabaseConnection().then(() => {
  app.listen(PORT, () => {
    console.log(`Auth stub running on http://localhost:${PORT}`);
  });
}).catch((error) => {
  console.error("Failed to connect to database", error);
  process.exit(1);
});
