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
4. The client requests `openid`, `profile`, `api.read` and `offline_access`. The auth server checks that those scopes are allowed for that client and asks for user consent when consent is required. Here, `api.read` requests API permission, while `offline_access` asks for offline access and an associated refresh token under the provider's consent policy.
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

With simple rotation enabled, the auth server returns a new refresh token and invalidates the old one:

```text
R1 -> used once -> returns access_token=A2 + refresh_token=R2
R1 -> reused later -> inactive, reject
```

Rotation does not happen on every normal API call. Normal API calls use the access token. The refresh token is sent only to the authorization server when the client needs fresh tokens.

Rotation alone does not imply replay detection or refresh-token-family revocation. This project configures Spring Authorization Server 7.1 to rotate tokens with `reuseRefreshTokens(false)`, but it does not add policy that identifies reuse as an attack and revokes all active descendants of the grant.

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

## Related Architecture Guides

This overview stays focused on behavior implemented by this repository. For broader architectural guidance, see:

- [Identity Providers And Multitenancy](identity-providers-and-multitenancy.md) for conceptual guidance about external identity providers, signup, local user mirrors, organizations, tenants, roles and claims. That architecture is not implemented here.
- [Browser Clients And Token Lifecycle](browser-clients-and-token-lifecycle.md) for refresh-token handling, SPA and BFF designs, cookies, sticky sessions, rotation, expiration and invalidation.

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

In this project, `auth-server` also does both jobs. It authenticates the in-memory account `user` and issues OAuth2/OIDC tokens.

In this project, the client asks for these scopes:

```text
openid, profile, api.read, offline_access
```

Think of them like this:

```text
openid -> turn this OAuth2 request into an OIDC login request
profile -> ask for standard user profile claims
api.read -> ask for permission to call the resource server read API
offline_access -> request offline access and an associated refresh token
```

- `openid` asks for OIDC login and an ID token. It tells the auth server: "I want an ID token for this signed-in user."
- `profile` asks for optional standard profile claims. It tells the auth server: "I want basic user profile data such as name, username or picture when available."
- `api.read` asks for API access. It tells the auth server: "I want permission to call the resource server read API."
- `offline_access` requests access that can continue when the user is not present. Under [OIDC Core offline access](https://openid.net/specs/openid-connect-core-1_0.html#OfflineAccess), it is a request for offline access and normally requires appropriate consent; it is not an API permission and does not guarantee that the provider will issue a refresh token. The registered interactive clients in this project allow this scope and the refresh-token grant.

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

A provider could return optional OIDC identity claims like the following. This is illustrative; this project only defines the local account `user` and does not customize a `name` claim:

```json
{
  "sub": "example-user-id",
  "name": "Example User",
  "preferred_username": "example-user",
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

1. The client requests `openid`, `profile`, `api.read` and `offline_access` during login.

2. For access tokens, the customizer removes exactly `openid` and `profile` from the authorized scope set. It does not generically classify every remaining value as an API scope, so `offline_access` can remain alongside `api.read`:

    ```json
    {
      "scope": "api.read offline_access"
    }
    ```

    That is behavior specific to the current customizer, not a statement that `offline_access` authorizes the resource API. The `/tasks` authorization decision still depends on `api.read` and its mapped `SCOPE_api.read` authority.

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
  "aud": "api://resource-server",
  "scope": "api.read offline_access",
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
  "scope": "api.read offline_access",
  "exp": 1710000300
}
```

An ID token is meant for the client application:

```json
{
  "iss": "http://host.docker.internal:9000",
  "sub": "user",
  "aud": "client-server-pkce",
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

The client app can use ID token claims to show who signed in, such as the subject and any optional profile claims the provider actually supplies. The resource server usually does not need display profile data. It needs claims for API authorization, such as issuer, subject, audience, scopes and expiration.

This project does not use opaque tokens.

Because this project uses signed JWT access tokens, the access token is also transparent to the resource server. Transparent means the resource server can read the token claims directly and validate the token signature locally.

That does not mean the token is secret. A signed JWT is usually readable by whoever has the token. The signature proves the token was issued by the trusted authorization server and was not changed after it was issued.

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

The default local client/resource configuration and the Docker Compose stack use:

```text
http://host.docker.internal:9000
```

The k3d stack uses a distinct host alias:

```text
http://host.k3d.internal:9000
```

The issuer configured in `auth-server` must match the issuer configured in `resource-server` for the selected runtime. A token minted with `http://host.docker.internal:9000` is rejected by a resource server configured for `http://host.k3d.internal:9000`, and the reverse is also true.

## Audience

The audience tells which resource server the access token is meant for.

This project uses:

```text
api://resource-server
```

The auth server adds this value to the JWT `aud` claim for configured clients. The resource server checks that the `aud` claim contains the expected value.

Audience validation matters because it prevents a token minted for one API from being accepted by another API.

## Token Validation

Access-token validation and ID-token validation happen at different recipients and answer different questions.

### Access Token Validation

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

### ID Token Validation

The OIDC client validates an ID token for the authentication response. In addition to verifying the signature, issuer and expiration, it must verify that its own client ID is an audience. If the ID token has multiple audiences, the client should verify the OIDC `azp` (authorized party) claim; when `azp` is present, the client should verify that it identifies this client. If the authentication request included a `nonce`, the client must verify that the ID token contains the same value.

ID-token validation also depends on the flow and response received, so clients should use a maintained OIDC library instead of implementing these checks manually. This project's Spring Security OAuth2 client performs the OIDC protocol processing; the resource server's JWT validator is for access tokens and is not a substitute for ID-token validation.

## Token Lifetimes In This Project

The registered interactive clients use 5-minute access tokens and a 60-minute TTL for each refresh token. Refresh-token reuse is disabled, so a successful refresh rotates the refresh token. The project does not configure a separate absolute refresh-grant lifetime, inactivity timeout, replay-family detection or family revocation. The machine-to-machine client uses a 5-minute access token and no refresh token.

For lifecycle design guidance, including SPA and BFF storage, rotation, expiration and invalidation, see [Browser Clients And Token Lifecycle](browser-clients-and-token-lifecycle.md).

## Logout And Revocation

The client uses [OIDC RP-initiated logout](https://openid.net/specs/openid-connect-rpinitiated-1_0.html): it clears the client application session and redirects to the OpenID Provider's end-session endpoint to end the authorization-server login session. OAuth token revocation is a separate operation defined by [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009.html).

This project does not explicitly call the RFC 7009 token revocation endpoint during logout. It therefore does not claim that OIDC logout revokes refresh tokens, grants or already-issued JWT access tokens. The JWT access tokens remain locally valid at the resource server until expiration unless an additional revocation mechanism is introduced.

## PKCE

PKCE means Proof Key for Code Exchange.

PKCE protects the authorization code flow. The client creates a temporary secret, sends a derived challenge during authorization and later proves it still has the original secret when exchanging the authorization code for tokens.

This helps prevent stolen authorization codes from being exchanged by an attacker.

In this project, `client-server-pkce` is a confidential client using PKCE:

- it has a client secret
- it uses authorization code flow
- it requires PKCE

Reference: [Proof Key for Code Exchange (RFC 7636)](https://www.rfc-editor.org/rfc/rfc7636.html).

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
