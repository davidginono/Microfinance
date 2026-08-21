import { authenticatedBrowse, envDuration, envNumber, routeMix } from './lib/saccos.js';

const routes = routeMix();
const targetRps = envNumber('SACCOS_TARGET_RPS', 50);
const maxVUs = envNumber('SACCOS_MAX_VUS', Math.max(100, targetRps * 4));
const soakDuration = envDuration('SACCOS_SOAK_DURATION', '60m');

export const options = {
  scenarios: {
    authenticated_soak: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: Math.min(maxVUs, Math.max(20, targetRps)),
      maxVUs: maxVUs,
      stages: [
        { target: targetRps, duration: '5m' },
        { target: targetRps, duration: soakDuration },
        { target: 0, duration: '2m' }
      ]
    }
  },
  thresholds: {
    checks: ['rate>0.95'],
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000', 'p(99)<6000']
  }
};

export default function () {
  authenticatedBrowse(routes);
}
