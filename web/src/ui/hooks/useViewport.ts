import { useEffect, useState } from 'react';

export type WindowWidthClass = 'compact' | 'medium' | 'expanded';

export interface AdaptiveViewport {
  width: number;
  height: number;
  widthClass: WindowWidthClass;
  isCompact: boolean;
  isMedium: boolean;
  isExpanded: boolean;
  isTwoPane: boolean;
  gridCols: 1 | 2 | 3;
}

function compute(width: number, height: number): AdaptiveViewport {
  const widthClass: WindowWidthClass =
    width >= 840 ? 'expanded' : width >= 600 ? 'medium' : 'compact';
  return {
    width,
    height,
    widthClass,
    isCompact: widthClass === 'compact',
    isMedium: widthClass === 'medium',
    isExpanded: widthClass === 'expanded',
    isTwoPane: width >= 840,
    gridCols: width >= 1024 ? 3 : width >= 600 ? 2 : 1,
  };
}

export function useViewport(): AdaptiveViewport {
  const [vp, setVp] = useState<AdaptiveViewport>(() =>
    typeof window !== 'undefined'
      ? compute(window.innerWidth, window.innerHeight)
      : compute(1280, 800),
  );

  useEffect(() => {
    const onResize = () => setVp(compute(window.innerWidth, window.innerHeight));
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  return vp;
}
