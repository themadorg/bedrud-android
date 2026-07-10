# auth.md

You are an agent reading Bedrud discovery metadata. This file describes how authentication works **today** and how to use a Bedrud instance safely. It is **not** a full WorkOS [auth.md](https://workos.com/auth-md) registration protocol implementation.

## Hosts

| Host | Role |
|------|------|
| `https://bedrud.org` | Documentation, OpenAPI reference (`/swagger.json`), and this discovery document. **Not** a multi-tenant API. |
| `https://bedrud.xyz` | Public demo instance of the Bedrud server (live API + health). |
| `https://<your-host>` | Self-hosted production (or other) instance you or your user operate. |

API calls always target an **instance** (`bedrud.xyz` or a self-hosted host), never `bedrud.org` as the API base.

## Current state

Bedrud does **not** support agentic registration flows from the auth.md protocol:

- No `identity_assertion` / ID-JAG registration
- No `service_auth` or `anonymous` agent registration
- No `POST /agent/identity`, claim ceremony, or `/oauth2/token` JWT-bearer exchange for agents
- No OAuth Protected Resource Metadata or Authorization Server metadata for agent registration

Do **not** mint ID-JAGs or call agent-registration endpoints against Bedrud until this file is updated to advertise them.

Credentials are issued through the product’s existing human-oriented auth paths on each instance.

## How users get accounts

Accounts live on the **instance** (demo or self-hosted), not on bedrud.org.

1. **Local registration** — `POST /auth/register` on the instance (email/password). May require an invite token when the operator enables invite-gated registration.
2. **Local login** — `POST /auth/login` (or the web UI) to obtain session credentials.
3. **OAuth (as client)** — configured providers (e.g. Google) via the instance’s OAuth begin/callback routes. Bedrud is an OAuth *client* to those providers, not an Authorization Server for third-party agents.
4. **Passkeys** — WebAuthn register/login on the instance for users who have enrolled them.

Human-oriented docs:

- Authentication: https://bedrud.org/en/docs/backend/authentication
- Quickstart: https://bedrud.org/en/docs/getting-started/quickstart
- Configuration: https://bedrud.org/en/docs/getting-started/configuration

## Credentials

Successful login/register yields a **JWT access token** (and refresh handling as implemented by the instance). Call the API with:

```http
GET /api/... HTTP/1.1
Host: bedrud.xyz
Authorization: Bearer <access_token>
```

Replace the host with the user’s instance. Present the token only as a Bearer header; do not put it in query strings or logs.

### How agents should obtain credentials

Prefer tooling and secrets the user already configured. Look in this order; stop at the first that works:

1. Instance base URL and token already in the agent environment or project config (user-defined env vars or secret store).
2. An existing session or tool the user wired up for that instance.

If nothing is configured:

- **Do not ask the user to paste tokens into chat.**
- Tell them to register or log in on their instance (or the demo), issue/obtain a token through the supported UI or API, put it in an environment variable or secret store, then resume.
- Never commit tokens, echo them in command history, PR descriptions, or error reports.

## Use the API

Machine-readable description (OpenAPI / Swagger 2.0):

- https://bedrud.org/swagger.json

Interactive human docs:

- https://bedrud.org/en/docs/api/api-reference

Health (per instance):

```http
GET /api/health HTTP/1.1
Host: bedrud.xyz
```

Demo: https://bedrud.xyz/api/health

API catalog (RFC 9727): https://bedrud.org/.well-known/api-catalog

## Errors (auth-related)

| Status | Meaning | What to do |
|--------|---------|------------|
| `401` on first use | Missing, malformed, or wrong-instance token | Confirm instance URL and that the credential is for that host. |
| `401` after a working call | Logout, expiry, or revocation | Drop the cached token; re-auth out of band with the user; re-read from secret store. |
| `403` | Authenticated but not allowed | User needs a different role/access; do not escalate by inventing credentials. |
| `429` | Rate limited | Back off and retry; honor rate-limit headers if present. |

## Revocation

Users revoke sessions by logging out or by operator-side invalidation on the instance. Agents discover revocation as `401` on a previously working Bearer token — discard it and re-authenticate with the user through a secure channel (not chat paste).

## Legal and contact

- Terms: https://bedrud.org/en/terms
- Privacy: https://bedrud.org/en/privacy
- Contact: info@bedrud.org
- Project: https://github.com/themadorg/bedrud
