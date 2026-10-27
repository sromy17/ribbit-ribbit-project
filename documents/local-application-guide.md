# Persistent local application

Run `bash scripts/start-local.sh` from the repository root. Open http://127.0.0.1:4200. The launcher starts the backend on 8082, authentication on 4000, and Angular on 4200 with matching proxies. PostgreSQL must already be available; existing databases are preserved. Ctrl-C stops the app services, leaving the data intact. Service logs and process IDs are in the ignored `.local/` directory.

The older port 14300 belongs to the disposable integration demo. Use port 4200 for persistent work.

## Login

Use **User sign in** for `local_trader`, and **Staff sign in** for `local_admin`, `local_operations`, or `local_analyst`. Generated passwords are in `.local/staff-credentials.txt` (owner-readable, excluded from Git). Existing users and their permissions were preserved. Existing accounts without a currency were assigned USD with stakeholder approval.

The separate local trader has a USD account funded with 1,000 educational dollars through the local wallet. A one-share AAPL verification purchase and sale are retained in its history; after both 1 USD fees the account holds 998 USD and no open position. This account is separate from existing customers. The wallet and trade execution are paper accounting: there is no brokerage execution or banking integration. Quotes come from the configured Fauxnance provider; its educational, delayed/cache status must not be treated as an exchange execution feed.

## Database inspection

Open http://localhost:5050 and sign into your existing pgAdmin account. The registered server **Local OLTP and OLAP** connects through `host.docker.internal:5432`; use your existing PostgreSQL password from the root `.env` if prompted. Refresh **Databases** to see `ribbit-oltp` and `ribbit-olap` separately. The launcher also registers the versioned US equity catalog used by Market, including its declared USD/share conventions; ambiguous tickers are left for explicit venue mapping. This configures instruments without inventing old execution evidence. The former database stores customer accounts, orders, fills and audit evidence; the latter stores published reporting facts and report versions. The original OLTP backup is `.local/backups/pre-persistent-integration.dump`.

An empty Docker volume is initialized by Compose. Existing volumes are upgraded additively by the local launcher; initialization scripts do not rerun automatically on existing volumes. Do not delete volumes to apply a migration.

## Verify the journey

1. Sign in as `local_trader`, select its trading account, and inspect the portfolio and History. History shows committed fills and execution value; old date-only records remain labelled as such.
2. Open Market to fetch provider prices. Select Trade, buy a quantity within the available balance, and check the resulting holding, fee and cash balance. Selling more than the holding or buying beyond available funds must fail without changing financial records.
3. Sign in as `local_operations` or `local_admin`. Look up the trader/account and investigate its order ID. The recorded lifecycle, fill, cash and holding movements should agree with customer History. Only admins can work dispute cases or manage staff.
4. Sign in as `local_analyst`. Select the actual execution period. The local refresh runs approximately every ten seconds; check the published dataset and freshness label before comparing totals. Analytics reads the separate OLAP database and never substitutes trading queries on reporting failure.
5. Toggle dark mode, navigate between workspaces and reload. Stop/restart the launcher and repeat the history lookup: records and balances must remain.

Legacy transactions lack some original timestamps, fees and execution metadata. They remain visible, with reporting incompleteness warnings; the migration does not invent that evidence. Small client segment reporting remains withheld because its disclosure policy has not been approved. Metric approval, retention policy and production load targets still require stakeholder sign-off.

For another local setup, explicitly run `node scripts/provision-local-accounts.cjs` after starting/migrating PostgreSQL to create separate accounts. `--legacy-usd` additionally assigns USD to currency-less accounts and should only be used with the account owner's approval. This command does not reset passwords or grant permissions to existing usernames.

## News and market appearance

Market and company news use public Yahoo Finance RSS when no Finnhub key is configured; an optional server-side `FINNHUB_API_KEY` selects Finnhub instead. The backend validates dates and HTTPS article links, caches results for a minute, and reports upstream failures without generating articles. Headlines retain the feed's source and publication time. Stock searches filter the available company feed to the selected date range.

The Market treemap sizes tiles by the absolute daily percentage move: large gains and losses receive more area, with color showing direction. Moves below 1% receive a minimum size for readability. Labels retain the actual uncapped percentage and price; area does not represent market capitalization. The layout recalculates from the available screen width and height so all loaded stocks fit on one page. Small tiles prioritize tickers and percentage changes; selecting a tile opens full details. Customer, admin and analytics workspaces share the Ribbit Trader Pro emerald/slate Tailwind theme in light and dark modes, including help, news, charts and stock dialogs. News, portfolio and other workspace views grow with the page and use the browser scrollbar. News filters retain distinct muted category tints. Stock-details, quick-trade and investor form pop-ups are centered within the viewport, with internal scrolling for tall content.


## Ribbit Trader Pro entrance

The root URL opens Ribbit Trader Pro, credited “by Ribbit Ribbit,” using the shared Tailwind light and dark theme. User sign-in opens the customer form; Staff entrance opens the staff form. `/login` still offers both paths separately. These choices do not grant permissions: backend authentication still selects the authorized workspace. The selected theme persists across reloads.

## Sorting trade history

Each History column heading is a button. Click it for ascending order; click again for descending order. The arrow and accessible `aria-sort` state show the active direction. History starts with the newest date first. IDs use natural ordering (2 before 10); quantities, prices and executed values sort numerically. Dates compare recorded timestamps or the existing legacy date-only value. Missing/invalid values stay last in both directions. Dispute tickets sort by their displayed status (No disputes, Resolved, Under review). Ties use order ID for predictable results. Sorting includes all loaded orders, survives refreshes, and does not change financial records or ticket associations.

## Customer dispute to admin workflow

In **History**, each executed trade offers **Dispute trade** or **View tickets / dispute**. Enter a short title and reason; the transaction ID is fixed to that row. Check the fee option if relevant. Each new submission can create another ticket for the same trade, including after resolution; retrying an uncertain submission reuses its request key to avoid duplicates.

Open the admin dispute queue with `local_admin`. The customer and account links open the actual customer record. Claim the ticket, accept it for investigation, add internal notes and resolve it with a reason. Customer History shows **Under review** while any ticket is active and **Resolved** when all tickets are resolved. Opening the customer's ticket list shows its public resolution reason; private staff notes and assignment details stay hidden. Refresh History for immediate changes; its status also refreshes every minute when a ticket dialog is closed. The admin queue refreshes every 15 seconds when idle.

Persistent verification records: trade 2009 has one active ticket; trade 2008 has two resolved tickets. These are clearly named local verification records, not hardcoded UI data. Creating and resolving these tickets left the educational account balance unchanged at 998 USD. Resolution records a decision; it does not automatically refund cash or alter holdings.
