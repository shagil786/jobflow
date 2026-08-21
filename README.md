# JobFlow

JobFlow is a private job-search command center that turns captured jobs and scoped Gmail conversations into evidence-backed next actions.

## Repository layout

- `web/` — Next.js App Router dashboard and core interaction slice.
- `extension/` — TypeScript browser-capture package with a safe generic extractor.
- `contracts/` — versioned API and event contracts shared across services.
- `services/` — independently deployable Spring Boot service skeletons.
- `docs/` — approved product and implementation specification.

## Local web app

```bash
cd web
npm install
npm run dev
```

The dashboard does not use sample records or demo identities. It requires a verified session and reads only persisted service data. The job service now requires an OIDC issuer through `JOBFLOW_OIDC_ISSUER_URI`; it derives the user from the JWT subject and the tenant from the `tenant_id` claim. Gmail, AWS, and AI remain explicit adapter boundaries until their credentials and provider contracts are configured.

## Safety boundaries

- Imported Gmail content is scoped to an explicit `JobFlow/Track` label or user search.
- AI suggestions remain reviewable and never silently become user truth.
- Drafts are editable and unsent.
- The extension captures visible job data only; it does not submit applications or send messages.
