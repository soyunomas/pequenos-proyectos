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
    const w=device.viewport.width,h=device.viewport.height;
    const rect=async selector=>page.locator(selector).first().evaluate(el=>{
      const r=el.getBoundingClientRect(),s=getComputedStyle(el);
      return {x:r.x,y:r.y,width:r.width,height:r.height,font:parseFloat(s.fontSize),radius:parseFloat(s.borderTopLeftRadius),maxWidth:s.maxWidth,background:s.backgroundColor};
    });
    const camera=await rect('#mirror');
    assert.ok(camera.width>=w-1&&camera.height>=h-1,device.name+' full-screen camera');
    await page.getByRole('button',{name:'Abrir ajustes'}).click();
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
    assert.ok(row.font>=19&&row.height>=88,device.name+' comfortably tappable filter');
    assert.ok((await rect('#selected-filter-title')).font>=26,device.name+' readable primary filter');
    assert.equal(await page.locator('#filters').inputValue(),'eyes-big');
    const button=await rect('#settings-done');
    assert.ok(Math.abs(button.x)<1&&Math.abs(button.width-w)<1,device.name+' footer edge to edge');
    assert.ok(button.height>=68);
    assert.ok((await rect('.panel h2')).font>=19,'Readable section heading');
    const overflow=await page.locator('#panel').evaluate(el=>el.scrollWidth-el.clientWidth);
    assert.ok(overflow<=1,device.name+' NO horizontal overflow: '+overflow);
    await page.screenshot({path:'facechanger-pages/test-results/'+device.name+'-ajustes.png'});

    await page.getByRole('button',{name:/Ojos grandes/}).click();
    const picker=page.locator('#filter-picker');
    await picker.waitFor({state:'visible'});
    await page.waitForTimeout(230);
    const pickerBack=await rect('.picker-back'),pickerTitle=await rect('.picker-top h2');
    assert.ok(pickerBack.x+pickerBack.width<=pickerTitle.x+1&&pickerTitle.x+pickerTitle.width<=w+1,
      device.name+' filter toolbar must not overlap');
    const list=await rect('.filter-option');
    assert.ok(Math.abs(list.x)<1&&Math.abs(list.width-w)<1,device.name+' full width filter list');
    assert.ok(list.height>=64&&list.font>=19);
    assert.equal(await page.locator('.filter-option').count(),32);
    await page.screenshot({path:'facechanger-pages/test-results/'+device.name+'-filtros.png'});
    await page.locator('#filter-search').fill('hamster');
    assert.equal(await page.locator('.filter-option').count(),1);
    await page.getByRole('button',{name:'Mejillas de hámster'}).click();
    await picker.waitFor({state:'hidden'});
    assert.equal(await page.locator('#filters').inputValue(),'cheeks-hamster');
    await page.getByRole('button',{name:'Ver mi reflejo'}).click();
    await page.locator('#panel').waitFor({state:'hidden'});
    assert.equal(await page.locator('#panel').getAttribute('inert'),'');
    assert.deepEqual(errors,[],device.name+' JS errors');
    console.log('Interfaz Android borde a borde correcta:',device.name);
    await context.close();
  }
} finally {await browser.close()}
