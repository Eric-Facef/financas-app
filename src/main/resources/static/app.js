'use strict';

// Servido pelo Spring (porta 8080): API na mesma origem ('').
// Aberto pelo Live Server / Vite (5500, 5501, 5173): usa a API em http://<mesmo host>:8080.
// Para outro endereço, defina window.API_BASE antes de carregar este arquivo.
const DEV_PORTS = ['5500', '5501', '5173'];
const API = window.API_BASE ?? (DEV_PORTS.includes(location.port) ? `http://${location.hostname}:8080` : '');
const state = { token: null, accounts: [], categories: [], dash: null, chart: null, registering: false, editingAcc: null, stmt: null, stFilter: 'all', txs: [], hidden: false, printing: false, notes: [], wd: null, wdChart: null, wdMode: 'total', editingNote: null };

const store = {
  get: (k, d = null) => { try { return localStorage.getItem(k) ?? d; } catch { return d; } },
  set: (k, v) => { try { localStorage.setItem(k, v); } catch { /* sem armazenamento */ } },
};
state.hidden = store.get('fin.hide') === '1';

const $ = (id) => document.getElementById(id);
const iso = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const fmtDate = (s) => s.split('-').reverse().join('/');
const brl = (v) => (state.hidden && !state.printing ? 'R$ ••••' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }));
// ---- cor do banco (pelo nome da conta)
const norm = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const BANKS = [
  [/santander/, '#ff2a2a'], [/\bbb\b|banco do brasil|brasil/, '#ffd400'], [/nubank|\bnu\b/, '#a63cff'],
  [/itau/, '#ff7a1a'], [/bradesco/, '#ff2d6f'], [/caixa|\bcef\b/, '#2f8cff'], [/\binter\b/, '#ff8a00'],
  [/\bc6\b/, '#d9d9e0'], [/picpay/, '#21e06b'], [/mercado ?pago/, '#2cc7ff'], [/sicredi/, '#7ed321'],
  [/sicoob/, '#00c2a8'], [/\bbtg\b/, '#4d7cff'],
];
const bankColor = (a) => (BANKS.find(([re]) => re.test(norm(a.name))) || [])[1] || (a.type === 'SAVINGS' ? '#7c8cff' : '#00e5ff');
const bankStyle = (a) => { const c = bankColor(a); return `--c:${c};--c-rgb:${[1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16)).join(',')}`; };
const accCard = (a) => `<div class="card acc-card" style="${bankStyle(a)}"><p class="text-xs acc-name truncate">${esc(a.name)}</p><h3 class="text-xl font-bold mt-1">${brl(a.balance)}</h3></div>`;

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function toast(msg, isError = false) {
  const el = $('toast');
  el.textContent = msg;
  el.className = `fixed bottom-4 left-1/2 -translate-x-1/2 px-4 py-2 rounded-lg text-sm shadow-lg ${isError ? 'toast-err' : 'toast-ok'}`;
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

function showAuth(note) {
  state.token = null;
  Object.assign(state, { dash: null, accounts: [], txs: [], stmt: null, notes: [], wd: null, editingNote: null });
  state.chart?.destroy(); state.chart = null;
  state.wdChart?.destroy(); state.wdChart = null;
  ['summary', 'accGroups', 'txList', 'stmtDoc', 'savCards', 'accList', 'auditBody', 'pkList', 'txCat', 'txAcc', 'gFrom', 'gTo', 'rFrom', 'rTo', 'stAcc', 'noteList'].forEach((id) => ($(id).innerHTML = ''));
  ['leftover', 'rBalance', 'expTotal', 'wdTop', 'wdSub'].forEach((id) => ($(id).textContent = ''));
  $('appView').classList.add('hidden');
  $('authView').classList.remove('hidden');
  $('authNote').textContent = note || ''; $('authNote').classList.toggle('hidden', !note);
}

async function showApp() {
  $('authView').classList.add('hidden');
  $('appView').classList.remove('hidden');
  idle.last = Date.now();
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

async function logout(note) {
  await fetch(`${API}/api/v1/auth/logout`, { method: 'POST', credentials: API ? 'include' : 'same-origin' }).catch(() => {});
  showAuth(note);
}
$('logoutBtn').onclick = () => logout();

// ---------------------------------------------------------------- dados
const currentMonth = () => iso().slice(0, 7);

async function load() {
  // Firefox desktop não tem <input type=month>: valida o formato antes de chamar a API
  if (!/^\d{4}-\d{2}$/.test($('month').value)) $('month').value = currentMonth();
  const month = $('month').value;
  try {
    const [dash, cats, txs, audit, wd, notes] = await Promise.all([
      api(`/dashboard?month=${month}`),
      api('/categories'),
      api(`/transactions?month=${month}&size=100`),
      api('/audit?size=50'),
      api(`/reports/weekday?month=${month}`).catch(() => null),
      api('/notes').catch(() => null),
    ]);
    Object.assign(state, { dash, accounts: dash.accounts, categories: cats, txs: txs.content, wd, notes: notes ?? state.notes });
    renderAccounts(); renderChart(); renderSelects(); renderTransactions(state.txs); renderAudit(audit.content); renderSavings(); renderAccountsTab(); renderWeekday(); renderNotes();
  } catch (err) { toast(err.message, true); }
}

// ---------------------------------------------------------------- render
const byType = (t) => state.accounts.filter((a) => a.type === t);
const sum = (list) => list.reduce((acc, a) => acc + a.balance, 0);

function renderAccounts() {
  const d = state.dash, cc = byType('CHECKING'), sv = byType('SAVINGS');
  $('summary').innerHTML = `
    <div class="card acc-card" style="--c:#00e5ff;--c-rgb:0,229,255"><p class="text-xs text-slate-400">Total geral</p>
      <h3 class="text-3xl font-bold mt-1">${brl(d.totalBalance)}</h3>
      <p class="text-xs text-slate-500 mt-2">Contas ${brl(sum(cc))} · Poupança ${brl(sum(sv))}</p></div>
    <div class="card acc-card" style="--c:#b98cff;--c-rgb:185,140,255"><p class="text-xs text-slate-400">Sobra do mês</p>
      <h3 class="text-3xl font-bold mt-1 ${d.leftover < 0 ? 'text-red-400' : 'text-purple-400'}">${brl(d.leftover)}</h3>
      <p class="text-xs text-slate-500 mt-2">Receitas <span class="text-emerald-400">${brl(d.income)}</span> · Despesas <span class="text-red-400">${brl(d.expenses)}</span></p></div>`;

  const group = (title, list, text) => `
    <div><div class="flex items-baseline gap-3 mb-2"><h2 class="font-semibold">${title}</h2><span class="text-sm font-semibold ${text}">${brl(sum(list))}</span></div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">${list.map(accCard).join('') || '<p class="text-sm text-slate-500">Nenhuma conta. Adicione na aba Contas.</p>'}</div></div>`;
  $('accGroups').innerHTML = group('Contas correntes', cc, 'text-cyan-400') + group('Poupança', sv, 'text-purple-400');
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
    data: { labels: list.map((c) => `${c.name} (${c.percentage}%)`), datasets: [{ data: list.map((c) => c.total), backgroundColor: list.map((c) => c.color || '#6d6d7c'), borderColor: '#000', borderWidth: 2 }] },
    options: {
      responsive: true, maintainAspectRatio: false, cutout: '68%',
      plugins: { legend: { position: 'bottom', labels: { color: '#a1a1b0', font: { size: 11 } } },
        tooltip: { callbacks: { label: (ctx) => ` ${brl(ctx.raw)}` } } },
    },
  });
}

