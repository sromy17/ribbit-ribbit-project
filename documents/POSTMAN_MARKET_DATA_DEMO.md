# Postman Market Data Demo

This workflow keeps API keys, database passwords, and JWTs out of the recorded
screen. Store them as local Postman environment values and do not export the
environment or include screenshots of its value editor.

## 1. Start the services

Start PostgreSQL and Kafka as usual. Start the auth stub from
`shared/auth-stub`; its `.env` remains local and is not part of the recording.

Start Spring with the values in your shell or local IDE launch configuration:

```text
FAUXNANCE_API_KEY=<local secret>
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
MARKET_DATA_KAFKA_ENABLED=true
SERVER_PORT=8081
```

Do not put real values in a Postman collection or commit them to the project.

## 2. Create a Postman environment

Create an environment named `Ribbit Local Demo` with these variables:

| Variable | Initial value | Current value | Type |
| --- | --- | --- | --- |
| `authBaseUrl` | `http://localhost:4001` | same | default |
| `apiBaseUrl` | `http://localhost:8081` | same | default |
| `demoUsername` | `demo_recorder` | same | default |
| `demoPassword` | blank | local password | secret |
| `jwtToken` | blank | blank | secret |
| `accountId` | `101` | same | default |

The auth stub port must match `AUTH_STUB_PORT` in its local `.env`.

## 3. Register and log in

Create a `POST {{authBaseUrl}}/register` request:

```json
{
  "username": "{{demoUsername}}",
  "password": "{{demoPassword}}"
}
```

Create a `POST {{authBaseUrl}}/login` request with the same body. In the
request's **Tests** tab, store the token without printing it:

```javascript
const body = pm.response.json();
pm.environment.set("jwtToken", body.token);
```

After this, avoid displaying the login response while recording.

## 4. Add authorization once

At the collection level, choose **Authorization** -> **Bearer Token** and set
the token to `{{jwtToken}}`. Requests in the collection inherit this setting.

## 5. Show live market data

Create a `GET {{apiBaseUrl}}/market-data/quotes/AAPL` request. The response is
safe to show and looks like:

```json
{
  "symbol": "AAPL",
  "price": 150.25,
  "eventEpochMillis": 1792756800000,
  "source": "fauxnance",
  "quoteAtUtc": null,
  "currency": null,
  "priceType": null
}
```

The first request fetches from Fauxnance, caches the snapshot, and publishes it
to Kafka. A subsequent `GET {{apiBaseUrl}}/market-data/quotes` displays the
currently cached snapshots.
`eventEpochMillis` is the actual application receipt time (the value above is
illustrative), not an upstream market timestamp. Optional upstream quote time,
currency and price type are preserved when supplied, otherwise left unknown.
Cache/Kafka delivery does not replace the original source or receipt time.

If Fauxnance returns `401` or `502`, the Spring process needs a valid
`FAUXNANCE_API_KEY`. The key is never sent from Postman; it is held by Spring.

## 6. Show the Kafka handoff

While recording, keep Kafka consumption in a separate background process or
skip it from the screen. To verify the handoff outside the recording:

```bash
docker exec -it ribbit-kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic market.quotes \
  --from-beginning
```

The Postman request's response demonstrates the application-facing data. The
Kafka consumer verifies the asynchronous handoff without exposing credentials.