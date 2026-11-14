/** Cross-tab dispatch coordination. No story or command body enters the lease. */
const DURATION=10000;
let database:Promise<IDBDatabase>|undefined;
function db(){return database??=new Promise<IDBDatabase>((resolve,reject)=>{
 const request=indexedDB.open('capybee-game-coordination',1);
 request.onupgradeneeded=()=>request.result.createObjectStore('leases');
 request.onsuccess=()=>resolve(request.result);request.onerror=()=>{database=undefined;reject(new Error('GAME_COORDINATION_UNAVAILABLE'));};
});}
async function lease(key:string,token:string,release=false):Promise<boolean>{
 const database=await db();return new Promise((resolve,reject)=>{
  const transaction=database.transaction('leases','readwrite'),store=transaction.objectStore('leases');let acquired=false;
  const request=store.get(key);
  request.onsuccess=()=>{const old=request.result as {token:string;until:number}|undefined;
   if(release){if(old?.token===token)store.delete(key);acquired=true;}
   else if(!old||old.until<Date.now()||old.token===token){store.put({token,until:Date.now()+DURATION},key);acquired=true;}
  };
  transaction.oncomplete=()=>resolve(acquired);transaction.onerror=()=>reject(new Error('GAME_COORDINATION_UNAVAILABLE'));transaction.onabort=transaction.onerror;
 });
}
export async function withGameLock<T>(owner:string,run:string,work:()=>Promise<T>):Promise<T|undefined>{
 const key='game:'+owner+':'+run;
 if(navigator.locks)return navigator.locks.request(key,{ifAvailable:true},lock=>lock?work():Promise.resolve(undefined));
 const token=crypto.randomUUID();if(!await lease(key,token))return undefined;
 const heartbeat=setInterval(()=>{void lease(key,token).catch(()=>{});},3000);
 try{return await work();}finally{clearInterval(heartbeat);await lease(key,token,true);}
}