function fill(id, list, withBalance = true) {
  const el = $(id), prev = el.value;
  el.innerHTML = list.map((a) => `<option value="${esc(a.id)}">${esc(a.name)}${withBalance ? ' · ' + brl(a.balance) : ''}</option>`).join('');
  if ([...el.options].some((o) => o.value === prev)) el.value = prev;
}

function renderSelects() {
  const kind = $('txType').value;
  const prevCat = $('txCat').value;
  $('txCat').innerHTML = state.categories.filter((c) => c.kind === kind)
    .map((c) => `<option value="${esc(c.id)}">${esc(c.icon || '')} ${esc(c.name)}</option>`).join('');
  if ([...$('txCat').options].some((o) => o.value === prevCat)) $('txCat').value = prevCat;
  fill('txAcc', state.accounts, false);
  fill('gFrom', byType('CHECKING')); fill('gTo', byType('SAVINGS'));
  fill('rFrom', byType('SAVINGS')); fill('rTo', byType('CHECKING'));
  $('rBalance').textContent = brl(state.accounts.find((a) => a.id === $('rFrom').value)?.balance ?? 0);
  const prevStmt = $('stAcc').value;
  $('stAcc').innerHTML = '<option value="">Total (todas as contas)</option>'
    + state.accounts.map((a) => `<option value="${esc(a.id)}">${esc(a.name)}</option>`).join('');
  if ([...$('stAcc').options].some((o) => o.value === prevStmt)) $('stAcc').value = prevStmt;
}

