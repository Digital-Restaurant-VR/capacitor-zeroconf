import type { ZeroConfPlugin } from './definitions';
/**
 * ZeroConf plugin instance
 *
 * Provides ZeroConf/Bonjour/mDNS service discovery and publishing capabilities
 * across iOS, Android, and Electron platforms.
 *
 * @example
 * ```typescript
 * import { ZeroConf } from 'capacitor-zeroconf';
 *
 * // Listen for discovered services
 * const listener = await ZeroConf.addListener('discover', (result) => {
 *   console.log('Service discovered:', result.service.name);
 * });
 *
 * // Start watching for HTTP services
 * await ZeroConf.watch({
 *   type: '_http._tcp.',
 *   domain: 'local.'
 * });
 * ```
 */
declare const ZeroConf: ZeroConfPlugin;
export * from './definitions';
export { ZeroConf };
