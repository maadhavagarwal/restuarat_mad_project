// Restaurant OS Frontend Logic connected to Kotlin Spring Boot REST API
const API_BASE = '/api';

let state = {
  currentView: 'pos',
  categories: [],
  menuItems: [],
  tables: [],
  kots: [],
  activeCategory: 'All',
  searchQuery: '',
  cart: [],
  linkedCustomer: null,
  selectedTable: null
};

// Initialize App
document.addEventListener('DOMContentLoaded', () => {
  if (window.lucide) lucide.createIcons();
  fetchInitialData();
  setInterval(refreshKotsAndTables, 3000); // Polling for real-time KDS updates
});

// View Navigation
function switchView(viewName) {
  state.currentView = viewName;
  document.querySelectorAll('.nav-menu button').forEach(btn => btn.classList.remove('active'));
  
  const viewMap = {
    pos: { title: 'Point of Sale', desc: 'Offline-ready Kotlin REST Billing Engine' },
    kds: { title: 'Kitchen Display System (KDS)', desc: 'Real-Time KOT Order Processing Pipeline' },
    tables: { title: 'Table Management', desc: 'Interactive Floor Layout & Real-time Occupancy' },
    customer: { title: 'Customer Digital App', desc: 'Self-Ordering Storefront & Loyalty System' },
    analytics: { title: 'Analytics & Insights', desc: 'Real-time Sales Metrics & Operations Overview' }
  };

  document.getElementById('view-title').textContent = viewMap[viewName].title;
  document.getElementById('view-desc').textContent = viewMap[viewName].desc;

  ['pos', 'kds', 'tables', 'customer', 'analytics'].forEach(v => {
    document.getElementById(`view-${v}`).style.display = (v === viewName) ? (v === 'pos' || v === 'kds' ? 'flex' : 'block') : 'none';
  });

  if (viewName === 'kds') renderKDS();
  if (viewName === 'tables') renderTables();
  if (viewName === 'customer') renderCustomerApp();
  if (viewName === 'analytics') renderAnalytics();
}

// Fetch Initial Data from Kotlin REST Backend
async function fetchInitialData() {
  try {
    const [catRes, menuRes, tableRes, kotRes] = await Promise.all([
      fetch(`${API_BASE}/categories`),
      fetch(`${API_BASE}/menu-items`),
      fetch(`${API_BASE}/tables`),
      fetch(`${API_BASE}/kots`)
    ]);

    state.categories = await catRes.json();
    state.menuItems = await menuRes.json();
    state.tables = await tableRes.json();
    state.kots = await kotRes.json();

    renderCategories();
    renderMenuItems();
    renderCart();
  } catch (err) {
    console.error('Error connecting to Kotlin backend:', err);
  }
}

async function refreshKotsAndTables() {
  try {
    const [kotRes, tableRes] = await Promise.all([
      fetch(`${API_BASE}/kots`),
      fetch(`${API_BASE}/tables`)
    ]);
    state.kots = await kotRes.json();
    state.tables = await tableRes.json();

    if (state.currentView === 'kds') renderKDS();
    if (state.currentView === 'tables') renderTables();
  } catch (err) {
    // Ignore transient network errors
  }
}

// Render Categories
function renderCategories() {
  const container = document.getElementById('categories-bar');
  if (!container) return;

  let html = `<button class="cat-btn ${state.activeCategory === 'All' ? 'active' : ''}" onclick="selectCategory('All')">All Items</button>`;
  
  state.categories.forEach(cat => {
    html += `<button class="cat-btn ${state.activeCategory === cat.id ? 'active' : ''}" onclick="selectCategory(${cat.id})">${cat.name}</button>`;
  });

  container.innerHTML = html;
}

function selectCategory(catId) {
  state.activeCategory = catId;
  renderCategories();
  renderMenuItems();
}

function onSearchMenu(query) {
  state.searchQuery = query.toLowerCase().trim();
  renderMenuItems();
}

