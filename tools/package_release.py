"""Validate the signed release and collect the Play Console handoff, excluding keys."""
from pathlib import Path
import hashlib, json, os, struct, subprocess, zipfile, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
java=Path(os.environ.get('JAVA_HOME',r'C:/Program Files/Android/Android Studio/jbr'))/'bin'/('java.exe' if os.name=='nt' else 'java')
apk=ROOT/'app/build/outputs/apk/release/app-release.apk'
aab=ROOT/'app/build/outputs/bundle/release/app-release.aab'
assert apk.is_file() and aab.is_file(),'Build release first.'
for file in [apk,aab]:
    with zipfile.ZipFile(file) as z:
        assert not any('demo_scan' in n for n in z.namelist()),'Debug scene leaked into release'
apksigner=sorted(SDK.glob('build-tools/*/lib/apksigner.jar'))[-1]
signed=subprocess.run([str(java),'-jar',str(apksigner),'verify','--verbose','--print-certs',str(apk)],capture_output=True,text=True,check=True)
zipalign=apksigner.parent.parent/('zipalign.exe' if os.name=='nt' else 'zipalign')
subprocess.run([str(zipalign),'-c','-P','16','4',str(apk)],capture_output=True,text=True,check=True)
aapt=apksigner.parent.parent/('aapt2.exe' if os.name=='nt' else 'aapt2')
badging=subprocess.run([str(aapt),'dump','badging',str(apk)],capture_output=True,text=True,check=True).stdout
assert "uses-permission: name='android.permission.INTERNET'" not in badging
assert "uses-feature: name='android.hardware.camera'" not in badging
assert "targetSdkVersion:'36'" in badging
alignments={}
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if name.endswith('.so') and any(abi in name for abi in ['arm64-v8a','x86_64']):
            data=z.read(name)
            offset=struct.unpack_from('<Q',data,32)[0]
            size,count=struct.unpack_from('<HH',data,54)
            segments=[struct.unpack_from('<Q',data,offset+i*size+48)[0] for i in range(count) if struct.unpack_from('<I',data,offset+i*size)[0]==1]
            assert min(segments)>=16384,(name,segments)
            alignments[name]=segments
jarsigner=java.with_name('jarsigner.exe' if os.name=='nt' else 'jarsigner')
aab_signature=subprocess.run([str(jarsigner),'-verify',str(aab)],capture_output=True,text=True,check=True)
assert 'jar verified.' in aab_signature.stdout,'AAB has no verified JAR signature'
tests=[ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert tests and all(int(r.get('failures','0'))==0 and int(r.get('errors','0'))==0 for r in tests)
lint=ET.parse(ROOT/'app/build/reports/lint-results-release.xml').getroot()
assert not any(i.get('severity') in ['Error','Fatal'] for i in lint.findall('issue'))
assets=json.loads((ROOT/'docs/asset-validation.json').read_text())
assert assets['passed']
ui=json.loads((ROOT/'output/ui-smoke-result.json').read_text())
assert ui.get('release_apk_smoke'),'Run the signed release smoke test first'
report={
    'package':'com.deploydulupulangnanti.qrcodescanner','version':'1.0.0 (1)',
    'unit_tests':sum(int(r.get('tests','0')) for r in tests),'unit_failures':0,
    'release_lint_errors':0,'release_lint_warnings':sum(i.get('severity')=='Warning' for i in lint.findall('issue')),
    'ui_smoke':ui,'release_excludes_debug_scene':True,
    'aab_signature_verified':True,
    'internet_permission':False,'camera_hardware_optional':True,'target_sdk':36,
    'apk_16kb_zip_alignment':True,'native_64bit_load_segment_alignment':alignments,
    'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
    'aab_sha256':hashlib.sha256(aab.read_bytes()).hexdigest(),
    'apk_signature_verification':signed.stdout.strip().splitlines(),
}
(ROOT/'output/verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
output=ROOT/'output/QRScannerPro-PlayStore-Package.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
    z.write(apk,'release/QRScannerPro-1.0.0.apk'); z.write(aab,'release/QRScannerPro-1.0.0.aab')
    mapping=ROOT/'app/build/outputs/mapping/release/mapping.txt'
    if mapping.exists(): z.write(mapping,'release/mapping.txt')
    z.write(ROOT/'index.html','privacy/index.html')
    for section in ['graphics','screenshots','listing','video']:
        for p in (ROOT/'store'/section).rglob('*'):
            if p.is_file() and p.suffix!='.log': z.write(p,str(p.relative_to(ROOT)))
    for p in (ROOT/'docs').glob('*'):
        if p.is_file(): z.write(p,str(p.relative_to(ROOT)))
    z.write(ROOT/'store/ASSETS.md','store/ASSETS.md')
    z.write(ROOT/'store/source/imagegen-prompt.txt','store/source/imagegen-prompt.txt')
    z.write(ROOT/'output/verification.json','docs/verification.json')
print(json.dumps({'package':str(output),'bytes':output.stat().st_size,'unit_tests':report['unit_tests'],'lint_errors':0,'signed_apk':True},indent=2))
