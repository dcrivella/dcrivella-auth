# OAuth2 And OIDC Overview

This project is a small OAuth2 and OpenID Connect learning stack. It has three Spring Boot applications:

- `auth-server`: the Authorization Server. It authenticates users, registers OAuth2 clients, issues tokens and publishes token verification metadata.
- `client-server`: the OAuth2/OIDC client web application. It is the app the user opens in the browser. It signs the user in with authorization code flow plus PKCE, receives tokens and calls the resource server.
- `resource-server`: the protected API. It accepts access tokens, validates them and returns protected data.

## OAuth2 And OIDC

OAuth2 is an authorization framework. It answers the question: "Can this client access this resource?"

In the browser flow:

1. The user opens the client application.
2. The client redirects the browser to the auth server.
3. The user signs in on the auth server login page, not inside the client web app.
4. The client requests scopes such as `openid`, `profile` and `api.read`. The auth server checks that those scopes are allowed for that client and asks for user consent when consent is required.
5. The client receives an access token.
6. The client uses that access token to call the resource server on behalf of the user.

So the access token represents an authorization decision: this client can call this API with these scopes for this user.

OIDC, short for OpenID Connect, is an identity layer on top of OAuth2. It answers the question: "Who is the signed-in user?"

They complement each other:

- OAuth2 gives the client an access token for APIs.
- OIDC adds login semantics and an ID token for the user identity.

JWT and OAuth2 are different kinds of things:

```text
OAuth2 -> authorization framework / protocol
JWT    -> token format that an OAuth2 authorization server may use
```

So "JWT vs OAuth2" is usually the wrong comparison. OAuth2 can issue access tokens in different formats. In this project, the access tokens are signed JWTs.

## OAuth2 Grant Types In This Project

An OAuth2 grant type is the way a client asks the authorization server for tokens.

This project uses these grant types:

```text
authorization_code -> user login through the browser
refresh_token      -> get a new access token after login
client_credentials -> service-to-service access with no user
```

### Authorization Code + PKCE

The `client-server-pkce` client uses authorization code flow with PKCE:

```text
1. User signs in through the auth server.
2. Client receives an authorization code.
3. Client exchanges the code plus PKCE verifier for tokens.
4. Auth server returns an access token, ID token and refresh token.
```

In token endpoint terms, the exchange uses:

```text
grant_type=authorization_code
```

PKCE protects the authorization code exchange. It does not replace the grant type; it strengthens the authorization code flow.

### Refresh Token Grant

Refresh token rotation happens when the client uses the current refresh token to ask for a new access token:

```text
grant_type=refresh_token
refresh_token=R1
```

With rotation enabled, the auth server returns a new refresh token and invalidates the old one:

```text
R1 -> used once -> returns access_token=A2 + refresh_token=R2
R1 -> reused later -> suspicious, reject according to policy
```

Rotation does not happen on every normal API call. Normal API calls use the access token. The refresh token is sent only to the authorization server when the client needs fresh tokens.

### Client Credentials Grant

The `client-m2m` client uses client credentials:

```text
grant_type=client_credentials
```

This is for machine-to-machine access. There is no user login and normally no refresh token:

```text
service authenticates with client_id + client_secret
-> auth server returns access token
-> service calls resource server
```

When the access token expires, the service authenticates with its client credentials again and requests another access token. A refresh token is usually unnecessary because the client already has its own credential.

## Login With Google Analogy

Many websites use "Login with Google" only to sign the user in.

In that case, Google acts as the identity provider and authorization server:

- Identity provider -> authenticates the user and answers "Who signed in?"
- Authorization server -> issues tokens and answers "What access was granted?"

Typical flow:

1. The user opens `example.com`.
2. The user clicks "Login with Google".
3. `example.com` redirects the browser to Google.
4. The user signs in on Google's login page.
5. Google may ask for consent, such as permission to share email or profile data.
6. Google redirects the browser back to `example.com`.
7. `example.com` receives tokens from Google.
8. `example.com` uses the ID token to know who signed in.
9. `example.com` may create or update a local user record in its own database.

If the app only wants login, the most important token is the ID token:

```text
ID token -> tells the client application who signed in
```

The app may also receive an access token. That access token is useful when the app wants to call Google's UserInfo endpoint or Google APIs such as Gmail and Google Drive:

```text
Access token -> lets the app call an API
```

So the short memory version is:

```text
Identity provider -> authenticates users
Authorization server -> issues tokens
OIDC provider -> authorization server with OIDC support that can issue ID tokens
```

An OIDC provider is not just another name for an identity provider. It is an authorization server that supports OpenID Connect. In practice, OIDC providers usually authenticate users too, so they often act as both identity provider and authorization server.

Examples of OIDC providers:

- Google: authenticates Google users and issues OIDC/OAuth2 tokens.
- Microsoft Entra ID: authenticates organization users and issues OIDC/OAuth2 tokens.
- Keycloak: self-hosted OIDC provider.
- Zitadel: OIDC provider focused on identity and access management.
- Okta: OIDC provider for workforce/customer identity.
- Auth0: OIDC provider for application login.

Some OIDC providers can also delegate login to external identity providers. That means the client app talks to one OIDC provider, but the user may sign in through another provider behind it.

Google is commonly used directly:

```text
client app -> Google -> Google account
```

Zitadel can also delegate login to another provider:

```text
client app -> Zitadel -> Zitadel local user
client app -> Zitadel -> Google
client app -> Zitadel -> GitHub
client app -> Zitadel -> corporate SAML IdP
```

