# capacitor-zeroconf

A Capacitor plugin for ZeroConf/Bonjour/mDNS service discovery and publishing.

## Features

- **Discover services** on your local network using mDNS/Bonjour
- **Publish services** to make your app discoverable by other devices
- **Cross-platform support** for iOS, Android, and Electron
- **Event-driven architecture** with proper service discovery callbacks
- **TypeScript support** with full type definitions

## Platform Support

| Platform | Supported |
|----------|-----------|
| iOS      | ✅        |
| Android  | ✅        |
| Electron | ✅        |
| Web      | ❌        |

**Note:** This plugin requires native platform capabilities and does not work in web browsers. Service discovery and publishing operations are automatically stopped when the application is terminated or goes into the background.

## Install

Install directly from this GitHub repository to get the latest fixes:

```bash
npm install byrdsandbytes/capacitor-zeroconf
npx cap sync
```

or

```bash
yarn add byrdsandbytes/capacitor-zeroconf
yarn cap sync
```

**Note:** If you get TypeScript errors like "Cannot find module 'capacitor-zeroconf'", this means the package wasn't built when installed from GitHub. You can either:

1. **Recommended:** Install a specific release tag:
   ```bash
   npm install byrdsandbytes/capacitor-zeroconf#v4.0.0
   ```

2. **Or** manually build after installation:
   ```bash
   cd node_modules/capacitor-zeroconf
   npm run build
   ```

## Quick Start

### Discovering Services

```typescript
import { ZeroConf } from 'capacitor-zeroconf';

// Set up listener for discovered services
const listener = await ZeroConf.addListener('discover', (result) => {
  console.log(`Service ${result.action}:`, result.service.name);
  
  if (result.action === 'resolved') {
    console.log('Service details:', {
      name: result.service.name,
      host: result.service.hostname,
      port: result.service.port,
      addresses: result.service.ipv4Addresses
    });
  }
});

// Start watching for HTTP services
await ZeroConf.watch({
  type: '_http._tcp.',
  domain: 'local.'
});

// Stop watching
await ZeroConf.unwatch({
  type: '_http._tcp.',
  domain: 'local.'
});

// Clean up
listener.remove();
```

### Publishing Services

```typescript
// Publish your app as a discoverable service
await ZeroConf.register({
  type: '_http._tcp.',
  domain: 'local.',
  name: 'My App',
  port: 8080,
  props: {
    description: 'My awesome app',
    version: '1.0.0'
  }
});

// Stop publishing
await ZeroConf.unregister({
  type: '_http._tcp.',
  domain: 'local.',
  name: 'My App'
});
```

## Important Notes

⚠️ **Breaking Change Fix**: This fork fixes a critical issue where the `watch()` method only returned the first discovered service. The native implementations now properly emit all discovered services as events through the `addListener('discover', ...)` pattern.

**Migration Guide**: If you were using the old version and only getting the first result, no code changes are needed - you'll now receive all discovered services as expected.

## API

<docgen-index>

