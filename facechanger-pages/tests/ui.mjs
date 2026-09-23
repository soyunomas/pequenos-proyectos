import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const targets=[
  {name:'Móvil pequeño',viewport:{width:320,height:640},isMobile:true,hasTouch:true,deviceScaleFactor:2,colorScheme:'light'},
  {name:'Móvil Android',viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:3,colorScheme:'light'},
  {name:'Escritorio',viewport:{width:1440,height:900},isMobile:false,hasTouch:false,deviceScaleFactor:1,colorScheme:'light'},
  {name:'Móvil Android oscuro',viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:3,colorScheme:'dark'}
];
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
try {
  for(const target of targets){
    const context=await browser.newContext({...target});
    const page=await context.newPage(),errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto('http://127.0.0.1:8765/',{waitUntil:'networkidle'});
    assert.equal(await page.title(),'FaceChanger · espejo táctil');
    const canvas=page.locator('#mirror'),before=await canvas.boundingBox();
    assert.ok(before && before.width>=target.viewport.width-2&&before.height>=target.viewport.height-2,target.name+' camera full screen');
    await page.getByRole('button',{name:'Abrir ajustes'}).click();
    const panel=page.locator('#panel');
    await panel.waitFor({state:'visible'});
    await page.waitForTimeout(210);
    assert.equal(await panel.getAttribute('inert'),null);
    const panelBox=await panel.boundingBox();
    assert.ok(panelBox&&Math.abs(panelBox.width-target.viewport.width)<2&&
      Math.abs(panelBox.height-target.viewport.height)<2,target.name+' full-screen panel');
    const css=async locator=>locator.evaluate(el=>{
      const s=getComputedStyle(el);
      return {font:parseFloat(s.fontSize),height:el.getBoundingClientRect().height,
        background:s.backgroundColor,color:s.color,radius:parseFloat(s.borderTopLeftRadius)}
    });
    const lead=await css(page.locator('.panel-lead'));
    assert.ok(lead.font>=30,target.name+' Material headline');
    const surface=await css(panel);
    assert.equal(surface.background,target.colorScheme==='dark'?'rgb(21, 19, 25)':'rgb(250, 248, 255)');
    const card=await css(page.locator('.filter-section'));
    assert.ok(card.radius>=24,target.name+' Material card shape');
    const selected=await css(page.locator('.selected-filter'));
    assert.ok(selected.radius>=22&&selected.height>=140,target.name+' Material tonal choice card');
    assert.ok((await css(page.locator('.selected-filter-title'))).font>=29,target.name+' legible preset');
    assert.equal(await page.locator('#filters').inputValue(),'eyes-big');
    assert.equal(await page.locator('#filters option').count(),32);
    assert.ok((await css(page.locator('#settings-done'))).height>=64,target.name+' Material CTA');

    await page.getByRole('button',{name:/FILTRO SELECCIONADO/}).click();
    const picker=page.locator('#filter-picker');
    await picker.waitFor({state:'visible'});
    await page.waitForTimeout(210);
    const pickerBox=await picker.boundingBox();
    assert.ok(pickerBox&&Math.abs(pickerBox.width-target.viewport.width)<2&&
      Math.abs(pickerBox.height-target.viewport.height)<2,target.name+' full-screen filter picker');
    assert.equal(await panel.getAttribute('inert'),'');
    assert.equal(await page.locator('.filter-option').count(),32);
    const row=await css(page.locator('.filter-option').first());
    assert.ok(row.font>=21&&row.height>=75&&row.radius>=20,target.name+' Material touch rows');
    await page.locator('#filter-search').fill('hamster');
    assert.equal(await page.locator('.filter-option').count(),1);
    await page.getByRole('button',{name:'Mejillas de hámster'}).click();
    await picker.waitFor({state:'hidden'});
    assert.equal(await page.locator('#filters').inputValue(),'cheeks-hamster');
    assert.equal(await panel.getAttribute('inert'),null);
    await page.getByRole('button',{name:/FILTRO SELECCIONADO/}).click();
    await picker.waitFor({state:'visible'});
    await page.keyboard.press('Escape');
    await picker.waitFor({state:'hidden'});
    assert.ok(await panel.isVisible(),target.name+' back stack');
    await page.locator('details.advanced').first().locator('summary').click();
    await page.locator('#extra-filter').selectOption('eyes-big');
    await page.getByRole('button',{name:'Añadir a la mezcla'}).click();
    assert.ok(await page.locator('.effect-title').getByText('Ojos grandes').isVisible());
    const during=await canvas.boundingBox();
    assert.ok(Math.abs(during.width-before.width)<2,target.name+' no camera layout shift');
    await page.getByRole('button',{name:'Ver mi reflejo'}).click();
    await panel.waitFor({state:'hidden'});
    assert.equal(await panel.getAttribute('inert'),'');
    assert.deepEqual(errors,[],target.name+' JS exceptions');
    console.log('Material 3 validado:',target.name);
    await context.close();
  }
}finally{await browser.close()}
