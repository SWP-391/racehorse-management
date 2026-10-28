// Generates frontend/public/models/horse.glb: one shared, procedurally modelled racehorse (NFR-014, TECH-004, DEC-033).
//
// Contract with the rest of the system (TECH-049): for each of the 41 body-region codes of ba/07 the file holds the
// nodes `skin.<CODE>`, `muscle.<CODE>`, `skeleton.<CODE>` (each with a mesh) and an empty `anchor.<CODE>` where a mark
// is drawn. A better model can replace this file as long as it keeps those node names; marks store only the code.
//
// How it is built: every layer is an implicit surface (signed distance fields of ellipsoids and tapered segments,
// blended with smooth unions) turned into a mesh by surface nets, so the body, neck, head and legs flow into each other.
// - skin: one blended body in bay colours (black points, dark muzzle), separate hooves, mane, forelock and tail;
// - muscle: named superficial muscles, each a domed patch just under the skin with fibre striation and tendinous ends,
//   over a darker deep layer; tendons and ligaments in the lower legs;
// - skeleton: skull and jaw, vertebrae with withers spines, ribs, sternum, scapulae, pelvis and every limb bone.
// Colours are vertex colours (COLOR_0) on white materials. Units are metres, +Y up, the horse faces +Z and its left
// side is +X. Deterministic (no random numbers), so two runs write identical bytes. No dependency.
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------------------------------------------------------
// vector helpers
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const mul = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const len = (a) => Math.hypot(a[0], a[1], a[2]);
const norm = (a) => {
  const l = len(a) || 1;
  return [a[0] / l, a[1] / l, a[2] / l];
};
const lerp = (a, b, t) => a + (b - a) * t;
const lerp3 = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
const smooth = (e0, e1, x) => {
  const t = clamp((x - e0) / (e1 - e0), 0, 1);
  return t * t * (3 - 2 * t);
};
const mirror = (p) => [-p[0], p[1], p[2]];

/** Catmull-Rom through numeric control values; u in [0, n-1]. */
function cr(vals, u) {
  const n = vals.length;
  const i = Math.max(0, Math.min(Math.floor(u), n - 2));
  const t = u - i;
  const p0 = vals[Math.max(i - 1, 0)], p1 = vals[i], p2 = vals[i + 1], p3 = vals[Math.min(i + 2, n - 1)];
  return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
}

const smin = (a, b, k) => {
  if (k <= 0) return Math.min(a, b);
  const h = Math.max(k - Math.abs(a - b), 0) / k;
  return Math.min(a, b) - h * h * k * 0.25;
};
const smax = (a, b, k) => -smin(-a, -b, k);

