import type { EventSession } from './api';

let currentSession: EventSession | null = null;

export function setEventSession(session: EventSession) {
  currentSession = session;
}

export function getEventSession() {
  return currentSession;
}
