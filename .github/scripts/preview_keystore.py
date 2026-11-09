#!/usr/bin/env python3
"""Create stable, separate test certificate without storing a test private key.

The preview private key is deterministically derived from an existing *secret*
keystore blob (domain-separated SHA-256), never from a public repo value.
This creates an independent certificate on every CI build, allowing preview APK
updates. Release signing is NEVER used to sign the preview variant.
"""
import base64
import datetime as dt
import hashlib
import hmac
import os
from pathlib import Path

from Crypto.PublicKey import RSA
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

secret = base64.b64decode(os.environ["BALANCEHUB_KEYSTORE_BASE64"], validate=True)
if len(secret) < 256:
    raise ValueError("Missing or truncated private signing seed")
seed = hashlib.sha256(b"BalanceHub isolated preview key v1\0" + secret).digest()
counter = 0
def deterministic_random(n: int) -> bytes:
    global counter
    out = bytearray()
    while len(out) < n:
        counter += 1
        out.extend(hmac.digest(seed, counter.to_bytes(8, "big"), "sha256"))
    return bytes(out[:n])

rsa = RSA.generate(2048, randfunc=deterministic_random)
private_key = serialization.load_pem_private_key(rsa.export_key(pkcs=8), password=None)
name = x509.Name([
    x509.NameAttribute(NameOID.COMMON_NAME, "BalanceHub Test signing - not production"),
    x509.NameAttribute(NameOID.ORGANIZATION_NAME, "BalanceHub preview"),
])
certificate = (x509.CertificateBuilder()
    .subject_name(name).issuer_name(name)
    .public_key(private_key.public_key())
    .serial_number(202611090110)
    .not_valid_before(dt.datetime(2026, 1, 1, tzinfo=dt.timezone.utc))
    .not_valid_after(dt.datetime(2046, 1, 1, tzinfo=dt.timezone.utc))
    .sign(private_key, hashes.SHA256()))
data = pkcs12.serialize_key_and_certificates(
    b"balancehub-preview", private_key, certificate, None,
    serialization.BestAvailableEncryption(b"balancehub-preview"))
destination = Path(os.environ["RUNNER_TEMP"]) / "balancehub-preview.p12"
destination.write_bytes(data)
destination.chmod(0o600)
print("Preview certificate SHA-256:", certificate.fingerprint(hashes.SHA256()).hex())
