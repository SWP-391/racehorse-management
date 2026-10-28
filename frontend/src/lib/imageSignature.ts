/** The kind of image a file really is, read from its first bytes rather than its name or declared type (TECH-180). */
export type ImageKind = 'jpeg' | 'png';

const read = (blob: Blob): Promise<Uint8Array> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(blob);
  });

/** JPEG starts FF D8 FF; PNG starts 89 50 4E 47. Anything else, or an unreadable file, is not an accepted image. */
export async function imageKind(file: Blob): Promise<ImageKind | null> {
  try {
    const b = await read(file.slice(0, 4));
    if (b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return 'jpeg';
    if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return 'png';
  } catch {
    // An unreadable file is refused like any other that is not an image.
  }
  return null;
}
