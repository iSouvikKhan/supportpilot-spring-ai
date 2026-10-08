import { Injectable, signal } from '@angular/core';

const KEY_STORAGE = 'supportpilot.approverKey';
const NAME_STORAGE = 'supportpilot.approverName';

function read(storage: () => Storage, key: string): string {
  try {
    return storage().getItem(key) ?? '';
  } catch {
    return '';
  }
}

function write(storage: () => Storage, key: string, value: string): void {
  try {
    if (value) storage().setItem(key, value);
    else storage().removeItem(key);
  } catch {
    /* storage unavailable (private mode); keep the value in memory only */
  }
}

/**
 * Holds the approver key for this browser tab only (sessionStorage) and the approver's
 * display name (localStorage).
 */
@Injectable({ providedIn: 'root' })
export class ApproverKeyService {
  readonly key = signal(read(() => sessionStorage, KEY_STORAGE));
  readonly name = signal(read(() => localStorage, NAME_STORAGE));

  setKey(value: string): void {
    this.key.set(value.trim());
    write(() => sessionStorage, KEY_STORAGE, value.trim());
  }

  setName(value: string): void {
    this.name.set(value.trim());
    write(() => localStorage, NAME_STORAGE, value.trim());
  }

  clearKey(): void {
    this.setKey('');
  }
}
