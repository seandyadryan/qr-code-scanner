"""Render Play artwork from device captures. Deterministic, editable typography and frames."""
from pathlib import Path
import argparse, json, math, subprocess
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / 'store'
PAPER = '#F8F6F2'; INK = '#202820'; RUST = '#B94C2B'; MUTED = '#73796D'; SAGE = '#E7EBDD'
FONTS = Path('C:/Windows/Fonts')
def font(size, bold=False):
    choices = ['segoeuib.ttf', 'arialbd.ttf'] if bold else ['segoeui.ttf', 'arial.ttf']
    for name in choices:
        if (FONTS/name).exists(): return ImageFont.truetype(str(FONTS/name), int(size))
    return ImageFont.truetype('DejaVuSans.ttf', int(size))

def text(im, xy, value, size, fill=INK, bold=False, spacing=4):
    ImageDraw.Draw(im).multiline_text(xy, value, font=font(size,bold), fill=fill, spacing=spacing)

def mark(im, xy, size, bg=RUST):
    d=ImageDraw.Draw(im); x,y=xy
    d.rounded_rectangle((x,y,x+size,y+size), radius=size*.23, fill=bg)
    u=size/9
    for cx,cy in [(2,2),(5,2),(2,5)]:
        d.rectangle((x+cx*u,y+cy*u,x+(cx+2)*u,y+(cy+2)*u), fill=PAPER)
        d.rectangle((x+(cx+.55)*u,y+(cy+.55)*u,x+(cx+1.45)*u,y+(cy+1.45)*u), fill=bg)
    for cx,cy in [(5,5),(6,6),(5,7),(7,5),(7,7)]:
        d.rectangle((x+cx*u,y+cy*u,x+(cx+.8)*u,y+(cy+.8)*u), fill=PAPER)

