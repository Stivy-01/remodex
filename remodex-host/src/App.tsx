import { useState, useEffect, useCallback, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { isTauri } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import QRCode from "qrcode";

interface LogEntry {
  timestamp: string;
  source: string;
  level: string;
  message: string;
}

interface NetworkInterface {
  name: string;
  address: string;
  kind: string;
  is_private: boolean;
}

interface AppStatus {
  state: string;
  relay_mode: string;
  relay: string;
  bridge: string;
  network: string;
  relay_url: string;
  pairing_payload: string | null;
  phone_connected: boolean;
}

interface DebugInfo {
  cwd: string;
  repo_root: string;
  relay_dir: string;
  relay_server_exists: boolean;
  bridge_dir: string;
  bridge_bin_exists: boolean;
  config_path: string;
  config_exists: boolean;
  node_version: string;
}

type View = "dashboard" | "network" | "logs" | "settings" | "debug";

interface AppConfig {
  relay_mode: string;
  selected_ip: string;
  relay_port: number;
  remote_relay_url: string;
  auto_start: boolean;
  auto_restart: boolean;
  start_minimized: boolean;
  launch_at_startup: boolean;
  setup_completed: boolean;
  requires_entitlement: boolean;
  free_message_limit: number;
  relay_path: string | null;
  bridge_path: string | null;
  log_level: string;
}

const STATE_LABELS: Record<string, string> = {
  stopped: "Stopped",
  starting: "Starting...",
  relay_running: "Relay Running",
  local_ready: "Local Ready",
  remote_placeholder_ready: "Remote Placeholder",
  waiting_for_pairing: "Waiting for Pairing",
  connected: "Connected",
  warning: "Warning",
  error: "Error",
};

const STATE_COLORS: Record<string, string> = {
  stopped: "#9AA4B2",
  starting: "#FFB020",
  relay_running: "#4F8CFF",
  local_ready: "#35C759",
  remote_placeholder_ready: "#FFB020",
  waiting_for_pairing: "#4F8CFF",
  connected: "#35C759",
  warning: "#FFB020",
  error: "#FF5C5C",
};

const STATUS_COLORS: Record<string, string> = {
  running: "#35C759",
  starting: "#FFB020",
  stopped: "#9AA4B2",
  error: "#FF5C5C",
};

const SOURCE_COLORS: Record<string, string> = {
  relay: "#4F8CFF",
  bridge: "#35C759",
  app: "#9AA4B2",
};

const LOG_LEVEL_COLORS: Record<string, string> = {
  error: "#FF5C5C",
  warning: "#FFB020",
  info: "#F4F7FA",
  debug: "#9AA4B2",
};

function App() {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [appState, setAppState] = useState("stopped");
  const [status, setStatus] = useState<AppStatus>({
    state: "stopped",
    relay_mode: "local",
    relay: "stopped",
    bridge: "stopped",
    network: "--",
    relay_url: "",
    pairing_payload: null,
    phone_connected: false,
  });
  const [networks, setNetworks] = useState<NetworkInterface[]>([]);
  const [pairingPayload, setPairingPayload] = useState<string | null>(null);
  const [view, setView] = useState<View>("dashboard");
  const [starting, setStarting] = useState(false);
  const [tauriReady, setTauriReady] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [debugInfo, setDebugInfo] = useState<DebugInfo | null>(null);
  const [phoneConnected, setPhoneConnected] = useState(false);
  const [firstRun, setFirstRun] = useState(false);
  const [firewallWarning, setFirewallWarning] = useState<{ ip: string; port: number; message: string } | null>(null);
  const [settings, setSettings] = useState<AppConfig>({
    relay_mode: "local",
    selected_ip: "",
    relay_port: 9000,
    remote_relay_url: "wss://relay.remodex.app",
    auto_start: false,
    auto_restart: false,
    start_minimized: false,
    launch_at_startup: false,
    setup_completed: false,
    requires_entitlement: true,
    free_message_limit: 5,
    relay_path: null,
    bridge_path: null,
    log_level: "info",
  });
  const [settingsPort, setSettingsPort] = useState("9000");
  const [portStatus, setPortStatus] = useState<"checking" | "available" | "taken">("available");
  const logsEndRef = useRef<HTMLDivElement>(null);

  const logError = useCallback((msg: string) => {
    console.error("[RemodexHost]", msg);
    setErrorMsg(msg);
    setTimeout(() => setErrorMsg(null), 10000);
  }, []);

  const addLog = useCallback((entry: LogEntry) => {
    setLogs((prev) => [...prev.slice(-499), entry]);
  }, []);

  // Wait for Tauri to be ready before registering listeners
  useEffect(() => {
    if (!isTauri()) return;

    // Poll until Tauri internals are available
    const checkReady = () => {
      if ((window as unknown as Record<string, unknown>).__TAURI_INTERNALS__) {
        setTauriReady(true);
      } else {
        setTimeout(checkReady, 50);
      }
    };
    checkReady();
  }, []);

  // Load settings when Tauri is ready
  useEffect(() => {
    if (!tauriReady) return;
    invoke<AppConfig>("get_config").then((cfg) => {
      setSettings(cfg);
      setSettingsPort(String(cfg.relay_port));
      if (!cfg.setup_completed) {
        setFirstRun(true);
      }
    }).catch(() => {});
  }, [tauriReady]);


  // Listen for log entries
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen<LogEntry>("log-entry", (event) => {
      addLog(event.payload);
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => {
      unlistenFn?.();
    };
  }, [tauriReady, addLog]);

  // Listen for status changes
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen<AppStatus>("status-changed", (event) => {
      setStatus((prev) => ({ ...prev, ...event.payload }));
      if (event.payload.state) setAppState(event.payload.state);
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => {
      unlistenFn?.();
    };
  }, [tauriReady]);

  // Listen for pairing ready
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen<string>("pairing-ready", (event) => {
      setPairingPayload(event.payload);
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => {
      unlistenFn?.();
    };
  }, [tauriReady]);

  // Listen for phone connection from relay logs
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen<string>("phone-connected", () => {
      setPhoneConnected(true);
      setAppState("connected");
      invoke("notify", { title: "Remodex Host", body: "Phone connected!" }).catch(() => {});
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => {
      unlistenFn?.();
    };
  }, [tauriReady]);

  // Listen for phone disconnection
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen<string>("phone-disconnected", () => {
      setPhoneConnected(false);
      if (appState === "connected") setAppState("running");
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => {
      unlistenFn?.();
    };
  }, [tauriReady]);

  // Listen for first-run
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen("first-run", () => {
      setFirstRun(true);
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => { unlistenFn?.(); };
  }, [tauriReady]);

  // Listen for firewall warning
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen<{ ip: string; port: number; message: string }>("firewall-warning", (event) => {
      setFirewallWarning(event.payload);
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => { unlistenFn?.(); };
  }, [tauriReady]);

  // Listen for process crashes
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen<{ process: string; exit_code: number }>("process-crashed", (event) => {
      const { process, exit_code } = event.payload;
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "error",
        message: `${process} crashed (exit code: ${exit_code})`,
      });
      setErrorMsg(`${process} crashed with exit code ${exit_code}`);
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => {
      unlistenFn?.();
    };
  }, [tauriReady, addLog]);

  // Listen for show-qr event from tray
  useEffect(() => {
    if (!tauriReady) return;
    let unlistenFn: (() => void) | null = null;

    listen("show-qr", async () => {
      const win = getCurrentWindow();
      await win.show().catch(() => {});
      await win.setFocus().catch(() => {});
      setView("dashboard");
    }).then((fn) => {
      unlistenFn = fn;
    });

    return () => {
      unlistenFn?.();
    };
  }, [tauriReady]);

  // Scroll logs to bottom
  useEffect(() => {
    logsEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [logs]);

  useEffect(() => {
    if (!pairingPayload) return;
    const canvas = document.getElementById("qr-canvas") as HTMLCanvasElement | null;
    if (!canvas) return;
    QRCode.toCanvas(canvas, pairingPayload, { width: 200, margin: 1 }).catch(() => {});
  }, [pairingPayload]);

  // Poll status periodically
  useEffect(() => {
    if (!tauriReady) return;

    const poll = async () => {
      try {
        const s = await invoke<AppStatus>("get_status");
        setStatus(s);
        setAppState(s.state);
        setPhoneConnected(s.phone_connected);
        if (s.pairing_payload) {
          setPairingPayload(s.pairing_payload);
        }
      } catch (_) {}
    };
    poll();
    const interval = setInterval(poll, 3000);
    return () => clearInterval(interval);
  }, [tauriReady]);

  const handleStartAll = async () => {
    if (!tauriReady) return;
    setStarting(true);
    setAppState("starting");
    try {
      await invoke<string>("start_all");
    } catch (e) {
      logError(`Start failed: ${e}`);
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "error",
        message: `Start failed: ${e}`,
      });
      setAppState("error");
    }
    setStarting(false);
  };

  const handleStopAll = async () => {
    if (!tauriReady) return;
    try {
      await invoke<string>("stop_bridge");
      if (status.relay_mode !== "remote") {
        await invoke<string>("stop_relay");
      }
      setAppState("stopped");
      setPairingPayload(null);
      setPhoneConnected(false);
    } catch (e) {
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "error",
        message: `Stop failed: ${e}`,
      });
    }
  };

  const handleRestartBridge = async () => {
    if (!tauriReady) return;
    try {
      await invoke("stop_bridge");
      await new Promise((r) => setTimeout(r, 500));
      await invoke<string>("start_bridge");
    } catch (e) {
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "error",
        message: `Restart bridge failed: ${e}`,
      });
    }
  };

  const handleRestartRelay = async () => {
    if (!tauriReady) return;
    try {
      await invoke("stop_relay");
      await new Promise((r) => setTimeout(r, 500));
      await invoke<string>("start_relay");
    } catch (e) {
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "error",
        message: `Restart relay failed: ${e}`,
      });
    }
  };

  const handleLoadNetworks = async () => {
    if (!tauriReady) return;
    try {
      const nics = await invoke<NetworkInterface[]>("detect_networks");
      setNetworks(nics);
      setView("network");
    } catch (e) {
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "error",
        message: `Network detection failed: ${e}`,
      });
    }
  };

  const handleSelectNetwork = async (ip: string) => {
    if (!tauriReady) return;
    try {
      await invoke("select_network", { ip });
      setStatus((prev) => ({ ...prev, network: ip }));
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "info",
        message: `Selected network: ${ip}`,
      });
      setView("dashboard");
    } catch (e) {
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "error",
        message: `Select network failed: ${e}`,
      });
    }
  };

  const handleClearLogs = async () => {
    if (!tauriReady) return;
    try {
      await invoke("clear_logs");
      setLogs([]);
    } catch (_) {}
  };

  const handleDebug = async () => {
    try {
      const info = await invoke<DebugInfo>("debug_paths");
      console.log("[RemodexHost] Debug info:", info);
      setDebugInfo(info);
      setView("debug");
    } catch (e) {
      logError(`Debug failed: ${e}`);
    }
  };

  const handleSaveSettings = async () => {
    if (!tauriReady) return;
    const newConfig: AppConfig = {
      ...settings,
      relay_port: parseInt(settingsPort) || 9000,
    };
    try {
      await invoke("save_config_cmd", { config: newConfig });
      setSettings(newConfig);
      addLog({
        timestamp: new Date().toLocaleTimeString(),
        source: "app",
        level: "info",
        message: "Settings saved.",
      });
    } catch (e) {
      logError(`Save failed: ${e}`);
    }
  };

  const handleCheckPort = async (port: string) => {
    setSettingsPort(port);
    const portNum = parseInt(port) || 0;
    if (portNum < 1 || portNum > 65535) return;
    setPortStatus("checking");
    try {
      const result = await invoke<{ available: boolean; suggested: number | null }>("check_port", { port: portNum });
      setPortStatus(result.available ? "available" : "taken");
      if (!result.available && result.suggested) {
        setSettingsPort(String(result.suggested));
      }
    } catch {
      setPortStatus("available");
    }
  };

  const handleCopyUrl = async () => {
    if (status.relay_url) {
      await navigator.clipboard.writeText(status.relay_url);
    }
  };

  const isStopped = appState === "stopped" || appState === "error";

  return (
    <div
      style={{
        background: "var(--bg-primary)",
        height: "100vh",
        display: "flex",
        flexDirection: "column",
        padding: "14px",
        gap: "10px",
      }}
    >
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <div style={{ fontSize: "15px", fontWeight: 700, color: "var(--text-primary)", letterSpacing: "-0.3px" }}>
            Remodex Host
          </div>
          <div style={{ fontSize: "10px", color: "var(--text-secondary)", marginTop: "1px" }}>
            Local bridge manager
          </div>
        </div>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "5px",
            background: "var(--bg-surface)",
            padding: "3px 8px",
            borderRadius: "5px",
            border: "1px solid var(--border-color)",
          }}
        >
          <div
            style={{
              width: "6px",
              height: "6px",
              borderRadius: "50%",
              background: STATE_COLORS[appState] || "#9AA4B2",
              animation: appState === "starting" ? "pulse 1s infinite" : "none",
            }}
          />
          <span style={{ fontSize: "10px", color: "var(--text-secondary)", fontWeight: 500 }}>
            {STATE_LABELS[appState] || appState}
          </span>
        </div>
      </div>

      {/* Error Banner */}
      {errorMsg && (
        <div
          style={{
            background: "#FF5C5C15",
            border: "1px solid #FF5C5C30",
            borderRadius: "6px",
            padding: "8px 10px",
            fontSize: "11px",
            color: "#FF5C5C",
            fontFamily: "monospace",
            wordBreak: "break-all",
          }}
        >
          {errorMsg}
        </div>
      )}

      {/* Firewall Warning */}
      {firewallWarning && (
        <div
          style={{
            background: "#FFB02015",
            border: "1px solid #FFB02030",
            borderRadius: "6px",
            padding: "8px 10px",
            fontSize: "11px",
            color: "#FFB020",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
          }}
        >
          <span style={{ flex: 1 }}>{firewallWarning.message}</span>
          <button
            onClick={() => setFirewallWarning(null)}
            style={{
              background: "none",
              border: "none",
              color: "#FFB020",
              cursor: "pointer",
              fontSize: "14px",
              padding: "0 0 0 8px",
              lineHeight: 1,
            }}
          >
            x
          </button>
        </div>
      )}

      {/* Onboarding / First-run */}
      {firstRun && (
        <div
          style={{
            flex: 1,
            background: "var(--bg-surface)",
            borderRadius: "7px",
            border: "1px solid var(--border-color)",
            padding: "16px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            textAlign: "center",
            gap: "16px",
          }}
        >
          <div style={{ fontSize: "16px", fontWeight: 700, color: "var(--text-primary)" }}>
            Welcome to Remodex Host
          </div>
          <div style={{ fontSize: "11px", color: "var(--text-secondary)", maxWidth: "300px" }}>
            This app lets you pair your phone with your PC to use Remodex without terminals.
            Let's get your local relay and bridge running.
          </div>
          <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
            <ActionBtn
              label="Let's Go"
              color="#35C759"
              onClick={async () => {
                setFirstRun(false);
                await invoke("complete_setup");
                handleLoadNetworks();
              }}
            />
          </div>
        </div>
      )}

      {/* View: Dashboard, Network, Logs, Debug */}
      {view === "dashboard" && (
        <>
          {/* Relay Mode Selector */}
          <div
            style={{
              background: "var(--bg-surface)",
              borderRadius: "7px",
              border: "1px solid var(--border-color)",
              padding: "8px 12px",
              display: "flex",
              gap: "8px",
            }}
          >
            <ModeBtn
              label="Local LAN"
              active={status.relay_mode === "local" || !status.relay_mode}
              desc="Free"
              onClick={async () => {
                if (status.relay_mode !== "local") {
                  await invoke("set_relay_mode", { mode: "local" });
                  setStatus((p) => ({ ...p, relay_mode: "local", relay: "stopped" }));
                }
              }}
            />
            <ModeBtn
              label="Remote"
              active={status.relay_mode === "remote"}
              desc="Placeholder"
              onClick={async () => {
                if (status.relay_mode !== "remote") {
                  await invoke("set_relay_mode", { mode: "remote" });
                  setStatus((p) => ({ ...p, relay_mode: "remote", relay: "external" }));
                }
              }}
            />
          </div>

          {/* Remote placeholder banner */}
          {status.relay_mode === "remote" && (
            <div
              style={{
                background: "#FFB02015",
                border: "1px solid #FFB02030",
                borderRadius: "6px",
                padding: "6px 10px",
                fontSize: "10px",
                color: "#FFB020",
              }}
            >
              Remote relay is prepared but not active. Subscription handled by mobile app later.
            </div>
          )}

          {/* Status Cards */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px" }}>
            <StatusCard label="Relay" status={status.relay} />
            <StatusCard label="Bridge" status={status.bridge} />
            <StatusCard
              label="Network"
              status={status.network && status.network !== "--" ? "connected" : "unknown"}
              value={status.network || "--"}
              onClick={handleLoadNetworks}
            />
            <StatusCard
              label="Phone"
              status={phoneConnected ? "running" : "unknown"}
              value={phoneConnected ? "Connected" : "--"}
            />
          </div>

          {/* Connection Info */}
          {status.relay_url && (
            <div
              style={{
                background: "var(--bg-surface)",
                borderRadius: "7px",
                border: "1px solid var(--border-color)",
                padding: "10px 12px",
                display: "flex",
                flexDirection: "column",
                gap: "6px",
              }}
            >
              <div style={{ fontSize: "11px", fontWeight: 600, color: "var(--text-secondary)" }}>
                Connection
              </div>
              <div
                style={{
                  fontSize: "11px",
                  fontFamily: "ui-monospace, Consolas, monospace",
                  color: "var(--text-primary)",
                  background: "var(--bg-primary)",
                  padding: "5px 8px",
                  borderRadius: "4px",
                  wordBreak: "break-all",
                }}
              >
                {status.relay_url}
              </div>
              <button
                onClick={handleCopyUrl}
                style={{
                  fontSize: "10px",
                  color: "var(--accent-blue)",
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  alignSelf: "flex-start",
                  padding: 0,
                }}
              >
                Copy URL
              </button>
            </div>
          )}

          {/* QR Code — always visible */}
          <div
            style={{
              background: "var(--bg-surface)",
              borderRadius: "7px",
              border: "1px solid var(--border-color)",
              padding: "12px",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <div style={{ fontSize: "11px", fontWeight: 600, color: "var(--text-secondary)" }}>
              Scan with Remodex mobile app
            </div>
            {pairingPayload ? (
              <div
                style={{
                  background: "#FFFFFF",
                  padding: "8px",
                  borderRadius: "6px",
                  lineHeight: 0,
                }}
              >
                <canvas id="qr-canvas" width="200" height="200" style={{ display: "block" }}></canvas>
              </div>
            ) : (
              <div
                style={{
                  width: "200px",
                  height: "200px",
                  background: "var(--bg-primary)",
                  borderRadius: "6px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <span style={{ fontSize: "11px", color: "var(--text-secondary)" }}>
                  {status.relay === "running" && status.bridge === "running"
                    ? "Generating QR..."
                    : "Start relay to generate QR"}
                </span>
              </div>
            )}
            {pairingPayload && (
              <div
                style={{
                  fontSize: "10px",
                  color: "var(--text-secondary)",
                  fontFamily: "monospace",
                  textAlign: "center",
                  wordBreak: "break-all",
                  maxWidth: "100%",
                }}
              >
                {pairingPayload.slice(0, 80)}...
              </div>
            )}
          </div>

          {/* Actions */}
          <div style={{ display: "flex", gap: "6px" }}>
            {isStopped ? (
              <ActionBtn label={starting ? "Starting..." : "Start All"} color="#35C759" onClick={handleStartAll} disabled={starting || !tauriReady} />
            ) : (
              <ActionBtn label="Stop All" color="#FF5C5C" onClick={handleStopAll} disabled={!tauriReady} />
            )}
            <ActionBtn label="Restart Bridge" color="#4F8CFF" onClick={handleRestartBridge} disabled={status.bridge !== "running" || !tauriReady} />
            <ActionBtn label="Restart Relay" color="#FFB020" onClick={handleRestartRelay} disabled={status.relay !== "running" || !tauriReady} />
          </div>
        </>
      )}

      {view === "network" && (
        <div
          style={{
            flex: 1,
            background: "var(--bg-surface)",
            borderRadius: "7px",
            border: "1px solid var(--border-color)",
            padding: "12px",
            overflow: "auto",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
            <div style={{ fontSize: "12px", fontWeight: 600, color: "var(--text-primary)" }}>
              Choose Network
            </div>
            <button
              onClick={() => setView("dashboard")}
              style={{
                fontSize: "10px",
                color: "var(--text-secondary)",
                background: "none",
                border: "none",
                cursor: "pointer",
              }}
            >
              Back
            </button>
          </div>
          <div style={{ fontSize: "10px", color: "var(--text-secondary)", marginBottom: "10px" }}>
            Select the network your phone is connected to.
          </div>
          {networks.length === 0 ? (
            <div style={{ textAlign: "center", padding: "20px", color: "var(--text-secondary)", fontSize: "11px" }}>
              No networks detected. Connect to Wi-Fi or Ethernet.
            </div>
          ) : (
            networks.map((nic, i) => (
              <div
                key={i}
                onClick={() => handleSelectNetwork(nic.address)}
                style={{
                  padding: "8px 10px",
                  marginBottom: "4px",
                  background: status.network === nic.address ? "var(--bg-elevated)" : "transparent",
                  borderRadius: "5px",
                  border: status.network === nic.address ? "1px solid var(--accent-blue)" : "1px solid transparent",
                  cursor: "pointer",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  transition: "all 0.1s",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                  <div style={{ fontSize: "11px", color: "var(--text-primary)", fontWeight: 500 }}>
                    {nic.name}
                  </div>
                  {nic.is_private && (
                    <span
                      style={{
                        fontSize: "9px",
                        padding: "1px 5px",
                        background: "#35C75920",
                        color: "#35C759",
                        borderRadius: "3px",
                        fontWeight: 600,
                      }}
                    >
                      RECOMMENDED
                    </span>
                  )}
                  <span
                    style={{
                      fontSize: "9px",
                      padding: "1px 5px",
                      background: "var(--bg-primary)",
                      color: "var(--text-secondary)",
                      borderRadius: "3px",
                    }}
                  >
                    {nic.kind}
                  </span>
                </div>
                <span
                  style={{
                    fontSize: "11px",
                    fontFamily: "monospace",
                    color: status.network === nic.address ? "var(--accent-blue)" : "var(--text-secondary)",
                  }}
                >
                  {nic.address}
                </span>
              </div>
            ))
          )}
        </div>
      )}

      {view === "logs" && (
        <div
          style={{
            flex: 1,
            background: "var(--bg-surface)",
            borderRadius: "7px",
            border: "1px solid var(--border-color)",
            display: "flex",
            flexDirection: "column",
            minHeight: 0,
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              padding: "6px 10px",
              borderBottom: "1px solid var(--border-color)",
              flexShrink: 0,
            }}
          >
            <span style={{ fontSize: "11px", fontWeight: 600, color: "var(--text-secondary)" }}>
              Logs ({logs.length} lines)
            </span>
            <div style={{ display: "flex", gap: "4px" }}>
              <ActionBtn
                label="Back"
                color="#9AA4B2"
                onClick={() => setView("dashboard")}
                compact
              />
              <ActionBtn
                label="Copy All"
                color="#4F8CFF"
                onClick={() => {
                  const text = logs.map((l) => `${l.timestamp} [${l.source}] ${l.level}: ${l.message}`).join("\n");
                  navigator.clipboard.writeText(text);
                }}
                compact
              />
              <ActionBtn
                label="Clear"
                color="#FF5C5C"
                onClick={handleClearLogs}
                compact
              />
            </div>
          </div>
          <div
            className="logs-area"
            style={{
              flex: 1,
              overflow: "auto",
              minHeight: 0,
            }}
          >
            {logs.length === 0 ? (
              <div style={{ padding: "20px", textAlign: "center", color: "var(--text-secondary)", fontSize: "11px" }}>
                No logs yet.
              </div>
            ) : (
              logs.map((log, i) => (
                <div
                  key={i}
                  onClick={() => {
                    const line = `${log.timestamp} [${log.source}] ${log.level}: ${log.message}`;
                    navigator.clipboard.writeText(line);
                  }}
                  title="Click to copy"
                  style={{
                    fontSize: "10px",
                    fontFamily: "ui-monospace, Consolas, monospace",
                    padding: "1px 8px",
                    lineHeight: "1.6",
                    cursor: "pointer",
                    borderRadius: "2px",
                    transition: "background 0.08s",
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLDivElement).style.background = "var(--bg-elevated)";
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLDivElement).style.background = "transparent";
                  }}
                >
                  <span style={{ color: "var(--text-secondary)", marginRight: "6px" }}>
                    {log.timestamp}
                  </span>
                  <span
                    style={{
                      color: SOURCE_COLORS[log.source] || "#9AA4B2",
                      fontWeight: 600,
                      marginRight: "6px",
                    }}
                  >
                    {log.source}
                  </span>
                  <span style={{ color: LOG_LEVEL_COLORS[log.level] || "#F4F7FA" }}>
                    {log.message}
                  </span>
                </div>
              ))
            )}
            <div ref={logsEndRef} />
          </div>
        </div>
      )}

      {view === "settings" && (
        <div
          style={{
            flex: 1,
            background: "var(--bg-surface)",
            borderRadius: "7px",
            border: "1px solid var(--border-color)",
            padding: "12px",
            overflow: "auto",
          }}
        >
          <div style={{ fontSize: "12px", fontWeight: 600, color: "var(--text-primary)", marginBottom: "12px" }}>
            Settings
          </div>

          {/* Relay Port */}
          <div style={{ marginBottom: "12px" }}>
            <label style={{ fontSize: "11px", color: "var(--text-secondary)", display: "block", marginBottom: "4px" }}>
              Relay Port
            </label>
            <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
              <input
                type="number"
                value={settingsPort}
                onChange={(e) => handleCheckPort(e.target.value)}
                style={{
                  flex: 1,
                  padding: "6px 8px",
                  fontSize: "11px",
                  fontFamily: "monospace",
                  background: "var(--bg-primary)",
                  border: `1px solid ${portStatus === "taken" ? "#FF5C5C" : "var(--border-color)"}`,
                  borderRadius: "4px",
                  color: "var(--text-primary)",
                  outline: "none",
                }}
              />
              <span style={{
                fontSize: "10px",
                color: portStatus === "checking" ? "var(--warning-amber)"
                  : portStatus === "available" ? "var(--success-green)"
                  : "#FF5C5C",
                minWidth: "60px",
              }}>
                {portStatus === "checking" ? "..." : portStatus === "available" ? "free" : "taken"}
              </span>
            </div>
          </div>

          {/* Auto Restart */}
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              marginBottom: "12px",
              cursor: "pointer",
              fontSize: "11px",
              color: "var(--text-secondary)",
            }}
          >
            <input
              type="checkbox"
              checked={settings.auto_restart}
              onChange={(e) => setSettings({ ...settings, auto_restart: e.target.checked })}
              style={{ accentColor: "var(--accent-blue)" }}
            />
            Auto-restart crashed processes
          </label>

          {/* Start Minimized */}
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              marginBottom: "12px",
              cursor: "pointer",
              fontSize: "11px",
              color: "var(--text-secondary)",
            }}
          >
            <input
              type="checkbox"
              checked={settings.start_minimized}
              onChange={(e) => setSettings({ ...settings, start_minimized: e.target.checked })}
              style={{ accentColor: "var(--accent-blue)" }}
            />
            Start minimized to tray
          </label>

          {/* Launch at Startup */}
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              marginBottom: "12px",
              cursor: "pointer",
              fontSize: "11px",
              color: "var(--text-secondary)",
            }}
          >
            <input
              type="checkbox"
              checked={settings.launch_at_startup}
              onChange={(e) => setSettings({ ...settings, launch_at_startup: e.target.checked })}
              style={{ accentColor: "var(--accent-blue)" }}
            />
            Launch at Windows startup
          </label>

          {/* Remote Relay URL */}
          <div style={{ marginBottom: "10px" }}>
            <label style={{ fontSize: "11px", color: "var(--text-secondary)", display: "block", marginBottom: "4px" }}>
              Remote Relay URL (placeholder)
            </label>
            <input
              type="text"
              value={settings.remote_relay_url || ""}
              onChange={(e) => setSettings({ ...settings, remote_relay_url: e.target.value })}
              placeholder="wss://relay.remodex.app"
              style={{
                width: "100%",
                padding: "6px 8px",
                fontSize: "10px",
                fontFamily: "monospace",
                background: "var(--bg-primary)",
                border: "1px solid var(--border-color)",
                borderRadius: "4px",
                color: "var(--text-primary)",
                outline: "none",
              }}
            />
          </div>

          {/* Relay Path */}
          <div style={{ marginBottom: "10px" }}>
            <label style={{ fontSize: "11px", color: "var(--text-secondary)", display: "block", marginBottom: "4px" }}>
              Relay path (custom)
            </label>
            <input
              type="text"
              value={settings.relay_path || ""}
              onChange={(e) => setSettings({ ...settings, relay_path: e.target.value || null })}
              placeholder="default (auto-detect)"
              style={{
                width: "100%",
                padding: "6px 8px",
                fontSize: "10px",
                fontFamily: "monospace",
                background: "var(--bg-primary)",
                border: "1px solid var(--border-color)",
                borderRadius: "4px",
                color: "var(--text-primary)",
                outline: "none",
              }}
            />
          </div>

          {/* Bridge Path */}
          <div style={{ marginBottom: "12px" }}>
            <label style={{ fontSize: "11px", color: "var(--text-secondary)", display: "block", marginBottom: "4px" }}>
              Bridge path (custom)
            </label>
            <input
              type="text"
              value={settings.bridge_path || ""}
              onChange={(e) => setSettings({ ...settings, bridge_path: e.target.value || null })}
              placeholder="default (auto-detect)"
              style={{
                width: "100%",
                padding: "6px 8px",
                fontSize: "10px",
                fontFamily: "monospace",
                background: "var(--bg-primary)",
                border: "1px solid var(--border-color)",
                borderRadius: "4px",
                color: "var(--text-primary)",
                outline: "none",
              }}
            />
          </div>

          {/* Save */}
          <div style={{ display: "flex", gap: "6px" }}>
            <ActionBtn label="Save" color="#35C759" onClick={handleSaveSettings} />
            <ActionBtn label="Cancel" color="#9AA4B2" onClick={() => setView("dashboard")} />
          </div>
        </div>
      )}

      {view === "debug" && debugInfo && (
        <div
          style={{
            flex: 1,
            background: "var(--bg-surface)",
            borderRadius: "7px",
            border: "1px solid var(--border-color)",
            padding: "12px",
            overflow: "auto",
            fontFamily: "monospace",
            fontSize: "11px",
            lineHeight: "1.7",
          }}
        >
          <div style={{ fontSize: "12px", fontWeight: 600, color: "var(--text-primary)", marginBottom: "8px" }}>
            Diagnostics
          </div>
          {[
            ["CWD", debugInfo.cwd],
            ["Repo root", debugInfo.repo_root],
            ["Node.js", debugInfo.node_version],
            ["Relay dir", debugInfo.relay_dir],
            ["  server.js exists", String(debugInfo.relay_server_exists)],
            ["Bridge dir", debugInfo.bridge_dir],
            ["  remodex.js exists", String(debugInfo.bridge_bin_exists)],
            ["Config path", debugInfo.config_path],
            ["Config exists", String(debugInfo.config_exists)],
          ].map(([label, value]) => (
            <div key={label} style={{ display: "flex", gap: "8px", color: "var(--text-secondary)" }}>
              <span style={{ color: "var(--text-primary)", minWidth: "160px" }}>{label}</span>
              <span style={{
                color: value?.startsWith("false") || value?.startsWith("unknown")
                  ? "#FF5C5C" : value?.startsWith("true") ? "#35C759" : "var(--text-secondary)"
              }}>
                {value}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Bottom tabs */}
      <div style={{ display: "flex", gap: "2px", background: "var(--bg-surface)", borderRadius: "6px", padding: "2px" }}>
        <ViewTab label="Dashboard" active={view === "dashboard"} onClick={() => setView("dashboard")} />
        <ViewTab label="Network" active={view === "network"} onClick={handleLoadNetworks} />
        <ViewTab label="Logs" active={view === "logs"} onClick={() => setView("logs")} />
        <ViewTab label="Settings" active={view === "settings"} onClick={() => {
          invoke<AppConfig>("get_config").then(setSettings).catch(() => {});
          setSettingsPort(String(settings.relay_port));
          setView("settings");
        }} />
        <ViewTab label="Debug" active={view === "debug"} onClick={handleDebug} />
      </div>
    </div>
  );
}

function StatusCard({
  label,
  status,
  value,
  onClick,
}: {
  label: string;
  status: string;
  value?: string;
  onClick?: () => void;
}) {
  const color = STATUS_COLORS[status] || "#9AA4B2";
  return (
    <div
      onClick={onClick}
      style={{
        background: "var(--bg-surface)",
        borderRadius: "7px",
        border: "1px solid var(--border-color)",
        padding: "9px 11px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        cursor: onClick ? "pointer" : "default",
        transition: "border-color 0.15s",
      }}
    >
      <span style={{ fontSize: "11px", color: "var(--text-secondary)", fontWeight: 500 }}>{label}</span>
      {value !== undefined ? (
        <span style={{ fontSize: "11px", fontFamily: "monospace", color: "var(--text-primary)" }}>{value}</span>
      ) : (
        <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
          <div style={{ width: "5px", height: "5px", borderRadius: "50%", background: color }} />
          <span style={{ fontSize: "11px", color: color === "#9AA4B2" ? "var(--text-secondary)" : color, fontWeight: 500, textTransform: "capitalize" }}>
            {status}
          </span>
        </div>
      )}
    </div>
  );
}

function ActionBtn({
  label,
  color,
  onClick,
  disabled,
  compact,
}: {
  label: string;
  color: string;
  onClick: () => void;
  disabled?: boolean;
  compact?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        flex: compact ? "none" : 1,
        padding: compact ? "4px 8px" : "7px",
        fontSize: compact ? "10px" : "11px",
        fontWeight: 500,
        color: disabled ? "var(--text-secondary)" : color,
        background: disabled ? "var(--bg-surface)" : `${color}15`,
        border: `1px solid ${disabled ? "var(--border-color)" : `${color}30`}`,
        borderRadius: "5px",
        cursor: disabled ? "default" : "pointer",
        opacity: disabled ? 0.5 : 1,
        transition: "all 0.1s",
      }}
    >
      {label}
    </button>
  );
}

function ViewTab({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{
        flex: 1,
        padding: "6px",
        fontSize: "10px",
        fontWeight: 500,
        color: active ? "var(--text-primary)" : "var(--text-secondary)",
        background: active ? "var(--bg-elevated)" : "transparent",
        border: "none",
        borderRadius: "4px",
        cursor: "pointer",
        transition: "all 0.1s",
      }}
    >
      {label}
    </button>
  );
}

function ModeBtn({
  label,
  desc,
  active,
  onClick,
}: {
  label: string;
  desc: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      style={{
        flex: 1,
        padding: "6px 8px",
        fontSize: "11px",
        fontWeight: 500,
        color: active ? "var(--accent-blue)" : "var(--text-secondary)",
        background: active ? "var(--accent-blue)15" : "transparent",
        border: `1px solid ${active ? "var(--accent-blue)" : "var(--border-color)"}`,
        borderRadius: "5px",
        cursor: "pointer",
        textAlign: "left",
        transition: "all 0.1s",
      }}
    >
      <div style={{ fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: "9px", opacity: 0.7 }}>{desc}</div>
    </button>
  );
}

export default App;
