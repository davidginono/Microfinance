import http from 'k6/http';
import { check, sleep } from 'k6';
import { baseUrl } from './lib/saccos.js';

export const options = {
  scenarios: {
    health: {
      executor: 'constant-vus',
      vus: 1,
      duration: '30s'
    }
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000']
  }
};

export default function () {
  const response = http.get(baseUrl() + '/actuator/health', { tags: { route: '/actuator/health' } });
  check(response, {
    'health returned 200': function (res) { return res.status === 200; },
    'health is up': function (res) { return String(res.body).indexOf('"status":"UP"') !== -1; }
  });
  sleep(1);
}
