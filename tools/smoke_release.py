"""Smoke test the CI-signed APK on the dedicated emulator after store capture."""
import json,time
import capture_android as ui

assert ui.adb('get-serialno').decode().strip().startswith('emulator-'),'Use an emulator, not a personal device.'
ui.adb('logcat','-c')
ui.adb('uninstall',ui.PKG)
ui.adb('install',ui.ROOT/'app/build/outputs/apk/release/app-release.apk',timeout=180)
ui.launch(capture=False)
ui.tap_node(text='Create')
ui.tap_node(klass='android.widget.EditText')
time.sleep(2)
for character in 'a.co':
    ui.shell('input','text',character)
    time.sleep(.4)
ui.shell('input','keyevent','4')
ui.tap_node(text='Create QR code')
time.sleep(2)
assert any(n.get('text')=='https://a.co' for n in ui.hierarchy().iter('node')),'Optimized QR generation failed'
ui.shot(ui.ROOT/'output/release-qr-result.png')
ui.tap_node(desc='Close result')
ui.shell('pm','grant',ui.PKG,'android.permission.CAMERA')
ui.launch(capture=False)
time.sleep(3)
tree=ui.hierarchy()
assert not any(n.get('text')=='Camera unavailable' for n in tree.iter('node')),'Camera initialization failed'
ui.shot(ui.ROOT/'output/release-camera.png')
logs=ui.adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace')
assert f'Process: {ui.PKG},' not in logs,'Release runtime crash'
result=json.loads((ui.ROOT/'output/ui-smoke-result.json').read_text())
result['release_apk_smoke']=['signed APK installed','R8 optimized QR generation','camera preview initialization','no app runtime crash']
(ui.ROOT/'output/ui-smoke-result.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print('Signed release APK smoke test passed.',flush=True)
