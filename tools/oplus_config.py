#!/usr/bin/env python3
"""Read and write the OPlus camera configs, which ColorOS 17 encrypts on some devices.

The format is a small envelope around AES-128-ECB:

    01 01 <2 bytes> <ciphertext> FF

The key is not a constant: it is derived from the device's own libOplusSecurity.so, 32 bytes at
offset 0x7AB XOR 0x3F, every second byte kept. That is why a config patched for one device cannot be
re-encrypted for another — the tool has to run against the target device's library.

Verified on 真我 GT7 Pro (RMX5010, ColorOS 17.0.0.111): decrypt then re-encrypt reproduces the stock
file byte for byte, so the 2 header bytes are carried over unchanged and never recomputed.

    python oplus_config.py key   <libOplusSecurity.so>
    python oplus_config.py dump  <libOplusSecurity.so> <config> <out>
    python oplus_config.py pack  <libOplusSecurity.so> <plain.json> <template-config> <out>

`pack` takes the envelope header from the stock file it replaces, so only the payload changes.
"""
import subprocess
import sys
from pathlib import Path

KEY_OFFSET = 0x7AB
KEY_XOR = 0x3F


def config_key(security: Path) -> bytes:
    raw = security.read_bytes()
    if len(raw) < KEY_OFFSET + 32:
        raise SystemExit(f"{security} is too small to hold the key")
    return bytes(value ^ KEY_XOR for value in raw[KEY_OFFSET:KEY_OFFSET + 32])[::2]


def _aes(key: bytes, data: bytes, decrypt: bool) -> bytes:
    args = ["openssl", "enc", "-aes-128-ecb", "-nopad", "-K", key.hex()]
    args.append("-d" if decrypt else "-e")
    done = subprocess.run(args, input=data, capture_output=True)
    if done.returncode != 0:
        raise SystemExit(f"openssl failed: {done.stderr.decode()[:200]}")
    return done.stdout


def is_envelope(data: bytes) -> bool:
    return data[:2] == b"\x01\x01" and data[-1] == 0xFF and len(data) > 8


def decrypt(key: bytes, data: bytes) -> bytes:
    if not is_envelope(data):
        return data
    plain = _aes(key, data[4:-4], decrypt=True)
    if not plain or not 1 <= plain[-1] <= 16:
        raise SystemExit("bad padding: wrong key for this device")
    return plain[:-plain[-1]]


def encrypt(key: bytes, plain: bytes, frame: bytes) -> bytes:
    """Re-wraps `plain` in the envelope of `frame` (the stock file being replaced).

    The 4-byte head and the 4-byte tail are copied verbatim. Neither is a checksum of the payload:
    crc32, adler32 and plain sums of both the plaintext and the ciphertext were checked against all
    four stock configs and none matched, and ECB has no IV to carry. Only the ciphertext changes.
    """
    if not is_envelope(frame):
        raise SystemExit("template is not an encrypted config")
    pad = 16 - len(plain) % 16
    return frame[:4] + _aes(key, plain + bytes([pad]) * pad, decrypt=False) + frame[-4:]


def main() -> None:
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    action, security = sys.argv[1], Path(sys.argv[2])
    key = config_key(security)

    if action == "key":
        print(f"{security}: AES key {key.hex()}")
    elif action == "dump":
        source, out = Path(sys.argv[3]), Path(sys.argv[4])
        plain = decrypt(key, source.read_bytes())
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(plain)
        print(f"{source} -> {out}  {'envelope' if is_envelope(source.read_bytes()) else 'plaintext'}"
              f"  {len(plain)}B")
    elif action == "pack":
        plain, template, out = Path(sys.argv[3]), Path(sys.argv[4]), Path(sys.argv[5])
        stock = template.read_bytes()
        packed = encrypt(key, plain.read_bytes(), stock)
        # Self-check: the camera has to read back exactly what was packed.
        if decrypt(key, packed) != plain.read_bytes():
            raise SystemExit("round trip mismatch, refusing to write")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(packed)
        print(f"{plain} -> {out}  {len(packed)}B  (round trip ok)")
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
