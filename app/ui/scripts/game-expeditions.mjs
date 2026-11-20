import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const evidence=fileURLToPath(new URL('../../../docs/evidence/37/',import.meta.url));
const focusEvidence=fileURLToPath(new URL('../../../docs/evidence/39/',import.meta.url));
const mobile={width:360,height:640};
const art='.game-encounter-backdrop';
const bubble='.game-encounter-content';
const body='.game-scene-body';
const footer='.game-scene-footer';

async function screenshot(page,name,directory=evidence){
 await mkdir(directory,{recursive:true});
 await page.screenshot({path:directory+name+'.png'});
}

export async function checkSceneFocus(game){
 const state=await game.locator(body).evaluate(node=>{
  const heading=node.querySelector('.game-expedition-location');
  const frame=node.getBoundingClientRect(),caption=heading?.getBoundingClientRect();
  return {
   scrollTop:node.scrollTop,scrollLeft:node.scrollLeft,focused:heading===document.activeElement,
   visible:!!caption&&caption.top>=frame.top&&caption.bottom<=frame.bottom,
   containment:getComputedStyle(node).overscrollBehavior,
  };
 });
 assert.deepEqual(state,{scrollTop:0,scrollLeft:0,focused:true,visible:true,containment:'contain'});
}

export async function checkSceneAdvance({page,game,advance,zoom=false,capture=true}){
 const viewport=page.viewportSize();
 try {
  await page.emulateMedia({reducedMotion:'reduce'});
  // At 200% page zoom a 360 × 640 viewport has half as many CSS pixels.
  await page.setViewportSize(zoom?{width:180,height:320}:{width:360,height:480});
  const offset=await game.locator(body).evaluate(node=>{node.scrollTop=node.scrollHeight;return node.scrollTop;});
  assert.ok(offset>0,'the outgoing scene must scroll to exercise the reset');
  await advance();
  await checkSceneFocus(game);
  if(await game.locator(footer).count()){
   assert.equal(await game.locator(footer).evaluate(node=>getComputedStyle(node).overscrollBehavior),'contain');
  }
  if(capture)await screenshot(page,zoom?'after-advance-200-zoom':'after-advance-small-viewport',focusEvidence);
 }finally{
  await page.setViewportSize(viewport);
  await page.emulateMedia({reducedMotion:'no-preference'});
 }
}

async function atMobile(page,game,check){
 const viewport=page.viewportSize();
 const scroller=game.locator(body),scrollTop=await scroller.evaluate(node=>node.scrollTop);
 try{
  await page.setViewportSize(mobile);
  await scroller.evaluate(node=>{node.scrollTop=0;});
  await check();
 }finally{
  await page.setViewportSize(viewport);
  await scroller.evaluate((node,top)=>{node.scrollTop=top;},scrollTop);
 }
}

async function checkFooter(page,game,count){
 const bar=game.locator(footer);
 const {width,height}=page.viewportSize();
 const frame=await bar.boundingBox();
 assert.ok(frame&&frame.x>=0&&frame.x+frame.width<=width&&frame.y>=0&&frame.y+frame.height<=height+1,
  'the action footer is outside the viewport');
 assert.ok(frame.height<=height*.45+1,'the action footer exceeds 45% of the viewport');
 const buttons=bar.locator('fieldset button');
 assert.equal(await buttons.count(),count);
 assert.ok(await buttons.evaluateAll(nodes=>nodes.every(node=>{
  const rect=node.getBoundingClientRect();
  return rect.width>=44&&rect.height>=48;
 })), 'an expedition action is smaller than its minimum touch target');
 return frame;
}

async function checkBackdrop(game){
 assert.ok(await game.locator(art).evaluate(node=>
  node.getBoundingClientRect().height>=6*parseFloat(getComputedStyle(document.documentElement).fontSize)),
  'the backdrop is shorter than 6rem');
}

export async function checkExpeditionOpening({page,game,capture=false}){
 await atMobile(page,game,async()=>{
  assert.equal(await game.evaluate(node=>node.classList.contains('game-scene-first')),true);
  const frame=await checkFooter(page,game,1);
  await checkBackdrop(game);
  const text=await game.locator(bubble).boundingBox();
  assert.ok(text.y>=0&&text.y+text.height<=frame.y,'the full opening beat must fit above the footer');
  assert.equal(await game.locator(body).evaluate(node=>node.scrollTop),0);
  if(capture)await screenshot(page,'opening-mobile');
  // Shrink the CSS viewport to exercise a change in available height.
  await page.setViewportSize({width:mobile.width,height:560});
  await checkFooter(page,game,1);
  await checkBackdrop(game);
 });
}

