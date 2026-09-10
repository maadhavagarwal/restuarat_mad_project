-- Bistro Lumière / Restaurant OS SQL Database Schema
-- Compatible with PostgreSQL, Supabase, and SQLite

-- 1. Users Table (Authentication & Roles)
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'CUSTOMER', -- CUSTOMER, CASHIER, KITCHEN_STAFF, MANAGER
    loyalty_points INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table Bookings / Reservations
CREATE TABLE IF NOT EXISTS table_bookings (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    guest_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    table_number VARCHAR(50) DEFAULT 'Auto-Assigned',
    booking_date DATE NOT NULL,
    time_slot VARCHAR(50) NOT NULL, -- e.g., '19:00 - 20:30'
    guest_count INT NOT NULL DEFAULT 2,
    special_requests TEXT,
    status VARCHAR(50) DEFAULT 'CONFIRMED', -- CONFIRMED, SEATED, CANCELLED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Paid Orders & Invoices
CREATE TABLE IF NOT EXISTS paid_orders (
    id SERIAL PRIMARY KEY,
    order_number VARCHAR(100) UNIQUE NOT NULL,
    table_number VARCHAR(50) NOT NULL,
    customer_id INT REFERENCES users(id) ON DELETE SET NULL,
    customer_email VARCHAR(255),
    subtotal DECIMAL(10, 2) NOT NULL,
    tax_amount DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL, -- Cash, Card, UPI
    status VARCHAR(50) DEFAULT 'PAID',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Email Notification Logs
CREATE TABLE IF NOT EXISTS email_logs (
    id SERIAL PRIMARY KEY,
    recipient_email VARCHAR(255) NOT NULL,
    email_type VARCHAR(100) NOT NULL, -- RECEIPT_INVOICE, BOOKING_CONFIRMATION
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'SENT', -- SENT, FAILED, SIMULATED
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
