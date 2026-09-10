import { NextResponse } from 'next/server';
import { findUserByEmail, hashPassword } from '@/lib/dbClient';

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const { email, password } = body;

    if (!email || !password) {
      return NextResponse.json(
        { error: 'Email and password are required.' },
        { status: 400 }
      );
    }

    const user = await findUserByEmail(email);
    if (!user) {
      return NextResponse.json(
        { error: 'Invalid email address or password.' },
        { status: 401 }
      );
    }

    const inputHash = await hashPassword(password);
    if (user.password_hash !== inputHash) {
      return NextResponse.json(
        { error: 'Invalid email address or password.' },
        { status: 401 }
      );
    }

    const token = `token_${user.id}_${Date.now()}`;

    return NextResponse.json({
      message: 'Login successful.',
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
    console.error('Login failed:', error);
    return NextResponse.json(
      { error: 'Server error during authentication.' },
      { status: 500 }
    );
  }
}
