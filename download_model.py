#!/usr/bin/env python3
"""
Download TTS model for EPUB Audio Reader.
Baixa o modelo vits-piper-en_US-amy-low do release oficial do Sherpa-ONNX.
"""

import argparse
import os
import subprocess
import sys

MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2"
MODEL_DIR = "vits-piper-en_US-amy-low"
ASSET_DIR = "core/tts/src/main/assets"

def download_model(token: str = None):
    print("Downloading TTS model: vits-piper-en_US-amy-low...")

    # Criar diretorio de assets
    os.makedirs(ASSET_DIR, exist_ok=True)

    # Download do modelo
    tar_file = "/tmp/vits-piper-en_US-amy-low.tar.bz2"

    cmd = ["wget", "-q", "--show-progress", "-O", tar_file, MODEL_URL]
    if token:
        # GitHub releases nao precisa de token, mas aceitamos para compatibilidade
        pass

    print(f"Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"wget failed: {result.stderr}")
        # Tentar com curl
        cmd2 = ["curl", "-L", "-o", tar_file, MODEL_URL]
        print(f"Trying curl: {' '.join(cmd2)}")
        result2 = subprocess.run(cmd2, capture_output=True, text=True)
        if result2.returncode != 0:
            print(f"curl also failed: {result2.stderr}")
            sys.exit(1)

    print(f"Downloaded: {tar_file}")
    print(f"Size: {os.path.getsize(tar_file) / 1024 / 1024:.1f} MB")

    # Extrair
    extract_dir = "/tmp/tts_model_extract"
    os.makedirs(extract_dir, exist_ok=True)

    print(f"Extracting to {extract_dir}...")
    result = subprocess.run(
        ["tar", "-xjf", tar_file, "-C", extract_dir],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print(f"Extract failed: {result.stderr}")
        sys.exit(1)

    # Mover para assets
    src_dir = os.path.join(extract_dir, MODEL_DIR)
    dst_dir = os.path.join(ASSET_DIR, MODEL_DIR)

    print(f"Moving model to {dst_dir}...")
    if os.path.exists(dst_dir):
        import shutil
        shutil.rmtree(dst_dir)

    import shutil
    shutil.move(src_dir, dst_dir)

    # Listar arquivos
    print(f"Model files in {dst_dir}:")
    for root, dirs, files in os.walk(dst_dir):
        for f in files:
            filepath = os.path.join(root, f)
            size = os.path.getsize(filepath)
            relpath = os.path.relpath(filepath, ASSET_DIR)
            print(f"  {relpath}: {size/1024:.1f} KB")

    total_size = sum(
        os.path.getsize(os.path.join(root, f))
        for root, dirs, files in os.walk(dst_dir)
        for f in files
    )
    print(f"Total model size: {total_size / 1024 / 1024:.1f} MB")
    print("Done!")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", default=None, help="HF token (not used for GitHub releases)")
    args = parser.parse_args()
    download_model(args.token)
