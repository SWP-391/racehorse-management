// Renders frontend/public/models/horse.glb to PNG with a small software rasteriser (no WebGL, no dependency), so the
// look of the shared 3D model can be judged and checked into docs/3d-preview without a browser.
//
//   node scripts/preview-model.mjs <outDir> <name> [--layers skin,muscle,skeleton] [--az 90] [--el 8] [--mark LF_FETLOCK]
//                                  [--glb path] [--w 1100] [--h 760] [--zoom 1] [--target x,y,z] [--mats muscle,tendon]
import { readFileSync, writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const out = resolve(args[0], `${args[1]}.png`);
const opt = (name, d) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : d;
};
const layers = opt('layers', 'skin').split(',');
const mats = opt('mats', '') ? opt('mats', '').split(',') : null;
const az = (Number(opt('az', 90)) * Math.PI) / 180;
const el = (Number(opt('el', 8)) * Math.PI) / 180;
const W = Number(opt('w', 1100)), H = Number(opt('h', 760));
const zoom = Number(opt('zoom', 1));
const mark = opt('mark', '');
const glb = opt('glb', resolve(here, '../public/models/horse.glb'));
const target = opt('target', '0,0.95,0.15').split(',').map(Number);

// ---- read the GLB -------------------------------------------------------------------------------------------
const buf = readFileSync(glb);
const jsonLen = buf.readUInt32LE(12);
const gltf = JSON.parse(buf.subarray(20, 20 + jsonLen).toString());
const bin = buf.subarray(20 + jsonLen + 8);
const read = (acc, Type, comps) => {
  const a = gltf.accessors[acc], v = gltf.bufferViews[a.bufferView];
  const arr = new Type(bin.buffer.slice(bin.byteOffset + v.byteOffset, bin.byteOffset + v.byteOffset + v.byteLength));
  return arr.subarray(0, a.count * comps);
};

// ---- camera -------------------------------------------------------------------------------------------------
const dist = 5.2 / zoom;
const eye = [target[0] + dist * Math.sin(az) * Math.cos(el), target[1] + dist * Math.sin(el), target[2] + dist * Math.cos(az) * Math.cos(el)];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const norm = (a) => {
  const l = Math.hypot(...a) || 1;
  return [a[0] / l, a[1] / l, a[2] / l];
};
const fwd = norm(sub(target, eye));
const right = norm(cross(fwd, [0, 1, 0]));
const up = cross(right, fwd);
const f = 1 / Math.tan((22 * Math.PI) / 180);
const SS = 2, SW = W * SS, SH = H * SS;
const project = (p) => {
  const d = sub(p, eye);
  const x = dot(d, right), y = dot(d, up), z = dot(d, fwd);
  return [SW / 2 + ((x * f) / z) * (SH / 2), SH / 2 - ((y * f) / z) * (SH / 2), z];
};

// ---- buffers ------------------------------------------------------------------------------------------------
const color = new Float32Array(SW * SH * 3);
const depth = new Float32Array(SW * SH).fill(Infinity);
const lin = (v) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
for (let i = 0; i < SW * SH; i++) {
  const t = Math.floor(i / SW) / SH;
  color[i * 3] = lin(0.93 - 0.1 * t);
  color[i * 3 + 1] = lin(0.94 - 0.08 * t);
  color[i * 3 + 2] = lin(0.96 - 0.04 * t);
}
const EXPOSURE = 0.52;
const L1 = norm([0.5, 0.8, 0.45]), L2 = norm([-0.6, 0.3, -0.5]);
const tri = (P, N, base, noDepth = false, emissive = false, C = null) => {
  const minX = Math.max(0, Math.floor(Math.min(P[0][0], P[1][0], P[2][0])));
  const maxX = Math.min(SW - 1, Math.ceil(Math.max(P[0][0], P[1][0], P[2][0])));
  const minY = Math.max(0, Math.floor(Math.min(P[0][1], P[1][1], P[2][1])));
  const maxY = Math.min(SH - 1, Math.ceil(Math.max(P[0][1], P[1][1], P[2][1])));
  const area = (P[1][0] - P[0][0]) * (P[2][1] - P[0][1]) - (P[2][0] - P[0][0]) * (P[1][1] - P[0][1]);
  if (Math.abs(area) < 1e-9) return;
  for (let y = minY; y <= maxY; y++) {
    for (let x = minX; x <= maxX; x++) {
      const px = x + 0.5, py = y + 0.5;
      const w0 = ((P[1][0] - px) * (P[2][1] - py) - (P[2][0] - px) * (P[1][1] - py)) / area;
      const w1 = ((P[2][0] - px) * (P[0][1] - py) - (P[0][0] - px) * (P[2][1] - py)) / area;
      const w2 = 1 - w0 - w1;
      if (w0 < 0 || w1 < 0 || w2 < 0) continue;
      const z = 1 / (w0 / P[0][2] + w1 / P[1][2] + w2 / P[2][2]);
      const i = y * SW + x;
      if (!noDepth && z >= depth[i]) continue;
      depth[i] = noDepth ? depth[i] : z;
      let c;
      if (emissive) c = base;
      else {
        const w = [w0 / P[0][2] * z, w1 / P[1][2] * z, w2 / P[2][2] * z];
        let n = norm([0, 1, 2].map((k) => N[0][k] * w[0] + N[1][k] * w[1] + N[2][k] * w[2]));
        if (dot(n, fwd) > 0) n = [-n[0], -n[1], -n[2]];
        const d1 = Math.max(dot(n, L1), 0), d2 = Math.max(dot(n, L2), 0);
        const rim = Math.pow(1 - Math.max(dot(n, [-fwd[0], -fwd[1], -fwd[2]]), 0), 3) * 0.12;
        const k = 0.28 + 0.78 * d1 + 0.18 * d2 + rim;
        const vc = C ? [0, 1, 2].map((q) => C[0][q] * w[0] + C[1][q] * w[1] + C[2][q] * w[2]) : [1, 1, 1];
        c = [base[0] * vc[0] * k * EXPOSURE, base[1] * vc[1] * k * EXPOSURE, base[2] * vc[2] * k * EXPOSURE];
      }
      color[i * 3] = c[0];
      color[i * 3 + 1] = c[1];
      color[i * 3 + 2] = c[2];
    }
  }
};

