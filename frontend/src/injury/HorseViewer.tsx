import { Suspense, useEffect, useMemo, useState } from 'react';
import { Canvas, type ThreeEvent } from '@react-three/fiber';
import { ContactShadows, Environment, Lightformer, OrbitControls, useCursor, useGLTF } from '@react-three/drei';
import * as THREE from 'three';
import { anchorName, parseNodeName, STATUS_COLOURS, type LayerState } from './injuryModel';
import type { MarkStatus } from '../api/types';

/** GLTFLoader strips '.' from node names; the original glTF name is kept in userData.name. */
const gltfName = (o: THREE.Object3D): string => (o.userData?.name as string | undefined) ?? o.name;

export const MODEL_URL = '/models/horse.glb';

export interface ViewerProps {
  layers: LayerState;
  selected: string | null;
  marks: { id: number; regionCode: string; status: MarkStatus }[];
  onSelect: (code: string) => void;
}

const HIGHLIGHT = new THREE.Color('#3d86ff');
const HOVER = new THREE.Color('#ffffff');
const NONE = new THREE.Color(0, 0, 0);

/** Surface look per model material: a soft sheen on the coat, moist muscle, ivory bone (presentation only). */
const LOOK: Record<string, Partial<THREE.MeshPhysicalMaterialParameters>> = {
  coat: { roughness: 0.5, sheen: 0.55, sheenRoughness: 0.45, sheenColor: new THREE.Color('#b0713f'), clearcoat: 0.08, clearcoatRoughness: 0.6 },
  mane: { roughness: 0.62, sheen: 0.4, sheenRoughness: 0.35, sheenColor: new THREE.Color('#3a3430') },
  hoof: { roughness: 0.42, clearcoat: 0.35, clearcoatRoughness: 0.45 },
  eye: { roughness: 0.04, clearcoat: 1, clearcoatRoughness: 0.02 },
  muscle: { roughness: 0.4, clearcoat: 0.45, clearcoatRoughness: 0.35 },
  muscleDeep: { roughness: 0.55, clearcoat: 0.2, clearcoatRoughness: 0.5 },
  tendon: { roughness: 0.3, clearcoat: 0.6, clearcoatRoughness: 0.25, sheen: 0.3, sheenColor: new THREE.Color('#e9e4ff') },
  bone: { roughness: 0.62, clearcoat: 0.12, clearcoatRoughness: 0.6 },
};

function lookOf(source: THREE.Material): THREE.Material {
  const s = source as THREE.MeshStandardMaterial;
  return new THREE.MeshPhysicalMaterial({
    name: s.name,
    color: s.color,
    vertexColors: s.vertexColors,
    roughness: s.roughness,
    metalness: 0,
    ...LOOK[s.name],
  });
}

/** The model: layer visibility, see-through skin when inner layers are on, highlight and markers (TECH-050). */
function Horse({ layers, selected, marks, onSelect }: ViewerProps) {
  const gltf = useGLTF(MODEL_URL);
  const [hovered, setHovered] = useState<string | null>(null);
  useCursor(hovered !== null);
  const scene = useMemo(() => {
    const copy = gltf.scene.clone(true);
    copy.traverse((o) => {
      if (o instanceof THREE.Mesh) o.material = lookOf(o.material as THREE.Material);
    });
    return copy;
  }, [gltf.scene]);

  // The materials are this component's own copies (`lookOf`): free them when the model leaves, so going in and out of the
  // page many times does not pile up GPU memory until the browser drops the WebGL context.
  useEffect(
    () => () => {
      scene.traverse((o) => {
        if (o instanceof THREE.Mesh) (o.material as THREE.Material).dispose();
      });
    },
    [scene],
  );

  useEffect(() => {
    const inner = layers.muscle || layers.skeleton;
    scene.traverse((o) => {
      const node = parseNodeName(gltfName(o));
      if (node) o.visible = layers[node.layer];
      if (!(o instanceof THREE.Mesh)) return;
      const owner = regionOf(o);
      if (!owner) return;
      const m = o.material as THREE.MeshPhysicalMaterial;
      const seeThrough = owner.layer === 'skin' && inner;
      if (m.transparent !== seeThrough) m.needsUpdate = true; // the opaque/transparent shader variant changes
      m.transparent = seeThrough;
      m.opacity = seeThrough ? 0.22 : 1;
      m.depthWrite = !seeThrough;
      const isSelected = owner.code === selected;
      const isHovered = !isSelected && owner.code === hovered;
      m.emissive.copy(isSelected ? HIGHLIGHT : isHovered ? HOVER : NONE);
      m.emissiveIntensity = isSelected ? 0.32 : isHovered ? 0.1 : 0;
    });
  }, [scene, layers, selected, hovered]);

  // Positions are looked up when the model or the set of regions changes, not on every hover or click re-render.
  const regionKey = marks.map((m) => m.regionCode).join('|');
  const positions = useMemo(() => {
    const byName = new Map<string, THREE.Object3D>();
    scene.traverse((o) => byName.set(gltfName(o), o));
    return regionKey === '' ? [] : regionKey.split('|').map((code) => byName.get(anchorName(code))?.position.toArray() ?? null);
  }, [scene, regionKey]);
  const anchors = marks.map((mark, i) => ({ mark, position: positions[i] ?? null }));

  const hitRegion = (e: ThreeEvent<PointerEvent | MouseEvent>) => {
    const hit = e.intersections.find((i) => visibleChain(i.object) && regionOf(i.object));
    return hit ? regionOf(hit.object)!.code : null;
  };

  function click(e: ThreeEvent<MouseEvent>) {
    const code = hitRegion(e);
    if (!code) return;
    e.stopPropagation();
    onSelect(code);
  }

  return (
    <group>
      <primitive
        object={scene}
        onClick={click}
        onPointerMove={(e: ThreeEvent<PointerEvent>) => {
          e.stopPropagation();
          const code = hitRegion(e);
          if (code !== hovered) setHovered(code);
        }}
        onPointerOut={() => setHovered(null)}
      />
      {anchors.map(({ mark, position }) =>
        position ? (
          <group key={mark.id} position={position as [number, number, number]}>
            <mesh renderOrder={10}>
              <sphereGeometry args={[0.03, 24, 16]} />
              <meshBasicMaterial color={STATUS_COLOURS[mark.status]} depthTest={false} transparent opacity={0.95} />
            </mesh>
            <mesh renderOrder={9}>
              <sphereGeometry args={[0.042, 24, 16]} />
              <meshBasicMaterial color="#ffffff" depthTest={false} transparent opacity={0.9} />
            </mesh>
          </group>
        ) : null,
      )}
    </group>
  );
}

