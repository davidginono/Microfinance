import { authenticatedBrowse, envNumber, routeMix } from './lib/saccos.js';

const routes = routeMix();
const targetRps = envNumber('SACCOS_TARGET_RPS', 100);
const maxVUs = envNumber('SACCOS_MAX_VUS', Math.max(100, targetRps * 4));

export const options = {
  scenarios: {
    authenticated_stress: {
      executor: 'ramping-arrival-rate',
      startRate: Math.max(1, Math.floor(targetRps * 0.1)),
      timeUnit: '1s',
      preAllocatedVUs: Math.min(maxVUs, Math.max(20, targetRps)),
      maxVUs: maxVUs,
      stages: [
        { target: Math.max(1, Math.floor(targetRps * 0.25)), duration: '1m' },
        { target: Math.max(1, Math.floor(targetRps * 0.5)), duration: '2m' },
        { target: targetRps, duration: '3m' },
        { target: Math.max(targetRps + 1, Math.floor(targetRps * 1.25)), duration: '2m' },
        { target: 0, duration: '1m' }
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
