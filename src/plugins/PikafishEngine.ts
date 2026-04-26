import { registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

export interface PikafishEnginePlugin {
  init(): Promise<{ success: boolean; message?: string; error?: string }>;
  sendCommand(options: { command: string }): Promise<{ success: boolean; error?: string }>;
  setMessageCallback(): Promise<void>;
  quit(): Promise<void>;
  addListener(eventName: string, listenerFunc: (data: { message: string }) => void): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

const PikafishEngine = registerPlugin<PikafishEnginePlugin>('PikafishEngine');

export default PikafishEngine;
