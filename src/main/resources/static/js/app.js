/* ═══════════════════════════════════════════════════════════════
   NotifySystem — Frontend Application
   Architecture:
     State    → single source of truth object
     API      → fetch wrapper with JWT
     WS       → StompJS client with auto-reconnect
     UI       → pure DOM manipulation, no framework
   ═══════════════════════════════════════════════════════════════ */

'use strict';

// ─── Application State ──────────────────────────────────────────
const State = {
  token:       null,
  username:    null,
  email:       null,
  role:        null,
  currentPage: 0,
  totalPages:  0,
  totalItems:  0,
  unreadCount: 0,
  notifications: [],
  currentSection: 'all',
  panelOpen:   false,
  recentNotifs: [],   // for the slide panel (last 15)
  stompClient: null,
};

const PAGE_SIZE = 20;
const API_BASE  = '/api';

// ─── Type → Icon mapping ────────────────────────────────────────
const TYPE_ICONS = {
  INFO:    'ℹ️',
  SUCCESS: '✅',
  WARNING: '⚠️',
  ALERT:   '🔔',
  ERROR:   '❌',
};

const TOAST_DURATION = {
  LOW:      4000,
  NORMAL:   5000,
  HIGH:     7000,
  CRITICAL: 0,     // persistent — user must dismiss
};

// ═══════════════════════════════════════════════════════════════
//  API HELPERS
// ═══════════════════════════════════════════════════════════════