// ---------------------------------------------------------------------------------------------------------------
// primitives: { f(x,y,z) -> approximate signed distance, lo, hi (bounding box) }
function frame(axis, hint) {
  const e1 = norm(axis);
  let h = hint;
  if (Math.abs(dot(e1, norm(h))) > 0.97) h = Math.abs(e1[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
  const e2 = norm(sub(h, mul(e1, dot(h, e1))));
  return [e1, e2, cross(e1, e2)];
}
function boxOf(c, axes, radii, pad = 0) {
  const lo = [], hi = [];
  for (let k = 0; k < 3; k++) {
    const ext = Math.hypot(axes[0][k] * radii[0], axes[1][k] * radii[1], axes[2][k] * radii[2]) + pad;
    lo.push(c[k] - ext);
    hi.push(c[k] + ext);
  }
  return { lo, hi };
}

/** Ellipsoid with radii [along axis, along hint, third]. */
function ell(c, radii, axis = [0, 0, 1], hint = [0, 1, 0]) {
  const [e1, e2, e3] = frame(axis, hint);
  const [r1, r2, r3] = radii;
  const rmin = Math.min(r1, r2, r3);
  const f = (x, y, z) => {
    const px = x - c[0], py = y - c[1], pz = z - c[2];
    const a = px * e1[0] + py * e1[1] + pz * e1[2];
    const b = px * e2[0] + py * e2[1] + pz * e2[2];
    const d = px * e3[0] + py * e3[1] + pz * e3[2];
    const k0 = Math.sqrt((a / r1) ** 2 + (b / r2) ** 2 + (d / r3) ** 2);
    const k1 = Math.sqrt((a / (r1 * r1)) ** 2 + (b / (r2 * r2)) ** 2 + (d / (r3 * r3)) ** 2);
    return k1 < 1e-12 ? -rmin : (k0 * (k0 - 1)) / k1;
  };
  return { f, ...boxOf(c, [e1, e2, e3], radii) };
}

/** Tapered segment a->b with elliptic cross-section [radius along `side`, radius across]; rounded ends. */
function seg(a, b, ra, rb, side = [1, 0, 0]) {
  const ab = sub(b, a);
  const L = len(ab);
  const [e1, e2, e3] = frame(ab, side);
  const f = (x, y, z) => {
    const px = x - a[0], py = y - a[1], pz = z - a[2];
    const t = (px * e1[0] + py * e1[1] + pz * e1[2]) / L;
    const tc = t < 0 ? 0 : t > 1 ? 1 : t;
    const r2 = ra[0] + (rb[0] - ra[0]) * tc, r3 = ra[1] + (rb[1] - ra[1]) * tc;
    const u = px * e2[0] + py * e2[1] + pz * e2[2];
    const v = px * e3[0] + py * e3[1] + pz * e3[2];
    const m = r2 < r3 ? r2 : r3;
    const ax = (t - tc) * L;
    return (Math.sqrt((u / r2) ** 2 + (v / r3) ** 2 + (ax / m) ** 2) - 1) * m;
  };
  const r = Math.max(ra[0], ra[1], rb[0], rb[1]);
  const lo = [Math.min(a[0], b[0]) - r, Math.min(a[1], b[1]) - r, Math.min(a[2], b[2]) - r];
  const hi = [Math.max(a[0], b[0]) + r, Math.max(a[1], b[1]) + r, Math.max(a[2], b[2]) + r];
  return { f, lo, hi };
}

/** A smooth tube through stations [{p, r: [side, across]}], resampled with Catmull-Rom into short segments. */
function loft(stations, { n = 0, side = [1, 0, 0], k = 0.012 } = {}) {
  const N = n || (stations.length - 1) * 4;
  const at = (u) => ({
    p: [0, 1, 2].map((q) => cr(stations.map((s) => s.p[q]), u)),
    r: [0, 1].map((q) => Math.max(cr(stations.map((s) => s.r[q]), u), 0.002)),
  });
  const out = [];
  for (let i = 0; i < N; i++) {
    const s0 = at((i / N) * (stations.length - 1)), s1 = at(((i + 1) / N) * (stations.length - 1));
    out.push({ ...seg(s0.p, s1.p, s0.r, s1.r, side), k });
  }
  return out;
}

/** Hoof capsule: coronet at `top` (front height), toe forward, sloping wall, flat sole on y = 0. */
function hoofShape(top, { rx0 = 0.05, rz0 = 0.054, rx1 = 0.066, rz1 = 0.076, lean = 0.62, shrink = 0 } = {}) {
  const H = top[1];
  const f = (x, y, z) => {
    const t = clamp((H - y) / H, 0, 1);
    const cz = top[2] + (H - y) * lean * 0.55 - 0.012;
    const rx = rx0 + (rx1 - rx0) * t - shrink, rz = rz0 + (rz1 - rz0) * t - shrink;
    const dx = x - top[0], dz = z - cz;
    const side = (Math.sqrt((dx / rx) ** 2 + (dz / rz) ** 2) - 1) * Math.min(rx, rz);
    // coronet line slopes down from toe to heel
    const topPlane = y - (H - shrink) + (cz - z) * 0.45;
    return smax(smax(side, -y - shrink + 0.0, 0.006), topPlane, 0.012);
  };
  const r = Math.max(rx1, rz1) + 0.02;
  return { f, lo: [top[0] - r, -0.002, top[2] - r - 0.05], hi: [top[0] + r, H + 0.03, top[2] + r + 0.08] };
}

// ---------------------------------------------------------------------------------------------------------------
// sampled fields and surface nets
class Field {
  constructor(lo, hi, h, init = 1) {
    this.h = h;
    this.o = lo.map((v) => v - 2 * h);
    this.n = [0, 1, 2].map((k) => Math.ceil((hi[k] - lo[k]) / h) + 5);
    this.F = new Float32Array(this.n[0] * this.n[1] * this.n[2]).fill(init);
    this.best = null;
    this.lab = null;
  }
  withLabels() {
    this.best = new Float32Array(this.F.length).fill(1);
    this.lab = new Uint8Array(this.F.length);
    return this;
  }
  range(lo, hi, pad) {
    const { o, h, n } = this;
    const r = [];
    for (let k = 0; k < 3; k++) r.push([Math.max(0, Math.floor((lo[k] - pad - o[k]) / h)), Math.min(n[k] - 1, Math.ceil((hi[k] + pad - o[k]) / h))]);
    return r;
  }
  /** op: 'add' (smooth union), 'sub' (smooth subtraction), 'int' (smooth intersection) */
  apply(prim, { op = 'add', k = prim.k ?? 0.02, label = -1 } = {}) {
    const { o, h, n, F, best, lab } = this;
    const pad = Math.max(k, 0.01) + 2 * h;
    const [[i0, i1], [j0, j1], [k0, k1]] = this.range(prim.lo, prim.hi, pad);
    const nx = n[0], nxy = n[0] * n[1];
    for (let kk = k0; kk <= k1; kk++) {
      const z = o[2] + kk * h;
      for (let j = j0; j <= j1; j++) {
        const y = o[1] + j * h;
        let idx = kk * nxy + j * nx + i0;
        for (let i = i0; i <= i1; i++, idx++) {
          const d = prim.f(o[0] + i * h, y, z);
          if (op === 'add') {
            F[idx] = smin(F[idx], d, k);
            if (best && label >= 0 && d < best[idx]) {
              best[idx] = d;
              lab[idx] = label;
            }
          } else if (op === 'sub') F[idx] = smax(F[idx], -d, k);
          else F[idx] = smax(F[idx], d, k);
        }
      }
    }
  }
  /** set F from a function of position (x, y, z, old) over the whole grid */
  map(fn) {
    const { o, h, n, F } = this;
    let idx = 0;
    for (let kk = 0; kk < n[2]; kk++) for (let j = 0; j < n[1]; j++) for (let i = 0; i < n[0]; i++, idx++) F[idx] = fn(o[0] + i * h, o[1] + j * h, o[2] + kk * h, F[idx]);
  }
  sample(x, y, z) {
    const { o, h, n, F } = this;
    const fx = (x - o[0]) / h, fy = (y - o[1]) / h, fz = (z - o[2]) / h;
    if (fx < 0 || fy < 0 || fz < 0 || fx >= n[0] - 1 || fy >= n[1] - 1 || fz >= n[2] - 1) return 1;
    const i = Math.floor(fx), j = Math.floor(fy), k = Math.floor(fz);
    const tx = fx - i, ty = fy - j, tz = fz - k;
    const nx = n[0], nxy = n[0] * n[1];
    const b = k * nxy + j * nx + i;
    const c00 = F[b] * (1 - tx) + F[b + 1] * tx;
    const c10 = F[b + nx] * (1 - tx) + F[b + nx + 1] * tx;
    const c01 = F[b + nxy] * (1 - tx) + F[b + nxy + 1] * tx;
    const c11 = F[b + nxy + nx] * (1 - tx) + F[b + nxy + nx + 1] * tx;
    return (c00 * (1 - ty) + c10 * ty) * (1 - tz) + (c01 * (1 - ty) + c11 * ty) * tz;
  }
  grad(x, y, z) {
    const e = this.h * 0.5;
    return [this.sample(x + e, y, z) - this.sample(x - e, y, z), this.sample(x, y + e, z) - this.sample(x, y - e, z), this.sample(x, y, z + e) - this.sample(x, y, z - e)];
  }
  labelAt(p) {
    const { o, h, n, lab } = this;
    const i = clamp(Math.round((p[0] - o[0]) / h), 0, n[0] - 1), j = clamp(Math.round((p[1] - o[1]) / h), 0, n[1] - 1), k = clamp(Math.round((p[2] - o[2]) / h), 0, n[2] - 1);
    return lab[k * n[0] * n[1] + j * n[0] + i];
  }
}

/** Surface nets: one vertex per sign-changing cell, projected onto the surface; returns {pos, nor, tri}. */
function polygonize(field) {
  const { o, h, n, F } = field;
  const [nx, ny, nz] = n;
  const nxy = nx * ny;
  const cellIdx = new Int32Array(nx * ny * nz).fill(-1);
  const pos = [], nor = [], tri = [];
  const corner = [[0, 0, 0], [1, 0, 0], [0, 1, 0], [1, 1, 0], [0, 0, 1], [1, 0, 1], [0, 1, 1], [1, 1, 1]];
  const edges = [[0, 1], [2, 3], [4, 5], [6, 7], [0, 2], [1, 3], [4, 6], [5, 7], [0, 4], [1, 5], [2, 6], [3, 7]];
  const offs = corner.map(([a, b, c]) => a + b * nx + c * nxy);
  const v = new Float64Array(8);
  for (let k = 0; k < nz - 1; k++) {
    for (let j = 0; j < ny - 1; j++) {
      for (let i = 0; i < nx - 1; i++) {
        const base = k * nxy + j * nx + i;
        let mask = 0;
        for (let c = 0; c < 8; c++) {
          v[c] = F[base + offs[c]];
          if (v[c] < 0) mask |= 1 << c;
        }
        if (mask === 0 || mask === 255) continue;
        let sx = 0, sy = 0, sz = 0, cnt = 0;
        for (const [a, b] of edges) {
          if ((v[a] < 0) === (v[b] < 0)) continue;
          const t = v[a] / (v[a] - v[b]);
          sx += corner[a][0] + (corner[b][0] - corner[a][0]) * t;
          sy += corner[a][1] + (corner[b][1] - corner[a][1]) * t;
          sz += corner[a][2] + (corner[b][2] - corner[a][2]) * t;
          cnt++;
        }
        let p = [o[0] + (i + sx / cnt) * h, o[1] + (j + sy / cnt) * h, o[2] + (k + sz / cnt) * h];
        for (let it = 0; it < 2; it++) {
          const f = field.sample(...p);
          const g = field.grad(...p);
          const gg = dot(g, g);
          if (gg < 1e-14) break;
          // grad() is a difference over one cell, i.e. h times the gradient, hence the factor h
          const step = mul(g, -(f * h) / gg);
          const sl = len(step);
          p = add(p, sl > h * 0.6 ? mul(step, (h * 0.6) / sl) : step);
        }
        cellIdx[base] = pos.length / 3;
        pos.push(p[0], p[1], p[2]);
        const g = norm(field.grad(...p));
        nor.push(g[0], g[1], g[2]);
      }
    }
  }
  // quads around every sign-changing grid edge
  const axes = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
  for (let k = 1; k < nz - 1; k++) {
    for (let j = 1; j < ny - 1; j++) {
      for (let i = 1; i < nx - 1; i++) {
        const base = k * nxy + j * nx + i;
        const inside = F[base] < 0;
        for (let a = 0; a < 3; a++) {
          const step = axes[a][0] + axes[a][1] * nx + axes[a][2] * nxy;
          if (inside === F[base + step] < 0) continue;
          const b = (a + 1) % 3, c = (a + 2) % 3;
          const db = axes[b][0] + axes[b][1] * nx + axes[b][2] * nxy, dc = axes[c][0] + axes[c][1] * nx + axes[c][2] * nxy;
          const q = [cellIdx[base - db - dc], cellIdx[base - dc], cellIdx[base], cellIdx[base - db]];
          if (q.some((x) => x < 0)) continue;
          if (inside) tri.push(q[0], q[1], q[2], q[0], q[2], q[3]);
          else tri.push(q[0], q[2], q[1], q[0], q[3], q[2]);
        }
      }
    }
  }
  return { pos: new Float32Array(pos), nor: new Float32Array(nor), tri: new Uint32Array(tri) };
}

// ---------------------------------------------------------------------------------------------------------------
// materials, regions and collected parts
const MATERIALS = {
  coat: { color: [1, 1, 1, 1], rough: 0.62 },
  hoof: { color: [0.09, 0.075, 0.062, 1], rough: 0.4 },
  mane: { color: [0.02, 0.017, 0.014, 1], rough: 0.85 },
  eye: { color: [0.008, 0.006, 0.006, 1], rough: 0.08 },
  muscle: { color: [1, 1, 1, 1], rough: 0.45 },
  muscleDeep: { color: [1, 1, 1, 1], rough: 0.55 },
  tendon: { color: [1, 1, 1, 1], rough: 0.38 },
  bone: { color: [1, 0.94, 0.8, 1], rough: 0.55 },
};
const matNames = Object.keys(MATERIALS);

const TRUNK = ['HEAD', 'NECK', 'WITHERS', 'L_SHOULDER', 'R_SHOULDER', 'CHEST', 'BACK', 'LOIN', 'BELLY', 'CROUP', 'L_HIP', 'R_HIP', 'TAIL_BASE'];
const FORE = ['ELBOW', 'FOREARM', 'CARPUS', 'CANNON', 'FETLOCK', 'PASTERN', 'HOOF'];
const HIND = ['STIFLE', 'GASKIN', 'HOCK', 'CANNON', 'FETLOCK', 'PASTERN', 'HOOF'];
const LIMBS = [['LF', FORE, 1], ['RF', FORE, -1], ['LH', HIND, 1], ['RH', HIND, -1]];
export const CODES = [...TRUNK, ...LIMBS.flatMap(([pre, names]) => names.map((p) => `${pre}_${p}`))];

class Mesh {
  constructor() {
    this.pos = [];
    this.nor = [];
    this.col = null;
    this.idx = [];
  }
  get empty() {
    return this.idx.length === 0;
  }
}
const parts = { skin: {}, muscle: {}, skeleton: {} };
const put = (layer, code, mesh, mat) => {
  if (!mesh || mesh.empty) return;
  if (!CODES.includes(code)) throw new Error(`unknown region ${code}`);
  (parts[layer][code] ||= []).push({ mesh, mat });
};

// ---------------------------------------------------------------------------------------------------------------
// mesh simplification: quadric error metric with half-edge collapses (vertices keep their exact surface normal and
// colour); open borders are locked so neighbouring region meshes still meet without cracks
class Heap {
  constructor() {
    this.a = [];
  }
  push(x) {
    const a = this.a;
    a.push(x);
    let i = a.length - 1;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (a[p][0] <= a[i][0]) break;
      [a[p], a[i]] = [a[i], a[p]];
      i = p;
    }
  }
  pop() {
    const a = this.a;
    const top = a[0], last = a.pop();
    if (a.length) {
      a[0] = last;
      let i = 0;
      for (;;) {
        const l = 2 * i + 1, r = l + 1;
        let m = i;
        if (l < a.length && a[l][0] < a[m][0]) m = l;
        if (r < a.length && a[r][0] < a[m][0]) m = r;
        if (m === i) break;
        [a[m], a[i]] = [a[i], a[m]];
        i = m;
      }
    }
    return top;
  }
  get size() {
    return this.a.length;
  }
}
function simplify(mesh, ratio) {
  const nV = mesh.pos.length / 3, nF = mesh.idx.length / 3;
  const target = Math.floor(nF * ratio);
  if (nF < 60 || ratio >= 1) return mesh;
  const P = mesh.pos, T = Int32Array.from(mesh.idx);
  const alive = new Uint8Array(nF).fill(1);
  const vf = Array.from({ length: nV }, () => []);
  for (let f = 0; f < nF; f++) for (let k = 0; k < 3; k++) vf[T[f * 3 + k]].push(f);
  const edgeCount = new Map();
  for (let f = 0; f < nF; f++) {
    for (let k = 0; k < 3; k++) {
      const a = T[f * 3 + k], b = T[f * 3 + ((k + 1) % 3)];
      const key = a < b ? a * nV + b : b * nV + a;
      edgeCount.set(key, (edgeCount.get(key) ?? 0) + 1);
    }
  }
  const locked = new Uint8Array(nV);
  for (const [key, c] of edgeCount) if (c !== 2) {
    locked[Math.floor(key / nV)] = 1;
    locked[key % nV] = 1;
  }
  const Q = new Float64Array(nV * 10);
  const fnorm = (f, override = -1, w = -1) => {
    const ids = [T[f * 3], T[f * 3 + 1], T[f * 3 + 2]].map((v) => (v === override ? w : v));
    const p = ids.map((v) => [P[v * 3], P[v * 3 + 1], P[v * 3 + 2]]);
    return cross(sub(p[1], p[0]), sub(p[2], p[0]));
  };
  for (let f = 0; f < nF; f++) {
    const n = fnorm(f);
    const area = len(n);
    if (area < 1e-14) continue;
    const [a, b, c] = mul(n, 1 / area);
    const v0 = T[f * 3];
    const d = -(a * P[v0 * 3] + b * P[v0 * 3 + 1] + c * P[v0 * 3 + 2]);
    const q = [a * a, a * b, a * c, a * d, b * b, b * c, b * d, c * c, c * d, d * d];
    for (let k = 0; k < 3; k++) {
      const v = T[f * 3 + k];
      for (let i = 0; i < 10; i++) Q[v * 10 + i] += q[i] * area;
    }
  }
  const err = (u, v) => {
    const x = P[v * 3], y = P[v * 3 + 1], z = P[v * 3 + 2];
    const q = (i) => Q[u * 10 + i] + Q[v * 10 + i];
    return q(0) * x * x + 2 * q(1) * x * y + 2 * q(2) * x * z + 2 * q(3) * x + q(4) * y * y + 2 * q(5) * y * z + 2 * q(6) * y + q(7) * z * z + 2 * q(8) * z + q(9);
  };
  const stamp = new Int32Array(nV);
  const dead = new Uint8Array(nV);
  const heap = new Heap();
  const pushEdge = (a, b) => {
    if (!locked[a]) heap.push([err(a, b), a, b, stamp[a], stamp[b]]);
    if (!locked[b]) heap.push([err(b, a), b, a, stamp[b], stamp[a]]);
  };
  for (const key of edgeCount.keys()) pushEdge(Math.floor(key / nV), key % nV);
  const nbrs = (v) => {
    const s = new Set();
    for (const f of vf[v]) if (alive[f]) for (let k = 0; k < 3; k++) s.add(T[f * 3 + k]);
    s.delete(v);
    return s;
  };
  let faces = nF;
  while (faces > target && heap.size) {
    const [, u, v, su, sv] = heap.pop();
    if (dead[u] || dead[v] || su !== stamp[u] || sv !== stamp[v]) continue;
    const nu = nbrs(u), nvv = nbrs(v);
    if (!nu.has(v)) continue;
    let common = 0;
    for (const w of nu) if (nvv.has(w)) common++;
    if (common !== 2) continue;
    // reject flips and slivers
    let ok = true;
    for (const f of vf[u]) {
      if (!alive[f]) continue;
      if (T[f * 3] === v || T[f * 3 + 1] === v || T[f * 3 + 2] === v) continue;
      const before = fnorm(f), after = fnorm(f, u, v);
      const lb = len(before), la = len(after);
      if (la < 1e-12 || dot(before, after) / (lb * la) < 0.4) {
        ok = false;
        break;
      }
    }
    if (!ok) continue;
    for (const f of vf[u]) {
      if (!alive[f]) continue;
      if (T[f * 3] === v || T[f * 3 + 1] === v || T[f * 3 + 2] === v) {
        alive[f] = 0;
        faces--;
        continue;
      }
      for (let k = 0; k < 3; k++) if (T[f * 3 + k] === u) T[f * 3 + k] = v;
      vf[v].push(f);
    }
    dead[u] = 1;
    for (let i = 0; i < 10; i++) Q[v * 10 + i] += Q[u * 10 + i];
    stamp[v]++;
    for (const w of nbrs(v)) {
      stamp[w]++;
      pushEdge(v, w);
    }
  }
  const out = new Mesh();
  const map = new Int32Array(nV).fill(-1);
  for (let f = 0; f < nF; f++) {
    if (!alive[f]) continue;
    for (let k = 0; k < 3; k++) {
      const v = T[f * 3 + k];
      if (map[v] < 0) {
        map[v] = out.pos.length / 3;
        out.pos.push(P[v * 3], P[v * 3 + 1], P[v * 3 + 2]);
        out.nor.push(mesh.nor[v * 3], mesh.nor[v * 3 + 1], mesh.nor[v * 3 + 2]);
        if (mesh.col) (out.col ||= []).push(mesh.col[v * 3], mesh.col[v * 3 + 1], mesh.col[v * 3 + 2]);
      }
      out.idx.push(map[v]);
    }
  }
  return out;
}

/** Split a polygonized surface into meshes per (region, material); colour(p, n, region) gives a linear RGB colour. */
const RATIO = process.env.NO_SIMPLIFY ? { skin: 1, muscle: 1, skeleton: 1 } : { skin: 0.3, muscle: 0.22, skeleton: 0.22 };
function emit(layer, surf, classify, colour, ratio = RATIO[layer]) {
  const groups = new Map();
  const { pos, nor, tri } = surf;
  for (let t = 0; t < tri.length; t += 3) {
    const a = tri[t], b = tri[t + 1], c = tri[t + 2];
    const cen = [0, 1, 2].map((q) => (pos[a * 3 + q] + pos[b * 3 + q] + pos[c * 3 + q]) / 3);
    const res = classify(cen);
    if (!res) continue;
    const key = `${res.code}|${res.mat}`;
    if (!groups.has(key)) groups.set(key, { ...res, tris: [] });
    groups.get(key).tris.push(a, b, c);
  }
  for (const { code, mat, tris } of [...groups.values()].sort((x, y) => (x.code + x.mat < y.code + y.mat ? -1 : 1))) {
    const m = new Mesh();
    if (colour) m.col = [];
    const map = new Map();
    for (const v of tris) {
      if (!map.has(v)) {
        map.set(v, m.pos.length / 3);
        const p = [pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]], nn = [nor[v * 3], nor[v * 3 + 1], nor[v * 3 + 2]];
        m.pos.push(...p);
        m.nor.push(...nn);
        if (colour) m.col.push(...colour(p, nn, code, mat));
      }
      m.idx.push(map.get(v));
    }
    put(layer, code, simplify(m, ratio), mat);
  }
}

