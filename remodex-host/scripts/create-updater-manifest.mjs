import { access, mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const appRoot = path.resolve(fileURLToPath(new URL("..", import.meta.url)));
const tauriConfigPath = path.join(appRoot, "src-tauri", "tauri.conf.json");
const tauriConfig = JSON.parse(await readFile(tauriConfigPath, "utf8"));

const version = tauriConfig.version;
const releaseTag = process.env.REMODEX_HOST_UPDATE_TAG || "Remodex-Host-Beta";
const baseUrl =
  process.env.REMODEX_HOST_UPDATE_BASE_URL ||
  `https://github.com/Stivy-01/remodex/releases/download/${releaseTag}`;

const bundleRoot = path.join(appRoot, "src-tauri", "target", "release", "bundle");
const nsisDir = path.join(bundleRoot, "nsis");
const manifestPath = path.join(bundleRoot, "latest.json");

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function findInstaller() {
  const files = (await readdir(nsisDir)).sort();
  const currentVersionFiles = files.filter((file) => file.includes(`_${version}_`));
  const setup = currentVersionFiles.find((file) => file.endsWith("-setup.exe"));
  if (setup) return path.join(nsisDir, setup);
  const exe = currentVersionFiles.find((file) => file.endsWith(".exe"));
  if (exe) return path.join(nsisDir, exe);
  throw new Error(`No Windows NSIS installer for version ${version} found in ${nsisDir}`);
}

const installerPath = await findInstaller();
const signaturePath = `${installerPath}.sig`;

if (!(await exists(signaturePath))) {
  throw new Error(`Missing updater signature: ${signaturePath}`);
}

const installerName = path.basename(installerPath);
const remoteInstallerName =
  process.env.REMODEX_HOST_UPDATE_INSTALLER_NAME || installerName.replaceAll(" ", ".");
const signature = (await readFile(signaturePath, "utf8")).trim();

const manifest = {
  version,
  notes: `Remodex Host ${version}`,
  pub_date: new Date().toISOString(),
  platforms: {
    "windows-x86_64": {
      signature,
      url: `${baseUrl}/${encodeURIComponent(remoteInstallerName)}`,
    },
  },
};

await mkdir(bundleRoot, { recursive: true });
await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Wrote ${manifestPath}`);
