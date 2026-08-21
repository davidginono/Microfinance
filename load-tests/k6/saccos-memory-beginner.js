import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import {
  authenticatedBrowse,
  baseUrl,
  envDuration,
  envNumber,
  login,
  routeMix
} from './lib/saccos.js';

const routes = routeMix();
const targetRps = envNumber('SACCOS_TARGET_RPS', 25);
const maxVUs = envNumber('SACCOS_MAX_VUS', Math.max(75, targetRps * 5));
const rampDuration = envDuration('SACCOS_RAMP_DURATION', '2m');
const holdDuration = envDuration('SACCOS_SOAK_DURATION', '10m');
const sampleEverySeconds = envNumber('SACCOS_MEMORY_SAMPLE_SECONDS', 10);
const sampleDuration = envDuration(
  'SACCOS_MEMORY_SAMPLE_DURATION',
  secondsToDuration(durationToSeconds(rampDuration, 120) + durationToSeconds(holdDuration, 600) + 90)
);

const actuatorMetricFetchOk = new Rate('actuator_metric_fetch_ok');
const heapUsedMb = new Trend('jvm_heap_used_mb');
const nonHeapUsedMb = new Trend('jvm_nonheap_used_mb');
const heapUsedPercent = new Trend('jvm_heap_used_percent');
const liveThreads = new Trend('jvm_threads_live');
const hikariPending = new Trend('hikaricp_connections_pending');
const gcPauseMaxMs = new Trend('jvm_gc_pause_max_ms');

let metricsLoggedIn = false;

export const options = {
  scenarios: {
    user_logins_and_pages: {
      executor: 'ramping-arrival-rate',
      exec: 'userLoginsAndPages',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: Math.min(maxVUs, Math.max(20, targetRps)),
      maxVUs: maxVUs,
      stages: [
        { target: targetRps, duration: rampDuration },
        { target: targetRps, duration: holdDuration },
        { target: 0, duration: '1m' }
      ]
    },
    memory_sampler: {
      executor: 'constant-vus',
      exec: 'memorySampler',
      vus: 1,
      duration: sampleDuration,
      gracefulStop: '5s'
    }
  },
  thresholds: {
    checks: ['rate>0.95'],
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000', 'p(99)<6000'],
    actuator_metric_fetch_ok: ['rate>0.90']
  }
};

export function userLoginsAndPages() {
  authenticatedBrowse(routes);
}

export function memorySampler() {
  if (!metricsLoggedIn) {
    login();
    metricsLoggedIn = true;
  }

  const heapUsedBytes = actuatorValue('jvm.memory.used', 'area:heap');
  const heapMaxBytes = actuatorValue('jvm.memory.max', 'area:heap');
  const nonHeapUsedBytes = actuatorValue('jvm.memory.used', 'area:nonheap');
  const threadCount = actuatorValue('jvm.threads.live');
  const pendingConnections = actuatorValue('hikaricp.connections.pending');
  const gcPauseMaxSeconds = actuatorMeasurement('jvm.gc.pause', 'MAX');

  if (isUsefulNumber(heapUsedBytes)) {
    heapUsedMb.add(bytesToMb(heapUsedBytes));
  }
  if (isUsefulNumber(nonHeapUsedBytes)) {
    nonHeapUsedMb.add(bytesToMb(nonHeapUsedBytes));
  }
  if (isUsefulNumber(heapUsedBytes) && isUsefulNumber(heapMaxBytes)) {
    heapUsedPercent.add((heapUsedBytes / heapMaxBytes) * 100);
  }
  if (isUsefulNumber(threadCount)) {
    liveThreads.add(threadCount);
  }
  if (isUsefulNumber(pendingConnections)) {
    hikariPending.add(pendingConnections);
  }
  if (isUsefulNumber(gcPauseMaxSeconds)) {
    gcPauseMaxMs.add(gcPauseMaxSeconds * 1000);
  }

  sleep(sampleEverySeconds);
}