function surfaceOf(prims, h, opts = {}) {
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
  for (const p of prims) for (let k = 0; k < 3; k++) {
    lo[k] = Math.min(lo[k], p.prim.lo[k]);
    hi[k] = Math.max(hi[k], p.prim.hi[k]);
  }
  const fld = new Field(opts.lo ?? lo, opts.hi ?? hi, h);
  if (opts.labels) fld.withLabels();
  for (const p of prims) fld.apply(p.prim, p);
  return fld;
}

// ---------------------------------------------------------------------------------------------------------------
// anatomy: landmarks of the left side (the right side mirrors them)
const FORE_J = {
  shoulder: [0.17, 1.24, 0.64], elbow: [0.165, 0.985, 0.465], olecranon: [0.15, 1.03, 0.37],
  knee: [0.155, 0.535, 0.5], fetlock: [0.152, 0.22, 0.495], coronet: [0.15, 0.094, 0.585],
};
const HIND_J = {
  coxae: [0.215, 1.43, -0.42], hip: [0.17, 1.2, -0.7], ischium: [0.09, 1.26, -0.96], stifle: [0.19, 0.95, -0.455],
  hock: [0.15, 0.56, -0.8], calcaneus: [0.15, 0.63, -0.875], fetlock: [0.148, 0.22, -0.77], coronet: [0.146, 0.094, -0.7],
};

// the head frame: poll P, axis a towards the muzzle, d towards the face (dorsal), lateral x
const POLL = [0, 1.93, 1.12];
const MUZZLE = [0, 1.4, 1.5];
const HA = norm(sub(MUZZLE, POLL));
const HD = [0, HA[2], -HA[1]]; // a turned by +90 degrees in the y-z plane: towards the forehead
const H = (s, v, x = 0) => add(add(add(POLL, mul(HA, s)), mul(HD, v)), [x, 0, 0]);
const hell = (s, v, x, radii) => ell(H(s, v, x), radii, HA, HD); // radii [along a, along d, lateral]
const headS = (p) => dot(sub(p, POLL), HA);
const headV = (p) => dot(sub(p, POLL), HD);

// ---------------------------------------------------------------------------------------------------------------
// SKIN
const L = { HEAD: 1, NECK: 2, TORSO: 3, TAIL: 4, EAR: 5, LF: 10, RF: 11, LH: 12, RH: 13, HOOF_LF: 20, HOOF_RF: 21, HOOF_LH: 22, HOOF_RH: 23 };
const skinPrims = [];
const S = (prim, label, k = 0.06, op = 'add') => skinPrims.push({ prim, label, k, op });

// trunk
S(ell([0, 1.2, 0.02], [0.56, 0.315, 0.255]), L.TORSO, 0.1); // barrel
S(ell([0, 1.16, 0.46], [0.24, 0.285, 0.2]), L.TORSO, 0.12); // forehand
S(ell([0, 1.1, 0.6], [0.13, 0.19, 0.15]), L.TORSO, 0.1); // breast
S(seg([0, 1.5, 0.08], [0, 1.6, 0.38], [0.06, 0.07], [0.055, 0.075]), L.TORSO, 0.12); // withers
S(ell([0, 1.43, -0.28], [0.36, 0.13, 0.2]), L.TORSO, 0.12); // loin
S(ell([0, 1.33, -0.63], [0.36, 0.28, 0.235]), L.TORSO, 0.12); // hindquarters
S(ell([0, 1.5, -0.9], [0.12, 0.08, 0.1]), L.TORSO, 0.1); // tail head
for (const sx of [1, -1]) {
  const m = (p) => (sx > 0 ? p : mirror(p));
  S(ell(m([0.115, 1.38, 0.43]), [0.25, 0.12, 0.06], norm(sub(m(FORE_J.shoulder), m([0.1, 1.55, 0.3]))), [sx, 0, 0]), L.TORSO, 0.13); // shoulder blade
  S(ell(m([0.14, 1.12, 0.5]), [0.15, 0.17, 0.1]), L.TORSO, 0.09); // triceps mass
  S(ell(m([0.16, 1.23, 0.62]), [0.08, 0.08, 0.06]), L.TORSO, 0.06); // point of shoulder
  S(ell(m([0.19, 1.41, -0.43]), [0.06, 0.05, 0.05]), L.TORSO, 0.07); // point of hip
  S(ell(m([0.14, 1.12, -0.66]), [0.27, 0.27, 0.13]), L.TORSO, 0.1); // thigh
  S(ell(m([0.095, 1.06, -0.86]), [0.12, 0.25, 0.1]), L.TORSO, 0.08); // hamstrings
  // the buttock rounds over into the hamstring line that runs down to the point of the hock
  S(seg(m([0.07, 1.36, -0.93]), m([0.1, 1.05, -0.92]), [0.1, 0.12], [0.09, 0.11]), L.TORSO, 0.1);
  S(seg(m([0.1, 1.05, -0.9]), m([0.13, 0.9, -0.84]), [0.085, 0.1], [0.065, 0.075]), L.TORSO, 0.08);
  S(seg(m([0.13, 0.93, -0.83]), m([0.148, 0.72, -0.82]), [0.06, 0.07], [0.034, 0.04]), sx > 0 ? L.LH : L.RH, 0.1);
  S(ell(m([0.14, 1.04, -0.5]), [0.12, 0.13, 0.09]), L.TORSO, 0.08); // stifle fold
}

// neck and crest
const NECKLINE = [
  { p: [0, 1.32, 0.56], r: [0.2, 0.29] },
  { p: [0, 1.47, 0.73], r: [0.155, 0.235] },
  { p: [0, 1.61, 0.865], r: [0.118, 0.185] },
  { p: [0, 1.735, 0.975], r: [0.093, 0.148] },
  { p: [0, 1.83, 1.06], r: [0.082, 0.128] },
];
for (const p of loft(NECKLINE, { k: 0.03 })) S(p, L.NECK, 0.06);
for (const p of loft([
  { p: [0, 1.58, 0.42], r: [0.05, 0.05] },
  { p: [0, 1.72, 0.66], r: [0.05, 0.05] },
  { p: [0, 1.83, 0.86], r: [0.045, 0.045] },
  { p: [0, 1.9, 1.0], r: [0.04, 0.04] },
  { p: [0, 1.92, 1.07], r: [0.04, 0.04] },
], { k: 0.03 })) S(p, L.NECK, 0.07);

// head
// the profile is a long, nearly straight line from a flat forehead down the nasal ridge to the muzzle
S(hell(0.06, -0.02, 0, [0.11, 0.08, 0.09]), L.HEAD, 0.05); // cranium
S(hell(0.19, -0.02, 0, [0.13, 0.066, 0.1]), L.HEAD, 0.06); // flat forehead
S(hell(0.16, -0.145, 0, [0.125, 0.122, 0.096]), L.HEAD, 0.07); // jowls
S(hell(0.38, -0.04, 0, [0.17, 0.078, 0.07]), L.HEAD, 0.07); // face
S(hell(0.53, -0.05, 0, [0.11, 0.07, 0.058]), L.HEAD, 0.05); // nose
S(seg(H(0.12, 0.0), H(0.58, -0.012), [0.05, 0.04], [0.034, 0.03]), L.HEAD, 0.05); // nasal ridge: the straight line
S(hell(0.62, -0.058, 0, [0.064, 0.07, 0.058]), L.HEAD, 0.05); // muzzle
S(hell(0.655, -0.082, 0, [0.03, 0.048, 0.054]), L.HEAD, 0.03); // upper lip
S(hell(0.62, -0.128, 0, [0.05, 0.032, 0.043]), L.HEAD, 0.03); // chin
S(seg(H(0.17, -0.215), H(0.59, -0.135), [0.06, 0.04], [0.04, 0.03]), L.HEAD, 0.05); // jaw line
for (const sx of [1, -1]) {
  S(hell(0.15, 0.035, sx * 0.07, [0.04, 0.022, 0.03]), L.HEAD, 0.03); // brow over the eye
  S(hell(0.33, -0.045, sx * 0.066, [0.1, 0.012, 0.012]), L.HEAD, 0.025); // facial crest (cheekbone)
  S(hell(0.6, -0.003, sx * 0.046, [0.03, 0.018, 0.016]), L.HEAD, 0.02); // nostril wing
  S(hell(0.632, -0.01, sx * 0.042, [0.036, 0.018, 0.014]), L.HEAD, 0.008, 'sub'); // nostril
  // ears: flattened cones, hollow at the front
  const base = H(0.025, 0.055, sx * 0.05);
  const dir = norm([sx * 0.32, 1, 0.12]);
  for (const p of loft([
    { p: base, r: [0.03, 0.022] },
    { p: add(base, mul(dir, 0.06)), r: [0.031, 0.02] },
    { p: add(base, mul(dir, 0.1)), r: [0.018, 0.012] },
    { p: add(base, mul(dir, 0.135)), r: [0.003, 0.003] },
  ], { side: [1, 0, 0], k: 0.01 })) S(p, L.EAR, 0.03);
  S(ell(add(add(base, mul(dir, 0.08)), [0, 0, 0.018]), [0.06, 0.012, 0.018], dir, [0, 0, 1]), L.EAR, 0.008, 'sub');
}
S(hell(0.64, -0.1, 0, [0.065, 0.006, 0.08]), L.HEAD, 0.004, 'sub'); // mouth

// legs
function legSkin(J, sx, fore) {
  const m = (p) => (sx > 0 ? p : mirror(p));
  const st = fore
    ? [
        [[0.165, 1.03, 0.44], [0.078, 0.1]],
        [[0.162, 0.9, 0.47], [0.07, 0.088]],
        [[0.16, 0.76, 0.484], [0.057, 0.072]],
        [[0.157, 0.625, 0.495], [0.044, 0.054]],
        [[0.155, 0.54, 0.498], [0.054, 0.058]],
        [[0.154, 0.475, 0.496], [0.036, 0.046]],
        [[0.153, 0.36, 0.49], [0.032, 0.043]],
        [[0.152, 0.27, 0.488], [0.034, 0.046]],
        [[0.152, 0.215, 0.492], [0.045, 0.055]],
        [[0.151, 0.16, 0.522], [0.037, 0.042]],
        [[0.15, 0.1, 0.565], [0.037, 0.04]],
      ]
    : [
        [[0.172, 1.0, -0.56], [0.1, 0.16]],
        [[0.165, 0.88, -0.61], [0.077, 0.13]],
        [[0.16, 0.76, -0.69], [0.058, 0.1]],
        [[0.155, 0.65, -0.765], [0.045, 0.074]],
        [[0.152, 0.56, -0.8], [0.05, 0.078]],
        [[0.15, 0.475, -0.795], [0.036, 0.048]],
        [[0.149, 0.36, -0.785], [0.033, 0.046]],
        [[0.148, 0.27, -0.776], [0.034, 0.047]],
        [[0.148, 0.215, -0.772], [0.045, 0.056]],
        [[0.147, 0.16, -0.745], [0.037, 0.042]],
        [[0.146, 0.1, -0.71], [0.037, 0.04]],
      ];
  const lab = fore ? (sx > 0 ? L.LF : L.RF) : sx > 0 ? L.LH : L.RH;
  for (const p of loft(st.map(([p, r]) => ({ p: m(p), r })), { n: 40, k: 0.012 })) S(p, lab, 0.05);
  // ergot behind the fetlock, knee and hock details
  S(ell(m([J.fetlock[0], 0.2, J.fetlock[2] - 0.045]), [0.025, 0.02, 0.02]), lab, 0.02);
  if (fore) {
    S(ell(m([0.152, 0.55, 0.455]), [0.03, 0.02, 0.018]), lab, 0.02); // accessory carpal
  } else {
    S(seg(m([0.152, 0.84, -0.73]), m(J.calcaneus), [0.022, 0.03], [0.022, 0.026]), lab, 0.03); // achilles
    S(ell(m(J.calcaneus), [0.03, 0.03, 0.025]), lab, 0.02);
    S(ell(m([0.195, 0.955, -0.45]), [0.07, 0.06, 0.05]), lab, 0.05); // stifle
  }
  const hl = fore ? (sx > 0 ? L.HOOF_LF : L.HOOF_RF) : sx > 0 ? L.HOOF_LH : L.HOOF_RH;
  S(hoofShape(m(J.coronet)), hl, 0.012);
}
for (const sx of [1, -1]) {
  legSkin(FORE_J, sx, true);
  legSkin(HIND_J, sx, false);
}
// tail dock
for (const p of loft([
  { p: [0, 1.5, -0.93], r: [0.06, 0.055] },
  { p: [0, 1.49, -1.03], r: [0.045, 0.042] },
  { p: [0, 1.4, -1.1], r: [0.035, 0.034] },
  { p: [0, 1.3, -1.12], r: [0.026, 0.026] },
], { k: 0.02 })) S(p, L.TAIL, 0.05);

