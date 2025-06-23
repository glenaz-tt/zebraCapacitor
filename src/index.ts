import { registerPlugin } from '@capacitor/core';
import type { ZebraCapacitorPluginInterface } from './definitions';

const ZebraCapacitor = registerPlugin<ZebraCapacitorPluginInterface>('ZebraCapacitor');

export * from './definitions';
export { ZebraCapacitor };