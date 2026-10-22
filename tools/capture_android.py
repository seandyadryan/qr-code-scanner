"""Capture the built Android UI with adb. Debug-only staged scene; no release hooks."""
from pathlib import Path
import os, subprocess, time, json, re, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('ANDROID_HOME', str(Path.home()/'AppData/Local/Android/Sdk')))
ADB=str(SDK/'platform-tools/adb.exe' if os.name=='nt' else SDK/'platform-tools/adb')
PKG='com.deploydulupulangnanti.qrcodescanner'
def adb(*args, timeout=60):
    p=subprocess.run([ADB,*map(str,args)],capture_output=True,timeout=timeout)
    if p.returncode: raise RuntimeError((p.stdout+p.stderr).decode(errors='replace')[:800])
    return p.stdout
def shell(*args): return adb('shell',*args).decode(errors='replace')
def launch(tab=None,capture=True):
    shell('am','force-stop',PKG)
    args=['am','start','-W','-n',PKG+'/.MainActivity']
    if capture: args += ['--ez','capture','true','--ei','tab',str(tab or 0)]
    shell(*args); time.sleep(2)
    wait_ready({0:'scan',1:'create',2:'history',3:'settings',4:'result'}.get(tab or 0,'scan'))
def hierarchy():
    for attempt in range(4):
        try:
            dumped=shell('uiautomator','dump','/data/local/tmp/qrcodescanner-window.xml')
            if 'dumped to:' not in dumped: raise RuntimeError(dumped)
            return ET.fromstring(shell('cat','/data/local/tmp/qrcodescanner-window.xml'))
        except (RuntimeError,ET.ParseError):
            if attempt==3: raise
            time.sleep(1)