function renderTransactions(list) {
  $('txList').innerHTML = list.length ? list.map((t) => {
    const credit = t.type === 'INCOME' || t.type === 'TRANSFER_IN';
    return `<div class="flex items-center justify-between p-3 rounded-xl bg-slate-900/60 border border-slate-800">
      <div class="flex items-center gap-3 min-w-0"><span class="text-xl">${t.transferId ? '🔁' : esc(t.categoryIcon || '💳')}</span>
        <div class="min-w-0"><p class="text-sm font-semibold truncate">${esc(t.description)}</p>
        <p class="text-xs text-slate-400">${esc(t.categoryName || t.accountName)} · ${esc(t.accountName)} · ${fmtDate(t.occurredOn)}${t.occurredOn > iso() ? ' <span class="text-amber-400">(agendado)</span>' : ''}</p></div></div>
      <div class="flex items-center gap-3"><span class="text-sm font-bold font-mono ${credit ? 'text-emerald-400' : 'text-red-400'}">${credit ? '+' : '-'} ${brl(t.amount)}</span>
        <button data-del="${esc(t.id)}" class="text-slate-500 hover:text-red-400" title="Excluir">✕</button></div></div>`;
  }).join('') : '<p class="text-sm text-slate-500 text-center py-4">Nenhum lançamento neste mês.</p>';
}

function renderAudit(list) {
  $('auditBody').innerHTML = list.map((l) => `<tr><td class="p-2 text-slate-400 font-mono">${new Date(l.createdAt).toLocaleString('pt-BR')}</td>
    <td class="p-2 font-semibold text-cyan-400">${esc(l.action)}</td><td class="p-2">${esc(l.entity)}</td>
    <td class="p-2 text-slate-300">${esc(Object.entries(l.details || {}).map(([k, v]) => `${k}: ${v}`).join(' · '))}</td></tr>`).join('');
}

function renderSavings() {
  $('leftover').textContent = brl(Math.max(state.dash.availableLeftover, 0));
  $('savCards').innerHTML = byType('SAVINGS').map(accCard).join('') || '<p class="text-sm text-slate-500">Nenhuma poupança. Adicione na aba Contas.</p>';
}

function renderAccountsTab() {
  $('accList').innerHTML = state.accounts.map((a) => `
    <div class="flex items-center justify-between gap-3 p-3 rounded-xl bg-slate-900/60 border border-slate-800" style="${bankStyle(a)}">
      <span class="dot"></span>
      <div class="min-w-0 flex-1"><p class="text-sm font-semibold truncate">${esc(a.name)}</p>
        <p class="text-xs text-slate-400">${a.type === 'SAVINGS' ? 'Poupança' : 'Conta corrente'} · ${brl(a.balance)}</p></div>
      <div class="flex gap-3 text-xs"><button data-edit="${esc(a.id)}" class="text-cyan-400">Editar</button>
        <button data-delacc="${esc(a.id)}" class="text-slate-500 hover:text-red-400">Excluir</button></div></div>`).join('');
}

// ---------------------------------------------------------------- ações
$('txType').onchange = renderSelects;
$('month').onchange = load;

$('txForm').onsubmit = async (e) => {
  e.preventDefault();
  try {
    await api('/transactions', { method: 'POST', body: {
      accountId: $('txAcc').value, categoryId: $('txCat').value, type: $('txType').value,
      amount: Number(Number($('txAmount').value).toFixed(2)), description: $('txDesc').value, occurredOn: $('txDate').value,
    } });
    const date = $('txDate').value;
    e.target.reset(); $('txDate').value = iso(); toast('Lançamento salvo');
    $('month').value = date.slice(0, 7);   // mostra o mês do lançamento, mesmo que seja outro
    await load();
  } catch (err) { toast(err.message, true); }
};

$('txList').onclick = async (e) => {
  const id = e.target.dataset.del;
  if (!id || !confirm('Excluir este lançamento?')) return;
  try { await api(`/transactions/${id}`, { method: 'DELETE' }); toast('Lançamento excluído'); await load(); }
  catch (err) { toast(err.message, true); }
};

const balanceOf = (id) => state.accounts.find((a) => a.id === id)?.balance ?? 0;

$('gAll').onclick = () => {
  const value = Math.min(Math.max(state.dash.availableLeftover, 0), balanceOf($('gFrom').value));
  $('gAmount').value = value > 0 ? value.toFixed(2) : '';
};
$('rAll').onclick = () => { const v = balanceOf($('rFrom').value); $('rAmount').value = v > 0 ? v.toFixed(2) : ''; };
$('rFrom').onchange = () => { $('rBalance').textContent = brl(balanceOf($('rFrom').value)); };

