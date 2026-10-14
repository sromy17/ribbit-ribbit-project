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
const JWT_EXPIRATION_SECONDS = Number(process.env.JWT_EXPIRATION_SECONDS || 3600);
const DB_URL = process.env.DB_URL;
const DB_USER = process.env.DB_USER;
const DB_PASSWORD = process.env.DB_PASSWORD;
const DB_SSL = (process.env.DB_SSL || "false").toLowerCase() === "true";

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

    return res.json({
      token,
      tokenType: "Bearer",
      expiresInSeconds: JWT_EXPIRATION_SECONDS
    });
  } catch (error) {
    console.error("Login error", error);
    return res.status(500).json({ message: "Login failed" });
  }
});

app.post("/logout", (_req, res) => {
  return res.status(204).send();
});

ensureDatabaseConnection().then(() => {
  app.listen(PORT, () => {
    console.log(`Auth stub running on http://localhost:${PORT}`);
  });
}).catch((error) => {
  console.error("Failed to connect to database", error);
  process.exit(1);
});
