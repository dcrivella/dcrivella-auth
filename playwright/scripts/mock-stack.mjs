import {
  createHash,
  generateKeyPairSync,
  randomBytes,
  randomUUID,
  sign,
  verify,
} from 'node:crypto';
import { once } from 'node:events';
import { existsSync } from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { spawn } from 'node:child_process';

const CLIENT_ID = 'client-server-pkce';
const CLIENT_SECRET = 'secret-client-server';
const REGISTRATION_ID = 'client-server-pkce-oidc';
const ACCESS_TOKEN_AUDIENCE = 'api://resource-server';
const KEY_ID = 'playwright-mock-key';
const SESSION_COOKIE = 'MOCK_AUTH_SESSION';
const FLOW_COOKIE = 'MOCK_AUTH_FLOW';
const PLAYWRIGHT_USERNAME = process.env.DCRIVELLA_PLAYWRIGHT_USERNAME ?? 'playwright';
const PLAYWRIGHT_PASSWORD = process.env.DCRIVELLA_PLAYWRIGHT_PASSWORD ?? 'playwright-pass';

const repositoryRoot = process.env.DCRIVELLA_REPOSITORY_ROOT;
if (!repositoryRoot) {
  throw new Error('DCRIVELLA_REPOSITORY_ROOT is required');
}

const clientServerUrl = localHttpUrl(
  process.env.DCRIVELLA_PLAYWRIGHT_CLIENT_URL ?? 'http://localhost:18080',
  'client server',
);
const authServerUrl = localHttpUrl(
  process.env.DCRIVELLA_PLAYWRIGHT_AUTH_SERVER_URL ?? 'http://localhost:19000',
  'authorization server',
);
const resourceServerUrl = localHttpUrl(
  process.env.DCRIVELLA_PLAYWRIGHT_RESOURCE_SERVER_URL ?? 'http://localhost:19001',
  'resource server',
);
const redirectUri = new URL(
  `/login/oauth2/code/${REGISTRATION_ID}`,
  clientServerUrl,
).toString();
const postLogoutRedirectUri = new URL('/', clientServerUrl).toString();
const clientServerJar = path.join(
  repositoryRoot,
  'client-server',
  'build',
  'libs',
  'client-server-playwright.jar',
);
if (!existsSync(clientServerJar)) {
  throw new Error(`Missing ${clientServerJar}; run the mock:playwright mise task`);
}

const { privateKey, publicKey } = generateKeyPairSync('rsa', {
  modulusLength: 2048,
});
const publicJwk = publicKey.export({ format: 'jwk' });
Object.assign(publicJwk, {
  alg: 'RS256',
  kid: KEY_ID,
  use: 'sig',
});

const flows = new Map();
const authorizationCodes = new Map();
const authorizedScopesByGrant = new Map();

const authServer = http.createServer(async (request, response) => {
  try {
    await handleAuthorizationServerRequest(request, response);
  } catch (error) {
    console.error('Mock authorization server request failed:', safeErrorMessage(error));
    send(response, 500, 'Mock authorization server error');
  }
});

const resourceServer = http.createServer((request, response) => {
  try {
    handleResourceServerRequest(request, response);
  } catch (error) {
    console.error('Mock resource server request failed:', safeErrorMessage(error));
    send(response, 500, 'Mock resource server error');
  }
});

await Promise.all([
  listen(authServer, portOf(authServerUrl)),
  listen(resourceServer, portOf(resourceServerUrl)),
]);

console.log(
  `Playwright mock OIDC and resource servers are ready on ${authServerUrl.origin} and ${resourceServerUrl.origin}`,
);

const clientProcess = spawn(
  process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java',
  ['-jar', clientServerJar],
  {
    cwd: repositoryRoot,
    env: {
      ...process.env,
      API_RESOURCE_SERVER_URL: resourceServerUrl.origin,
      LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY: 'WARN',
      LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY_OAUTH2: 'WARN',
      LOGGING_LEVEL_ROOT: 'WARN',
      SERVER_PORT: String(portOf(clientServerUrl)),
      SPRING_MAIN_BANNER_MODE: 'off',
      SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_SPRING_ISSUER_URI: authServerUrl.origin,
    },
    stdio: 'inherit',
  },
);

let stopping = false;

clientProcess.once('error', (error) => {
  console.error('Could not start the mock client server:', safeErrorMessage(error));
  void shutdown(1);
});

