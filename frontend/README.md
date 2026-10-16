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
trading service at `http://localhost:8082` and `/auth/*` to the Node auth stub
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