console.time('skin');
const skin = surfaceOf(skinPrims, 0.0095, { labels: true });
console.timeEnd('skin');

// regions of the trunk from position (the dominant primitive gives head, neck, tail or a limb)
function torsoRegion(p) {
  const [x, y, z] = p;
  const side = (n) => (x >= 0 ? 'L_' : 'R_') + n;
  if (z > 0.56) return y > 1.5 ? 'WITHERS' : 'CHEST';
  if (z > 0.15) {
    if (y > 1.46) return 'WITHERS';
    if (y > 1.02 && Math.abs(x) > 0.07) return side('SHOULDER');
    return y < 1.02 ? 'CHEST' : 'WITHERS';
  }
  if (z > -0.24) return y > 1.34 ? 'BACK' : 'BELLY';
  if (z > -0.46) {
    if (y > 1.38) return 'LOIN';
    return y > 1.05 && Math.abs(x) > 0.16 ? side('HIP') : 'BELLY';
  }
  if (y > 1.46 && Math.abs(x) < 0.13) return 'CROUP';
  if (z < -0.9 && y > 1.36) return 'CROUP';
  return y > 0.86 ? side('HIP') : 'BELLY';
}
const LIMB_OF = { [L.LF]: 'LF', [L.RF]: 'RF', [L.LH]: 'LH', [L.RH]: 'RH' };
const HOOF_OF = { [L.HOOF_LF]: 'LF', [L.HOOF_RF]: 'RF', [L.HOOF_LH]: 'LH', [L.HOOF_RH]: 'RH' };
function limbRegion(pre, y) {
  const fore = pre[1] === 'F';
  const bands = fore
    ? [[0.86, 'ELBOW'], [0.6, 'FOREARM'], [0.47, 'CARPUS'], [0.265, 'CANNON'], [0.165, 'FETLOCK'], [-1, 'PASTERN']]
    : [[0.84, 'STIFLE'], [0.62, 'GASKIN'], [0.47, 'HOCK'], [0.265, 'CANNON'], [0.165, 'FETLOCK'], [-1, 'PASTERN']];
  for (const [lim, name] of bands) if (y > lim) return `${pre}_${name}`;
  return `${pre}_PASTERN`;
}
function regionAt(p, label) {
  if (label === L.HEAD || label === L.EAR) return 'HEAD';
  if (label === L.NECK) return p[2] < 0.5 && p[1] > 1.5 ? 'WITHERS' : 'NECK';
  if (label === L.TAIL) return 'TAIL_BASE';
  if (HOOF_OF[label]) return `${HOOF_OF[label]}_HOOF`;
  if (LIMB_OF[label]) {
    const pre = LIMB_OF[label];
    if (pre[1] === 'H' && p[1] > 0.84 && p[2] < -0.62) return (p[0] >= 0 ? 'L_' : 'R_') + 'HIP';
    return limbRegion(pre, p[1]);
  }
  return torsoRegion(p);
}

// bay coat: red-brown body, black points below knees and hocks, dark muzzle, darker topline and lighter belly
// linear colours (the app shows roughly sRGB(0.5 x colour x light)): a rich bay
const BAY = [0.2, 0.066, 0.028], BLACK = [0.018, 0.014, 0.011], MUZZLE_C = [0.07, 0.04, 0.03], BELLY_C = [0.3, 0.12, 0.05];
function coatColour(p, n, code, mat) {
  if (mat === 'hoof') return [1, 1, 1];
  let c = BAY.slice();
  const shade = 0.88 + 0.12 * Math.sin(p[2] * 9 + p[1] * 4) * Math.sin(p[1] * 6.5);
  c = mul(c, shade);
  const belly = smooth(0.2, -0.8, n[1]) * smooth(1.25, 0.95, p[1]);
  c = lerp3(c, BELLY_C, belly * 0.5);
  const top = smooth(0.6, 1, n[1]) * smooth(1.4, 1.6, p[1]);
  c = lerp3(c, mul(BAY, 0.7), top * 0.6);
  const front = p[2] > 0;
  const pointsTop = front ? 0.57 : 0.6;
  const k = smooth(pointsTop + 0.06, pointsTop - 0.07, p[1]);
  c = lerp3(c, BLACK, k);
  if (code === 'HEAD') {
    const s = headS(p);
    c = lerp3(c, MUZZLE_C, smooth(0.5, 0.61, s));
    // ear rims darker
    if (p[1] > POLL[1] + 0.03) c = lerp3(c, BLACK, smooth(POLL[1] + 0.05, POLL[1] + 0.15, p[1]));
  }
  return c;
}

emit('skin', polygonize(skin), (c) => {
  const lab = skin.labelAt(c);
  const code = regionAt(c, lab);
  return { code, mat: HOOF_OF[lab] ? 'hoof' : 'coat' };
}, coatColour);

/** y of the top of the skin on the midline at z */
function skinTop(z) {
  let y = 2.1;
  while (y > 0.8 && skin.sample(0, y, z) > 0) y -= 0.002;
  return y;
}
/** x of the skin surface on the left side at (y, z), searching inwards from outside */
function skinX(y, z, from = 0.4) {
  let x = from;
  while (x > 0 && skin.sample(x, y, z) > 0) x -= 0.002;
  return x;
}

// eyes
for (const sx of [1, -1]) {
  const c0 = H(0.175, 0.03);
  const sxv = skinX(c0[1], c0[2]);
  const e = ell([sx * (sxv - 0.006), c0[1], c0[2]], [0.03, 0.022, 0.016], norm([0, -0.2, 1]), [0, 1, 0]);
  const fld = surfaceOf([{ prim: e, k: 0 }], 0.003);
  emit('skin', polygonize(fld), () => ({ code: 'HEAD', mat: 'eye' }), null);
}

// mane, forelock and tail hair: strand ridges on soft volumes
function hair(prims, h, code, strands) {
  const fld = surfaceOf(prims, h);
  if (strands) fld.map((x, y, z, d) => (d < 0.03 ? d + strands(x, y, z) : d));
  emit('skin', polygonize(fld), () => ({ code, mat: 'mane' }), null);
}
{
  // the mane rises from the crest and falls over the left side of the neck as a layer of hair
  const mane = [];
  const zs = [0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.98, 1.05];
  for (let i = 0; i < zs.length - 1; i++) {
    const at = (z, j) => {
      const yt = skinTop(z);
      const len = 0.15 + 0.035 * Math.sin(j * 1.9) + (z < 0.5 ? -0.05 : 0);
      const yl = yt - 0.02 - len;
      return { top: [0.006, yt + 0.008, z], low: [skinX(yl, z - 0.03) + 0.012, yl, z - 0.03], len };
    };
    const a = at(zs[i], i), b = at(zs[i + 1], i + 1);
    mane.push({ prim: seg(a.top, b.top, [0.016, 0.022], [0.016, 0.022]), k: 0.03 });
    const am = [skinX(a.top[1] - 0.06, a.top[2]) * 0.6 + 0.02, a.top[1] - 0.05, a.top[2] - 0.015];
    const bm = [skinX(b.top[1] - 0.06, b.top[2]) * 0.6 + 0.02, b.top[1] - 0.05, b.top[2] - 0.015];
    mane.push({ prim: seg(am, bm, [0.02, 0.035], [0.02, 0.035], [1, 0.5, 0]), k: 0.04 });
    mane.push({ prim: seg(lerp3(am, a.low, 0.55), lerp3(bm, b.low, 0.55), [0.014, a.len * 0.32], [0.014, b.len * 0.32], [1, 0, 0]), k: 0.05 });
  }
  hair(mane, 0.0055, 'NECK', (x, y, z) => 0.003 * Math.sin(z * 300 + Math.sin(y * 30) * 2.5));
  const fl = [{ prim: ell(H(0.07, 0.068, 0), [0.1, 0.018, 0.035], HA, HD), k: 0 }];
  hair(fl, 0.004, 'HEAD', (x, y, z) => 0.003 * Math.sin(x * 300));
  const tail = loft([
    { p: [0, 1.5, -0.99], r: [0.055, 0.045] },
    { p: [0, 1.4, -1.1], r: [0.075, 0.065] },
    { p: [0, 1.18, -1.16], r: [0.1, 0.08] },
    { p: [0, 0.92, -1.16], r: [0.095, 0.075] },
    { p: [0, 0.68, -1.12], r: [0.065, 0.05] },
    { p: [0, 0.54, -1.09], r: [0.02, 0.02] },
  ], { k: 0.04 }).map((prim) => ({ prim, k: 0.04 }));
  hair(tail, 0.006, 'TAIL_BASE', (x, y, z) => 0.006 * Math.sin(Math.atan2(x, z + 1.12) * 22 + y * 3));
}

// ---------------------------------------------------------------------------------------------------------------
// MUSCLE layer: a deep layer under the skin, named superficial muscles over it, tendons in the lower legs
const MUSCLE_C = [0.28, 0.035, 0.03], DEEP_C = [0.16, 0.022, 0.018], TENDON_C = [0.8, 0.78, 0.74], FASCIA_C = [0.5, 0.27, 0.24];
const LEG_FASCIA_C = [0.3, 0.17, 0.15];
const lowLeg = (p) => smooth(0.66, 0.5, p[1]);
{
  // deep layer: the skin surface pushed inwards (less in the thin lower legs and the head)
  const deep = new Field(skin.o.map((v, k) => v + 2 * skin.h), skin.o.map((v, k) => v + (skin.n[k] - 3) * skin.h), skin.h);
  deep.map((x, y, z) => {
    const thin = Math.max(smooth(0.66, 0.5, y), smooth(0.98, 1.1, z) * smooth(1.25, 1.35, y));
    return skin.sample(x, y, z) + lerp(0.024, 0.007, thin);
  });
  emit('muscle', polygonize(deep), (c) => {
    const lab = skin.labelAt(c);
    return { code: regionAt(c, lab), mat: HOOF_OF[lab] ? 'hoof' : lowLeg(c) > 0.5 ? 'tendon' : 'muscleDeep' };
  }, (p, n, code, mat) => (mat === 'hoof' ? [1, 1, 1] : lerp3(lerp3(DEEP_C, FASCIA_C, code === 'HEAD' ? 0.12 : 0), LEG_FASCIA_C, lowLeg(p))));
}

/**
 * A superficial muscle: blob = smooth union of shapes; its outer face follows the skin inset by 3 mm at the centre
 * and 16 mm at the border, so neighbouring muscles meet in a crease. Fibres run along `fib`; `ends` = [t0, t1]
 * along fib marks where the belly turns into tendon.
 */
