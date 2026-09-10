"use client";

import React, { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { Clock, CheckCircle2, Utensils, Check, ChefHat, Flame, AlertCircle } from 'lucide-react';
import db from '@/db/db';
import Sidebar from '@/components/Sidebar';

export default function KitchenDisplaySystem() {
  // Fetch active KOTs (not yet 'Served') and their associated OrderItems
  const kots = useLiveQuery(() => db.kots.where('status').notEqual('Served').toArray()) || [];
  const orderItems = useLiveQuery(() => db.orderItems.toArray()) || [];
  const menuItems = useLiveQuery(() => db.menuItems.toArray()) || [];

  const updateKOTStatus = async (id: number, newStatus: 'New' | 'Preparing' | 'Ready' | 'Served') => {
    await db.kots.update(id, { 
      status: newStatus, 
      updated_at: new Date().toISOString(),
      sync_status: 'pending'
    });
  };

  const getItemsForKOT = (orderId: number) => {
    return orderItems.filter(item => item.order_id === orderId).map(orderItem => {
      const menuDetails = menuItems.find(m => m.id === orderItem.menu_item_id);
      return {
        ...orderItem,
        name: menuDetails?.name || 'Artisanal Special'
      };
    });
  };

  const newOrders = kots.filter(k => k.status === 'New');
  const preparingOrders = kots.filter(k => k.status === 'Preparing');
  const readyOrders = kots.filter(k => k.status === 'Ready');

  return (
    <div className="flex h-screen bg-[#FAF8F5] overflow-hidden font-sans text-stone-800 select-none">
      <Sidebar />

      <main className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Header */}
        <header className="h-20 bg-white border-b border-[#E7E2D9] flex items-center justify-between px-8 shadow-xs">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-serif font-bold text-stone-900 tracking-tight">Kitchen Display System</h1>
              <span className="bg-amber-100 text-amber-900 text-xs font-semibold px-2.5 py-0.5 rounded-full border border-amber-200">
                Hot Pass Station #01
              </span>
            </div>
            <p className="text-xs text-stone-500 font-medium mt-0.5">Real-time KOT Processing & Culinary Line Synchronization</p>
          </div>

          <div className="flex items-center gap-3 text-xs font-medium text-stone-600 bg-[#FAF8F5] px-4 py-2 rounded-xl border border-[#E7E2D9]">
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-blue-500"></span> New ({newOrders.length})</span>
            <span className="w-px h-3 bg-stone-300"></span>
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-amber-500"></span> Cooking ({preparingOrders.length})</span>
            <span className="w-px h-3 bg-stone-300"></span>
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-emerald-500"></span> Ready ({readyOrders.length})</span>
          </div>
        </header>

        <div className="flex-1 overflow-x-auto p-8">
          <div className="flex gap-6 h-full min-w-max">
            
            {/* New Orders Column */}
            <div className="w-96 flex flex-col bg-stone-100/60 rounded-3xl p-5 border border-[#E7E2D9]">
              <div className="flex items-center justify-between mb-4 px-1">
                <h2 className="font-serif font-bold text-stone-900 text-base flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-blue-500"></span>
                  Incoming Tickets
                </h2>
                <span className="bg-blue-100 text-blue-900 text-xs font-bold px-2.5 py-0.5 rounded-full border border-blue-200">
                  {newOrders.length}
                </span>
              </div>
              <div className="flex-1 overflow-y-auto flex flex-col gap-4 pr-1">
                {newOrders.length === 0 ? (
                  <EmptyColumnNotice message="No incoming tickets" />
                ) : (
                  newOrders.map(kot => (
                    <KOTCard 
                      key={kot.id} 
                      kot={kot} 
                      items={getItemsForKOT(kot.order_id)} 
                      onAction={() => updateKOTStatus(kot.id!, 'Preparing')}
                      actionText="Start Cooking"
                      actionColor="bg-blue-600 hover:bg-blue-700 text-white shadow-xs"
                      icon={<ChefHat className="w-4 h-4" />}
                      badgeColor="bg-blue-50 text-blue-800 border-blue-200"
                    />
                  ))
                )}
              </div>
            </div>

            {/* Cooking / Preparing Column */}
            <div className="w-96 flex flex-col bg-amber-50/40 rounded-3xl p-5 border border-amber-200/60">
              <div className="flex items-center justify-between mb-4 px-1">
                <h2 className="font-serif font-bold text-amber-950 text-base flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500"></span>
                  Active Cooking Line
                </h2>
                <span className="bg-amber-100 text-amber-900 text-xs font-bold px-2.5 py-0.5 rounded-full border border-amber-200">
                  {preparingOrders.length}
                </span>
              </div>
              <div className="flex-1 overflow-y-auto flex flex-col gap-4 pr-1">
                {preparingOrders.length === 0 ? (
                  <EmptyColumnNotice message="No active dishes cooking" />
                ) : (
                  preparingOrders.map(kot => (
                    <KOTCard 
                      key={kot.id} 
                      kot={kot} 
                      items={getItemsForKOT(kot.order_id)} 
                      onAction={() => updateKOTStatus(kot.id!, 'Ready')}
                      actionText="Pass to Hot Window"
                      actionColor="bg-amber-700 hover:bg-amber-800 text-white shadow-xs"
                      icon={<Flame className="w-4 h-4" />}
                      badgeColor="bg-amber-100 text-amber-900 border-amber-200"
                    />
                  ))
                )}
              </div>
            </div>

            {/* Ready Column */}
            <div className="w-96 flex flex-col bg-emerald-50/40 rounded-3xl p-5 border border-emerald-200/60">
              <div className="flex items-center justify-between mb-4 px-1">
                <h2 className="font-serif font-bold text-emerald-950 text-base flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
                  Ready to Serve Pass
                </h2>
                <span className="bg-emerald-100 text-emerald-900 text-xs font-bold px-2.5 py-0.5 rounded-full border border-emerald-200">
                  {readyOrders.length}
                </span>
              </div>
              <div className="flex-1 overflow-y-auto flex flex-col gap-4 pr-1">
                {readyOrders.length === 0 ? (
                  <EmptyColumnNotice message="No completed orders pending runner" />
                ) : (
                  readyOrders.map(kot => (
                    <KOTCard 
                      key={kot.id} 
                      kot={kot} 
                      items={getItemsForKOT(kot.order_id)} 
                      onAction={() => updateKOTStatus(kot.id!, 'Served')}
                      actionText="Dispatched by Runner"
                      actionColor="bg-emerald-700 hover:bg-emerald-800 text-white shadow-xs"
                      icon={<Check className="w-4 h-4" />}
                      badgeColor="bg-emerald-100 text-emerald-900 border-emerald-200"
                    />
                  ))
                )}
              </div>
            </div>

          </div>
        </div>
      </main>
    </div>
  );
}