clientProcess.once('exit', (code, signal) => {
  if (!stopping) {
    console.error(
      `Mock client server stopped unexpectedly (${signal ? `signal ${signal}` : `exit ${code ?? 1}`})`,
    );
    void shutdown(code ?? 1);
  }
});

process.once('SIGINT', () => void shutdown(0));
process.once('SIGTERM', () => void shutdown(0));

async function handleAuthorizationServerRequest(request, response) {
  const requestUrl = new URL(request.url ?? '/', authServerUrl);

  if (request.method === 'GET' && requestUrl.pathname === '/.well-known/openid-configuration') {
    sendJson(response, 200, {
      issuer: authServerUrl.origin,
      authorization_endpoint: new URL('/oauth2/authorize', authServerUrl).toString(),
      token_endpoint: new URL('/oauth2/token', authServerUrl).toString(),
      jwks_uri: new URL('/oauth2/jwks', authServerUrl).toString(),
      end_session_endpoint: new URL('/connect/logout', authServerUrl).toString(),
      response_types_supported: ['code'],
      grant_types_supported: ['authorization_code', 'refresh_token'],
      subject_types_supported: ['public'],
      id_token_signing_alg_values_supported: ['RS256'],
      token_endpoint_auth_methods_supported: ['client_secret_basic'],
      scopes_supported: ['openid', 'profile', 'api.read', 'offline_access'],
      code_challenge_methods_supported: ['S256'],
    });
    return;
  }

  if (request.method === 'GET' && requestUrl.pathname === '/oauth2/jwks') {
    sendJson(response, 200, { keys: [publicJwk] });
    return;
  }

  if (request.method === 'POST' && requestUrl.pathname === '/__test/reset') {
    flows.clear();
    authorizationCodes.clear();
    authorizedScopesByGrant.clear();
    send(response, 204, '');
    return;
  }

  if (request.method === 'GET' && requestUrl.pathname === '/login') {
    const flow = findFlow(request, requestUrl);
    if (!flow) {
      send(response, 400, 'Missing authorization flow');
      return;
    }
    sendHtml(response, 200, loginPage(requestUrl.searchParams.has('error')));
    return;
  }

  if (request.method === 'POST' && requestUrl.pathname === '/login') {
    const flow = findFlow(request, requestUrl);
    if (!flow) {
      send(response, 400, 'Missing authorization flow');
      return;
    }

    const form = await readForm(request);
    if (
      form.get('username') !== PLAYWRIGHT_USERNAME ||
      form.get('password') !== PLAYWRIGHT_PASSWORD
    ) {
      redirect(response, '/login?error');
      return;
    }

    flow.authenticated = true;
    flow.username = PLAYWRIGHT_USERNAME;
    redirect(response, `/oauth2/authorize?flow=${encodeURIComponent(flow.id)}`, {
      'Set-Cookie': `${SESSION_COOKIE}=authenticated; Path=/; HttpOnly; SameSite=Lax`,
    });
    return;
  }

  if (request.method === 'GET' && requestUrl.pathname === '/oauth2/authorize') {
    const existingFlow = findFlow(request, requestUrl);
    if (existingFlow) {
      if (!existingFlow.authenticated) {
        redirect(response, '/login');
        return;
      }
      if (requestUrl.searchParams.get('consent_action') === 'cancel') {
        rejectAuthorization(response, existingFlow);
        return;
      }
      sendHtml(response, 200, consentPage(existingFlow));
      return;
    }

    const flow = createFlow(requestUrl);
    flows.set(flow.id, flow);
    redirect(response, '/login', {
      'Set-Cookie': `${FLOW_COOKIE}=${flow.id}; Path=/; HttpOnly; SameSite=Lax`,
    });
    return;
  }

  if (request.method === 'POST' && requestUrl.pathname === '/oauth2/authorize') {
    const form = await readForm(request);
    const flow = flows.get(form.get('flow'));
    if (!flow || !flow.authenticated) {
      send(response, 400, 'Unknown authorization flow');
      return;
    }

    if (form.get('consent_action') === 'cancel') {
      rejectAuthorization(response, flow);
      return;
    }

    const selectedScopes = new Set(form.getAll('scope'));
    const authorizedScopes = scopesAuthorizedFor(flow);
    const grantedScopes = new Set(
      [...authorizedScopes, ...selectedScopes].filter((scope) => flow.requestedScopes.has(scope)),
    );
    grantedScopes.add('openid');
    authorizedScopesByGrant.set(grantKey(flow), new Set(grantedScopes));

    const code = randomBytes(32).toString('base64url');
    authorizationCodes.set(code, {
      clientId: flow.clientId,
      codeChallenge: flow.codeChallenge,
      nonce: flow.nonce,
      redirectUri: flow.redirectUri,
      scopes: grantedScopes,
      username: flow.username,
    });
    flows.delete(flow.id);

    const callbackUrl = new URL(flow.redirectUri);
    callbackUrl.searchParams.set('code', code);
    callbackUrl.searchParams.set('state', flow.state);
    redirect(response, callbackUrl.toString());
    return;
  }

  if (request.method === 'POST' && requestUrl.pathname === '/oauth2/token') {
    await issueTokens(request, response);
    return;
  }

  if (request.method === 'GET' && requestUrl.pathname === '/connect/logout') {
    const logoutRedirect = requestUrl.searchParams.get('post_logout_redirect_uri');
    const idTokenHint = requestUrl.searchParams.get('id_token_hint');
    if (logoutRedirect !== postLogoutRedirectUri || !idTokenHint || !verifyJwt(idTokenHint)) {
      send(response, 400, 'Invalid logout request');
      return;
    }

    redirect(response, logoutRedirect, {
      'Set-Cookie': [
        `${SESSION_COOKIE}=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax`,
        `${FLOW_COOKIE}=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax`,
      ],
    });
    return;
  }

  send(response, 404, 'Not found');
}