* [`addListener('discover', ...)`](#addlistenerdiscover-)
* [`getHostname()`](#gethostname)
* [`register(...)`](#register)
* [`unregister(...)`](#unregister)
* [`stop()`](#stop)
* [`watch(...)`](#watch)
* [`unwatch(...)`](#unwatch)
* [`close()`](#close)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### addListener('discover', ...)

```typescript
addListener(eventName: 'discover', listenerFunc: (result: ZeroConfWatchResult) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                     |
| ------------------ | ---------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'discover'</code>                                                                  |
| **`listenerFunc`** | <code>(result: <a href="#zeroconfwatchresult">ZeroConfWatchResult</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### getHostname()

```typescript
getHostname() => Promise<{ hostname: string; }>
```

**Returns:** <code>Promise&lt;{ hostname: string; }&gt;</code>

--------------------


### register(...)

```typescript
register(request: ZeroConfRegisterRequest) => Promise<void>
```

| Param         | Type                                                                        |
| ------------- | --------------------------------------------------------------------------- |
| **`request`** | <code><a href="#zeroconfregisterrequest">ZeroConfRegisterRequest</a></code> |

--------------------


### unregister(...)

```typescript
unregister(request: ZeroConfUnregisterRequest) => Promise<void>
```

| Param         | Type                                                                            |
| ------------- | ------------------------------------------------------------------------------- |
| **`request`** | <code><a href="#zeroconfunregisterrequest">ZeroConfUnregisterRequest</a></code> |

--------------------


### stop()

```typescript
stop() => Promise<void>
```

--------------------


### watch(...)

```typescript
watch(request: ZeroConfWatchRequest, callback?: ZeroConfWatchCallback | undefined) => Promise<CallbackID>
```

| Param          | Type                                                                    |
| -------------- | ----------------------------------------------------------------------- |
| **`request`**  | <code><a href="#zeroconfwatchrequest">ZeroConfWatchRequest</a></code>   |
| **`callback`** | <code><a href="#zeroconfwatchcallback">ZeroConfWatchCallback</a></code> |

**Returns:** <code>Promise&lt;string&gt;</code>

--------------------


### unwatch(...)

```typescript
unwatch(request: ZeroConfUnwatchRequest) => Promise<void>
```

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`request`** | <code><a href="#zeroconfwatchrequest">ZeroConfWatchRequest</a></code> |

--------------------


### close()

```typescript
close() => Promise<void>
```

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### ZeroConfService

| Prop                | Type                                    |
| ------------------- | --------------------------------------- |
| **`domain`**        | <code>string</code>                     |
| **`type`**          | <code>string</code>                     |
| **`name`**          | <code>string</code>                     |
| **`port`**          | <code>number</code>                     |
| **`hostname`**      | <code>string</code>                     |
| **`ipv4Addresses`** | <code>string[]</code>                   |
| **`ipv6Addresses`** | <code>string[]</code>                   |
| **`txtRecord`**     | <code>{ [key: string]: string; }</code> |


#### ZeroConfRegisterRequest

| Prop        | Type                                    |
| ----------- | --------------------------------------- |
| **`port`**  | <code>number</code>                     |
| **`props`** | <code>{ [key: string]: string; }</code> |


#### ZeroConfUnregisterRequest

| Prop       | Type                |
| ---------- | ------------------- |
| **`name`** | <code>string</code> |


#### ZeroConfWatchRequest

| Prop         | Type                |
| ------------ | ------------------- |
| **`type`**   | <code>string</code> |
| **`domain`** | <code>string</code> |


### Type Aliases


#### ZeroConfWatchResult

<code>{ action: <a href="#zeroconfwatchaction">ZeroConfWatchAction</a>; service: <a href="#zeroconfservice">ZeroConfService</a>; }</code>


#### ZeroConfWatchAction

<code>'added' | 'removed' | 'resolved'</code>


#### ZeroConfWatchCallback

<code>(event: <a href="#zeroconfwatchresult">ZeroConfWatchResult</a>): void</code>


#### CallbackID

<code>string</code>


#### ZeroConfUnwatchRequest

<code><a href="#zeroconfwatchrequest">ZeroConfWatchRequest</a></code>

</docgen-api>

## Contributing

This is a fork of the original [capacitor-zeroconf](https://github.com/trik/capacitor-zeroconf) plugin with critical bug fixes for service discovery. 

### Recent Improvements

- ✅ **Fixed service discovery**: All discovered services are now properly returned (not just the first one)
- ✅ **Proper event emission**: Native implementations now use `notifyListeners()` correctly
- ✅ **Better TypeScript support**: Improved type definitions and error handling
- ✅ **Updated documentation**: Modern terminology and better examples

## License

MIT License

This project is licensed under the MIT License - the same license as the original [cordova-plugin-zeroconf](https://github.com/becvert/cordova-plugin-zeroconf) plugin.

## Credits

Originally ported from the [Cordova ZeroConf Plugin](https://github.com/becvert/cordova-plugin-zeroconf) and based on [capacitor-zeroconf](https://github.com/trik/capacitor-zeroconf) by Marco Marche.
