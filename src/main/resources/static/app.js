'use strict';

// Servido pelo Spring (porta 8080): API na mesma origem ('').
// Aberto pelo Live Server / Vite (5500, 5501, 5173): usa a API em http://<mesmo host>:8080.
// Para outro endereço, defina window.API_BASE antes de carregar este arquivo.
const DEV_PORTS = ['5500', '5501', '5173'];
const API = window.API_BASE ?? (DEV_PORTS.includes(location.port) ? `http://${location.hostname}:8080` : '');
const state = { token: null, accounts: [], categories: [], dash: null, chart: null, registering: false, editingAcc: null };

const $ = (id) => document.getElementById(id);
const iso = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const fmtDate = (s) => s.split('-').reverse().join('/');
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
const currentMonth = () => iso().slice(0, 7);

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
    renderAccounts(); renderChart(); renderSelects(); renderTransactions(txs.content); renderAudit(audit.content); renderSavings(); renderAccountsTab();
  } catch (err) { toast(err.message, true); }
}

// ---------------------------------------------------------------- render
const byType = (t) => state.accounts.filter((a) => a.type === t);
const sum = (list) => list.reduce((acc, a) => acc + a.balance, 0);

function renderAccounts() {
  const d = state.dash, cc = byType('CHECKING'), sv = byType('SAVINGS');
  $('summary').innerHTML = `
    <div class="card border-l-4 border-l-cyan-400"><p class="text-xs uppercase text-slate-400">Total geral</p>
      <h3 class="text-2xl font-bold mt-1">${brl(d.totalBalance)}</h3>
      <p class="text-xs text-slate-500 mt-1">Contas ${brl(sum(cc))} · Poupança ${brl(sum(sv))}</p></div>
    <div class="card border-l-4 border-l-purple-400"><p class="text-xs uppercase text-slate-400">Sobra do mês</p>
      <h3 class="text-2xl font-bold mt-1 text-purple-400">${brl(d.leftover)}</h3>
      <p class="text-xs text-slate-500 mt-1">Receitas ${brl(d.income)} · Despesas ${brl(d.expenses)}</p></div>`;

  const group = (title, list, color, text) => `
    <div><div class="flex items-baseline gap-3 mb-2"><h2 class="font-semibold">${title}</h2><span class="text-sm font-semibold ${text}">${brl(sum(list))}</span></div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">${list.map((a) => `
        <div class="card border-l-4 ${color}"><p class="text-xs uppercase text-slate-400">${esc(a.name)}</p>
          <h3 class="text-xl font-bold mt-1">${brl(a.balance)}</h3></div>`).join('') || '<p class="text-sm text-slate-500">Nenhuma conta. Adicione na aba Contas.</p>'}
      </div></div>`;
  $('accGroups').innerHTML = group('Contas correntes', cc, 'border-l-cyan-400', 'text-cyan-400') + group('Poupança', sv, 'border-l-emerald-400', 'text-emerald-400');
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

function fill(id, list, withBalance = true) {
  const el = $(id), prev = el.value;
  el.innerHTML = list.map((a) => `<option value="${esc(a.id)}">${esc(a.name)}${withBalance ? ' · ' + brl(a.balance) : ''}</option>`).join('');
  if ([...el.options].some((o) => o.value === prev)) el.value = prev;
}

function renderSelects() {
  const kind = $('txType').value;
  $('txCat').innerHTML = state.categories.filter((c) => c.kind === kind)
    .map((c) => `<option value="${esc(c.id)}">${esc(c.icon || '')} ${esc(c.name)}</option>`).join('');
  fill('txAcc', state.accounts, false);
  fill('gFrom', byType('CHECKING')); fill('gTo', byType('SAVINGS'));
  fill('rFrom', byType('SAVINGS')); fill('rTo', byType('CHECKING'));
  $('rBalance').textContent = brl(state.accounts.find((a) => a.id === $('rFrom').value)?.balance ?? 0);
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
  $('savCards').innerHTML = byType('SAVINGS').map((a) => `
    <div class="card border-l-4 border-l-emerald-400"><p class="text-xs uppercase text-slate-400">${esc(a.name)}</p>
    <h3 class="text-xl font-bold mt-1">${brl(a.balance)}</h3></div>`).join('')
    || '<p class="text-sm text-slate-500">Nenhuma poupança. Adicione na aba Contas.</p>';
}

function renderAccountsTab() {
  $('accList').innerHTML = state.accounts.map((a) => `
    <div class="flex items-center justify-between p-3 rounded-xl bg-slate-900/60 border border-slate-800">
      <div class="min-w-0"><p class="text-sm font-semibold truncate">${esc(a.name)}</p>
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
  ['dash', 'poup', 'contas', 'seg', 'audit'].forEach((t) => $(`t-${t}`).classList.toggle('hidden', t !== name));
  if (name === 'seg') loadPasskeys();
}
document.querySelectorAll('.tab').forEach((b) => (b.onclick = () => setTab(b.dataset.tab)));

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
  (await refresh()) ? showApp() : showAuth();
})();
