import {checkCompletion} from './game-completion.mjs';
import {checkHexMap} from './game-hex-map.mjs';
import {checkRecovery} from './game-recovery.mjs';
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
 const privateIdea='setup-private-'+crypto.randomUUID();
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
 // Drive the shared renderer through a dynamic ending via the actual controller API.
 const gameplay=page.locator('.game-run');
 for(let step=0;step<12;step++){
  if(await gameplay.getByRole('button',{name:'Start another adventure',exact:true}).count())break;
  const beforeCommands=requests.length;
  await gameplay.locator('fieldset button:enabled').first().click();
  await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
  assert.ok(requests.length>beforeCommands);
 }
 await gameplay.getByRole('button',{name:'Start another adventure',exact:true}).click();
 // A separate authored fixture exercises explicit Continue in Polish.
 await page.getByRole('button',{name:/Fixture — Minimal/}).click();
 while(await page.getByRole('button',{name:'Next',exact:true}).count())await page.getByRole('button',{name:'Next',exact:true}).click();
 await page.getByRole('button',{name:'Review',exact:true}).click();
 await page.getByRole('button',{name:'Start adventure',exact:true}).click();
 await gameplay.getByRole('button',{name:'Wsiadam do łódki',exact:true}).click();
 await gameplay.getByRole('button',{name:'Continue',exact:true}).click();
 await gameplay.getByRole('button',{name:'Start another adventure',exact:true}).waitFor();
 assert.equal(await gameplay.getAttribute('lang'),'pl');
 // The dungeon exposes informational places and server-authorized movement.
 await gameplay.getByRole('button',{name:'Start another adventure',exact:true}).click();
 await page.getByRole('button',{name:/Fixture — Text Dungeon/}).click();
 await page.getByRole('button',{name:'Review',exact:true}).click();
 await page.getByRole('button',{name:'Start adventure',exact:true}).click();
 await gameplay.getByText('Places',{exact:true}).click();
 await gameplay.getByRole('button',{name:'Go to: Corridor',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
 // Movement changes the place, not the scene: use the advertised choices.
 for(const label of ['Step into the corridor','Go left','Return to the corridor','Go right','Climb the stairs']){
  await gameplay.getByRole('button',{name:label,exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
 }
 await gameplay.getByRole('button',{name:'Start another adventure',exact:true}).waitFor();
 await gameplay.getByRole('button',{name:'Start another adventure',exact:true}).click();
 await page.getByRole('button',{name:/Fixture — Minimal/}).click();
 while(await page.getByRole('button',{name:'Next',exact:true}).count())await page.getByRole('button',{name:'Next',exact:true}).click();
 await page.getByRole('button',{name:'Review',exact:true}).click();await page.getByRole('button',{name:'Start adventure',exact:true}).click();
 const endButton=gameplay.getByRole('button',{name:'End adventure',exact:true});await endButton.click();
 await gameplay.locator('dialog').getByRole('button',{name:'Cancel',exact:true}).click();
 assert.equal(await endButton.evaluate(node=>node===document.activeElement),true);
 await endButton.click();await gameplay.locator('dialog').getByRole('button',{name:'End adventure',exact:true}).click();
 await gameplay.getByText('Abandoned',{exact:true}).waitFor();
 assert.equal(await gameplay.locator('h3').evaluate(node=>node===document.activeElement),true);
 await page.emulateMedia({reducedMotion:'reduce'});
 await page.evaluate(()=>document.querySelector('.game-run').style.zoom='2');
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));
 await page.evaluate(()=>document.querySelector('.game-run').style.zoom='1');
 const finalRequests=requests.length;
 await page.setViewportSize({width:1280,height:900});await page.screenshot({path:'/tmp/282-preparation-desktop.png'});
 await page.goBack();await page.locator('[role=dialog]').waitFor({state:'detached'});
 await page.waitForFunction(()=>document.activeElement?.textContent?.includes('Adventures'));
 assert.equal(requests.length,finalRequests);assert.deepEqual(errors,[]);
 await page.getByRole('button',{name:/Adventures/}).click();await page.getByRole('button',{name:'Continue',exact:true}).count().then(async count=>{if(count)await page.getByRole('button',{name:'Continue',exact:true}).click();});
 await checkHexMap({page,context,base,requests});
 await checkCompletion({page,context,base,requests});
 await checkRecovery({page,context,base,requests,logs});
 console.log('PASS: Spring-served preparation, private review, 360px/desktop, explicit start, reload/continue, browser Back and storage');
}catch(error){console.error('Browser acceptance assertion failed; story and request bodies omitted.');console.error('Status:',await page.locator('.game-run [role=status]').textContent({timeout:1000}).catch(()=>null));console.error('Queue metadata:',await page.evaluate(async()=>{const r=indexedDB.open('keyval-store');const db=await new Promise(resolve=>r.onsuccess=()=>resolve(r.result));if(!db.objectStoreNames.contains('keyval'))return [];const values=await new Promise(resolve=>{const r=db.transaction('keyval').objectStore('keyval').getAll();r.onsuccess=()=>resolve(r.result);});db.close();return values.filter(v=>v?.type==='gameCommand').map(v=>({id:v.clientId,poll:v.poll,status:v.status,attempts:v.attempts,notBefore:v.notBefore}));}));throw error;}finally{await browser.close();}
