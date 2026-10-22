"""Build the staged scan target and sample history used only for debug captures."""
from pathlib import Path
import json
import qrcode
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
dest = ROOT / 'app/src/debug/assets'
dest.mkdir(parents=True, exist_ok=True)
scene = Image.open(ROOT / 'store/source/cafe-scene.png').convert('RGB')
qr = qrcode.make('https://example.com/coffee').convert('RGB').resize((292, 292), Image.Resampling.NEAREST)
# The source photo label is at x=300..665, y=656..1040.
scene.paste(qr, (335, 700))
scene.save(dest / 'demo_scan.png', optimize=True)
qr.save(ROOT / 'store/source/test-qr.png')
samples = [
    ('https://studio.example.com', 'QR_CODE', True, False),
    ('WIFI:T:WPA;S:Studio Guest;P:hello-weekend;H:false;;', 'QR_CODE', True, True),
    ('https://example.com/coffee', 'QR_CODE', False, False),
    ('mailto:hello@example.com', 'QR_CODE', False, True),
    ('4006381333931', 'EAN_13', False, False),
    ('Take a moment. Find something new.', 'QR_CODE', True, True),
]
records = [dict(id=f'demo-{i}', value=v, format=f, favorite=star, created=created, time=1792625400000-i*7200000) for i,(v,f,star,created) in enumerate(samples)]
(ROOT / 'store/source/demo-history.json').write_text(json.dumps(records), encoding='utf-8')
print('Debug scene and demonstration records prepared.')