// p = 'g' (guardar na poupança) ou 'r' (devolver para a conta corrente)
function bindTransfer(p, okMsg) {
  $(`${p}Form`).onsubmit = async (e) => {
    e.preventDefault();
    const from = $(`${p}From`).value, to = $(`${p}To`).value;
    if (!from || !to) return toast('Cadastre as contas de origem e destino na aba Contas', true);
    const date = $(`${p}Date`).value || iso();
    try {
      await api('/transfers', { method: 'POST', body: {
        fromAccountId: from, toAccountId: to,
        amount: Number(Number($(`${p}Amount`).value).toFixed(2)), description: $(`${p}Desc`).value, occurredOn: date,
      } });
      $(`${p}Amount`).value = ''; $(`${p}Date`).value = iso(); toast(okMsg);
      $('month').value = date.slice(0, 7);
      await load();
    } catch (err) { toast(err.message, true); }
  };
}
bindTransfer('g', 'Valor guardado na poupança');
bindTransfer('r', 'Valor devolvido para a conta corrente');

// ---- contas
const PRESETS = { santander: ['Santander', 'CHECKING'], nubank: ['Nubank', 'CHECKING'], bb: ['Banco do Brasil', 'CHECKING'], bbp: ['Poupança BB', 'SAVINGS'] };
document.querySelectorAll('[data-preset]').forEach((b) => (b.onclick = () => {
  if (state.editingAcc) return;
  [$('accName').value, $('accType').value] = PRESETS[b.dataset.preset];
}));

function resetAccForm() {
  state.editingAcc = null;
  $('accForm').reset();
  $('accType').disabled = false;
  $('accFormTitle').textContent = 'Adicionar conta';
  $('accSubmit').textContent = 'Adicionar conta';
  $('accCancel').classList.add('hidden');
  $('presets').classList.remove('hidden');
}
$('accCancel').onclick = resetAccForm;

$('accList').onclick = async (e) => {
  const edit = e.target.dataset.edit, del = e.target.dataset.delacc;
  if (edit) {
    const a = state.accounts.find((x) => x.id === edit);
    state.editingAcc = edit;
    $('accName').value = a.name; $('accType').value = a.type; $('accType').disabled = true;
    $('accInitial').value = a.initialBalance;
    $('accFormTitle').textContent = `Editar conta: ${a.name}`;
    $('accSubmit').textContent = 'Salvar alterações';
    $('accCancel').classList.remove('hidden'); $('presets').classList.add('hidden');
    $('accName').focus();
  } else if (del && confirm('Excluir esta conta? (só é possível se ela não tiver lançamentos)')) {
    try { await api(`/accounts/${del}`, { method: 'DELETE' }); toast('Conta excluída'); await load(); }
    catch (err) { toast(err.message, true); }
  }
};

$('accForm').onsubmit = async (e) => {
  e.preventDefault();
  const initialBalance = Number(Number($('accInitial').value || 0).toFixed(2));
  try {
    if (state.editingAcc) {
      await api(`/accounts/${state.editingAcc}`, { method: 'PUT', body: { name: $('accName').value, initialBalance } });
    } else {
      await api('/accounts', { method: 'POST', body: { name: $('accName').value, type: $('accType').value, initialBalance } });
    }
    resetAccForm(); toast('Conta salva'); await load();
  } catch (err) { toast(err.message, true); }
};

function setTab(name) {
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
  ['dash', 'ext', 'rel', 'poup', 'contas', 'notas', 'seg', 'audit'].forEach((t) => $(`t-${t}`).classList.toggle('hidden', t !== name));
  if (name === 'seg') loadPasskeys();
  if (name === 'ext') loadStatement();
  if (name === 'rel') { renderChart(); renderWeekday(); }   // gráficos recriados já com a aba visível (tamanho certo)
  if (name === 'notas') loadNotes();
}
document.querySelectorAll('.tab').forEach((b) => (b.onclick = () => setTab(b.dataset.tab)));

// ---------------------------------------------------------------- relatório: gasto por dia da semana
const WD = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];

function renderWeekday() {
  const r = state.wd;
  state.wdChart?.destroy(); state.wdChart = null;
  const empty = !r || Number(r.total) === 0;
  $('wdEmpty').textContent = r ? 'Sem despesas neste mês.' : 'Relatório indisponível no momento.';
  $('wdEmpty').classList.toggle('hidden', !empty);
  $('wdChart').classList.toggle('hidden', empty);
  $('wdTop').textContent = '';
  $('wdSub').textContent = '';
  if (r) {
    const [y, m] = r.month.split('-').map(Number);
    const label = new Date(y, m - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
    $('wdSub').textContent = `${label} · até ${fmtDate(r.until)}`;
  }
  if (empty) return;

  const avg = state.wdMode === 'avg';
  const vals = r.days.map((d) => Number(avg ? d.average : d.total));
  const top = vals.indexOf(Math.max(...vals));
  const d = r.days[top];
  $('wdTop').textContent = `Dia com mais gastos: ${WD[top]}, ${brl(vals[top])}`
    + (avg ? ' em média por dia.' : ` em ${d.entries} lançamento${d.entries === 1 ? '' : 's'}.`);

  state.wdChart = new Chart($('wdChart'), {
    type: 'bar',
    data: { labels: WD.map((n) => n.slice(0, 3)), datasets: [{ data: vals, borderRadius: 6,
      backgroundColor: vals.map((_, i) => (i === top ? '#ff4d6d' : 'rgba(255,77,109,.35)')) }] },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: (c) => ` ${brl(c.raw)}` } } },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#a1a1b0' } },
        y: { beginAtZero: true, grid: { color: '#1d1d27' },
          ticks: { color: '#8f8fa0', callback: (v) => (state.hidden ? '' : Number(v).toLocaleString('pt-BR')) } },
      },
    },
  });
}

