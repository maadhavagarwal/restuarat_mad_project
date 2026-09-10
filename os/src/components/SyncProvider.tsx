"use client";

import { useEffect } from 'react';
import { startSync } from '@/db/syncService';

export default function SyncProvider({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    // Start background sync
    const stopSync = startSync();

    return () => {
      if (stopSync) stopSync();
    };
  }, []);

  return <>{children}</>;
}
