# Store artwork

All copy is English. The app identity is QR Scanner Pro; the developer is PT Deploy Dulu Pulang Nanti.

The marketing cards are generated from captured Android UI, placed inside complete device frames. The scanner card uses a staged camera scene in a debug-only capture mode; it is not shipped as a production camera feed. Sample records are demonstration content. Tablet and desktop images use captures at their respective device dimensions and the actual adaptive layout.

Phone frames use clean illustrative status-bar chrome (9:41 and a battery icon) so emulator-only debug and satellite icons do not appear in the marketing artwork. Application content remains the captured Android UI, with its aspect ratio preserved.

Sizes:
- Icon: 512 × 512 PNG.
- Feature graphic: 1024 × 500 PNG.
- Phone: 7 cards, 1080 × 1920 PNG (9:16).
- 7-inch tablet: 4 cards, 1080 × 1920 PNG (9:16).
- 10-inch tablet: 4 cards, 1920 × 1080 PNG (16:9).
- Desktop: 4 cards, 1920 × 1080 PNG (16:9). These represent the Android large-screen layout; no separate Windows executable is provided.
- Promo: 1920 × 1080 H.264 MP4, 24 fps, 30 seconds, original visual motion, no licensed soundtrack.

The cafe source photo was generated with the built-in ImageGen tool. Prompt: portrait editorial cafe product photo with a cream coffee bag, blank white label, travertine table, natural warm light, muted beige/charcoal/burnt orange, no writing, logo, phone, UI, or watermark. Exact generation prompt is recorded in `source/imagegen-prompt.txt`.

Run `python tools/make_store_assets.py` after the device captures are available in `store/captures/`. Use `--video` to also encode the promo. Dependencies: Pillow, qrcode, imageio-ffmpeg.