In that model, the client app trusts Zitadel. The user may sign in directly with Zitadel or through an external provider such as Google. After login succeeds, Zitadel issues tokens to the client app.

Trust means the client app is configured to accept Zitadel as the token issuer, usually through an issuer URL:

```text
issuer-uri: https://zitadel.example.com
```

From that issuer, the client can discover OIDC metadata and public keys. Then it can validate tokens by checking the issuer, signature, audience and expiration.

The trust relationship is:

```text
client app trusts Zitadel
Zitadel may trust Google
```

The client app does not need to trust every external provider behind Zitadel directly. Keycloak, Zitadel, Okta and Auth0 can commonly be configured to authenticate local users or delegate login to external providers such as Google, GitHub, Facebook or enterprise SAML/OIDC systems.

## Signup And User Ownership

Signup has two different kinds of user data:

```text
identity data -> owned by the auth server / OIDC provider
application data -> owned by the application or domain service
```

Identity data is about login:

- username
- email used for login
- password or external login link
- MFA settings
- identity provider subject
- auth-server roles or groups when used

Application data is about the product:

- customer profile
- cart or orders
- preferences
- application-specific roles
- billing data
- domain permissions

In this learning project, `auth-server` owns the demo login identity. The demo user is `user`. The `resource-server` owns protected API behavior such as `/tasks`.
 
This project does not implement real signup yet.

### Signup Process Options

In the market, a common signup approach is to let the application own the business signup flow, but let the identity provider, such as Zitadel, own the sensitive authentication screens.

A useful hybrid is:

```text
1. App shows "Create account", plan, company or tenant form.
2. App redirects to Zitadel signup/login.
3. Zitadel creates and verifies the identity.
4. App receives the login callback.
5. App creates local app_user, customer or tenant records.
```

This gives the product control over the business flow while still letting the identity provider handle passwords, MFA, verification and login security.

In a SaaS, the tenant is usually an application concept first. For example, if the signup screen asks for `company = Demo Shop`, the application can create `tenant-101` in its own database. Zitadel does not have to create the tenant first.

The normal ownership is:

```text
Application creates:
  tenant/customer/account for the product

Zitadel creates:
  user identity for login

Application links:
  local app_user -> Zitadel user subject
  local app_user -> local tenant
```

A Zitadel Organization is optional. Use it only if you want Zitadel to also model identity-side organization membership, organization roles, organization login policies or delegated administration.

Example data from the signup screen:

```text
Signup screen:
  email: demo@example.com
  name: Demo User
  company: Demo Shop
  plan: PRO
  password: ********
```

The application should send only identity/authentication data to Zitadel:

```json
{
  "email": "demo@example.com",
  "name": "Demo User",
  "password": "********"
}
```

Zitadel creates the identity and later returns identity data to the application through the login callback, ID token, UserInfo endpoint or management API:

```json
{
  "iss": "https://zitadel.example.com",
  "sub": "zitadel-user-789",
  "email": "demo@example.com",
  "email_verified": true,
  "name": "Demo User"
}
```

The application stores product/business data in its own database. A SQL result view could look like this after signup:

```text
tenant
+------------+-----------+------+
| id         | name      | plan |
+------------+-----------+------+
| tenant-101 | Demo Shop | PRO  |
+------------+-----------+------+

app_user
+----------+------------+------------------+-----------------+-----------------+-------+
| id       | tenant_id  | zitadel_user_id  | email           | display_name    | role  |
+----------+------------+------------------+-----------------+-----------------+-------+
| user-201 | tenant-101 | zitadel-user-789 | demo@example.com | Demo User       | OWNER |
+----------+------------+------------------+-----------------+-----------------+-------+

customer
+--------------+------------+-----------------+--------+
| id           | tenant_id  | billing_email   | status |
+--------------+------------+-----------------+--------+
| customer-301 | tenant-101 | demo@example.com | ACTIVE |
+--------------+------------+-----------------+--------+
```

The important point is that Zitadel stores the login identity, while the application stores the business records:

```text
Zitadel:
  zitadel-user-789
  demo@example.com
  password hash
  email verification
  MFA and login policy

Application database:
  tenant-101 / Demo Shop / PRO
  user-201 linked to zitadel-user-789
  customer-301 billing/domain record
```

If the application also creates or links a Zitadel Organization for the same company, the local tenant can store that optional link:

```text
tenant
+------------+-----------+------+----------------+
| id         | name      | plan | zitadel_org_id |
+------------+-----------+------+----------------+
| tenant-101 | Demo Shop | PRO  | zitadel-org-55 |
+------------+-----------+------+----------------+
```

But this is not required for every SaaS. A simple application can keep the tenant only in its own database and use Zitadel only for user identities.

The important separation is:

```text
Zitadel / auth server -> owns authentication identity
application service -> owns application user/customer/tenant data
resource server -> enforces API authorization from token claims and mapped authorities
```

Application-specific roles can live in the application database. Central identity/RBAC roles can live in Zitadel and be emitted as token claims when the resource server needs them.

Example e-commerce split:

```text
Zitadel:
  ADMIN
  MANAGER

application database:
  CUSTOMER
  PREMIUM_CUSTOMER
```

In this example, `MANAGER` is in Zitadel because it is a central company permission. The same employee might use it to access the management dashboard, admin portal and audit tools.

`CUSTOMER` and `PREMIUM_CUSTOMER` are in the application database because they are customer category/domain data for this e-commerce app.

```text
CUSTOMER -> normal customer category, not a security role
PREMIUM_CUSTOMER -> premium customer category, not a security role
```

Customer category can affect business rules, discounts or UI behavior. A security role controls what the user is allowed to do.

