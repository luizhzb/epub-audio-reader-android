#!/usr/bin/env python3
"""
Script para baixar modelo TTS da HuggingFace.
Executado pelo GitHub Actions durante o build — NAO eh parte do app.

Uso:
    python3 download_model.py --token $HF_TOKEN

O modelo eh salvo em: core/tts/src/main/assets/tts_model/
 e empacotado no APK automaticamente.
"""

import argparse
import os
import sys
import urllib.request

MODEL_DIR = "core/tts/src/main/assets/tts_model"

# Modelo en-US amy-low: ~16MB, funciona com Sherpa-ONNX
FILES = {
    "model.onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/low/en_US-amy-low.onnx",
    "model.onnx.json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/low/en_US-amy-low.onnx.json",
}


def download_file(url: str, dest: str, token: str) -> bool:
    print(f"  -> {dest}")
    try:
        req = urllib.request.Request(url)
        req.add_header("Authorization", f"Bearer {token}")
        req.add_header("User-Agent", "epub-audio-reader/1.0")

        with urllib.request.urlopen(req, timeout=120) as resp:
            if resp.status != 200:
                print(f"     ERRO: HTTP {resp.status}")
                return False
            data = resp.read()
            with open(dest, "wb") as f:
                f.write(data)
            print(f"     OK: {len(data):,} bytes")
            return True
    except Exception as e:
        print(f"     ERRO: {e}")
        return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", required=True, help="HuggingFace API token")
    args = parser.parse_args()

    os.makedirs(MODEL_DIR, exist_ok=True)

    print("=== Download Modelo TTS ===")
    print(f"Destino: {MODEL_DIR}")
    print("")

    all_ok = True
    for filename, url in FILES.items():
        dest = os.path.join(MODEL_DIR, filename)
        if not download_file(url, dest, args.token):
            all_ok = False

    # Criar tokens.txt vazio (Sherpa-ONNX nao precisa para Piper)
    tokens_path = os.path.join(MODEL_DIR, "tokens.txt")
    with open(tokens_path, "w") as f:
        pass
    print(f"  -> tokens.txt (vazio, placeholder)")

    print("")
    if all_ok:
        print("=== SUCESSO ===")
        for f in os.listdir(MODEL_DIR):
            path = os.path.join(MODEL_DIR, f)
            size = os.path.getsize(path)
            print(f"  {f}: {size:,} bytes")
        return 0
    else:
        print("=== FALHA ===")
        return 1


if __name__ == "__main__":
    sys.exit(main())
