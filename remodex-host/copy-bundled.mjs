// copy-bundled.mjs — Copies relay and phodex-bridge source files into src-tauri/bundled/
import { cpSync, rmSync, existsSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, "..");

function copyDir(src, dest, excludeDirs = []) {
  if (!existsSync(src)) return;
  cpSync(src, dest, {
    recursive: true,
    filter: (filePath) => {
      // Exclude paths containing any excluded directory name
      return !excludeDirs.some((dir) => filePath.split(/[/\\]/).includes(dir));
    },
  });
}

const bundledDir = join(__dirname, "src-tauri", "bundled");
// Clear existing
rmSync(bundledDir, { recursive: true, force: true });

// Copy relay sources (exclude node_modules and .git)
console.log("Copying relay sources...");
copyDir(
  join(repoRoot, "relay"),
  join(bundledDir, "relay"),
  ["node_modules", ".git"]
);

// Copy phodex-bridge sources
console.log("Copying phodex-bridge sources...");
copyDir(
  join(repoRoot, "phodex-bridge"),
  join(bundledDir, "phodex-bridge"),
  ["node_modules", ".git"]
);

console.log("Bundled files copied.");