document.querySelectorAll('[data-wd]').forEach((b) => (b.onclick = () => {
  state.wdMode = b.dataset.wd;
  document.querySelectorAll('[data-wd]').forEach((x) => x.classList.toggle('on', x === b));
  renderWeekday();
}));

// ---------------------------------------------------------------- notas (lembretes soltos)
function renderNotes() {
  const pending = state.notes.filter((n) => !n.done).length, done = state.notes.length - pending;
  document.querySelector('[data-tab=notas]').textContent = pending ? `Notas (${pending})` : 'Notas';
  $('noteClear').classList.toggle('hidden', done === 0);
  $('noteClear').textContent = `Apagar feitas (${done})`;
  $('noteList').innerHTML = state.notes.length ? state.notes.map((n) => `
    <div class="flex items-center gap-3 p-3 rounded-xl bg-slate-900/60 border border-slate-800">
      <button data-ndone="${esc(n.id)}" role="checkbox" aria-checked="${n.done}" aria-label="Marcar como feita" class="note-box ${n.done ? 'on' : ''}">${n.done ? '✓' : ''}</button>
      ${state.editingNote === n.id
        ? `<input data-nedit="${esc(n.id)}" class="in flex-1" maxlength="300" aria-label="Editar nota" value="${esc(n.text)}">`
        : `<p data-ntext="${esc(n.id)}" class="flex-1 text-sm break-words cursor-text ${n.done ? 'line-through text-slate-500' : ''}">${esc(n.text)}</p>`}
      <button data-ndel="${esc(n.id)}" class="text-slate-500 hover:text-red-400 px-1" aria-label="Excluir nota">✕</button></div>`).join('')
    : '<p class="text-sm text-slate-500 text-center py-4">Nenhuma nota. Escreva um lembrete acima, por exemplo: dar R$ 7 para a mãe.</p>';
  $('noteList').querySelector('[data-nedit]')?.focus();
}

async function loadNotes() {
  try { state.notes = await api('/notes'); renderNotes(); }
  catch (err) { toast(err.message, true); }
}

$('noteForm').onsubmit = async (e) => {
  e.preventDefault();
  const text = $('noteText').value.trim();
  if (!text) return;
  try {
    await api('/notes', { method: 'POST', body: { text } });
    $('noteText').value = '';
    await loadNotes();
  } catch (err) { toast(err.message, true); }
};

$('noteList').onclick = async (e) => {
  const t = e.target.closest('[data-ndone],[data-ndel],[data-ntext]');
  if (!t) return;
  try {
    if (t.dataset.ndone) {
      const n = state.notes.find((x) => x.id === t.dataset.ndone);
      n.done = !n.done; renderNotes();   // marca na hora; a lista é recarregada em seguida (feitas vão para o fim)
      await api(`/notes/${n.id}`, { method: 'PUT', body: { text: n.text, done: n.done } });
      await loadNotes();
    } else if (t.dataset.ndel) {
      await api(`/notes/${t.dataset.ndel}`, { method: 'DELETE' });
      await loadNotes();
    } else {
      state.editingNote = t.dataset.ntext; renderNotes();
    }
  } catch (err) { toast(err.message, true); await loadNotes(); }
};

async function saveNoteEdit(input) {
  const id = input.dataset.nedit;
  if (state.editingNote !== id) return;   // já cancelado/salvo (o blur dispara de novo ao remover o campo)
  state.editingNote = null;
  const n = state.notes.find((x) => x.id === id), text = input.value.trim();
  if (!n || !text || text === n.text) return renderNotes();
  try { await api(`/notes/${id}`, { method: 'PUT', body: { text, done: n.done } }); await loadNotes(); }
  catch (err) { toast(err.message, true); renderNotes(); }
}
$('noteList').addEventListener('focusout', (e) => { if (e.target.dataset?.nedit) saveNoteEdit(e.target); });
$('noteList').addEventListener('keydown', (e) => {
  if (!e.target.dataset?.nedit) return;
  if (e.key === 'Enter') { e.preventDefault(); e.target.blur(); }                 // o blur salva
  if (e.key === 'Escape') { state.editingNote = null; renderNotes(); }           // cancela sem salvar
});

