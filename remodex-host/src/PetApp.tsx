import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { isTauri } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { PetSprite, loadPetManifest, type PetManifest } from "./Pet";

export function PetApp() {
  const [manifest, setManifest] = useState<PetManifest | null>(null);
  const [appState, setAppState] = useState("stopped");
  const [direction, setDirection] = useState<"right" | "left" | null>(null);

  const moveRef = useRef({ vx: 0, vy: 0 });
  const userDrag = useRef({ active: false, moved: false, startTime: 0, startMouseX: 0, startMouseY: 0, startWinX: 0, startWinY: 0, lastMouseX: 0 });

  useEffect(() => { loadPetManifest("/pets/relay/pet.json").then(setManifest).catch(console.error); }, []);

  useEffect(() => {
    if (!isTauri()) return;
    const id = setInterval(() => {
      if ((window as any).__TAURI_INTERNALS__) {
        getCurrentWindow().setBackgroundColor("transparent").catch(() => {});
        listen<{ state: string }>("status-changed", e => { if (e.payload) setAppState(e.payload.state); }).catch(() => {});
        invoke<{ state: string }>("get_status").then(s => setAppState(s.state)).catch(() => {});
        clearInterval(id);
      }
    }, 50);
    return () => clearInterval(id);
  }, []);

  // ─── drag / click → open dashboard ────────────────────────
  useEffect(() => {
    if (!isTauri()) return;

    const onDown = async (e: MouseEvent) => {
      if (e.button !== 0) return;
      const pos = await invoke<[number, number] | null>("get_pet_window_position").catch(() => null);
      userDrag.current = { active: true, moved: false, startTime: Date.now(), startMouseX: e.screenX, startMouseY: e.screenY, startWinX: pos?.[0] ?? 0, startWinY: pos?.[1] ?? 0, lastMouseX: e.screenX };
    };

    const onMove = async (e: MouseEvent) => {
      if (!userDrag.current.active) return;
      const dx = e.screenX - userDrag.current.startMouseX;
      const dy = e.screenY - userDrag.current.startMouseY;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) userDrag.current.moved = true;
      if (!userDrag.current.moved) return;
      const lx = e.screenX - userDrag.current.lastMouseX;
      if (lx > 2) setDirection("right"); else if (lx < -2) setDirection("left");
      userDrag.current.lastMouseX = e.screenX;
      await invoke("move_pet_window", { x: userDrag.current.startWinX + dx, y: userDrag.current.startWinY + dy }).catch(() => {});
    };

    const onUp = () => {
      if (!userDrag.current.active) return;
      const moved = userDrag.current.moved;
      const dur = Date.now() - userDrag.current.startTime;
      userDrag.current.active = false;
      setDirection(null);
      if (!moved || dur > 3000) invoke("show_main_window").catch(() => {});
    };

    window.addEventListener("mousedown", onDown);
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => { window.removeEventListener("mousedown", onDown); window.removeEventListener("mousemove", onMove); window.removeEventListener("mouseup", onUp); };
  }, []);

  // ─── auto movement ─────────────────────────────────────
  useEffect(() => {
    if (!isTauri()) return;
    let stopped = false;
    let moveTimer: ReturnType<typeof setTimeout>;
    let pauseTimer: ReturnType<typeof setTimeout>;

    const startMove = () => {
      if (stopped) return;
      const a = Math.random() * Math.PI * 2;
      const sp = 1.5 + Math.random() * 2.5;
      moveRef.current.vx = Math.cos(a) * sp;
      moveRef.current.vy = Math.sin(a) * sp;
      setDirection(moveRef.current.vx > 0.1 ? "right" : moveRef.current.vx < -0.1 ? "left" : null);
      moveTimer = setTimeout(() => { moveRef.current.vx = 0; moveRef.current.vy = 0; setDirection(null); pauseTimer = setTimeout(startMove, 10000 + Math.random() * 20000); }, 2000 + Math.random() * 3000);
    };

    const tick = async () => {
      if (stopped || userDrag.current.active) return;
      if (moveRef.current.vx === 0 && moveRef.current.vy === 0) return;
      const pos = await invoke<[number, number] | null>("get_pet_window_position").catch(() => null);
      if (!pos || stopped) return;
      const mx = window.screen.availWidth - 80, my = window.screen.availHeight - 80;
      let nx = pos[0] + moveRef.current.vx, ny = pos[1] + moveRef.current.vy;
      if (nx <= 0) { nx = 0; moveRef.current.vx = Math.abs(moveRef.current.vx); setDirection("right"); }
      if (nx >= mx) { nx = mx; moveRef.current.vx = -Math.abs(moveRef.current.vx); setDirection("left"); }
      if (ny <= 0) { ny = 0; moveRef.current.vy = Math.abs(moveRef.current.vy); }
      if (ny >= my) { ny = my; moveRef.current.vy = -Math.abs(moveRef.current.vy); }
      await invoke("move_pet_window", { x: Math.max(0, Math.min(mx, nx)), y: Math.max(0, Math.min(my, ny)) }).catch(() => {});
    };

    const id = window.setInterval(tick, 33);
    pauseTimer = setTimeout(startMove, 2000);
    return () => { stopped = true; window.clearInterval(id); clearTimeout(moveTimer); clearTimeout(pauseTimer); };
  }, []);

  if (!manifest) return null;

  return (
    <div onDoubleClick={() => { invoke("show_main_window").catch(() => {}); }}
      style={{ width: "100%", height: "100%", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center" }}>
      <PetSprite manifest={manifest} basePath="/pets/relay" appState={appState}
        animationOverride={direction === "right" ? "running-right" : direction === "left" ? "running-left" : undefined}
        mirrorX={direction === "right"} scale={0.8} />
    </div>
  );
}
