#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::sync::Mutex;
use std::process::{Child, Command, Stdio};
use std::io::{BufRead, BufReader};
use std::net::{TcpListener, TcpStream};
use std::time::Duration;
use std::fs;
use tauri::Manager;
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::menu::{MenuBuilder, MenuItemBuilder};
use tauri::Emitter;
use tauri_plugin_updater::UpdaterExt;

#[cfg(target_os = "windows")]
use std::os::windows::process::CommandExt;

const CREATE_NO_WINDOW: u32 = 0x08000000;

// ─── Data types ──────────────────────────────────────────────

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
pub struct LogEntry {
    pub timestamp: String,
    pub source: String,
    pub level: String,
    pub message: String,
}

#[derive(serde::Serialize, Clone, Debug)]
pub struct NetworkInterface {
    pub name: String,
    pub address: String,
    pub kind: String,
    pub is_private: bool,
}

#[derive(serde::Serialize, Clone, Debug)]
pub struct DebugInfo {
    pub cwd: String,
    pub repo_root: String,
    pub relay_dir: String,
    pub relay_server_exists: bool,
    pub bridge_dir: String,
    pub bridge_bin_exists: bool,
    pub config_path: String,
    pub config_exists: bool,
    pub node_version: String,
}

#[derive(serde::Serialize, Clone, Debug)]
pub struct UpdateInfo {
    pub version: String,
    pub current_version: String,
    pub date: Option<String>,
    pub body: Option<String>,
}

#[derive(serde::Serialize, Clone, Debug)]
pub struct AppStatus {
    pub state: String,
    pub relay_mode: String,
    pub relay: String,
    pub bridge: String,
    pub network: String,
    pub relay_url: String,
    pub pairing_payload: Option<String>,
    pub pairing_code: Option<String>,
    pub phone_connected: bool,
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
pub struct AppConfig {
    #[serde(default = "default_relay_mode")]
    pub relay_mode: String, // "local" | "remote"
    pub selected_ip: String,
    pub relay_port: u16,
    #[serde(default)]
    pub remote_relay_url: String,
    #[serde(default)]
    pub auto_start: bool,
    #[serde(default)]
    pub auto_restart: bool,
    #[serde(default)]
    pub start_minimized: bool,
    #[serde(default)]
    pub launch_at_startup: bool,
    #[serde(default)]
    pub setup_completed: bool,
    #[serde(default)]
    pub requires_entitlement: bool,
    #[serde(default = "default_free_message_limit")]
    pub free_message_limit: u32,
    pub relay_path: Option<String>,
    pub bridge_path: Option<String>,
    #[serde(default = "default_log_level")]
    pub log_level: String,
}

fn default_relay_mode() -> String { "local".to_string() }
fn default_free_message_limit() -> u32 { 5 }
fn default_log_level() -> String { "info".to_string() }

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            relay_mode: "local".to_string(),
            selected_ip: String::new(),
            relay_port: 9000,
            remote_relay_url: "wss://relay.remodex.app".to_string(),
            auto_start: false,
            auto_restart: false,
            start_minimized: false,
            launch_at_startup: false,
            setup_completed: false,
            requires_entitlement: true,
            free_message_limit: 5,
            relay_path: None,
            bridge_path: None,
            log_level: "info".to_string(),
        }
    }
}

// ─── State ───────────────────────────────────────────────────

pub struct AppState {
    pub relay_process: Mutex<Option<Child>>,
    pub bridge_process: Mutex<Option<Child>>,
    pub logs: Mutex<Vec<LogEntry>>,
    pub config: Mutex<AppConfig>,
    pub pairing_payload: Mutex<Option<String>>,
    pub selected_ip: Mutex<String>,
    pub relay_url: Mutex<String>,
    pub phone_connected: Mutex<bool>,
    pub pairing_code: Mutex<Option<String>>,
    pub relay_intentional: Mutex<bool>,
    pub bridge_intentional: Mutex<bool>,
}

// ─── Helpers ─────────────────────────────────────────────────

fn get_repo_root() -> std::path::PathBuf {
    // Bundled production mode: copy to writable app data dir
    if let Ok(exe_path) = std::env::current_exe() {
        if let Some(exe_dir) = exe_path.parent() {
            let bundled_relay = exe_dir.join("bundled").join("relay").join("server.js");
            let bundled_bridge = exe_dir.join("bundled").join("phodex-bridge").join("bin").join("remodex.js");
            if bundled_relay.exists() && bundled_bridge.exists() {
                // Copy bundled files to writable app data directory
                let runtime_dir = dirs::data_dir()
                    .unwrap_or_else(|| std::path::PathBuf::from("."))
                    .join("remodex-host")
                    .join("runtime");

                // Copy relay if not already there
                let relay_dest = runtime_dir.join("relay");
                if !relay_dest.join("server.js").exists() {
                    let _ = fs::create_dir_all(&relay_dest);
                    let relay_src = exe_dir.join("bundled").join("relay");
                    if let Err(e) = copy_dir_recursive(&relay_src, &relay_dest) {
                        log::warn!("Failed to copy relay to runtime dir: {e}");
                    }
                }

                // Copy bridge if not already there
                let bridge_dest = runtime_dir.join("phodex-bridge");
                if !bridge_dest.join("bin").join("remodex.js").exists() {
                    let _ = fs::create_dir_all(&bridge_dest);
                    let bridge_src = exe_dir.join("bundled").join("phodex-bridge");
                    if let Err(e) = copy_dir_recursive(&bridge_src, &bridge_dest) {
                        log::warn!("Failed to copy bridge to runtime dir: {e}");
                    }
                }

                return runtime_dir;
            }
        }
    }

    // Dev mode: walk up from cwd
    let cwd = match std::env::current_dir() {
        Ok(p) => p,
        Err(_) => return std::path::PathBuf::from("."),
    };

    let mut current = cwd.clone();
    for _ in 0..5 {
        if current.join("relay").join("server.js").exists()
            && current.join("phodex-bridge").join("bin").join("remodex.js").exists()
        {
            return current;
        }
        if let Some(parent) = current.parent() {
            current = parent.to_path_buf();
        } else {
            break;
        }
    }

    current = cwd.clone();
    for _ in 0..2 {
        if let Some(parent) = current.parent() {
            current = parent.to_path_buf();
        }
    }
    current
}