An OIDC ID token from Zitadel would look more like this:

```json
{
  "iss": "https://zitadel.example.com",
  "sub": "zitadel-user-1",
  "aud": "client-app",
  "name": "Demo User",
  "iat": 1710000000,
  "exp": 1710000300
}
```

If the app will always use only Zitadel, a column name such as `zitadel_user_id` can work:

```text
app_user
--------
id                         9b9e7c4a-9b62-4c13-a2cf-12b9a0e0a111
zitadel_user_id            zitadel-user-1
display_name               Demo User
customer_category          PREMIUM_CUSTOMER
```

### Admin-Created Users And Local Mirrors

A Demo Shop admin screen can create internal operators:

```text
Admin -> Demo Shop UI -> Demo Shop backend

Demo Shop backend does two things:
  1. Calls Zitadel Management API to create the login user.
  2. Saves a local Operator row in the application database.
```

In this model, the frontend does not call Zitadel directly. The UI calls the application's backend. The backend wraps the Zitadel Management API and also writes the application's own database row.

That keeps important rules in one place:

```text
only ADMIN can create/update/deactivate operators
backend validates store access and roles
backend owns rollback/retry behavior
frontend never receives Zitadel management credentials
```

This is different from public signup. It is an admin-provisioning flow:

```text
public signup:
  user creates their own account

admin-created operator:
  an existing admin creates another user's account
```

Example operator creation form:

```text
fullName: Demo User
email: demo@example.com
role: OPERATOR
storeAccess: [store-10, store-20]
defaultStoreId: store-10
initialPassword: ********
```

The backend sends only identity/security data to Zitadel:

```json
{
  "fullName": "Demo User",
  "email": "demo@example.com",
  "role": "OPERATOR",
  "initialPassword": "********"
}
```

Zitadel creates the login identity and returns a user id:

```json
{
  "id": "zitadel-user-789",
  "email": "demo@example.com",
  "fullName": "Demo User"
}
```

Then the application stores a local mirror with domain-specific data:

```text
operator
+--------------+------------------+------------------+-----------+----------+------------------+--------+
| id           | zitadel_user_id  | email            | full_name | role     | default_store_id | status |
+--------------+------------------+------------------+-----------+----------+------------------+--------+
| operator-201 | zitadel-user-789 | demo@example.com | Demo User | OPERATOR | store-10         | ACTIVE |
+--------------+------------------+------------------+-----------+----------+------------------+--------+

operator_store_access
+--------------+----------+
| operator_id  | store_id |
+--------------+----------+
| operator-201 | store-10 |
| operator-201 | store-20 |
+--------------+----------+
```

The local `Operator` row is a mirror. It does not replace Zitadel as the identity source. It stores application-specific data that Zitadel should not own:

```text
Zitadel owns:
  login identity
  email/password/MFA
  security role/claims used for authorization
  active/inactive login state

Application database owns:
  internal operator id
  storeAccess
  default store
  role cache for list screens and filters
  local status/cache
  local search/filter fields
```

The `role` can appear in both places, but it does not mean both places have the same responsibility.

For example:

```text
Zitadel:
  user zitadel-user-789 has role OPERATOR

Access token issued by Zitadel:
  roles = ["OPERATOR"]

Application database:
  operator.role = OPERATOR
```

Zitadel should be the source of truth for security authorization. The resource server should decide whether a request is allowed from the authenticated token claims, such as `ADMIN`, `SUPERVISOR`, `OPERATOR` or `VIEWER`.

The local `operator.role` is useful as a cache for application screens:

```text
list operators
show role badge
filter by role
search without calling Zitadel for every row
```

When an admin changes an operator role, the backend should update Zitadel first. If Zitadel succeeds, the backend updates the local `operator.role` cache:

```text
1. Admin changes Demo User from OPERATOR to SUPERVISOR.
2. Backend updates the role in Zitadel.
3. Backend updates operator.role in the application database.
4. Future access tokens from Zitadel contain the new role.
```

If the local role cache and Zitadel disagree, trust Zitadel for security and reconcile the local mirror.

For creation, the application usually uses a small saga:

```text
1. Backend creates user in Zitadel.
2. Zitadel returns zitadel_user_id.
3. Backend saves local Operator mirror.
4. If local save fails, backend tries to delete the Zitadel user as rollback.
```

For updates, the backend should usually update Zitadel first and persist the local mirror only if Zitadel succeeds:

```text
rename/change role:
  update Zitadel
  update local Operator cache

deactivate:
  deactivate in Zitadel
  set local status = INACTIVE
```

The important rule is that login-sensitive state must stay aligned with Zitadel. If an operator is `INACTIVE`, Zitadel should also block login for that user.

There is one tradeoff: this is not a real distributed transaction. If Zitadel succeeds and the local database fails, rollback is best-effort. Production systems usually add structured logs, alerts or a reconciliation job to find orphaned Zitadel users or stale local mirrors.

### Tenant Data

Zitadel has tenant-like concepts, but it does not usually call them realms.

In Zitadel terminology:

```text
Instance -> top-level issuer / isolated identity environment
Organization -> tenant-like unit inside an instance
Project -> apps, roles and grants for a software solution
```

A simplified Zitadel shape can look like this:

```text
Zitadel Instance
-> Organization: Demo Shop (id: zitadel-org-1)
   -> Project: E-commerce Platform
      -> Application: web-client
      -> Application: mobile-client
      -> roles/grants
```

The `Instance` is important because it is the issuer boundary. The `Organization` is often the closest Zitadel concept to an identity-side tenant. The `Project` groups applications, roles and grants for one software solution.