$('noteClear').onclick = async () => {
  if (!confirm('Apagar todas as notas marcadas como feitas?')) return;
  try { await api('/notes/done', { method: 'DELETE' }); toast('Notas feitas apagadas'); await loadNotes(); }
  catch (err) { toast(err.message, true); }
};

// ---------------------------------------------------------------- extrato (por conta ou total)
const dayTitle = (s) => {
  const [y, m, d] = s.split('-').map(Number);
  const t = new Date(y, m - 1, d).toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  return t.charAt(0).toUpperCase() + t.slice(1);
};

function stmtRange(kind) {
  const now = new Date(), y = now.getFullYear(), m = now.getMonth();
  if (kind === 'month') return [iso(new Date(y, m, 1)), iso(new Date(y, m + 1, 0))];
  if (kind === 'prev') return [iso(new Date(y, m - 1, 1)), iso(new Date(y, m, 0))];
  if (kind === '30') return [iso(new Date(y, m, now.getDate() - 29)), iso(now)];
  return [$('stFrom').value, $('stTo').value];
}

async function loadStatement() {
  const [from, to] = stmtRange($('stPeriod').value);
  if (!from || !to) return;
  const q = new URLSearchParams({ from, to });
  if ($('stAcc').value) q.set('accountId', $('stAcc').value);
  try { state.stmt = await api(`/statements?${q}`); renderStatement(); }
  catch (err) { toast(err.message, true); }
}

function stmtEntryHtml(e, consolidated, withDate = false) {
  const amount = e.internal ? `<span class="font-semibold text-slate-300">${brl(e.amount)}</span>`
    : e.delta >= 0 ? `<span class="font-semibold pos text-emerald-400">+ ${brl(e.amount)}</span>`
      : `<span class="font-semibold neg text-red-400">− ${brl(e.amount)}</span>`;
  const icon = e.type.startsWith('TRANSFER') ? '🔁' : esc(e.categoryIcon || '💳');
  const sub = [e.label || e.categoryName, consolidated && !e.internal ? e.accountName : null, withDate ? fmtDate(e.date) : null]
    .filter(Boolean).map(esc).join(' · ');
  return `<div class="flex items-center justify-between gap-3 py-2">
    <div class="flex items-center gap-3 min-w-0"><span class="text-lg">${icon}</span>
      <div class="min-w-0"><p class="text-sm font-semibold truncate">${esc(e.description)}</p><p class="text-xs text-slate-400">${sub}</p></div></div>
    <div class="text-sm whitespace-nowrap">${amount}</div></div>`;
}

function renderStatement() {
  const s = state.stmt;
  if (!s) return;
  const f = state.stFilter;
  const keep = (e) => f === 'all' || (f === 'in' && e.delta > 0) || (f === 'out' && e.delta < 0);
  const days = [...s.days].reverse().map((d) => ({ ...d, entries: d.entries.filter(keep).reverse() })).filter((d) => d.entries.length);
  const upcoming = s.scheduled.filter(keep);
  const row = (label, value, cls = '') => `<div class="flex justify-between py-1"><span class="text-slate-400">${label}</span><span class="font-semibold ${cls}">${value}</span></div>`;

  $('stmtDoc').innerHTML = `
    <div class="flex justify-between items-start gap-3 pb-3 border-b border-slate-700">
      <div><p class="text-xs text-slate-400">Extrato</p>
        <h2 class="text-lg font-bold">${esc(s.accountName)}</h2>
        <p class="text-xs text-slate-400">${fmtDate(s.from)} a ${fmtDate(s.to)}</p></div>
      <div class="text-right text-xs text-slate-400"><p class="font-semibold">Finanças</p>
        <p>Gerado em ${new Date(s.generatedAt).toLocaleString('pt-BR')}</p></div>
    </div>
    <div class="py-3 text-sm border-b border-slate-700">
      ${row('Saldo anterior', brl(s.openingBalance))}${row('Entradas', '+ ' + brl(s.totalIn), 'pos text-emerald-400')}
      ${row('Saídas', '− ' + brl(s.totalOut), 'neg text-red-400')}${row('Saldo final', brl(s.closingBalance))}
    </div>
    ${days.length ? days.map((d) => `
      <div class="pt-4"><p class="text-xs font-semibold text-slate-400 pb-1">${esc(dayTitle(d.date))}</p>
        <div class="divide-y divide-slate-800/70">${d.entries.map((e) => stmtEntryHtml(e, s.consolidated)).join('')}</div>
        ${f === 'all' ? `<div class="flex justify-between text-xs text-slate-400 pt-2 border-t border-slate-800"><span>Saldo do dia</span><span class="font-semibold">${brl(d.closingBalance)}</span></div>` : ''}
      </div>`).join('') : '<p class="text-sm text-slate-500 text-center py-6">Nenhum lançamento no período.</p>'}
    ${upcoming.length ? `<div class="pt-5"><p class="text-xs font-semibold text-amber-400 pb-1">Agendados (ainda não entram no saldo)</p>
      <div class="divide-y divide-slate-800/70">${upcoming.map((e) => stmtEntryHtml(e, s.consolidated, true)).join('')}</div></div>` : ''}`;
}