const muscleList = [];
function muscle(name, code, shapes, fib, { ends = null, depth = 0.018, tint = 1, h = 0.0065, tendon = false } = {}) {
  muscleList.push({ name, code, shapes, fib: norm(fib), ends, depth, tint, h, tendon });
}
const GROW = 0.05; // how far a muscle may grow beyond its outline to meet its neighbours
const outline = (M) => {
  if (!M.E) {
    const shapes = M.shapes;
    M.E = (x, y, z) => {
      let e = Infinity;
      for (const s of shapes) e = Math.min(e, s.f(x, y, z));
      return e;
    };
    M.lo = [0, 1, 2].map((k) => Math.min(...shapes.map((s) => s.lo[k])));
    M.hi = [0, 1, 2].map((k) => Math.max(...shapes.map((s) => s.hi[k])));
  }
  return M;
};
function buildMuscle(M) {
  outline(M);
  const others = M.tendon ? [] : muscleList.filter((o) => o !== M && !o.tendon).map(outline);
  const blob = new Field(M.lo, M.hi, M.h, 0.02);
  const surfInset = M.tendon ? 0.002 : 0.003;
  blob.map((x, y, z) => {
    const sk = skin.sample(x, y, z);
    if (sk > 0.004 || sk < -0.055) return 0.02;
    const e = M.E(x, y, z);
    if (e > GROW) return 0.02;
    // the surface belongs to the nearest outline: halfway to the next muscle is the border (a Voronoi partition)
    let g = M.tendon ? e : e - GROW;
    if (!M.tendon) {
      let e2 = Infinity;
      for (const o of others) {
        if (x < o.lo[0] - GROW || x > o.hi[0] + GROW || y < o.lo[1] - GROW || y > o.hi[1] + GROW || z < o.lo[2] - GROW || z > o.hi[2] + GROW) continue;
        e2 = Math.min(e2, o.E(x, y, z));
      }
      g = Math.max((e - e2) / 2, e - GROW);
    }
    const u = clamp(-g / M.depth, 0, 1);
    // domed: 3 mm under the skin in the middle, 16 mm at the border; closed 5 cm deep (hidden by the deep layer)
    const top = smax(g, sk + surfInset + (M.tendon ? 0.004 : 0.0055) * (1 - Math.sqrt(u)), 0.005);
    return Math.max(top, -(sk + 0.05));
  });
  const surf = polygonize(blob);
  // the projection along the fibre for tendinous ends
  let tmin = Infinity, tmax = -Infinity;
  for (let i = 0; i < surf.pos.length; i += 3) {
    const t = surf.pos[i] * M.fib[0] + surf.pos[i + 1] * M.fib[1] + surf.pos[i + 2] * M.fib[2];
    tmin = Math.min(tmin, t);
    tmax = Math.max(tmax, t);
  }
  // neighbouring muscles differ only slightly in tone, so the layer reads as one body rather than a patchwork
  const base = mul(M.tendon ? TENDON_C : MUSCLE_C, 1 + (M.tint - 1) * 0.4);
  emit('muscle', surf, () => ({ code: M.code, mat: M.tendon ? 'tendon' : 'muscle' }), (p, n) => {
    const w = norm(cross(M.fib, n));
    const stripe = 0.5 + 0.5 * Math.sin((dot(p, w) * Math.PI * 2) / 0.011);
    const fine = 0.5 + 0.5 * Math.sin((dot(p, w) * Math.PI * 2) / 0.0037);
    let c = mul(base, 0.84 + 0.12 * stripe + 0.05 * fine);
    if (M.ends && !M.tendon) {
      const t = (dot(p, M.fib) - tmin) / (tmax - tmin || 1);
      const k = Math.max(smooth(M.ends[0] + 0.06, M.ends[0], t), smooth(M.ends[1] - 0.06, M.ends[1], t));
      c = lerp3(c, mul(FASCIA_C, 0.95 + 0.08 * stripe), k * 0.3);
    }
    return c;
  });
}

// snapping: muscles are placed by approximate points; each is moved onto the skin and then a little inwards
function snap(p, depth = 0) {
  let q = p.slice();
  for (let i = 0; i < 10; i++) {
    const d = skin.sample(...q);
    const g = norm(skin.grad(...q));
    q = sub(q, mul(g, d));
  }
  const n = norm(skin.grad(...q));
  return { p: sub(q, mul(n, depth)), n };
}
// Muscle outlines are drawn on the skin: a prism along the surface normal n, cut to a slab around the surface, so a
// patch follows the curvature of the body however large it is. The muscle's depth comes from the skin field.
const SLAB_OUT = 0.04, SLAB_IN = 0.12;
/** an elliptic patch centred near c: radii along the fibre `ax` and across it */
function patch(c, ra, rc, ax) {
  const { p: o, n } = snap(c);
  const e1 = norm(sub(ax, mul(n, dot(ax, n)))), e3 = cross(n, e1);
  const m = Math.min(ra, rc);
  const f = (x, y, z) => {
    const q = [x - o[0], y - o[1], z - o[2]];
    const a = dot(q, e1), c3 = dot(q, e3), h = dot(q, n);
    const e = (Math.sqrt((a / ra) ** 2 + (c3 / rc) ** 2) - 1) * m;
    return Math.max(e, h - SLAB_OUT, -h - SLAB_IN);
  };
  const r = Math.max(ra, rc) + SLAB_IN;
  return { f, lo: o.map((v) => v - r), hi: o.map((v) => v + r) };
}
/** a band on the surface from a to b, half widths wa, wb */
function band(a, b, wa, wb) {
  const A = snap(a), Bp = snap(b);
  const n = norm(add(A.n, Bp.n));
  const ab = sub(Bp.p, A.p);
  const abt = sub(ab, mul(n, dot(ab, n)));
  const L2 = dot(abt, abt);
  const f = (x, y, z) => {
    const q = [x - A.p[0], y - A.p[1], z - A.p[2]];
    const qt = sub(q, mul(n, dot(q, n)));
    const t = clamp(dot(qt, abt) / L2, 0, 1);
    const w = lerp(wa, wb, t);
    const e = len(sub(qt, mul(abt, t))) - w;
    const h = dot(sub(q, mul(ab, t)), n);
    return Math.max(e, h - SLAB_OUT, -h - SLAB_IN);
  };
  const r = Math.max(wa, wb) + SLAB_IN;
  return { f, lo: [0, 1, 2].map((k) => Math.min(A.p[k], Bp.p[k]) - r), hi: [0, 1, 2].map((k) => Math.max(A.p[k], Bp.p[k]) + r) };
}

