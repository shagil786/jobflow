# JobFlow services

Each directory is an independently deployable Spring Boot boundary. The current implementation exposes an internal service-info endpoint and compiles against the approved Java/Spring foundation. Persistence, OAuth, Gmail, queue, and AI adapters must be added behind these boundaries with the contracts repository as the compatibility source of truth.

The job service is fail-closed behind stateless OIDC JWT validation. Configure `JOBFLOW_OIDC_ISSUER_URI` before starting it; requests must carry a bearer token with `sub` and `tenant_id` claims. Client-supplied tenant and user headers are not trusted.

Build locally:

```bash
mvn -Dmaven.repo.local=../.m2 -Dmaven.test.skip=true package
```