function handleResourceServerRequest(request, response) {
  const requestUrl = new URL(request.url ?? '/', resourceServerUrl);
  if (request.method !== 'GET' || requestUrl.pathname !== '/tasks') {
    send(response, 404, 'Not found');
    return;
  }

  const authorization = request.headers.authorization;
  if (!authorization?.startsWith('Bearer ')) {
    send(response, 401, 'Unauthorized');
    return;
  }

  const claims = verifyJwt(authorization.slice('Bearer '.length));
  if (!claims || claims.iss !== authServerUrl.origin || claims.aud !== ACCESS_TOKEN_AUDIENCE) {
    send(response, 401, 'Unauthorized');
    return;
  }

  const scopes = new Set(typeof claims.scope === 'string' ? claims.scope.split(' ') : []);
  if (!scopes.has('api.read')) {
    send(response, 403, 'insufficient_scope');
    return;
  }

  sendHtml(
    response,
    200,
    `<h1>Tasks for ${escapeHtml(claims.sub)}:</h1><ol><li>Task 1</li><li>Task 2</li><li>Task 3</li></ol>`,
  );
}

function createFlow(requestUrl) {
  const responseType = requestUrl.searchParams.get('response_type');
  const clientId = requestUrl.searchParams.get('client_id');
  const requestedRedirectUri = requestUrl.searchParams.get('redirect_uri');
  const state = requestUrl.searchParams.get('state');
  const nonce = requestUrl.searchParams.get('nonce');
  const codeChallenge = requestUrl.searchParams.get('code_challenge');
  const codeChallengeMethod = requestUrl.searchParams.get('code_challenge_method');

  if (
    responseType !== 'code' ||
    clientId !== CLIENT_ID ||
    requestedRedirectUri !== redirectUri ||
    !state ||
    !nonce ||
    !codeChallenge ||
    codeChallengeMethod !== 'S256'
  ) {
    throw new Error('Invalid mock authorization request');
  }

  const requestedScopes = new Set(
    (requestUrl.searchParams.get('scope') ?? '').split(' ').filter(Boolean),
  );
  if (!requestedScopes.has('openid')) {
    throw new Error('The mock authorization request must include openid');
  }

  return {
    id: randomUUID(),
    authenticated: false,
    clientId,
    codeChallenge,
    nonce,
    redirectUri: requestedRedirectUri,
    requestedScopes,
    state,
  };
}

