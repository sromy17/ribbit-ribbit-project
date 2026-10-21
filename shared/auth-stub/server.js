const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const dotenv = require("dotenv");
const { Pool } = require("pg");

dotenv.config();

const app = express();
app.use(express.json());

const PORT = Number(process.env.AUTH_STUB_PORT || 4000);
const SHARED_SECRET = process.env.JWT_SHARED_SECRET;
const JWT_EXPIRATION_SECONDS = Number(process.env.JWT_EXPIRATION_SECONDS || 1200);
const REFRESH_TOKEN_VALID_DAYS = Number(process.env.REFRESH_TOKEN_VALID_DAYS || 7);
const DB_URL = process.env.DB_URL;
const DB_USER = process.env.DB_USER;
const DB_PASSWORD = process.env.DB_PASSWORD;
const DB_SSL = (process.env.DB_SSL || "false").toLowerCase() === "true";

function formatDateOnly(date) {
  return date.toISOString().slice(0, 10);
}

function extractBearerToken(authHeader) {
  if (typeof authHeader !== "string") {
    return undefined;
  }

  const match = authHeader.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : undefined;
}

function generateRefreshTokenValue() {
  return Math.floor(Math.random() * 900000000) + 100000000;
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
    ssl: DB_SSL ? { rejectUnauthorized: false } : false
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
    expiresIn: JWT_EXPIRATION_SECONDS
  });
}

async function allocateRefreshToken() {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const candidate = generateRefreshTokenValue();
    const existing = await pool.query(
      "SELECT 1 FROM user_sessions WHERE refreshtoken = $1",
      [candidate]
    );
    if (existing.rowCount === 0) {
      return candidate;
    }
  }

  throw new Error("Unable to allocate a refresh token");
}

async function upsertUserSession(userId, refreshToken, dateIssued) {
  await pool.query(
    `INSERT INTO user_sessions (userid, refreshtoken, dateissued)
     VALUES ($1, $2, $3)
     ON CONFLICT (userid)
     DO UPDATE SET refreshtoken = EXCLUDED.refreshtoken, dateissued = EXCLUDED.dateissued`,
    [userId, refreshToken, dateIssued]
  );
}

function isRefreshTokenExpired(dateIssued) {
  const issuedAt = new Date(dateIssued);
  const ageMs = Date.now() - issuedAt.getTime();
  const maxAgeMs = REFRESH_TOKEN_VALID_DAYS * 24 * 60 * 60 * 1000;
  return ageMs > maxAgeMs;
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

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.post("/register", async (req, res) => {
  const { username, password } = req.body || {};

  if (!username || !password) {
    return res.status(400).json({ message: "username and password are required" });
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
      "INSERT INTO users (userid, username, salt, userhashedsaltedpassword) VALUES ($1, $2, $3, $4)",
      [nextUserId, username, "bcrypt", passwordHash]
    );

    await client.query("COMMIT");
    return res.status(201).json({
      userId: nextUserId,
      username,
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
      "SELECT userid, username, userhashedsaltedpassword FROM users WHERE username = $1",
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

    const token = issueToken({
      userId: Number(user.userid),
      username: user.username,
      roles: ["MISSION_OPERATOR"]
    });
    const refreshToken = await allocateRefreshToken();
    const dateIssued = formatDateOnly(new Date());
    await upsertUserSession(Number(user.userid), refreshToken, dateIssued);

    return res.json({
      token,
      tokenType: "Bearer",
      expiresInSeconds: JWT_EXPIRATION_SECONDS,
      refreshToken,
      userId: Number(user.userid),
      username: user.username
    });
  } catch (error) {
    console.error("Login error", error);
    return res.status(500).json({ message: "Login failed" });
  }
});

app.post("/refresh", async (req, res) => {
  const refreshToken = Number(req.body?.refreshToken);

  if (!Number.isInteger(refreshToken)) {
    return res.status(400).json({ message: "refreshToken is required" });
  }

  try {
    const result = await pool.query(
      `SELECT us.userid, us.refreshtoken, us.dateissued, u.username
       FROM user_sessions us
       INNER JOIN users u ON u.userid = us.userid
       WHERE us.refreshtoken = $1`,
      [refreshToken]
    );

    if (result.rowCount === 0) {
      return res.status(401).json({ message: "Invalid refresh token" });
    }

    const session = result.rows[0];
    if (isRefreshTokenExpired(session.dateissued)) {
      await pool.query("DELETE FROM user_sessions WHERE userid = $1", [Number(session.userid)]);
      return res.status(401).json({ message: "Refresh token expired" });
    }

    const newAccessToken = issueToken({
      userId: Number(session.userid),
      username: session.username,
      roles: ["MISSION_OPERATOR"]
    });
    const newRefreshToken = await allocateRefreshToken();
    const dateIssued = formatDateOnly(new Date());
    await upsertUserSession(Number(session.userid), newRefreshToken, dateIssued);

    return res.json({
      token: newAccessToken,
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
    const payload = jwt.verify(token, SHARED_SECRET, {
      algorithms: ["HS256"],
      ignoreExpiration: true
    });

    const userId = Number(payload.uid);
    if (!Number.isInteger(userId)) {
      return res.status(400).json({ message: "Invalid access token payload" });
    }

    const revokedRefreshToken = await allocateRefreshToken();
    const revokeFromDate = new Date();
    revokeFromDate.setDate(revokeFromDate.getDate() + 1);
    await upsertUserSession(userId, revokedRefreshToken, formatDateOnly(revokeFromDate));
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