// Render Menu Grid
function renderMenuItems() {
  const grid = document.getElementById('menu-grid');
  if (!grid) return;

  let filtered = state.menuItems;

  if (state.activeCategory !== 'All') {
    filtered = filtered.filter(item => item.categoryId === state.activeCategory);
  }

  if (state.searchQuery) {
    filtered = filtered.filter(item => item.name.toLowerCase().includes(state.searchQuery));
  }

  if (filtered.length === 0) {
    grid.innerHTML = `<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 40px;">No menu items found.</div>`;
    return;
  }

  grid.innerHTML = filtered.map(item => {
    const categoryObj = state.categories.find(c => c.id === item.categoryId);
    const catName = categoryObj ? categoryObj.name : 'General';

    return `
      <div class="menu-card" onclick="addToCart(${item.id})">
        <div>
          <h3 style="font-size: 15px; font-weight: 700; color: var(--text-main);">${item.name}</h3>
          <span class="card-tag">${catName}</span>
        </div>
        <div style="display: flex; justify-content: space-between; align-items: flex-end;">
          <div class="card-price">₹${item.price.toFixed(0)}</div>
          <div class="card-add-btn">+</div>
        </div>
      </div>
    `;
  }).join('');
}

// Cart Functions
function addToCart(itemId) {
  const item = state.menuItems.find(m => m.id === itemId);
  if (!item) return;

  const existing = state.cart.find(c => c.id === itemId);
  if (existing) {
    existing.quantity += 1;
  } else {
    state.cart.push({ ...item, quantity: 1 });
  }

  renderCart();
}

function updateCartQty(itemId, delta) {
  const existing = state.cart.find(c => c.id === itemId);
  if (!existing) return;

  existing.quantity += delta;
  if (existing.quantity <= 0) {
    state.cart = state.cart.filter(c => c.id !== itemId);
  }
  renderCart();
}

function renderCart() {
  const container = document.getElementById('cart-items');
  if (!container) return;

  if (state.cart.length === 0) {
    container.innerHTML = `
      <div style="flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); padding: 40px; text-align: center;">
        <i data-lucide="shopping-cart" style="width: 48px; height: 48px; opacity: 0.3; margin-bottom: 12px;"></i>
        <p style="font-size: 14px; font-weight: 600;">No items in cart</p>
        <p style="font-size: 12px; color: #94a3b8; margin-top: 4px;">Click menu items to start building order</p>
      </div>
    `;
    if (window.lucide) lucide.createIcons();
    updateCartTotals();
    return;
  }

  container.innerHTML = state.cart.map(item => `
    <div class="cart-item">
      <div>
        <h4 style="font-size: 13px; font-weight: 700; color: var(--text-main);">${item.name}</h4>
        <p style="font-size: 12px; color: var(--primary); font-weight: 700; margin-top: 2px;">₹${(item.price * item.quantity).toFixed(0)}</p>
      </div>
      <div class="qty-control">
        <button class="qty-btn" onclick="updateCartQty(${item.id}, -1)">-</button>
        <span style="font-size: 13px; font-weight: 700;">${item.quantity}</span>
        <button class="qty-btn" onclick="updateCartQty(${item.id}, 1)">+</button>
      </div>
    </div>
  `).join('');

  updateCartTotals();
}

function updateCartTotals() {
  const subtotal = state.cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
  const tax = subtotal * 0.05;
  const grandTotal = subtotal + tax;

  document.getElementById('cart-subtotal').textContent = `₹${subtotal.toFixed(2)}`;
  document.getElementById('cart-tax').textContent = `₹${tax.toFixed(2)}`;
  document.getElementById('cart-grandtotal').textContent = `₹${grandTotal.toFixed(2)}`;
}

// Checkout Function
async function checkout(paymentMethod) {
  if (state.cart.length === 0) {
    alert('Cart is empty!');
    return;
  }

  const payload = {
    orderType: 'Dine-in',
    tableId: state.selectedTable ? state.selectedTable.id : null,
    customerId: state.linkedCustomer ? state.linkedCustomer.id : null,
    paymentMethod: paymentMethod,
    items: state.cart.map(i => ({ menuItemId: i.id, quantity: i.quantity }))
  };

  try {
    const res = await fetch(`${API_BASE}/orders/checkout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      const order = await res.json();
      alert(`✅ Order Paid successfully via ${paymentMethod}!\nOrder ID: #${order.id}\nSent to KDS.`);
      state.cart = [];
      state.linkedCustomer = null;
      renderCustomerBanner();
      renderCart();
      refreshKotsAndTables();
    } else {
      alert('Checkout failed!');
    }
  } catch (err) {
    console.error('Checkout error:', err);
    alert('Failed to execute order!');
  }
}