def tap_node(text=None,desc=None,klass=None,index=0,checkable=None):
    for attempt in range(8):
        nodes=[n for n in hierarchy().iter('node') if (text is None or n.get('text')==text) and (desc is None or n.get('content-desc')==desc) and (klass is None or n.get('class')==klass) and (checkable is None or n.get('checkable')==str(checkable).lower())]
        if len(nodes)>index: break
        if attempt==7: raise RuntimeError(f'UI node missing: {text or desc or klass}')
        time.sleep(1)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[index].get('bounds')))
    shell('input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(.6)
def wait_ready(name):
    targets={'scan':'A little scan.','create':'Make it a QR.','history':'Worth keeping.','settings':'Just your style.','dark-appearance':'A little scan.'}
    if name in targets or name in ['result','wifi']:
        deadline=time.monotonic()+120
        while True:
            tree=hierarchy()
            if any(n.get('text')=="System UI isn't responding" for n in tree.iter('node')):
                wait=next((n for n in tree.iter('node') if n.get('text')=='Wait'),None)
                if wait is not None:
                    x1,y1,x2,y2=map(int,re.findall(r'\d+',wait.get('bounds')))
                    shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
                    time.sleep(2)
                    continue
            if name in ['result','wifi']:
                ready=any(n.get('content-desc')=='QR code for the content below' for n in tree.iter('node'))
            else:
                ready=any(n.get('text','').startswith(targets[name]) for n in tree.iter('node'))
            if ready: break
            if time.monotonic()>deadline: raise RuntimeError(f'Screen did not finish loading: {name}')
            time.sleep(1)
        time.sleep(1)

def shot(path):
    wait_ready(path.stem)
    path.parent.mkdir(parents=True,exist_ok=True)
    deadline=time.monotonic()+30
    while True:
        pixels=adb('exec-out','screencap','-p')
        if len(pixels)>=25000: break
        if time.monotonic()>deadline: raise RuntimeError(f'Framebuffer is still blank: {path.name}')
        time.sleep(1)
    path.write_bytes(pixels)
    print('Captured',path.relative_to(ROOT),flush=True)
def seed():
    adb('push',ROOT/'store/source/demo-history.json','/data/local/tmp/demo-history.json')
    shell('run-as',PKG,'mkdir','-p','files')
    shell('run-as',PKG,'cp','/data/local/tmp/demo-history.json','files/history.json')

def main():
    deadline=time.monotonic()+300
    print('Waiting for the dedicated Android emulator…',flush=True)
    while True:
        try: booted=shell('getprop','sys.boot_completed').strip()=='1'
        except Exception: booted=False
        if booted: break
        if time.monotonic()>deadline: raise RuntimeError('Android did not finish booting.')
        time.sleep(3)
    assert adb('get-serialno').decode().strip().startswith('emulator-'),'Capture only on the dedicated emulator.'
    shell('input','keyevent','224')
    shell('wm','dismiss-keyguard')
    shell('locksettings','set-disabled','true')
    shell('input','keyevent','223')
    shell('input','keyevent','224')
    shell('wm','dismiss-keyguard')
    print('Android booted; installing the final debug APK.',flush=True)
    adb('logcat','-c')
    adb('install','-r',ROOT/'app/build/outputs/apk/debug/app-debug.apk',timeout=180)
    shell('settings','put','global','window_animation_scale','0')
    shell('settings','put','global','transition_animation_scale','0')
    shell('settings','put','global','animator_duration_scale','0')
    shell('settings','put','system','screen_off_timeout','2147483647')
    shell('input','keyevent','82')
    shell('settings','put','global','sysui_demo_allowed','1')
    shell('am','broadcast','-a','com.android.systemui.demo','-e','command','clock','-e','hhmm','0941')
    shell('am','broadcast','-a','com.android.systemui.demo','-e','command','battery','-e','level','100','-e','plugged','false')
    seed()
    print('Testing QR creation, favorites, history persistence and dark appearance.',flush=True)
    # Exercise the real generator and persistence with a prefilled debug form.
    # Avoid IME event flooding on hosts with slow emulator graphics.
    shell('wm','size','720x1600'); shell('wm','density','280')
    launch(tab=1)
    tap_node(text='Create QR code')
    time.sleep(3)
    root=hierarchy()
    assert any(n.get('text')=='https://example.com/studio' for n in root.iter('node')), 'QR creation result missing'
    tap_node(desc='Save favorite')
    tap_node(desc='Close result')
    launch(tab=2)
    saved=json.loads(shell('run-as',PKG,'cat','files/history.json'))
    assert any(r['value']=='https://example.com/studio' and r['created'] and r['favorite'] for r in saved),'Generated favorite did not persist'
    print('QR creation, favorites and history persistence passed.',flush=True)
    finish_captures()

def finish_captures():
    """Continue after the generator/favorite/persistence checks have passed."""
    launch(tab=3)
    tap_node(checkable=True,index=2)
    launch(tab=0)
    prefs=shell('run-as',PKG,'cat','shared_prefs/preferences.xml')
    assert 'name="dark" value="true"' in prefs,'Dark appearance did not persist'
    shot(ROOT/'output/dark-appearance.png')
    dark_capture=ROOT/'store/captures/phone/dark.png'
    dark_capture.parent.mkdir(parents=True,exist_ok=True)
    dark_capture.write_bytes((ROOT/'output/dark-appearance.png').read_bytes())
    launch(tab=3)
    tap_node(checkable=True,index=2)
    shell('pm','grant',PKG,'android.permission.CAMERA')
    capture_devices()

def capture_devices(configs=None):
    # Capture seeded states at four actual Android configurations.
    configs=configs or [('phone','720x1600','280'),('tablet-7','800x1280','160'),('tablet-10','1280x720','133'),('desktop','1280x720','107')]
    for kind,size,density in configs:
        shell('am','force-stop',PKG); seed()
        shell('wm','size',size); shell('wm','density',density); time.sleep(2)
        launch(0); shot(ROOT/f'store/captures/{kind}/scan.png')
        tap_node(text='Create'); shot(ROOT/f'store/captures/{kind}/create.png')
        tap_node(text='History'); shot(ROOT/f'store/captures/{kind}/history.png')
        tap_node(text='studio.example.com'); time.sleep(2); shot(ROOT/f'store/captures/{kind}/result.png')
        if kind=='phone':
            launch(3); shot(ROOT/'store/captures/phone/settings.png')
            shell('am','force-stop',PKG)
            demo=json.loads((ROOT/'store/source/demo-history.json').read_text())
            demo.insert(0,demo.pop(1))
            wifi_history=ROOT/'output/wifi-demo-history.json'
            wifi_history.write_text(json.dumps(demo),encoding='utf-8')
            adb('push',wifi_history,'/data/local/tmp/demo-history.json')
            shell('run-as',PKG,'cp','/data/local/tmp/demo-history.json','files/history.json')
            launch(4)
            shot(ROOT/'store/captures/phone/wifi.png')
    shell('wm','size','reset'); shell('wm','density','reset')
    logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace')
    (ROOT/'output/ui-smoke-log.txt').write_text(logs,encoding='utf-8')
    assert f'Process: {PKG},' not in logs, 'Inspect AndroidRuntime crash log'
    (ROOT/'output/ui-smoke-result.json').write_text(json.dumps({'passed':['app launch','QR create via prefilled UI','favorite via UI','generated history persisted across restart','dark appearance persisted across restart','phone/tablet/desktop layout captures'],'device':'Android 15 API 35 emulator','physical_camera_tested':False},indent=2),encoding='utf-8')
    print('UI smoke tests and captures complete.',flush=True)

if __name__=='__main__': main()