Your application can still have its own tenant model. For example, a SaaS app might have a `tenant` table for customer accounts:

```text
tenant
------
id                         tenant-1
name                       Demo Shop
zitadel_org_id             zitadel-org-1
```

Then users in your application can belong to that tenant:

```text
app_user
--------
id                         9b9e7c4a-9b62-4c13-a2cf-12b9a0e0a111
tenant_id                  tenant-1
zitadel_user_id            zitadel-user-1
display_name               Demo User
customer_category          PREMIUM_CUSTOMER
```

In that design:

```text
tenant_id -> your app tenant/customer/account
zitadel_org_id -> optional link to a Zitadel organization
zitadel_user_id -> identity user in Zitadel
```

So this column:

```text
tenant.zitadel_org_id = zitadel-org-1
```

points to:

```text
Zitadel Organization: Demo Shop (id: zitadel-org-1)
```

It does not point to the Zitadel Instance or Project.

Visualized together:

```text
Zitadel Organization: Demo Shop
<-> tenant.zitadel_org_id

Application tenant: tenant-1 / Demo Shop
-> app_user.tenant_id
-> orders.tenant_id
-> billing_account.tenant_id
```

A simple SaaS database usually starts with `tenant_id` columns:

```text
app_user.tenant_id
orders.tenant_id
tasks.tenant_id
billing_account.tenant_id
```

That keeps tenant ownership in the application domain model.

#### Tenant Storage Models

```text
tenant_id column -> simpler and common SaaS default
schema per tenant -> stronger separation with more operational complexity
database per tenant -> strongest separation with highest operational complexity
```

For a learning project or normal SaaS start, `tenant_id` columns are usually the clearest model. Do not assume a Zitadel organization replaces the application's own tenant table unless the app is intentionally designed that way.

References:

