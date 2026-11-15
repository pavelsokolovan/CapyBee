import {expect,it} from 'vitest';
import {decodeCompletion} from './gameCompletion';
const fixture={runId:'run',revision:3,completedAt:'2026-11-15T23:59:59Z',suggestionKey:'rest',suggestionVersion:1,mission:null,missionAccepted:false,cellAdded:false};
it('accepts reviewed public completion without prose or metrics',()=>{expect(decodeCompletion(fixture)).toEqual(fixture);});
it('rejects malformed timestamps, versions and Mission catalog fields',()=>{
 for(const change of [{completedAt:'bad'},{suggestionVersion:0},{revision:-1},{cellAdded:'yes'},{mission:{id:'m',code:'code',title:7,timeHint:'Short'}}])expect(()=>decodeCompletion({...fixture,...change})).toThrow();
});
