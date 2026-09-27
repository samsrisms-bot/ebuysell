const state = {
  token: localStorage.getItem('simivr_admin_token') || null,
  eventsPollHandle: null
};

const $ = (sel) => document.querySelector(sel);

async function apiFetch(path, options = {}) {
  // Only set JSON content-type for string (JSON.stringify'd) bodies — FormData needs the
  // browser to set its own multipart boundary header, so leave it alone in that case.
  const isJsonBody = typeof options.body === 'string';
  const res = await fetch(path, {
    ...options,
    headers: {
      ...(isJsonBody ? { 'Content-Type': 'application/json' } : {}),
      Authorization: `Bearer ${state.token}`,
      ...(options.headers || {})
    }
  });
  if (res.status === 401) {
    logout();
    throw new Error('session expired, please log in again');
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `request failed (${res.status})`);
  }
  return res.status === 204 ? null : res.json();
}

function showApp() {
  $('#login-view').classList.add('hidden');
  $('#app-view').classList.remove('hidden');
  loadCampaigns();
}

function showLogin(message) {
  $('#app-view').classList.add('hidden');
  $('#login-view').classList.remove('hidden');
  if (message) $('#login-error').textContent = message;
  if (state.eventsPollHandle) clearInterval(state.eventsPollHandle);
}

function logout() {
  state.token = null;
  localStorage.removeItem('simivr_admin_token');
  showLogin();
}

async function login() {
  const token = $('#admin-token-input').value.trim();
  if (!token) return;
  state.token = token;
  try {
    await apiFetch('/api/admin/login', { method: 'POST' });
    localStorage.setItem('simivr_admin_token', token);
    $('#login-error').textContent = '';
    showApp();
  } catch (e) {
    state.token = null;
    $('#login-error').textContent = 'Invalid admin token.';
  }
}

// ---- Campaigns ----
async function loadCampaigns() {
  const campaigns = await apiFetch('/api/campaigns');
  const body = $('#campaigns-body');
  body.innerHTML = '';
  for (const c of campaigns) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${escapeHtml(c.name)}</td>
      <td>${c.status}</td>
      <td>${c._count?.contacts ?? 0}</td>
      <td>${c._count?.leads ?? 0}</td>
      <td>
        <div class="campaign-id">${c.id}</div>
        <div class="csv-upload">
          <input type="file" accept=".csv" data-campaign-id="${c.id}" class="csv-input" />
          <button data-campaign-id="${c.id}" class="csv-upload-btn">Import CSV</button>
        </div>
      </td>`;
    body.appendChild(tr);
  }
  document.querySelectorAll('.csv-upload-btn').forEach((btn) => {
    btn.addEventListener('click', () => uploadCsv(btn.dataset.campaignId));
  });
}

async function createCampaign() {
  const name = $('#new-campaign-name').value.trim();
  if (!name) return;
  await apiFetch('/api/campaigns', { method: 'POST', body: JSON.stringify({ name }) });
  $('#new-campaign-name').value = '';
  loadCampaigns();
}

async function uploadCsv(campaignId) {
  const input = document.querySelector(`.csv-input[data-campaign-id="${campaignId}"]`);
  const file = input.files[0];
  if (!file) return alert('Choose a CSV file first.');
  const formData = new FormData();
  formData.append('file', file);
  try {
    const result = await apiFetch(`/api/campaigns/${campaignId}/contacts/csv`, {
      method: 'POST',
      body: formData,
      headers: {} // let the browser set the multipart boundary; apiFetch skips Content-Type when body isn't JSON-stringified
    });
    alert(`Imported ${result.imported} contacts (${result.skippedOptedOut} opted-out skipped, ${result.skippedInvalid} invalid rows skipped).`);
    loadCampaigns();
  } catch (e) {
    alert(`Import failed: ${e.message}`);
  }
}

// ---- Leads ----
async function loadLeads() {
  const status = $('#lead-status-filter').value;
  const leads = await apiFetch(`/api/leads${status ? `?status=${status}` : ''}`);
  const body = $('#leads-body');
  body.innerHTML = '';
  for (const l of leads) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${escapeHtml(l.number)}</td>
      <td>${escapeHtml(l.name || '-')}</td>
      <td>${escapeHtml(l.campaign?.name || '-')}</td>
      <td>${new Date(l.createdAt).toLocaleString()}</td>
      <td>
        <select data-lead-id="${l.id}" class="lead-status-select">
          <option value="NEW" ${l.status === 'NEW' ? 'selected' : ''}>New</option>
          <option value="CONTACTED" ${l.status === 'CONTACTED' ? 'selected' : ''}>Contacted</option>
          <option value="CLOSED" ${l.status === 'CLOSED' ? 'selected' : ''}>Closed</option>
        </select>
      </td>`;
    body.appendChild(tr);
  }
  document.querySelectorAll('.lead-status-select').forEach((sel) => {
    sel.addEventListener('change', async () => {
      await apiFetch(`/api/leads/${sel.dataset.leadId}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: sel.value })
      });
    });
  });
}

// ---- Events ----
async function loadEvents() {
  const events = await apiFetch('/api/events?limit=50');
  const body = $('#events-body');
  body.innerHTML = '';
  for (const e of events) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${new Date(e.receivedAt).toLocaleString()}</td>
      <td>${e.event}</td>
      <td>${e.direction}</td>
      <td>${escapeHtml(e.number)}</td>
      <td>${e.sim + 1}</td>
      <td>${escapeHtml(e.flow)}</td>
      <td>${(e.digits || []).join('')}</td>
      <td>${Math.round(e.durationMs / 1000)}s</td>`;
    body.appendChild(tr);
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ---- Tabs ----
function setupTabs() {
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
      document.querySelectorAll('.tab').forEach((t) => t.classList.add('hidden'));
      btn.classList.add('active');
      $(`#tab-${btn.dataset.tab}`).classList.remove('hidden');
      if (btn.dataset.tab === 'leads') loadLeads();
      if (btn.dataset.tab === 'events') loadEvents();
    });
  });
}

function setupEventPolling() {
  state.eventsPollHandle = setInterval(() => {
    if (!$('#tab-events').classList.contains('hidden')) loadEvents();
  }, 5000);
}

// ---- Wire up ----
$('#login-btn').addEventListener('click', login);
$('#admin-token-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') login(); });
$('#logout-btn').addEventListener('click', logout);
$('#create-campaign-btn').addEventListener('click', createCampaign);
$('#lead-status-filter').addEventListener('change', loadLeads);
setupTabs();
setupEventPolling();

if (state.token) {
  apiFetch('/api/admin/login', { method: 'POST' }).then(showApp).catch(() => showLogin());
} else {
  showLogin();
}