// muscles of the left side; mirrored for the right
function defineMuscles(sx) {
  const m = (p) => (sx > 0 ? p : mirror(p));
  const f = (v) => norm(sx > 0 ? v : mirror(v));
  const P = (c, ra, rc, ax, t) => patch(m(c), ra, rc, f(ax), t);
  const Bd = (a, b, wa, wb, t) => band(m(a), m(b), wa, wb, t);
  const Lr = (s) => (sx > 0 ? 'L_' : 'R_') + s;
  const F = sx > 0 ? 'LF' : 'RF', Hh = sx > 0 ? 'LH' : 'RH';
  const hx = (s, v, x) => H(s, v, x);
  // head
  muscle('masseter', 'HEAD', [patch(m(hx(0.17, -0.13, 0.1)), 0.1, 0.075, f(sub(hx(0.1, -0.05, 0), hx(0.25, -0.22, 0))), 0.03)], f(sub(hx(0.1, -0.05, 0), hx(0.25, -0.22, 0))), { tint: 1.05 });
  muscle('levator nasolabialis', 'HEAD', [band(m(hx(0.26, 0.03, 0.08)), m(hx(0.55, -0.01, 0.06)), 0.02, 0.03)], f(HA), { ends: [0.0, 0.8] });
  muscle('zygomaticus', 'HEAD', [band(m(hx(0.22, -0.06, 0.09)), m(hx(0.5, -0.08, 0.065)), 0.012, 0.012)], f(HA), { ends: [0.05, 0.85], tint: 1.08 });
  muscle('buccinator', 'HEAD', [band(m(hx(0.36, -0.11, 0.07)), m(hx(0.56, -0.1, 0.055)), 0.026, 0.02)], f(HA), { tint: 0.93 });
  muscle('dilator naris', 'HEAD', [patch(m(hx(0.6, 0.0, 0.055)), 0.03, 0.025, f(HA))], f([0, 1, 0]), { tint: 1.04 });
  muscle('orbicularis oris', 'HEAD', [band(m(hx(0.63, -0.09, 0.055)), m(hx(0.675, -0.09, 0.02)), 0.02, 0.02)], f(HD), { tint: 0.97 });
  muscle('parotidoauricularis', 'HEAD', [band(m(hx(0.0, 0.02, 0.08)), m(hx(0.12, -0.2, 0.085)), 0.02, 0.03)], f(HD), { ends: [0.0, 0.85], tint: 0.9 });
  muscle('temporalis', 'HEAD', [patch(m(hx(0.05, 0.03, 0.08)), 0.045, 0.035, f(HA), 0.02)], f(HA), { tint: 1.08 });
  // neck
  muscle('splenius', 'NECK', [Bd([0.05, 1.86, 1.02], [0.08, 1.64, 0.6], 0.045, 0.075, 0.04)], [0, -0.6, -0.8], { ends: [0.06, 1] });
  muscle('rhomboideus', 'NECK', [Bd([0.02, 1.76, 0.72], [0.03, 1.62, 0.45], 0.025, 0.03, 0.03)], [0, -0.4, -0.9], { tint: 0.92 });
  muscle('trapezius cervicis', 'NECK', [P([0.1, 1.6, 0.56], 0.14, 0.075, [0, -0.75, -0.65], 0.03)], [0, -0.75, -0.65], { ends: [0.0, 0.82], tint: 1.04 });
  muscle('brachiocephalicus', 'NECK', [Bd([0.07, 1.82, 1.08], [0.17, 1.27, 0.66], 0.035, 0.055, 0.035)], [0, -0.7, -0.6], { ends: [0.04, 0.96] });
  muscle('sternocephalicus', 'NECK', [Bd(H(0.2, -0.22, 0.04), [0.05, 1.13, 0.72], 0.022, 0.03, 0.03)], [0, -0.75, -0.6], { ends: [0.0, 0.9], tint: 1.08 });
  muscle('serratus cervicis', 'NECK', [P([0.15, 1.45, 0.64], 0.09, 0.06, [0, 0.55, 0.8], 0.03)], [0, 0.55, 0.8], { tint: 0.88 });
  // trunk
  muscle('trapezius thoracis', 'WITHERS', [P([0.06, 1.56, 0.24], 0.11, 0.06, [0, -0.6, 0.75], 0.025)], [0, -0.6, 0.75], { ends: [0.0, 0.85] });
  muscle('latissimus dorsi', 'BACK', [P([0.2, 1.36, 0.02], 0.3, 0.1, [0, -0.35, 0.93], 0.03)], [0, -0.35, 0.93], { ends: [0.2, 1.01], tint: 1.02 });
  muscle('longissimus', 'BACK', [Bd([0.06, 1.52, 0.18], [0.07, 1.55, -0.24], 0.04, 0.05, 0.035)], [0, 0, 1], { tint: 1.06 });
  muscle('longissimus lumborum', 'LOIN', [Bd([0.07, 1.55, -0.24], [0.08, 1.58, -0.48], 0.05, 0.055, 0.035)], [0, 0, 1], { ends: [0.0, 1], tint: 0.99 });
  for (let i = 0; i < 6; i++) {
    const z = 0.27 - i * 0.065;
    muscle(`serratus ventralis ${i + 1}`, 'BELLY', [P([0.26, 1.06 + i * 0.012, z], 0.085, 0.022, [0, -1, -0.3], 0.025)], [0, -1, -0.3], { tint: 0.96 + (i % 2) * 0.08 });
  }
  muscle('obliquus externus', 'BELLY', [P([0.25, 1.02, -0.22], 0.32, 0.17, [0, -0.5, 0.86], 0.03)], [0, -0.55, 0.83], { ends: [0.0, 0.7], tint: 0.97 });
  muscle('rectus abdominis', 'BELLY', [P([0.07, 0.86, -0.1], 0.32, 0.06, [0, 0, 1], 0.025)], [0, 0, 1], { tint: 1.02 });
  muscle('pectoralis ascendens', 'CHEST', [P([0.12, 0.88, 0.28], 0.2, 0.06, [0, 0.1, 1], 0.03)], [0, 0.1, 1], { tint: 0.92 });
  muscle('pectoralis descendens', 'CHEST', [P([0.08, 1.13, 0.71], 0.11, 0.06, [0.2, -0.95, -0.2], 0.03)], [0.2, -0.95, -0.2], { tint: 1.05 });
  muscle('pectoralis transversus', 'CHEST', [P([0.09, 0.93, 0.56], 0.1, 0.06, [0.5, 0, 0.85], 0.025)], [0.5, 0, 0.85], { tint: 0.97 });
  // shoulder and arm
  const scap = sub(FORE_J.shoulder, [0.1, 1.55, 0.3]);
  muscle('supraspinatus', Lr('SHOULDER'), [P([0.14, 1.43, 0.5], 0.13, 0.04, scap, 0.03)], scap, { ends: [0.0, 0.9] });
  muscle('infraspinatus', Lr('SHOULDER'), [P([0.17, 1.38, 0.41], 0.13, 0.055, scap, 0.03)], scap, { ends: [0.0, 0.85], tint: 1.07 });
  muscle('deltoideus', Lr('SHOULDER'), [P([0.2, 1.24, 0.48], 0.09, 0.05, [0, -0.6, 0.8], 0.03)], [0, -0.6, 0.8], { tint: 0.93 });
  muscle('triceps longum', `${F}_ELBOW`, [P([0.17, 1.14, 0.42], 0.14, 0.09, [0, -0.85, -0.1], 0.04)], [0, -0.9, -0.3], { ends: [0.03, 1] });
  muscle('triceps laterale', `${F}_ELBOW`, [P([0.2, 1.07, 0.53], 0.075, 0.05, [0, -0.7, -0.6], 0.03)], [0, -0.7, -0.6], { tint: 1.07 });
  muscle('brachialis', `${F}_ELBOW`, [P([0.2, 1.08, 0.61], 0.07, 0.03, [0, -0.85, -0.4], 0.025)], [0, -0.85, -0.4], { tint: 0.9 });
  // forearm
  muscle('extensor carpi radialis', `${F}_FOREARM`, [Bd([0.17, 0.95, 0.53], [0.16, 0.6, 0.54], 0.028, 0.008, 0.025)], [0, -1, 0], { ends: [0.04, 0.55] });
  muscle('extensor digitalis communis', `${F}_FOREARM`, [Bd([0.21, 0.94, 0.5], [0.19, 0.6, 0.52], 0.022, 0.007, 0.022)], [0, -1, 0], { ends: [0.04, 0.5], tint: 1.06 });
  muscle('extensor digitalis lateralis', `${F}_FOREARM`, [Bd([0.22, 0.9, 0.47], [0.2, 0.62, 0.49], 0.014, 0.006, 0.02)], [0, -1, 0], { ends: [0.04, 0.55], tint: 0.9 });
  muscle('ulnaris lateralis', `${F}_FOREARM`, [Bd([0.2, 0.94, 0.43], [0.18, 0.6, 0.465], 0.026, 0.01, 0.022)], [0, -1, 0], { ends: [0.04, 0.6], tint: 0.95 });
  muscle('flexor carpi ulnaris', `${F}_FOREARM`, [Bd([0.15, 0.95, 0.41], [0.155, 0.62, 0.45], 0.028, 0.012, 0.022)], [0, -1, 0], { ends: [0.04, 0.6], tint: 1.03 });
  muscle('flexor carpi radialis', `${F}_FOREARM`, [Bd([0.1, 0.93, 0.47], [0.12, 0.62, 0.48], 0.025, 0.01, 0.022)], [0, -1, 0], { ends: [0.04, 0.6], tint: 0.97 });
  limbTendons(F, true, m);
  // hindquarters and thigh
  muscle('gluteus medius', 'CROUP', [P([0.12, 1.55, -0.58], 0.22, 0.09, [0, 0.1, 1], 0.04)], [0, 0.15, 1], { ends: [0.0, 0.82], tint: 1.04 });
  muscle('gluteus superficialis', Lr('HIP'), [P([0.2, 1.34, -0.56], 0.12, 0.045, [0, -0.85, -0.45], 0.03)], [0, -0.85, -0.45], { ends: [0.0, 0.85] });
  muscle('tensor fasciae latae', Lr('HIP'), [P([0.22, 1.25, -0.42], 0.14, 0.055, [0, -0.9, -0.3], 0.03)], [0, -0.9, -0.3], { ends: [0.0, 0.5], tint: 0.96 });
  muscle('biceps femoris', Lr('HIP'), [P([0.2, 1.16, -0.73], 0.27, 0.075, [0, -0.95, 0.25], 0.04)], [0, -0.95, 0.25], { ends: [0.0, 0.93], tint: 1.06 });
  muscle('semitendinosus', Lr('HIP'), [P([0.12, 1.1, -0.91], 0.26, 0.045, [0, -0.95, 0.25], 0.03)], [0, -0.95, 0.25], { ends: [0.0, 0.92], tint: 0.94 });
  muscle('semimembranosus', Lr('HIP'), [P([0.04, 1.12, -0.93], 0.2, 0.04, [0, -0.95, 0.2], 0.03)], [0, -0.95, 0.2], { tint: 1.01 });
  muscle('vastus lateralis', `${Hh}_STIFLE`, [P([0.21, 1.04, -0.5], 0.1, 0.05, [0, -0.9, 0.35], 0.03)], [0, -0.9, 0.35], { ends: [0.0, 0.85] });
  muscle('gastrocnemius', `${Hh}_GASKIN`, [Bd([0.17, 0.92, -0.66], [0.155, 0.7, -0.81], 0.035, 0.016, 0.03)], [0, -0.85, -0.5], { ends: [0.0, 0.62], tint: 1.04 });
  muscle('extensor digitalis longus', `${Hh}_GASKIN`, [Bd([0.2, 0.9, -0.53], [0.17, 0.63, -0.75], 0.03, 0.01, 0.025)], [0, -0.85, -0.5], { ends: [0.0, 0.62] });
  muscle('extensor digitalis lateralis', `${Hh}_GASKIN`, [Bd([0.21, 0.87, -0.6], [0.19, 0.64, -0.78], 0.016, 0.007, 0.02)], [0, -0.85, -0.5], { ends: [0.0, 0.6], tint: 0.9 });
  muscle('flexor digitalis profundus', `${Hh}_GASKIN`, [Bd([0.2, 0.86, -0.66], [0.17, 0.64, -0.8], 0.022, 0.01, 0.022)], [0, -0.85, -0.5], { ends: [0.0, 0.65], tint: 0.97 });
  muscle('tendo calcaneus', `${Hh}_HOCK`, [Bd([0.155, 0.8, -0.76], HIND_J.calcaneus, 0.016, 0.014, 0.02)], [0, -1, 0], { tendon: true });
  limbTendons(Hh, false, m);
  muscle('coccygeus', 'TAIL_BASE', [Bd([0.03, 1.5, -0.93], [0.025, 1.4, -1.08], 0.025, 0.015, 0.025)], [0, -0.5, -0.8], { tint: 0.95 });
}
function limbTendons(pre, fore, m) {
  const J = fore ? FORE_J : HIND_J;
  const top = fore ? 0.6 : 0.5;
  const zc = fore ? 0.49 : -0.785;
  const x = J.fetlock[0];
  const T = (name, code, shape) => muscle(`${name} ${pre}`, code, [shape], [0, -1, 0], { tendon: true, h: 0.0045 });
  // flexor tendons behind the cannon, the suspensory ligament, the extensor tendon in front
  T('flexor tendons', `${pre}_CANNON`, band(m([x, top - 0.04, zc - 0.05]), m([x, 0.26, zc - 0.05]), 0.016, 0.018, 0.022));
  T('suspensory ligament', `${pre}_CANNON`, band(m([x + 0.03, top - 0.06, zc - 0.02]), m([x + 0.03, 0.27, zc - 0.025]), 0.012, 0.014, 0.016));
  T('extensor tendon', `${pre}_CANNON`, band(m([x, top - 0.02, zc + 0.05]), m([x, 0.26, zc + 0.05]), 0.009, 0.008, 0.012));
  T('fetlock annular ligament', `${pre}_FETLOCK`, patch(m([x, 0.215, zc - 0.03]), 0.04, 0.05, [0, 1, 0], 0.02));
  T('pastern', `${pre}_PASTERN`, patch(m(lerp3([x, 0.18, fore ? 0.52 : -0.75], J.coronet, 0.4)), 0.05, 0.05, [0, -1, 0.4], 0.02));
  if (fore) T('carpal fascia', `${pre}_CARPUS`, patch(m([x, 0.535, 0.55]), 0.06, 0.045, [0, 1, 0], 0.02));
  else T('hock fascia', `${pre}_HOCK`, patch(m([x + 0.04, 0.555, -0.8]), 0.06, 0.05, [0, 1, 0], 0.02));
  // hoof wall in the muscle layer
  const hfld = surfaceOf([{ prim: hoofShape(m(J.coronet), { shrink: 0.002 }), k: 0 }], 0.0045);
  emit('muscle', polygonize(hfld), () => ({ code: `${pre}_HOOF`, mat: 'hoof' }), null);
}
console.time('muscle');
defineMuscles(1);
defineMuscles(-1);
for (const M of muscleList) buildMuscle(M);
console.timeEnd('muscle');

