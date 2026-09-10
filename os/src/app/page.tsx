"use client";

import React, { useState, useEffect } from 'react';
import { 
  Search,
  Plus,
  Minus,
  Trash2,
  CreditCard,
  Banknote,
  Smartphone,
  UserPlus,
  UserCheck,
  X,
  Camera,
  Flame,
  Leaf,
  Star,
  Clock,
  ChevronDown,
  CheckCircle2,
  UtensilsCrossed
} from 'lucide-react';
import { useLiveQuery } from 'dexie-react-hooks';
import db from '@/db/db';
import Sidebar from '@/components/Sidebar';
import { Html5QrcodeScanner } from 'html5-qrcode';

export default function POS() {
  const [activeCategory, setActiveCategory] = useState<number | 'All'>('All');
  const [cart, setCart] = useState<any[]>([]);
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [selectedTable, setSelectedTable] = useState('Table 04');
  const [searchQuery, setSearchQuery] = useState('');

  // Customer Linking States
  const [linkedCustomer, setLinkedCustomer] = useState<any>(null);
  const [isCustomerModalOpen, setIsCustomerModalOpen] = useState(false);
  const [searchPhone, setSearchPhone] = useState('');
  const [isScanning, setIsScanning] = useState(false);

  // Seed DB on first load if empty
  useEffect(() => {
    const seedDB = async () => {
      const count = await db.categories.count();
      if (count === 0) {
        // Seed Categories
        const startersId = await db.categories.add({ name: 'Starters & Small Plates', sync_status: 'pending', updated_at: new Date().toISOString() });
        const mainsId = await db.categories.add({ name: 'Artisanal Mains', sync_status: 'pending', updated_at: new Date().toISOString() });
        const dessertsId = await db.categories.add({ name: 'Craft Desserts', sync_status: 'pending', updated_at: new Date().toISOString() });
        const bevsId = await db.categories.add({ name: 'Signature Drinks', sync_status: 'pending', updated_at: new Date().toISOString() });

        // Seed Rich Menu Items
        await db.menuItems.bulkAdd([
          { category_id: startersId as number, name: 'Charred Paneer Tikka', price: 280, tax_rate: 5, is_available: true, sync_status: 'pending', updated_at: new Date().toISOString() },
          { category_id: startersId as number, name: 'Crispy Truffle Fries', price: 210, tax_rate: 5, is_available: true, sync_status: 'pending', updated_at: new Date().toISOString() },
          { category_id: mainsId as number, name: 'Dum Handi Biryani', price: 380, tax_rate: 5, is_available: true, sync_status: 'pending', updated_at: new Date().toISOString() },
          { category_id: mainsId as number, name: 'Wood-fired Garlic Naan', price: 75, tax_rate: 5, is_available: true, sync_status: 'pending', updated_at: new Date().toISOString() },
          { category_id: dessertsId as number, name: 'Rose Saffron Gulab Jamun', price: 110, tax_rate: 5, is_available: true, sync_status: 'pending', updated_at: new Date().toISOString() },
          { category_id: bevsId as number, name: 'Mint Cucumber Mojito', price: 150, tax_rate: 5, is_available: true, sync_status: 'pending', updated_at: new Date().toISOString() },
          { category_id: bevsId as number, name: 'Almond Mango Lassi', price: 130, tax_rate: 5, is_available: true, sync_status: 'pending', updated_at: new Date().toISOString() },
        ]);
      }
    };
    seedDB();
  }, []);

  // Fetch Live Data from Dexie
  const categories = useLiveQuery(() => db.categories.toArray()) || [];
  const menuItems = useLiveQuery(() => {
    if (activeCategory === 'All') return db.menuItems.toArray();
    return db.menuItems.where('category_id').equals(activeCategory).toArray();
  }, [activeCategory]) || [];

  const filteredMenuItems = menuItems.filter(item => 
    searchQuery === '' || item.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const addToCart = (item: any) => {
    setCart(prev => {
      const existing = prev.find(i => i.id === item.id);
      if (existing) {
        return prev.map(i => i.id === item.id ? { ...i, quantity: i.quantity + 1 } : i);
      }
      return [...prev, { ...item, quantity: 1, notes: '' }];
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

  const updateNotes = (id: number, notes: string) => {
    setCart(prev => prev.map(item => item.id === id ? { ...item, notes } : item));
  };

  const total = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
  const tax = total * 0.05; // 5% GST
  const grandTotal = total + tax;

  const [receiptEmail, setReceiptEmail] = useState('');

  const handleCheckout = async (paymentMethod: 'Cash' | 'Card' | 'UPI') => {
    if (cart.length === 0) return;
    setIsCheckingOut(true);

    try {
      // 1. Create Order
      const orderId = await db.orders.add({
        order_type: 'Dine-in',
        status: 'Paid',
        total_amount: grandTotal,
        payment_method: paymentMethod,
        customer_id: linkedCustomer?.id || null,
        created_at: new Date().toISOString(),
        sync_status: 'pending',
        updated_at: new Date().toISOString()
      });

      // 2. Add Order Items
      const orderItems = cart.map(item => ({
        order_id: orderId as number,
        menu_item_id: item.id,
        quantity: item.quantity,
        price_at_time_of_order: item.price,
        sync_status: 'pending' as const,
        updated_at: new Date().toISOString()
      }));
      await db.orderItems.bulkAdd(orderItems);

      // 3. Create Kitchen Order Ticket (KOT)
      await db.kots.add({
        order_id: orderId as number,
        status: 'New',
        printed_status: false,
        sync_status: 'pending',
        updated_at: new Date().toISOString()
      });

      // 4. Update Loyalty Points if customer is linked
      if (linkedCustomer?.id) {
        const pointsEarned = Math.floor(grandTotal / 100);
        await db.customers.update(linkedCustomer.id, {
          loyalty_points: (linkedCustomer.loyalty_points || 0) + pointsEarned,
          updated_at: new Date().toISOString(),
          sync_status: 'pending'
        });
      }

      // 5. Send Digital Tax Invoice Email if email is provided
      const targetEmail = receiptEmail || (linkedCustomer?.email ? linkedCustomer.email : '');
      if (targetEmail) {
        fetch('/api/email/receipt', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            orderId,
            recipientEmail: targetEmail,
            tableNumber: selectedTable,
            items: cart,
            subtotal: total,
            tax,
            grandTotal,
            paymentMethod,
            customerName: linkedCustomer?.name || 'Valued Guest'
          })
        }).catch(err => console.error("Email receipt dispatch failed", err));
      }

      const emailNote = targetEmail ? `\n\n✉️ Digital Tax Receipt emailed to ${targetEmail}` : '';
      alert(`✅ Order Paid successfully via ${paymentMethod}!\n\nOrder ID: #${orderId}\nKOT sent to kitchen.${emailNote}`);
      setCart([]);
      setLinkedCustomer(null);
      setReceiptEmail('');
    } catch (error) {
      console.error("Checkout failed", error);
      alert("Failed to place order.");
    } finally {
      setIsCheckingOut(false);
    }
  };

  // Customer Linking Functions
  const handlePhoneSearch = async () => {
    if (!searchPhone) return;
    const customer = await db.customers.where('phone').equals(searchPhone).first();
    if (customer) {
      setLinkedCustomer(customer);
      setIsCustomerModalOpen(false);
      setSearchPhone('');
    } else {
      alert("Customer not found. Create one in the Customer Storefront.");
    }
  };

  // QR Scanner Effect
  useEffect(() => {
    let scanner: Html5QrcodeScanner | null = null;
    if (isScanning && isCustomerModalOpen) {
      scanner = new Html5QrcodeScanner(
        "qr-reader",
        { fps: 10, qrbox: { width: 250, height: 250 } },
        false
      );
      scanner.render(
        async (decodedText) => {
          let phone = decodedText;
          if (decodedText.startsWith('CUST-')) {
            phone = decodedText.split('-')[1];
          }
          const customer = await db.customers.where('phone').equals(phone).first();
          if (customer) {
            setLinkedCustomer(customer);
            setIsCustomerModalOpen(false);
            setIsScanning(false);
            if (scanner) scanner.clear();
          } else {
            alert("Customer QR code not recognized.");
          }
        },
        () => {}
      );
    }
    return () => {
      if (scanner) scanner.clear().catch(e => console.error(e));
    };
  }, [isScanning, isCustomerModalOpen]);

  // Helper for dish aesthetic tags
  const getDishBadges = (name: string) => {
    const lower = name.toLowerCase();
    const badges = [];
    if (lower.includes('biryani') || lower.includes('paneer') || lower.includes('truffle')) {
      badges.push({ label: 'Chef Choice', icon: <Star className="w-3 h-3 text-amber-500 fill-amber-500" />, bg: 'bg-amber-50 text-amber-900 border-amber-200' });
    }
    if (lower.includes('mojito') || lower.includes('tikka') || lower.includes('crispy')) {
      badges.push({ label: 'Popular', icon: <Flame className="w-3 h-3 text-orange-600" />, bg: 'bg-orange-50 text-orange-900 border-orange-200' });
    }
    if (lower.includes('paneer') || lower.includes('naan') || lower.includes('jamun') || lower.includes('lassi') || lower.includes('fries')) {
      badges.push({ label: 'Veg', icon: <Leaf className="w-3 h-3 text-emerald-600" />, bg: 'bg-emerald-50 text-emerald-900 border-emerald-200' });
    }
    return badges;
  };

  return (
    <div className="flex h-screen bg-[#FAF8F5] overflow-hidden font-sans text-stone-800 relative select-none">
      
      {/* Sidebar Navigation */}
      <Sidebar />

      {/* Main Cashier Workspace */}
      <main className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Header */}
        <header className="h-20 bg-white border-b border-[#E7E2D9] flex items-center justify-between px-8 shadow-xs">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-serif font-bold text-stone-900 tracking-tight">Bistro Lumière</h1>
              <span className="bg-amber-100/70 text-amber-900 text-xs font-semibold px-2.5 py-0.5 rounded-full border border-amber-200">
                Register #01
              </span>
            </div>
            <p className="text-xs text-stone-500 font-medium mt-0.5">
              Shift Active • Served by <span className="text-stone-700 font-semibold">Alex M.</span>
            </p>
          </div>
          
          <div className="flex items-center gap-4">
            <div className="relative">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-stone-400 w-4 h-4" />
              <input 
                type="text" 
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder="Search menu or code..." 
                className="pl-10 pr-4 py-2.5 bg-[#FAF8F5] border border-[#E7E2D9] rounded-xl focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 w-72 text-sm text-stone-800 placeholder-stone-400 outline-none transition-all"
              />
              {searchQuery && (
                <button onClick={() => setSearchQuery('')} className="absolute right-3 top-1/2 -translate-y-1/2 text-stone-400 hover:text-stone-600">
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
          </div>
        </header>

        <div className="flex-1 overflow-auto p-8">
          {/* Category Filter Pills */}
          <div className="flex gap-3 mb-8 overflow-x-auto pb-2 scrollbar-hide">
            <button
              onClick={() => setActiveCategory('All')}
              className={`px-5 py-2.5 rounded-xl font-medium text-sm whitespace-nowrap transition-all duration-200 ${
                activeCategory === 'All' 
                  ? 'bg-amber-800 text-white shadow-md shadow-amber-950/15' 
                  : 'bg-white text-stone-600 hover:bg-stone-100/70 border border-[#E7E2D9]'
              }`}
            >
              All Offerings
            </button>
            {categories.map(cat => (
              <button
                key={cat.id}
                onClick={() => setActiveCategory(cat.id!)}
                className={`px-5 py-2.5 rounded-xl font-medium text-sm whitespace-nowrap transition-all duration-200 ${
                  activeCategory === cat.id 
                    ? 'bg-amber-800 text-white shadow-md shadow-amber-950/15' 
                    : 'bg-white text-stone-600 hover:bg-stone-100/70 border border-[#E7E2D9]'
                }`}
              >
                {cat.name}
              </button>
            ))}
          </div>

          {/* Menu Offerings Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {filteredMenuItems.map(item => {
              const categoryName = categories.find(c => c.id === item.category_id)?.name || 'Specials';
              const badges = getDishBadges(item.name);

              return (
                <div 
                  key={item.id}
                  onClick={() => addToCart(item)}
                  className="group bg-white rounded-2xl p-5 shadow-xs border border-[#E7E2D9] cursor-pointer hover:shadow-md hover:border-amber-700/40 hover:-translate-y-0.5 transition-all duration-200 flex flex-col justify-between h-44 relative overflow-hidden"
                >
                  <div className="space-y-2">
                    <div className="flex justify-between items-start">
                      <h3 className="font-semibold text-stone-900 text-base group-hover:text-amber-800 transition-colors">
                        {item.name}
                      </h3>
                      <span className="text-[11px] font-medium text-stone-500 bg-stone-100 px-2 py-0.5 rounded-md border border-stone-200">
                        {categoryName}
                      </span>
                    </div>

                    <div className="flex flex-wrap gap-1.5 pt-1">
                      {badges.map((b, idx) => (
                        <span key={idx} className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-md border ${b.bg}`}>
                          {b.icon} {b.label}
                        </span>
                      ))}
                      <span className="inline-flex items-center gap-1 text-[10px] text-stone-500 bg-stone-50 px-1.5 py-0.5 rounded-md border border-stone-200/60">
                        <Clock className="w-2.5 h-2.5" /> 10-15m
                      </span>
                    </div>
                  </div>

                  <div className="flex justify-between items-end pt-4 border-t border-stone-100">
                    <div>
                      <p className="text-xs text-stone-400 font-medium">Price</p>
                      <p className="text-amber-900 font-bold text-xl tracking-tight">₹{item.price}</p>
                    </div>

                    <button 
                      onClick={(e) => {
                        e.stopPropagation();
                        addToCart(item);
                      }}
                      className="px-3.5 py-2 rounded-xl bg-amber-50 hover:bg-amber-800 hover:text-white text-amber-900 font-semibold text-xs transition-all duration-200 flex items-center gap-1.5 border border-amber-200 shadow-xs"
                    >
                      <Plus className="w-3.5 h-3.5" /> Add to Order
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </main>

      {/* Right Sidebar - Leather Guest Check Pad */}
      <aside className="w-96 bg-white border-l border-[#E7E2D9] flex flex-col shadow-xl z-10 shrink-0">
        
        {/* Table Selector Header */}
        <div className="p-5 border-b border-[#E7E2D9] flex justify-between items-center bg-[#FAF8F5]">
          <div>
            <h2 className="text-lg font-serif font-bold text-stone-900">Current Guest Check</h2>
            <p className="text-xs text-stone-500">Dine-In • Order #{Math.floor(1000 + Math.random() * 9000)}</p>
          </div>
          
          <div className="relative">
            <select 
              value={selectedTable}
              onChange={e => setSelectedTable(e.target.value)}
              className="appearance-none bg-white text-amber-900 font-semibold text-xs px-3 py-1.5 pr-7 rounded-lg border border-amber-200 shadow-xs cursor-pointer focus:outline-none"
            >
              <option value="Table 01">Table 01</option>
              <option value="Table 02">Table 02</option>
              <option value="Table 04">Table 04</option>
              <option value="Table 07">Table 07</option>
              <option value="Bar 02">Bar 02</option>
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-amber-800 absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </div>

        {/* Customer Loyalty Banner */}
        <div className="p-4 border-b border-[#E7E2D9] bg-stone-50/50">
          {linkedCustomer ? (
            <div className="flex justify-between items-center bg-amber-50/80 p-3 rounded-xl border border-amber-200 shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-full bg-amber-800 text-white flex items-center justify-center font-bold text-xs shadow-xs">
                  {linkedCustomer.name.charAt(0)}
                </div>
                <div>
                  <h4 className="font-bold text-xs text-stone-900">{linkedCustomer.name}</h4>
                  <p className="text-[11px] text-amber-900 font-medium">Gold Tier • {linkedCustomer.loyalty_points} Points</p>
                </div>
              </div>
              <button 
                onClick={() => setLinkedCustomer(null)} 
                className="p-1.5 text-stone-400 hover:text-red-600 rounded-lg hover:bg-white transition-colors"
                title="Remove Customer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <button 
              onClick={() => setIsCustomerModalOpen(true)}
              className="w-full py-2.5 px-4 border border-dashed border-stone-300 hover:border-amber-700/60 bg-white text-stone-600 hover:text-amber-900 rounded-xl font-medium text-xs flex items-center justify-center gap-2 transition-all shadow-xs"
            >
              <UserPlus className="w-4 h-4 text-amber-800" />
              Link Customer Profile (Loyalty & Pass)
            </button>
          )}
        </div>

        {/* Cart Item List */}
        <div className="flex-1 overflow-auto p-5 space-y-3">
          {cart.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-stone-400 py-12">
              <UtensilsCrossed className="w-10 h-10 mb-3 opacity-40 text-stone-400" />
              <p className="text-sm font-medium text-stone-500">Order check is empty</p>
              <p className="text-xs text-stone-400 mt-1 text-center max-w-[200px]">Select items from the menu to populate this check</p>
            </div>
          ) : (
            cart.map(item => (
              <div key={item.id} className="bg-[#FAF8F5] p-3.5 rounded-xl border border-[#E7E2D9] space-y-2">
                <div className="flex justify-between items-start">
                  <div>
                    <h4 className="font-semibold text-stone-900 text-sm">{item.name}</h4>
                    <p className="text-amber-900 font-bold text-xs mt-0.5">₹{item.price * item.quantity}</p>
                  </div>
                  
                  {/* Quantity Actions */}
                  <div className="flex items-center gap-2 bg-white rounded-lg border border-stone-200 p-1 shadow-xs">
                    <button 
                      onClick={() => updateQuantity(item.id, -1)} 
                      className="p-1 hover:bg-stone-100 rounded text-stone-600 transition-colors"
                    >
                      {item.quantity === 1 ? <Trash2 className="w-3.5 h-3.5 text-red-500" /> : <Minus className="w-3.5 h-3.5" />}
                    </button>
                    <span className="w-4 text-center font-bold text-xs text-stone-800">{item.quantity}</span>
                    <button 
                      onClick={() => updateQuantity(item.id, 1)} 
                      className="p-1 hover:bg-stone-100 rounded text-stone-600 transition-colors"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Optional Kitchen Note */}
                <input 
                  type="text" 
                  value={item.notes || ''} 
                  onChange={e => updateNotes(item.id, e.target.value)}
                  placeholder="Special prep note (e.g. extra spicy)..." 
                  className="w-full text-[11px] bg-white border border-stone-200 rounded-lg px-2.5 py-1 text-stone-700 outline-none placeholder-stone-400"
                />
              </div>
            ))
          )}
        </div>

        {/* Totals & Payment Actions */}
        <div className="p-5 bg-[#FAF8F5] border-t border-[#E7E2D9] space-y-4">
          <div>
            <label className="block text-[10px] font-bold text-stone-500 uppercase tracking-wider mb-1">Send Digital Receipt Email</label>
            <input 
              type="email" 
              value={receiptEmail}
              onChange={e => setReceiptEmail(e.target.value)}
              placeholder="e.g. guest@example.com"
              className="w-full text-xs bg-white border border-stone-200 rounded-lg px-3 py-1.5 text-stone-800 outline-none focus:ring-1 focus:ring-amber-700 placeholder-stone-400"
            />
          </div>

          <div className="space-y-2 text-xs text-stone-600">
            <div className="flex justify-between">
              <span>Subtotal</span>
              <span className="font-semibold text-stone-800">₹{total.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <span>GST (5%)</span>
              <span className="font-semibold text-stone-800">₹{tax.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-stone-900 font-bold text-lg pt-2 border-t border-stone-200">
              <span>Grand Total</span>
              <span className="text-amber-900 font-serif">₹{grandTotal.toFixed(2)}</span>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2">
            <button 
              onClick={() => handleCheckout('Cash')} 
              disabled={cart.length === 0 || isCheckingOut} 
              className="flex flex-col items-center justify-center gap-1.5 p-3 bg-white border border-stone-200 hover:border-amber-700 hover:bg-amber-50/50 rounded-xl transition-all text-stone-700 hover:text-amber-900 disabled:opacity-40 shadow-xs font-semibold text-xs"
            >
              <Banknote className="w-4 h-4 text-emerald-600" />
              <span>Cash</span>
            </button>
            <button 
              onClick={() => handleCheckout('Card')} 
              disabled={cart.length === 0 || isCheckingOut} 
              className="flex flex-col items-center justify-center gap-1.5 p-3 bg-white border border-stone-200 hover:border-amber-700 hover:bg-amber-50/50 rounded-xl transition-all text-stone-700 hover:text-amber-900 disabled:opacity-40 shadow-xs font-semibold text-xs"
            >
              <CreditCard className="w-4 h-4 text-blue-600" />
              <span>Card</span>
            </button>
            <button 
              onClick={() => handleCheckout('UPI')} 
              disabled={cart.length === 0 || isCheckingOut} 
              className="flex flex-col items-center justify-center gap-1.5 p-3 bg-white border border-stone-200 hover:border-amber-700 hover:bg-amber-50/50 rounded-xl transition-all text-stone-700 hover:text-amber-900 disabled:opacity-40 shadow-xs font-semibold text-xs"
            >
              <Smartphone className="w-4 h-4 text-amber-700" />
              <span>UPI</span>
            </button>
          </div>
        </div>
      </aside>

      {/* Customer Linking Modal */}
      {isCustomerModalOpen && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl w-full max-w-md overflow-hidden shadow-2xl flex flex-col border border-stone-200 animate-slide-up">
            <div className="p-6 border-b border-stone-100 flex justify-between items-center bg-[#FAF8F5]">
              <div>
                <h2 className="text-lg font-serif font-bold text-stone-900">Link Customer Account</h2>
                <p className="text-xs text-stone-500">Scan digital QR pass or search by phone</p>
              </div>
              <button 
                onClick={() => {
                  setIsCustomerModalOpen(false);
                  setIsScanning(false);
                }} 
                className="p-1.5 text-stone-400 hover:text-stone-600 rounded-full hover:bg-stone-200/60 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="p-6 space-y-5 flex-1 overflow-y-auto">
              {!isScanning ? (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-stone-700 mb-2 uppercase tracking-wide">Phone Number</label>
                    <div className="flex gap-2">
                      <input 
                        type="tel" 
                        value={searchPhone}
                        onChange={e => setSearchPhone(e.target.value)}
                        placeholder="e.g. 9876543210"
                        className="flex-1 border border-stone-200 rounded-xl px-4 py-2.5 bg-stone-50 text-sm focus:ring-2 focus:ring-amber-700/20 focus:border-amber-700 focus:outline-none"
                      />
                      <button 
                        onClick={handlePhoneSearch}
                        className="bg-amber-800 text-white px-5 py-2.5 rounded-xl font-semibold text-sm hover:bg-amber-900 transition-colors shadow-xs"
                      >
                        Search
                      </button>
                    </div>
                  </div>
                  
                  <div className="relative my-4">
                    <div className="absolute inset-0 flex items-center">
                      <div className="w-full border-t border-stone-200"></div>
                    </div>
                    <div className="relative flex justify-center text-xs">
                      <span className="px-3 bg-white text-stone-400 uppercase tracking-widest font-semibold">Or</span>
                    </div>
                  </div>

                  <button 
                    onClick={() => setIsScanning(true)}
                    className="w-full py-4 border-2 border-dashed border-stone-300 hover:border-amber-700 bg-amber-50/30 text-stone-700 rounded-2xl font-semibold text-sm flex flex-col items-center justify-center gap-2 transition-all shadow-xs"
                  >
                    <Camera className="w-6 h-6 text-amber-800" />
                    Scan Customer Digital Pass QR
                  </button>
                </>
              ) : (
                <div className="flex flex-col items-center">
                  <div id="qr-reader" className="w-full overflow-hidden rounded-xl border border-stone-200 bg-stone-50 min-h-[260px]"></div>
                  <button 
                    onClick={() => setIsScanning(false)}
                    className="mt-5 w-full py-2.5 bg-stone-100 text-stone-700 font-semibold text-sm rounded-xl hover:bg-stone-200 transition-colors"
                  >
                    Cancel Camera Scan
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