async function apiFetch(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(State.token ? { Authorization: `Bearer ${State.token}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || `Request failed: ${response.status}`);
  }
  return data;
}

const api = {
  login:        (body)   => apiFetch('/auth/login',                    { method: 'POST', body }),
  register:     (body)   => apiFetch('/auth/register',                 { method: 'POST', body }),
  getNotifs:    (page)   => apiFetch(`/notifications?page=${page}&size=${PAGE_SIZE}`),
  getUnread:    ()       => apiFetch('/notifications/unread-count'),
  markRead:     (id)     => apiFetch(`/notifications/${id}/read`,      { method: 'PATCH' }),
  markUnread:   (id)     => apiFetch(`/notifications/${id}/unread`,    { method: 'PATCH' }),
  markAllRead:  ()       => apiFetch('/notifications/mark-all-read',   { method: 'POST' }),
  deleteNotif:  (id)     => apiFetch(`/notifications/${id}`,           { method: 'DELETE' }),
  sendNotif:    (body)   => apiFetch('/notifications/send',            { method: 'POST', body }),
};

// ═══════════════════════════════════════════════════════════════
//  AUTH
// ═══════════════════════════════════════════════════════════════

function switchTab(tab) {
  const isLogin = tab === 'login';
  document.getElementById('tab-login').classList.toggle('active', isLogin);
  document.getElementById('tab-register').classList.toggle('active', !isLogin);
  document.getElementById('login-form').classList.toggle('hidden', !isLogin);
  document.getElementById('register-form').classList.toggle('hidden', isLogin);
  clearErrors();
}

async function handleLogin(e) {
  e.preventDefault();
  const username = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;
  setLoading('login-btn', true);
  clearErrors();

  try {
    const res = await api.login({ username, password });
    onAuthSuccess(res.data);
  } catch (err) {
    showFormError('login-error', err.message);
  } finally {
    setLoading('login-btn', false);
  }
}

async function handleRegister(e) {
  e.preventDefault();
  const username = document.getElementById('reg-username').value.trim();
  const email    = document.getElementById('reg-email').value.trim();
  const password = document.getElementById('reg-password').value;
  setLoading('reg-btn', true);
  clearErrors();

  try {
    const res = await api.register({ username, email, password });
    onAuthSuccess(res.data);
  } catch (err) {
    showFormError('reg-error', err.message);
  } finally {
    setLoading('reg-btn', false);
  }
}

function onAuthSuccess(data) {
  State.token    = data.token;
  State.username = data.username;
  State.email    = data.email;
  State.role     = data.role;

  // Persist session
  sessionStorage.setItem('ns_token',    data.token);
  sessionStorage.setItem('ns_username', data.username);
  sessionStorage.setItem('ns_email',    data.email);
  sessionStorage.setItem('ns_role',     data.role);

  showDashboard();
}

function handleLogout() {
  disconnectWS();
  sessionStorage.clear();
  State.token = State.username = State.email = State.role = null;
  State.notifications = [];
  State.recentNotifs = [];
  document.getElementById('notif-list').innerHTML = '';
  document.getElementById('panel-list').innerHTML = '';
  hideDashboard();
}

function restoreSession() {
  const token    = sessionStorage.getItem('ns_token');
  const username = sessionStorage.getItem('ns_username');
  const email    = sessionStorage.getItem('ns_email');
  const role     = sessionStorage.getItem('ns_role');

  if (token && username) {
    State.token    = token;
    State.username = username;
    State.email    = email;
    State.role     = role;
    showDashboard();
    return true;
  }
  return false;
}

// ═══════════════════════════════════════════════════════════════
//  WEBSOCKET (StompJS v7)
// ═══════════════════════════════════════════════════════════════

function connectWS() {
  setWsStatus('connecting');

  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: {
      Authorization: `Bearer ${State.token}`,
    },
    reconnectDelay:    5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    onConnect: () => {
      setWsStatus('connected');
      console.log('[WS] Connected as:', State.username);

      // Subscribe to user-specific notification queue
      client.subscribe(
        '/user/queue/notifications',
        (frame) => {
          const notif = JSON.parse(frame.body);
          onRealtimeNotification(notif);
        }
      );

      // Subscribe to broadcast topic
      client.subscribe('/topic/broadcast', (frame) => {
        const notif = JSON.parse(frame.body);
        // Only show toast if not targeted at self (avoids duplicate)
        if (notif.username !== State.username) {
          onRealtimeNotification(notif);
        }
      });
    },

    onDisconnect: () => {
      setWsStatus('disconnected');
      console.log('[WS] Disconnected');
    },

    onStompError: (frame) => {
      console.error('[WS] STOMP error:', frame.headers['message']);
      setWsStatus('disconnected');
    },

    onWebSocketClose: () => {
      setWsStatus('connecting');
    },
  });

  client.activate();
  State.stompClient = client;
}

function disconnectWS() {
  if (State.stompClient) {
    State.stompClient.deactivate();
    State.stompClient = null;
  }
  setWsStatus('disconnected');
}

/**
 * Called when a real-time notification arrives via WebSocket.
 * Does NOT trigger a full page reload — just injects into UI.
 */
function onRealtimeNotification(notif) {
  console.log('[WS] Notification received:', notif.title);

  // Update unread count
  State.unreadCount++;
  updateBadge();

  State.notifications.unshift(notif);
  if (State.notifications.length > PAGE_SIZE) {
    State.notifications = State.notifications.slice(0, PAGE_SIZE);
  }

  // Show toast
  showToast(notif);

  // Add to top of list (if in All/Unread section)
  prependNotifToList(notif);

  // Add to panel
  addToPanelFeed(notif);

  // Update stats
  updateStats();
}

// ═══════════════════════════════════════════════════════════════
//  DASHBOARD INIT
// ═══════════════════════════════════════════════════════════════

function showDashboard() {
  document.getElementById('auth-screen').classList.add('hidden');
  document.getElementById('dashboard').classList.remove('hidden');

  // Populate header
  document.getElementById('header-username').textContent = State.username;
  const roleLabel = State.role === 'ROLE_ADMIN' ? 'Admin' : 'User';
  document.getElementById('header-role').textContent = roleLabel;

  // Show admin nav if admin
  if (State.role === 'ROLE_ADMIN') {
    document.querySelectorAll('.admin-only').forEach(el => el.classList.remove('hidden'));
  }

  // Connect WebSocket
  connectWS();

  // Load initial data
  loadNotifications(0);
  refreshUnreadCount();
}

function hideDashboard() {
  document.getElementById('dashboard').classList.add('hidden');
  document.getElementById('auth-screen').classList.remove('hidden');
}

// ═══════════════════════════════════════════════════════════════
//  NOTIFICATIONS — Load & Render
// ═══════════════════════════════════════════════════════════════

async function loadNotifications(page = 0) {
  showListState('loading');

  try {
    const res = await api.getNotifs(page);
    const { content, totalPages, totalElements, number } = res.data;

    State.currentPage = number;
    State.totalPages  = totalPages;
    State.totalItems  = totalElements;
    State.notifications = content;

    renderNotifList(content);
    renderPagination(number, totalPages);
    updateStats();

    if (State.currentSection === 'unread') {
      renderUnreadSection();
    }
  } catch (err) {
    showListState('error', err.message);
  }
}

function renderNotifList(items) {
  const container = document.getElementById('notif-list');

  if (!items || items.length === 0) {
    container.innerHTML = '';
    showListState('empty');
    return;
  }

  container.innerHTML = items.map(n => buildNotifCard(n)).join('');
  document.getElementById('list-loading').classList.add('hidden');
  document.getElementById('list-empty').classList.add('hidden');
}

function buildNotifCard(n) {
  const icon     = TYPE_ICONS[n.type] || '🔔';
  const timeAgo  = formatRelativeTime(n.createdAt);
  const unreadCls = n.read ? '' : 'unread';
  const readBtn  = n.read
    ? `<button class="action-btn" onclick="toggleRead(${n.id}, false)" title="Mark unread">◎</button>`
    : `<button class="action-btn" onclick="toggleRead(${n.id}, true)"  title="Mark read">✓</button>`;

  return `
    <div class="notif-item ${unreadCls}" id="notif-${n.id}">
      <div class="notif-icon">${icon}</div>
      <div class="notif-body">
        <div class="notif-header">
          <div class="notif-title">${escapeHtml(n.title)}</div>
          <div class="notif-time">${timeAgo}</div>
        </div>
        <div class="notif-msg">${escapeHtml(n.message)}</div>
        <div class="notif-meta">
          <span class="type-badge ${n.type}">${n.type}</span>
          <span class="priority-badge ${n.priority}">${n.priority}</span>
        </div>
      </div>
      <div class="notif-actions">
        ${readBtn}
        <button class="action-btn danger" onclick="deleteNotif(${n.id})" title="Delete">🗑</button>
      </div>
    </div>`;
}

/**
 * Prepend a newly received real-time notification to the list (no reload).
 */
function prependNotifToList(notif) {
  const container = document.getElementById('notif-list');
  const loading   = document.getElementById('list-loading');
  const empty     = document.getElementById('list-empty');

  loading.classList.add('hidden');
  empty.classList.add('hidden');

  const div = document.createElement('div');
  div.innerHTML = buildNotifCard(notif);
  container.insertBefore(div.firstElementChild, container.firstChild);

  // Update total count
  State.totalItems++;
  document.getElementById('nav-total').textContent = State.totalItems;

  if (State.currentSection === 'unread') {
    renderUnreadSection();
  }
}

// ═══════════════════════════════════════════════════════════════
//  NOTIFICATION ACTIONS
// ═══════════════════════════════════════════════════════════════

async function toggleRead(id, markAsRead) {
  try {
    const res = markAsRead ? await api.markRead(id) : await api.markUnread(id);
    const notif = res.data;

    State.notifications = State.notifications.map((item) =>
      item.id === id ? notif : item
    );

    // Update card in-place
    const card = document.getElementById(`notif-${id}`);
    if (card) {
      card.outerHTML = buildNotifCard(notif);
    }

    // Update count
    State.unreadCount += markAsRead ? -1 : 1;
    State.unreadCount = Math.max(0, State.unreadCount);
    updateBadge();
    updateStats();

    if (State.currentSection === 'unread') {
      renderUnreadSection();
    }
  } catch (err) {
    showToastRaw('Error', err.message, 'ERROR', 'NORMAL');
  }
}

async function deleteNotif(id) {
  try {
    await api.deleteNotif(id);
    const deleted = State.notifications.find((item) => item.id === id);
    State.notifications = State.notifications.filter((item) => item.id !== id);
    const card = document.getElementById(`notif-${id}`);
    if (card) {
      card.style.animation = 'toast-out 0.2s ease forwards';
      setTimeout(() => card.remove(), 200);
    }
    State.totalItems = Math.max(0, State.totalItems - 1);
    if (deleted && !deleted.read) {
      State.unreadCount = Math.max(0, State.unreadCount - 1);
      updateBadge();
    }
    updateStats();

    if (State.currentSection === 'unread') {
      renderUnreadSection();
    }
  } catch (err) {
    showToastRaw('Error', err.message, 'ERROR', 'NORMAL');
  }
}

async function markAllRead() {
  try {
    await api.markAllRead();
    State.unreadCount = 0;
    updateBadge();
    updateStats();
    // Re-render to clear unread styling
    loadNotifications(State.currentPage);
    showToastRaw('Done', 'All notifications marked as read.', 'SUCCESS', 'LOW');
  } catch (err) {
    showToastRaw('Error', err.message, 'ERROR', 'NORMAL');
  }
}

async function refreshNotifications() {
  await loadNotifications(State.currentPage);
  await refreshUnreadCount();
}

async function refreshUnreadCount() {
  try {
    const res = await api.getUnread();
    State.unreadCount = res.data.count;
    updateBadge();
    updateStats();
  } catch (_) {}
}

// ═══════════════════════════════════════════════════════════════
//  SEND NOTIFICATION (Admin)
// ═══════════════════════════════════════════════════════════════

async function handleSendNotification(e) {
  e.preventDefault();
  const title    = document.getElementById('send-title').value.trim();
  const message  = document.getElementById('send-message').value.trim();
  const type     = document.getElementById('send-type').value;
  const priority = document.getElementById('send-priority').value;
  const target   = document.getElementById('send-target').value.trim();

  const btn      = document.getElementById('send-btn');
  const errEl    = document.getElementById('send-error');
  const successEl= document.getElementById('send-success');

  errEl.classList.add('hidden');
  successEl.classList.add('hidden');
  btn.disabled = true;
  btn.querySelector('.btn-text').textContent = '⏳ Sending…';

  try {
    await api.sendNotif({
      title,
      message,
      type,
      priority,
      targetUsername: target || null,
    });

    const to = target ? `to @${target}` : 'to all users';
    successEl.textContent = `✓ Notification sent ${to} successfully!`;
    successEl.classList.remove('hidden');

    // Reset form
    e.target.reset();
    document.getElementById('send-priority').value = 'NORMAL';
  } catch (err) {
    errEl.textContent = err.message;
    errEl.classList.remove('hidden');
  } finally {
    btn.disabled = false;
    btn.querySelector('.btn-text').textContent = '📤 Send Notification';
  }
}

// ═══════════════════════════════════════════════════════════════
//  NAVIGATION
// ═══════════════════════════════════════════════════════════════

function showSection(section) {
  State.currentSection = section;

  // Update nav active states
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  const sectionNavMap = { all: 0, unread: 1, send: 2 };
  const navItems = document.querySelectorAll('.nav-item');
  if (navItems[sectionNavMap[section]]) {
    navItems[sectionNavMap[section]].classList.add('active');
  }

  // Show/hide sections
  document.querySelectorAll('.section').forEach(el => el.classList.add('hidden'));
  document.getElementById(`section-${section}`).classList.remove('hidden');

  // Update toolbar title
  const titles = { all: 'All Notifications', unread: 'Unread', send: 'Send Notification' };
  document.getElementById('section-title').textContent = titles[section] || '';

  // Toggle actions visibility
  const showActions = section !== 'send';
  document.querySelector('.toolbar-actions').style.display = showActions ? 'flex' : 'none';

  if (section === 'unread') renderUnreadSection();
}

function renderUnreadSection() {
  const container = document.getElementById('unread-list');
  const emptyEl     = document.getElementById('unread-empty');
  const unreadItems = State.notifications.filter((item) => !item.read);

  if (unreadItems.length === 0) {
    container.innerHTML = '';
    emptyEl.classList.remove('hidden');
    return;
  }
  emptyEl.classList.add('hidden');
  container.innerHTML = unreadItems.map((item) => buildNotifCard(item)).join('');
}

function changePage(delta) {
  const newPage = State.currentPage + delta;
  if (newPage >= 0 && newPage < State.totalPages) {
    loadNotifications(newPage);
  }
}

// ═══════════════════════════════════════════════════════════════
//  NOTIFICATION PANEL
// ═══════════════════════════════════════════════════════════════

function toggleNotifPanel() {
  State.panelOpen = !State.panelOpen;
  document.getElementById('notif-panel').classList.toggle('hidden', !State.panelOpen);
  document.getElementById('panel-overlay').classList.toggle('hidden', !State.panelOpen);
}

function addToPanelFeed(notif) {
  State.recentNotifs.unshift(notif);
  if (State.recentNotifs.length > 15) State.recentNotifs.pop();
  renderPanelList();
}

function renderPanelList() {
  const container = document.getElementById('panel-list');
  if (State.recentNotifs.length === 0) {
    container.innerHTML = '<div class="empty-state" style="padding:40px 20px"><div class="empty-icon">🔕</div><div>No recent activity</div></div>';
    return;
  }
  container.innerHTML = State.recentNotifs.map(n => `
    <div class="panel-item ${n.type}">
      <div class="panel-item-title">${TYPE_ICONS[n.type]} ${escapeHtml(n.title)}</div>
      <div class="panel-item-msg">${escapeHtml(n.message)}</div>
      <div class="panel-item-time">${formatRelativeTime(n.createdAt)}</div>
    </div>
  `).join('');
}

// ═══════════════════════════════════════════════════════════════
//  TOAST SYSTEM
// ═══════════════════════════════════════════════════════════════

function showToast(notif) {
  showToastRaw(notif.title, notif.message, notif.type, notif.priority);
}

function showToastRaw(title, message, type = 'INFO', priority = 'NORMAL') {
  const duration = TOAST_DURATION[priority] ?? 5000;
  const icon     = TYPE_ICONS[type] || '🔔';
  const container= document.getElementById('toast-container');

  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `
    ${duration > 0 ? `<div class="toast-progress" style="animation-duration:${duration}ms"></div>` : ''}
    <div class="toast-icon">${icon}</div>
    <div class="toast-body">
      <div class="toast-title">${escapeHtml(title)}</div>
      <div class="toast-msg">${escapeHtml(message)}</div>
    </div>
    <button class="toast-close" onclick="dismissToast(this.parentElement)">✕</button>
  `;

  container.appendChild(el);

  if (duration > 0) {
    setTimeout(() => dismissToast(el), duration);
  }
}

function dismissToast(el) {
  if (!el || !el.parentElement) return;
  el.classList.add('removing');
  setTimeout(() => el.remove(), 250);
}

// ═══════════════════════════════════════════════════════════════
//  UI HELPERS
// ═══════════════════════════════════════════════════════════════

function updateBadge() {
  const badge = document.getElementById('bell-badge');
  const count = State.unreadCount;
  badge.textContent = count > 99 ? '99+' : count;
  badge.classList.toggle('hidden', count === 0);
}

function updateStats() {
  document.getElementById('nav-total').textContent  = State.totalItems;
  document.getElementById('nav-unread').textContent = State.unreadCount;
  document.getElementById('stat-total').textContent = State.totalItems;
  document.getElementById('stat-unread').textContent= State.unreadCount;
}

function renderPagination(page, totalPages) {
  const info    = document.getElementById('page-info');
  const prevBtn = document.getElementById('prev-btn');
  const nextBtn = document.getElementById('next-btn');
  const pgEl    = document.getElementById('pagination');

  info.textContent   = `Page ${page + 1} of ${Math.max(1, totalPages)}`;
  prevBtn.disabled   = page === 0;
  nextBtn.disabled   = page >= totalPages - 1;
  pgEl.style.display = totalPages <= 1 ? 'none' : 'flex';
}

function setWsStatus(status) {
  const wsEl   = document.getElementById('ws-status');
  const textEl = document.getElementById('ws-status-text');
  wsEl.className = `ws-status ${status}`;
  const labels = { connected: 'Live', disconnected: 'Offline', connecting: 'Connecting…' };
  textEl.textContent = labels[status] || status;
}

function showListState(state, msg) {
  const loading = document.getElementById('list-loading');
  const empty   = document.getElementById('list-empty');

  loading.classList.toggle('hidden', state !== 'loading');
  empty.classList.toggle('hidden',   state !== 'empty');

  if (state === 'error') {
    const container = document.getElementById('notif-list');
    container.innerHTML = `<div class="empty-state"><div class="empty-icon">⚠️</div><div>${escapeHtml(msg)}</div></div>`;
  }
}

function showFormError(id, msg) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.classList.remove('hidden');
}

function clearErrors() {
  ['login-error', 'reg-error'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.classList.add('hidden');
  });
}

function setLoading(btnId, loading) {
  const btn = document.getElementById(btnId);
  if (!btn) return;
  btn.disabled = loading;
  const textEl    = btn.querySelector('.btn-text');
  const spinnerEl = btn.querySelector('.btn-spinner');
  if (textEl)    textEl.classList.toggle('hidden', loading);
  if (spinnerEl) spinnerEl.classList.toggle('hidden', !loading);
}

// ─── Time formatting ────────────────────────────────────────────
function formatRelativeTime(isoString) {
  if (!isoString) return '';
  const date  = new Date(isoString);
  const now   = new Date();
  const diffMs = now - date;
  const diffS  = Math.floor(diffMs / 1000);
  const diffM  = Math.floor(diffS / 60);
  const diffH  = Math.floor(diffM / 60);
  const diffD  = Math.floor(diffH / 24);

  if (diffS < 10)  return 'just now';
  if (diffS < 60)  return `${diffS}s ago`;
  if (diffM < 60)  return `${diffM}m ago`;
  if (diffH < 24)  return `${diffH}h ago`;
  if (diffD < 7)   return `${diffD}d ago`;

  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

// ─── XSS protection ────────────────────────────────────────────
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// ═══════════════════════════════════════════════════════════════
//  BOOTSTRAP
// ═══════════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
  // Restore session from sessionStorage (page refresh survival)
  if (!restoreSession()) {
    // Show auth screen — already visible by default
  }

  // Keyboard shortcut: Escape closes panel
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && State.panelOpen) toggleNotifPanel();
  });

  // Auto-refresh unread count every 30 seconds as a fallback
  setInterval(() => {
    if (State.token) refreshUnreadCount();
  }, 30_000);
});