export function handleSummary(data) {
  const report = [
    'SACCOS LMS beginner memory test',
    '',
    'What ran',
    `- App URL: ${baseUrl()}`,
    `- Routes: ${routes.join(', ')}`,
    `- Target page requests/second: ${targetRps}`,
    `- Max virtual users: ${maxVUs}`,
    `- Ramp duration: ${rampDuration}`,
    `- Hold duration: ${holdDuration}`,
    '',
    'k6 request results',
    `- Checks passed: ${percent(data, 'checks', 'rate')}`,
    `- HTTP failures: ${percent(data, 'http_req_failed', 'rate')}`,
    `- HTTP p95: ${number(data, 'http_req_duration', 'p(95)', 'n/a')} ms`,
    `- HTTP p99: ${number(data, 'http_req_duration', 'p(99)', 'n/a')} ms`,
    '',
    'Memory and saturation samples',
    `- Heap used max: ${number(data, 'jvm_heap_used_mb', 'max', 'n/a')} MB`,
    `- Heap used average: ${number(data, 'jvm_heap_used_mb', 'avg', 'n/a')} MB`,
    `- Heap used max percent: ${number(data, 'jvm_heap_used_percent', 'max', 'n/a')}%`,
    `- Non-heap used max: ${number(data, 'jvm_nonheap_used_mb', 'max', 'n/a')} MB`,
    `- Live threads max: ${number(data, 'jvm_threads_live', 'max', 'n/a')}`,
    `- Hikari pending connections max: ${number(data, 'hikaricp_connections_pending', 'max', 'n/a')}`,
    `- JVM GC pause max: ${number(data, 'jvm_gc_pause_max_ms', 'max', 'n/a')} ms`,
    '',
    'How to read this',
    '- A healthy beginner run has checks above 95%, HTTP failures below 5%, and heap usage that does not march upward for the whole hold period.',
    '- Higher memory during many logins is normal because each virtual user keeps a session. Worry when heap keeps climbing during a long soak and never settles after traffic ramps down.',
    '- This proves local behavior only. A production capacity claim still needs a longer run with production-like data and hardware.',
    ''
  ].join('\n');

  return {
    stdout: report,
    'load-tests/results/beginner-memory-report.txt': report
  };
}

function actuatorValue(name, tag) {
  return actuatorMeasurement(name, 'VALUE', tag);
}

function actuatorMeasurement(name, statistic, tag) {
  const url = `${baseUrl()}/actuator/metrics/${name}${tag ? `?tag=${encodeURIComponent(tag)}` : ''}`;
  const response = http.get(url, { tags: { route: `/actuator/metrics/${name}` } });
  const ok = check(response, {
    [`metric ${name} loaded`]: function (res) { return res.status === 200; }
  });
  actuatorMetricFetchOk.add(ok);

  if (!ok) {
    return null;
  }

  let body;
  try {
    body = response.json();
  } catch (error) {
    fail(`Metric ${name} did not return JSON.`);
  }

  const measurements = body && Array.isArray(body.measurements) ? body.measurements : [];
  const measurement = measurements.find(function (item) {
    return String(item.statistic || '').toUpperCase() === statistic;
  }) || measurements[0];

  return measurement ? Number(measurement.value) : null;
}

function durationToSeconds(raw, fallbackSeconds) {
  const match = String(raw || '').trim().match(/^(\d+)(ms|s|m|h)$/i);
  if (!match) {
    return fallbackSeconds;
  }

  const value = Number(match[1]);
  const unit = match[2].toLowerCase();
  if (unit === 'ms') {
    return Math.max(1, Math.ceil(value / 1000));
  }
  if (unit === 's') {
    return value;
  }
  if (unit === 'm') {
    return value * 60;
  }
  return value * 60 * 60;
}

function secondsToDuration(seconds) {
  return `${Math.max(1, Math.ceil(seconds))}s`;
}

function bytesToMb(bytes) {
  return bytes / 1024 / 1024;
}

function isUsefulNumber(value) {
  return Number.isFinite(value) && value > 0;
}

function number(data, metricName, valueName, fallback) {
  const value = data.metrics
    && data.metrics[metricName]
    && data.metrics[metricName].values
    && data.metrics[metricName].values[valueName];
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return String(Math.round(value * 100) / 100);
}

function percent(data, metricName, valueName) {
  const value = data.metrics
    && data.metrics[metricName]
    && data.metrics[metricName].values
    && data.metrics[metricName].values[valueName];
  if (!Number.isFinite(value)) {
    return 'n/a';
  }
  return `${Math.round(value * 10000) / 100}%`;
}
