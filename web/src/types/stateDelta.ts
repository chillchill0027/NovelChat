export interface SceneDelta {
  location?: string;
  time_of_day?: string;
  weather?: string;
  present?: string[];
  description?: string;
}

export interface GoalUpdate {
  from: string;
  to: string;
}

export interface GoalsDelta {
  complete?: string[];
  add?: string[];
  update?: GoalUpdate[];
  current?: string;
}

export interface InventoryOp {
  op: 'add' | 'remove' | string;
  item: string;
  qty?: number;
}

export interface RelationshipOp {
  name: string;
  stage: string;
}

export interface PlayerDelta {
  fields?: Record<string, string>;
  inventory?: InventoryOp[];
  relationships?: RelationshipOp[];
}

export interface NpcDelta {
  name: string;
  emotion?: string;
  favor_delta?: number;
  trust_delta?: number;
  relationship_stage?: string;
  fields?: Record<string, string>;
  memory?: string;
  inventory?: InventoryOp[];
}

export interface NewNpcDelta {
  name: string;
  identity?: string;
  relationship_to_player?: string;
  gender?: string;
  age?: number;
  personality?: string;
  appearance?: string;
  speech_style?: string;
  fields?: Record<string, string>;
}

export interface DepartedNpc {
  name: string;
  permanent?: boolean;
}

export interface ItemTransferDelta {
  item: string;
  qty?: number;
  from: string;
  to: string;
}

export interface WorldDelta {
  name: string;
  type?: string;
  description?: string;
  keywords?: string[];
}

export interface EventDelta {
  title?: string;
  description?: string;
  related?: string[];
}

export interface StateDelta {
  summary?: string;
  scene?: SceneDelta;
  goals?: GoalsDelta;
  player?: PlayerDelta;
  npcs?: NpcDelta[];
  new_npcs?: NewNpcDelta[];
  departed?: DepartedNpc[];
  transfers?: ItemTransferDelta[];
  world?: WorldDelta[];
  events?: EventDelta[];
  suggestions?: string[];
}
