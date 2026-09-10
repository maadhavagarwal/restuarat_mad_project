"use client";

import React, { useState, useEffect } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { Users, LayoutGrid, Clock, CheckCircle2, AlertCircle, BookmarkCheck, Armchair } from 'lucide-react';
import db from '@/db/db';
import Sidebar from '@/components/Sidebar';

export default function TableManagement() {
  const [activeZone, setActiveZone] = useState<'All' | 'Main Room' | 'Terrace' | 'Bar'>('All');

  // Fetch Tables
  const tables = useLiveQuery(() => db.diningTables.toArray()) || [];

  // Seed DB with tables if empty
  useEffect(() => {
    const seedTables = async () => {
      const count = await db.diningTables.count();
      if (count === 0) {
        await db.diningTables.bulkAdd([
          { table_number: 'T1', seating_capacity: 2, status: 'Available', sync_status: 'pending', updated_at: new Date().toISOString() },
          { table_number: 'T2', seating_capacity: 2, status: 'Occupied', sync_status: 'pending', updated_at: new Date().toISOString() },
          { table_number: 'T3', seating_capacity: 4, status: 'Available', sync_status: 'pending', updated_at: new Date().toISOString() },
          { table_number: 'T4', seating_capacity: 4, status: 'Reserved', sync_status: 'pending', updated_at: new Date().toISOString() },
          { table_number: 'T5', seating_capacity: 6, status: 'Available', sync_status: 'pending', updated_at: new Date().toISOString() },
          { table_number: 'T6', seating_capacity: 8, status: 'Available', sync_status: 'pending', updated_at: new Date().toISOString() },
          { table_number: 'T7', seating_capacity: 2, status: 'Available', sync_status: 'pending', updated_at: new Date().toISOString() },
          { table_number: 'T8', seating_capacity: 4, status: 'Occupied', sync_status: 'pending', updated_at: new Date().toISOString() },
        ]);
      }
    };
    seedTables();
  }, []);

  const toggleTableStatus = async (table: any) => {
    let nextStatus: 'Available' | 'Occupied' | 'Reserved' = 'Available';
    if (table.status === 'Available') nextStatus = 'Occupied';
    else if (table.status === 'Occupied') nextStatus = 'Reserved';
    
    await db.diningTables.update(table.id, { 
      status: nextStatus,
      updated_at: new Date().toISOString(),
      sync_status: 'pending'
    });
  };

  // Occupancy metrics
  const availableCount = tables.filter(t => t.status === 'Available').length;
  const occupiedCount = tables.filter(t => t.status === 'Occupied').length;
  const reservedCount = tables.filter(t => t.status === 'Reserved').length;
  const occupancyRate = tables.length > 0 ? Math.round((occupiedCount / tables.length) * 100) : 0;

  return (
    <div className="flex h-screen bg-[#FAF8F5] overflow-hidden font-sans text-stone-800 select-none">
      <Sidebar />

      <main className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Header */}
        <header className="h-20 bg-white border-b border-[#E7E2D9] flex items-center justify-between px-8 shadow-xs">
          <div>
            <h1 className="text-2xl font-serif font-bold text-stone-900 tracking-tight">Floor Management</h1>
            <p className="text-xs text-stone-500 font-medium mt-0.5">Interactive Table Seating & Real-Time Floor Plan</p>
          </div>
          
          {/* Status Legend Pills */}
          <div className="flex items-center gap-4 bg-[#FAF8F5] px-4 py-2 rounded-xl border border-[#E7E2D9]">
            <LegendItem color="bg-emerald-500" label="Available" count={availableCount} />
            <div className="w-px h-4 bg-stone-300"></div>
            <LegendItem color="bg-orange-600" label="Occupied" count={occupiedCount} />
            <div className="w-px h-4 bg-stone-300"></div>
            <LegendItem color="bg-amber-500" label="Reserved" count={reservedCount} />
          </div>
        </header>

        <div className="flex-1 overflow-y-auto p-8 space-y-6">
          
          {/* Occupancy Stats Overview Bar */}
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            <StatCard label="Total Floor Capacity" value={`${tables.reduce((sum, t) => sum + t.seating_capacity, 0)} Seats`} sub={`${tables.length} Tables`} icon={<Armchair className="w-5 h-5 text-stone-600" />} />
            <StatCard label="Occupancy Rate" value={`${occupancyRate}%`} sub={`${occupiedCount} Tables Active`} icon={<Users className="w-5 h-5 text-orange-600" />} />
            <StatCard label="Available Tables" value={`${availableCount}`} sub="Ready to Seat Guests" icon={<CheckCircle2 className="w-5 h-5 text-emerald-600" />} />
            <StatCard label="Reserved Tables" value={`${reservedCount}`} sub="Upcoming Arrivals" icon={<BookmarkCheck className="w-5 h-5 text-amber-600" />} />
          </div>

          {/* Floor Section Tabs */}
          <div className="flex justify-between items-center bg-white p-4 rounded-2xl border border-[#E7E2D9] shadow-xs">
            <div className="flex gap-2">
              {(['All', 'Main Room', 'Terrace', 'Bar'] as const).map(zone => (
                <button
                  key={zone}
                  onClick={() => setActiveZone(zone)}
                  className={`px-4 py-2 rounded-xl font-medium text-xs transition-all ${
                    activeZone === zone 
                      ? 'bg-amber-800 text-white shadow-xs' 
                      : 'bg-[#FAF8F5] text-stone-600 hover:bg-stone-200/60 border border-stone-200/60'
                  }`}
                >
                  {zone === 'All' ? 'All Sections' : zone}
                </button>
              ))}
            </div>

            <p className="text-xs text-stone-400 font-medium hidden md:block">
              Click any table card to cycle occupancy status (Available → Occupied → Reserved)
            </p>
          </div>

          {/* Interactive Floor Grid */}
          <div className="bg-white rounded-3xl p-8 border border-[#E7E2D9] shadow-xs min-h-[480px]">
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
              {tables.map(table => {
                const isOccupied = table.status === 'Occupied';
                const isReserved = table.status === 'Reserved';

                let cardBorder = 'border-emerald-300/80 bg-emerald-50/20 hover:border-emerald-500';
                let statusBadgeBg = 'bg-emerald-100 text-emerald-800 border-emerald-200';
                let tableNumberColor = 'text-emerald-900';

                if (isOccupied) {
                  cardBorder = 'border-orange-300/80 bg-orange-50/30 hover:border-orange-500';
                  statusBadgeBg = 'bg-orange-100 text-orange-900 border-orange-200';
                  tableNumberColor = 'text-orange-950';
                } else if (isReserved) {
                  cardBorder = 'border-amber-300/80 bg-amber-50/30 hover:border-amber-500';
                  statusBadgeBg = 'bg-amber-100 text-amber-900 border-amber-200';
                  tableNumberColor = 'text-amber-950';
                }

                return (
                  <div 
                    key={table.id}
                    onClick={() => toggleTableStatus(table)}
                    className={`
                      relative group cursor-pointer transition-all duration-200 hover:-translate-y-1 hover:shadow-md
                      rounded-2xl border-2 p-5 flex flex-col justify-between h-48 select-none overflow-hidden ${cardBorder}
                    `}
                  >
                    {/* Header: Table Number & Status Pill */}
                    <div className="flex justify-between items-start">
                      <div>
                        <span className="text-[10px] font-bold uppercase tracking-wider text-stone-400">Table</span>
                        <h3 className={`text-3xl font-serif font-black tracking-tight ${tableNumberColor}`}>
                          {table.table_number}
                        </h3>
                      </div>
                      
                      <span className={`text-[11px] font-semibold px-2.5 py-1 rounded-full border shadow-xs ${statusBadgeBg}`}>
                        {table.status}
                      </span>
                    </div>

                    {/* Seating Layout Visual */}
                    <div className="flex items-center justify-between my-2 pt-2 border-t border-stone-200/50">
                      <div className="flex items-center gap-1.5 text-stone-700 bg-white/80 px-2.5 py-1 rounded-lg border border-stone-200 text-xs font-semibold shadow-xs">
                        <Users className="w-3.5 h-3.5 text-stone-500" />
                        {table.seating_capacity} Seater
                      </div>

                      {isOccupied && (
                        <span className="text-[11px] font-medium text-orange-800 flex items-center gap-1">
                          <Clock className="w-3 h-3 text-orange-600" /> 35m seated
                        </span>
                      )}

                      {isReserved && (
                        <span className="text-[11px] font-medium text-amber-800">
                          Resv: 7:30 PM
                        </span>
                      )}
                    </div>

                    {/* Hover Overlay Feedback */}
                    <div className="absolute inset-0 bg-stone-900/85 backdrop-blur-xs rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex flex-col items-center justify-center text-white p-4 text-center z-10">
                      <span className="font-serif font-bold text-lg mb-1">{table.table_number} • {table.status}</span>
                      <p className="text-xs text-stone-300">Click to change table status</p>
                      <span className="mt-3 text-[10px] uppercase font-bold bg-amber-700 text-white px-3 py-1 rounded-lg tracking-wider">
                        Toggle Status
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

        </div>
      </main>
    </div>
  );
}

function StatCard({ label, value, sub, icon }: { label: string, value: string, sub: string, icon: React.ReactNode }) {
  return (
    <div className="bg-white p-5 rounded-2xl border border-[#E7E2D9] shadow-xs flex items-center justify-between">
      <div>
        <p className="text-xs font-semibold text-stone-500 uppercase tracking-wider">{label}</p>
        <p className="text-2xl font-serif font-bold text-stone-900 mt-1 tracking-tight">{value}</p>
        <p className="text-xs text-stone-400 font-medium mt-0.5">{sub}</p>
      </div>
      <div className="w-10 h-10 rounded-xl bg-[#FAF8F5] border border-[#E7E2D9] flex items-center justify-center">
        {icon}
      </div>
    </div>
  );
}

function LegendItem({ color, label, count }: { color: string, label: string, count: number }) {
  return (
    <div className="flex items-center gap-2">
      <div className={`w-2.5 h-2.5 rounded-full ${color}`}></div>
      <span className="text-xs font-semibold text-stone-700">{label}</span>
      <span className="text-[11px] font-bold bg-stone-200/80 text-stone-700 px-1.5 py-0.2 rounded-md">{count}</span>
    </div>
  );
}
