"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { useLiveQuery } from 'dexie-react-hooks';
import { ShoppingBag, ChevronRight, User, Plus, Minus, Trash2, Star, Flame, Leaf, UtensilsCrossed } from 'lucide-react';
import db from '@/db/db';

export default function CustomerStorefront() {
  const [activeCategory, setActiveCategory] = useState<number | 'All'>('All');
  const [cart, setCart] = useState<any[]>([]);
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [isCartOpen, setIsCartOpen] = useState(false);

  // Fetch Live Data from Dexie
  const categories = useLiveQuery(() => db.categories.toArray()) || [];
  const menuItems = useLiveQuery(() => {
    if (activeCategory === 'All') return db.menuItems.toArray();
    return db.menuItems.where('category_id').equals(activeCategory).toArray();
  }, [activeCategory]) || [];

  const addToCart = (item: any) => {
    setCart(prev => {
      const existing = prev.find(i => i.id === item.id);
      if (existing) {
        return prev.map(i => i.id === item.id ? { ...i, quantity: i.quantity + 1 } : i);
      }
      return [...prev, { ...item, quantity: 1 }];
    });
  };

  const updateQuantity = (id: number, delta: number) => {
    setCart(prev => prev.map(item => {
      if (item.id === id) {
        const newQ = item.quantity + delta;
        return newQ > 0 ? { ...item, quantity: newQ } : item;
      }
      return item;
    }).filter(item => item.quantity > 0));
  };

  const total = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
  const tax = total * 0.05; // 5% GST
  const grandTotal = total + tax;

  const handleCheckout = async () => {
    if (cart.length === 0) return;
    setIsCheckingOut(true);

    try {
      const orderId = await db.orders.add({
        order_type: 'Delivery',
        status: 'Active',
        total_amount: grandTotal,
        payment_method: 'UPI',
        created_at: new Date().toISOString(),
        sync_status: 'pending',
        updated_at: new Date().toISOString()
      });

      const orderItems = cart.map(item => ({
        order_id: orderId as number,
        menu_item_id: item.id,
        quantity: item.quantity,
        price_at_time_of_order: item.price,
        sync_status: 'pending' as const,
        updated_at: new Date().toISOString()
      }));
      await db.orderItems.bulkAdd(orderItems);

      await db.kots.add({
        order_id: orderId as number,
        status: 'New',
        printed_status: false,
        sync_status: 'pending',
        updated_at: new Date().toISOString()
      });

      alert(`✅ Order Placed successfully!\n\nYour Order ID: #${orderId}`);
      setCart([]);
      setIsCartOpen(false);
    } catch (error) {
      console.error("Checkout failed", error);
      alert("Failed to place order.");
    } finally {
      setIsCheckingOut(false);
    }
  };

  const getDishTag = (name: string) => {
    const lower = name.toLowerCase();
    if (lower.includes('biryani') || lower.includes('paneer')) return { label: 'Chef Choice', icon: <Star className="w-3 h-3 text-amber-500 fill-amber-500" /> };
    if (lower.includes('mojito') || lower.includes('fries')) return { label: 'Popular', icon: <Flame className="w-3 h-3 text-orange-600" /> };
    return { label: 'Fresh Prep', icon: <Leaf className="w-3 h-3 text-emerald-600" /> };
  };

  return (
    <div className="min-h-screen bg-[#FAF8F5] font-sans pb-28 text-stone-800 select-none">
      {/* Brand Header */}
      <nav className="bg-white px-6 py-4 flex justify-between items-center border-b border-[#E7E2D9] sticky top-0 z-40 shadow-xs">
        <div className="flex items-center gap-2">
          <div className="w-9 h-9 bg-gradient-to-br from-amber-700 to-orange-600 rounded-xl flex items-center justify-center text-white font-serif font-bold text-base shadow-xs">
            BL
          </div>
          <div>
            <h1 className="text-base font-serif font-bold text-stone-900 tracking-tight leading-tight">Bistro Lumière</h1>
            <p className="text-[10px] text-stone-500 font-medium">Digital Storefront</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Link href="/customer/profile" className="w-9 h-9 rounded-xl bg-[#FAF8F5] border border-[#E7E2D9] flex items-center justify-center text-stone-600 hover:text-amber-800 transition-colors shadow-xs">
            <User className="w-4 h-4" />
          </Link>

          <button 
            onClick={() => setIsCartOpen(!isCartOpen)}
            className="w-9 h-9 rounded-xl bg-amber-800 flex items-center justify-center text-white relative shadow-md shadow-amber-950/20"
          >
            <ShoppingBag className="w-4 h-4" />
            {cart.length > 0 && (
              <span className="absolute -top-1.5 -right-1.5 bg-orange-600 text-white text-[10px] font-bold w-4.5 h-4.5 flex items-center justify-center rounded-full border-2 border-white">
                {cart.reduce((sum, item) => sum + item.quantity, 0)}
              </span>
            )}
          </button>
        </div>
      </nav>

      {/* Hero Banner */}
      <div className="bg-gradient-to-br from-amber-900 via-stone-900 to-stone-950 text-white px-6 py-10 mb-6 shadow-md relative overflow-hidden">
        <div className="relative z-10 max-w-lg">
          <span className="inline-block bg-amber-500/20 text-amber-300 text-[11px] font-semibold px-3 py-1 rounded-full border border-amber-400/30 mb-3 tracking-wide">
            ⭐ Artisanal Dining Experience
          </span>
          <h2 className="text-2xl sm:text-3xl font-serif font-bold mb-2 tracking-tight leading-snug">
            Crafted Culinary Delights,<br />Delivered Fresh to You
          </h2>
          <p className="text-stone-300 text-xs sm:text-sm font-medium">
            Explore our curated menu cooked with fresh local ingredients.
          </p>
        </div>
      </div>

      {/* Categories Filter Horizontal */}
      <div className="px-6 mb-6 overflow-x-auto pb-2 scrollbar-hide flex gap-2.5">
        <button
          onClick={() => setActiveCategory('All')}
          className={`px-4.5 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all ${
            activeCategory === 'All' 
              ? 'bg-amber-800 text-white shadow-xs' 
              : 'bg-white text-stone-600 border border-[#E7E2D9]'
          }`}
        >
          All Dishes
        </button>
        {categories.map(cat => (
          <button
            key={cat.id}
            onClick={() => setActiveCategory(cat.id!)}
            className={`px-4.5 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all ${
              activeCategory === cat.id 
                ? 'bg-amber-800 text-white shadow-xs' 
                : 'bg-white text-stone-600 border border-[#E7E2D9]'
            }`}
          >
            {cat.name}
          </button>
        ))}
      </div>

      {/* Menu List */}
      <div className="px-6 space-y-3.5 max-w-xl mx-auto">
        {menuItems.map(item => {
          const tag = getDishTag(item.name);
          const cartItem = cart.find(i => i.id === item.id);

          return (
            <div key={item.id} className="bg-white rounded-2xl p-4 shadow-xs border border-[#E7E2D9] flex justify-between items-center hover:border-amber-700/40 transition-colors">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold text-stone-900 text-sm">{item.name}</h3>
                  <span className="inline-flex items-center gap-1 text-[10px] font-semibold bg-[#FAF8F5] text-stone-600 px-2 py-0.5 rounded-md border border-stone-200">
                    {tag.icon} {tag.label}
                  </span>
                </div>
                <p className="text-amber-900 font-bold text-base font-serif">₹{item.price}</p>
              </div>
              
              {cartItem ? (
                <div className="flex items-center gap-2.5 bg-[#FAF8F5] rounded-xl border border-stone-200 p-1 shadow-xs">
                  <button onClick={() => updateQuantity(item.id!, -1)} className="p-1 text-stone-600 hover:text-stone-900">
                    {cartItem.quantity === 1 ? <Trash2 className="w-3.5 h-3.5 text-red-500" /> : <Minus className="w-3.5 h-3.5" />}
                  </button>
                  <span className="w-4 text-center font-bold text-xs text-stone-800">{cartItem.quantity}</span>
                  <button onClick={() => updateQuantity(item.id!, 1)} className="p-1 text-stone-600 hover:text-stone-900">
                    <Plus className="w-3.5 h-3.5" />
                  </button>
                </div>
              ) : (
                <button 
                  onClick={() => addToCart(item)}
                  className="bg-amber-50 hover:bg-amber-800 hover:text-white text-amber-900 px-4 py-2 rounded-xl text-xs font-semibold border border-amber-200 shadow-xs transition-colors"
                >
                  + Add
                </button>
              )}
            </div>
          );
        })}
      </div>

      {/* Floating Bottom Cart Bar (Mobile Storefront) */}
      {cart.length > 0 && !isCartOpen && (
        <div className="fixed bottom-6 left-6 right-6 z-30 max-w-xl mx-auto">
          <button
            onClick={() => setIsCartOpen(true)}
            className="w-full bg-amber-800 text-white p-4 rounded-2xl shadow-xl flex justify-between items-center hover:bg-amber-900 transition-colors animate-slide-up"
          >
            <div className="flex items-center gap-3">
              <span className="bg-amber-900 text-amber-100 text-xs font-bold px-2.5 py-1 rounded-lg">
                {cart.reduce((sum, item) => sum + item.quantity, 0)} Items
              </span>
              <span className="text-sm font-semibold">View Order Check</span>
            </div>
            <div className="flex items-center gap-2 font-serif font-bold text-base">
              ₹{grandTotal.toFixed(2)} <ChevronRight className="w-4 h-4" />
            </div>
          </button>
        </div>
      )}

      {/* Slide-up Cart Sheet */}
      {isCartOpen && (
        <div className="fixed inset-0 z-50 flex flex-col justify-end">
          <div className="absolute inset-0 bg-stone-900/60 backdrop-blur-xs" onClick={() => setIsCartOpen(false)}></div>
          <div className="bg-white w-full rounded-t-3xl p-6 relative flex flex-col max-h-[85vh] animate-slide-up shadow-2xl border-t border-stone-200">
            <div className="w-10 h-1 bg-stone-300 rounded-full mx-auto mb-5"></div>
            
            <div className="flex justify-between items-center mb-5">
              <div>
                <h2 className="text-xl font-serif font-bold text-stone-900">Your Guest Check</h2>
                <p className="text-xs text-stone-500">Order Delivery • Bistro Lumière</p>
              </div>
              <button onClick={() => setIsCartOpen(false)} className="text-stone-400 hover:text-stone-600 text-sm font-semibold">
                Close
              </button>
            </div>
            
            <div className="flex-1 overflow-y-auto space-y-3.5 mb-5 pr-1">
              {cart.length === 0 ? (
                <div className="text-center text-stone-400 py-10">
                  <UtensilsCrossed className="w-8 h-8 mx-auto mb-2 opacity-40" />
                  <p className="text-xs font-medium">Your cart is empty</p>
                </div>
              ) : (
                cart.map(item => (
                  <div key={item.id} className="flex justify-between items-center bg-[#FAF8F5] p-3 rounded-xl border border-stone-200/60">
                    <div>
                      <h4 className="font-semibold text-stone-900 text-xs">{item.name}</h4>
                      <p className="text-stone-500 text-[11px]">Qty: {item.quantity} x ₹{item.price}</p>
                    </div>
                    <p className="font-serif font-bold text-amber-900 text-sm">₹{item.price * item.quantity}</p>
                  </div>
                ))
              )}
            </div>

            {cart.length > 0 && (
              <div className="pt-4 border-t border-stone-200 space-y-3">
                <div className="space-y-1.5 text-xs text-stone-600">
                  <div className="flex justify-between">
                    <span>Subtotal</span>
                    <span>₹{total.toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>GST (5%)</span>
                    <span>₹{tax.toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between text-stone-900 font-bold text-base pt-2 border-t border-stone-200">
                    <span>Total Amount</span>
                    <span className="text-amber-900 font-serif">₹{grandTotal.toFixed(2)}</span>
                  </div>
                </div>
                
                <button 
                  onClick={handleCheckout}
                  disabled={isCheckingOut}
                  className="w-full bg-amber-800 hover:bg-amber-900 text-white font-semibold text-sm py-3.5 rounded-xl flex items-center justify-center gap-2 transition-colors shadow-md shadow-amber-950/15"
                >
                  Place Order <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
