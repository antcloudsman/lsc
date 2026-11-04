# Brief

**Question:** Rebuild the Android app swss.apk (space.xinzhi.globe, "三维山水全景地图", a Feng Shui 3D landscape compass/map) as a functionally identical desktop app.

**Purpose:** The person wants a desktop equivalent of a Feng Shui compass app, with the build/debug environment and dependencies in local Docker and source on the host.

**Targets:**
- E:\APPDetector\input\swss.apk

**Constraints:**
- Change files only inside app/
- No logos/brand text/long passages copied
- Rust + eframe/egui
- Docker for build/deps, source on host

**Done when:** A Rust/egui desktop app builds and runs with --screenshot producing a non-blank PNG, showing the Feng Shui compass (24 mountains, three plates, divisions) and knowledge base; Docker build files provided; mechanics.json and MECHANICS.md recorded.