async function issueTokens(request, response) {
  const credentials = basicCredentials(request.headers.authorization);
  if (credentials?.clientId !== CLIENT_ID || credentials.clientSecret !== CLIENT_SECRET) {
    sendOAuthError(response, 401, 'invalid_client');
    return;
  }

  const form = await readForm(request);
  const code = form.get('code');
  const authorization = authorizationCodes.get(code);
  const codeVerifier = form.get('code_verifier');
  if (
    form.get('grant_type') !== 'authorization_code' ||
    !authorization ||
    authorization.clientId !== CLIENT_ID ||
    form.get('redirect_uri') !== authorization.redirectUri ||
    !codeVerifier ||
    pkceChallenge(codeVerifier) !== authorization.codeChallenge
  ) {
    sendOAuthError(response, 400, 'invalid_grant');
    return;
  }
  authorizationCodes.delete(code);

  const now = Math.floor(Date.now() / 1000);
  const scope = [...authorization.scopes].join(' ');
  const sessionId = randomUUID();
  const accessToken = signJwt({
    iss: authServerUrl.origin,
    sub: authorization.username,
    aud: ACCESS_TOKEN_AUDIENCE,
    iat: now,
    nbf: now,
    exp: now + 300,
    jti: randomUUID(),
    sid: sessionId,
    scope,
  });
  const idToken = signJwt({
    iss: authServerUrl.origin,
    sub: authorization.username,
    aud: CLIENT_ID,
    azp: CLIENT_ID,
    iat: now,
    exp: now + 300,
    auth_time: now,
    nonce: authorization.nonce,
    sid: sessionId,
    name: authorization.username,
  });

  sendJson(
    response,
    200,
    {
      access_token: accessToken,
      expires_in: 300,
      id_token: idToken,
      scope,
      token_type: 'Bearer',
    },
    {
      'Cache-Control': 'no-store',
      Pragma: 'no-cache',
    },
  );
}

function rejectAuthorization(response, flow) {
  flows.delete(flow.id);
  const callbackUrl = new URL(flow.redirectUri);
  callbackUrl.searchParams.set('error', 'access_denied');
  callbackUrl.searchParams.set('error_description', 'The resource owner denied the request');
  callbackUrl.searchParams.set('state', flow.state);
  redirect(response, callbackUrl.toString());
}

function findFlow(request, requestUrl) {
  const flowId = requestUrl.searchParams.get('flow') ?? cookies(request)[FLOW_COOKIE];
  return flowId ? flows.get(flowId) : undefined;
}

function consentPage(flow) {
  const authorizedScopes = scopesAuthorizedFor(flow);
  const requestedScopes = ['profile', 'api.read', 'offline_access'].filter((scope) =>
    flow.requestedScopes.has(scope),
  );
  const newScopes = requestedScopes.filter((scope) => !authorizedScopes.has(scope));
  const existingScopes = ['openid', ...requestedScopes].filter((scope, index, all) =>
    authorizedScopes.has(scope) && all.indexOf(scope) === index,
  );

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Consent required</title>
  <style>${pageStyles()}</style>
</head>
<body>
  <main>
    <h1>Consent required</h1>
    <p><strong>${CLIENT_ID}</strong> wants to access your account <strong>${escapeHtml(flow.username)}</strong></p>
    <p>The following permissions are requested by the above app.<br>Please review these and consent if you approve.</p>
    <form method="post" action="/oauth2/authorize">
      <input type="hidden" name="flow" value="${escapeHtml(flow.id)}">
      <input type="hidden" name="scope" value="openid">
      ${newScopes.map(scopeCheckbox).join('')}
      <p>You have already granted the following permissions to the above app:</p>
      ${existingScopes.map(grantedScopeCheckbox).join('')}
      <button type="submit" name="consent_action" value="approve">Submit Consent</button>
    </form>
    <a href="/oauth2/authorize?flow=${encodeURIComponent(flow.id)}&amp;consent_action=cancel">Cancel</a>
    <small>Your consent to provide access is required.<br>If you do not approve, click Cancel, in which case no information will be shared with the app.</small>
  </main>
</body>
</html>`;
}

function scopesAuthorizedFor(flow) {
  return authorizedScopesByGrant.get(grantKey(flow)) ?? new Set(['openid']);
}

function grantKey(flow) {
  return `${flow.clientId}\u0000${flow.username}`;
}

function loginPage(hasError) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Please sign in</title>
  <style>${pageStyles()}</style>
</head>
<body>
  <main>
    <h1>Please sign in</h1>
    ${hasError ? '<p role="alert">Invalid username or password.</p>' : ''}
    <form method="post" action="/login">
      <input name="username" placeholder="Username" autocomplete="username">
      <input name="password" type="password" placeholder="Password" autocomplete="current-password">
      <button type="submit">Sign in</button>
    </form>
  </main>
</body>
</html>`;
}

function scopeCheckbox(scope) {
  return `<label><input type="checkbox" name="scope" value="${escapeHtml(scope)}"> ${escapeHtml(scope)}</label>`;
}

function grantedScopeCheckbox(scope) {
  return `<label><input type="checkbox" checked disabled> ${escapeHtml(scope)}</label>`;
}