function regionOf(o: THREE.Object3D | null): { layer: string; code: string } | null {
  for (let p = o; p; p = p.parent) {
    const node = parseNodeName(gltfName(p));
    if (node) return node;
  }
  return null;
}

function visibleChain(o: THREE.Object3D | null): boolean {
  for (let p = o; p; p = p.parent) if (!p.visible) return false;
  return true;
}

/** A studio light set rendered into the environment map (no file is downloaded). */
function Studio() {
  return (
    <Environment resolution={256} frames={1}>
      <color attach="background" args={['#8f949c']} />
      <Lightformer form="rect" intensity={2.4} color="#fff6ea" position={[3, 4, 3]} scale={[5, 3, 1]} target={[0, 1, 0]} />
      <Lightformer form="rect" intensity={1.0} color="#e8f0ff" position={[-4, 2, 1.5]} scale={[4, 4, 1]} target={[0, 1, 0]} />
      <Lightformer form="rect" intensity={1.6} color="#ffffff" position={[-1, 3, -4]} scale={[6, 1.2, 1]} target={[0, 1, 0]} />
      <Lightformer form="rect" intensity={0.6} color="#ffffff" position={[0, 6, 0]} scale={[6, 6, 1]} rotation-x={Math.PI / 2} />
      <Lightformer form="rect" intensity={0.25} color="#c9b9a3" position={[0, -1, 0]} scale={[8, 8, 1]} rotation-x={-Math.PI / 2} />
    </Environment>
  );
}

/** The 3D canvas; loaded lazily so three.js stays out of the main bundle. */
export default function HorseViewer(props: ViewerProps) {
  return (
    <Canvas
      camera={{ position: [3.9, 1.85, 3.55], fov: 30, near: 0.05, far: 40 }}
      className="bg-[radial-gradient(ellipse_at_50%_40%,var(--card)_0%,var(--secondary)_55%,var(--muted)_100%)]"
      style={{ height: 'max(260px, min(460px, calc(100svh - 7.5rem)))' }}
      dpr={[1, 2]}
      onCreated={({ gl, invalidate }) => {
        // A lost context is restorable only when the loss is not cancelled by default (the browser may drop one when many are open).
        gl.domElement.addEventListener('webglcontextlost', (e) => e.preventDefault());
        gl.domElement.addEventListener('webglcontextrestored', () => invalidate());
      }}
      gl={{ antialias: true, toneMapping: THREE.ACESFilmicToneMapping, toneMappingExposure: 1.05, outputColorSpace: THREE.SRGBColorSpace }}
    >
      <ambientLight intensity={0.15} />
      <directionalLight position={[3, 5, 3]} intensity={1.1} color="#fff4e6" />
      <directionalLight position={[-3, 2.5, -2.5]} intensity={0.55} color="#dfe8ff" />
      <Suspense fallback={null}>
        <Studio />
        <Horse {...props} />
        <ContactShadows position={[0, 0.001, 0.15]} scale={4.2} blur={2.4} far={1.6} opacity={0.55} resolution={512} color="#2b2620" />
      </Suspense>
      <OrbitControls
        target={[0, 0.98, 0.12]}
        enablePan={false}
        enableDamping
        dampingFactor={0.08}
        minDistance={1.2}
        maxDistance={8}
        maxPolarAngle={Math.PI * 0.53}
      />
    </Canvas>
  );
}

useGLTF.preload(MODEL_URL);