// ---- draw ---------------------------------------------------------------------------------------------------
const nodeByName = new Map(gltf.nodes.map((n) => [n.name, n]));
let triangles = 0;
for (const node of gltf.nodes) {
  if (node.mesh === undefined) continue;
  if (!layers.includes(node.name.split('.')[0])) continue;
  for (const prim of gltf.meshes[node.mesh].primitives) {
    if (mats && !mats.includes(gltf.materials[prim.material].name)) continue;
    const pos = read(prim.attributes.POSITION, Float32Array, 3);
    const nor = read(prim.attributes.NORMAL, Float32Array, 3);
    const idxAcc = gltf.accessors[prim.indices];
    const idx = read(prim.indices, idxAcc.componentType === 5125 ? Uint32Array : Uint16Array, 1);
    const base = gltf.materials[prim.material].pbrMetallicRoughness.baseColorFactor;
    const col = prim.attributes.COLOR_0 !== undefined ? read(prim.attributes.COLOR_0, Uint8Array, 4) : null;
    const vcol = (v) => (col ? [col[v * 4] / 255, col[v * 4 + 1] / 255, col[v * 4 + 2] / 255] : [1, 1, 1]);
    const proj = [];
    for (let i = 0; i < pos.length / 3; i++) proj.push(project([pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]]));
    for (let i = 0; i < idx.length; i += 3) {
      const a = idx[i], b = idx[i + 1], c = idx[i + 2];
      if (proj[a][2] <= 0.1 || proj[b][2] <= 0.1 || proj[c][2] <= 0.1) continue;
      tri([proj[a], proj[b], proj[c]], [[nor[a * 3], nor[a * 3 + 1], nor[a * 3 + 2]], [nor[b * 3], nor[b * 3 + 1], nor[b * 3 + 2]], [nor[c * 3], nor[c * 3 + 1], nor[c * 3 + 2]]], base, false, false, col ? [vcol(a), vcol(b), vcol(c)] : null);
      triangles++;
    }
  }
}
if (mark) {
  const a = nodeByName.get(`anchor.${mark}`);
  if (!a) throw new Error(`no anchor.${mark}`);
  const r = 0.035;
  const rings = 8, segs = 14;
  const pts = [];
  for (let i = 0; i <= rings; i++) {
    const phi = (i / rings) * Math.PI;
    for (let j = 0; j < segs; j++) {
      const th = (j / segs) * Math.PI * 2;
      pts.push(project([a.translation[0] + r * Math.sin(phi) * Math.cos(th), a.translation[1] + r * Math.cos(phi), a.translation[2] + r * Math.sin(phi) * Math.sin(th)]));
    }
  }
  for (let i = 0; i < rings; i++) {
    for (let j = 0; j < segs; j++) {
      const j2 = (j + 1) % segs;
      const A = pts[i * segs + j], B = pts[(i + 1) * segs + j], C = pts[(i + 1) * segs + j2], D = pts[i * segs + j2];
      tri([A, B, D], null, [0.85, 0.02, 0.01], true, true);
      tri([B, C, D], null, [0.85, 0.02, 0.01], true, true);
    }
  }
}

// ---- downsample and write the PNG ----------------------------------------------------------------------------
// colours are linear; the background is already a display colour. Model pixels are scaled like the app's renderer
// (measured against browser screenshots: about 0.52 x albedo x shading, then sRGB encoding).
const srgb = (v) => (v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055);
const raw = Buffer.alloc((W * 3 + 1) * H);
for (let y = 0; y < H; y++) {
  raw[y * (W * 3 + 1)] = 0;
  for (let x = 0; x < W; x++) {
    for (let k = 0; k < 3; k++) {
      let s = 0;
      for (let dy = 0; dy < SS; dy++) for (let dx = 0; dx < SS; dx++) s += color[((y * SS + dy) * SW + x * SS + dx) * 3 + k];
      raw[y * (W * 3 + 1) + 1 + x * 3 + k] = Math.max(0, Math.min(255, Math.round(srgb(s / (SS * SS)) * 255)));
    }
  }
}
const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc = (b) => {
  let c = 0xffffffff;
  for (const x of b) c = crcTable[(c ^ x) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const c = Buffer.alloc(4);
  c.writeUInt32BE(crc(td));
  return Buffer.concat([len, td, c]);
};
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0);
ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8;
ihdr[9] = 2;
writeFileSync(out, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
console.log(`${out}: ${triangles} triangles, layers ${layers.join('+')}`);
