# Identity Providers And Multitenancy

> This document describes a conceptual Zitadel integration. Zitadel integration,
> user provisioning, central roles and tenant access metadata are not implemented
> by this repository.

The current project uses an in-memory account named `user`. See
[OAuth2 And OIDC Overview](oauth2-oidc-overview.md) for the behavior that is
implemented by `auth-server`, `client-server` and `resource-server`.

## Responsibility Boundaries

In the proposed architecture, Zitadel is the authority for identity and central
authorization data:

- user identity, credentials, authentication and MFA;
- central roles such as `ADMIN`, `MANAGER` and `USER`;
- access metadata such as `tenant_access`.

The application database remains authoritative for domain data. For example, it
can hold accounts, tasks and other records that belong to `tenant-101` or
`tenant-202`.

When a domain record must refer to a person, the application can keep a minimal
local link:

```text
app_user
+----------+---------------------+
| id       | zitadel_user_id     |
+----------+---------------------+
| user-201 | zitadel-user-789    |
+----------+---------------------+
```

The application should not copy roles or `tenant_access` into this table only to
make local queries easier. Two writable copies create ambiguity about which
value is authoritative and require reconciliation. Local records should contain
identity-related data only when the domain genuinely needs it.

This minimal link does not require a database lookup on every request. The
client can obtain the signed-in user's profile claims, such as `name`, from the
ID token or UserInfo when Zitadel is configured to provide them. Tenant names
and descriptions are different: they are domain data and should come from the
application when a screen needs to display them.

This boundary does not move domain authorization into Zitadel. The resource
server can combine trusted token claims with current domain state. For example,
it may require the `MANAGER` role and then verify that the requested task belongs
to a tenant allowed by the token.

## ID Tokens And Access Tokens

OIDC and OAuth2 tokens have different audiences and purposes:

```text
ID token     -> identifies the authenticated user to the client, session or UI
Access token -> authorizes a client to call an API
```

The client uses the ID token when establishing the user's login session. The
resource server validates the access token, including its issuer, signature,
audience and expiration, before using its authorization claims.

An ID token must never be sent to the resource server as a Bearer token. Even if
Zitadel is configured to include a role or metadata claim in both token types,
API authorization depends on the claims in the validated access token.

A conceptual access-token payload could contain:

```json
{
  "iss": "https://zitadel.example.com",
  "sub": "zitadel-user-789",
  "aud": "resource-api",
  "roles": ["MANAGER"],
  "tenant_access": ["tenant-101"],
  "scope": "tasks.read tasks.write"
}
```

The resource server must map claims to the authorities expected by Spring
Security. For example, `MANAGER` can map to `ROLE_MANAGER`, while `tasks.read`
can map to `SCOPE_tasks.read`.

## Tenant Access Metadata

Zitadel metadata can describe which tenants a user may access. There is no
single representation that is best for every system. The choice depends on the
number of tenants, how often descriptive data changes and whether avoiding a
domain lookup is more important than keeping tokens compact and current.

### Strategy 1: Stable IDs

The recommended default is a compact format containing only the special value
`ALL` or stable tenant IDs:

```json
{
  "tenant_access": ["ALL"]
}
```

```json
{
  "tenant_access": ["tenant-101", "tenant-202"]
}
```

The resource server interprets `ALL` as access to every tenant permitted by the
application policy. Otherwise, it compares the requested tenant's stable ID
with the values in the claim. The API must still enforce domain constraints;
the presence of a tenant ID does not bypass checks such as record ownership or
record state.

IDs keep the access token compact, avoid synchronizing descriptive fields with
Zitadel and make the authorization decision independent of a tenant rename. A
normal domain request often already loads a task, account or another record with
its `tenant_id`, so checking that ID against the claim does not necessarily add
another database call.

This strategy does require resolution when a screen needs tenant details. The
backend should resolve all requested IDs in one domain query rather than issue
one query per ID. For example:

```text
tenant_access: [tenant-101, tenant-202]
                 |
                 v
domain query: find tenants where id in (tenant-101, tenant-202)
                 |
                 v
response: [{id, name, description}, {id, name, description}]
```

For `ALL`, the UI cannot derive the tenant list from the token. It obtains the
current list from a domain endpoint, subject to the same domain rules as any
other query.

### Caching Resolved Details

A backend cache can reduce repeated domain reads while keeping IDs as the
authorization format. Cache entries use the stable tenant ID as their key and
contain only descriptive data:

```json
{
  "tenant-101": {
    "name": "Example Tenant",
    "description": "Primary workspace"
  }
}
```

The resolution flow is:

```text
1. Collect all tenant IDs needed by the response.
2. Read all matching cache entries in one operation.
3. Resolve cache misses in one domain query.
4. Return the combined result and cache the descriptive values.
```

The cache can be local to one backend instance or shared by multiple instances;
that is an operational choice. Entries should expire after an appropriate TTL
or be invalidated when the domain changes. The required freshness determines
the policy: a short TTL reduces stale labels, while explicit invalidation avoids
waiting for expiration when update events are reliable.

The application database or domain service remains authoritative. Cached
`name` and `description` values may be temporarily stale because they affect
presentation only. Roles, `tenant_access` and authorization decisions must not
come from this descriptive cache.

