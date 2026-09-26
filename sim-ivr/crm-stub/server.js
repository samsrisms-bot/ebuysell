/**
 * Reference CRM receiver for the SIM IVR Android app.
 *
 * Implements the exact contract the app calls:
 *   POST {base}/api/ivr/events                    <- every call result + every callback/opt-out
 *   GET  {base}/api/ivr/campaigns/:id/contacts     -> campaign contact list to dial
 *
 * Run: npm install && npm start   (listens on PORT, default 8080)
 *
 * Set API_KEY to require the app's Settings > CRM API key to match (sent as
 * "Authorization: Bearer <key>" and "X-Api-Key: <key>"); leave unset to accept any request.
 *
 * This is intentionally minimal — adapt the handlers below to forward into Chat247, TeleCRM, or
 * any other CRM's own API/schema.
 */
const express = require('express');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;
const API_KEY = process.env.API_KEY || '';
const DATA_DIR = path.join(__dirname, 'data');
const EVENTS_LOG = path.join(DATA_DIR, 'events.log');
const CAMPAIGNS_DIR = path.join(DATA_DIR, 'campaigns');

fs.mkdirSync(CAMPAIGNS_DIR, { recursive: true });

const app = express();
app.use(express.json());

function checkAuth(req, res, next) {
  if (!API_KEY) return next();
  const header = req.get('Authorization') || '';
  const bearer = header.startsWith('Bearer ') ? header.slice(7) : null;
  const apiKeyHeader = req.get('X-Api-Key');
  if (bearer === API_KEY || apiKeyHeader === API_KEY) return next();
  return res.status(401).json({ error: 'invalid or missing API key' });
}

// POST /api/ivr/events
// Body: { event, direction, number, sim, flow, digits[], durationMs, startedAt, endedAt, campaignId, vars }
app.post('/api/ivr/events', checkAuth, (req, res) => {
  const event = req.body;
  const line = JSON.stringify({ receivedAt: new Date().toISOString(), ...event });
  fs.appendFileSync(EVENTS_LOG, line + '\n');

  console.log(`[ivr-event] ${event.event || 'unknown'} ${event.direction || ''} ${event.number || ''} ` +
    `sim=${event.sim} flow=${event.flow} digits=${JSON.stringify(event.digits)} campaign=${event.campaignId || '-'}`);

  if (event.event === 'callback_request') {
    console.log(`  -> NEW LEAD: callback requested by ${event.number} (vars: ${JSON.stringify(event.vars)})`);
  }
  if (Array.isArray(event.digits) && event.digits.includes('9') && event.campaignId) {
    console.log(`  -> OPT-OUT: ${event.number} pressed 9, should be suppressed from future campaigns`);
  }

  res.status(200).json({ ok: true });
});

// GET /api/ivr/events - convenience endpoint for inspecting what the app has sent (not part of the app's contract)
app.get('/api/ivr/events', checkAuth, (req, res) => {
  if (!fs.existsSync(EVENTS_LOG)) return res.json([]);
  const lines = fs.readFileSync(EVENTS_LOG, 'utf8').trim().split('\n').filter(Boolean);
  res.json(lines.map((l) => JSON.parse(l)));
});

// GET /api/ivr/campaigns/:id/contacts
// Response: [{ name, phone, vars: { ... } }, ...]
// Looks for data/campaigns/<id>.json; falls back to a small sample list so the endpoint is
// testable out of the box.
app.get('/api/ivr/campaigns/:id/contacts', checkAuth, (req, res) => {
  const file = path.join(CAMPAIGNS_DIR, `${req.params.id}.json`);
  if (fs.existsSync(file)) {
    return res.json(JSON.parse(fs.readFileSync(file, 'utf8')));
  }
  res.json([
    { name: 'Asha Rao', phone: '+919000000001', vars: { city: 'Hyderabad' } },
    { name: 'Vikram Singh', phone: '+919000000002', vars: { city: 'Delhi' } }
  ]);
});

app.get('/', (req, res) => {
  res.type('text').send('SIM IVR CRM stub is running. See README for endpoints.');
});

app.listen(PORT, () => {
  console.log(`SIM IVR CRM stub listening on http://0.0.0.0:${PORT}`);
  console.log(`Set the app's Settings > CRM base URL to http://<this-machine-ip>:${PORT}`);
});
