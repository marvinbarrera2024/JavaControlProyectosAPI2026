import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const docsUrl = `${baseUrl}/v3/api-docs`;
const adminPassword = 'ipcIpkFE9_OKFr9LSWNp1MYDK68P_NS4';
const virtualUsers = Number(__ENV.K6_VUS || 100);
const categoryCount = Number(__ENV.K6_CATEGORY_COUNT || 10);
const projectCount = Number(__ENV.K6_PROJECT_COUNT || 100);
const taskCount = Number(__ENV.K6_TASK_COUNT || 10000);

export const options = {
  scenarios: {
    create_tasks: {
      executor: 'shared-iterations',
      vus: virtualUsers,
      iterations: taskCount,
      maxDuration: '2h',
    },
  },
  setupTimeout: '10m',
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

function requireCreatedId(response, endpoint) {
  const created = check(response, {
    [`${endpoint} returns HTTP 200`]: (result) => result.status === 200,
  });
  if (!created) {
    throw new Error(`${endpoint} failed with HTTP ${response.status}`);
  }

  const id = response.json('id');
  if (!id) {
    throw new Error(`${endpoint} did not return an id`);
  }
  return id;
}

export function setup() {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const response = http.get(docsUrl, { timeout: '2s' });
    if (response.status === 200) {
      break;
    }
    if (attempt === 29) {
      throw new Error('API did not become ready within 60 seconds');
    }
    sleep(2);
  }

  const loginResponse = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ login: 'admin', clave: adminPassword }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'auth-login' },
    },
  );
  if (loginResponse.status !== 200) {
    throw new Error(`Admin login failed with HTTP ${loginResponse.status}`);
  }

  const token = loginResponse.json('token');
  if (!token) {
    throw new Error('Admin login did not return a token');
  }

  const runId = Date.now();
  const categoryIds = [];
  for (let categoryIndex = 1; categoryIndex <= categoryCount; categoryIndex += 1) {
    const response = http.post(
      `${baseUrl}/api/categorias`,
      JSON.stringify({ nombre: `k6-${runId}-categoria-${categoryIndex}` }),
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        tags: { endpoint: 'category-create' },
      },
    );
    categoryIds.push(requireCreatedId(response, 'Category creation'));
  }

  const projectIds = [];
  for (let projectIndex = 1; projectIndex <= projectCount; projectIndex += 1) {
    const categoryId = categoryIds[(projectIndex - 1) % categoryIds.length];
    const response = http.post(
      `${baseUrl}/api/proyectos`,
      JSON.stringify({
        titulo: `k6-${runId}-proyecto-${projectIndex}`,
        descripcion: 'Proyecto para prueba de carga k6',
        fechaInicio: '2026-01-01',
        fechaFin: '2026-12-31',
        categoriaId: categoryId,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        tags: { endpoint: 'project-create' },
      },
    );
    projectIds.push(requireCreatedId(response, 'Project creation'));
  }

  return { token, projectIds, runId };
}

export default function (data) {
  const projectIndex = (__VU - 1 + __ITER) % data.projectIds.length;
  const response = http.post(
    `${baseUrl}/api/tareas`,
    JSON.stringify({
      nombre: `k6-${data.runId}-tarea-${__VU}-${__ITER}`,
      descripcion: 'Tarea para prueba de carga k6',
      duracion: '1 dia',
      proyectoId: data.projectIds[projectIndex],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.token}`,
      },
      tags: { endpoint: 'task-create' },
    },
  );

  check(response, {
    'task creation returns HTTP 200': (result) => result.status === 200,
    'task creation returns an id': (result) => Boolean(result.json('id')),
  });

  if (__ITER % 10 === 0) {
    sleep(0.1);
  }
}