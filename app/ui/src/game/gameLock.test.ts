import 'fake-indexeddb/auto';
import {it,expect,vi} from 'vitest';
import {withGameLock} from './gameLock';
it('transactional leases serialize competing tabs and release on failure',async()=>{
 Object.defineProperty(navigator,'locks',{configurable:true,value:undefined});
 let release!:()=>void;const active=withGameLock('owner','run',async()=>{await new Promise<void>(resolve=>{release=resolve;});return 1;});
 await vi.waitFor(()=>expect(release).toBeTypeOf('function'));
 expect(await withGameLock('owner','run',async()=>2)).toBeUndefined();
 release();expect(await active).toBe(1);expect(await withGameLock('owner','run',async()=>3)).toBe(3);
 await expect(withGameLock('owner','run',async()=>{throw new Error('interrupted');})).rejects.toThrow('interrupted');expect(await withGameLock('owner','run',async()=>4)).toBe(4);
});
it('uses Web Locks without a competing unsafe localStorage lease',async()=>{
 const request=vi.fn(async(_key,options,callback)=>{expect(options).toEqual({ifAvailable:true});return callback(null);});
 Object.defineProperty(navigator,'locks',{configurable:true,value:{request}});
 const work=vi.fn();expect(await withGameLock('owner','run',work)).toBeUndefined();expect(work).not.toHaveBeenCalled();
 Object.defineProperty(navigator,'locks',{configurable:true,value:undefined});
});
it('reclaims an expired crashed-tab lease transactionally',async()=>{
 Object.defineProperty(navigator,'locks',{configurable:true,value:undefined});
 const db=await new Promise<IDBDatabase>((resolve,reject)=>{const request=indexedDB.open('capybee-game-coordination',1);request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});
 await new Promise<void>(resolve=>{const transaction=db.transaction('leases','readwrite');transaction.objectStore('leases').put({token:'crashed',until:Date.now()-1},'game:owner:expired');transaction.oncomplete=()=>resolve();});
 expect(await withGameLock('owner','expired',async()=> 'recovered')).toBe('recovered');db.close();
});
