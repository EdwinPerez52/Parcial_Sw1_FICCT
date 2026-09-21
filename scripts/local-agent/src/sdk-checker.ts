/**
 * SDK Checker — verifies Flutter SDK, Android SDK, and ADB availability.
 */
import { execSync } from 'child_process';

export interface SdkStatus {
  flutterAvailable: boolean;
  flutterVersion: string | null;
  adbAvailable: boolean;
  adbVersion: string | null;
  androidHome: string | null;
  devices: DeviceInfo[];
}

export interface DeviceInfo {
  id: string;
  name: string;
  status: string;
}

function tryExec(command: string): string | null {
  try {
    return execSync(command, { encoding: 'utf8', timeout: 15000, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
  } catch {
    return null;
  }
}

/** Checks Flutter SDK availability and version. */
export function checkFlutter(): { available: boolean; version: string | null } {
  const output = tryExec('flutter --version --machine');
  if (!output) {
    // Try plain version
    const plain = tryExec('flutter --version');
    if (plain) {
      const match = plain.match(/Flutter\s+(\S+)/);
      return { available: true, version: match ? match[1] : 'unknown' };
    }
    return { available: false, version: null };
  }
  try {
    const parsed = JSON.parse(output);
    return { available: true, version: parsed.frameworkVersion || 'unknown' };
  } catch {
    return { available: true, version: 'unknown' };
  }
}

/** Checks ADB availability and version. */
export function checkAdb(): { available: boolean; version: string | null } {
  const output = tryExec('adb version');
  if (!output) return { available: false, version: null };
  const match = output.match(/version\s+(\S+)/);
  return { available: true, version: match ? match[1] : 'unknown' };
}

/** Lists connected Android devices via ADB. */
export function listDevices(): DeviceInfo[] {
  const output = tryExec('adb devices -l');
  if (!output) return [];

  const lines = output.split('\n').slice(1); // Skip header
  const devices: DeviceInfo[] = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('*')) continue;

    const parts = trimmed.split(/\s+/);
    if (parts.length < 2) continue;

    const id = parts[0];
    const status = parts[1];
    const modelMatch = trimmed.match(/model:(\S+)/);
    const name = modelMatch ? modelMatch[1].replace(/_/g, ' ') : id;

    devices.push({ id, name, status });
  }

  return devices;
}

/** Gets the full SDK status. */
export function getSdkStatus(): SdkStatus {
  const flutter = checkFlutter();
  const adb = checkAdb();
  const androidHome = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || null;

  return {
    flutterAvailable: flutter.available,
    flutterVersion: flutter.version,
    adbAvailable: adb.available,
    adbVersion: adb.version,
    androidHome,
    devices: adb.available ? listDevices() : [],
  };
}