// ---------------------------------------------------------------------------------------------------------------
// SKELETON
function bone(code, prims, h = 0.0048) {
  const fld = surfaceOf(prims.map((p) => ({ prim: p.prim ?? p, k: p.k ?? 0.012, op: p.op ?? 'add' })), h);
  emit('skeleton', polygonize(fld), () => ({ code, mat: 'bone' }), null);
}
const B = (prim, k = 0.012, op = 'add') => ({ prim, k, op });
/** a flat plate on the triangle a, b, c with half thickness t (exact distance to a triangle, rounded) */
function plate(a, b, c, t) {
  const ba = sub(b, a), cb = sub(c, b), ac = sub(a, c);
  const nor = cross(ba, ac);
  const nn = dot(nor, nor);
  const cba = cross(ba, nor), ccb = cross(cb, nor), cac = cross(ac, nor);
  const d2 = (v) => dot(v, v);
  const f = (x, y, z) => {
    const p = [x, y, z];
    const pa = sub(p, a), pb = sub(p, b), pc = sub(p, c);
    let d;
    if (Math.sign(dot(cba, pa)) + Math.sign(dot(ccb, pb)) + Math.sign(dot(cac, pc)) < 2) {
      d = Math.min(
        d2(sub(mul(ba, clamp(dot(ba, pa) / d2(ba), 0, 1)), pa)),
        d2(sub(mul(cb, clamp(dot(cb, pb) / d2(cb), 0, 1)), pb)),
        d2(sub(mul(ac, clamp(dot(ac, pc) / d2(ac), 0, 1)), pc)),
      );
    } else d = (dot(nor, pa) * dot(nor, pa)) / nn;
    return Math.sqrt(d) - t;
  };
  const lo = [0, 1, 2].map((k) => Math.min(a[k], b[k], c[k]) - t), hi = [0, 1, 2].map((k) => Math.max(a[k], b[k], c[k]) + t);
  return { f, lo, hi };
}
/** a long bone a->b: wide ends ra, rb (side, across), slimmer shaft rm */
function longBone(a, b, ra, rm, rb, side = [1, 0, 0]) {
  const mix = (r1, r2, t) => [lerp(r1[0], r2[0], t), lerp(r1[1], r2[1], t)];
  return loft([
    { p: a, r: ra },
    { p: lerp3(a, b, 0.14), r: mix(ra, rm, 0.75) },
    { p: lerp3(a, b, 0.5), r: rm },
    { p: lerp3(a, b, 0.86), r: mix(rb, rm, 0.75) },
    { p: b, r: rb },
  ], { n: 14, side, k: 0.008 }).map((p) => B(p, 0.008));
}
console.time('skeleton');
// skull
bone('HEAD', [
  B(hell(0.07, -0.015, 0, [0.085, 0.072, 0.068]), 0.03), // cranium
  B(hell(0.02, -0.0, 0, [0.03, 0.04, 0.06]), 0.02), // occiput and nuchal crest
  B(hell(0.19, 0.025, 0, [0.09, 0.03, 0.085]), 0.03), // frontal bone between the orbits
  B(seg(H(0.22, 0.02), H(0.6, -0.03), [0.048, 0.018], [0.03, 0.014]), 0.04), // nasal bones
  ...[1, -1].flatMap((sx) => [
    B(hell(0.38, -0.065, sx * 0.042, [0.16, 0.055, 0.022]), 0.035), // maxilla with the cheek teeth
    B(hell(0.355, -0.035, sx * 0.064, [0.11, 0.012, 0.01]), 0.015), // facial crest
    B(hell(0.172, 0.03, sx * 0.072, [0.036, 0.036, 0.02]), 0.012), // orbit rim
    B(hell(0.176, 0.03, sx * 0.088, [0.026, 0.026, 0.024]), 0.006, 'sub'), // orbit
    B(seg(H(0.08, -0.045, sx * 0.07), H(0.17, -0.005, sx * 0.075), [0.01, 0.013], [0.01, 0.013]), 0.012), // zygomatic arch
    // mandible: a broad, rounded ramus and the body carrying the cheek teeth
    B(plate(H(0.075, -0.045, sx * 0.072), H(0.17, -0.27, sx * 0.06), H(0.3, -0.19, sx * 0.055), 0.008), 0.015),
    B(ell(H(0.15, -0.19, sx * 0.064), [0.085, 0.085, 0.008], HA, HD), 0.02),
    ...Array.from({ length: 6 }, (_, t) => B(hell(0.25 + t * 0.042, -0.115, sx * 0.04, [0.02, 0.03, 0.015]), 0.006)), // cheek teeth
    B(plate(H(0.06, 0.01, sx * 0.07), H(0.075, -0.045, sx * 0.072), H(0.14, -0.1, sx * 0.068), 0.006), 0.012), // coronoid
    B(seg(H(0.16, -0.25, sx * 0.058), H(0.61, -0.135, sx * 0.02), [0.011, 0.045], [0.01, 0.022]), 0.02),
  ]),
  B(hell(0.645, -0.078, 0, [0.04, 0.02, 0.026]), 0.03), // incisive bone
  ...[-1.5, -0.5, 0.5, 1.5].flatMap((k) => [B(hell(0.69, -0.098, k * 0.013, [0.02, 0.012, 0.007]), 0.004), B(hell(0.675, -0.122, k * 0.012, [0.02, 0.011, 0.007]), 0.004)]), // incisors
  B(hell(0.63, -0.125, 0, [0.04, 0.022, 0.03]), 0.02), // front of the mandible
], 0.004);
// vertebral column: neck, thoracic with withers spines, lumbar with transverse processes, sacrum, tail
const NECKV = [[1.26, 0.6], [1.36, 0.73], [1.5, 0.84], [1.63, 0.93], [1.74, 1.0], [1.83, 1.05], [1.88, 1.09]];
for (let i = 0; i < NECKV.length - 1; i++) {
  const [y0, z0] = NECKV[i], [y1, z1] = NECKV[i + 1];
  const a = [0, y0, z0], b = [0, y1, z1];
  const mid = lerp3(a, b, 0.5);
  const ax = norm(sub(b, a));
  const up = norm(cross(ax, [1, 0, 0]));
  bone('NECK', [
    // body: waisted in the middle with flared ends, the ends of neighbours meet at the discs
    B(seg(lerp3(a, b, -0.02), lerp3(a, b, 0.3), [0.03, 0.028], [0.022, 0.02]), 0.012),
    B(seg(lerp3(a, b, 0.3), lerp3(a, b, 0.7), [0.022, 0.02], [0.022, 0.02]), 0.012),
    B(seg(lerp3(a, b, 0.7), lerp3(a, b, 1.0), [0.022, 0.02], [0.03, 0.027]), 0.012),
    B(ell(add(mid, mul(up, -0.032)), [0.085, 0.02, 0.034], ax, up), 0.02), // arch with articular processes
    B(seg(add(lerp3(a, b, 0.2), mul(up, -0.05)), add(lerp3(a, b, 0.8), mul(up, -0.05)), [0.006, 0.012], [0.006, 0.012], [1, 0, 0]), 0.015), // dorsal ridge
    ...[1, -1].map((sx) => B(ell(add(add(mid, mul(up, 0.01)), [sx * 0.04, 0, 0]), [0.05, 0.012, 0.022], ax, up), 0.012)), // transverse processes
    ...(i === NECKV.length - 2 ? [1, -1].map((sx) => B(ell(add(b, [sx * 0.045, 0, 0]), [0.04, 0.012, 0.045], ax, up), 0.015)) : []), // atlas wings
  ]);
}
const spineY = (z) => (z > 0.3 ? 1.28 + (0.58 - z) * 0.2 : z > -0.15 ? 1.34 + (0.3 - z) * 0.08 : 1.38 + (-0.15 - z) * 0.12);
for (let i = 0; i < 24; i++) {
  const z = 0.55 - i * 0.047;
  const code = z > 0.15 ? 'WITHERS' : z > -0.25 ? 'BACK' : 'LOIN';
  const y = spineY(z);
  const prims = [B(seg([0, y, z + 0.02], [0, y, z - 0.02], [0.03, 0.028], [0.03, 0.028]), 0.008)];
  const withers = z > 0.12;
  const ty = withers ? skinTop(z) - 0.035 : Math.min(skinTop(z) - 0.05, y + 0.1);
  const lean = z > 0.0 ? -0.04 - (withers ? 0.08 : 0) : z > -0.25 ? -0.01 : 0.025;
  prims.push(B(seg([0, y + 0.02, z], [0, ty, z + lean], [0.007, 0.022], [0.006, withers ? 0.02 : 0.026]), 0.012));
  if (code === 'LOIN') for (const sx of [1, -1]) prims.push(B(plate([0, y, z - 0.015], [0, y, z + 0.015], [sx * 0.12, y + 0.01, z], 0.006), 0.01));
  bone(code, prims);
}
bone('CROUP', [
  B(plate([0, 1.44, -0.6], [0.06, 1.44, -0.66], [0, 1.45, -0.9], 0.012), 0.02),
  B(plate([0, 1.44, -0.6], [-0.06, 1.44, -0.66], [0, 1.45, -0.9], 0.012), 0.02), // sacrum
  ...[0, 1, 2, 3].map((i) => B(seg([0, 1.45, -0.66 - i * 0.06], [0, Math.min(skinTop(-0.66 - i * 0.06) - 0.05, 1.52), -0.68 - i * 0.06], [0.005, 0.012], [0.005, 0.01]), 0.01)),
]);
bone('TAIL_BASE', Array.from({ length: 10 }, (_, i) => {
  const z = -0.92 - i * 0.03, y = 1.46 - i * i * 0.003;
  const r = 0.016 - i * 0.001;
  return B(seg([0, y, z], [0, y - 0.006, z - 0.024], [r * 1.2, r], [r * 1.1, r * 0.9]), 0.005);
}));
// ribs from the spine around the barrel to the sternum and the costal arch
// the ribcage is narrow between the shoulders and widest at the barrel; the scapulae lie outside it
const ribMax = (z) => lerp(0.075, 0.235, smooth(0.52, -0.05, z));
const torsoW = (y, z) => Math.min(skinX(y, z) - 0.035, ribMax(z));
for (let i = 0; i < 18; i++) {
  const z0 = 0.52 - i * 0.042;
  const code = z0 > 0.25 ? 'CHEST' : 'BELLY';
  for (const sx of [1, -1]) {
    const pts = [];
    const yTop = spineY(z0) - 0.01;
    const yBot = i < 8 ? 0.92 + i * 0.008 : 0.99 + (i - 8) * 0.032;
    for (let k = 0; k <= 9; k++) {
      const t = k / 9;
      const y = lerp(yTop, yBot, t);
      const z = z0 - 0.1 * Math.sin(t * Math.PI * 0.85) - (i > 9 ? 0.05 * t : 0);
      const w = Math.max(torsoW(y, z), 0.04);
      const xr = 0.035 + (w - 0.035) * Math.sin(Math.min(t * 1.2, 1) * Math.PI * 0.6 + 0.3);
      pts.push([sx * xr, y, z]);
    }
    if (i < 8) pts[pts.length - 1][0] = sx * 0.03;
    bone(code, loft(pts.map((p, k) => ({ p, r: [k < 2 ? 0.01 : 0.015, 0.0085] })), { n: 16, side: [0, 0, 1], k: 0.006 }).map((p) => B(p, 0.006)), 0.0045);
  }
}
bone('CHEST', [B(seg([0, 1.0, 0.66], [0, 0.89, 0.2], [0.016, 0.03], [0.03, 0.02]), 0.02), B(ell([0, 1.03, 0.68], [0.03, 0.02, 0.025]), 0.02)]);
for (const sx of [1, -1]) {
  const m = (p) => (sx > 0 ? p : mirror(p));
  const F = sx > 0 ? 'LF' : 'RF', Hh = sx > 0 ? 'LH' : 'RH';
  const K = HIND_J, J = FORE_J;
  // pelvis: ilium wing, shaft, acetabulum, ischium and pubis
  bone(sx > 0 ? 'L_HIP' : 'R_HIP', [
    B(plate(m([0.035, 1.56, -0.58]), m(K.coxae), m([0.15, 1.3, -0.62]), 0.009), 0.02),
    B(ell(m(K.coxae), [0.04, 0.03, 0.025]), 0.02),
    B(ell(m([0.04, 1.57, -0.58]), [0.03, 0.025, 0.02]), 0.015), // tuber sacrale
    B(seg(m([0.15, 1.3, -0.62]), m(K.hip), [0.02, 0.03], [0.03, 0.035]), 0.02),
    B(ell(m(K.hip), [0.045, 0.045, 0.045]), 0.02),
    B(plate(m(K.hip), m(K.ischium), m([0.035, 1.13, -0.86]), 0.009), 0.02),
    B(plate(m(K.hip), m([0.035, 1.13, -0.86]), m([0.02, 1.14, -0.72]), 0.008), 0.02),
    B(ell(m(K.ischium), [0.035, 0.03, 0.028]), 0.02),
  ]);
  // scapula with spine and cartilage, humerus
  const t0 = m([0.125, 1.54, 0.17]), t1 = m([0.13, 1.55, 0.43]), neck = m([0.18, 1.31, 0.59]);
  bone(sx > 0 ? 'L_SHOULDER' : 'R_SHOULDER', [
    B(plate(t0, t1, neck, 0.007), 0.02),
    B(plate(m([0.105, 1.585, 0.18]), m([0.11, 1.59, 0.45]), lerp3(t0, t1, 0.5), 0.005), 0.02), // cartilage
    B(seg(add(lerp3(lerp3(t0, t1, 0.45), neck, 0.08), [sx * 0.012, 0, 0]), add(lerp3(lerp3(t0, t1, 0.45), neck, 0.85), [sx * 0.012, 0, 0]), [0.012, 0.01], [0.006, 0.006]), 0.012), // spine
    B(ell(m(add(J.shoulder, [-0.01, 0.02, -0.01])), [0.04, 0.035, 0.04]), 0.015), // glenoid
  ]);
  bone(`${F}_ELBOW`, [
    ...longBone(m(J.shoulder), m(J.elbow), [0.042, 0.05], [0.024, 0.028], [0.038, 0.04]),
    B(ell(m(add(J.shoulder, [0.015, 0.02, 0.025])), [0.03, 0.03, 0.03]), 0.02), // greater tubercle
    B(ell(m([0.19, 1.12, 0.57]), [0.02, 0.03, 0.015]), 0.015), // deltoid tuberosity
    B(plate(m(J.olecranon), m([0.155, 0.96, 0.44]), m([0.15, 0.99, 0.37]), 0.01), 0.015), // olecranon
  ]);
  bone(`${F}_FOREARM`, [
    ...longBone(m([0.162, 0.96, 0.47]), m([0.155, 0.575, 0.5]), [0.036, 0.03], [0.022, 0.018], [0.036, 0.028]),
    B(seg(m([0.152, 0.94, 0.43]), m([0.152, 0.76, 0.455]), [0.008, 0.013], [0.003, 0.005]), 0.01), // ulna
  ]);
  bone(`${F}_CARPUS`, [
    ...[0, 1, 2].flatMap((c) => [0, 1].map((r) => B(ell(m([0.155 + (c - 1) * 0.021, 0.552 - r * 0.03, 0.502]), [0.017, 0.014, 0.022]), 0.003))),
    B(ell(m([0.155, 0.55, 0.458]), [0.012, 0.022, 0.014]), 0.004), // accessory carpal
  ]);
  legBones(F, J, m, true);
  bone(`${Hh}_STIFLE`, [
    ...longBone(m(K.hip), m(add(K.stifle, [0, 0.02, -0.03])), [0.045, 0.05], [0.028, 0.03], [0.045, 0.055]),
    B(ell(m([0.215, 1.22, -0.69]), [0.035, 0.04, 0.03]), 0.02), // greater trochanter
    B(ell(m([0.2, 1.03, -0.62]), [0.018, 0.022, 0.016]), 0.015), // third trochanter
    B(ell(m([0.188, 0.995, -0.41]), [0.022, 0.034, 0.018]), 0.008), // patella
  ]);
  bone(`${Hh}_GASKIN`, [
    ...longBone(m(add(K.stifle, [0, -0.025, -0.03])), m([0.15, 0.6, -0.79]), [0.045, 0.05], [0.024, 0.025], [0.034, 0.034]),
    B(seg(m([0.185, 0.93, -0.44]), m([0.172, 0.82, -0.54]), [0.01, 0.018], [0.006, 0.008]), 0.012), // tibial crest
    B(seg(m([0.195, 0.92, -0.5]), m([0.18, 0.78, -0.57]), [0.006, 0.006], [0.003, 0.003]), 0.008), // fibula
  ]);
  bone(`${Hh}_HOCK`, [
    B(seg(m(K.calcaneus), m([0.15, 0.52, -0.82]), [0.014, 0.026], [0.016, 0.018]), 0.012), // calcaneus
    B(ell(m([0.15, 0.575, -0.79]), [0.032, 0.026, 0.032]), 0.01), // talus
    ...[-1, 1].map((k) => B(seg(m([0.15 + k * 0.012, 0.6, -0.765]), m([0.15 + k * 0.012, 0.56, -0.765]), [0.006, 0.006], [0.006, 0.006]), 0.006)), // trochlear ridges
    ...[0, 1].map((r) => B(ell(m([0.15, 0.535 - r * 0.022, -0.795]), [0.03, 0.01, 0.034]), 0.003)),
  ]);
  legBones(Hh, K, m, false);
}
function legBones(pre, J, m, fore) {
  const zc = fore ? 0.5 : -0.792, zb = fore ? 0.497 : -0.775;
  const top = fore ? 0.505 : 0.49, x = J.fetlock[0];
  bone(`${pre}_CANNON`, [
    ...longBone(m([x, top, zc]), m([x, 0.24, zb]), [0.026, 0.022], [0.017, 0.014], [0.027, 0.02]),
    ...[1, -1].map((k) => B(seg(m([x + k * 0.018, top - 0.005, zc - 0.014]), m([x + k * 0.014, top - 0.16, zc - 0.018]), [0.006, 0.008], [0.0025, 0.003]), 0.008)), // splints
  ]);
  bone(`${pre}_FETLOCK`, [...[1, -1].map((k) => B(ell(m([x + k * 0.012, 0.222, zb - 0.032]), [0.013, 0.019, 0.012]), 0.004))]);
  const p1 = m([x, 0.228, zb + 0.004]), p2 = m(lerp3([x, 0.228, zb], J.coronet, 0.6)), p3 = m(add(J.coronet, [0, 0.005, -0.006]));
  bone(`${pre}_PASTERN`, [
    ...longBone(p1, p2, [0.024, 0.02], [0.017, 0.014], [0.022, 0.018]),
    ...longBone(add(p2, mul(norm(sub(p3, p2)), 0.006)), p3, [0.023, 0.019], [0.02, 0.017], [0.024, 0.02]),
  ]);
  bone(`${pre}_HOOF`, [B(hoofShape(m(J.coronet), { rx0: 0.036, rz0: 0.038, rx1: 0.05, rz1: 0.055, shrink: 0.012 }), 0.01)]);
}
console.timeEnd('skeleton');

