/**
 * 06-api.test.js
 *
 * Covers: 08 Testing Node and Express APIs
 *
 * Jest + Supertest tests against the REST API defined in 01-rest-api-server.js.
 * The app is imported directly (require) -- .listen() is never called because
 * that file only listens when run as the main module (require.main === module).
 * Supertest spins up its own ephemeral server per request against the app object.
 *
 * Run: npm test   (or: npx jest)
 */

const request = require('supertest');
const app = require('./01-rest-api-server');

describe('Tasks REST API', () => {
  test('GET /tasks returns 200 and an array of tasks', async () => {
    const res = await request(app).get('/tasks');
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
    expect(res.body.length).toBeGreaterThan(0);
    expect(res.body[0]).toHaveProperty('title');
  });

  test('POST /tasks creates a new task and returns 201', async () => {
    const res = await request(app)
      .post('/tasks')
      .send({ title: 'Write tests with Supertest' });

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({
      title: 'Write tests with Supertest',
      done: false,
    });
    expect(res.body).toHaveProperty('id');
  });

  test('POST /tasks with missing title returns 400 (failure case)', async () => {
    const res = await request(app).post('/tasks').send({ done: true });

    expect(res.status).toBe(400);
    expect(res.body).toHaveProperty('error');
  });

  test('GET /tasks/:id returns 404 for a non-existent task', async () => {
    const res = await request(app).get('/tasks/999999');
    expect(res.status).toBe(404);
    expect(res.body).toHaveProperty('error');
  });

  test('PUT /tasks/:id updates an existing task', async () => {
    // Create one first so the test doesn't depend on seed data ordering.
    const created = await request(app).post('/tasks').send({ title: 'Temp task' });
    const id = created.body.id;

    const res = await request(app).put(`/tasks/${id}`).send({ done: true });

    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ id, title: 'Temp task', done: true });
  });

  test('DELETE /tasks/:id removes the task and returns 204', async () => {
    const created = await request(app).post('/tasks').send({ title: 'To be deleted' });
    const id = created.body.id;

    const delRes = await request(app).delete(`/tasks/${id}`);
    expect(delRes.status).toBe(204);

    const getRes = await request(app).get(`/tasks/${id}`);
    expect(getRes.status).toBe(404);
  });
});