function buildCsv(s) {
  const q = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const n = (v) => Number(v).toFixed(2).replace('.', ',');
  const lines = [['"Data"', '"Descrição"', '"Conta"', '"Categoria / transferência"', 'Valor', 'Saldo']];
  s.days.forEach((d) => d.entries.forEach((e) => lines.push(
    [q(fmtDate(e.date)), q(e.description), q(e.accountName), q(e.label || e.categoryName || ''), n(e.delta), n(e.balanceAfter)])));
  return lines.map((l) => l.join(';')).join('\r\n');
}

$('stAcc').onchange = loadStatement;
$('stPeriod').onchange = () => { $('stCustom').classList.toggle('hidden', $('stPeriod').value !== 'custom'); loadStatement(); };
$('stFrom').onchange = $('stTo').onchange = loadStatement;
document.querySelectorAll('[data-sf]').forEach((b) => (b.onclick = () => {
  state.stFilter = b.dataset.sf;
  document.querySelectorAll('[data-sf]').forEach((x) => x.classList.toggle('on', x === b));
  renderStatement();
}));
$('stPrint').onclick = () => window.print();
$('stCsv').onclick = () => {
  if (!state.stmt) return;
  const s = state.stmt;
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob(['\uFEFF' + buildCsv(s)], { type: 'text/csv;charset=utf-8' }));
  const nome = (s.accountName || 'total').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-');
  a.download = `extrato-${nome}-${s.from}_${s.to}.csv`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
};

// ---------------------------------------------------------------- login por digital (passkeys / WebAuthn)
// O servidor manda/recebe binários em base64url; o navegador usa ArrayBuffer. Estas funções convertem.
const b64 = {
  enc: (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''),
  dec: (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)).buffer,
};
const passkeysSupported = () => !!(window.PublicKeyCredential && navigator.credentials);

const creationOptions = (pk) => ({
  ...pk,
  challenge: b64.dec(pk.challenge),
  user: { ...pk.user, id: b64.dec(pk.user.id) },
  excludeCredentials: (pk.excludeCredentials || []).map((c) => ({ ...c, id: b64.dec(c.id) })),
});
const requestOptions = (pk) => ({
  ...pk,
  challenge: b64.dec(pk.challenge),
  allowCredentials: (pk.allowCredentials || []).map((c) => ({ ...c, id: b64.dec(c.id) })),
});
// Mesmo formato que a biblioteca da Yubico espera (igual ao do "webauthn-json")
const attestationJson = (c) => ({
  type: c.type, id: c.id, rawId: b64.enc(c.rawId), clientExtensionResults: c.getClientExtensionResults(),
  response: {
    clientDataJSON: b64.enc(c.response.clientDataJSON),
    attestationObject: b64.enc(c.response.attestationObject),
    transports: c.response.getTransports ? c.response.getTransports() : [],
  },
});
const assertionJson = (c) => ({
  type: c.type, id: c.id, rawId: b64.enc(c.rawId), clientExtensionResults: c.getClientExtensionResults(),
  response: {
    clientDataJSON: b64.enc(c.response.clientDataJSON),
    authenticatorData: b64.enc(c.response.authenticatorData),
    signature: b64.enc(c.response.signature),
    ...(c.response.userHandle && { userHandle: b64.enc(c.response.userHandle) }),
  },
});

function pkError(err) {
  if (err.name === 'NotAllowedError') return 'Operação cancelada ou expirou. Tente de novo.';
  if (err.name === 'InvalidStateError') return 'Este aparelho já está cadastrado.';
  if (err.name === 'SecurityError') return 'O endereço do site não confere com o configurado no servidor (PASSKEY_RP_ID).';
  return err.message;
}

async function loginWithPasskey() {
  const { challengeId, options } = await api('/auth/passkey/options', { method: 'POST' });
  const cred = await navigator.credentials.get({ publicKey: requestOptions(options.publicKey) });
  const data = await api('/auth/passkey/login', { method: 'POST', body: { challengeId, credential: assertionJson(cred) } });
  state.token = data.accessToken;
  $('userEmail').textContent = data.email;
  await showApp();
}

async function registerPasskey() {
  const name = prompt('Dê um nome para este aparelho (ex.: Meu celular)', 'Meu celular');
  if (name === null) return;
  const { challengeId, options } = await api('/passkeys/register/options', { method: 'POST' });
  const cred = await navigator.credentials.create({ publicKey: creationOptions(options.publicKey) });
  await api('/passkeys/register/verify', { method: 'POST', body: { challengeId, name, credential: attestationJson(cred) } });
}

