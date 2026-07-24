import type {
  CompleteUploadRequest, CreateEventRequest, EventResponse, GalleryPage,
  AdminEvent, AdminUser, EventSettings, JoinResponse, MediaItem, MemberView, ModerationState, MyEventCard, OwnerDashboard, Plan, PlatformStats, Profile, PromoCode, PublicEvent, Subscription, UserDashboard, WhitelistEntry, UploadUrlRequest, UploadUrlResponse
} from './types';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || '/api';

export class ApiError extends Error {
  status: number;
  code?: string;
  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

interface RequestInitWithToken extends RequestInit {
  token?: string;
}

async function request<T>(path: string, init: RequestInitWithToken = {}): Promise<T> {
  const { token, headers, body, ...rest } = init;
  const finalHeaders: Record<string, string> = { ...(headers as Record<string, string>) };
  if (body !== undefined) {
    finalHeaders['Content-Type'] = 'application/json';
  }
  if (token) {
    finalHeaders['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, { ...rest, headers: finalHeaders, body });

  if (!response.ok) {
    let detail = response.statusText;
    let code: string | undefined;
    try {
      const problem = await response.json();
      detail = problem.detail ?? detail;
      code = problem.code;
    } catch {
      // non-JSON error body; keep status text
    }
    throw new ApiError(detail, response.status, code);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const getPublicEvent = (code: string) =>
  request<PublicEvent>(`/events/code/${encodeURIComponent(code)}`);

export const joinEvent = (code: string, displayName: string) =>
  request<JoinResponse>(`/events/code/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    body: JSON.stringify({ displayName })
  });

export const requestUploadUrl = (payload: UploadUrlRequest) =>
  request<UploadUrlResponse>(`/media/upload-url`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });

export const completeUpload = (mediaId: string, payload: CompleteUploadRequest) =>
  request<MediaItem>(`/media/${encodeURIComponent(mediaId)}/complete`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });

export const fetchGallery = (code: string, cursor?: string | null, limit = 30) => {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) {
    params.set('cursor', cursor);
  }
  return request<GalleryPage>(`/events/code/${encodeURIComponent(code)}/media?${params.toString()}`);
};

export const createEvent = (token: string, payload: CreateEventRequest) =>
  request<EventResponse>(`/events`, { method: 'POST', token, body: JSON.stringify(payload) });

/** Uploads the file bytes straight to R2 using the presigned PUT URL. */
export async function uploadToR2(uploadUrl: string, file: File, contentType: string): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: file
  });
  if (!response.ok) {
    throw new ApiError(`Direct upload failed (${response.status})`, response.status);
  }
}

// ---- My Events (authenticated) ----

export const fetchMyEvents = (token: string) =>
  request<MyEventCard[]>(`/me/events`, { token });

export const joinEventAuthenticated = (token: string, inviteCode: string) =>
  request<void>(`/me/events/join`, {
    method: 'POST',
    token,
    body: JSON.stringify({ inviteCode })
  });

export const leaveEvent = (token: string, eventId: string) =>
  request<void>(`/me/events/${encodeURIComponent(eventId)}/membership`, {
    method: 'DELETE',
    token
  });

// ---- Dashboards ----

export const fetchUserDashboard = (token: string) =>
  request<UserDashboard>(`/me/dashboard`, { token });

export const fetchOwnerDashboard = (token: string, eventId: string) =>
  request<OwnerDashboard>(`/events/${encodeURIComponent(eventId)}/dashboard`, { token });

// ---- Members / guests (owner) ----

export const fetchEventMembers = (token: string, eventId: string) =>
  request<MemberView[]>(`/events/${encodeURIComponent(eventId)}/members`, { token });

export const removeEventMember = (token: string, eventId: string, membershipId: string) =>
  request<void>(`/events/${encodeURIComponent(eventId)}/members/${encodeURIComponent(membershipId)}`, {
    method: 'DELETE',
    token
  });

// ---- Moderation + settings ----

export const fetchOwnerGallery = (token: string, eventId: string, state: ModerationState, cursor?: string) => {
  const params = new URLSearchParams({ state });
  if (cursor) params.set('cursor', cursor);
  return request<GalleryPage>(`/events/${encodeURIComponent(eventId)}/media?${params.toString()}`, { token });
};

export const moderateMedia = (token: string, eventId: string, mediaId: string, action: string) =>
  request<MediaItem>(`/events/${encodeURIComponent(eventId)}/media/${encodeURIComponent(mediaId)}/moderation`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ action })
  });

export const permanentDeleteMedia = (token: string, eventId: string, mediaId: string) =>
  request<void>(`/events/${encodeURIComponent(eventId)}/media/${encodeURIComponent(mediaId)}/permanent`, {
    method: 'DELETE',
    token
  });

export const getEventSettings = (token: string, eventId: string) =>
  request<EventSettings>(`/events/${encodeURIComponent(eventId)}/settings`, { token });

export const updateEventSettings = (token: string, eventId: string, body: Partial<EventSettings>) =>
  request<EventSettings>(`/events/${encodeURIComponent(eventId)}/settings`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(body)
  });

// ---- Plans + billing ----

export const fetchPlans = () => request<Plan[]>(`/plans`);

export const fetchSubscription = (token: string) =>
  request<Subscription>(`/me/subscription`, { token });

export const createCheckoutSession = (token: string, planCode: string) =>
  request<{ url: string }>(`/billing/checkout-session`, {
    method: 'POST',
    token,
    body: JSON.stringify({ planCode })
  });

export const createPortalSession = (token: string) =>
  request<{ url: string }>(`/billing/portal-session`, { method: 'POST', token });

export const redeemPromo = (token: string, code: string) =>
  request<{ type: string; message: string }>(`/me/promo/redeem`, {
    method: 'POST',
    token,
    body: JSON.stringify({ code })
  });

// ---- Admin ----

export const fetchProfile = (token: string) => request<Profile>(`/me/profile`, { token });

const adminQ = (path: string, query?: string) =>
  query ? `${path}?query=${encodeURIComponent(query)}` : path;

export const adminUsers = (token: string, query?: string) =>
  request<AdminUser[]>(adminQ(`/admin/users`, query), { token });
export const adminSetUserPlan = (token: string, id: string, planCode: string) =>
  request<void>(`/admin/users/${id}/subscription`, { method: 'POST', token, body: JSON.stringify({ planCode }) });
export const adminDisableUser = (token: string, id: string) =>
  request<void>(`/admin/users/${id}/disable`, { method: 'POST', token });
export const adminEnableUser = (token: string, id: string) =>
  request<void>(`/admin/users/${id}/enable`, { method: 'POST', token });
export const adminDeleteUser = (token: string, id: string) =>
  request<void>(`/admin/users/${id}`, { method: 'DELETE', token });

export const adminEvents = (token: string, query?: string) =>
  request<AdminEvent[]>(adminQ(`/admin/events`, query), { token });
export const adminArchiveEvent = (token: string, id: string) =>
  request<void>(`/admin/events/${id}/archive`, { method: 'POST', token });
export const adminRemoveEvent = (token: string, id: string) =>
  request<void>(`/admin/events/${id}`, { method: 'DELETE', token });

export const adminStats = (token: string) => request<PlatformStats>(`/admin/analytics`, { token });

export const adminPromoCodes = (token: string) => request<PromoCode[]>(`/admin/promo-codes`, { token });
export const adminCreatePromo = (token: string, body: Record<string, unknown>) =>
  request<PromoCode>(`/admin/promo-codes`, { method: 'POST', token, body: JSON.stringify(body) });
export const adminDisablePromo = (token: string, id: string) =>
  request<void>(`/admin/promo-codes/${id}/disable`, { method: 'POST', token });

export const adminWhitelist = (token: string) => request<WhitelistEntry[]>(`/admin/whitelist`, { token });
export const adminAddWhitelist = (token: string, email: string, note: string) =>
  request<WhitelistEntry>(`/admin/whitelist`, { method: 'POST', token, body: JSON.stringify({ email, note }) });
export const adminRemoveWhitelist = (token: string, id: string) =>
  request<void>(`/admin/whitelist/${id}`, { method: 'DELETE', token });

export const fetchEvent = (token: string, id: string) =>
  request<EventResponse>(`/events/${encodeURIComponent(id)}`, { token });
