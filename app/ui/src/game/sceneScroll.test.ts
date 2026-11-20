import {expect,it,vi} from 'vitest';
import {focusScene,resetSceneScroll} from './sceneScroll';

function scrolled() {
  const node=document.createElement('div');
  node.scrollTop=80;
  node.scrollLeft=25;
  return node;
}

it('resets both axes instantly and tolerates a missing scroller',()=>{
  const node=scrolled(),scroll=vi.spyOn(node,'scrollTo');
  resetSceneScroll(node);
  expect([node.scrollTop,node.scrollLeft]).toEqual([0,0]);
  expect(scroll).toHaveBeenCalledExactlyOnceWith({top:0,left:0,behavior:'instant'});
  expect(()=>resetSceneScroll(null)).not.toThrow();
});

it('resets scroll before focusing once and accepts a missing target or scroller',()=>{
  const node=scrolled(),target=document.createElement('h4');
  const focus=vi.spyOn(target,'focus').mockImplementation(()=>{
    expect([node.scrollTop,node.scrollLeft]).toEqual([0,0]);
  });
  focusScene(target,node);
  expect(focus).toHaveBeenCalledExactlyOnceWith({preventScroll:true});
  focus.mockClear();
  focusScene(target,null);
  expect(focus).toHaveBeenCalledExactlyOnceWith({preventScroll:true});
  const other=scrolled();
  focusScene(null,other);
  expect([other.scrollTop,other.scrollLeft]).toEqual([0,0]);
  expect(()=>focusScene(null,null)).not.toThrow();
});
