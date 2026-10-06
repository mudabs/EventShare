package com.eventshare.api.report;

/** Why a visitor reported a photo. */
public enum ReportReason {
    /** "This photo shows me and I want it removed." */
    ME_REMOVE,
    INAPPROPRIATE,
    COPYRIGHT,
    CHILD_SAFETY,
    OTHER
}