- [Zitadel Organizations](https://zitadel.com/docs/guides/manage/console/organizations-overview)
- [Zitadel Instances](https://zitadel.com/docs/concepts/structure/instance)

### Identity Roles And Application Data

Identity roles and application domain data can live in different places:

```text
identity roles/groups -> stored in the auth server and emitted as token claims
application categories/domain data -> stored in the application database
```

Application categories such as `CUSTOMER` or `PREMIUM_CUSTOMER` are not security roles. They describe product/domain state. Security roles control what the user is allowed to do.

If identity roles/groups are emitted in tokens, the resource server must map those claims to Spring Security authorities.

### Claims And Fine-Grained Authorization

Roles, groups and scopes can be emitted as token claims. That is common for coarse authorization:

```json
{
  "sub": "zitadel-user-1",
  "roles": ["ADMIN", "MANAGER"],
  "scope": "api.read"
}
```

This is useful when the token only needs to say broad things:

```text
MANAGER -> can access management area
ADMIN -> can access admin tools
api.read -> can call read API endpoints
```

The resource server still needs to map those claims to authorities before Spring Security can use them:

```text
roles claim ADMIN -> ROLE_ADMIN
scope api.read -> SCOPE_api.read
```

## Auth Server Sessions And SSO

An auth server or OIDC provider like Keycloak or Zitadel normally stores some form of login session.

It needs to know things like:

- this browser/user is already signed in
- when the login happened
- when the session expires
- which client apps have active sessions
- which refresh tokens or grants exist
- whether logout should invalidate sessions/tokens

This auth-server session is what enables single sign-on (SSO). If the user is already signed in at the auth server, another client app can redirect the browser to the auth server and the user may not need to type the password again.

Example:

```text
user signs in at auth server
client app A redirects to auth server -> no password prompt
client app B redirects to auth server -> no password prompt
```

The auth server session is not the same as the client app session:

```text
auth server session -> user is signed in at the identity/auth system
client app session -> user is signed in to one specific application
```

So auth servers commonly store login sessions. Client apps can also store their own sessions. They are related, but they are not the same session.

In this project, `auth-server` also does both jobs. It authenticates the demo user and issues OAuth2/OIDC tokens.

In this project, the client asks for these scopes:

```text
openid, profile, api.read
```

Think of them like this:

```text
openid -> turn this OAuth2 request into an OIDC login request
profile -> ask for standard user profile claims
api.read -> ask for permission to call the resource server read API
```

- `openid` asks for OIDC login and an ID token. It tells the auth server: "I want an ID token for this signed-in user."
- `profile` asks for optional standard profile claims. It tells the auth server: "I want basic user profile data such as name, username or picture when available."
- `api.read` asks for API access. It tells the auth server: "I want permission to call the resource server read API."

Only `openid` is required to make the request an OIDC login request. Without `openid`, it is just an OAuth2 authorization request, not an OIDC authentication request. `profile` is common, but optional. `email`, `phone` and `address` are other standard optional OIDC scopes when the client wants those claims.

The OpenID Connect Core 1.0 specification defines these standard scope groups:

```text
profile -> name, family_name, given_name, middle_name, nickname, preferred_username, profile, picture, website, gender, birthdate, zoneinfo, locale, updated_at
email   -> email, email_verified
phone   -> phone_number, phone_number_verified
address -> address
```

These scopes request claims. They do not guarantee every claim will be returned. The provider may return only the claims it has, only the claims the user consented to share or only the claims allowed by its policy.

Reference: [OpenID Connect Core 1.0 specification](https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims).

Example OIDC identity claims:

```json
{
  "sub": "user",
  "name": "Demo User",
  "preferred_username": "user",
  "picture": "https://example.com/avatar.png",
  "email": "user@example.com",
  "email_verified": true,
  "phone_number": "+1 555 0100",
  "phone_number_verified": true,
  "address": {
    "street_address": "123 Example Street",
    "locality": "Example City",
    "region": "CA",
    "postal_code": "94000",
    "country": "US"
  }
}
```

When `openid` is approved, the auth server can issue an ID token to the client. The ID token contains OIDC identity data about the signed-in user.

## User Consent

User consent means the auth server asks the user to approve the scopes requested by the client.

In this project, `client-server-pkce` is registered in the auth server with consent required. If the user does not approve `api.read`, the access token will not contain the `api.read` scope.

The `/tasks` endpoint requires:

```text
SCOPE_api.read
```

Spring Security maps the token scope `api.read` to the authority `SCOPE_api.read`. Without that authority, the resource server denies access to `/tasks`.

If the user does not approve `openid`, the OIDC login cannot complete correctly because `openid` is the scope that enables OIDC and ID token issuance.

## Spring Security Principal And Authorities

In Spring Security, the principal is the authenticated identity. In this project, the resource server uses the JWT `sub` claim as the principal.

The `sub` claim means subject. Depending on the identity system, examples might look like `user`, `user@example.com` or `9f6c2f3e-7d1b-4c8a-9b7f-1f9e4a8d1234`. In a real system, `sub` should be a stable unique identifier for the user, not a display name.

An authority is a permission granted to that principal. Spring Security checks authorities when it decides whether a request can access an endpoint.

In this project, access token scope mapping works like this:

1. The client requests `openid`, `profile` and `api.read` during login.

2. The auth server keeps OIDC scopes such as `openid` and `profile` for the login/ID-token side, then issues an access token with only API scopes:

    ```json
    {
      "scope": "api.read"
    }
    ```

3. The client server receives that access token and sends it to the resource server as a Bearer token.

4. The resource server validates the token and maps the token scopes to Spring Security authorities:

    ```text
    api.read -> SCOPE_api.read
    ```

That is why the `/tasks` endpoint can require:

```java
@PreAuthorize("hasAuthority('SCOPE_api.read')")
```

A role is a special kind of authority often used for RBAC (role-based access control). In Spring, role checks commonly use `hasRole`:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Spring treats that as a check for the authority `ROLE_ADMIN`. This project uses OAuth2 scope authorities for API access instead of role checks.

If an auth server also emitted roles in a token claim:

```json
{
  "roles": ["USER", "ADMIN"]
}
```

the resource server would need configuration that maps those role values to Spring authorities such as:

```text
ROLE_USER
ROLE_ADMIN
```

Without that mapping, the roles are just token data. Spring Security only makes access decisions from the authorities available in the authenticated principal.

In short:

- `SCOPE_api.read` usually comes from an OAuth2 access token scope like `api.read`.
- `ROLE_ADMIN` usually comes from an application role like `ADMIN`.

A claim is a piece of information declared inside a token. For example, a token can declare the user subject, name, audience, scopes or expiration.

This project uses a hardcoded demo account with username `user`, so the example subject below is `user`. In a real system, `sub` should usually be a stable unique user ID.

Example OAuth2 JWT access token claims:

```json
{
  "sub": "user",
  "name": "Demo User",
  "aud": "api://resource-server",
  "scope": "api.read",
  "exp": 1710000000
}
```

## Tokens

An access token is an OAuth2 token used to call an API. In this project, the client sends the access token to `resource-server` as a Bearer token.

This project uses JWT access tokens. A JWT access token is self-contained: the token carries claims such as issuer, subject, audience, scope and expiration inside the token itself.

An ID token is an OIDC token about the authenticated user. It is for the client application, not for calling APIs.

The access token and ID token are two different tokens:

```text
access token: eyJhbGciOiJSUzI1NiIsImtpZCI6ImFjY2Vzcy10b2tlbiJ9...
id token:     eyJhbGciOiJSUzI1NiIsImtpZCI6ImlkLXRva2VuIn0...
```

An access token is meant for the resource server:

```json
{
  "iss": "http://host.docker.internal:9000",
  "sub": "user",
  "aud": "api://resource-server",
  "scope": "api.read",
  "exp": 1710000300
}
```

An ID token is meant for the client application:

```json
{
  "iss": "http://host.docker.internal:9000",
  "sub": "user",
  "aud": "client-server-pkce",
  "name": "Demo User",
  "iat": 1710000000,
  "exp": 1710000300
}
```

The `aud` claim is the clearest clue for who the token is meant for:

- `aud: api://resource-server` means the token is meant for the resource server/API.
- `aud: client-server-pkce` means the token is meant for the client application.

A useful way to remember the split:

- ID token -> tells the client application who signed in.
- Access token -> lets the resource server decide whether the API call is allowed.

The client app can use ID token claims to show who signed in, such as a subject or display name. The resource server usually does not need display profile data. It needs claims for API authorization, such as issuer, subject, audience, scopes and expiration.

This project does not use opaque tokens.

Because this project uses signed JWT access tokens, the access token is also transparent to the resource server. Transparent means the resource server can read the token claims directly and validate the token signature locally.

That does not mean the token is secret. A signed JWT is usually readable by whoever has the token. The signature proves the token was issued by the trusted authorization server and was not changed after it was issued.

## Refresh Tokens

A refresh token is an OAuth2 token used by a client to get a new access token without asking the user to sign in again.

This matters because access tokens should be short-lived. In this project, access tokens last 5 minutes. A refresh token lets the client obtain a fresh access token after that without forcing the user through the browser login flow again.

Refresh tokens are only useful when there is an existing authorization grant to continue. That usually means a user-based flow:

```text
user signs in -> client receives access token + optional refresh token
access token expires -> client uses refresh token to get a new access token
```

Not every client gets a refresh token. The auth server decides per registered client and grant type.

In this project:

- `client-server-pkce`: supports refresh tokens. It is a browser login client using authorization code + PKCE.
- `client-postman-confidential`: supports refresh tokens. It is a confidential client with a client secret.
- `client-server-postman-pkce`: does not support refresh tokens. It is a short Postman PKCE example.
- `client-m2m`: does not support refresh tokens. It uses `client_credentials`, so there is no human login session to continue.

Machine-to-machine clients do not need refresh tokens in the same way. They authenticate directly with their own client credentials and can request a new access token when needed:

```text
machine client -> auth server -> new access token
```

PKCE does not forbid refresh tokens. PKCE protects the authorization code exchange. Refresh token issuance is a separate decision made by the auth server.

For public clients, such as a PKCE client without a client secret, refresh tokens need extra care because the client cannot strongly protect a secret. Modern OAuth guidance recommends sender-constrained refresh tokens or refresh token rotation for public clients.

### Pure SPA

For a pure browser SPA, using no refresh token can make sense:

```text
no refresh token in SPA -> less token theft impact if the browser is compromised
refresh token in SPA -> better user experience but higher risk if XSS happens
```

If a SPA does not use refresh tokens, the user does not always need to type the password again whenever the access token expires. The client can redirect to the auth server again. If the user still has an auth-server session, the auth server can issue a new authorization code without showing the login form again.

```text
access token expires
-> redirect to auth server
-> auth server sees existing login session
-> new authorization code
-> new access token
```

### Backend-for-Frontend

For serious browser applications, such as e-commerce, a common architecture is Backend-for-Frontend (BFF):

```text
browser SPA -> backend session cookie -> backend stores OAuth tokens server-side
```

A BFF is usually a backend built specifically for one frontend experience. For example, a JavaScript SPA can call a Spring Boot BFF instead of storing OAuth tokens in the browser.

This project's `client-server` is slightly different: it is a server-side Spring Boot web client using Thymeleaf. It renders HTML on the server and also acts as the OAuth2/OIDC client. It is not a pure SPA+BFF split, but the security idea is similar: the browser does not need to directly store OAuth refresh tokens.

In that model, the browser does not store OAuth access tokens or refresh tokens directly. The browser stores only a session cookie for the BFF:

```text
browser stores: SESSION=abc123
BFF stores: access token + refresh token
```

The cookie is sent automatically by the browser when it calls the BFF:

```http
GET /api/orders
Cookie: SESSION=abc123
```

Then the BFF looks up the server-side session and uses the access token when it calls the resource server:

```text
browser -> sends request to BFF with session cookie
BFF -> sends request to resource server with access token
```

More explicitly:

```text
1. Browser calls BFF:
   GET /api/orders
   Cookie: SESSION=abc123

2. BFF looks up the server-side session:
   abc123 -> access_token=A1
   abc123 -> refresh_token=R1

3. BFF calls the resource server:
   GET /orders
   Authorization: Bearer A1

4. Resource server validates A1 and returns protected data.
```

So the BFF can be a small backend service that manages login, keeps the browser session, stores or retrieves OAuth tokens server-side and calls resource servers on behalf of the browser.

The cookie should be configured with security attributes:

- `HttpOnly`: JavaScript cannot read the cookie with `document.cookie`.
- `Secure`: the browser sends the cookie only over HTTPS.
- `SameSite=Lax`: the browser sends the cookie when the user is navigating your site, but usually does not send it when another site tries to make a hidden request to your site.

The cookie is not necessarily encrypted. In many systems, it is an opaque random session ID. The important point is that the cookie should not contain OAuth access tokens or refresh tokens. The sensitive OAuth tokens stay server-side.

### Session Cookie vs Sticky Sessions

#### Session Cookie

In the BFF pattern, the BFF is the server-side client component. After login succeeds, the BFF creates the session cookie. The browser SPA receives the cookie, but the BFF creates and validates it. It is usually an opaque random value:

```text
SESSION=abc123
```

A cookie is a small name/value record stored by the browser for a website. Historically browsers stored cookies in files. Modern browsers may store them in internal databases. For web development, the important part is the HTTP behavior.

The BFF sends the cookie to the browser with `Set-Cookie`:

```http
HTTP/1.1 200 OK
Set-Cookie: SESSION=abc123; HttpOnly; Secure; SameSite=Lax
```

The browser sends that value back to the BFF on later requests with the `Cookie` header:

```http
GET /api/orders
Cookie: SESSION=abc123
```

The cookie is basically saying:

```text
this browser has session abc123
```

The cookie should usually contain only an opaque session ID. It does not need to contain the username or roles and it should not contain OAuth access tokens or refresh tokens. The BFF uses the session ID to look up server-side state:

```text
abc123 -> user=user
abc123 -> access token
abc123 -> refresh token
```

A more concrete example:

```text
Browser cookie:
  SESSION=abc123

BFF session store:
  abc123 -> user=user
  abc123 -> access_token=A1
  abc123 -> refresh_token=R1
```

So `abc123` is like a lookup key. The browser has the key. The BFF has the real session data.

#### Sticky Sessions

Sticky sessions are a load balancer routing strategy that keeps the same browser going to the same backend instance:

```text
request 1 -> BFF instance A
request 2 -> BFF instance A
request 3 -> BFF instance A
```

Sticky sessions are sometimes used when sessions are stored only in memory inside one backend instance.

In Java web applications, the session cookie is often named `JSESSIONID`.

Typical flow:

```text
1. Browser sends first request.
2. Java web app creates an HTTP session.
3. Java web app returns: Set-Cookie: JSESSIONID=abc123
4. Browser stores the cookie.
5. Browser sends later requests with: Cookie: JSESSIONID=abc123
6. Java web app uses abc123 to find the server-side session.
```

With sticky sessions, a load balancer may use that cookie to keep sending the browser to the same backend instance:

```text
Cookie: JSESSIONID=abc123
load balancer routes abc123 -> BFF instance A
```

#### Production Recommendation

```text
BFF instances -> shared session store like Redis
browser -> session cookie
load balancer -> no sticky session required
```

With a shared session store, every BFF instance can read the session by ID, so the load balancer does not need to pin the browser to one backend instance.

#### BFF Bottleneck

The BFF can become a bottleneck because browser traffic goes through it before reaching backend APIs. In production, keep the BFF small, fast, horizontally scalable and monitored.

Typical production shape:

```text
browser
-> load balancer
-> BFF instance A/B/C
-> Redis session store
-> resource servers/APIs
```

The BFF improves browser token security, but it adds an operational component that must be scaled and monitored.

### BFF And Stateless JWTs

A BFF with server-side sessions gives up some of the fully stateless style often associated with JWTs. That is usually intentional.

The browser-facing side becomes stateful:

```text
browser -> sends request to BFF with SESSION cookie
BFF -> looks up session in session store
```

But the resource server can still validate JWT access tokens statelessly:

```text
BFF -> sends request to resource server with access token
resource server -> validates JWT signature, issuer, audience, expiration and scopes
```

So the split is:

```text
Pure SPA with JWTs:
  more stateless
  browser handles tokens
  higher token exposure risk

BFF/server-side session:
  more stateful at the browser edge
  tokens stay server-side
  better browser security posture
```

### Practical Guidance

So the practical guidance is:

```text
pure SPA -> avoid refresh tokens if possible or use rotation plus strong XSS defenses
e-commerce / high-value app -> prefer BFF or server-side session
server-side web app -> refresh tokens are more acceptable because the server can store them
```

This project's `client-server` is a server-side OAuth2/OIDC web client, not a pure JavaScript SPA. That makes refresh tokens more reasonable here because the OAuth client state is handled on the server side.

### Refresh Token Rotation

This project rotates refresh tokens for clients that receive them:

```text
old refresh token -> used once
auth server returns -> new access token + new refresh token
```

Practical example:

```text
1. Client has:
   access_token=A1
   refresh_token=R1

2. A1 expires.

3. Client sends R1 to the auth server.

4. Auth server validates R1, invalidates R1 and returns:
   access_token=A2
   refresh_token=R2

5. Client stores R2 and stops using R1.
```

If someone tries to use `R1` again after step 4, the auth server can treat that as refresh token reuse. That is suspicious because a rotated refresh token should only be used once. Depending on policy, the auth server can reject the refresh token session.

Rotation happens each time the refresh token is used, not only when the overall refresh-token lifetime is ending.

Example with a mobile app:

```text
access token lifetime: 30 minutes
refresh token session lifetime: 90 days

09:00 A1 expires
09:00 use R1 -> auth server returns A2 + R2

09:30 A2 expires
09:30 use R2 -> auth server returns A3 + R3

10:00 A3 expires
10:00 use R3 -> auth server returns A4 + R4
```

In that example, the same refresh token value is not kept for 90 days. The exact refresh token changes on every refresh request.

The 90 days means the overall login/session can keep being renewed for up to 90 days, depending on policy. See [Typical Token Lifetime Examples](#typical-token-lifetime-examples) for the web and mobile examples.

Reference: [OAuth 2.1 Authorization Framework draft](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-15).

## JWT, JWS And Keys

A JWT is a JSON Web Token. It is a compact token format for carrying claims.

This project signs JWTs as JWS tokens. JWS means JSON Web Signature.

That means the token is signed, not encrypted. JWTs are readable by default. The token content can be read, but it cannot be changed without being detected.

The resource server reads the JWT claims, then verifies the signature to prove the token was issued by the trusted authorization server.

You can paste a JWT into [jwt.io](https://www.jwt.io/) to inspect the header and payload. This works because a normal signed JWT is Base64URL-encoded, not encrypted.

The public key is not used to decrypt the JWT content. The content is already readable. The public key is used to verify the signature:

```text
decode JWT -> read header and payload
verify JWT -> check signature with public key
```

The `auth-server` generates an RSA key pair at startup:

- the private key signs tokens
- the public key is published through the Authorization Server JWK set endpoint
- the resource server uses the public key to verify token signatures

Because the key is generated at startup in this learning project, restarting the auth server creates a new key. Tokens signed with the previous key may stop validating after restart.

## Issuer

The issuer is the identity of the Authorization Server that minted the token.

In local configuration, the resource server trusts:

```text
http://host.docker.internal:9000
```

The issuer configured in `auth-server` must match the issuer configured in `resource-server`. If a token says it was issued by `http://localhost:9000` but the resource server trusts `http://host.docker.internal:9000`, validation fails.

## Audience

The audience tells which resource server the access token is meant for.

This project uses:

```text
api://resource-server
```

The auth server adds this value to the JWT `aud` claim for configured clients. The resource server checks that the `aud` claim contains the expected value.

Audience validation matters because it prevents a token minted for one API from being accepted by another API.

## Token Validation

The resource server validates JWT access tokens before serving protected API requests.

In practical terms, the resource server asks:

> Is this access token valid, issued by my trusted auth server, meant for me and does it contain the permission required for this endpoint?

Validation includes:

- signature validation using the auth server public key
- issuer validation
- expiration validation
- audience validation
- scope to authority mapping

Spring Security maps `scope` or `scp` claims to authorities with the `SCOPE_` prefix. A token with `api.read` becomes `SCOPE_api.read`.

## Token Expiration

Short-lived access tokens are a good practice. A common maximum for many systems is around 1 hour, with shorter lifetimes preferred when practical.

This project uses short access tokens:

```text
access token: 5 minutes
refresh token: 60 minutes where enabled
```

These project values are conservative learning defaults, not a universal market standard. They make expiration and refresh behavior easy to see during local testing.

Common real-world ranges are usually more like:

```text
access token:
  5 minutes to 1 hour

refresh token:
  hours, days, weeks or longer
```

### Typical Token Lifetime Examples

```text
high-risk app / banking / admin:
  access token: 5-15 minutes
  refresh token session: longer than the access token, but still short for security
  refresh token value: rotated on each refresh request

normal web app / e-commerce:
  access token: 10-60 minutes
  refresh token session: hours to days or weeks
  refresh token value: usually rotated on each refresh request

mobile app:
  access token: 15-60 minutes
  refresh token session: days to months
  refresh token value: strongly protected and rotated on each refresh request
```

Short access token lifetimes reduce the impact of a leaked token. Refresh tokens need stronger handling because they can be used to obtain new access tokens.

Access tokens are normally shorter-lived than refresh tokens:

```text
access token -> short lifetime, used often and sent to APIs
refresh token -> longer lifetime, used rarely and sent only to the auth server
```

This is not a protocol law, but it is the normal useful design. If a refresh token had the same lifetime or a shorter lifetime than the access token, it would not help much.

This project also disables refresh token reuse for clients that receive refresh tokens, which rotates refresh tokens.

Clients should normally reuse a valid access token until it expires or is close to expiring:

```text
access token is still valid -> reuse it for API calls
access token expired or almost expired -> use refresh token or login flow to get a new one
```

Requesting a new access token before every API call is usually a bad fit for JWT access tokens. It adds load to the auth server and removes much of the benefit of local JWT validation.

## Token Invalidation

Token invalidation means making an existing token no longer usable before its normal expiration time.

For JWT access tokens, the usual design is stateless validation:

```text
resource server -> validates JWT signature, issuer, audience, expiration and scopes
```

That means the resource server does not normally call the auth server on every API request. This is one of the main benefits of JWT access tokens.

The tradeoff is important:

```text
JWT access token is issued
-> resource server can validate it locally
-> token usually stays valid until exp
```

So for JWT access tokens, the normal good practice is:

```text
short access token lifetime
strong refresh token handling
logout invalidates sessions and refresh tokens
```

If a JWT access token leaks, the short expiration limits how long it can be used. In this project, access tokens last 5 minutes.

Logout usually does not magically erase a JWT access token that was already issued. Since the token is self-contained, a resource server that only validates JWTs locally may keep accepting it until it expires.

What logout should normally invalidate is server-side state:

- auth server login session
- client app session
- refresh tokens
- authorization grants

Example:

```text
1. User logs out.
2. Client app removes its own session.
3. Auth server removes its login session.
4. Auth server revokes refresh tokens or grants.
5. Existing JWT access tokens expire naturally soon after.
```

If a system needs immediate access-token revocation, it needs extra server-side checks. Common options are:

- opaque access tokens with introspection
- JWT denylist checked by the resource server
- very short JWT access token lifetime

Opaque tokens make immediate revocation easier because the resource server asks the auth server whether the token is still active. JWT denylists also work, but they add shared state to the resource server path.

So the practical idea is:

```text
JWT access token -> short-lived and usually not centrally checked on every request
refresh token -> revocable and rotated
login session -> revocable at the auth server
client session -> revocable at the client app or BFF
```

That is why short JWT access-token expiration is so important.

## PKCE

PKCE means Proof Key for Code Exchange.

PKCE protects the authorization code flow. The client creates a temporary secret, sends a derived challenge during authorization and later proves it still has the original secret when exchanging the authorization code for tokens.

This helps prevent stolen authorization codes from being exchanged by an attacker.

In this project, `client-server-pkce` is a public client:

- it has no client secret
- it uses authorization code flow
- it requires PKCE

## Opaque Tokens

An opaque token is a reference access token. The token value itself does not contain readable claims for the resource server.

With opaque tokens, the resource server usually sends the token back to the authorization server for introspection. The authorization server then answers whether the token is active and what claims or permissions it represents.

Introspection means the resource server asks the authorization server to inspect the token:

```text
resource server -> auth server: is this token active?
auth server -> resource server: active=true, sub=user, scope=api.read
```

It is called introspection because the authorization server looks up the token state and explains what the token means. The resource server cannot understand an opaque token by reading it directly.

For JWT access tokens, introspection is usually not needed:

```text
JWT token -> resource server reads claims and validates signature locally
opaque token -> resource server asks auth server through introspection
```

Opaque tokens are useful when you want central server-side token state, immediate revocation checks or tokens that do not expose claims to the caller.

The practical difference:

```text
JWT access token:
  client reuses token until expiration
  resource server validates token locally
  auth server is not called on every API request

opaque access token:
  client sends token to resource server
  resource server asks auth server if token is active
  auth server can centrally reject revoked tokens
```

So if every API request must be checked centrally with the auth server, an opaque token may be a better model than pretending a JWT is fully stateless while still adding server-side checks on every request.

This project does not use opaque tokens. It uses signed JWT access tokens, so the resource server can validate tokens locally after it discovers the issuer metadata and public keys.
