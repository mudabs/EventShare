const HEX = Array.from({ length: 256 }, (_, i) => i.toString(16).padStart(2, '0'));

export async function sha256HexFromBytes(bytes: ArrayBuffer | Uint8Array): Promise<string> {
  const data = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  const subtle = globalThis.crypto?.subtle;
  if (!subtle?.digest) {
    throw new Error('SHA-256 is not available in this runtime.');
  }
  const digest = await subtle.digest('SHA-256', data);
  const view = new Uint8Array(digest);
  let out = '';
  for (const byte of view) out += HEX[byte];
  return out;
}
