#!/usr/bin/env python3
"""Tiny adb UI driver. Usage:
  droid.py texts                 list visible texts with their centers
  droid.py tap "<text>"          tap the first node whose text or content-desc equals <text> (case-insensitive)
  droid.py shot <file.png>       screenshot
Requires SERIAL=<adb serial>, because a phone and an emulator are often connected together.
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
SERIAL = os.environ.get("SERIAL") or sys.exit("Set SERIAL=<adb serial> (see `adb devices`)")

def adb(*args, binary=False):
    out = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True)
    return out.stdout if binary else out.stdout.decode(errors="replace")

def nodes():
    for attempt in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/rt_ui.xml")
        xml = adb("shell", "cat", "/sdcard/rt_ui.xml")
        if "<hierarchy" in xml:
            break
        time.sleep(1)
    root = ET.fromstring(xml[xml.index("<hierarchy"):])
    for node in root.iter("node"):
        label = node.get("text") or node.get("content-desc") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if label and m:
            x1, y1, x2, y2 = map(int, m.groups())
            yield label, (x1 + x2) // 2, (y1 + y2) // 2, node.get("package")

def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "texts"
    if cmd == "texts":
        for label, x, y, pkg in nodes():
            print(f"{x:5d},{y:5d}  [{pkg}]  {label!r}")
    elif cmd == "tap":
        wanted = sys.argv[2].lower()
        for label, x, y, pkg in nodes():
            if label.lower() == wanted:
                adb("shell", "input", "tap", str(x), str(y))
                print(f"tapped {label!r} at {x},{y} [{pkg}]")
                return 0
        print(f"NOT FOUND: {sys.argv[2]!r}")
        return 1
    elif cmd == "shot":
        open(sys.argv[2], "wb").write(adb("exec-out", "screencap", "-p", binary=True))
        print(f"saved {sys.argv[2]}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