async function loadPasskeys() {
  const ok = passkeysSupported();
  $('pkUnsupported').classList.toggle('hidden', ok);
  $('pkAdd').classList.toggle('hidden', !ok);
  try {
    const list = await api('/passkeys');
    const d = (v) => new Date(v).toLocaleDateString('pt-BR');
    $('pkList').innerHTML = list.length ? list.map((p) => `
      <div class="flex items-center justify-between p-3 rounded-xl bg-slate-900/60 border border-slate-800">
        <div class="min-w-0"><p class="text-sm font-semibold truncate">${esc(p.name)}</p>
          <p class="text-xs text-slate-400">Criada em ${d(p.createdAt)}${p.lastUsedAt ? ' · último uso ' + d(p.lastUsedAt) : ''}</p></div>
        <button data-delpk="${esc(p.id)}" class="text-xs text-slate-500 hover:text-red-400">Remover</button></div>`).join('')
      : '<p class="text-sm text-slate-500">Nenhuma digital cadastrada.</p>';
  } catch (err) { toast(err.message, true); }
}

$('pkLogin').onclick = async () => {
  $('authError').classList.add('hidden');
  try { await loginWithPasskey(); }
  catch (err) { $('authError').textContent = pkError(err); $('authError').classList.remove('hidden'); }
};
$('pkAdd').onclick = async () => {
  try { await registerPasskey(); toast('Digital ativada neste aparelho'); await loadPasskeys(); }
  catch (err) { toast(pkError(err), true); }
};
$('pkList').onclick = async (e) => {
  const id = e.target.dataset.delpk;
  if (!id || !confirm('Remover esta digital? Você ainda poderá entrar com e-mail e senha.')) return;
  try { await api(`/passkeys/${id}`, { method: 'DELETE' }); toast('Digital removida'); await loadPasskeys(); }
  catch (err) { toast(err.message, true); }
};
if (passkeysSupported()) $('pkLoginBox').classList.remove('hidden');

// ---------------------------------------------------------------- olho: esconde os valores
function setHidden(v) {
  state.hidden = v; store.set('fin.hide', v ? '1' : '0');
  $('eyeBtn').setAttribute('aria-pressed', String(v));
  $('eyeOn').classList.toggle('hidden', v); $('eyeOff').classList.toggle('hidden', !v);
  if (state.dash) { renderAccounts(); renderChart(); renderSelects(); renderTransactions(state.txs); renderSavings(); renderAccountsTab(); renderStatement(); renderWeekday(); }
}
$('eyeBtn').onclick = () => setHidden(!state.hidden);
setHidden(state.hidden);
// o extrato impresso / em PDF sempre sai com os valores
window.addEventListener('beforeprint', () => { state.printing = true; renderStatement(); });
window.addEventListener('afterprint', () => { state.printing = false; renderStatement(); });

// ---------------------------------------------------------------- sair após ficar sem mexer
const idle = { last: Date.now(), warn: false };
const IDLE_WARN_S = 30;
const hideIdleWarn = () => { idle.warn = false; $('idleWarn').classList.add('hidden'); };
const bump = () => { idle.last = Date.now(); if (idle.warn) hideIdleWarn(); };
['pointerdown', 'keydown', 'touchstart', 'wheel', 'scroll'].forEach((ev) => window.addEventListener(ev, bump, { passive: true, capture: true }));

function idleTick() {
  const limit = Number(store.get('fin.idleMin', '5')) * 60000;
  if (!limit || $('appView').classList.contains('hidden')) return hideIdleWarn();
  const left = limit - (Date.now() - idle.last);
  if (left <= 0) { hideIdleWarn(); return logout('Você saiu por inatividade. Entre de novo para continuar.'); }
  if (left <= IDLE_WARN_S * 1000) {
    $('idleMsg').textContent = `Saindo por inatividade em ${Math.ceil(left / 1000)}s`;
    idle.warn = true; $('idleWarn').classList.remove('hidden');
  }
}
setInterval(idleTick, 1000);   // compara horários (não conta ticks): funciona mesmo se o celular suspender o app
document.addEventListener('visibilitychange', () => { if (!document.hidden) idleTick(); });
$('idleStay').onclick = bump;
$('idleSel').value = store.get('fin.idleMin', '5');
$('idleSel').onchange = () => { store.set('fin.idleMin', $('idleSel').value); bump(); toast('Configuração salva'); };

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
  ['txDate', 'gDate', 'rDate'].forEach((id) => ($(id).value = iso()));
  $('stFrom').value = iso(new Date(new Date().getFullYear(), new Date().getMonth(), 1)); $('stTo').value = iso();
  (await refresh()) ? showApp() : showAuth();
})();
