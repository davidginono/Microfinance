import { authenticatedBrowse, routeMix } from './lib/saccos.js';

const routes = routeMix();

export const options = {
  scenarios: {
    authenticated_smoke: {
      executor: 'constant-vus',
      vus: 2,
      duration: '1m'
    }
  },
  thresholds: {
    checks: ['rate>0.95'],
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000', 'p(99)<4000']
  }
};

export default function () {
  authenticatedBrowse(routes);
}
