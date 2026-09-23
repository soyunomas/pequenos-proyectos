import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const targets = [
  { name:'Móvil pequeño', viewport:{width:320,height:640}, isMobile:true, hasTouch:true, deviceScaleFactor:2 },
  { name:'Móvil Android', viewport:{width:390,height:844}, isMobile:true, hasTouch:true, deviceScaleFactor:3 },
  { name:'Escritorio', viewport:{width:1440,height:900}, isMobile:false, hasTouch:false, deviceScaleFactor:1 },
];
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
try{
  for(const target of targets){
    const context=await browser.newContext({...target});
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto('http://127.0.0.1:8765/',{waitUntil:'networkidle'});
    assert.equal(await page.title(),'FaceChanger · espejo táctil');
    const canvas=page.locator('#mirror'),before=await canvas.boundingBox();
    assert.ok(before&&before.width>=target.viewport.width-2&&before.height>=target.viewport.height-2);
    assert.equal(await page.locator('#view-zoom').count(),0);

    await page.getByRole('button',{name:'Abrir ajustes'}).click();
    const panel=page.locator('#panel');
    await panel.waitFor({state:'visible',timeout:3000});
    await page.waitForTimeout(170);
    const box=await panel.boundingBox();
    assert.ok(box&&Math.abs(box.x)<2&&Math.abs(box.y)<2&&
      Math.abs(box.width-target.viewport.width)<2&&Math.abs(box.height-target.viewport.height)<2,
      target.name+' pantalla de ajustes completa: '+JSON.stringify(box));
    assert.equal(await panel.getAttribute('inert'),null);
    const font=async locator=>Number.parseFloat(await locator.evaluate(el=>getComputedStyle(el).fontSize));
    assert.ok(await font(page.locator('.panel-lead'))>=28,target.name+' título legible');
    assert.ok(await font(page.locator('#selected-filter-title'))>=28,target.name+' nombre de filtro legible');
    assert.ok(await font(page.locator('.panel h2').first())>=23,target.name+' sección legible');
    assert.ok(await font(page.locator('.panel .slider').first())>=22,target.name+' intensidad legible');
    assert.ok((await page.locator('#filter-picker-open').boundingBox()).height>=140,target.name+' tarjeta grande');
    assert.equal(await page.locator('#filters').inputValue(),'eyes-big');
    assert.equal(await page.locator('#filters option').count(),32);

    await page.getByRole('button',{name:/FILTRO SELECCIONADO/}).click();
    const picker=page.locator('#filter-picker');
    await picker.waitFor({state:'visible',timeout:3000});
    await page.waitForTimeout(170);
    const pb=await picker.boundingBox();
    assert.ok(pb&&Math.abs(pb.width-target.viewport.width)<2&&
      Math.abs(pb.height-target.viewport.height)<2,target.name+' catálogo pantalla completa');
    assert.equal(await panel.getAttribute('inert'),'');
    assert.ok(await font(page.locator('.filter-option').first())>=22,target.name+' opciones de filtro legibles');
    assert.ok((await page.locator('.filter-option').first().boundingBox()).height>=72,
      target.name+' objetivo táctil grande');
    assert.equal(await page.locator('.filter-option').count(),32);
    const search=page.locator('#filter-search');
    await search.fill('hamster');
    assert.equal(await page.locator('.filter-option').count(),1,'Búsqueda tolera acentos');
    assert.ok(await page.getByRole('button',{name:'Mejillas de hámster'}).isVisible());
    await search.fill('nariz');
    assert.ok(await page.getByRole('button',{name:'Nariz grande'}).isVisible());
    await page.getByRole('button',{name:'Nariz grande'}).click();
    await picker.waitFor({state:'hidden',timeout:3000});
    assert.equal(await page.locator('#filters').inputValue(),'nose-big');
    assert.equal(await page.locator('#selected-filter-title').textContent(),'Nariz grande');
    assert.equal(await panel.getAttribute('inert'),null);

    await page.getByRole('button',{name:/FILTRO SELECCIONADO/}).click();
    await picker.waitFor({state:'visible'});
    await page.keyboard.press('Escape');
    await picker.waitFor({state:'hidden',timeout:3000});
    assert.ok(await panel.isVisible(),target.name+' escape returns to settings');

    await page.locator('details.advanced').first().locator('summary').click();
    await page.locator('#extra-filter').selectOption('mouth-smile');
    await page.getByRole('button',{name:'Añadir a la mezcla'}).click();
    assert.ok(await page.locator('.effect-title').getByText('Sonrisa gigante').isVisible());
    await page.locator('details.advanced').nth(1).locator('summary').click();
    await page.getByRole('button',{name:'Guardar filtro'}).scrollIntoViewIfNeeded();
    assert.ok(await page.getByRole('button',{name:'Guardar filtro'}).isVisible());

    const during=await canvas.boundingBox();
    assert.ok(Math.abs(during.width-before.width)<2,'No resize of mirror');
    await page.getByRole('button',{name:'Ver mi reflejo'}).click();
    await panel.waitFor({state:'hidden',timeout:3000});
    assert.equal(await panel.getAttribute('inert'),'');
    assert.deepEqual(errors,[],target.name+' errors');
    console.log('Correcto:',target.name);
    await context.close();
  }
}finally{await browser.close()}
