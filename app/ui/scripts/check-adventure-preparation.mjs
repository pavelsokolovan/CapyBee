import {chromium} from 'playwright';
import assert from 'node:assert/strict';
const base=process.argv[2];
if(!base || !/^http:\/\/127\.0\.0\.1:\d+$/.test(base))throw new Error('Use the local preparation HTTP fixture host');
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE??'/usr/bin/chromium',args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:360,height:800},extraHTTPHeaders:{'X-Preparation-Fixture':'fixture'}});
const page=await context.newPage();
const requests=[];
page.on('request',request=>{if(request.method()==='POST' && request.url().includes('/api/game/'))requests.push({url:request.url(),body:request.postDataJSON()});});
const errors=[],logs=[];page.on('pageerror',()=>errors.push('browser exception'));page.on('console',entry=>logs.push(entry.text()));
try {
 await page.goto(base);await page.getByRole('button',{name:'Play',exact:true}).click();
 await page.getByRole('button',{name:/Adventures/}).click();
 await page.getByRole('button',{name:/New AI-created adventure/}).click();
 await page.getByRole('button',{name:'Next',exact:true}).click();
 await page.getByRole('button',{name:'Next',exact:true}).click();
 await page.getByRole('button',{name:'Next',exact:true}).click();
 const privateIdea='A fictional woodland journey';
 await page.locator('textarea').fill(privateIdea);
 await page.getByRole('button',{name:'Review',exact:true}).click();
 assert.equal(await page.locator('textarea').count(),0);
 assert.equal((await page.locator('[role=dialog]').innerText()).includes(privateIdea),false);
 await page.getByText('Idea added',{exact:true}).waitFor();
 assert.equal(requests.length,0);
 // The fixture already has a current run: replacement must be explicit.
 const replace=page.getByRole('checkbox',{name:/pause my current/});if(await replace.count())await replace.check();
 const before=await page.evaluate(()=>({local:JSON.stringify(localStorage),session:JSON.stringify(sessionStorage),history:JSON.stringify(history.state),url:location.href}));
 assert.equal(JSON.stringify(before).includes(privateIdea),false);
 await page.getByRole('button',{name:'Start adventure',exact:true}).focus();await page.keyboard.press('Tab');
 assert.equal(await page.getByRole('button',{name:'Back to Play',exact:true}).evaluate(node=>node===document.activeElement),true);
 await page.keyboard.press('Shift+Tab');
 assert.equal(await page.getByRole('button',{name:'Start adventure',exact:true}).evaluate(node=>node===document.activeElement),true);
 await page.screenshot({path:'/tmp/282-preparation-360.png'});
 const sizes=await page.evaluate(()=>({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}));
 assert.ok(sizes.scroll<=sizes.width,'mobile page overflows');
 await page.getByRole('button',{name:'Start adventure',exact:true}).click();
 await page.getByText('Your adventure is saved. You can leave and come back.').waitFor();
 assert.equal(requests.length,1);assert.equal(requests[0].body.suggestion,privateIdea);
 const stores=await page.evaluate(async()=>{
  const databases=await indexedDB.databases();const values=[];
  for(const item of databases){if(!item.name)continue;const db=await new Promise((resolve,reject)=>{const r=indexedDB.open(item.name);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
   for(const name of db.objectStoreNames){values.push(await new Promise((resolve,reject)=>{const r=db.transaction(name).objectStore(name).getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);}));}db.close();}
  return JSON.stringify({local:localStorage,session:sessionStorage,values,history:history.state});
 });assert.equal(stores.includes(privateIdea),false);
 const cacheLeak=await page.evaluate(async idea=>{for(const name of await caches.keys()){const cache=await caches.open(name);for(const key of await cache.keys()){const response=await cache.match(key);if(response&&(await response.text()).includes(idea))return true;}}return false;},privateIdea);
 assert.equal(cacheLeak,false);assert.equal(logs.some(line=>line.includes(privateIdea)),false);
 await page.reload();await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
 await page.getByRole('button',{name:'Continue',exact:true}).click();await page.getByText('Your adventure is saved. You can leave and come back.').waitFor();
 assert.equal(requests.length,1,'reload submitted another paid attempt');
 await page.setViewportSize({width:1280,height:900});await page.screenshot({path:'/tmp/282-preparation-desktop.png'});
 await page.goBack();await page.locator('[role=dialog]').waitFor({state:'detached'});
 await page.waitForFunction(()=>document.activeElement?.textContent?.includes('Adventures'));
 assert.equal(requests.length,1);assert.deepEqual(errors,[]);
 console.log('PASS: Spring-served preparation, private review, 360px/desktop, explicit start, reload/continue, browser Back and storage');
}finally{await browser.close();}
