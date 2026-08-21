import http from 'k6/http';
import { check, fail, sleep } from 'k6';

let loggedIn = false;

export function baseUrl() {
  return (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
}

export function envNumber(name, fallback) {
  var raw = __ENV[name];
  if (raw === undefined || raw === null || String(raw).trim() === '') {
    return fallback;
  }
  var parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function envDuration(name, fallback) {
  var raw = __ENV[name];
  return raw === undefined || raw === null || String(raw).trim() === '' ? fallback : String(raw).trim();
}

export function routeMix() {
  if (__ENV.SACCOS_ROUTES && String(__ENV.SACCOS_ROUTES).trim() !== '') {
    return String(__ENV.SACCOS_ROUTES)
      .split(',')
      .map(function (route) { return route.trim(); })
      .filter(function (route) { return route !== ''; });
  }

  var accountType = String(__ENV.SACCOS_ACCOUNT_TYPE || 'staff').toLowerCase();
  if (accountType === 'member') {
    return [
      '/app/dashboard',
      '/app/loan-products',
      '/app/loan-applications',
      '/app/guarantee-requests'
    ];
  }

  return [
    '/admin/dashboard',
    '/admin/users',
    '/admin/settings-controls'
  ];
}

export function authenticatedBrowse(routes) {
  if (!loggedIn) {
    login();
    loggedIn = true;
  }

  var route = routes[(__VU + __ITER) % routes.length];
  var response = http.get(absoluteUrl(route), { tags: { route: route } });

  if (isLoginRedirect(response)) {
    loggedIn = false;
    login();
    loggedIn = true;
    response = http.get(absoluteUrl(route), { tags: { route: route } });
  }

  check(response, {
    'page returned 200': function (res) { return res.status === 200; }
  });

  var thinkTime = Number(__ENV.SACCOS_THINK_TIME_SECONDS || '0');
  if (thinkTime > 0) {
    sleep(thinkTime);
  }
}

export function login() {
  var accountType = String(__ENV.SACCOS_ACCOUNT_TYPE || 'staff').toLowerCase();
  var username = String(__ENV.SACCOS_USERNAME || 'ADM001');
  var password = __ENV.SACCOS_PASSWORD;

  if (!password || String(password).trim() === '') {
    fail('Set SACCOS_PASSWORD before running authenticated load tests.');
  }

  var loginPath = accountType === 'staff' ? '/login?tab=staff' : '/login';
  var loginPage = http.get(baseUrl() + loginPath);
  check(loginPage, {
    'login page loaded': function (res) { return res.status === 200; }
  });

  var csrf = extractCsrf(loginPage.body);
  var form = {
    username: username,
    password: password
  };
  form[csrf.name] = csrf.value;

  if (accountType === 'staff') {
    form.loginType = 'staff-password';
  }

  var loginResponse = http.post(baseUrl() + '/login', form, {
    redirects: 0,
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });
  var location = header(loginResponse, 'Location');

  check(loginResponse, {
    'login redirected': function (res) { return res.status === 302 || res.status === 303; },
    'login did not fail': function () { return location.indexOf('/login?error') === -1; },
    'login did not require MFA': function () { return location.indexOf('/login/mfa') === -1; }
  });

  if (location.indexOf('/login?error') !== -1) {
    fail('Login failed. Check SACCOS_USERNAME, SACCOS_PASSWORD, and SACCOS_ACCOUNT_TYPE.');
  }
  if (location.indexOf('/login/mfa') !== -1) {
    fail('This account requires MFA. Use a benchmark account without MFA or test MFA separately.');
  }

  if (location !== '') {
    var landing = http.get(absoluteUrl(location), { tags: { route: 'login-landing' } });
    check(landing, {
      'landing page loaded': function (res) { return res.status === 200; }
    });
  }
}

export function absoluteUrl(pathOrUrl) {
  if (!pathOrUrl || pathOrUrl === '') {
    return baseUrl() + '/';
  }
  if (/^https?:\/\//i.test(pathOrUrl)) {
    return pathOrUrl;
  }
  if (pathOrUrl.charAt(0) !== '/') {
    return baseUrl() + '/' + pathOrUrl;
  }
  return baseUrl() + pathOrUrl;
}

function extractCsrf(html) {
  var match = String(html || '').match(/<input[^>]+name=["']([^"']*csrf[^"']*)["'][^>]+value=["']([^"']+)["']/i);
  if (!match) {
    fail('CSRF token was not found on the login page.');
  }
  return {
    name: match[1],
    value: match[2]
  };
}

function isLoginRedirect(response) {
  if (response.status !== 302 && response.status !== 303) {
    return false;
  }
  return header(response, 'Location').indexOf('/login') !== -1;
}

function header(response, name) {
  return response.headers[name] || response.headers[name.toLowerCase()] || '';
}
