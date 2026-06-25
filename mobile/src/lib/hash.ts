export function sha256HexFallback(input: string) {
  let hash = 0;
  for (let i = 0; i < input.length; i += 1) {
    hash = (hash << 5) - hash + input.charCodeAt(i);
    hash |= 0;
  }
  return Array.from({ length: 64 }, (_, i) => {
    const nibble = (hash >>> ((i % 8) * 4)) & 0xf;
    return nibble.toString(16);
  }).join('');
}
