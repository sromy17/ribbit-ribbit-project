# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.1.

## Development server

To start a local development server, run:

```bash
npm start
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Connecting to the local backend

The frontend uses the real services by default. `npm start` loads
[`proxy.conf.json`](./proxy.conf.json), forwarding `/api/*` to the Spring
trading service at `http://localhost:8081` and `/auth/*` to the Node auth stub
at `http://localhost:4000`.

Start the services in this order:

```bash
# Terminal 1: database and Spring
docker compose up -d postgres
export JWT_SHARED_SECRET='use-the-same-secret-for-auth-and-spring-32-chars-minimum'
mvn spring-boot:run

# Terminal 2: authentication stub, using the same database and JWT secret
cd shared/auth-stub
npm install
npm start

# Terminal 3: Angular
cd frontend
npm install
npm start
```

Set `FAUXNANCE_API_KEY` if the Fauxnance deployment requires one. The Spring
service calls Fauxnance server-side when an order is submitted, so the browser
does not need the Fauxnance API key.

The frontend stores the auth-stub bearer token in `localStorage` and attaches
it to `/api` requests. The investor dashboard resolves the user's account
through `GET /api/accounts/user/{userId}` instead of assuming that a user ID
is an account ID.

## Code scaffolding

### Stock detail charts

The Graphs view uses a single "LEAP Trader Pro Heatmap" heading.
Tile sizes use synthetic rank weights, not reported market capitalization.

Selecting a heatmap tile opens the official TradingView chart in its detail panel.
Details open in a native modal dialog without resizing the heatmap. Close with
Escape, the close button, or the backdrop before selecting another tile.
Trade This Stock opens a compact Quick Trade dialog over the stock details,
without leaving Graphs or recreating the chart. It defaults to BUY, one share,
and the tile's current price, with a live subtotal and estimated fee (0.1%, $1
minimum). SELL shows estimated net proceeds after fees.
`PortfolioStateService.executeTrade(accountNumber, trade)` submits to the existing
backend order endpoint and reconciles cash, holdings, orders, and history from
the server. No local duplicate cash deduction is made. Confirmed execution closes
only the ticket and displays a success banner using the actual execution result.
Rejected orders and synchronization failures are shown explicitly; an uncertain
submission instructs the user to check History before retrying. No automatic order
retries are performed. Closing/navigating away does not cancel server reconciliation.
The widget loads `https://s3.tradingview.com/tv.js` once on demand and uses the
selected ticker/exchange, dark mode, and daily candles. Closing the panel destroys
the widget. The surrounding heatmap remains available.

TradingView requires an internet connection and permits users to change symbols
inside its own chart. Quotes and exchange delays depend on TradingView's coverage;
the widget is not the platform's order execution price source. Only the public
stock symbol is supplied to widget configuration, not account balances or holdings.
Script failures display a retry control; browser content blockers may need to
allow TradingView.

Company news uses authenticated same-origin backend endpoints `/api/market/news` and `/api/market/company-news`. Set `FINNHUB_API_KEY` on Spring only; no provider key belongs in Angular environment files. The backend validates symbols/date ranges, applies provider timeouts and a short bounded cache. Missing configuration or provider failure returns 503; the UI shows unavailable instead of manufactured headlines. Polling retries after transient failures.

News sector tabs share the general Finnhub feed (`category=general`) and filter
it locally. Switching sector or ticker cancels the old view subscription to
prevent stale polling responses from overwriting the selected feed. Missing
article images continue to use the deterministic editorial image pool.

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Application structure

The investor sidebar fills the remaining viewport beneath the header, flush with
the left and bottom edges. Navigation scrolls independently on short screens,
while the full-width Active Session footer stays visible. The footer is
informational; Logout remains available in the top header.

### Help

The Help tab is a simple FAQ with four native accordions:
orders, portfolio history, market charts, and local data privacy. It uses OnPush
change detection and does not fetch telemetry or collect support tickets.
Previously saved local tickets are left untouched.

The Angular application is organized by responsibility and feature so the
course topics map directly to the code:

```text
src/app/
├── core/
│   ├── guards/       # Authenticated flows and role checks
│   └── services/     # Singleton API and authentication services
├── features/
│   ├── auth/login/   # Login form and navigation
│   └── dashboard/
│       ├── investor/ # Portfolio, orders, and trade history
│       ├── admin/    # Platform administration views
│       └── analyst/  # Market trends and heatmap views
├── app.routes.ts     # Route configuration and route data
└── app.config.ts     # Application providers
```

Components and templates stay with their feature, services handle HTTP and
dependency injection, forms stay in the login/investor components, and
authenticated routing is centralized in the reusable `AuthGuard`.

## Running unit tests

### Portfolio account state

`PortfolioStateService` publishes account-specific RxJS state for Portfolio,
Trade, and History. Orders still execute on the backend; after confirmation,
cash, holdings, and orders are refreshed together. Fees and average cost remain
backend-authoritative rather than being applied a second time in the browser.
Live quotes value holdings; unavailable quotes explicitly warn that cost basis
is being used.

Confirmed state and observed balance snapshots persist under
`leap_account_<accountId>` in localStorage. Cached balances are shown on return,
then reconciled with the server. Failed synchronization does not erase the cache.
This browser cache is not an authorization mechanism or a replacement for the
server ledger.

The balance chart uses recorded snapshots only, including a flat current balance
for newly observed accounts. Asset Allocation above Holdings uses the same
published state: shares times current price for each position, plus cash. Legend
percentages use total portfolio value; unavailable quotes retain the visible
estimated-valuation notice.

The history chart cannot reconstruct returns before the browser
first observed the account. Daily snapshots are captured while the dashboard is
open (checked every minute) or on the next account refresh; no background job
runs when the browser is closed. Clearing browser storage removes local history.

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
