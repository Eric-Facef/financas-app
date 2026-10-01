'use strict';

// Servido pelo Spring (porta 8080): API na mesma origem ('').
// Aberto pelo Live Server / Vite (5500, 5501, 5173): usa a API em http://<mesmo host>:8080.
// Para outro endereço, defina window.API_BASE antes de carregar este arquivo.
const DEV_PORTS = ['5500', '5501', '5173'];
const API = window.API_BASE ?? (DEV_PORTS.includes(location.port) ? `http://${location.hostname}:8080` : '');
const state = { token: null, accounts: [], categories: [], dash: null, chart: null, registering: false };

const $ = (id) => document.getElementById(id);
const brl = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function toast(msg, isError = false) {
  const el = $('toast');
  el.textContent = msg;
  el.className = `fixed bottom-4 left-1/2 -translate-x-1/2 px-4 py-2 rounded-lg text-sm shadow-lg ${isError ? 'bg-red-600' : 'bg-emerald-600'}`;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => el.classList.add('hidden'), 3500);
}

// ---------------------------------------------------------------- HTTP + sessão
async function api(path, { method = 'GET', body, retry = true } = {}) {
  const res = await fetch(`${API}/api/v1${path}`, {
    method,
    credentials: API ? 'include' : 'same-origin',
    headers: { ...(body && { 'Content-Type': 'application/json' }), ...(state.token && { Authorization: `Bearer ${state.token}` }) },
    body: body && JSON.stringify(body),
  });
  if (res.status === 401 && retry && !path.startsWith('/auth/')) {
    if (await refresh()) return api(path, { method, body, retry: false });
    showAuth();
    throw new Error('Sessão expirada. Entre novamente.');
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.errors ? Object.values(data.errors)[0] : data?.detail || 'Erro inesperado');
  return data;
}

// Uma única renovação por vez: o refresh token é rotacionado, chamadas paralelas o invalidariam.
let refreshing = null;
function refresh() {
  refreshing ??= (async () => {
    try {
      const res = await fetch(`${API}/api/v1/auth/refresh`, { method: 'POST', credentials: API ? 'include' : 'same-origin' });
      if (!res.ok) return false;
      const data = await res.json();
      state.token = data.accessToken;
      $('userEmail').textContent = data.email;
      return true;
    } catch { return false; }
  })().finally(() => { refreshing = null; });
  return refreshing;
}

function showAuth() {
  state.token = null;
  $('appView').classList.add('hidden');
  $('authView').classList.remove('hidden');
}

async function showApp() {
  $('authView').classList.add('hidden');
  $('appView').classList.remove('hidden');
  await load();
}

// ---------------------------------------------------------------- auth UI
$('authToggle').onclick = () => {
  state.registering = !state.registering;
  $('authTitle').textContent = $('authSubmit').textContent = state.registering ? 'Criar conta' : 'Entrar';
  $('authToggle').textContent = state.registering ? 'Já tem conta? Entrar' : 'Não tem conta? Cadastre-se';
  $('password').autocomplete = state.registering ? 'new-password' : 'current-password';
};

$('authForm').onsubmit = async (e) => {
  e.preventDefault();
  $('authError').classList.add('hidden');
  try {
    const data = await api(state.registering ? '/auth/register' : '/auth/login', {
      method: 'POST', body: { email: $('email').value, password: $('password').value },
    });
    state.token = data.accessToken;
    $('userEmail').textContent = data.email;
    $('password').value = '';
    await showApp();
  } catch (err) {
    $('authError').textContent = err.message;
    $('authError').classList.remove('hidden');
  }
};

$('logoutBtn').onclick = async () => {
  await fetch(`${API}/api/v1/auth/logout`, { method: 'POST', credentials: API ? 'include' : 'same-origin' }).catch(() => {});
  showAuth();
};

