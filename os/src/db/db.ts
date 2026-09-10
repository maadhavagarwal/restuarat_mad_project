import Dexie, { type EntityTable } from 'dexie';

export interface Customer {
  id?: number;
  phone: string;
  name: string;
  loyalty_points: number;
  qr_code: string;
  sync_status: 'synced' | 'pending';
  updated_at: string;
}

export interface Category {
  id?: number;
  name: string;
  sync_status: 'synced' | 'pending';
  updated_at: string;
}

export interface MenuItem {
  id?: number;
  category_id: number;
  name: string;
  price: number;
  tax_rate: number;
  is_available: boolean;
  sync_status: 'synced' | 'pending';
  updated_at: string;
}

export interface DiningTable {
  id?: number;
  table_number: string;
  seating_capacity: number;
  status: 'Available' | 'Occupied' | 'Reserved';
  sync_status: 'synced' | 'pending';
  updated_at: string;
}

export interface Order {
  id?: number;
  table_id?: number | null;
  customer_id?: number | null;
  order_type: 'Dine-in' | 'Takeaway' | 'Delivery';
  total_amount: number;
  status: 'Active' | 'Paid' | 'Cancelled';
  payment_method?: 'UPI' | 'Card' | 'Cash';
  created_at: string;
  sync_status: 'synced' | 'pending';
  updated_at: string;
}

export interface OrderItem {
  id?: number;
  order_id: number;
  menu_item_id: number;
  quantity: number;
  price_at_time_of_order: number;
  sync_status: 'synced' | 'pending';
  updated_at: string;
}

export interface KOT {
  id?: number;
  order_id: number;
  status: 'New' | 'Preparing' | 'Ready' | 'Served';
  printed_status: boolean;
  sync_status: 'synced' | 'pending';
  updated_at: string;
}

const db = new Dexie('RestaurantOSDatabase') as Dexie & {
  customers: EntityTable<Customer, 'id'>;
  categories: EntityTable<Category, 'id'>;
  menuItems: EntityTable<MenuItem, 'id'>;
  diningTables: EntityTable<DiningTable, 'id'>;
  orders: EntityTable<Order, 'id'>;
  orderItems: EntityTable<OrderItem, 'id'>;
  kots: EntityTable<KOT, 'id'>;
};

// Schema declaration
db.version(2).stores({
  customers: '++id, phone, qr_code, sync_status',
  categories: '++id, name, sync_status',
  menuItems: '++id, category_id, name, is_available, sync_status',
  diningTables: '++id, table_number, status, sync_status',
  orders: '++id, table_id, customer_id, order_type, status, created_at, sync_status',
  orderItems: '++id, order_id, menu_item_id, sync_status',
  kots: '++id, order_id, status, sync_status'
});

export { db };
export default db;
