import type {ReactNode} from 'react';
import type {GameView} from '../../game/gameApi';
import {GameBackdrop} from './art/GameArt';
import './gameEncounter.css';

export const encounterCopy={en:{text:'Text mode'},pl:{text:'Tryb tekstowy'}} as const;
/** Artwork comes only from the accepted public view; children retain their identity across modes. */
export function GameEncounter({view,children}:{view:GameView;children:ReactNode}) {
 const presentation='presentation' in view?view.presentation:null;
 return <div className={presentation?'game-art game-encounter':'game-encounter'}>
  {presentation?<div className="game-encounter-backdrop" aria-hidden="true"><GameBackdrop assetId={presentation.backdropAssetId} decorative/></div>:null}
  <div className="game-encounter-content">{children}</div>
 </div>;
}