// Customer Linking Modal & Actions
function openCustomerModal() {
  document.getElementById('customer-modal').style.display = 'flex';
}

function closeCustomerModal() {
  document.getElementById('customer-modal').style.display = 'none';
}

async function searchCustomer() {
  const phone = document.getElementById('cust-phone-input').value.trim();
  if (!phone) return;

  try {
    const res = await fetch(`${API_BASE}/customers/search?phone=${encodeURIComponent(phone)}`);
    if (res.ok) {
      state.linkedCustomer = await res.json();
      closeCustomerModal();
      renderCustomerBanner();
    } else {
      alert('Customer not found with this phone number.');
    }
  } catch (err) {
    alert('Search error');
  }
}

async function createCustomer() {
  const name = document.getElementById('cust-name-input').value.trim();
  const phone = document.getElementById('cust-phone-input').value.trim();

  if (!name || !phone) {
    alert('Please enter both name and phone.');
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/customers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, phone })
    });
    state.linkedCustomer = await res.json();
    closeCustomerModal();
    renderCustomerBanner();
  } catch (err) {
    alert('Failed to create customer.');
  }
}

function renderCustomerBanner() {
  const banner = document.getElementById('customer-banner');
  if (!banner) return;

  if (state.linkedCustomer) {
    banner.innerHTML = `
      <div style="background: var(--primary-light); padding: 12px; border-radius: var(--radius-md); border: 1px solid #c7d2fe; display: flex; justify-content: space-between; align-items: center;">
        <div>
          <div style="font-size: 13px; font-weight: 700; color: var(--text-main);">${state.linkedCustomer.name}</div>
          <div style="font-size: 11px; color: var(--primary); font-weight: 600;">Points: ${state.linkedCustomer.loyaltyPoints} | ${state.linkedCustomer.phone}</div>
        </div>
        <button onclick="unlinkCustomer()" style="border: none; background: transparent; color: var(--danger); font-weight: bold; cursor: pointer;">✕</button>
      </div>
    `;
  } else {
    banner.innerHTML = `
      <button class="btn-outline" onclick="openCustomerModal()">
        + Link Customer Profile
      </button>
    `;
  }
}

function unlinkCustomer() {
  state.linkedCustomer = null;
  renderCustomerBanner();
}

// Kitchen Display System (KDS)
function renderKDS() {
  const newList = document.getElementById('kds-new-list');
  const prepList = document.getElementById('kds-prep-list');
  const readyList = document.getElementById('kds-ready-list');

  const newOrders = state.kots.filter(k => k.status === 'New');
  const prepOrders = state.kots.filter(k => k.status === 'Preparing');
  const readyOrders = state.kots.filter(k => k.status === 'Ready');

  document.getElementById('count-new-kots').textContent = newOrders.length;
  document.getElementById('count-prep-kots').textContent = prepOrders.length;
  document.getElementById('count-ready-kots').textContent = readyOrders.length;

  newList.innerHTML = newOrders.map(kot => renderKOTCard(kot, 'Start Preparing', '#2563eb', 'Preparing')).join('');
  prepList.innerHTML = prepOrders.map(kot => renderKOTCard(kot, 'Mark as Ready', '#d97706', 'Ready')).join('');
  readyList.innerHTML = readyOrders.map(kot => renderKOTCard(kot, 'Serve Order', '#16a34a', 'Served')).join('');
}

function renderKOTCard(kot, actionText, color, nextStatus) {
  const itemsHtml = kot.items.map(item => `
    <li style="display: flex; justify-content: space-between;">
      <span style="font-weight: 700; color: var(--primary);">${item.quantity}x</span>
      <span style="font-weight: 600; flex: 1; margin-left: 8px;">${item.menuItemName}</span>
    </li>
  `).join('');

  return `
    <div class="kot-card">
      <div class="kot-header">
        <span>Order #${kot.orderId}</span>
        <span style="color: var(--text-muted); font-size: 11px;">KOT #${kot.id}</span>
      </div>
      <ul class="kot-item-list">
        ${itemsHtml}
      </ul>
      <button class="kot-action-btn" style="background: ${color};" onclick="updateKOTStatus(${kot.id}, '${nextStatus}')">
        ${actionText}
      </button>
    </div>
  `;
}

