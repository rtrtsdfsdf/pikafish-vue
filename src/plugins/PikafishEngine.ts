import { registerPlugin } from '@capacitor/core';

export interface PikafishEnginePlugin {
  init(): Promise<{ success: boolean; message?: string; error?: string }>;
  sendCommand(options: { command: string }): Promise<{ success: boolean; error?: string }>;
  quit(): Promise<void>;
  addListener(eventName: 'engineMessage', listenerFunc: (data: { message: string }) => void): Promise<void>;
}

export const PikafishEngine = registerPlugin<PikafishEnginePlugin>('PikafishEngine');