fn copy_dir_recursive(src: &std::path::Path, dest: &std::path::Path) -> std::io::Result<()> {
    if !src.exists() {
        return Ok(());
    }
    fs::create_dir_all(dest)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let file_type = entry.file_type()?;
        let dest_path = dest.join(entry.file_name());
        if file_type.is_dir() {
            if entry.file_name() != "node_modules" && entry.file_name() != ".git" {
                copy_dir_recursive(&entry.path(), &dest_path)?;
            }
        } else {
            let _ = fs::copy(entry.path(), &dest_path);
        }
    }
    Ok(())
}

fn config_path() -> std::path::PathBuf {
    if let Some(dir) = dirs::config_dir() {
        let p = dir.join("remodex-host").join("config.json");
        if let Some(parent) = p.parent() {
            let _ = fs::create_dir_all(parent);
        }
        p
    } else {
        std::path::PathBuf::from("remodex-host-config.json")
    }
}

fn add_log(app_handle: &tauri::AppHandle, source: &str, level: &str, message: &str) {
    let state = app_handle.state::<AppState>();
    let entry = LogEntry {
        timestamp: chrono::Local::now().format("%H:%M:%S").to_string(),
        source: source.to_string(),
        level: level.to_string(),
        message: message.to_string(),
    };

    if let Ok(mut logs) = state.logs.lock() {
        logs.push(entry.clone());
        if logs.len() > 1000 {
            logs.remove(0);
        }
    }

    let _ = app_handle.emit("log-entry", entry);

    // Detect phone connection from relay logs
    if source == "relay" && message.contains("Mobile connected") {
        let _ = app_handle.emit("phone-connected", message.to_string());
        if let Ok(mut pc) = state.phone_connected.lock() {
            *pc = true;
        }
        show_notification("Remodex Host", "Phone connected!");
    }

    // Detect phone disconnect
    if source == "relay" && message.contains("Mobile disconnected") {
        let _ = app_handle.emit("phone-disconnected", message.to_string());
        if let Ok(mut pc) = state.phone_connected.lock() {
            *pc = false;
        }
    }
}

// ─── Registry helpers (Windows) ──────────────────────────────

fn set_launch_at_startup(enable: bool) {
    #[cfg(target_os = "windows")]
    {
        use std::process::Command;
        let exe_path = std::env::current_exe()
            .map(|p| p.display().to_string())
            .unwrap_or_default();
        let key = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
    let name = "RemodexHost";
    if enable {
        let mut cmd = Command::new("reg");
        cmd.args(["add", key, "/v", name, "/t", "REG_SZ", "/d", &exe_path, "/f"]);
        #[cfg(target_os = "windows")] { cmd.creation_flags(CREATE_NO_WINDOW); }
        let _ = cmd.output();
    } else {
        let mut cmd = Command::new("reg");
        cmd.args(["delete", key, "/v", name, "/f"]);
        #[cfg(target_os = "windows")] { cmd.creation_flags(CREATE_NO_WINDOW); }
        let _ = cmd.output();
    }
    }
}

fn fire_wall_warning(app_handle: &tauri::AppHandle, ip: &str, port: u16) {
    if ip == "127.0.0.1" || ip == "localhost" || ip.is_empty() {
        return;
    }
    add_log(app_handle, "app", "warning", &format!(
        "Relay is on {ip}:{port}. Windows Firewall may block incoming connections from your phone."
    ));
    let _ = app_handle.emit("firewall-warning", serde_json::json!({
        "ip": ip,
        "port": port,
        "message": "Windows Firewall could be blocking the port. Make sure your phone can reach this PC.",
    }));
}

