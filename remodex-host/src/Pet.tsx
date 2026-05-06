import { useEffect, useMemo, useState } from "react";

export type PetAnimation = {
  row: number;
  frames: number;
  fps: number;
  loop: boolean;
};

export type PetManifest = {
  schema: string;
  name: string;
  slug: string;
  spritesheet: {
    file: string;
    width: number;
    height: number;
    columns: number;
    rows: number;
    cellWidth: number;
    cellHeight: number;
  };
  animations: Record<string, PetAnimation>;
  stateMap: Record<string, string>;
};

export async function loadPetManifest(url: string): Promise<PetManifest> {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to load pet manifest: ${res.status}`);
  }
  return res.json();
}

type PetProps = {
  manifest: PetManifest;
  basePath: string;
  appState: string;
  scale?: number;
  animationOverride?: string;
  mirrorX?: boolean;
};

export function PetSprite({ manifest, basePath, appState, scale = 1, animationOverride, mirrorX }: PetProps) {
  const animationName = animationOverride
    ?? manifest.stateMap[appState]
    ?? manifest.stateMap.default
    ?? "idle";
  const animation =
    manifest.animations[animationName] ?? manifest.animations.idle;

  const [frame, setFrame] = useState(0);

  useEffect(() => {
    setFrame(0);
    if (animation.frames <= 1) return;

    const frameMs = 1000 / animation.fps;
    const id = window.setInterval(() => {
      setFrame((prev) => {
        const next = prev + 1;
        if (next < animation.frames) return next;
        return animation.loop ? 0 : animation.frames - 1;
      });
    }, frameMs);

    return () => window.clearInterval(id);
  }, [animationName, animation.fps, animation.frames, animation.loop]);

  const cellW = manifest.spritesheet.cellWidth;
  const cellH = manifest.spritesheet.cellHeight;
  const cols = manifest.spritesheet.columns;

  const backgroundPosition = useMemo(() => {
    const xPx = -(frame * cellW);
    const yPx = -(animation.row * cellH);
    return `${xPx}px ${yPx}px`;
  }, [frame, animation.row, cellW, cellH]);

  const bgWidth = cols * cellW;

  return (
    <div
      aria-label={`${manifest.name} pet: ${animationName}`}
      style={{
        width: cellW,
        height: cellH,
        transform: mirrorX ? `scaleX(${-scale}) scaleY(${scale})` : `scale(${scale})`,
        transformOrigin: "center center",
        backgroundImage: `url("${basePath}/${manifest.spritesheet.file}")`,
        backgroundRepeat: "no-repeat",
        backgroundPosition,
        backgroundSize: `${bgWidth}px auto`,
        imageRendering: "pixelated",
        pointerEvents: "none",
      }}
    />
  );
}
