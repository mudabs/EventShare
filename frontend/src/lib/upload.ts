import type { GuestIdentity } from '@/store/guestStore';
import { completeUpload, requestUploadUrl, uploadToR2 } from './api';
import { sha256Hex } from './sha256';

/**
 * Full direct-to-R2 upload for one file, whether it came from the file picker or a
 * live camera capture: reserve a presigned URL, PUT the bytes, then confirm with a hash.
 */
export async function uploadCapturedFile(code: string, file: File, identity?: GuestIdentity): Promise<void> {
  const contentType = file.type || 'application/octet-stream';
  const reservation = await requestUploadUrl({
    inviteCode: code,
    filename: file.name,
    contentType,
    sizeBytes: file.size,
    uploaderDisplayName: identity?.displayName,
    membershipId: identity?.membershipId
  });
  await uploadToR2(reservation.uploadUrl, file, reservation.requiredContentType);
  const sha256 = await sha256Hex(file);
  await completeUpload(reservation.mediaId, { sha256 });
}
