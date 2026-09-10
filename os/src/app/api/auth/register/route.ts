import { NextResponse } from 'next/server';
import { findUserByEmail, createUser, hashPassword } from '@/lib/dbClient';

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const { name, email, password, phone, role } = body;

    if (!name || !email || !password) {
      return NextResponse.json(
        { error: 'Name, email, and password are required.' },
        { status: 400 }
      );
    }

    // Check if user already exists
    const existing = await findUserByEmail(email);
    if (existing) {
      return NextResponse.json(
        { error: 'An account with this email address already exists.' },
        { status: 409 }
      );
    }

    // Hash password & save user in SQL DB
    const password_hash = await hashPassword(password);
    const user = await createUser({
      name,
      email,
      phone,
      password_hash,
      role: role || 'CUSTOMER'
    });

    // Create session token (Mock JWT token string)
    const token = `token_${user.id}_${Date.now()}`;

    return NextResponse.json({
      message: 'Account registered successfully.',
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        phone: user.phone,
        role: user.role,
        loyalty_points: user.loyalty_points
      }
    });
  } catch (error: any) {
    console.error('Registration failed:', error);
    return NextResponse.json(
      { error: 'Server error during registration.' },
      { status: 500 }
    );
  }
}
