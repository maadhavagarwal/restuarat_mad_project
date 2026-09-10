import { supabase } from './supabaseClient';

export interface UserRecord {
  id: number;
  name: string;
  email: string;
  phone?: string;
  password_hash: string;
  role: 'CUSTOMER' | 'CASHIER' | 'KITCHEN_STAFF' | 'MANAGER';
  loyalty_points: number;
  created_at: string;
}

export interface BookingRecord {
  id: number;
  user_id?: number;
  guest_name: string;
  email: string;
  phone: string;
  table_number: string;
  booking_date: string;
  time_slot: string;
  guest_count: number;
  special_requests?: string;
  status: 'CONFIRMED' | 'SEATED' | 'CANCELLED';
  created_at: string;
}

// In-memory fallback SQL store for offline / dev mode
const memoryStore = {
  users: [
    {
      id: 1,
      name: 'Rahul Sharma',
      email: 'rahul@example.com',
      phone: '9876543210',
      password_hash: '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', // hashed 'password'
      role: 'CUSTOMER' as const,
      loyalty_points: 35,
      created_at: new Date().toISOString()
    }
  ] as UserRecord[],
  bookings: [] as BookingRecord[]
};

/**
 * Simple password hashing function using Web Crypto API (SHA-256)
 */
export async function hashPassword(password: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(password);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * SQL User Queries
 */
export async function findUserByEmail(email: string): Promise<UserRecord | null> {
  try {
    const { data, error } = await supabase
      .from('users')
      .select('*')
      .eq('email', email.toLowerCase())
      .single();

    if (!error && data) {
      return data as UserRecord;
    }
  } catch (e) {
    // Fallthrough to memory store
  }

  const found = memoryStore.users.find(u => u.email.toLowerCase() === email.toLowerCase());
  return found || null;
}

export async function createUser(userData: {
  name: string;
  email: string;
  phone?: string;
  password_hash: string;
  role?: 'CUSTOMER' | 'CASHIER' | 'KITCHEN_STAFF' | 'MANAGER';
}): Promise<UserRecord> {
  const newUser: UserRecord = {
    id: memoryStore.users.length + 1,
    name: userData.name,
    email: userData.email.toLowerCase(),
    phone: userData.phone || '',
    password_hash: userData.password_hash,
    role: userData.role || 'CUSTOMER',
    loyalty_points: 20, // 20 welcome bonus points
    created_at: new Date().toISOString()
  };

  try {
    const { data, error } = await supabase
      .from('users')
      .insert([newUser])
      .select()
      .single();

    if (!error && data) {
      return data as UserRecord;
    }
  } catch (e) {
    // Fallthrough
  }

  memoryStore.users.push(newUser);
  return newUser;
}

/**
 * SQL Booking Queries
 */
export async function createTableBooking(booking: Omit<BookingRecord, 'id' | 'created_at'>): Promise<BookingRecord> {
  const newBooking: BookingRecord = {
    ...booking,
    id: memoryStore.bookings.length + 101,
    created_at: new Date().toISOString()
  };

  try {
    const { data, error } = await supabase
      .from('table_bookings')
      .insert([newBooking])
      .select()
      .single();

    if (!error && data) {
      return data as BookingRecord;
    }
  } catch (e) {
    // Fallthrough
  }

  memoryStore.bookings.push(newBooking);
  return newBooking;
}

export async function getBookingsByEmail(email: string): Promise<BookingRecord[]> {
  try {
    const { data, error } = await supabase
      .from('table_bookings')
      .select('*')
      .eq('email', email.toLowerCase())
      .order('created_at', { ascending: false });

    if (!error && data && data.length > 0) {
      return data as BookingRecord[];
    }
  } catch (e) {
    // Fallthrough
  }

  return memoryStore.bookings.filter(b => b.email.toLowerCase() === email.toLowerCase());
}
