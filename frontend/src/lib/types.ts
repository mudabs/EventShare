export type EventType =
  | 'WEDDING' | 'FAMILY' | 'GRADUATION' | 'CHURCH'
  | 'CONFERENCE' | 'BIRTHDAY' | 'REUNION' | 'OTHER';

export interface PublicEvent {
  name: string;
  eventType: EventType;
  active: boolean;
  allowGuestDownloads: boolean;
  showUploaderNames: boolean;
  showUploadTimestamps: boolean;
  anonymous: boolean;
  coverImageUrl: string | null;
}

export interface EventResponse {
  id: string;
  name: string;
  description: string | null;
  eventType: EventType;
  status: 'ACTIVE' | 'ARCHIVED';
  inviteCode: string;
  inviteUrl: string;
  eventDate: string | null;
  allowGuestDownloads: boolean;
  autoApprove: boolean;
  createdAt: string;
}

export interface JoinResponse {
  membershipId: string;
  eventId: string;
  inviteCode: string;
  eventName: string;
  displayName: string;
}

export interface UploadUrlRequest {
  inviteCode: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploaderDisplayName?: string;
  membershipId?: string;
}

export interface UploadUrlResponse {
  mediaId: string;
  objectKey: string;
  uploadUrl: string;
  httpMethod: string;
  requiredContentType: string;
  expiresInSeconds: number;
}

export interface CompleteUploadRequest {
  sha256: string;
  width?: number;
  height?: number;
}

export interface MediaItem {
  id: string;
  eventId: string;
  mediaType: 'PHOTO' | 'VIDEO';
  contentType: string;
  originalFilename: string | null;
  status: string;
  moderationState: string;
  uploaderDisplayName: string | null;
  width: number | null;
  height: number | null;
  durationSeconds: number | null;
  duplicate: boolean;
  createdAt: string;
  originalUrl: string;
  thumbnailUrl: string | null;
}

export interface GalleryPage {
  items: MediaItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface CreateEventRequest {
  name: string;
  description?: string;
  eventType: EventType;
  eventDate?: string;
  allowGuestDownloads?: boolean;
  autoApprove?: boolean;
}

export interface MyEventCard {
  id: string;
  name: string;
  eventType: EventType;
  coverImageUrl: string | null;
  eventDate: string | null;
  role: 'OWNER' | 'GUEST';
  status: 'ACTIVE' | 'ARCHIVED';
  inviteCode: string;
  photoCount: number;
  videoCount: number;
  lastActivityAt: string | null;
}

export interface DayCount {
  date: string;
  count: number;
}

export interface OwnerDashboard {
  eventId: string;
  totalPhotos: number;
  totalVideos: number;
  uploadsToday: number;
  storageUsedBytes: number;
  totalGuests: number;
  uniqueVisitors: number;
  activeGuests: number;
  createdAt: string;
  expiresAt: string | null;
  uploadActivity: DayCount[];
}

export interface UserDashboard {
  eventsOwned: number;
  eventsJoined: number;
  totalPhotos: number;
  totalVideos: number;
  recentEvents: MyEventCard[];
}

export type ModerationState = 'VISIBLE' | 'HIDDEN' | 'ARCHIVED' | 'DELETED';

export interface EventSettings {
  eventId: string;
  name: string;
  eventDate: string | null;
  uploaderVisibility: 'NAMED' | 'ANONYMOUS';
  showUploadTimestamps: boolean;
  showUploaderNames: boolean;
  showUploadStats: boolean;
  coverMediaId: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
}

export interface Plan {
  code: string;
  name: string;
  priceCents: number;
  billingInterval: 'NONE' | 'MONTH' | 'YEAR' | 'ONE_TIME';
  maxEvents: number | null;
  maxGuestsPerEvent: number | null;
  maxPhotosPerEvent: number | null;
  maxVideosPerEvent: number | null;
  storageBytes: number | null;
  zipExport: boolean;
  advancedAnalytics: boolean;
  priorityProcessing: boolean;
  retentionMonths: number | null;
}

export interface Subscription {
  planCode: string;
  planName: string;
  status: string;
  source: string;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  whitelisted: boolean;
  effectivePlan: Plan;
}

export interface Profile { id: string; email: string; displayName: string; role: string; disabled: boolean; }
export interface AdminUser { id: string; email: string; displayName: string; role: string; disabled: boolean; planCode: string; createdAt: string; lastSeenAt: string | null; }
export interface AdminEvent { id: string; name: string; eventType: string; status: string; hostId: string; mediaCount: number; createdAt: string; }
export interface MonthCount { month: string; count: number; }
export interface PlatformStats { totalUsers: number; totalEvents: number; totalUploads: number; totalStorageBytes: number; monthlyGrowth: MonthCount[]; }
export interface PromoCode { id: string; code: string; type: string; valueNumeric: number | null; grantsPlanCode: string | null; durationDays: number | null; maxRedemptions: number | null; redemptionsUsed: number; expiresAt: string | null; active: boolean; }
export interface WhitelistEntry { id: string; email: string; note: string | null; active: boolean; createdAt: string; }