export async function captureExpeditionHub({page,game}){
 await atMobile(page,game,async()=>{
  const scroller=game.locator(body);
  assert.equal(await scroller.locator('.game-actions button').count(),6);
  assert.equal(await game.locator(footer).count(),0);
  await screenshot(page,'hub-mobile');
  assert.deepEqual(await scroller.evaluate(node=>{
   const overlay=node.closest('.adventure-overlay');
   const header=node.closest('.adventure-panel').querySelector('header');
   const top=header.getBoundingClientRect().top;
   node.scrollTop=120;
   overlay.scrollTop=120;
   return {bodyOverflow:getComputedStyle(node).overflowY,bodyScrolled:node.scrollTop>0,
    overlayOverflow:getComputedStyle(overlay).overflowY,overlayScroll:overlay.scrollTop,
    headerMoved:header.getBoundingClientRect().top!==top};
  }),{bodyOverflow:'auto',bodyScrolled:true,overlayOverflow:'hidden',overlayScroll:0,headerMoved:false});
 });
}

export async function captureExpeditionLantern({page,game}){
 await atMobile(page,game,async()=>{
  const frame=await checkFooter(page,game,3);
  await checkBackdrop(game);
  await screenshot(page,'lantern-mobile');
  await game.locator(body).evaluate(node=>{node.scrollTop=node.scrollHeight;});
  assert.deepEqual(await game.locator(footer).boundingBox(),frame,'the footer moved when the scene scrolled');

  // At 200% page zoom the CSS viewport contains half as many pixels in each direction.
  await page.setViewportSize({width:mobile.width/2,height:mobile.height/2});
  await checkFooter(page,game,3);
  const overflowing=await game.evaluate(node=>[document.documentElement,node.closest('.adventure-overlay'),
   node.querySelector('.game-scene-body'),node.querySelector('.game-scene-footer')]
   .filter(region=>region.scrollWidth>region.clientWidth)
   .map(region=>({region:region.className||region.tagName,width:region.clientWidth,scrollWidth:region.scrollWidth})));
  assert.deepEqual(overflowing,[],'the expedition overflows horizontally at 200% zoom');
  const lastAction=game.locator(footer+' button').last();
  await lastAction.focus();
  const last=await lastAction.boundingBox();
  const zoomedFooter=await game.locator(footer).boundingBox();
  assert.ok(last.y+last.height<=zoomedFooter.y+zoomedFooter.height+1,'the zoomed footer cannot scroll to its last action');
 });
}

export async function checkExpedition({page,game,requests}){
 const encounter=game.locator('.game-encounter');
 const heading=encounter.getByRole('heading',{level:4});
 const text=await encounter.locator(bubble).innerText();
 const choices=()=>game.locator('.game-actions fieldset button').allTextContents();
 const labels=await choices(),before=requests.length;
 const toggle=game.locator('.game-scene-menu button[aria-pressed]');
 const menu=game.locator('.game-scene-menu > summary');
 const viewport=page.viewportSize();
 const tail=()=>encounter.locator(bubble).evaluate(node=>getComputedStyle(node,'::before').content);
 try{
  await page.setViewportSize({width:360,height:1500});
  assert.equal(await encounter.locator(art+' > svg').count(),1);
  assert.ok(await encounter.locator('.game-encounter-stage').evaluate(stage=>{
   const frame=stage.getBoundingClientRect();
   return stage.children.length>0&&[...stage.children].every(figure=>{
    const bounds=figure.getBoundingClientRect();
    return bounds.height>0&&bounds.top>=frame.top&&bounds.bottom<=frame.bottom;
   });
  }), 'a scene character is clipped by the artwork frame');
  assert.notEqual(await tail(),'none');
  await atMobile(page,game,async()=>{
   await menu.click();await toggle.focus();await page.keyboard.press('Enter');
   assert.equal(await toggle.getAttribute('aria-pressed'),'true');
   assert.equal(await menu.evaluate(node=>node===document.activeElement&&!node.parentElement.open),true);
   assert.equal(await encounter.locator(art).count(),0);
   assert.equal(await tail(),'none');
   assert.equal(await heading.isVisible(),true);
   assert.equal(await encounter.locator(bubble).innerText(),text);
   assert.deepEqual(await choices(),labels);
   await checkFooter(page,game,labels.length);
   await screenshot(page,'text-mode-mobile');

   await menu.click();await toggle.click();
   await page.emulateMedia({forcedColors:'active',reducedMotion:'reduce'});
   assert.equal(await encounter.locator(art+' > svg').isVisible(),false);
   assert.equal(await heading.isVisible(),true);
   assert.equal(await heading.evaluate(node=>getComputedStyle(node).position),'static');
   assert.equal(await tail(),'none');
   assert.equal(await encounter.locator(bubble).innerText(),text);
   assert.deepEqual(await choices(),labels);
   await checkFooter(page,game,labels.length);
   await screenshot(page,'forced-colors-mobile');
  });
  assert.equal(requests.length,before);
 }finally{
  await page.emulateMedia({forcedColors:'none',reducedMotion:'no-preference'});
  await page.setViewportSize(viewport);
 }
 console.log('PASS: one-screen expedition, pinned and inline actions, zoom, keyboard Text mode and forced colors');
}