def device(im, capture, box, tablet=False):
    x,y,w,h=map(int,box)
    raw=Image.open(capture).convert('RGB')
    border=max(8,int(w*.024))
    screen=ImageOps.contain(raw,(w-2*border,h-2*border),Image.Resampling.LANCZOS)
    if not tablet:
        # Clean illustrative device chrome avoids emulator-only satellite/debug icons.
        chrome=ImageDraw.Draw(screen)
        background=screen.getpixel((3,3))
        foreground='#E3E9DE' if sum(background)<384 else INK
        chrome.rectangle((0,0,screen.width,int(screen.height*.075)),fill=background)
        cy=int(screen.height*.04)
        chrome.text((int(screen.width*.09),cy),'9:41',font=font(max(12,int(screen.width*.044))),fill=foreground,anchor='lm')
        bx=int(screen.width*.86); bw=max(14,int(screen.width*.045)); bh=max(8,int(screen.width*.023)); by=cy-bh//2
        chrome.rounded_rectangle((bx,by,bx+bw,by+bh),radius=2,outline=foreground,width=2)
        chrome.rectangle((bx+3,by+3,bx+bw-3,by+bh-3),fill=foreground)
        chrome.rectangle((bx+bw+1,cy-2,bx+bw+3,cy+2),fill=foreground)
    actualw,actualh=screen.width+2*border,screen.height+2*border
    x+=(w-actualw)//2; y+=(h-actualh)//2; w,h=actualw,actualh
    outer=(x,y,x+w,y+h)
    shadow=Image.new('RGBA',im.size); sd=ImageDraw.Draw(shadow)
    sd.rounded_rectangle((x+10,y+22,x+w+10,y+h+22),radius=w*.09,fill=(20,30,20,75))
    shadow=shadow.filter(ImageFilter.GaussianBlur(22)); im.paste(shadow,(0,0),shadow)
    d=ImageDraw.Draw(im)
    d.rounded_rectangle(outer,radius=min(w,h)*.075,fill='#171C18',outline='#677064',width=3)
    inset=border
    mask=Image.new('L',screen.size); ImageDraw.Draw(mask).rounded_rectangle((0,0,screen.width,screen.height),radius=min(w,h)*.055,fill=255)
    im.paste(screen,(x+inset,y+inset),mask)
    if not tablet:
        cy=y+inset+int(screen.height*.04)
        d.ellipse((x+w//2-9,cy-9,x+w//2+9,cy+9),fill='#101510')
    else:
        d.ellipse((x+w//2-4,y+3,x+w//2+4,y+11),fill='#444B43')

CARDS = [
    ('scan','QR & barcodes.\nOne simple scan.','Scan QR codes and barcodes with your camera.','SCAN WITH CONFIDENCE'),
    ('create','A shortcut\nto your world.','Turn links and ideas into shareable QR codes.','CREATE SOMETHING USEFUL'),
    ('history','Good finds.\nKept close.','Search your history. Star the ones that matter.','YOUR PERSONAL COLLECTION'),
    ('result','See it first.\nThen decide.','Review, copy, save, or open. You’re in control.','THOUGHTFULLY DESIGNED'),
    ('wifi','Welcome them\nwith one scan.','Create a QR for your guest Wi-Fi network.','SHARE THE CONNECTION'),
    ('settings','Less noise.\nMore privacy.','No ads. No account. Works offline.','MADE FOR EVERY DAY'),
    ('dark','A softer view.\nAfter hours.','Switch to a calm dark appearance.','LIGHT OR DARK. YOUR CALL.'),
]

def portrait_card(kind, name, index, title, subtitle, label, size=(1080,1920)):
    im=Image.new('RGB',size,PAPER); d=ImageDraw.Draw(im)
    d.ellipse((730,1050,1450,1790),fill=SAGE)
    d.line((72,172,1008,172),fill='#DDDFD5',width=2)
    mark(im,(72,63),65); text(im,(157,73),'QR SCANNER PRO',26,bold=True)
    text(im,(74,216),title,86,bold=True,spacing=-3)
    text(im,(77,433),subtitle,29,fill=MUTED)
    raw=Image.open(STORE/'captures'/kind/f'{name}.png')
    ratio=raw.width/raw.height
    h=1280 if kind=='phone' else 1210
    w=int(h*ratio)
    device(im,STORE/'captures'/kind/f'{name}.png',((1080-w)//2,540,w,h),kind!='phone')
    text(im,(76,1847),f'{index:02d} / {label}',23,bold=True)
    out=STORE/'screenshots'/kind; out.mkdir(parents=True,exist_ok=True)
    im.save(out/f'{index:02d}-{name}.png',optimize=True)
    return im

def landscape_card(kind,name,index,title,subtitle,label):
    im=Image.new('RGB',(1920,1080),PAPER); d=ImageDraw.Draw(im)
    d.ellipse((1160,350,2200,1390),fill=SAGE)
    mark(im,(90,88),67); text(im,(178,103),'QR SCANNER PRO',25,bold=True)
    d.line((90,205,740,205),fill='#DDDFD5',width=2)
    text(im,(85,284),title,80,bold=True,spacing=0)
    # Short lines keep the left column composed and clear of the device.
    import textwrap
    text(im,(91,518),'\n'.join(textwrap.wrap(subtitle,34)),31,fill=MUTED,spacing=12)
    text(im,(91,933),f'{index:02d} / {label}',21,bold=True)
    device(im,STORE/'captures'/kind/f'{name}.png',(810,235,1020,574),True)
    out=STORE/'screenshots'/kind; out.mkdir(parents=True,exist_ok=True)
    im.save(out/f'{index:02d}-{name}.png',optimize=True)
    return im

def main(video=False):
    graphics=STORE/'graphics'; graphics.mkdir(parents=True,exist_ok=True)
    icon=Image.new('RGB',(512,512),RUST); mark(icon,(0,0),512); icon.save(graphics/'app-icon-512.png')
    # Legacy launchers use the same exact geometric identity.
    for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        p=ROOT/f'app/src/main/res/mipmap-{density}'; p.mkdir(parents=True,exist_ok=True)
        icon.resize((px,px),Image.Resampling.LANCZOS).save(p/'ic_launcher.png')
        icon.resize((px,px),Image.Resampling.LANCZOS).save(p/'ic_launcher_round.png')
    if not (STORE/'captures/phone/scan.png').exists():
        print('Icon ready; capture Android screens before rendering marketing cards.'); return
    for i,(name,title,sub,label) in enumerate(CARDS,1): portrait_card('phone',name,i,title,sub,label)
    for i,(name,title,sub,label) in enumerate(CARDS[:4],1):
        portrait_card('tablet-7',name,i,title,sub,label)
        for kind in ['tablet-10','desktop']: landscape_card(kind,name,i,title,sub,label)
    feature=Image.new('RGB',(1024,500),PAPER); d=ImageDraw.Draw(feature)
    d.ellipse((660,-75,1160,580),fill=SAGE)
    mark(feature,(50,47),49); text(feature,(115,55),'QR SCANNER PRO',19,bold=True)
    text(feature,(47,145),'A little scan.\nA lot of possibility.',47,bold=True,spacing=0)
    text(feature,(50,299),'Scan. Create. Keep discovering.',23,fill=MUTED)
    text(feature,(50,417),'NO ADS  /  NO ACCOUNT  /  WORKS OFFLINE',13,bold=True)
    device(feature,STORE/'captures/phone/scan.png',(731,46,180,400))
    feature.save(graphics/'feature-graphic-1024x500.png',optimize=True)
    preview=Image.new('RGB',(1620,960),PAPER)
    for i,(name,*_) in enumerate(CARDS):
        card=Image.open(STORE/f'screenshots/phone/{i+1:02d}-{name}.png').resize((540,960))
        if i<3: preview.paste(card,(i*540,0))
    preview.save(STORE/'preview.png',optimize=True)
    manifest=[]
    for p in sorted(STORE.rglob('*.png')):
        if 'source' in p.parts or 'captures' in p.parts: continue
        img=Image.open(p)
        manifest.append(dict(file=str(p.relative_to(STORE)).replace('\\','/'),width=img.width,height=img.height,bytes=p.stat().st_size))
    (STORE/'asset-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    if video: make_video()
    print('Store assets rendered and dimensions recorded in asset-manifest.json.')

def make_video():
    import imageio_ffmpeg
    out=STORE/'video'; out.mkdir(parents=True,exist_ok=True)
    ff=imageio_ffmpeg.get_ffmpeg_exe()
    args=[ff,'-y','-f','rawvideo','-vcodec','rawvideo','-s','1920x1080','-pix_fmt','rgb24','-r','24','-i','-','-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(out/'qr-scanner-pro-promo.mp4')]
    proc=subprocess.Popen(args,stdin=subprocess.PIPE,stderr=open(out/'encoding.log','w'))
    for stage in range(6):
        name,title,sub,label=CARDS[stage]
        base=Image.new('RGB',(1920,1080),PAPER)
        ImageDraw.Draw(base).ellipse((1110,-100,2060,1230),fill=SAGE)
        mark(base,(96,87),62); text(base,(179,100),'QR SCANNER PRO',26,bold=True)
        text(base,(90,300),title,91,bold=True,spacing=0)
        import textwrap
        text(base,(96,568),'\n'.join(textwrap.wrap(sub,38)),31,fill=MUTED,spacing=12)
        text(base,(96,946),label,21,bold=True)
        device(base,STORE/'captures/phone'/f'{name}.png',(1270,76,416,925))
        for frame in range(120):
            t=frame/24
            # A subtle push-in plus calm fade between the six five-second scenes.
            scale=1+.012*frame/120
            bw,bh=int(1920*scale),int(1080*scale)
            image=base.resize((bw,bh),Image.Resampling.BICUBIC)
            image=image.crop(((bw-1920)//2,(bh-1080)//2,(bw+1920)//2,(bh+1080)//2))
            alpha=min(1,t/.3,(5-t)/.3)
            if alpha<1: image=Image.blend(Image.new('RGB',(1920,1080),PAPER),image,max(0,alpha))
            proc.stdin.write(image.tobytes())
    proc.stdin.close()
    if proc.wait()!=0: raise RuntimeError('Video encoding failed; see encoding.log')
    (out/'storyboard.txt').write_text('\n\n'.join(f'{i*5:02d}–{i*5+5:02d}s: {title.replace(chr(10)," ")} | {sub}' for i,(_,title,sub,_) in enumerate(CARDS[:6]))+'\n\n30 seconds; 1920x1080; H.264; 24 fps; no audio. Upload as public or unlisted; turn off ads and age restrictions.',encoding='utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--video',action='store_true'); args=parser.parse_args(); main(args.video)
