# Usage Example - Fixed ZeroConf Plugin

## The Problem (Before Fix)
The `watch()` method was trying to resolve the promise multiple times, which doesn't work in JavaScript. Only the first discovered service would be returned.

## The Solution (After Fix)
The plugin now properly uses Capacitor's event listener pattern. Use both `addListener('discover', ...)` and `watch()` together:

## Usage Pattern

```typescript
import { ZeroConf } from 'capacitor-zeroconf';

// 1. First, set up the event listener to receive discovered services
const listener = await ZeroConf.addListener('discover', (result) => {
  console.log(`Service ${result.action}:`, result.service);
  
  switch (result.action) {
    case 'added':
      console.log('New service found:', result.service.name);
      break;
    case 'resolved':
      console.log('Service resolved with details:', result.service);
      break;
    case 'removed':
      console.log('Service removed:', result.service.name);
      break;
  }
});

// 2. Then start watching for services
const callbackId = await ZeroConf.watch({
  type: '_snapcast._tcp.',
  domain: 'local.'
});

console.log('Watching started with ID:', callbackId);

// 3. Stop watching when done
await ZeroConf.unwatch({
  type: '_snapcast._tcp.',
  domain: 'local.'
});

// 4. Remove the event listener
listener.remove();
```

## Alternative: Use only the callback parameter (Legacy)

```typescript
// You can still use the callback parameter if needed
const callbackId = await ZeroConf.watch({
  type: '_snapcast._tcp.',
  domain: 'local.'
}, (result) => {
  console.log(`Service ${result.action}:`, result.service);
});
```

## What Changed

### Before (Broken):
- `watch()` tried to resolve the promise multiple times
- Only first service discovery worked
- Subsequent discoveries were lost

### After (Fixed):
- `watch()` returns a CallbackID immediately
- Service discoveries are emitted as 'discover' events via `notifyListeners()`
- All discovered services are properly received
- Both event listener and callback patterns work

## Native Implementation Changes

### Android (`ZeroConfPlugin.java`):
- Changed from `call.resolve(status)` to `notifyListeners("discover", status)`
- Returns callback ID immediately instead of resolving multiple times

### iOS (`ZeroConfPlugin.swift`):
- Changed from `call.resolve([...])` to `notifyListeners("discover", data: [...])`
- Returns callback ID immediately instead of resolving multiple times