// ---------------------------------------------------------------- dados
const currentMonth = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`; };

async function load() {
  // Firefox desktop não tem <input type=month>: valida o formato antes de chamar a API
  if (!/^\d{4}-\d{2}$/.test($('month').value)) $('month').value = currentMonth();
  const month = $('month').value;
  try {
    const [dash, cats, txs, audit] = await Promise.all([
      api(`/dashboard?month=${month}`),
      api('/categories'),
      api(`/transactions?month=${month}&size=100`),
      api('/audit?size=50'),
    ]);
    Object.assign(state, { dash, accounts: dash.accounts, categories: cats });
    renderAccounts(); renderChart(); renderSelects(); renderTransactions(txs.content); renderAudit(audit.content); renderLeftover();
  } catch (err) { toast(err.message, true); }
}

// ---------------------------------------------------------------- render
function renderAccounts() {
  const d = state.dash;
  const colors = { CHECKING: 'border-l-cyan-400', SAVINGS: 'border-l-emerald-400' };
  const cards = state.accounts.map((a) => `
    <div class="card border-l-4 ${colors[a.type] || ''}"><p class="text-xs uppercase text-slate-400">${esc(a.name)}</p>
    <h3 class="text-2xl font-bold mt-1">${brl(a.balance)}</h3></div>`);
  cards.push(`<div class="card border-l-4 border-l-purple-400"><p class="text-xs uppercase text-slate-400">Sobra do mês</p>
    <h3 class="text-2xl font-bold mt-1 text-purple-400">${brl(d.leftover)}</h3>
    <p class="text-xs text-slate-500 mt-1">Receitas ${brl(d.income)} · Despesas ${brl(d.expenses)}</p></div>`);
  $('accounts').innerHTML = cards.join('');
}

function renderChart() {
  const list = state.dash.expensesByCategory;
  $('expTotal').textContent = `Total: ${brl(state.dash.expenses)}`;
  $('chartEmpty').classList.toggle('hidden', list.length > 0);
  $('chart').classList.toggle('hidden', list.length === 0);
  state.chart?.destroy();
  if (!list.length) return;
  state.chart = new Chart($('chart'), {
    type: 'doughnut',
    data: { labels: list.map((c) => `${c.name} (${c.percentage}%)`), datasets: [{ data: list.map((c) => c.total), backgroundColor: list.map((c) => c.color || '#64748b'), borderWidth: 0 }] },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom', labels: { color: '#94a3b8', font: { size: 11 } } },
        tooltip: { callbacks: { label: (ctx) => ` ${brl(ctx.raw)}` } } },
    },
  });
}

function renderSelects() {
  const kind = $('txType').value;
  $('txCat').innerHTML = state.categories.filter((c) => c.kind === kind)
    .map((c) => `<option value="${esc(c.id)}">${esc(c.icon || '')} ${esc(c.name)}</option>`).join('');
  const prev = $('txAcc').value;
  $('txAcc').innerHTML = state.accounts.map((a) => `<option value="${esc(a.id)}">${esc(a.name)}</option>`).join('');
  if (prev) $('txAcc').value = prev;
}

function renderTransactions(list) {
  $('txList').innerHTML = list.length ? list.map((t) => {
    const credit = t.type === 'INCOME' || t.type === 'TRANSFER_IN';
    return `<div class="flex items-center justify-between p-3 rounded-xl bg-slate-900/60 border border-slate-800">
      <div class="flex items-center gap-3 min-w-0"><span class="text-xl">${t.transferId ? '🔁' : esc(t.categoryIcon || '💳')}</span>
        <div class="min-w-0"><p class="text-sm font-semibold truncate">${esc(t.description)}</p>
        <p class="text-xs text-slate-400">${esc(t.categoryName || t.accountName)} · ${t.occurredOn.split('-').reverse().join('/')}</p></div></div>
      <div class="flex items-center gap-3"><span class="text-sm font-bold font-mono ${credit ? 'text-emerald-400' : 'text-red-400'}">${credit ? '+' : '-'} ${brl(t.amount)}</span>
        <button data-del="${esc(t.id)}" class="text-slate-500 hover:text-red-400" title="Excluir">✕</button></div></div>`;
  }).join('') : '<p class="text-sm text-slate-500 text-center py-4">Nenhum lançamento neste mês.</p>';
}

function renderAudit(list) {
  $('auditBody').innerHTML = list.map((l) => `<tr><td class="p-2 text-slate-400 font-mono">${new Date(l.createdAt).toLocaleString('pt-BR')}</td>
    <td class="p-2 font-semibold text-cyan-400">${esc(l.action)}</td><td class="p-2">${esc(l.entity)}</td>
    <td class="p-2 text-slate-300">${esc(Object.entries(l.details || {}).map(([k, v]) => `${k}: ${v}`).join(' · '))}</td></tr>`).join('');
}

function renderLeftover() {
  $('leftover').textContent = brl(Math.max(state.dash.availableLeftover, 0));
}

// ---------------------------------------------------------------- ações
$('txType').onchange = renderSelects;
$('month').onchange = load;

$('txForm').onsubmit = async (e) => {
  e.preventDefault();
  try {
    await api('/transactions', { method: 'POST', body: {
      accountId: $('txAcc').value, categoryId: $('txCat').value, type: $('txType').value,
      amount: Number(Number($('txAmount').value).toFixed(2)), description: $('txDesc').value,
    } });
    e.target.reset(); toast('Lançamento salvo'); await load();
  } catch (err) { toast(err.message, true); }
};

$('txList').onclick = async (e) => {
  const id = e.target.dataset.del;
  if (!id || !confirm('Excluir este lançamento?')) return;
  try { await api(`/transactions/${id}`, { method: 'DELETE' }); toast('Lançamento excluído'); await load(); }
  catch (err) { toast(err.message, true); }
};

$('allocAll').onclick = () => {
  const checking = state.accounts.find((a) => a.type === 'CHECKING');
  const value = Math.min(Math.max(state.dash.availableLeftover, 0), checking ? checking.balance : 0);
  $('trAmount').value = value > 0 ? value.toFixed(2) : '';
};

$('trForm').onsubmit = async (e) => {
  e.preventDefault();
  const from = state.accounts.find((a) => a.type === 'CHECKING');
  const to = state.accounts.find((a) => a.type === 'SAVINGS');
  if (!from || !to) return toast('Cadastre uma conta corrente e uma poupança', true);
  try {
    await api('/transfers', { method: 'POST', body: {
      fromAccountId: from.id, toAccountId: to.id,
      amount: Number(Number($('trAmount').value).toFixed(2)), description: $('trDesc').value,
    } });
    $('trAmount').value = ''; toast('Transferência realizada'); await load(); setTab('dash');
  } catch (err) { toast(err.message, true); }
};

function setTab(name) {
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
  ['dash', 'sobra', 'audit'].forEach((t) => $(`t-${t}`).classList.toggle('hidden', t !== name));
}
document.querySelectorAll('.tab').forEach((b) => (b.onclick = () => setTab(b.dataset.tab)));

// ---------------------------------------------------------------- PWA
let installEvent;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault(); installEvent = e; $('installBtn').classList.remove('hidden');
});
$('installBtn').onclick = async () => {
  if (!installEvent) return;
  installEvent.prompt(); await installEvent.userChoice; installEvent = null; $('installBtn').classList.add('hidden');
};
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/service-worker.js');

// ---------------------------------------------------------------- boot
(async () => {
  $('month').value = currentMonth();
  (await refresh()) ? showApp() : showAuth();
})();
