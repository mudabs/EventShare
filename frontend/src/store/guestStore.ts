import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface GuestIdentity {
  membershipId?: string;
  displayName: string;
}

interface GuestState {
  identities: Record<string, GuestIdentity>;
  setIdentity: (code: string, identity: GuestIdentity) => void;
  clearIdentity: (code: string) => void;
}

/**
 * Remembers, per event invite code, the participant's display identity so they are
 * not re-prompted on every visit. Persisted to localStorage.
 */
export const useGuestStore = create<GuestState>()(
  persist(
    (set) => ({
      identities: {},
      setIdentity: (code, identity) =>
        set((state) => ({ identities: { ...state.identities, [code]: identity } })),
      clearIdentity: (code) =>
        set((state) => {
          const next = { ...state.identities };
          delete next[code];
          return { identities: next };
        })
    }),
    { name: 'eventshare-guest' }
  )
);