fn show_notification(title: &str, body: &str) {
    #[cfg(target_os = "windows")]
    {
        let ps = format!(
            r#"Add-Type -AssemblyName System.Windows.Forms; $n = New-Object System.Windows.Forms.NotifyIcon; $n.Icon = [System.Drawing.SystemIcons]::Information; $n.BalloonTipIcon = 'Info'; $n.BalloonTipTitle = '{}'; $n.BalloonTipText = '{}'; $n.Visible = $true; $n.ShowBalloonTip(3000); Start-Sleep -Seconds 3; $n.Dispose()"#,
            title, body
        );
        let _ = Command::new("powershell")
            .args(["-WindowStyle", "Hidden", "-Command", &ps])
            .spawn();
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = Command::new("osascript")
            .args(["-e", &format!(r#"display notification "{}" with title "{}""#, body, title)])
            .spawn();
    }
}

fn hide_console(cmd: &mut Command) {
    #[cfg(target_os = "windows")]
    {
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
}

fn ensure_deps(dir: &std::path::Path, app_handle: &tauri::AppHandle) -> bool {
    let node_modules = dir.join("node_modules");
    let package_json = dir.join("package.json");

    if !package_json.exists() {
        return true;
    }

    if node_modules.exists() {
        return true;
    }

    add_log(app_handle, "app", "info", &format!("Installing dependencies in {}...", dir.display()));

    let output = if cfg!(windows) {
        let cmd = format!(
            "cd /d {} && npm install --no-audit --no-fund --silent",
            dir.display()
        );
        let mut c = Command::new("cmd");
        c.args(["/C", &cmd])
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        hide_console(&mut c);
        c.output()
    } else {
        Command::new("npm")
            .args(["install", "--no-audit", "--no-fund", "--silent"])
            .current_dir(dir)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .output()
    };

    match output {
        Ok(out) if out.status.success() => {
            add_log(app_handle, "app", "info", &format!("Dependencies installed in {}", dir.display()));
            true
        }
        Ok(out) => {
            let stderr = String::from_utf8_lossy(&out.stderr).to_string();
            let stdout = String::from_utf8_lossy(&out.stdout).to_string();
            add_log(app_handle, "app", "error", &format!("npm install failed: {}{}", stderr, stdout));
            false
        }
        Err(e) => {
            add_log(app_handle, "app", "error", &format!("Failed to run npm install: {}", e));
            false
        }
    }
}

// Removed find_npm — now unused

fn is_port_available(port: u16) -> bool {
    TcpListener::bind(("127.0.0.1", port)).is_ok()
}

fn find_available_port(start: u16) -> u16 {
    (start..start + 100).find(|&p| is_port_available(p)).unwrap_or(9000)
}

fn wait_for_port(port: u16, timeout_secs: u32) -> bool {
    let addr = std::net::SocketAddr::from(([127, 0, 0, 1], port));
    for _ in 0..(timeout_secs * 10) {
        if TcpStream::connect_timeout(&addr, Duration::from_millis(100)).is_ok() {
            return true;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    false
}

fn load_config() -> AppConfig {
    let path = config_path();
    if path.exists() {
        if let Ok(content) = fs::read_to_string(&path) {
            if let Ok(cfg) = serde_json::from_str::<AppConfig>(&content) {
                return cfg;
            }
        }
    }
    AppConfig::default()
}

fn save_config(config: &AppConfig) {
    let path = config_path();
    if let Ok(json) = serde_json::to_string_pretty(config) {
        let _ = fs::write(path, json);
    }
}

// ─── Network detection ──────────────────────────────────────

fn is_private_ipv4(addr: &str) -> bool {
    let parts: Vec<&str> = addr.split('.').collect();
    if parts.len() != 4 {
        return false;
    }
    if let (Ok(a), Ok(b)) = (parts[0].parse::<u8>(), parts[1].parse::<u8>()) {
        match a {
            10 => true,
            172 => (16..=31).contains(&b),
            192 => b == 168,
            _ => false,
        }
    } else {
        false
    }
}

fn is_virtual_interface(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.contains("hyper-v")
        || lower.contains("virtual")
        || lower.contains("docker")
        || lower.contains("vswitch")
        || lower.contains("vmware")
        || lower.contains("virtualbox")
        || lower.contains("wsl")
        || lower.contains("bluetooth")
        || lower.contains("loopback")
        || lower.contains("pseudo")
}

fn is_vpn_interface(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.contains("vpn")
        || lower.contains("tailscale")
        || lower.contains("zerotier")
        || lower.contains("wireguard")
        || lower.contains("openvpn")
        || lower.contains("nord")
        || lower.contains("proton")
}

fn detect_network_interfaces() -> Vec<NetworkInterface> {
    let mut interfaces = Vec::new();

    // Use netsh on Windows, ip on Linux/Mac
    #[cfg(target_os = "windows")]
    {
        let mut cmd = Command::new("powershell");
        cmd.args([
            "-NoProfile", "-Command",
            "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.AddressState -eq 'Preferred' } | Select-Object IPAddress, InterfaceAlias | ConvertTo-Json -Compress"
        ]);
        hide_console(&mut cmd);
        if let Ok(output) = cmd.output()
        {
            if let Ok(stdout) = String::from_utf8(output.stdout) {
                // Try to parse the JSON array
                if let Ok(values) = serde_json::from_str::<Vec<serde_json::Value>>(&stdout) {
                    for v in values {
                        let name = v["InterfaceAlias"].as_str().unwrap_or("unknown").to_string();
                        let address = v["IPAddress"].as_str().unwrap_or("").to_string();
                        if !address.is_empty() {
                            let is_virt = is_virtual_interface(&name);
                            let is_vpn = is_vpn_interface(&name);
                            let is_priv = is_private_ipv4(&address);
                            let kind = if is_vpn {
                                "vpn"
                            } else if is_virt {
                                "virtual"
                            } else if name.to_lowercase().contains("wi-fi") || name.to_lowercase().contains("wifi") {
                                "wifi"
                            } else if name.to_lowercase().contains("ethernet") {
                                "ethernet"
                            } else {
                                "other"
                            };
                            interfaces.push(NetworkInterface {
                                name,
                                address,
                                kind: kind.to_string(),
                                is_private: is_priv && !is_virt,
                            });
                        }
                    }
                } else {
                    // Try single object
                    if let Ok(v) = serde_json::from_str::<serde_json::Value>(&stdout) {
                        if let Some(obj) = v.as_object() {
                            let name = obj.get("InterfaceAlias").and_then(|s| s.as_str()).unwrap_or("unknown").to_string();
                            let address = obj.get("IPAddress").and_then(|s| s.as_str()).unwrap_or("").to_string();
                            let is_priv = is_private_ipv4(&address);
                            if !address.is_empty() {
                                interfaces.push(NetworkInterface {
                                    name,
                                    address,
                                    kind: "other".to_string(),
                                    is_private: is_priv,
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    #[cfg(not(target_os = "windows"))]
    {
        let output = Command::new("sh")
            .args(["-c", "ifconfig | grep 'inet ' | grep -v 127.0.0.1"])
            .output();
        if let Ok(out) = output {
            if let Ok(stdout) = String::from_utf8(out.stdout) {
                for line in stdout.lines() {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 2 {
                        let addr = parts[1];
                        if is_private_ipv4(addr) {
                            interfaces.push(NetworkInterface {
                                name: "en0".to_string(),
                                address: addr.to_string(),
                                kind: "other".to_string(),
                                is_private: true,
                            });
                        }
                    }
                }
            }
        }
    }

    // Sort: private interfaces first, non-virtual first, wifi first
    interfaces.sort_by(|a, b| {
        b.is_private.cmp(&a.is_private)
            .then(a.kind.cmp(&b.kind))
            .then(a.address.cmp(&b.address))
    });

    interfaces
}

// ─── Process piping ─────────────────────────────────────────

fn pipe_stdout_to_logs(
    stdout: std::process::ChildStdout,
    app_handle: tauri::AppHandle,
    source: String,
) {
    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        for line in reader.lines() {
            if let Ok(text) = line {
                if !text.trim().is_empty() {
                    add_log(&app_handle, &source, "info", &text);
                }
            }
        }
    });
}

fn pipe_stderr_to_logs(
    stderr: std::process::ChildStderr,
    app_handle: tauri::AppHandle,
    source: String,
) {
    std::thread::spawn(move || {
        let reader = BufReader::new(stderr);
        for line in reader.lines() {
            if let Ok(text) = line {
                if !text.trim().is_empty() {
                    add_log(&app_handle, &source, "error", &text);
                }
            }
        }
    });
}

// ─── Tauri commands ─────────────────────────────────────────

#[tauri::command]
fn detect_networks() -> Result<Vec<NetworkInterface>, String> {
    Ok(detect_network_interfaces())
}

#[tauri::command]
fn select_network(app_handle: tauri::AppHandle, ip: String) -> Result<(), String> {
    let state = app_handle.state::<AppState>();
    {
        let mut selected = state.selected_ip.lock().map_err(|e| e.to_string())?;
        *selected = ip.clone();
    }
    {
        let mut config = state.config.lock().map_err(|e| e.to_string())?;
        config.selected_ip = ip.clone();
        save_config(&config);
    }
    add_log(&app_handle, "app", "info", &format!("Selected network: {ip}"));
    Ok(())
}

#[tauri::command]
fn get_config(app_handle: tauri::AppHandle) -> Result<AppConfig, String> {
    let state = app_handle.state::<AppState>();
    let config = state.config.lock().map_err(|e| e.to_string())?;
    Ok(config.clone())
}

#[tauri::command]
fn save_config_cmd(app_handle: tauri::AppHandle, config: AppConfig) -> Result<(), String> {
    let state = app_handle.state::<AppState>();
    let mut current = state.config.lock().map_err(|e| e.to_string())?;
    let old_startup = current.launch_at_startup;
    *current = config.clone();
    save_config(&config);
    // Update registry if launch_at_startup changed
    if config.launch_at_startup != old_startup {
        set_launch_at_startup(config.launch_at_startup);
    }
    Ok(())
}

#[tauri::command]
fn start_relay(app_handle: tauri::AppHandle) -> Result<String, String> {
    let state = app_handle.state::<AppState>();

    // Check if already running
    {
        let guard = state.relay_process.lock().map_err(|e| e.to_string())?;
        if guard.is_some() {
            return Err("Relay already running".to_string());
        }
    }

    let config = state.config.lock().map_err(|e| e.to_string())?.clone();
    let port = config.relay_port;

    if !is_port_available(port) {
        return Err(format!("Port {port} is already in use"));
    }

    let repo_root = get_repo_root();
    let relay_dir = repo_root.join("relay");
    let server_js = relay_dir.join("server.js");

    if !server_js.exists() {
        return Err(format!(
            "relay/server.js not found at {}\nRepo root: {}\nCWD: {}",
            server_js.display(),
            repo_root.display(),
            std::env::current_dir().map(|p| p.display().to_string()).unwrap_or_else(|_| "?".to_string())
        ));
    }

    // Ensure npm dependencies are installed
    if !ensure_deps(&relay_dir, &app_handle) {
        return Err("Failed to install relay dependencies. Check logs for details.".to_string());
    }

    add_log(&app_handle, "app", "info", &format!("Starting relay on port {port}..."));

    let mut relay_cmd = Command::new("node");
    relay_cmd
        .arg(&server_js)
        .current_dir(&relay_dir)
        .env("PORT", port.to_string())
        .env("RELAY_PORT", port.to_string())
        .env("RELAY_BIND_HOST", "0.0.0.0")
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    hide_console(&mut relay_cmd);
    let mut child = relay_cmd
        .spawn()
        .map_err(|e| format!("Failed to start relay: {e}"))?;

    let stdout = child.stdout.take().unwrap();
    let stderr = child.stderr.take().unwrap();
    let pid = child.id();
    let ah_s = app_handle.clone();
    let ah_e = app_handle.clone();

    pipe_stdout_to_logs(stdout, ah_s, "relay".to_string());
    pipe_stderr_to_logs(stderr, ah_e, "relay".to_string());

    {
        let mut guard = state.relay_process.lock().map_err(|e| e.to_string())?;
        *guard = Some(child);
    }

    // Wait for relay to be ready
    let ah_wait = app_handle.clone();
    let wait_port = port;
    std::thread::spawn(move || {
        let ready = wait_for_port(wait_port, 15);
        let state = ah_wait.state::<AppState>();
        if ready {
            add_log(&ah_wait, "app", "info", &format!("Relay ready on port {wait_port}"));
        } else {
            add_log(&ah_wait, "app", "error", "Relay did not become ready in time");
        }
        // Emit status update
        let _ = ah_wait.emit("status-changed", AppStatus {
            state: if ready { "relay_running".to_string() } else { "error".to_string() },
            relay_mode: "local".to_string(),
            relay: if ready { "running".to_string() } else { "error".to_string() },
            bridge: "stopped".to_string(),
            network: state.selected_ip.lock().ok().map(|s| s.clone()).unwrap_or_default(),
            relay_url: state.relay_url.lock().ok().map(|s| s.clone()).unwrap_or_default(),
            pairing_payload: None,
            pairing_code: None,
            phone_connected: false,
        });
    });

    // Build relay URL
    let selected_ip = state.selected_ip.lock().map_err(|e| e.to_string())?.clone();
    let relay_url = if selected_ip.is_empty() {
        format!("ws://127.0.0.1:{port}/relay")
    } else {
        format!("ws://{selected_ip}:{port}/relay")
    };
    {
        let mut url_guard = state.relay_url.lock().map_err(|e| e.to_string())?;
        *url_guard = relay_url.clone();
    }

    add_log(&app_handle, "app", "info", &format!("Relay started (PID: {pid})"));
    add_log(&app_handle, "app", "info", &format!("Relay URL: {relay_url}"));

    // Firewall warning for LAN IPs
    fire_wall_warning(&app_handle, &selected_ip, port);

    // Crash watcher
    let ah_watch = app_handle.clone();
    std::thread::spawn(move || {
        loop {
            std::thread::sleep(Duration::from_millis(500));
            let state = ah_watch.state::<AppState>();
            let exited = {
                if let Ok(mut guard) = state.relay_process.lock() {
                    if let Some(ref mut child) = *guard {
                        match child.try_wait() {
                            Ok(Some(status)) => Some(status),
                            _ => None,
                        }
                    } else {
                        return; // already stopped
                    }
                } else {
                    return;
                }
            };

            if let Some(status) = exited {
                let intentional = state.relay_intentional.lock().ok().map(|i| *i).unwrap_or(true);
                if let Ok(mut guard) = state.relay_process.lock() {
                    *guard = None;
                }
                if intentional {
                    // reset flag
                    if let Ok(mut i) = state.relay_intentional.lock() { *i = false; }
                    return;
                }
                let code = status.code().unwrap_or(-1);
                add_log(&ah_watch, "app", "error", &format!("Relay crashed (exit code: {code})"));
                let _ = ah_watch.emit("process-crashed", serde_json::json!({
                    "process": "relay",
                    "exit_code": code,
                }));

                // Auto-restart
                let auto_restart = state.config.lock().ok().map(|c| c.auto_restart).unwrap_or(false);
                if auto_restart {
                    add_log(&ah_watch, "app", "info", "Auto-restarting relay...");
                    let _ = start_relay(ah_watch.clone());
                }
                return;
            }
        }
    });

    Ok(relay_url)
}

#[tauri::command]
fn stop_relay(app_handle: tauri::AppHandle) -> Result<String, String> {
    let state = app_handle.state::<AppState>();
    // Mark as intentional stop so crash watcher doesn't report
    if let Ok(mut i) = state.relay_intentional.lock() { *i = true; }
    let mut guard = state.relay_process.lock().map_err(|e| e.to_string())?;
    if let Some(ref mut child) = *guard {
        add_log(&app_handle, "app", "info", "Stopping relay...");
        child.kill().map_err(|e| format!("Failed to kill relay: {e}"))?;
        child.wait().map_err(|e| format!("Failed to wait relay: {e}"))?;
        *guard = None;
        add_log(&app_handle, "app", "info", "Relay stopped");
        Ok("Relay stopped".to_string())
    } else {
        Err("Relay not running".to_string())
    }
}

#[tauri::command]
fn start_bridge(app_handle: tauri::AppHandle) -> Result<String, String> {
    let state = app_handle.state::<AppState>();

    {
        let guard = state.bridge_process.lock().map_err(|e| e.to_string())?;
        if guard.is_some() {
            return Err("Bridge already running".to_string());
        }
    }

    let relay_url = state.relay_url.lock().map_err(|e| e.to_string())?.clone();
    if relay_url.is_empty() {
        return Err("Relay URL not set. Start relay first.".to_string());
    }

    let repo_root = get_repo_root();
    let bridge_dir = repo_root.join("phodex-bridge");
    let bridge_bin = bridge_dir.join("bin").join("remodex.js");

    if !bridge_bin.exists() {
        return Err(format!(
            "phodex-bridge/bin/remodex.js not found at {}\nRepo root: {}\nCWD: {}",
            bridge_bin.display(),
            repo_root.display(),
            std::env::current_dir().map(|p| p.display().to_string()).unwrap_or_else(|_| "?".to_string())
        ));
    }

    // Ensure npm dependencies are installed
    if !ensure_deps(&bridge_dir, &app_handle) {
        return Err("Failed to install bridge dependencies. Check logs for details.".to_string());
    }

    add_log(&app_handle, "app", "info", &format!("Starting bridge with relay: {relay_url}"));
    {
        if let Ok(mut pp) = state.pairing_payload.lock() {
            *pp = None;
        }
        if let Ok(mut pc) = state.pairing_code.lock() {
            *pc = None;
        }
    }

    let mut bridge_cmd = Command::new("node");
    bridge_cmd
        .arg(&bridge_bin)
        .arg("run")
        .current_dir(&bridge_dir)
        .env("REMODEX_RELAY", &relay_url)
        .env("REMODEX_PRINT_PAIRING_JSON", "1")
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    hide_console(&mut bridge_cmd);
    let mut child = bridge_cmd
        .spawn()
        .map_err(|e| format!("Failed to start bridge: {e}"))?;

    let pid = child.id();
    let stdout = child.stdout.take().unwrap();
    let stderr = child.stderr.take().unwrap();

    // Pipe stdout to logs AND detect pairing JSON
    let ah_pairing = app_handle.clone();
    let ah_stdout = app_handle.clone();
    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        let mut capturing_pairing_json = false;
        let mut capturing_pairing_code = false;

        for line in reader.lines() {
            if let Ok(text) = line {
                let trimmed = text.trim();
                if !trimmed.is_empty() {
                    if trimmed.contains("Or paste this pairing code") {
                        capturing_pairing_code = true;
                        add_log(&ah_stdout, "bridge", "info", "Manual pairing code ready");
                        continue;
                    }

                    if trimmed.contains("Pairing JSON") {
                        capturing_pairing_json = true;
                        add_log(&ah_stdout, "bridge", "info", "Pairing QR payload ready");
                        continue;
                    }

                    if capturing_pairing_code {
                        let state = ah_pairing.state::<AppState>();
                        if let Ok(mut pc) = state.pairing_code.lock() {
                            *pc = Some(trimmed.to_string());
                        }
                        let _ = ah_pairing.emit("pairing-code-ready", trimmed);
                        add_log(&ah_pairing, "app", "info", "Pairing code captured");
                        capturing_pairing_code = false;
                        continue;
                    }

                    if capturing_pairing_json {
                        if let Ok(payload) = serde_json::from_str::<serde_json::Value>(trimmed) {
                            if payload.get("relay").is_some() && payload.get("sessionId").is_some() {
                                let state = ah_pairing.state::<AppState>();
                                if let Ok(mut pp) = state.pairing_payload.lock() {
                                    *pp = Some(trimmed.to_string());
                                }
                                let _ = ah_pairing.emit("pairing-ready", trimmed);
                                add_log(&ah_pairing, "app", "info", "Pairing payload captured");
                                capturing_pairing_json = false;
                                continue;
                            }
                        }
                        add_log(&ah_pairing, "app", "warning", "Failed to parse pairing payload");
                        capturing_pairing_json = false;
                        continue;
                    }

                    add_log(&ah_stdout, "bridge", "info", trimmed);
                }
            }
        }
    });

    pipe_stderr_to_logs(stderr, app_handle.clone(), "bridge".to_string());

    {
        let mut guard = state.bridge_process.lock().map_err(|e| e.to_string())?;
        *guard = Some(child);
    }

    // Emit status update
    let ah_status = app_handle.clone();
    let relay_mode = app_handle.state::<AppState>().config.lock().ok().map(|c| c.relay_mode.clone()).unwrap_or_else(|| "local".to_string());
    let _ = ah_status.emit("status-changed", AppStatus {
        state: if relay_mode == "remote" { "remote_placeholder_ready".to_string() } else { "waiting_for_pairing".to_string() },
        relay_mode,
        relay: "running".to_string(),
        bridge: "starting".to_string(),
        network: app_handle.state::<AppState>().selected_ip.lock().ok().map(|s| s.clone()).unwrap_or_default(),
        relay_url: relay_url.clone(),
        pairing_payload: None,
        pairing_code: None,
        phone_connected: false,
    });

    add_log(&app_handle, "app", "info", &format!("Bridge started (PID: {pid})"));

    // Crash watcher
    let ah_watch = app_handle.clone();
    std::thread::spawn(move || {
        loop {
            std::thread::sleep(Duration::from_millis(500));
            let state = ah_watch.state::<AppState>();
            let exited = {
                if let Ok(mut guard) = state.bridge_process.lock() {
                    if let Some(ref mut child) = *guard {
                        match child.try_wait() {
                            Ok(Some(status)) => Some(status),
                            _ => None,
                        }
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            };

            if let Some(status) = exited {
                let intentional = state.bridge_intentional.lock().ok().map(|i| *i).unwrap_or(true);
                if let Ok(mut guard) = state.bridge_process.lock() {
                    *guard = None;
                }
                if intentional {
                    if let Ok(mut i) = state.bridge_intentional.lock() { *i = false; }
                    return;
                }
                let code = status.code().unwrap_or(-1);
                add_log(&ah_watch, "app", "error", &format!("Bridge crashed (exit code: {code})"));
                let _ = ah_watch.emit("process-crashed", serde_json::json!({
                    "process": "bridge",
                    "exit_code": code,
                }));

                let auto_restart = state.config.lock().ok().map(|c| c.auto_restart).unwrap_or(false);
                if auto_restart {
                    add_log(&ah_watch, "app", "info", "Auto-restarting bridge...");
                    let _ = start_bridge(ah_watch.clone());
                }
                return;
            }
        }
    });

    Ok(format!("Bridge started, PID: {pid}"))
}

#[tauri::command]
fn stop_bridge(app_handle: tauri::AppHandle) -> Result<String, String> {
    let state = app_handle.state::<AppState>();
    if let Ok(mut i) = state.bridge_intentional.lock() { *i = true; }
    let mut guard = state.bridge_process.lock().map_err(|e| e.to_string())?;
    if let Some(ref mut child) = *guard {
        add_log(&app_handle, "app", "info", "Stopping bridge...");
        child.kill().map_err(|e| format!("Failed to kill bridge: {e}"))?;
        child.wait().map_err(|e| format!("Failed to wait bridge: {e}"))?;
        *guard = None;
        add_log(&app_handle, "app", "info", "Bridge stopped");
        Ok("Bridge stopped".to_string())
    } else {
        Err("Bridge not running".to_string())
    }
}

#[tauri::command]
fn stop_all(app_handle: tauri::AppHandle) -> Result<String, String> {
    add_log(&app_handle, "app", "info", "Stopping all processes...");
    let r1 = stop_bridge(app_handle.clone());
    let r2 = stop_relay(app_handle.clone());
    if r1.is_ok() && r2.is_ok() {
        add_log(&app_handle, "app", "info", "All processes stopped");
    }
    Ok("All processes stopped".to_string())
}

#[tauri::command]
fn start_all(app_handle: tauri::AppHandle) -> Result<String, String> {
    let state = app_handle.state::<AppState>();
    let is_remote = state.config.lock().map_err(|e| e.to_string())?.relay_mode == "remote";
    drop(state);

    add_log(&app_handle, "app", "info", "Starting all processes...");

    if is_remote {
        add_log(&app_handle, "app", "info", "Remote mode: skipping local relay");
        let remote_url = {
            let state = app_handle.state::<AppState>();
            let config = state.config.lock().map_err(|e| e.to_string())?;
            config.remote_relay_url.clone()
        };
        {
            let state = app_handle.state::<AppState>();
            let mut url = state.relay_url.lock().map_err(|e| e.to_string())?;
            *url = remote_url.clone();
        }
        start_bridge(app_handle.clone())?;
        Ok(remote_url)
    } else {
        let relay_url = start_relay(app_handle.clone())?;
        std::thread::sleep(Duration::from_millis(500));
        let _ = start_bridge(app_handle.clone())?;
        Ok(relay_url)
    }
}

#[tauri::command]
fn get_logs(app_handle: tauri::AppHandle) -> Result<Vec<LogEntry>, String> {
    let state = app_handle.state::<AppState>();
    let logs = state.logs.lock().map_err(|e| e.to_string())?;
    Ok(logs.clone())
}

#[tauri::command]
fn clear_logs(app_handle: tauri::AppHandle) -> Result<(), String> {
    let state = app_handle.state::<AppState>();
    let mut logs = state.logs.lock().map_err(|e| e.to_string())?;
    logs.clear();
    Ok(())
}

#[tauri::command]
fn get_status(app_handle: tauri::AppHandle) -> Result<AppStatus, String> {
    let state = app_handle.state::<AppState>();

    let _relay = {
        state.relay_process.lock().map_err(|e| e.to_string())?
            .as_ref().map_or("stopped", |_| "running").to_string()
    };
    let bridge = {
        state.bridge_process.lock().map_err(|e| e.to_string())?
            .as_ref().map_or("stopped", |_| "running").to_string()
    };
    let network = state.selected_ip.lock().map_err(|e| e.to_string())?.clone();
    let relay_url = state.relay_url.lock().map_err(|e| e.to_string())?.clone();
    let pairing_payload = state.pairing_payload.lock().map_err(|e| e.to_string())?.clone();
    let pairing_code = state.pairing_code.lock().map_err(|e| e.to_string())?.clone();
    let phone_connected = state.phone_connected.lock().map_err(|e| e.to_string())?.clone();

    let relay_mode = state.config.lock().map_err(|e| e.to_string())?.relay_mode.clone();

    let relay_status = if relay_mode == "remote" {
        if relay_url.is_empty() { "external".to_string() } else { "external".to_string() }
    } else {
        state.relay_process.lock().map_err(|e| e.to_string())?
            .as_ref().map_or("stopped", |_| "running").to_string()
    };

    let state_str = if relay_status == "running" && bridge == "running" {
        if relay_mode == "remote" { "remote_placeholder_ready" } else { "local_ready" }
    } else if relay_status == "running" {
        "relay_running"
    } else if bridge == "running" {
        "waiting_for_pairing"
    } else {
        "stopped"
    };

    Ok(AppStatus {
        state: state_str.to_string(),
        relay_mode,
        relay: relay_status,
        bridge,
        network,
        relay_url,
        pairing_payload,
        pairing_code,
        phone_connected,
    })
}

#[tauri::command]
fn set_relay_mode(app_handle: tauri::AppHandle, mode: String) -> Result<(), String> {
    if mode != "local" && mode != "remote" {
        return Err("relay_mode must be 'local' or 'remote'".to_string());
    }
    let state = app_handle.state::<AppState>();
    let mut config = state.config.lock().map_err(|e| e.to_string())?;
    config.relay_mode = mode.clone();
    save_config(&config);
    add_log(&app_handle, "app", "info", &format!("Relay mode set to: {mode}"));
    Ok(())
}

#[tauri::command]
fn set_remote_relay_url(app_handle: tauri::AppHandle, url: String) -> Result<(), String> {
    if !url.starts_with("ws://") && !url.starts_with("wss://") {
        return Err("Remote URL must start with ws:// or wss://".to_string());
    }
    let state = app_handle.state::<AppState>();
    let mut config = state.config.lock().map_err(|e| e.to_string())?;
    config.remote_relay_url = url;
    save_config(&config);
    add_log(&app_handle, "app", "info", "Remote relay URL updated");
    Ok(())
}

#[tauri::command]
fn get_pairing_payload(app_handle: tauri::AppHandle) -> Result<Option<String>, String> {
    let state = app_handle.state::<AppState>();
    let payload = state.pairing_payload.lock().map_err(|e| e.to_string())?;
    Ok(payload.clone())
}

#[tauri::command]
fn check_port(port: u16) -> Result<serde_json::Value, String> {
    let available = is_port_available(port);
    let suggested = if !available {
        Some(find_available_port(port))
    } else {
        None
    };
    Ok(serde_json::json!({
        "available": available,
        "suggested": suggested,
    }))
}

#[tauri::command]
fn complete_setup(app_handle: tauri::AppHandle) -> Result<(), String> {
    let state = app_handle.state::<AppState>();
    let mut config = state.config.lock().map_err(|e| e.to_string())?;
    config.setup_completed = true;
    save_config(&config);
    Ok(())
}

#[tauri::command]
fn show_main_window(app_handle: tauri::AppHandle) -> Result<(), String> {
    if let Some(window) = app_handle.get_webview_window("main") {
        window.show().map_err(|e| e.to_string())?;
        window.set_focus().map_err(|e| e.to_string())?;
        let _ = app_handle.emit("show-qr", ());
        return Ok(());
    }
    Err("main window not found".to_string())
}

#[tauri::command]
fn show_pet_popup(app_handle: tauri::AppHandle) -> Result<(), String> {
    if let Some(w) = app_handle.get_webview_window("pet-popup") {
        w.show().map_err(|e| format!("popup show failed: {e}"))?;
        w.center().map_err(|e| format!("popup center failed: {e}"))?;
        w.set_focus().map_err(|e| format!("popup focus failed: {e}"))?;
        return Ok(());
    }

    let popup = tauri::WebviewWindowBuilder::new(
        &app_handle,
        "pet-popup",
        tauri::WebviewUrl::App("popup.html".into()),
    )
    .title("")
    .inner_size(220.0, 240.0)
    .decorations(false)
    .always_on_top(true)
    .resizable(false)
    .shadow(false)
    .transparent(false)
    .skip_taskbar(true)
    .visible(true)
    .center()
    .build()
    .map_err(|e| format!("popup build failed: {e}"))?;

    popup.set_focus().map_err(|e| format!("popup focus failed: {e}"))?;

    Ok(())
}

#[tauri::command]
fn hide_pet_popup(app_handle: tauri::AppHandle) -> Result<(), String> {
    if let Some(w) = app_handle.get_webview_window("pet-popup") {
        w.hide().map_err(|e| format!("popup hide failed: {e}"))?;
    }
    Ok(())
}

#[tauri::command]
fn move_pet_window(app_handle: tauri::AppHandle, x: f64, y: f64) -> Result<(), String> {
    if let Some(window) = app_handle.get_webview_window("pet") {
        window
            .set_position(tauri::Position::Physical(tauri::PhysicalPosition::new(x as i32, y as i32)))
            .map_err(|e| e.to_string())?;
        return Ok(());
    }
    Err("pet window not found".to_string())
}

#[tauri::command]
fn get_pet_window_position(app_handle: tauri::AppHandle) -> Result<Option<(f64, f64)>, String> {
    if let Some(window) = app_handle.get_webview_window("pet") {
        let pos = window.outer_position().map_err(|e| e.to_string())?;
        return Ok(Some((pos.x as f64, pos.y as f64)));
    }
    Ok(None)
}

#[tauri::command]
fn notify(title: String, body: String) {
    show_notification(&title, &body);
}

#[tauri::command]
fn debug_paths() -> Result<DebugInfo, String> {
    let cwd = std::env::current_dir()
        .map(|p| p.display().to_string())
        .unwrap_or_else(|_| "unknown".to_string());
    let repo_root = get_repo_root();
    let relay_dir = repo_root.join("relay");
    let bridge_dir = repo_root.join("phodex-bridge");

    let node_version = Command::new("node")
        .args(["-e", "console.log(process.version)"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_else(|_| "unknown".to_string());

    Ok(DebugInfo {
        cwd,
        repo_root: repo_root.display().to_string(),
        relay_dir: relay_dir.display().to_string(),
        relay_server_exists: relay_dir.join("server.js").exists(),
        bridge_dir: bridge_dir.display().to_string(),
        bridge_bin_exists: bridge_dir.join("bin").join("remodex.js").exists(),
        config_path: config_path().display().to_string(),
        config_exists: config_path().exists(),
        node_version,
    })
}

#[tauri::command]
async fn check_for_update(app_handle: tauri::AppHandle) -> Result<Option<UpdateInfo>, String> {
    let update = app_handle
        .updater()
        .map_err(|e| format!("updater unavailable: {e}"))?
        .check()
        .await
        .map_err(|e| format!("update check failed: {e}"))?;

    Ok(update.map(|update| UpdateInfo {
        version: update.version,
        current_version: update.current_version,
        date: update.date.map(|d| d.to_string()),
        body: update.body,
    }))
}

#[tauri::command]
async fn install_update(app_handle: tauri::AppHandle) -> Result<(), String> {
    let update = app_handle
        .updater()
        .map_err(|e| format!("updater unavailable: {e}"))?
        .check()
        .await
        .map_err(|e| format!("update check failed: {e}"))?;

    let Some(update) = update else {
        add_log(&app_handle, "app", "info", "No update available");
        return Ok(());
    };

    add_log(
        &app_handle,
        "app",
        "info",
        &format!("Installing Remodex Host update {}", update.version),
    );

    update
        .download_and_install(|_chunk_length, _content_length| {}, || {})
        .await
        .map_err(|e| format!("update install failed: {e}"))?;

    add_log(&app_handle, "app", "info", "Update installed; restarting");
    app_handle.restart();
}

// ─── Main ───────────────────────────────────────────────────

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app_config = load_config();

    let state = AppState {
        relay_process: Mutex::new(None),
        bridge_process: Mutex::new(None),
        logs: Mutex::new(Vec::new()),
        config: Mutex::new(app_config.clone()),
        pairing_payload: Mutex::new(None),
        selected_ip: Mutex::new(app_config.selected_ip.clone()),
        relay_url: Mutex::new(String::new()),
        phone_connected: Mutex::new(false),
        pairing_code: Mutex::new(None),
        relay_intentional: Mutex::new(false),
        bridge_intentional: Mutex::new(false),
    };

    tauri::Builder::default()
        .plugin(tauri_plugin_updater::Builder::new().build())
        .manage(state)
        .setup(move |app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            // Close to tray: intercept window close, hide instead of quitting
            if let Some(window) = app.get_webview_window("main") {
                let w = window.clone();
                window.on_window_event(move |event| {
                    if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                        api.prevent_close();
                        let _ = w.hide();
                    }
                });
            }

            // Start minimized if configured
            if app_config.start_minimized {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.hide();
                }
            }

            // Pet overlay window
            let screen_width = app.primary_monitor()
                .ok().flatten()
                .map(|m| m.size().width as f64)
                .unwrap_or(1920.0);
            let _pet = tauri::WebviewWindowBuilder::new(
                app,
                "pet",
                tauri::WebviewUrl::App("pet.html".into()),
            )
            .title("Remodex Pet")
            .inner_size(80.0, 80.0)
            .decorations(false)
            .always_on_top(true)
            .resizable(false)
            .transparent(true)
            .shadow(false)
            .skip_taskbar(true)
            .visible(app_config.relay_mode == "local")
            .position(screen_width - 120.0, 200.0)
            .build()?;

            let _pet_popup = tauri::WebviewWindowBuilder::new(
                app,
                "pet-popup",
                tauri::WebviewUrl::App("popup.html".into()),
            )
            .title("Remodex")
            .inner_size(220.0, 240.0)
            .decorations(false)
            .always_on_top(true)
            .resizable(false)
            .shadow(false)
            .transparent(false)
            .skip_taskbar(true)
            .visible(false)
            .center()
            .build()?;

            // Emit first-run flag to frontend
            if !app_config.setup_completed {
                let h = app.handle().clone();
                std::thread::spawn(move || {
                    std::thread::sleep(std::time::Duration::from_millis(500));
                    let _ = h.emit("first-run", ());
                });
            }

            let show = MenuItemBuilder::with_id("show", "Show Remodex Host").build(app)?;
            let show_qr = MenuItemBuilder::with_id("show_qr", "Show QR").build(app)?;
            let start = MenuItemBuilder::with_id("start", "Start All").build(app)?;
            let stop = MenuItemBuilder::with_id("stop", "Stop All").build(app)?;
            let restart_bridge = MenuItemBuilder::with_id("restart_bridge", "Restart Bridge").build(app)?;
            let restart_relay = MenuItemBuilder::with_id("restart_relay", "Restart Relay").build(app)?;
            let sep1 = tauri::menu::PredefinedMenuItem::separator(app)?;
            let logs = MenuItemBuilder::with_id("open_logs", "Open Logs").build(app)?;
            let sep2 = tauri::menu::PredefinedMenuItem::separator(app)?;
            let quit = MenuItemBuilder::with_id("quit", "Quit").build(app)?;

            let menu = MenuBuilder::new(app)
                .item(&show)
                .item(&show_qr)
                .separator()
                .item(&start)
                .item(&stop)
                .item(&restart_bridge)
                .item(&restart_relay)
                .item(&sep1)
                .item(&logs)
                .item(&sep2)
                .item(&quit)
                .build()?;

            let _tray = TrayIconBuilder::new()
                .menu(&menu)
                .icon(app.default_window_icon().cloned().unwrap())
                .tooltip("Remodex Host")
                .on_menu_event(|app, event| {
                    match event.id().as_ref() {
                        "show" => {
                            if let Some(window) = app.get_webview_window("main") {
                                let _ = window.show();
                                let _ = window.set_focus();
                            }
                        }
                        "show_qr" => {
                            if let Some(window) = app.get_webview_window("main") {
                                let _ = window.show();
                                let _ = window.set_focus();
                                let _ = app.emit("show-qr", ());
                            }
                        }
                        "start" => {
                            let handle = app.app_handle().clone();
                            let _ = start_all(handle);
                        }
                        "stop" => {
                            let handle = app.app_handle().clone();
                            let _ = stop_all(handle);
                        }
                        "restart_bridge" => {
                            let handle = app.app_handle().clone();
                            let _ = stop_bridge(handle.clone());
                            std::thread::sleep(Duration::from_millis(500));
                            let _ = start_bridge(handle);
                        }
                        "restart_relay" => {
                            let handle = app.app_handle().clone();
                            let _ = stop_relay(handle.clone());
                            std::thread::sleep(Duration::from_millis(500));
                            let _ = start_relay(handle);
                        }
                        "open_logs" => {
                            if let Some(window) = app.get_webview_window("main") {
                                let _ = window.show();
                                let _ = window.set_focus();
                            }
                        }
                        "quit" => {
                            // Kill child processes before exit
                            let handle = app.app_handle().clone();
                            let _ = stop_all(handle);
                            std::thread::sleep(Duration::from_millis(500));
                            app.exit(0);
                        }
                        _ => {}
                    }
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event {
                        let app = tray.app_handle();
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                })
                .build(app)?;

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            detect_networks,
            select_network,
            get_config,
            save_config_cmd,
            start_relay,
            stop_relay,
            start_bridge,
            stop_bridge,
            start_all,
            stop_all,
            get_logs,
            clear_logs,
            get_status,
            get_pairing_payload,
            debug_paths,
            check_port,
            complete_setup,
            show_main_window,
            move_pet_window,
            get_pet_window_position,
            show_pet_popup,
            hide_pet_popup,
            notify,
            set_relay_mode,
            set_remote_relay_url,
            check_for_update,
            install_update,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
