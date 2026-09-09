/**
 * 01-rest-api-server.js
 *
 * Covers: 03 Express.js Fundamentals + 05 Building REST APIs with Express
 *
 * A small, real REST API for a "tasks" resource, using only Express's
 * built-in body parser (express.json()) -- no deprecated `body-parser`
 * package needed (Express 4.16+ / 5.x ships this natively).
 *
 * Run directly:   node 01-rest-api-server.js
 * Used by tests:  06-api.test.js does `require('./01-rest-api-server')`
 *                 which returns the Express `app` WITHOUT calling .listen()
 *                 (listen only happens when this file is the entry point).
 */

const express = require('express');

const app = express();

// Built-in JSON body parser -- replaces the old `body-parser` package.
app.use(express.json());

// --- "Database" -----------------------------------------------------------
// In-memory store, just for demo purposes. Resets on every restart.
let tasks = [
  { id: 1, title: 'Learn Express', done: false },
  { id: 2, title: 'Write REST API', done: true },
];
let nextId = 3;

// --- Helpers ----------------------------------------------------------------
function findTaskOr404(req, res, next) {
  const id = Number(req.params.id);
  const task = tasks.find((t) => t.id === id);
  if (!task) {
    return res.status(404).json({ error: `Task with id ${id} not found` });
  }
  req.task = task;
  next();
}

function validateTaskBody(req, res, next) {
  const { title, done } = req.body;

  if (title === undefined && done === undefined) {
    return res.status(400).json({ error: 'Request body must include at least "title" or "done"' });
  }
  if (title !== undefined && (typeof title !== 'string' || title.trim().length === 0)) {
    return res.status(400).json({ error: '"title" must be a non-empty string' });
  }
  if (done !== undefined && typeof done !== 'boolean') {
    return res.status(400).json({ error: '"done" must be a boolean' });
  }
  next();
}

// --- Routes -----------------------------------------------------------------

// GET /tasks - list all tasks (supports ?done=true|false filter)
app.get('/tasks', (req, res) => {
  const { done } = req.query;
  let result = tasks;
  if (done === 'true') result = tasks.filter((t) => t.done);
  if (done === 'false') result = tasks.filter((t) => !t.done);
  res.status(200).json(result);
});

// GET /tasks/:id - get a single task
app.get('/tasks/:id', findTaskOr404, (req, res) => {
  res.status(200).json(req.task);
});

// POST /tasks - create a task
app.post('/tasks', (req, res) => {
  const { title, done } = req.body;

  if (typeof title !== 'string' || title.trim().length === 0) {
    return res.status(400).json({ error: '"title" is required and must be a non-empty string' });
  }

  const task = {
    id: nextId++,
    title: title.trim(),
    done: typeof done === 'boolean' ? done : false,
  };
  tasks.push(task);
  res.status(201).json(task);
});

// PUT /tasks/:id - full/partial update of a task
app.put('/tasks/:id', findTaskOr404, validateTaskBody, (req, res) => {
  const { title, done } = req.body;
  if (title !== undefined) req.task.title = title.trim();
  if (done !== undefined) req.task.done = done;
  res.status(200).json(req.task);
});

// DELETE /tasks/:id - remove a task
app.delete('/tasks/:id', findTaskOr404, (req, res) => {
  tasks = tasks.filter((t) => t.id !== req.task.id);
  res.status(204).send();
});

// Fallback 404 for anything else
app.use((req, res) => {
  res.status(404).json({ error: 'Route not found' });
});

// Only start listening when this file is run directly (`node 01-rest-api-server.js`).
// When required by tests (06-api.test.js), `app` is exported untouched so
// supertest can drive it without an open port.
const PORT = process.env.PORT || 3001;
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`[01] REST API server listening on http://localhost:${PORT}`);
    console.log('Try: curl http://localhost:3001/tasks');
  });
}

module.exports = app;