function KOTCard({ kot, items, onAction, actionText, actionColor, icon, badgeColor }: any) {
  const timeStr = new Date(kot.updated_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const [checkedItems, setCheckedItems] = useState<Record<number, boolean>>({});

  const toggleCheck = (idx: number) => {
    setCheckedItems(prev => ({ ...prev, [idx]: !prev[idx] }));
  };

  return (
    <div className="bg-white rounded-2xl shadow-xs border border-[#E7E2D9] overflow-hidden">
      {/* KOT Header */}
      <div className="px-4 py-3 border-b border-[#E7E2D9] flex justify-between items-center bg-[#FAF8F5]">
        <div>
          <span className="text-[10px] font-bold uppercase tracking-wider text-stone-400">Order Check</span>
          <h4 className="font-serif font-bold text-stone-900 text-base">#{kot.order_id}</h4>
        </div>

        <div className="flex items-center gap-2">
          <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-md border ${badgeColor}`}>
            {kot.status}
          </span>
          <div className="text-[11px] text-stone-500 flex items-center gap-1 font-medium bg-stone-100 px-2 py-0.5 rounded-md">
            <Clock className="w-3 h-3 text-stone-400" />
            {timeStr}
          </div>
        </div>
      </div>
      
      {/* Item Checklist */}
      <div className="p-4 space-y-4">
        <ul className="space-y-2.5">
          {items.map((item: any, idx: number) => {
            const isChecked = checkedItems[idx];
            return (
              <li 
                key={idx} 
                onClick={() => toggleCheck(idx)}
                className={`flex justify-between items-center p-2 rounded-xl border transition-all cursor-pointer select-none ${
                  isChecked 
                    ? 'bg-stone-50 border-stone-200/80 opacity-60' 
                    : 'bg-[#FAF8F5]/80 border-stone-200/60 hover:border-amber-700/40'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <span className={`w-5 h-5 rounded-md flex items-center justify-center text-xs font-bold ${
                    isChecked ? 'bg-emerald-600 text-white' : 'bg-amber-100 text-amber-900 border border-amber-200'
                  }`}>
                    {isChecked ? '✓' : `${item.quantity}x`}
                  </span>
                  <span className={`text-xs font-semibold ${isChecked ? 'line-through text-stone-400' : 'text-stone-800'}`}>
                    {item.name}
                  </span>
                </div>
              </li>
            );
          })}
        </ul>

        {/* Action Button */}
        <button 
          onClick={onAction}
          className={`w-full py-2.5 rounded-xl font-semibold text-xs flex items-center justify-center gap-2 transition-all duration-200 ${actionColor}`}
        >
          {icon}
          {actionText}
        </button>
      </div>
    </div>
  );
}

function EmptyColumnNotice({ message }: { message: string }) {
  return (
    <div className="h-40 border-2 border-dashed border-stone-200 rounded-2xl flex flex-col items-center justify-center text-stone-400 p-4">
      <ChefHat className="w-8 h-8 opacity-30 mb-2" />
      <p className="text-xs font-medium">{message}</p>
    </div>
  );
}