function pageStyles() {
  return 'body{font-family:Arial,sans-serif;background:#f5f5f5}main{max-width:620px;margin:3rem auto;text-align:center}form{display:grid;gap:1rem;justify-content:center}input,button{font:inherit;padding:.65rem}label{display:block}a,small{display:block;margin-top:1.5rem}button{background:#086efd;color:white;border:0}';
}

function signJwt(claims) {
  const header = base64UrlJson({ alg: 'RS256', kid: KEY_ID, typ: 'JWT' });
  const payload = base64UrlJson(claims);
  const signingInput = `${header}.${payload}`;
  const signature = sign('RSA-SHA256', Buffer.from(signingInput), privateKey).toString(
    'base64url',
  );
  return `${signingInput}.${signature}`;
}

function verifyJwt(token) {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return undefined;
  }

  const [header, payload, signature] = parts;
  const valid = verify(
    'RSA-SHA256',
    Buffer.from(`${header}.${payload}`),
    publicKey,
    Buffer.from(signature, 'base64url'),
  );
  if (!valid) {
    return undefined;
  }

  const claims = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  if (typeof claims.exp !== 'number' || claims.exp <= Math.floor(Date.now() / 1000)) {
    return undefined;
  }
  return claims;
}

function base64UrlJson(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

function pkceChallenge(verifier) {
  return createHash('sha256').update(verifier).digest('base64url');
}

function basicCredentials(authorizationHeader) {
  if (!authorizationHeader?.startsWith('Basic ')) {
    return undefined;
  }
  const decoded = Buffer.from(authorizationHeader.slice('Basic '.length), 'base64').toString(
    'utf8',
  );
  const separator = decoded.indexOf(':');
  if (separator < 0) {
    return undefined;
  }
  return {
    clientId: decodeURIComponent(decoded.slice(0, separator)),
    clientSecret: decodeURIComponent(decoded.slice(separator + 1)),
  };
}

function cookies(request) {
  return Object.fromEntries(
    (request.headers.cookie ?? '')
      .split(';')
      .map((entry) => entry.trim())
      .filter(Boolean)
      .map((entry) => {
        const separator = entry.indexOf('=');
        return separator < 0
          ? [entry, '']
          : [entry.slice(0, separator), decodeURIComponent(entry.slice(separator + 1))];
      }),
  );
}

async function readForm(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  return new URLSearchParams(Buffer.concat(chunks).toString('utf8'));
}

function sendOAuthError(response, status, error) {
  sendJson(
    response,
    status,
    { error },
    {
      'Cache-Control': 'no-store',
      Pragma: 'no-cache',
    },
  );
}

function sendJson(response, status, body, headers = {}) {
  send(response, status, JSON.stringify(body), {
    'Content-Type': 'application/json; charset=utf-8',
    ...headers,
  });
}

function sendHtml(response, status, body) {
  send(response, status, body, { 'Content-Type': 'text/html; charset=utf-8' });
}

function send(response, status, body, headers = {}) {
  if (response.headersSent) {
    return;
  }
  response.writeHead(status, headers);
  response.end(body);
}

function redirect(response, location, headers = {}) {
  send(response, 302, '', { Location: location, ...headers });
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function localHttpUrl(value, name) {
  const url = new URL(value);
  if (
    url.protocol !== 'http:' ||
    !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) ||
    url.pathname !== '/'
  ) {
    throw new Error(`The mock ${name} URL must be an HTTP localhost origin`);
  }
  return url;
}

function portOf(url) {
  if (url.port) {
    return Number(url.port);
  }
  return 80;
}

function listen(server, port) {
  server.listen(port);
  return once(server, 'listening');
}

function safeErrorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

function terminateClient(signal) {
  if (clientProcess.exitCode !== null || clientProcess.signalCode !== null) {
    return;
  }
  clientProcess.kill(signal);
}

async function shutdown(exitCode) {
  if (stopping) {
    return;
  }
  stopping = true;

  terminateClient('SIGTERM');
  await Promise.race([
    once(clientProcess, 'exit'),
    new Promise((resolve) => setTimeout(resolve, 5_000)),
  ]);
  terminateClient('SIGKILL');

  authServer.closeAllConnections();
  resourceServer.closeAllConnections();
  await Promise.allSettled([close(authServer), close(resourceServer)]);
  process.exit(exitCode);
}

function close(server) {
  if (!server.listening) {
    return Promise.resolve();
  }
  return new Promise((resolve) => server.close(resolve));
}
