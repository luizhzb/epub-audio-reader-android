#!/usr/bin/env python3
"""Download TTS model pt-BR para EPUB Audio Reader."""

import argparse
import os
import shutil
import subprocess
import sys

# Modelo pt-BR — vits-piper pt_BR-faber-medium
MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-faber-medium.tar.bz2"
MODEL_DIR = "vits-piper-pt_BR-faber-medium"
MODEL_NAME = "pt_BR-faber-medium.onnx"
ASSET_DIR = "core/tts/src/main/assets"

def download_model(token: str = None):
    print("Downloading TTS model pt-BR: vits-piper-pt_BR-faber-medium...")
    os.makedirs(ASSET_DIR, exist_ok=True)
    
    tar_file = "/tmp/vits-piper-pt_BR-faber-medium.tar.bz2"
    
    # Verificar se ja existe
    if os.path.exists(os.path.join(ASSET_DIR, MODEL_DIR, MODEL_NAME)):
        print("Modelo ja existe em assets, pulando download.")
        return
    
    cmd = ["wget", "-q", "--show-progress", "-O", tar_file, MODEL_URL]
    print(f"Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"wget failed: {result.stderr}")
        cmd2 = ["curl", "-L", "--progress-bar", "-o", tar_file, MODEL_URL]
        print(f"Trying curl...")
        result2 = subprocess.run(cmd2, capture_output=True, text=True)
        if result2.returncode != 0:
            print(f"curl also failed: {result2.stderr}")
            sys.exit(1)
    
    print(f"Downloaded: {os.path.getsize(tar_file) / 1024 / 1024:.1f} MB")
    
    extract_dir = "/tmp/tts_model_extract"
    if os.path.exists(extract_dir):
        shutil.rmtree(extract_dir)
    os.makedirs(extract_dir, exist_ok=True)
    
    print(f"Extracting...")
    result = subprocess.run(["tar", "-xjf", tar_file, "-C", extract_dir], capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Extract failed: {result.stderr}")
        sys.exit(1)
    
    src_dir = os.path.join(extract_dir, MODEL_DIR)
    dst_dir = os.path.join(ASSET_DIR, MODEL_DIR)
    
    if os.path.exists(dst_dir):
        shutil.rmtree(dst_dir)
    shutil.move(src_dir, dst_dir)
    
    print(f"Model files:")
    for root, dirs, files in os.walk(dst_dir):
        for f in files:
            filepath = os.path.join(root, f)
            relpath = os.path.relpath(filepath, ASSET_DIR)
            print(f"  {relpath}: {os.path.getsize(filepath) / 1024:.1f} KB")
    
    total = sum(os.path.getsize(os.path.join(root, f)) for root, _, files in os.walk(dst_dir) for f in files)
    print(f"Total: {total / 1024 / 1024:.1f} MB")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", default=None)
    args = parser.parse_args()
    download_model(args.token)
