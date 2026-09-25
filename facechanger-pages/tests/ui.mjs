import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';

const variants=[
  {name:'android-small',viewport:{width:320,height:640},isMobile:true,hasTouch:true,deviceScaleFactor:2,colorScheme:'light'},
  {name:'android',viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:3,colorScheme:'light'},
  {name:'desktop',viewport:{width:1440,height:900},isMobile:false,hasTouch:false,deviceScaleFactor:1,colorScheme:'light'},
  {name:'android-dark',viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:3,colorScheme:'dark'}
];
await mkdir('facechanger-pages/test-results',{recursive:true});
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
try {
  for(const device of variants){
    const context=await browser.newContext({...device});
    const page=await context.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.goto('http://127.0.0.1:8765/',{waitUntil:'networkidle'});
    const assets=await page.evaluate(async()=>{
      const checks=[];
      for(const species of ['cockroach','wasp']){
        const img=new Image();
        img.src='./assets/'+species+'.webp?v=clean-topdown-2';
        await img.decode();
        const canvas=document.createElement('canvas');
        canvas.width=img.naturalWidth;canvas.height=img.naturalHeight;
        const ctx=canvas.getContext('2d',{willReadFrequently:true});
        ctx.drawImage(img,0,0);
        const {data}=ctx.getImageData(0,0,canvas.width,canvas.height);
        const getAlpha=(x,y)=>data[(y*canvas.width+x)*4+3];
        let difference=0;
        if(species==='wasp')for(let y=0;y<canvas.height;y++)
          for(let x=0;x<canvas.width;x++)difference+=
            Math.abs(getAlpha(x,y)-getAlpha(canvas.width-1-x,y));
        checks.push({species,width:img.naturalWidth,height:img.naturalHeight,
          corners:[getAlpha(0,0),getAlpha(canvas.width-1,0),
            getAlpha(0,canvas.height-1),getAlpha(canvas.width-1,canvas.height-1)],
          symmetry: difference/(canvas.width*canvas.height)});
      }
      return checks;
    });
    assert.ok(assets.every(a=>a.width>=80&&a.height>=80),'Imágenes nítidas de insectos');
    assert.ok(assets.every(a=>a.corners.every(v=>v<12)),'Recortes sin bordes incrustados');
    assert.ok(assets.find(a=>a.species==='wasp').symmetry<3,
      'Silueta de la avispa bilateral y sin inclinación');
    const w=device.viewport.width,h=device.viewport.height;
    const rect=async selector=>page.locator(selector).first().evaluate(el=>{
      const r=el.getBoundingClientRect(),s=getComputedStyle(el);
      return {x:r.x,y:r.y,width:r.width,height:r.height,font:parseFloat(s.fontSize),radius:parseFloat(s.borderTopLeftRadius),maxWidth:s.maxWidth,background:s.backgroundColor};
    });
    const camera=await rect('#mirror');
    assert.ok(camera.width>=w-1&&camera.height>=h-1,device.name+' full-screen camera');
    const nav=page.getByRole('button',{name:'Abrir menú'});
    assert.equal(await nav.locator('svg path').getAttribute('d'),'M3 6h18M3 12h18M3 18h18',
      device.name+' three-line hamburger, no settings gear');
    const navBox=await rect('#settings-open');
    assert.ok(navBox.width>=70&&navBox.height>=70,'Large hamburger touch target');
    await nav.click();
    await page.locator('#panel').waitFor({state:'visible'});
    await page.waitForTimeout(230);
    const panel=await rect('#panel');
    assert.ok(Math.abs(panel.x)<1&&Math.abs(panel.width-w)<1&&Math.abs(panel.height-h)<1,
      device.name+' edge-to-edge screen: '+JSON.stringify(panel));
    assert.equal(panel.maxWidth,'none','No desktop max-width on phone');
    assert.equal(panel.background,device.colorScheme==='dark'?'rgb(21, 19, 25)':'rgb(250, 248, 255)');
    assert.equal(await page.locator('.panel-lead').count(),0,'No hero text that wastes phone space');
    const back=await rect('.settings-close'),title=await rect('.panel-top h1');
    assert.ok(back.x+back.width<=title.x+1&&title.x+title.width<=w+1,
      device.name+' toolbar must not overlap: '+JSON.stringify({back,title}));
    const main=await rect('.filter-section'),row=await rect('.selected-filter');
    assert.ok(Math.abs(main.x)<1&&Math.abs(main.width-w)<1,device.name+' full width filter section');
    assert.ok(Math.abs(row.x)<1&&Math.abs(row.width-w)<1,device.name+' full width filter row');
    assert.equal(row.radius,0,'No desktop card inside another card');
    assert.ok(row.font>=30&&row.height>=145,device.name+' oversized filter row');
    assert.ok((await rect('#selected-filter-title')).font>=45,device.name+' enlarged primary filter');
    assert.ok((await rect('#selected-filter-category')).font>=33,device.name+' enlarged category');
    assert.ok((await rect('.panel .slider')).font>=37,device.name+' enlarged slider label');
    assert.ok((await rect('.panel .slider output')).font>=39,device.name+' enlarged numeric value');
    assert.ok((await rect('.panel .check')).font>=34,device.name+' enlarged manual control');
    assert.ok((await rect('.panel .advanced>summary')).font>=37,device.name+' enlarged advanced rows');
    assert.equal(await page.locator('#filters').inputValue(),'spider');
    assert.equal(await page.locator('#intensity').inputValue(),'100');
    assert.equal(await page.locator('#spider-speed').inputValue(),'100');
    assert.equal(await page.locator('#creature-count').inputValue(),'1');
    assert.equal(await page.locator('#spider-size').inputValue(),'175');
    await page.locator('#spider-speed').evaluate(el=>{el.value='175';el.dispatchEvent(new Event('input',{bubbles:true}));});
    await page.locator('#spider-size').evaluate(el=>{el.value='140';el.dispatchEvent(new Event('input',{bubbles:true}));});
    assert.equal(await page.locator('#spider-speed-value').textContent(),'175 %');
    assert.equal(await page.locator('#spider-size-value').textContent(),'140 %');
    await page.locator('#creature-count').evaluate(el=>{
      el.value='5';el.dispatchEvent(new Event('input',{bubbles:true}));
    });
    assert.equal(await page.locator('#creature-count-value').textContent(),'5');

    const button=await rect('#settings-done');
    assert.ok(Math.abs(button.x)<1&&Math.abs(button.width-w)<1,device.name+' footer edge to edge');
    assert.ok(button.height>=95&&button.font>=36,device.name+' oversized fixed footer');
    assert.ok((await rect('.panel h2')).font>=39,'Readably oversized section heading');
    const overflow=await page.locator('#panel').evaluate(el=>el.scrollWidth-el.clientWidth);
    assert.ok(overflow<=1,device.name+' NO horizontal overflow: '+overflow);
    await page.screenshot({path:'facechanger-pages/test-results/'+device.name+'-ajustes.png'});

    await page.locator('#filter-picker-open').click();
    const picker=page.locator('#filter-picker');
    await picker.waitFor({state:'visible'});
    await page.waitForTimeout(230);
    const pickerBack=await rect('.picker-back'),pickerTitle=await rect('.picker-top h2');
    assert.ok(pickerBack.x+pickerBack.width<=pickerTitle.x+1&&pickerTitle.x+pickerTitle.width<=w+1,
      device.name+' filter toolbar must not overlap');
    const list=await rect('.filter-option');
    assert.ok(Math.abs(list.x)<1&&Math.abs(list.width-w)<1,device.name+' full width filter list');
    assert.ok(list.height>=98&&list.font>=39,device.name+' oversized filter choices');
    assert.equal(await page.locator('.filter-option').count(),59);
    assert.ok((await rect('.picker-search-label')).font>=34,device.name+' search instructions');
    assert.ok((await rect('#filter-search')).font>=35,device.name+' search entry');
    assert.ok((await rect('.picker-group h3')).font>=36,device.name+' category heading');
    assert.equal(await page.locator('.filter-option').first().getAttribute('data-filter-id'),'spider');
    assert.equal(await page.locator('#makeup-layer').count(),1);
    assert.equal(await page.locator('button[data-filter-id="makeup-green"]').count(),1);
    const pickerOverflow=await picker.evaluate(el=>el.scrollWidth-el.clientWidth);
    assert.ok(pickerOverflow<=1,device.name+' no horizontal picker overflow');
    await page.screenshot({path:'facechanger-pages/test-results/'+device.name+'-filtros.png'});
    await page.locator('button[data-filter-id="cockroach"]').scrollIntoViewIfNeeded();
    assert.ok(await page.locator('button[data-filter-id="cockroach"]').isVisible());
    await page.locator('button[data-filter-id="wasp"]').scrollIntoViewIfNeeded();
    assert.ok(await page.locator('button[data-filter-id="wasp"]').isVisible());
    await page.locator('button[data-filter-id="trend-squash"]').scrollIntoViewIfNeeded();
    assert.ok(await page.locator('button[data-filter-id="trend-squash"]').isVisible(),
      device.name+' new TikTok-inspired filters reachable');
    await page.locator('#filter-search').fill('carita de bebe');
    assert.equal(await page.locator('.filter-option').count(),1);
    await page.locator('button[data-filter-id="trend-baby"]').click();
    assert.equal(await page.locator('#filters').inputValue(),'trend-baby');
    assert.equal(await page.locator('#selected-filter-category').textContent(),'Inspirados en tendencias');
    await page.locator('#filter-picker-open').click();
    await picker.waitFor({state:'visible'});
    await page.locator('#filter-search').fill('');
    await page.locator('button[data-filter-id="face-alien"]').scrollIntoViewIfNeeded();
    assert.ok(await page.locator('button[data-filter-id="face-alien"]').isVisible(),
      device.name+' face-alien reachable after adding more filters');
    await page.locator('button[data-filter-id="normal"]').scrollIntoViewIfNeeded();
    await page.locator('#filter-search').fill('extrañeza sutil');
    assert.equal(await page.locator('.filter-option').count(),1);
    await page.locator('button[data-filter-id="uncanny-droop"]').click();
    assert.equal(await page.locator('#intensity').inputValue(),'40','Combinado empieza al 40 %');
    await page.locator('#filter-picker-open').click();
    await picker.waitFor({state:'visible'});
    await page.locator('#filter-search').fill('ojos grandes');
    await page.locator('button[data-filter-id="eyes-big"]').click();
    assert.equal(await page.locator('#intensity').inputValue(),'75','Ojos grandes se vuelve a seleccionar al 75 %');
    await page.locator('#filter-picker-open').click();
    await picker.waitFor({state:'visible'});
    await page.locator('#filter-search').fill('hamster');
    assert.equal(await page.locator('.filter-option').count(),1);
    await page.getByRole('button',{name:'Mejillas de hámster'}).click();
    await picker.waitFor({state:'hidden'});
    assert.equal(await page.locator('#filters').inputValue(),'cheeks-hamster');
    await page.getByRole('button',{name:'Ver mi reflejo'}).click();
    await page.locator('#panel').waitFor({state:'hidden'});
    assert.equal(await page.locator('#panel').getAttribute('inert'),'');
    const quick=await rect('#quick-reset');
    assert.ok(quick.width>=44&&quick.height>=42,device.name+' reset táctil discreto');
    await page.getByRole('button',{name:'Restablecer los efectos'}).click();
    assert.equal(await page.locator('#filters').inputValue(),'normal');
    assert.equal(await page.locator('#spider-speed').inputValue(),'100');
    assert.equal(await page.locator('#creature-count').inputValue(),'1');
    assert.equal(await page.locator('#spider-size').inputValue(),'175');
    assert.deepEqual(errors,[],device.name+' JS errors');
    console.log('Interfaz Android texto grande y menú de tres líneas correcta:',device.name);
    await context.close();
  }
} finally {await browser.close()}
