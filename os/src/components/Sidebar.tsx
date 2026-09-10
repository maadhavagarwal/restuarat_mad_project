import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { 
  LayoutGrid, 
  Receipt, 
  ChefHat, 
  Settings,
  Users,
  Utensils
} from 'lucide-react';

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-20 bg-[#FAF8F5] border-r border-[#E7E2D9] flex flex-col items-center py-6 shadow-sm z-20 shrink-0 select-none">
      {/* Brand Monogram */}
      <div className="relative mb-8 group cursor-pointer">
        <div className="w-12 h-12 bg-gradient-to-br from-amber-700 to-orange-600 rounded-2xl flex items-center justify-center text-white font-serif font-bold text-xl shadow-md shadow-orange-950/20 group-hover:scale-105 transition-transform">
          BL
        </div>
        <span className="absolute -top-1 -right-1 w-3.5 h-3.5 bg-emerald-500 border-2 border-[#FAF8F5] rounded-full shadow-sm" title="Register Online • Synced"></span>
      </div>
      
      {/* Main Navigation */}
      <nav className="flex flex-col gap-5 w-full items-center flex-1">
        <NavItem href="/" icon={<Receipt />} active={pathname === '/'} title="Point of Sale (POS)" />
        <NavItem href="/tables" icon={<LayoutGrid />} active={pathname === '/tables'} title="Floor Management" />
        <NavItem href="/kitchen" icon={<ChefHat />} active={pathname === '/kitchen'} title="Kitchen Display (KDS)" />
        <NavItem href="/customer" icon={<Users />} active={pathname.startsWith('/customer')} title="Customer Storefront" />
      </nav>

      {/* Cashier Shift Avatar */}
      <div className="mt-auto flex flex-col items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-stone-200 border-2 border-amber-600/30 flex items-center justify-center text-stone-700 font-semibold text-xs shadow-inner">
          AM
        </div>
      </div>
    </aside>
  );
}

function NavItem({ icon, active = false, href, title }: { icon: React.ReactNode, active?: boolean, href: string, title: string }) {
  return (
    <Link 
      href={href}
      title={title}
      className={`relative group w-12 h-12 rounded-2xl flex items-center justify-center transition-all duration-200 ${
        active 
          ? 'bg-amber-700 text-white shadow-md shadow-amber-900/15 scale-105' 
          : 'text-stone-500 hover:bg-stone-200/60 hover:text-stone-900'
      }`}
    >
      {React.cloneElement(icon as React.ReactElement<{ className?: string }>, { className: 'w-5 h-5' })}
      
      {/* Floating Tooltip */}
      <span className="absolute left-16 bg-stone-900 text-stone-100 text-xs font-medium px-2.5 py-1 rounded-lg opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap shadow-lg z-50">
        {title}
      </span>
    </Link>
  );
}
