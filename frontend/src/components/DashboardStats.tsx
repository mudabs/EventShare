'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { fetchUserDashboard } from '@/lib/api';

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 text-center">
      <div className="text-2xl font-bold text-slate-900">{value}</div>
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  );
}

export function DashboardStats() {
  const { getToken, isSignedIn } = useAuth();
  const { data } = useQuery({
    queryKey: ['userDashboard'],
    queryFn: async () => fetchUserDashboard((await getToken()) ?? ''),
    enabled: !!isSignedIn
  });

  const d = data ?? { eventsOwned: 0, eventsJoined: 0, totalPhotos: 0, totalVideos: 0, recentEvents: [] };

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <Stat label="Events owned" value={d.eventsOwned} />
      <Stat label="Events joined" value={d.eventsJoined} />
      <Stat label="Photos" value={d.totalPhotos} />
      <Stat label="Videos" value={d.totalVideos} />
    </div>
  );
}
