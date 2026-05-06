import QRCode from "qrcode";
import { invoke } from "@tauri-apps/api/core";

(async () => {
  const qrArea = document.getElementById("qr-area")!;
  const startBtn = document.getElementById("start-btn")!;

  try {
    const payload = await invoke("get_pairing_payload");
    if (payload) {
      const canvas = document.createElement("canvas");
      await QRCode.toCanvas(canvas, String(payload), { width: 140, margin: 1 });
      qrArea.innerHTML = "";
      qrArea.appendChild(canvas);
    } else {
      qrArea.innerHTML = '<span class="note">Start relay first</span>';
    }
  } catch {
    qrArea.innerHTML = '<span class="note">No relay running</span>';
  }

  startBtn.addEventListener("click", async () => {
    try { await invoke("start_all"); } catch {}
    try { await invoke("hide_pet_popup"); } catch {}
  });
})();
