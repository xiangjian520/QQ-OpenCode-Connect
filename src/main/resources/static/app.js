const WEB_KEY = 'web:WEB:admin';
const state = {
  csrf: null,
  conversations: [],
  models: [],
  status: null,
  settings: {},
  selected: WEB_KEY,
  view: 'overview'
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const esc = (value) => String(value ?? '').replace(/[&<>"']/g, ch => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[ch]));
const fmtTime = (value) => {
  if (!value) return '—';
  const date = new Date(typeof value === 'number' ? value : value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN', {hour12: false});
};

async function csrfToken(force = false) {
  if (state.csrf && !force) return state.csrf;
  const response = await fetch('/api/ui/csrf', {credentials: 'same-origin'});
  if (!response.ok) throw new Error('无法获取 CSRF Token');
  state.csrf = await response.json();
  return state.csrf;
}

async function api(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await csrfToken();
    headers.set(token.headerName || 'X-XSRF-TOKEN', token.token);
  }
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, {...options, method, headers, credentials: 'same-origin'});
  if (response.status === 401 || response.redirected && location.pathname.endsWith('/login.html')) {
    location.href = '/login.html';
    throw new Error('登录已过期');
  }
  if (!response.ok) {
    let message = `请求失败 (${response.status})`;
    try { const body = await response.json(); message = body.error || body.detail || message; } catch (_) {}
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

function toast(message, error = false) {
  const element = $('#toast');
  element.textContent = message;
  element.classList.toggle('error', error);
  element.classList.add('visible');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => element.classList.remove('visible'), 3200);
}

function switchView(view) {
  state.view = view;
  $$('.view').forEach(el => el.classList.toggle('active', el.id === `view-${view}`));
  $$('.nav button').forEach(btn => btn.classList.toggle('active', btn.dataset.view === view));
  const titles = {overview: '运行总览', chat: '网页聊天', sessions: '会话记录', settings: '运行设置', logs: '日志与审计'};
  $('#view-title').textContent = titles[view] || '控制台';
  if (view === 'chat') loadMessages();
  if (view === 'logs') loadLogs();
  if (view === 'settings') renderSettings();
}

async function loadStatus() {
  try {
    state.status = await api('/api/ui/status');
    renderStatus();
    $('#live-dot').classList.add('online');
    $('#live-text').textContent = '实时连接';
  } catch (error) {
    $('#live-dot').classList.remove('online');
    $('#live-text').textContent = '连接异常';
    toast(error.message, true);
  }
}

function renderStatus() {
  const s = state.status;
  if (!s) return;
  const onebot = s.onebot || {};
  const opencode = s.opencode || {};
  const queue = s.queue || {};
  const model = s.defaultModel?.label || '未配置模型';
  const cards = [
    ['QQ / ONEBOT', onebot.connected ? '已连接' : '未连接', `账号 ${onebot.selfId || '—'} · ${onebot.connectionCount || 0} 个连接`],
    ['OPENCODE', opencode.healthy ? '服务在线' : '不可用', `${opencode.version || 'unknown'} · ${model}`],
    ['队列', `${queue.pending || 0} 待处理`, `${queue.running || 0} 运行中 · ${queue.failed || 0} 失败`],
    ['思考强度', variantLabel(s.settings?.defaultVariant || 'none'), '默认关闭思考，优先发送速度']
  ];
  $('#status-cards').innerHTML = cards.map(([label, value, detail]) => `
    <article class="card"><small>${esc(label)}</small><strong>${esc(value)}</strong><div class="detail" title="${esc(detail)}">${esc(detail)}</div></article>
  `).join('');
}

function variantLabel(value) {
  return ({none: '关闭', minimal: '最低', low: '低', medium: '中', high: '高', xhigh: '最高', max: '最高'})[value] || value || '默认';
}

async function loadConversations() {
  try {
    state.conversations = await api('/api/ui/conversations?limit=100');
  } catch (error) {
    state.conversations = [];
    toast(error.message, true);
  }
  renderConversations();
  renderRecentSessions();
  renderSessionsTable();
  syncChatControls();
}

function allConversations() {
  const web = state.conversations.find(item => item.key === WEB_KEY) || {
    key: WEB_KEY, displayName: '网页管理员', chatType: 'WEB', platform: 'web',
    modelId: state.status?.defaultModel?.modelId || null, agentProfile: 'qq-chat', activeJobs: 0
  };
  return [web, ...state.conversations.filter(item => item.key !== WEB_KEY)];
}

function renderConversations() {
  const list = allConversations();
  $('#conversation-list').innerHTML = list.map(item => conversationButton(item, state.selected === item.key)).join('') || '<div class="empty">暂无会话</div>';
  $$('#conversation-list .session-item').forEach(btn => btn.addEventListener('click', () => selectConversation(btn.dataset.key)));
}

function conversationButton(item, active) {
  const name = item.displayName || item.chatId || item.key;
  const type = item.chatType === 'GROUP' ? '群聊' : item.chatType === 'PRIVATE' ? '私聊' : '网页';
  const initial = item.chatType === 'WEB' ? '✿' : item.chatType === 'GROUP' ? '群' : 'Q';
  return `<button class="session-item ${active ? 'active' : ''}" data-key="${esc(item.key)}">
    <span class="avatar">${esc(initial)}</span>
    <span class="session-copy"><strong>${esc(name)}</strong><small>${esc(type)} · ${esc(item.modelId || '默认模型')}</small></span>
    <span class="badge">${item.activeJobs || 0}</span>
  </button>`;
}

function renderRecentSessions() {
  const list = allConversations().slice(0, 5);
  $('#recent-sessions').innerHTML = list.map(item => conversationButton(item, false)).join('');
  $$('#recent-sessions .session-item').forEach(btn => btn.addEventListener('click', () => {
    selectConversation(btn.dataset.key);
    switchView('chat');
  }));
}

function renderSessionsTable() {
  $('#sessions-body').innerHTML = allConversations().map(item => `
    <tr>
      <td title="${esc(item.key)}">${esc(item.displayName || item.chatId || item.key)}</td>
      <td>${esc(item.chatType || 'WEB')}</td>
      <td>${esc(item.modelId || '默认')}</td>
      <td>${esc(item.agentProfile || 'qq-chat')}</td>
      <td>${item.activeJobs || 0}</td>
      <td>${fmtTime(item.updatedAt)}</td>
    </tr>
  `).join('');
}

function selectConversation(key) {
  state.selected = key;
  renderConversations();
  const item = allConversations().find(c => c.key === key);
  $('#chat-title').textContent = item?.displayName || item?.chatId || '网页管理员';
  $('#chat-subtitle').textContent = key === WEB_KEY ? '独立网页会话 · 不影响 QQ 上下文' : '只读查看 · 发送消息仍进入网页会话';
  const isWeb = key === WEB_KEY;
  $('#chat-input').disabled = !isWeb;
  $('#chat-input').placeholder = isWeb ? '写下你的问题… Enter 发送，Shift+Enter 换行' : '当前为只读会话，切换到网页管理员后可发送';
  $('#chat-form .send').disabled = !isWeb;
  syncChatControls();
  loadMessages();
}

function syncChatControls() {
  const item = allConversations().find(c => c.key === state.selected);
  const model = state.models.find(m => m.providerId === item?.modelProvider && m.id === item?.modelId)
    || state.models.find(m => m.id === state.status?.defaultModel?.id);
  const modelSelect = $('#model-select');
  modelSelect.innerHTML = state.models.map(m =>
    `<option value="${esc(m.providerId)}|${esc(m.id)}" ${model && m.id === model.id ? 'selected' : ''}>${esc(m.label)}</option>`
  ).join('') || '<option value="">暂无模型</option>';
  const variantSelect = $('#variant-select');
  const variants = model?.variants || [];
  const currentVariant = item?.modelVariant || state.settings.defaultVariant || '';
  variantSelect.innerHTML = variants.length
    ? variants.map(v => `<option value="${esc(v)}" ${v === currentVariant ? 'selected' : ''}>${esc(variantLabel(v))}</option>`).join('')
    : '<option value="">默认</option>';
  variantSelect.disabled = variants.length === 0;
}

async function loadModels() {
  try {
    state.models = await api('/api/ui/models');
  } catch (_) {
    state.models = state.status?.defaultModel ? [state.status.defaultModel] : [];
  }
  syncChatControls();
}

async function changeModel() {
  const [providerId, modelId] = $('#model-select').value.split('|');
  if (!providerId || !modelId) return;
  const variant = $('#variant-select').value || null;
  try {
    await api(`/api/ui/conversations/${encodeURIComponent(state.selected)}/model`, {
      method: 'POST', body: JSON.stringify({providerId, modelId, variant})
    });
    toast('模型已切换');
    await loadConversations();
  } catch (error) {
    toast(error.message, true);
  }
}

async function changeVariant() {
  if (!$('#variant-select').value) return;
  try {
    await api(`/api/ui/conversations/${encodeURIComponent(state.selected)}/variant`, {
      method: 'POST', body: JSON.stringify({variant: $('#variant-select').value})
    });
    toast('思考强度已切换');
    await loadConversations();
  } catch (error) {
    toast(error.message, true);
  }
}

async function loadMessages() {
  try {
    const messages = await api(`/api/ui/conversations/${encodeURIComponent(state.selected)}/messages?limit=250`);
    renderMessages(messages);
  } catch (error) {
    if (!String(error.message).includes('404')) toast(error.message, true);
    renderMessages([]);
  }
}

function renderMessages(messages) {
  const box = $('#message-list');
  if (!messages || messages.length === 0) {
    box.innerHTML = '<div class="empty">还没有消息。发出第一句问候吧。</div>';
    return;
  }
  box.innerHTML = messages.map(message => {
    const out = message.direction === 'OUT';
    const cls = `${out ? 'out' : 'in'} ${message.status === 'send-failed' || message.status === 'failed' ? 'failed' : ''}`;
    return `<div class="message ${cls}">
      <div class="message-meta">${esc(out ? (message.senderId === 'system' ? '系统' : 'OpenCode') : '用户')} · ${fmtTime(message.createdAt)}</div>
      <div class="bubble">${esc(message.content)}</div>
    </div>`;
  }).join('');
  box.scrollTop = box.scrollHeight;
}

async function sendMessage(text) {
  const value = text.trim();
  if (!value) return;
  await api('/api/ui/chat', {method: 'POST', body: JSON.stringify({text: value})});
  toast('消息已发送');
  await loadMessages();
  await loadConversations();
}

async function newConversation() {
  const exists = state.conversations.some(item => item.key === WEB_KEY);
  state.selected = WEB_KEY;
  if (exists) {
    try {
      await api(`/api/ui/conversations/${encodeURIComponent(WEB_KEY)}/reset`, {method: 'POST'});
      toast('已创建新的网页对话');
    } catch (error) {
      toast(error.message, true);
    }
  } else {
    toast('新的网页会话会在发送第一条消息时创建');
  }
  selectConversation(WEB_KEY);
  await loadConversations();
}

async function stopConversation() {
  try {
    const result = await api(`/api/ui/conversations/${encodeURIComponent(state.selected)}/stop`, {method: 'POST'});
    toast(`已停止，取消 ${result.cancelled || 0} 个任务`);
    await loadMessages();
    await loadStatus();
  } catch (error) {
    toast(error.message, true);
  }
}

async function loadSettings() {
  try {
    state.settings = await api('/api/ui/settings');
    renderSettings();
  } catch (error) {
    toast(error.message, true);
  }
}

function renderSettings() {
  const form = $('#settings-form');
  if (!form || !state.settings) return;
  for (const [key, value] of Object.entries(state.settings)) {
    const field = form.elements.namedItem(key);
    if (field) field.value = value;
  }
}

async function saveSettings(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const values = {};
  for (const element of form.elements) {
    if (element.name) values[element.name] = element.value;
  }
  try {
    state.settings = await api('/api/ui/settings', {method: 'PUT', body: JSON.stringify(values)});
    renderSettings();
    $('#settings-message').textContent = '已保存';
    toast('运行设置已保存');
    await loadStatus();
  } catch (error) {
    $('#settings-message').textContent = error.message;
    toast(error.message, true);
  }
}

async function loadLogs() {
  try {
    const [logs, audit] = await Promise.all([api('/api/ui/logs?limit=150'), api('/api/ui/audit?limit=150')]);
    $('#logs-list').innerHTML = logs.length ? logs.map(logEntry).join('') : '<div class="empty">暂无运行日志</div>';
    $('#recent-logs').innerHTML = logs.slice(0, 6).map(logEntry).join('') || '<div class="empty">暂无日志</div>';
    $('#audit-list').innerHTML = audit.length ? audit.map(row => `
      <div class="log-entry"><time>${fmtTime(row.created_at)}</time><strong>${esc(row.action)}</strong> · ${esc(row.actor_id || '')} ${esc(row.target || '')}</div>
    `).join('') : '<div class="empty">暂无审计记录</div>';
  } catch (error) {
    toast(error.message, true);
  }
}

function logEntry(entry) {
  return `<div class="log-entry priority-${esc(entry.level)}"><time>${fmtTime(entry.time)}</time>${esc(entry.message)}</div>`;
}

let refreshTimer;
function scheduleRefresh() {
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(async () => {
    await Promise.all([loadStatus(), loadConversations()]);
    if (state.view === 'chat') await loadMessages();
    if (state.view === 'logs') await loadLogs();
  }, 220);
}

function connectEvents() {
  const source = new EventSource('/api/ui/events');
  source.onopen = () => {
    $('#live-dot').classList.add('online');
    $('#live-text').textContent = '实时连接';
  };
  source.onerror = () => {
    $('#live-dot').classList.remove('online');
    $('#live-text').textContent = '正在重连';
  };
  for (const name of ['message', 'queue-changed', 'job-changed', 'conversation-changed', 'status-changed', 'settings-changed', 'log']) {
    source.addEventListener(name, (event) => {
      if (name === 'log') {
        try { const entry = JSON.parse(event.data); } catch (_) {}
      }
      scheduleRefresh();
    });
  }
}

async function init() {
  try {
    await csrfToken(true);
    await Promise.all([loadStatus(), loadConversations(), loadSettings(), loadLogs()]);
    await loadModels();
    selectConversation(WEB_KEY);
    connectEvents();
    setInterval(loadStatus, 20_000);
  } catch (error) {
    toast(error.message, true);
  }
}

$$('.nav button').forEach(btn => btn.addEventListener('click', () => switchView(btn.dataset.view)));
$$('[data-go]').forEach(btn => btn.addEventListener('click', () => switchView(btn.dataset.go)));
$('#new-chat').addEventListener('click', newConversation);
$('#stop-chat').addEventListener('click', stopConversation);
$('#model-select').addEventListener('change', changeModel);
$('#variant-select').addEventListener('change', changeVariant);
$('#settings-form').addEventListener('submit', saveSettings);
$('#chat-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const input = $('#chat-input');
  const text = input.value;
  if (!text.trim()) return;
  input.value = '';
  try { await sendMessage(text); } catch (error) { input.value = text; toast(error.message, true); }
});
$('#chat-input').addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    $('#chat-form').requestSubmit();
  }
});
$('#theme-toggle').addEventListener('click', () => {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  localStorage.setItem('qqbot-theme', next);
  $('#theme-toggle').textContent = next === 'dark' ? '切换浅色' : '切换深色';
});
$('#logout').addEventListener('click', async () => {
  try {
    const token = await csrfToken();
    await fetch('/logout', {method: 'POST', credentials: 'same-origin', headers: {[token.headerName]: token.token}});
  } finally {
    location.href = '/login.html?logout';
  }
});

const savedTheme = localStorage.getItem('qqbot-theme');
if (savedTheme) {
  document.documentElement.dataset.theme = savedTheme;
  $('#theme-toggle').textContent = savedTheme === 'dark' ? '切换浅色' : '切换深色';
}
init();
