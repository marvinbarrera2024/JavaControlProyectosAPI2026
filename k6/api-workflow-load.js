import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const docsUrl = `${baseUrl}/v3/api-docs`;
const adminPassword = 'ipcIpkFE9_OKFr9LSWNp1MYDK68P_NS4';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '3m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

export function setup() {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const response = http.get(docsUrl, { timeout: '2s' });
    if (response.status === 200) {
      return;
    }
    sleep(2);
  }

  throw new Error('API did not become ready within 60 seconds');
}

function callApi(endpoint, method, path, token, payload) {
  const headers = { Authorization: `Bearer ${token}` };
  if (payload !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const response = http.request(
    method,
    `${baseUrl}${path}`,
    payload === undefined ? null : JSON.stringify(payload),
    { headers, tags: { endpoint } },
  );
  const succeeded = check(response, {
    [`${endpoint} returns HTTP 200`]: (result) => result.status === 200,
  });

  return { response, succeeded };
}

function responseId(response) {
  try {
    return response.json('id');
  } catch {
    return null;
  }
}

export default function () {
  let categoryId = null;
  let projectId = null;
  let taskId = null;

  const loginResponse = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ login: 'admin', clave: adminPassword }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'auth-login' },
    },
  );
  const loginSucceeded = check(loginResponse, {
    'admin login returns HTTP 200': (response) => response.status === 200,
  });

  if (!loginSucceeded) {
    return;
  }

  const token = loginResponse.json('token');
  const runId = `${Date.now()}-${__VU}-${__ITER}`;

  try {
    let result = callApi('category-create', 'POST', '/api/categorias', token, {
      nombre: `k6-category-${runId}`,
    });
    categoryId = result.succeeded ? responseId(result.response) : null;
    if (!categoryId) return;

    callApi('category-get-by-id', 'GET', `/api/categorias/${categoryId}`, token);
    callApi('category-list', 'GET', '/api/categorias/lista', token);
    callApi('category-page', 'GET', '/api/categorias?page=0&size=20&sort=id,desc', token);
    callApi('category-update', 'PUT', `/api/categorias/${categoryId}`, token, {
      id: categoryId,
      nombre: `k6-category-updated-${runId}`,
    });

    result = callApi('project-create', 'POST', '/api/proyectos', token, {
      titulo: `k6-project-${runId}`,
      descripcion: 'Proyecto temporal de prueba de carga',
      fechaInicio: '2026-01-01',
      fechaFin: '2026-12-31',
      categoriaId: categoryId,
    });
    projectId = result.succeeded ? responseId(result.response) : null;
    if (!projectId) return;

    callApi('project-get-by-id', 'GET', `/api/proyectos/${projectId}`, token);
    callApi('project-list', 'GET', '/api/proyectos/lista', token);
    callApi('project-page', 'GET', '/api/proyectos?page=0&size=20&sort=id,desc', token);
    callApi('project-update', 'PUT', `/api/proyectos/${projectId}`, token, {
      id: projectId,
      titulo: `k6-project-updated-${runId}`,
      descripcion: 'Proyecto temporal actualizado',
      fechaInicio: '2026-01-01',
      fechaFin: '2026-12-31',
      categoriaId: categoryId,
    });

    result = callApi('task-create', 'POST', '/api/tareas', token, {
      nombre: `k6-task-${runId}`,
      descripcion: 'Tarea temporal de prueba de carga',
      duracion: '1 dia',
      proyectoId: projectId,
    });
    taskId = result.succeeded ? responseId(result.response) : null;
    if (!taskId) return;

    callApi('task-get-by-id', 'GET', `/api/tareas/${taskId}`, token);
    callApi('task-list', 'GET', '/api/tareas/lista', token);
    callApi('task-page', 'GET', '/api/tareas?page=0&size=20&sort=id,desc', token);
    callApi('task-list-by-project', 'GET', `/api/tareas/proyecto/${projectId}`, token);
    callApi('task-update', 'PUT', `/api/tareas/${taskId}`, token, {
      id: taskId,
      nombre: `k6-task-updated-${runId}`,
      descripcion: 'Tarea temporal actualizada',
      duracion: '2 dias',
      estado: 'APROBADA',
      proyectoId: projectId,
    });
    callApi('task-change-status', 'PATCH', '/api/tareas', token, {
      id: taskId,
      estado: 'PROCESO',
    });
  } finally {
    if (taskId) callApi('task-delete', 'DELETE', `/api/tareas/${taskId}`, token);
    if (projectId) callApi('project-delete', 'DELETE', `/api/proyectos/${projectId}`, token);
    if (categoryId) callApi('category-delete', 'DELETE', `/api/categorias/${categoryId}`, token);
  }

  sleep(1);
}