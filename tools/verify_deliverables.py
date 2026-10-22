from pathlib import Path
from PIL import Image
import json, subprocess

root=Path(__file__).resolve().parents[1]
listing=root/'store/listing/en-US'
short=(listing/'short-description.txt').read_text(encoding='utf-8').strip()
full=(listing/'full-description.txt').read_text(encoding='utf-8').strip()
assert len(short)<=80, len(short)
assert len(full)<=4000, len(full)
report={'listing_characters':{'short':len(short),'full':len(full)},'screenshots':{}}
for kind,minimum,maximum,minedge,maxedge in [('phone',2,8,320,3840),('tablet-7',1,8,320,3840),('tablet-10',1,8,1080,7680),('desktop',4,8,1080,7680)]:
    files=list((root/f'store/screenshots/{kind}').glob('*.png'))
    assert minimum<=len(files)<=maximum,(kind,len(files))
    report['screenshots'][kind]=[]
    for p in files:
        im=Image.open(p); w,h=im.size
        assert minedge<=min(w,h) and max(w,h)<=maxedge,(p,im.size)
        assert w*9==h*16 or w*16==h*9,(p,im.size)
        assert p.stat().st_size<=8*1024*1024,p
        im.verify()
        report['screenshots'][kind].append({'file':p.name,'size':[w,h],'bytes':p.stat().st_size})
for name,size,limit in [('app-icon-512.png',(512,512),1024*1024),('feature-graphic-1024x500.png',(1024,500),15*1024*1024)]:
    p=root/'store/graphics'/name
    assert Image.open(p).size==size
    assert p.stat().st_size<=limit
assert (root/'index.html').exists()
assert 'seandyadryan2@gmail.com' in (root/'index.html').read_text(encoding='utf-8')
assert (root/'store/video/qr-scanner-pro-promo.mp4').stat().st_size>100000
report['passed']=True
(root/'docs/asset-validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({'passed':True,'short_characters':len(short),'full_characters':len(full),'screenshots':{k:len(v) for k,v in report['screenshots'].items()}},indent=2))
