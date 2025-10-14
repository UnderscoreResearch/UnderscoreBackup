# X25519 Encryption Package

This package contains the implementation of the X25519 elliptic curve cryptography for the Underscore Backup system.

## Files

- **Curve25519.java**: Implementation of the Curve25519 elliptic curve.
- **Field25519.java**: Implementation of the Field25519 mathematical field.
- **X25519.java**: X25519 key exchange algorithm implementation.
- **X25519KeyMethod.java**: Implementation of the X25519 key method.

X25519 is an elliptic curve Diffie-Hellman key exchange using Curve25519. It provides strong security for key exchange and is widely used in modern cryptographic systems. While not quantum-resistant, it offers excellent security against classical computing attacks.

Files in this directory are copyright 2017 Google Inc. Licensed under the Apache License, Version 2.0 (the "License").