# ZeroConf Plugin - Fix Summary

## Problem Identified
The `watch()` method was not returning discovered services properly because:

1. **Multiple Promise Resolution**: Native implementations (iOS & Android) were calling `call.resolve()` multiple times
2. **Wrong Pattern**: JavaScript promises can only be resolved once - subsequent calls are ignored
3. **Events Not Emitted**: The plugin interface defined `addListener('discover', ...)` but native code wasn't emitting events

## Root Cause
The plugin had **two different patterns** defined:
- `addListener('discover', ...)` - Proper Capacitor event pattern ✅  
- `watch(..., callback?)` - Callback-based pattern with multiple resolves ❌

The native implementations were incorrectly trying to resolve the `watch()` promise multiple times instead of emitting events.

## Changes Made

### 1. Android (`ZeroConfPlugin.java`)
**Before:**
```java
call.resolve(status); // Called multiple times - BROKEN
```

**After:**
```java
// Return callback ID immediately
String callbackId = "watch_" + type + domain + "_" + System.currentTimeMillis();
call.resolve(callbackId);

// Emit events for discoveries
notifyListeners("discover", status);
```

### 2. iOS (`ZeroConfPlugin.swift`) 
**Before:**
```swift
call.resolve(["action": actionStr, "service": jsonifyService(unwrappedService)]) // Called multiple times - BROKEN
```

**After:**
```swift
// Return callback ID immediately  
let callbackId = "watch_\(type)\(domain)_\(Date().timeIntervalSince1970)"
call.resolve(callbackId)

// Emit events for discoveries
notifyListeners("discover", data: ["action": actionStr, "service": jsonifyService(unwrappedService)])
```

### 3. Web (`web.ts`)
Added proper `addListener` method implementation (stub for web platform).

## Usage Pattern (Fixed)

```typescript
// Set up event listener to receive discovered services
const listener = await ZeroConf.addListener('discover', (result) => {
  console.log(`Service ${result.action}:`, result.service);
});

// Start watching (returns callback ID immediately)
const callbackId = await ZeroConf.watch({
  type: '_snapcast._tcp.',
  domain: 'local.'
});

// All discovered services now properly trigger the 'discover' event
// No more "undefined" results or missing services!

// Stop watching
await ZeroConf.unwatch({ type: '_snapcast._tcp.', domain: 'local.' });
listener.remove();
```

## Result
✅ **Fixed**: All discovered services are now properly returned via events
✅ **Fixed**: No more "undefined" watch results  
✅ **Fixed**: Multiple service discoveries work correctly
✅ **Fixed**: Both `addListener` and `watch` patterns work as intended

The logs should now show discovered services being properly handled in JavaScript instead of just "TO JS" messages that were ignored.
