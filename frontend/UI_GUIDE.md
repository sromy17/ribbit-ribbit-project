# Leap UI system

The seven routed pages use Tailwind CSS 4 through its PostCSS integration and
shared standalone Angular components in `src/app/shared/ui/index.ts`. These are
project-owned components; there is no dependency on paid Tailwind Plus assets
or a React-specific component library.

## Run the real application

```bash
npm ci
npm start
```

Open http://localhost:4200. Restart an existing `ng serve` process after pulling
this change: Angular must load the new `.postcssrc.json` configuration. A server
started before that file existed can show unstyled content until restarted.
The real application still uses its existing backend/auth proxy configuration.

## Explore every design without a backend

```bash
npm run preview:ui
```

Open http://127.0.0.1:4202/admin-dashboard. The top ribbon links to sign-in,
registration, account selection/setup, investor, admin, and analyst screens.
No login is needed in this explicit design-preview build. All users, money,
orders, quotes, tickets, and case actions are in-memory sample data; refresh
resets them. This preview never connects to the backend or places real orders.

`preview/main.ts` is a separate Angular CLI entry point selected only by the
`ui-preview` configuration and `tsconfig.preview.json`. The normal production
build starts at `src/main.ts` and does not import or bundle the preview providers.
Do not deploy the ui-preview configuration as the real application.

## Shared components

- `button[uiButton]`: secondary by default, or `uiButton="primary"`, `"ghost"`,
  or `"danger"`. Native button semantics and disabled states are preserved.
- `input[uiInput]`, `select[uiInput]`, `textarea[uiInput]`: consistent field styling;
  associate every field with a label. Checkboxes use native controls.
- `app-workspace`: header, brand, theme control, identity and sign-out event;
  optional `[workspaceActions]` projection for account switching/refresh.
- `app-page-heading`: eyebrow, title, description, and projected actions.
- `app-status-badge`: readable text and tone for trading/case states. Colour is
  supplementary, never the only state indicator.
- `app-empty-state`: clear title, description, and optional projected action.
- `app-brand`: local SVG mark; no remote font/image request is required.

Tokens live in `src/styles.css` and map into Tailwind's theme using `@theme`.
The default dark palette is ink/blue-grey with a mint accent; light mode uses
white/slate with a deeper green. The header theme button persists the preference
under `leap-theme`. Reduced-motion preferences are respected.

Page layouts use shared Tailwind component classes and utilities. Old duplicated
component styles have been removed rather than layered over the new system.
Tables scroll within their own wrapper, navigation reflows on narrow screens,
and the workspace includes a keyboard skip link and visible focus indicators.
Routes load lazily so dashboards do not increase the sign-in page's initial JS.

## Validate

```bash
npm test -- --watch=false
NG_BUILD_MAX_WORKERS=2 npm run build
```

Visual review used the explicitly labelled sample-data build at 1440×960 and
390×844, including admin/investor horizontal-overflow checks, both themes, case
detail, account cards, analyst views, and registration/setup forms. The real
backend was not changed by this UI work. Four existing admin workflow tests
remain the behavioural regression checks; a design preview is not a substitute
for end-to-end backend verification.
