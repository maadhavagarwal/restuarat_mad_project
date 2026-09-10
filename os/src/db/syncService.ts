import db from './db';
import { supabase } from '@/lib/supabaseClient';

const SYNCABLE_TABLES = [
  'customers',
  'categories',
  'menuItems',
  'diningTables',
  'orders',
  'orderItems',
  'kots'
];

export const startSync = () => {
  if (typeof window === 'undefined') return;

  // Basic check for Supabase config
  const supaUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  if (!supaUrl) {
    console.warn("Supabase URL not configured, skipping background sync.");
    return;
  }

  const performSync = async () => {
    if (!navigator.onLine) {
      console.log('Offline: Sync paused.');
      return;
    }

    console.log('Starting sync with Supabase...');

    for (const tableName of SYNCABLE_TABLES) {
      const table = (db as any)[tableName];
      if (!table) continue;

      try {
        // Find all pending records
        const pendingRecords = await table.where('sync_status').equals('pending').toArray();
        
        if (pendingRecords.length === 0) continue;

        console.log(`Syncing ${pendingRecords.length} records for table: ${tableName}`);

        // We prepare the payload by overriding sync_status to 'synced' for the cloud DB
        const payload = pendingRecords.map((record: any) => ({
          ...record,
          sync_status: 'synced'
        }));

        // Upsert into Supabase
        const { error } = await supabase
          .from(tableName)
          .upsert(payload, { onConflict: 'id' });

        if (error) {
          console.error(`Error syncing ${tableName}:`, error);
        } else {
          // If successful, update local Dexie records to synced
          const idsToUpdate = pendingRecords.map((r: any) => r.id);
          await db.transaction('rw', table, async () => {
            for (const id of idsToUpdate) {
              await table.update(id, { sync_status: 'synced' });
            }
          });
          console.log(`Successfully synced ${tableName}`);
        }

      } catch (err) {
        console.error(`Failed to sync table ${tableName}:`, err);
      }
    }
  };

  // Run immediately
  performSync();

  // Run on online event
  window.addEventListener('online', performSync);

  // Periodic fallback check every 30 seconds
  const intervalId = setInterval(() => {
    performSync();
  }, 30000);

  return () => {
    window.removeEventListener('online', performSync);
    clearInterval(intervalId);
  };
};
