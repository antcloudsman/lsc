# Build / debug environment for the Feng Shui compass app.
# Source lives on the host and is bind-mounted; dependencies are installed here.
FROM rust:1.90-slim

# Native dependencies needed to build and (headlessly) run eframe/egui on Linux.
RUN apt-get update && apt-get install -y --no-install-recommends \
        pkg-config \
        libx11-dev \
        libxkbcommon-dev \
        libwayland-dev \
        libgl1-mesa-dev \
        libegl1-mesa-dev \
        libfontconfig1-dev \
        fonts-noto-cjk \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# Pre-fetch crates so builds are fast (source is bind-mounted at /src).
CMD ["cargo", "build"]