// ---------------------------------------------------------------------------------------------------------------
// proportions: one smooth space warp applied to every layer at the end, so skin, muscle and bone stay aligned.
// It gives the Thoroughbred lines: a finer head, a longer neck carried more forward and a shorter back.
function ramp(w, a, b, steps = 400) {
  // integral of the window w over [a, t], tabulated: returns t -> value
  const tab = new Float64Array(steps + 1);
  const dt = (b - a) / steps;
  for (let i = 1; i <= steps; i++) tab[i] = tab[i - 1] + w(a + (i - 0.5) * dt) * dt;
  return (t) => {
    const u = clamp((t - a) / dt, 0, steps);
    const i = Math.min(Math.floor(u), steps - 1);
    return tab[i] + (tab[i + 1] - tab[i]) * (u - i);
  };
}
const NECK_B = [0, 1.34, 0.56], NECK_A = norm([0, 0.6, 0.8]);
const neckShift = ramp((t) => smooth(0.0, 0.16, t) * smooth(0.82, 0.66, t), -0.2, 1.4);
const backShift = ramp((z) => smooth(-0.52, -0.36, z) * smooth(0.26, 0.12, z), -1.4, 0.4);
const BACK_TOTAL = backShift(0.4);
function warp(p) {
  let [x, y, z] = p;
  // finer head: narrower jowls and face, slightly slimmer in depth
  const hs = headS(p);
  const mh = smooth(-0.08, 0.06, hs) * smooth(1.25, 1.45, y + 0.1 * Math.max(z - 1.1, 0));
  if (mh > 0) {
    x *= 1 - 0.13 * mh;
    const v = headV([x, y, z]);
    const dv = -v * 0.07 * mh * smooth(0.05, 0.3, hs); // the face slims towards the muzzle
    y += HD[1] * dv;
    z += HD[2] * dv;
  }
  // longer neck: stretch along the neck axis, the head travels with it
  const t = dot(sub([x, y, z], NECK_B), NECK_A);
  const mz = smooth(0.22, 0.46, z);
  const s = 0.22 * neckShift(t) * mz;
  y += NECK_A[1] * s;
  z += NECK_A[2] * s;
  // a slightly slimmer neck in the middle
  const nm = smooth(0.05, 0.22, t) * smooth(0.7, 0.45, t) * mz;
  x *= 1 - 0.08 * nm;
  // rounded croup: the topline slopes down to the tail head and the upper back corner rolls forward
  const cr0 = smooth(-0.5, -1.02, z) * smooth(1.18, 1.52, y);
  y -= 0.075 * cr0 * cr0;
  z += 0.045 * smooth(-0.78, -1.05, z) * smooth(1.12, 1.5, y);
  // tucked-up flank: the underline rises from the girth towards the stifle
  const tuck = smooth(1.08, 0.86, y) * smooth(-0.02, -0.2, z) * smooth(-0.56, -0.36, z) * smooth(0.3, 0.16, Math.abs(x));
  y += 0.055 * tuck;
  // shorter back and loin: everything behind the band moves forward
  z += 0.15 * (BACK_TOTAL - backShift(z));
  return [x, y, z];
}
function warpAll() {
  const e = 1e-3;
  for (const layer of Object.keys(parts)) {
    for (const code of Object.keys(parts[layer])) {
      for (const { mesh } of parts[layer][code]) {
        const P = mesh.pos, N = mesh.nor;
        for (let i = 0; i < P.length; i += 3) {
          const p = [P[i], P[i + 1], P[i + 2]];
          const J = [0, 1, 2].map((k) => {
            const a = p.slice(), b = p.slice();
            a[k] += e;
            b[k] -= e;
            return mul(sub(warp(a), warp(b)), 1 / (2 * e)); // column k
          });
          // normals transform with the cofactor matrix (inverse transpose up to scale)
          const n = [N[i], N[i + 1], N[i + 2]];
          const c0 = cross(J[1], J[2]), c1 = cross(J[2], J[0]), c2 = cross(J[0], J[1]);
          const nn = norm(add(add(mul(c0, n[0]), mul(c1, n[1])), mul(c2, n[2])));
          const q = warp(p);
          P[i] = q[0];
          P[i + 1] = q[1];
          P[i + 2] = q[2];
          N[i] = nn[0];
          N[i + 1] = nn[1];
          N[i + 2] = nn[2];
        }
      }
    }
  }
}
console.time('warp');
warpAll();
console.timeEnd('warp');

// ---------------------------------------------------------------------------------------------------------------
// anchors: a point on the skin surface of each region where a mark is drawn
const DIRS = {
  HEAD: [1, 0, 0], NECK: [0, 1, 0], WITHERS: [0, 1, 0], BACK: [0, 1, 0], LOIN: [0, 1, 0], CROUP: [0, 1, 0],
  BELLY: [0, -1, 0], CHEST: [0, 0, 1], TAIL_BASE: [0, 0, -1],
};
function anchorOf(code) {
  const v = [];
  for (const { mesh, mat } of parts.skin[code]) {
    if (mat === 'mane' || mat === 'eye') continue;
    for (let i = 0; i < mesh.pos.length; i += 3) v.push([mesh.pos[i], mesh.pos[i + 1], mesh.pos[i + 2]]);
  }
  const c = mul(v.reduce((a, p) => add(a, p), [0, 0, 0]), 1 / v.length);
  const dir = DIRS[code] ?? [code[0] === 'L' ? 1 : -1, 0, 0];
  // the surface vertex furthest out in `dir` while staying close to the centre line through c
  let best = v[0], score = -Infinity;
  for (const p of v) {
    const d = sub(p, c);
    const along = dot(d, dir);
    const s = along - 1.5 * len(sub(d, mul(dir, along)));
    if (s > score) {
      score = s;
      best = p;
    }
  }
  return best;
}

// ---------------------------------------------------------------------------------------------------------------
// GLB writer
function writeGlb(file) {
  const chunks = [];
  const bufferViews = [], accessors = [], meshes = [], nodes = [], materials = [];
  let offset = 0;
  const pushBuf = (typed, target) => {
    const buf = Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength);
    const pad = (4 - (buf.length % 4)) % 4;
    chunks.push(buf, Buffer.alloc(pad));
    bufferViews.push({ buffer: 0, byteOffset: offset, byteLength: buf.length, target });
    offset += buf.length + pad;
    return bufferViews.length - 1;
  };
  for (const key of matNames) {
    const m = MATERIALS[key];
    materials.push({ name: key, pbrMetallicRoughness: { baseColorFactor: m.color, metallicFactor: 0, roughnessFactor: m.rough } });
  }
  const meshIndex = (list) => {
    const prims = list.map(({ mesh, mat }) => {
      const pos = new Float32Array(mesh.pos), nor = new Float32Array(mesh.nor);
      const big = mesh.pos.length / 3 > 65535;
      const idx = big ? new Uint32Array(mesh.idx) : new Uint16Array(mesh.idx);
      const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
      for (let i = 0; i < pos.length; i += 3) {
        for (let k = 0; k < 3; k++) {
          min[k] = Math.min(min[k], pos[i + k]);
          max[k] = Math.max(max[k], pos[i + k]);
        }
      }
      accessors.push({ bufferView: pushBuf(pos, 34962), componentType: 5126, count: pos.length / 3, type: 'VEC3', min, max });
      const attributes = { POSITION: accessors.length - 1 };
      accessors.push({ bufferView: pushBuf(nor, 34962), componentType: 5126, count: nor.length / 3, type: 'VEC3' });
      attributes.NORMAL = accessors.length - 1;
      if (mesh.col) {
        // linear colours, sRGB-free, as normalized bytes (RGBA keeps the 4-byte alignment)
        const col = new Uint8Array((mesh.col.length / 3) * 4);
        for (let i = 0, j = 0; i < mesh.col.length; i += 3, j += 4) {
          col[j] = Math.round(clamp(mesh.col[i], 0, 1) * 255);
          col[j + 1] = Math.round(clamp(mesh.col[i + 1], 0, 1) * 255);
          col[j + 2] = Math.round(clamp(mesh.col[i + 2], 0, 1) * 255);
          col[j + 3] = 255;
        }
        accessors.push({ bufferView: pushBuf(col, 34962), componentType: 5121, normalized: true, count: col.length / 4, type: 'VEC4' });
        attributes.COLOR_0 = accessors.length - 1;
      }
      accessors.push({ bufferView: pushBuf(idx, 34963), componentType: big ? 5125 : 5123, count: idx.length, type: 'SCALAR' });
      return { attributes, indices: accessors.length - 1, material: matNames.indexOf(mat) };
    });
    meshes.push({ primitives: prims });
    return meshes.length - 1;
  };
  const children = [];
  for (const code of CODES) {
    for (const layer of ['skin', 'muscle', 'skeleton']) {
      if (!parts[layer][code]) throw new Error(`missing ${layer}.${code}`);
      nodes.push({ name: `${layer}.${code}`, mesh: meshIndex(parts[layer][code]) });
      children.push(nodes.length - 1);
    }
    nodes.push({ name: `anchor.${code}`, translation: anchorOf(code).map((x) => Math.round(x * 1e5) / 1e5) });
    children.push(nodes.length - 1);
  }
  nodes.push({ name: 'horse', children });
  const json = {
    asset: { version: '2.0', generator: 'racehorse generate-horse-model.mjs' },
    scene: 0,
    scenes: [{ nodes: [nodes.length - 1] }],
    nodes, meshes, materials, accessors, bufferViews,
    buffers: [{ byteLength: offset }],
  };
  let jsonBuf = Buffer.from(JSON.stringify(json));
  jsonBuf = Buffer.concat([jsonBuf, Buffer.alloc((4 - (jsonBuf.length % 4)) % 4, 0x20)]);
  const bin = Buffer.concat(chunks);
  const header = Buffer.alloc(12);
  header.writeUInt32LE(0x46546c67, 0);
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(12 + 8 + jsonBuf.length + 8 + bin.length, 8);
  const ch = (length, type) => {
    const b = Buffer.alloc(8);
    b.writeUInt32LE(length, 0);
    b.writeUInt32LE(type, 4);
    return b;
  };
  const out = Buffer.concat([header, ch(jsonBuf.length, 0x4e4f534a), jsonBuf, ch(bin.length, 0x004e4942), bin]);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, out);
  return out.length;
}

if (process.env.DEBUG_REGIONS) {
  for (const code of CODES) {
    const row = ['skin', 'muscle', 'skeleton'].map((layer) => {
      let n = 0;
      const c = [0, 0, 0];
      for (const { mesh } of parts[layer][code]) {
        n += mesh.idx.length / 3;
        for (let i = 0; i < mesh.pos.length; i += 3) for (let k = 0; k < 3; k++) c[k] += mesh.pos[i + k];
      }
      const nv = parts[layer][code].reduce((a, { mesh }) => a + mesh.pos.length / 3, 0);
      return `${layer}:${n}@${c.map((v) => (v / nv).toFixed(2)).join(',')}`;
    });
    console.log(code.padEnd(12), row.join('  '), 'anchor', anchorOf(code).map((v) => v.toFixed(2)).join(','));
  }
}
const here = dirname(fileURLToPath(import.meta.url));
const target = process.argv[2] ?? resolve(here, '../public/models/horse.glb');
const bytes = writeGlb(target);
console.log(`wrote ${target} (${(bytes / 1048576).toFixed(2)} MB, ${CODES.length} regions)`);
