/**
 * Computes the hex SHA-256 of a file using the Web Crypto API. The backend uses
 * this for exact duplicate detection. Note: this reads the whole file into
 * memory; for very large videos a streaming/chunked hash would be preferable.
 */
export async function sha256Hex(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}