async function updateKOTStatus(kotId, newStatus) {
  try {
    await fetch(`${API_BASE}/kots/${kotId}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: newStatus })
    });
    refreshKotsAndTables();
  } catch (err) {
    console.error('Failed to update KOT status', err);
  }
}

// Table Management View
function renderTables() {
  const grid = document.getElementById('table-grid');
  if (!grid) return;

  grid.innerHTML = state.tables.map(table => `
    <div class="table-card" style="border-color: ${getTableColor(table.status)};" onclick="cycleTableStatus(${table.id}, '${table.status}')">
      <div class="table-number" style="color: ${getTableColor(table.status)};">${table.tableNumber}</div>
      <div style="font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 8px;">Seats: ${table.seatingCapacity}</div>
      <span class="table-status-pill ${table.status}">${table.status}</span>
    </div>
  `).join('');
}

function getTableColor(status) {
  if (status === 'Available') return '#10b981';
  if (status === 'Occupied') return '#ef4444';
  if (status === 'Reserved') return '#f59e0b';
  return '#64748b';
}

async function cycleTableStatus(tableId, currentStatus) {
  let nextStatus = 'Available';
  if (currentStatus === 'Available') nextStatus = 'Occupied';
  else if (currentStatus === 'Occupied') nextStatus = 'Reserved';

  try {
    await fetch(`${API_BASE}/tables/${tableId}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: nextStatus })
    });
    refreshKotsAndTables();
  } catch (err) {
    console.error('Failed to update table status');
  }
}

// Customer Mobile App Simulator View
function renderCustomerApp() {
  const container = document.getElementById('customer-store-content');
  if (!container) return;

  container.innerHTML = `
    <h3 style="font-size: 16px; font-weight: 800; margin-bottom: 12px; color: var(--text-main);">Popular Dishes</h3>
    <div style="display: flex; flex-direction: column; gap: 12px;">
      ${state.menuItems.slice(0, 5).map(item => `
        <div style="display: flex; justify-content: space-between; align-items: center; padding: 12px; background: #f8fafc; border-radius: 14px; border: 1px solid var(--border-color);">
          <div>
            <h4 style="font-size: 14px; font-weight: 700;">${item.name}</h4>
            <p style="font-size: 13px; font-weight: 800; color: var(--primary);">₹${item.price.toFixed(0)}</p>
          </div>
          <button onclick="addToCart(${item.id})" style="background: var(--primary); color: white; border: none; padding: 6px 14px; border-radius: 8px; font-weight: 700; cursor: pointer;">
            + Add
          </button>
        </div>
      `).join('')}
    </div>
  `;
}

// Analytics Dashboard View
async function renderAnalytics() {
  const grid = document.getElementById('analytics-grid');
  if (!grid) return;

  try {
    const res = await fetch(`${API_BASE}/analytics`);
    const data = await res.json();

    grid.innerHTML = `
      <div class="stat-card">
        <div style="font-size: 13px; font-weight: 700; color: var(--text-muted);">Total Revenue</div>
        <div class="stat-val" style="color: #10b981;">₹${data.totalRevenue.toFixed(2)}</div>
      </div>
      <div class="stat-card">
        <div style="font-size: 13px; font-weight: 700; color: var(--text-muted);">Total Orders</div>
        <div class="stat-val">${data.totalOrders}</div>
      </div>
      <div class="stat-card">
        <div style="font-size: 13px; font-weight: 700; color: var(--text-muted);">Active KOT Queue</div>
        <div class="stat-val" style="color: #f59e0b;">${data.activeKOTs}</div>
      </div>
      <div class="stat-card">
        <div style="font-size: 13px; font-weight: 700; color: var(--text-muted);">Available Tables</div>
        <div class="stat-val" style="color: var(--primary);">${data.availableTables} / ${data.availableTables + data.occupiedTables}</div>
      </div>
    `;
  } catch (err) {
    grid.innerHTML = 'Failed to load analytics data';
  }
}
