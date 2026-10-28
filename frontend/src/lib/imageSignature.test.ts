import { imageKind } from './imageSignature';

const blob = (bytes: number[]) => new File([new Uint8Array(bytes)], 'x.bin');

describe('imageKind (TECH-180)', () => {
  test('recognises JPEG and PNG by their first bytes', async () => {
    expect(await imageKind(blob([0xff, 0xd8, 0xff, 0xe0, 0, 0]))).toBe('jpeg');
    expect(await imageKind(blob([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a]))).toBe('png');
  });

  test('refuses text, an empty file and a short file whatever its name says', async () => {
    expect(await imageKind(new File(['not an image'], 'a.jpg', { type: 'image/jpeg' }))).toBeNull();
    expect(await imageKind(new File([], 'a.png', { type: 'image/png' }))).toBeNull();
    expect(await imageKind(blob([0xff, 0xd8]))).toBeNull();
  });
});
