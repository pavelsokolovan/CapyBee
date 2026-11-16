import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
export async function checkExpedition({page,game,requests}){
 const dir=fileURLToPath(new URL('../../../docs/evidence/30.5/',import.meta.url));await mkdir(dir,{recursive:true});
 const encounter=game.locator('.game-encounter');assert.equal(await encounter.count(),1);
 assert.equal(await encounter.locator('.game-encounter-backdrop svg').count(),1);
 const text=await encounter.innerText(),choices=await encounter.locator('fieldset button').allTextContents(),before=requests.length;
 const viewport=page.viewportSize();await page.setViewportSize({width:360,height:1500});await encounter.evaluate(n=>n.scrollIntoView({block:'center'}));await encounter.screenshot({path:dir+'illustrated-mobile.png'});
 const toggle=game.getByRole('button',{name:'Text mode',exact:true});await toggle.focus();await page.keyboard.press('Enter');
 assert.equal(await toggle.getAttribute('aria-pressed'),'true');assert.equal(await toggle.evaluate(n=>n===document.activeElement),true);
 assert.equal(await game.locator('.game-board .game-hex-map').count(),0);
 assert.equal(await encounter.locator('.game-encounter-backdrop').isVisible(),false);
 assert.equal(await encounter.innerText(),text);assert.deepEqual(await encounter.locator('fieldset button').allTextContents(),choices);assert.equal(requests.length,before);
 await encounter.evaluate(n=>n.scrollIntoView({block:'center'}));await encounter.screenshot({path:dir+'text-mobile.png'});
 await game.evaluate(n=>n.style.zoom='2');assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));await game.evaluate(n=>n.style.zoom='1');
 await toggle.click();await page.emulateMedia({forcedColors:'active',reducedMotion:'reduce'});assert.equal(await encounter.locator('.game-encounter-backdrop').isVisible(),false);assert.equal(await encounter.innerText(),text);
 await encounter.evaluate(n=>n.scrollIntoView({block:'center'}));await encounter.screenshot({path:dir+'forced-colors.png'});
 await page.emulateMedia({forcedColors:'none',reducedMotion:'no-preference'});await page.setViewportSize(viewport);
 console.log('PASS: illustrated expedition, keyboard text mode, unchanged choices, no mutations, zoom and forced colors');
}