This IDs-plus-batch-resolution approach is the preferred hybrid for
administrative screens: authorization stays compact and stable, while the UI
can receive current descriptive data without making one request per tenant.

### Strategy 2: Enriched Metadata

An alternative is to include descriptive data directly in Zitadel metadata:

```json
{
  "tenant_access": [
    {
      "id": "tenant-101",
      "name": "Example Tenant",
      "description": "Primary workspace"
    }
  ]
}
```

This representation can help a UI render labels without a domain lookup, but it
has important costs:

- descriptive values must be synchronized whenever domain data changes;
- tokens become larger as tenants or fields are added;
- descriptive values can become stale while an access token remains valid.

Therefore, enriched fields are display hints only. Authorization must use the
stable `id`, and the application database remains authoritative for tenant names
and other domain information.

Enriched metadata is a reasonable choice when each user has a small, stable
tenant list and avoiding a display-data lookup has a measured benefit. It is a
poor fit when names change frequently, tenant lists are large or immediate
display consistency matters. Updating a name requires updating Zitadel metadata
and then waiting for a new or renewed token before the client sees the change.

A cache can still be used with enriched metadata, but it normally adds little:
the token itself already contains the display snapshot. If the UI requires
current values, it should resolve the IDs through the domain instead of treating
the enriched claim as current domain state.

### Strategy 3: Server-Side Access Resolution

Embedding access data in a JWT becomes inefficient when a user can access
hundreds of tenants or when membership changes very frequently. In that case,
the token can carry a coarse entitlement or compact reference while the resource
server resolves the current tenant set through a server-side authorization
source.

This approach keeps tokens small and can make changes effective sooner, but it
gives up fully stateless authorization. Normal requests may depend on another
service or cache, so availability, latency, invalidation and failure behavior
must be designed explicitly. A request must fail closed when the current access
set cannot be established.

This is a different responsibility model from placing `tenant_access` directly
in Zitadel-issued JWTs. It should be chosen deliberately for scale or freshness,
not introduced merely to avoid resolving tenant names. A broad value such as
`ALL` must represent a real policy decision and must never be used as a shortcut
for an oversized tenant list.

### Strategy Comparison

| Strategy | Authoritative authorization data | Additional domain read | Token size | Freshness | Best fit |
| --- | --- | --- | --- | --- | --- |
| Stable IDs | Validated access-token IDs | Only for display data; batch and cache when useful | Small | Access changes appear in new tokens; names come from the domain | Default for small or moderate tenant lists |
| Enriched metadata | Stable IDs inside each token object | Usually unnecessary for labels | Larger | Names can remain stale until token renewal | Small, stable lists with a measured display-latency requirement |
| IDs plus batch resolution and cache | Validated access-token IDs | Batched on cache miss | Small | Authorization follows token lifetime; label freshness follows cache policy | Administrative and list screens that need tenant details |
| Server-side access resolution | Current server-side access source | Required for authorization, normally through a cache | Small | Can reflect changes sooner | Very large or highly dynamic access sets |

The decision rule is:

```text
Start with stable IDs.
Need labels? Resolve in a batch and cache descriptive data.
Need to eliminate that lookup for a small, stable list? Consider enrichment.
Have a very large or dynamic access set? Consider server-side authorization.
```

## Administrative Provisioning

An administrative UI should call the application backend rather than expose
Zitadel management credentials to the browser. A user-provisioning flow is:

```text
1. An administrator sends the new user's identity, role and tenant access to
   the application backend.
2. The backend calls the Zitadel Management API to create the identity.
3. The backend assigns a central role such as ADMIN, MANAGER or USER in Zitadel.
4. The backend updates tenant_access metadata in Zitadel.
5. The application saves id + zitadel_user_id only if its domain needs a local
   reference to that user.
```

The backend should define retry, compensation and audit behavior because the
Zitadel call and a local database write are not one transaction. It should also
keep management API credentials server-side and authorize the administrator
before making changes.

Normal API requests do not query the Management API. They use roles and metadata
from the validated access token. Administrative screens that need the current
identity state can query Zitadel through the backend instead of relying on a
duplicated local role or metadata cache.

## Authorization Updates And Token Lifetime

Changing a role or `tenant_access` value in Zitadel does not rewrite access
tokens that have already been issued. The new authorization state appears in a
new or renewed token, while an existing token can retain its previous claims
until it expires.

The acceptable delay is a security-policy decision. Sensitive systems should
combine an appropriately short access-token lifetime with mechanisms supported
by their deployment, such as token revocation, session termination or requiring
the user to authenticate again. The API must continue to validate every access
token and must not assume that a management update changes an already-issued
token immediately.

## Resulting Model

```text
Zitadel
  identity and authentication
  roles: ADMIN, MANAGER, USER
  tenant_access: ALL or stable tenant IDs
  issues ID tokens and access tokens

Client application
  uses the ID token for login/session identity
  sends an access token when calling the API

Resource server
  validates the access token
  maps trusted claims to authorities
  combines claims with current domain rules

Application database
  owns tenants, accounts, tasks and other domain records
  optionally links id to zitadel_user_id
  does not duplicate Zitadel roles or access metadata for convenience
  resolves descriptive tenant data in batches and may cache it
```
