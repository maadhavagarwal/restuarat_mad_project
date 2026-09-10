"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { useLiveQuery } from 'dexie-react-hooks';
import { QRCodeSVG } from 'qrcode.react';
import { ArrowLeft, Star, Clock, Award, QrCode, Calendar, Lock, Mail, User, ShieldCheck } from 'lucide-react';
import db from '@/db/db';
import { useAuth } from '@/components/AuthContext';

export default function CustomerProfile() {
  const { user, login, register, logout } = useAuth();

  const [isRegisterTab, setIsRegisterTab] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [authError, setAuthError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const pastOrders = useLiveQuery(
    async () => {
      const orders = await db.orders.toArray();
      return orders.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
    },
    []
  );

  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    setIsSubmitting(true);

    if (isRegisterTab) {
      if (!name || !email || !password) {
        setAuthError('Name, email, and password are required.');
        setIsSubmitting(false);
        return;
      }
      const res = await register(name, email, password, phone);
      if (!res.success) {
        setAuthError(res.error || 'Registration failed.');
      }
    } else {
      if (!email || !password) {
        setAuthError('Email and password are required.');
        setIsSubmitting(false);
        return;
      }
      const res = await login(email, password);
      if (!res.success) {
        setAuthError(res.error || 'Invalid credentials.');
      }
    }
    setIsSubmitting(false);
  };

  if (!user) {
    return (
      <div className="min-h-screen bg-[#FAF8F5] flex items-center justify-center p-6 text-stone-800 select-none">
        <div className="bg-white p-8 rounded-3xl shadow-xl border border-[#E7E2D9] w-full max-w-md space-y-6">
          <div className="text-center space-y-2">
            <div className="w-12 h-12 bg-gradient-to-br from-amber-700 to-orange-600 rounded-2xl mx-auto flex items-center justify-center text-white font-serif font-bold text-xl shadow-md">
              BL
            </div>
            <h2 className="text-2xl font-serif font-bold text-stone-900 tracking-tight">Bistro Lumière Privé</h2>
            <p className="text-xs text-stone-500 font-medium">SQL Authenticated Gourmet Pass</p>
          </div>

          {/* Sign In vs Register Tabs */}
          <div className="flex bg-[#FAF8F5] p-1 rounded-xl border border-[#E7E2D9]">
            <button
              onClick={() => { setIsRegisterTab(false); setAuthError(''); }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
                !isRegisterTab ? 'bg-white text-stone-900 shadow-xs' : 'text-stone-500 hover:text-stone-800'
              }`}
            >
              Sign In
            </button>
            <button
              onClick={() => { setIsRegisterTab(true); setAuthError(''); }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
                isRegisterTab ? 'bg-white text-stone-900 shadow-xs' : 'text-stone-500 hover:text-stone-800'
              }`}
            >
              Create Account
            </button>
          </div>

          {authError && (
            <div className="bg-red-50 text-red-700 text-xs font-medium px-3.5 py-2.5 rounded-xl border border-red-200">
              ⚠️ {authError}
            </div>
          )}

          <form onSubmit={handleAuthSubmit} className="space-y-4">
            {isRegisterTab && (
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1 uppercase tracking-wide">Full Name</label>
                <input 
                  type="text" 
                  required 
                  value={name} 
                  onChange={(e) => setName(e.target.value)}
                  className="w-full border border-stone-200 rounded-xl px-4 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none" 
                  placeholder="e.g. Rahul Sharma" 
                />
              </div>
            )}

            <div>
              <label className="block text-xs font-semibold text-stone-700 mb-1 uppercase tracking-wide">Email Address</label>
              <input 
                type="email" 
                required 
                value={email} 
                onChange={(e) => setEmail(e.target.value)}
                className="w-full border border-stone-200 rounded-xl px-4 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none" 
                placeholder="e.g. rahul@example.com" 
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-stone-700 mb-1 uppercase tracking-wide">Password</label>
              <input 
                type="password" 
                required 
                value={password} 
                onChange={(e) => setPassword(e.target.value)}
                className="w-full border border-stone-200 rounded-xl px-4 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none" 
                placeholder="••••••••" 
              />
            </div>

            {isRegisterTab && (
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1 uppercase tracking-wide">Phone Number (Optional)</label>
                <input 
                  type="tel" 
                  value={phone} 
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full border border-stone-200 rounded-xl px-4 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none" 
                  placeholder="e.g. 9876543210" 
                />
              </div>
            )}

            <button 
              type="submit" 
              disabled={isSubmitting}
              className="w-full bg-amber-800 hover:bg-amber-900 text-white font-semibold py-3.5 rounded-xl transition-all shadow-md shadow-amber-950/15 text-sm flex items-center justify-center gap-2"
            >
              <ShieldCheck className="w-4 h-4" />
              {isSubmitting ? 'Authenticating...' : (isRegisterTab ? 'Create Account & Join Privé' : 'Sign In to Member Pass')}
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#FAF8F5] font-sans pb-24 text-stone-800 select-none">
      {/* Header */}
      <nav className="bg-white px-6 py-4 flex items-center justify-between border-b border-[#E7E2D9] sticky top-0 z-50 shadow-xs">
        <div className="flex items-center gap-3">
          <Link href="/customer" className="w-9 h-9 rounded-xl bg-[#FAF8F5] border border-[#E7E2D9] flex items-center justify-center text-stone-600 hover:text-amber-800 transition-colors">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <h1 className="text-base font-serif font-bold text-stone-900 tracking-tight">Member Gourmet Pass</h1>
        </div>

        <button onClick={logout} className="text-xs text-red-600 font-semibold hover:underline">
          Sign Out
        </button>
      </nav>

      <div className="px-6 mt-6 space-y-6 max-w-md mx-auto">
        
        {/* Quick Link: Table Reservation Banner */}
        <Link 
          href="/customer/reservations" 
          className="bg-amber-50 hover:bg-amber-100/80 border border-amber-200 p-4 rounded-2xl flex items-center justify-between shadow-xs transition-colors group"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-800 text-white flex items-center justify-center font-bold">
              <Calendar className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-serif font-bold text-stone-900 text-sm group-hover:text-amber-900">Book a Table</h3>
              <p className="text-xs text-stone-500 font-medium">Reserve seating & get instant email confirmation</p>
            </div>
          </div>
          <span className="text-amber-800 font-bold text-lg">→</span>
        </Link>

        {/* Digital Gold Membership Pass Card */}
        <div className="bg-gradient-to-br from-stone-900 via-stone-800 to-amber-950 text-white rounded-3xl p-6 shadow-xl relative overflow-hidden border border-amber-500/30">
          <div className="flex justify-between items-start mb-6">
            <div>
              <span className="text-[10px] font-bold tracking-widest uppercase text-amber-400">Bistro Lumière Privé</span>
              <h2 className="text-xl font-serif font-bold tracking-tight text-white mt-0.5">{user.name}</h2>
              <p className="text-xs text-stone-300 font-medium">{user.email}</p>
            </div>
            <div className="bg-amber-500/20 text-amber-300 text-[10px] font-bold px-2.5 py-1 rounded-full border border-amber-400/30 flex items-center gap-1">
              <Award className="w-3 h-3 text-amber-400" /> Gold Member
            </div>
          </div>

          <div className="flex justify-between items-end pt-4 border-t border-stone-700/60">
            <div>
              <p className="text-[10px] uppercase font-semibold text-stone-400 tracking-wider">Account Role / Phone</p>
              <p className="text-xs font-mono font-medium text-stone-200 mt-0.5">{user.phone || user.role}</p>
            </div>

            <div className="text-right">
              <p className="text-[10px] uppercase font-semibold text-amber-400 tracking-wider">Loyalty Rewards</p>
              <p className="text-2xl font-serif font-bold text-amber-300 tracking-tight">{user.loyalty_points || 35} Pts</p>
            </div>
          </div>
        </div>

        {/* QR Pass Card for POS Scanning */}
        <div className="bg-white rounded-3xl p-6 shadow-xs border border-[#E7E2D9] flex flex-col items-center">
          <div className="flex items-center gap-2 mb-4">
            <QrCode className="w-4 h-4 text-amber-800" />
            <h3 className="text-xs font-bold text-stone-700 uppercase tracking-wider">Counter Scan Pass</h3>
          </div>
          
          <div className="bg-[#FAF8F5] p-5 rounded-2xl border border-[#E7E2D9] flex items-center justify-center shadow-inner">
            <QRCodeSVG value={`CUST-${user.email}`} size={180} />
          </div>

          <p className="text-center text-xs text-stone-500 mt-4 leading-relaxed max-w-xs">
            Present this QR pass at the register cashier to link your table order and earn loyalty points automatically.
          </p>
        </div>

        {/* Recent Order History */}
        <div className="bg-white rounded-3xl p-6 shadow-xs border border-[#E7E2D9]">
          <h3 className="text-base font-serif font-bold text-stone-900 mb-4 flex items-center gap-2">
            <Clock className="w-4 h-4 text-amber-800" /> Past Dining Orders
          </h3>
          
          <div className="space-y-3">
            {pastOrders && pastOrders.length > 0 ? (
              pastOrders.map(order => (
                <div key={order.id} className="border-b border-stone-100 pb-3.5 last:border-0 last:pb-0 flex justify-between items-center">
                  <div>
                    <p className="font-semibold text-stone-900 text-xs">Order #{order.id}</p>
                    <p className="text-[10px] text-stone-400">{new Date(order.created_at).toLocaleDateString()}</p>
                  </div>
                  <div className="text-right">
                    <p className="font-serif font-bold text-amber-900 text-xs">₹{order.total_amount.toFixed(2)}</p>
                    <span className="text-[9px] font-bold px-2 py-0.5 bg-stone-100 rounded-md text-stone-600 uppercase tracking-wide">
                      {order.order_type}
                    </span>
                  </div>
                </div>
              ))
            ) : (
              <p className="text-xs text-stone-400 font-medium py-2">No past orders registered yet.</p>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}
