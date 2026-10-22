"""Decode a fixture through Android's real photo picker on a dedicated emulator."""
import json,re,time
import capture_android as ui

assert ui.adb('get-serialno').decode().strip().startswith('emulator-')
ui.adb('push',ui.ROOT/'store/source/test-qr.png','/sdcard/Pictures/QRScannerQA.png')
ui.shell('am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Pictures/QRScannerQA.png')
ui.launch(capture=False)
ui.tap_node(text='From image')
time.sleep(2)
photos=[n for n in ui.hierarchy().iter('node') if n.get('content-desc','').startswith('Photo taken on')]
assert len(photos)==1,'Use a dedicated emulator with exactly one photo fixture'
x1,y1,x2,y2=map(int,re.findall(r'\d+',photos[0].get('bounds')))
ui.shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
deadline=time.monotonic()+120
while True:
    if any(n.get('text')=='https://example.com/coffee' for n in ui.hierarchy().iter('node')): break
    assert time.monotonic()<deadline,'Signed release did not decode the selected QR image'
    time.sleep(1)
ui.shot(ui.ROOT/'output/release-image-import.png')
report=ui.ROOT/'output/ui-smoke-result.json'
result=json.loads(report.read_text())
check='system photo picker QR image decoded as https://example.com/coffee'
if check not in result['release_apk_smoke']: result['release_apk_smoke'].append(check)
report.write_text(json.dumps(result,indent=2),encoding='utf-8')
print('Signed release image import and QR decoding passed.',flush=True)
