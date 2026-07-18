# Browser Clients And Token Lifecycle

This guide covers refresh-token and browser-session design, including SPA and BFF tradeoffs, cookies, sticky sessions, rotation, expiration and invalidation.

The project-specific statements describe the current server-side Spring Boot client and registered-client token settings. The SPA, BFF, Redis/shared-session and production lifetime examples are architectural guidance, not additional components implemented by this repository.

See [OAuth2 And OIDC Overview](oauth2-oidc-overview.md) for the implemented OAuth2/OIDC flow.

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

- `client-server-pkce`: supports refresh tokens. It is a confidential browser login client using authorization code + PKCE.
- `client-postman-confidential`: supports refresh tokens. It is a confidential client with a client secret.
- `client-server-postman-pkce`: supports refresh tokens. It is a confidential Postman client using authorization code + PKCE.
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
browser stores: __Host-Http-SESSION=abc123
BFF stores: access token + refresh token
```

The cookie is sent automatically by the browser when it calls the BFF:

```http
GET /api/orders
Cookie: __Host-Http-SESSION=abc123
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
   Cookie: __Host-Http-SESSION=abc123

2. BFF looks up the server-side session:
   abc123 -> access_token=A1
   abc123 -> refresh_token=R1

3. BFF calls the resource server:
   GET /orders
   Authorization: Bearer A1

4. Resource server validates A1 and returns protected data.
```

So the BFF can be a small backend service that manages login, keeps the browser session, stores or retrieves OAuth tokens server-side and calls resource servers on behalf of the browser.

The OAuth browser-based apps draft 27 gives BFF session cookies a stricter security profile:

- `Secure` and `HttpOnly` are required.
- `SameSite=Strict` is preferred unless the deployment has a documented reason to relax it.
- `Path=/` is preferred.
- `Domain` should be omitted so that the cookie is host-only.
- The name should use a secure prefix that shows how the cookie was set, such as `__Host-Http-` where supported.

Because browsers attach cookies automatically, these attributes do not replace CSRF protection. A BFF must implement a CSRF defense for its cookie-authenticated endpoints, for example framework anti-forgery support or a carefully configured CORS policy that requires a custom request header.

The cookie is not necessarily encrypted. In many systems, it is an opaque random session ID. The important point is that the cookie should not contain OAuth access tokens or refresh tokens. The sensitive OAuth tokens stay server-side.

### Session Cookie vs Sticky Sessions

#### Session Cookie

In the BFF pattern, the BFF is the server-side client component. After login succeeds, the BFF creates the session cookie. The browser SPA receives the cookie, but the BFF creates and validates it. It is usually an opaque random value:

```text
__Host-Http-SESSION=abc123
```

A cookie is a small name/value record stored by the browser for a website. Historically browsers stored cookies in files. Modern browsers may store them in internal databases. For web development, the important part is the HTTP behavior.

The BFF sends the cookie to the browser with `Set-Cookie`:

```http
HTTP/1.1 200 OK
Set-Cookie: __Host-Http-SESSION=abc123; Path=/; HttpOnly; Secure; SameSite=Strict
```

The browser sends that value back to the BFF on later requests with the `Cookie` header:

```http
GET /api/orders
Cookie: __Host-Http-SESSION=abc123
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
  __Host-Http-SESSION=abc123

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

### Refresh Token Rotation And Replay

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

If someone tries to use `R1` again after step 4, rejecting that inactive token is not by itself full replay detection. A stronger design detects that a previously rotated token was presented and then revokes the active refresh token or the entire grant family, because an attacker and the legitimate client may now hold different descendants of the same grant.

These controls are separate:

- rotation replaces the refresh token after a successful refresh;
- replay detection recognizes reuse of a previously rotated token;
- family revocation invalidates the active descendants or grant after replay;
- an absolute lifetime limits the total age of the refresh-token grant;
- an inactivity lifetime expires the grant after it has not been used for a configured period.

This project configures only simple rotation with Spring Authorization Server 7.1 by setting `reuseRefreshTokens(false)`. An old rotated token is no longer active, but the project does not add replay-family tracking or family-revocation policy.

Rotation happens each time the refresh token is used, not only when the overall refresh-token lifetime is ending.

Example of a separate policy a mobile app could adopt:

```text
access token lifetime: 30 minutes
absolute refresh token grant lifetime: 90 days

09:00 A1 expires
09:00 use R1 -> auth server returns A2 + R2

09:30 A2 expires
09:30 use R2 -> auth server returns A3 + R3

10:00 A3 expires
10:00 use R3 -> auth server returns A4 + R4
```

In that example, the same refresh token value is not kept for 90 days. The exact refresh token changes on every refresh request.

The 90 days in this hypothetical policy limits the overall grant even though individual refresh-token values rotate. It is not configured by this project.

Reference: [OAuth 2.1 Authorization Framework draft](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-15).

## Token Expiration

Short-lived access tokens reduce the exposure window after theft, but OAuth does not define a universal one-hour maximum. Lifetimes are deployment policy and should reflect the token's privileges, sender constraints, revocation model and usability requirements.

This project uses short access tokens:

```text
access token: 5 minutes
each refresh token: 60 minutes where enabled
```

These are learning-project settings, not recommended maxima or market standards. Spring Authorization Server applies the configured 60-minute TTL to each newly issued refresh token. The project does not configure a separate absolute grant lifetime or inactivity timeout, so rotation can issue another individually valid token without establishing a 60-minute ceiling for the whole login grant.

### Example Token Lifetime Policies

The following values are illustrative policy examples, not protocol requirements or universal recommendations:

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

This project disables refresh token reuse for clients that receive refresh tokens, which provides the simple rotation behavior described above. It should not be read as replay-family detection.

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

So a common JWT access-token design combines:

```text
short access token lifetime
strong refresh token handling
explicit session termination and token-revocation policy
```

If a JWT access token leaks, the short expiration limits how long it can be used. In this project, access tokens last 5 minutes.

Logout usually does not magically erase a JWT access token that was already issued. Since the token is self-contained, a resource server that only validates JWTs locally may keep accepting it until it expires.

OIDC logout and OAuth token revocation solve different problems:

```text
OIDC RP-initiated logout -> ends login sessions at the client and OpenID Provider
OAuth RFC 7009 revocation -> explicitly asks the authorization server to revoke a token
```

This project's `client-server` invalidates its local session and uses OIDC RP-initiated logout to end the authorization-server login session. It does not make an explicit RFC 7009 call to the token revocation endpoint. Therefore the documentation does not assume that logout revokes issued refresh tokens or authorization grants. Existing JWT access tokens can remain usable until their five-minute expiration, and issued refresh tokens follow authorization-server token state and expiry unless separately revoked.

In a production design, decide explicitly whether logout also invokes token revocation or revokes the associated grant. RFC 7009 requires an authorization server that exposes revocation to support refresh-token revocation and recommends support for access-token revocation; the effect on related tokens is server policy.

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

References:

- [OAuth 2.0 Security Best Current Practice (RFC 9700)](https://www.rfc-editor.org/rfc/rfc9700.html)
- [OAuth 2.0 Token Revocation (RFC 7009)](https://www.rfc-editor.org/rfc/rfc7009.html)
- [OAuth 2.0 for Browser-Based Applications, draft 27](https://www.ietf.org/archive/id/draft-ietf-oauth-browser-based-apps-27.html)
