import { API_BASE_URL } from './config';

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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body !== undefined) headers.set('Content-Type', 'application/json');
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    let detail = response.statusText;
    let code: string | undefined;
    try {
      const problem = await response.json();
      detail = problem.detail ?? detail;
      code = problem.code;
    } catch {}
    throw new ApiError(detail, response.status, code);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export type PublicEvent = {
  name: string;
  eventType: string;
  active: boolean;
  allowGuestDownloads: boolean;
};

export type JoinResponse = {
  membershipId: string;
  eventId: string;
  inviteCode: string;
  eventName: string;
  displayName: string;
};

export type UploadUrlRequest = {
  inviteCode: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploaderDisplayName?: string;
  membershipId?: string;
};

export type UploadUrlResponse = {
  mediaId: string;
  objectKey: string;
  uploadUrl: string;
  httpMethod: string;
  requiredContentType: string;
  expiresInSeconds: number;
};

export type CompleteUploadRequest = {
  sha256: string;
  width?: number;
  height?: number;
};

export type MediaItem = {
  id: string;
  mediaType: 'PHOTO' | 'VIDEO';
  status: string;
  moderationState: string;
  uploaderDisplayName?: string;
  originalUrl?: string;
  thumbnailUrl?: string;
  createdAt: string;
};

export type GalleryPage = {
  items: MediaItem[];
  nextCursor?: string | null;
  hasMore: boolean;
};

export type EventSession = {
  inviteCode: string;
  membershipId?: string;
  displayName?: string;
};

export type EventResponse = {
  id: string;
  name: string;
  inviteCode: string;
  inviteUrl: string;
  allowGuestDownloads: boolean;
  autoApprove: boolean;
  status: string;
};

export type CreateEventRequest = {
  name: string;
  description?: string;
  eventType: string;
  eventDate?: string;
  allowGuestDownloads?: boolean;
  autoApprove?: boolean;
};

export const getPublicEvent = (code: string) =>
  request<PublicEvent>(`/events/code/${encodeURIComponent(code)}`);

export const joinEvent = (code: string, displayName: string) =>
  request<JoinResponse>(`/events/code/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    body: JSON.stringify({ displayName }),
  });

export const fetchGallery = (code: string, cursor?: string | null, limit = 20) => {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) params.set('cursor', cursor);
  return request<GalleryPage>(`/events/code/${encodeURIComponent(code)}/media?${params.toString()}`);
};

export const requestUploadUrl = (payload: UploadUrlRequest) =>
  request<UploadUrlResponse>('/media/upload-url', {
    method: 'POST',
    body: JSON.stringify(payload),
  });

export const completeUpload = (mediaId: string, payload: CompleteUploadRequest) =>
  request<MediaItem>(`/media/${encodeURIComponent(mediaId)}/complete`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });

export const createEvent = (token: string, payload: CreateEventRequest) =>
  request<EventResponse>('/events', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  });
