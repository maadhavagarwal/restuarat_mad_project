"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, Calendar, Clock, Users, Mail, Phone, User, CheckCircle2, BookmarkCheck, Sparkles } from 'lucide-react';
import { useAuth } from '@/components/AuthContext';

export default function TableReservations() {
  const { user } = useAuth();

  const [guestName, setGuestName] = useState(user?.name || '');
  const [email, setEmail] = useState(user?.email || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [bookingDate, setBookingDate] = useState(new Date().toISOString().split('T')[0]);
  const [timeSlot, setTimeSlot] = useState('19:30 PM');
  const [guestCount, setGuestCount] = useState(2);
  const [tablePreference, setTablePreference] = useState('Main Dining Hall');
  const [specialRequests, setSpecialRequests] = useState('');
  
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [confirmedBooking, setConfirmedBooking] = useState<any>(null);

  const timeSlots = [
    '12:30 PM', '13:30 PM', '18:00 PM', '19:00 PM', '19:30 PM', '20:30 PM', '21:30 PM'
  ];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!guestName || !email || !bookingDate || !timeSlot) {
      alert('Please fill out all required booking fields.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await fetch('/api/email/booking', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          guestName,
          email,
          phone,
          bookingDate,
          timeSlot,
          guestCount,
          tableNumber: `${tablePreference} • Table Auto-Assigned`,
          specialRequests
        })
      });

      const data = await res.json();
      if (!res.ok) {
        alert(data.error || 'Failed to place table reservation.');
        return;
      }

      setConfirmedBooking(data.booking);
    } catch (err) {
      console.error('Booking submission error:', err);
      alert('Error connecting to reservation server.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#FAF8F5] font-sans pb-24 text-stone-800 select-none">
      {/* Navbar */}
      <nav className="bg-white px-6 py-4 flex items-center justify-between border-b border-[#E7E2D9] sticky top-0 z-40 shadow-xs">
        <div className="flex items-center gap-3">
          <Link href="/customer" className="w-9 h-9 rounded-xl bg-[#FAF8F5] border border-[#E7E2D9] flex items-center justify-center text-stone-600 hover:text-amber-800 transition-colors">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <div>
            <h1 className="text-base font-serif font-bold text-stone-900 tracking-tight leading-tight">Table Reservations</h1>
            <p className="text-[10px] text-stone-500 font-medium">Bistro Lumière • Instant Email Confirmation</p>
          </div>
        </div>
      </nav>

      {/* Main Reservation Card */}
      <div className="px-6 mt-8 max-w-xl mx-auto">
        <div className="bg-white rounded-3xl p-6 sm:p-8 shadow-xl border border-[#E7E2D9] space-y-6">
          <div className="border-b border-stone-100 pb-5">
            <span className="inline-flex items-center gap-1.5 bg-amber-50 text-amber-900 text-[11px] font-semibold px-3 py-1 rounded-full border border-amber-200 mb-2">
              <Sparkles className="w-3 h-3 text-amber-600" /> Artisanal Dining Guarantee
            </span>
            <h2 className="text-2xl font-serif font-bold text-stone-900 tracking-tight">Reserve Your Table</h2>
            <p className="text-xs text-stone-500 font-medium mt-1">
              Select your dining time & party size. An automated confirmation ticket will be emailed instantly.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Contact Details */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1.5 uppercase tracking-wide flex items-center gap-1">
                  <User className="w-3 h-3 text-amber-800" /> Full Name
                </label>
                <input 
                  type="text"
                  required
                  value={guestName}
                  onChange={e => setGuestName(e.target.value)}
                  placeholder="e.g. Rahul Sharma"
                  className="w-full border border-stone-200 rounded-xl px-3.5 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1.5 uppercase tracking-wide flex items-center gap-1">
                  <Mail className="w-3 h-3 text-amber-800" /> Email for Confirmation
                </label>
                <input 
                  type="email"
                  required
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="e.g. rahul@example.com"
                  className="w-full border border-stone-200 rounded-xl px-3.5 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-stone-700 mb-1.5 uppercase tracking-wide flex items-center gap-1">
                <Phone className="w-3 h-3 text-amber-800" /> Phone Number
              </label>
              <input 
                type="tel"
                required
                value={phone}
                onChange={e => setPhone(e.target.value)}
                placeholder="e.g. 9876543210"
                className="w-full border border-stone-200 rounded-xl px-3.5 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none"
              />
            </div>

            {/* Date & Guest Count */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 border-t border-stone-100">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1.5 uppercase tracking-wide flex items-center gap-1">
                  <Calendar className="w-3 h-3 text-amber-800" /> Dining Date
                </label>
                <input 
                  type="date"
                  required
                  value={bookingDate}
                  onChange={e => setBookingDate(e.target.value)}
                  className="w-full border border-stone-200 rounded-xl px-3.5 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1.5 uppercase tracking-wide flex items-center gap-1">
                  <Users className="w-3 h-3 text-amber-800" /> Party Size
                </label>
                <select
                  value={guestCount}
                  onChange={e => setGuestCount(Number(e.target.value))}
                  className="w-full border border-stone-200 rounded-xl px-3.5 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none"
                >
                  {[1, 2, 3, 4, 5, 6, 8, 10].map(num => (
                    <option key={num} value={num}>{num} {num === 1 ? 'Guest' : 'Guests'}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Time Slot Selection */}
            <div className="space-y-2">
              <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wide flex items-center gap-1">
                <Clock className="w-3 h-3 text-amber-800" /> Preferred Time Slot
              </label>
              <div className="flex flex-wrap gap-2">
                {timeSlots.map(slot => (
                  <button
                    type="button"
                    key={slot}
                    onClick={() => setTimeSlot(slot)}
                    className={`px-3.5 py-2 rounded-xl text-xs font-semibold transition-all ${
                      timeSlot === slot 
                        ? 'bg-amber-800 text-white shadow-xs' 
                        : 'bg-[#FAF8F5] text-stone-700 hover:bg-stone-200/60 border border-stone-200'
                    }`}
                  >
                    {slot}
                  </button>
                ))}
              </div>
            </div>

            {/* Table Section Preference */}
            <div>
              <label className="block text-xs font-semibold text-stone-700 mb-1.5 uppercase tracking-wide">Seating Area Preference</label>
              <div className="grid grid-cols-3 gap-2">
                {['Main Dining Hall', 'Garden Terrace', 'Bar Lounge'].map(pref => (
                  <button
                    type="button"
                    key={pref}
                    onClick={() => setTablePreference(pref)}
                    className={`p-2.5 rounded-xl text-xs font-semibold text-center border transition-all ${
                      tablePreference === pref 
                        ? 'bg-amber-50 text-amber-900 border-amber-800 shadow-xs' 
                        : 'bg-[#FAF8F5] text-stone-600 border-stone-200'
                    }`}
                  >
                    {pref}
                  </button>
                ))}
              </div>
            </div>

            {/* Special Notes */}
            <div>
              <label className="block text-xs font-semibold text-stone-700 mb-1.5 uppercase tracking-wide">Special Requests / Dietary Notes</label>
              <textarea 
                rows={2}
                value={specialRequests}
                onChange={e => setSpecialRequests(e.target.value)}
                placeholder="e.g. Anniversary celebration, high chair needed, window table..."
                className="w-full border border-stone-200 rounded-xl px-3.5 py-2.5 bg-[#FAF8F5] text-stone-900 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none resize-none"
              />
            </div>

            <button 
              type="submit"
              disabled={isSubmitting}
              className="w-full bg-amber-800 hover:bg-amber-900 text-white font-semibold py-3.5 rounded-xl transition-all shadow-md shadow-amber-950/15 text-sm flex items-center justify-center gap-2"
            >
              {isSubmitting ? 'Dispatching Booking Email...' : 'Confirm Table Booking & Send Email'}
            </button>
          </form>
        </div>
      </div>

      {/* Booking Confirmation Ticket Modal */}
      {confirmedBooking && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl w-full max-w-md p-6 sm:p-8 shadow-2xl border border-stone-200 animate-slide-up text-center space-y-5">
            <div className="w-14 h-14 bg-emerald-100 text-emerald-800 rounded-2xl mx-auto flex items-center justify-center">
              <BookmarkCheck className="w-8 h-8" />
            </div>

            <div>
              <span className="text-[10px] font-bold uppercase tracking-widest text-emerald-700">Reservation Confirmed</span>
              <h3 className="text-2xl font-serif font-bold text-stone-900 mt-1">Table Reserved!</h3>
              <p className="text-xs text-stone-500 mt-1">An automated confirmation email has been dispatched to <strong>{confirmedBooking.email}</strong>.</p>
            </div>

            <div className="bg-[#FAF8F5] p-4 rounded-2xl border border-stone-200 text-left space-y-2 text-xs text-stone-700">
              <div className="flex justify-between">
                <span className="text-stone-400">Ref Code:</span>
                <span className="font-bold text-amber-900">#BL-RES-{confirmedBooking.id}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-stone-400">Guest Name:</span>
                <span className="font-semibold text-stone-900">{confirmedBooking.guest_name}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-stone-400">Date & Time:</span>
                <span className="font-semibold text-stone-900">{confirmedBooking.booking_date} • {confirmedBooking.time_slot}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-stone-400">Party Size:</span>
                <span className="font-semibold text-stone-900">{confirmedBooking.guest_count} Guests</span>
              </div>
              <div className="flex justify-between">
                <span className="text-stone-400">Section:</span>
                <span className="font-semibold text-emerald-700">{confirmedBooking.table_number}</span>
              </div>
            </div>

            <button 
              onClick={() => setConfirmedBooking(null)}
              className="w-full bg-stone-900 text-white font-semibold py-3 rounded-xl hover:bg-stone-800 transition-colors text-sm"
            >
              Done & Return
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
