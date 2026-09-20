package lnreader.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import lnreader.platform.PlatformIO
import lnreader.platform.PlatformJson
import kotlinx.coroutines.CoroutineDispatcher

private class NativeBridge {
    suspend fun httpRequest(url: String, initJson: String): String =
        PlatformIO.executeJsRequest(url, initJson)

    fun log(message: String) {
        println("[js] $message")
    }
}

private val JS_BOOTSTRAP = """
    globalThis.__cjsLoad = function(source) {
        const module = { exports: {} };
        const fn = new Function('module', 'exports', 'require', source);
        fn(module, module.exports, function(name) {
            throw new Error('nested require not supported in this runtime: ' + name);
        });
        return module.exports;
    };

    globalThis.__NovelStatus = {
        Unknown: 'Unknown', Ongoing: 'Ongoing', Completed: 'Completed',
        Licensed: 'Licensed', PublishingFinished: 'Publishing Finished',
        Cancelled: 'Cancelled', OnHiatus: 'On Hiatus', STUB: 'STUB', Inactive: 'Inactive',
    };

    globalThis.Buffer = (function() {
        const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

        function decode(str) {
            str = str.replace(/[^A-Za-z0-9+/]/g, '');
            const bytes = [];
            let buf = 0, bits = 0;
            for (let i = 0; i < str.length; i++) {
                const idx = B64.indexOf(str[i]);
                if (idx === -1) continue;
                buf = (buf << 6) | idx;
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    bytes.push((buf >> bits) & 0xff);
                }
            }
            return bytes;
        }

        function encode(bytes) {
            let out = '';
            for (let i = 0; i < bytes.length; i += 3) {
                const b0 = bytes[i], b1 = bytes[i + 1], b2 = bytes[i + 2];
                const t = (b0 << 16) | ((b1 || 0) << 8) | (b2 || 0);
                out += B64[(t >> 18) & 63] + B64[(t >> 12) & 63];
                out += (i + 1 < bytes.length) ? B64[(t >> 6) & 63] : '=';
                out += (i + 2 < bytes.length) ? B64[t & 63] : '=';
            }
            return out;
        }

        function wrap(bytes) {
            const buf = new Uint8Array(bytes);
            buf.toString = function(enc) {
                if (enc === 'base64') return encode(buf);
                if (enc === 'hex') {
                    let s = '';
                    for (let i = 0; i < buf.length; i++) s += buf[i].toString(16).padStart(2, '0');
                    return s;
                }
                let s = '';
                for (let i = 0; i < buf.length; i++) s += String.fromCharCode(buf[i]);
                return s;
            };
            return buf;
        }

        return {
            from: function(input, encoding) {
                if (typeof input === 'string') {
                    if (encoding === 'base64') return wrap(decode(input));
                    const bytes = [];
                    for (let i = 0; i < input.length; i++) bytes.push(input.charCodeAt(i) & 0xff);
                    return wrap(bytes);
                }
                return wrap(Array.from(input || []));
            },
            alloc: function(size) { return wrap(new Array(size).fill(0)); },
            isBuffer: function(x) { return x instanceof Uint8Array; },
        };
    })();

    globalThis.URLSearchParams = function(init) {
        this._params = [];
        if (typeof init === 'string') {
            init = init.replace(/^\?/, '');
            if (init) {
                init.split('&').forEach(pair => {
                    const eq = pair.indexOf('=');
                    const k = eq === -1 ? pair : pair.slice(0, eq);
                    const v = eq === -1 ? '' : pair.slice(eq + 1);
                    this._params.push([decodeURIComponent(k || ''), decodeURIComponent((v || '').replace(/\+/g, ' '))]);
                });
            }
        } else if (init && typeof init === 'object') {
            Object.keys(init).forEach(k => this._params.push([k, String(init[k])]));
        }
    };
    URLSearchParams.prototype.get = function(name) {
        const found = this._params.find(p => p[0] === name);
        return found ? found[1] : null;
    };
    URLSearchParams.prototype.getAll = function(name) {
        return this._params.filter(p => p[0] === name).map(p => p[1]);
    };
    URLSearchParams.prototype.set = function(name, value) {
        let found = false;
        this._params = this._params.filter(p => {
            if (p[0] === name) {
                if (!found) {
                    found = true;
                    return true;
                }
                return false;
            }
            return true;
        }).map(p => p[0] === name ? [name, String(value)] : p);
        if (!found) this._params.push([name, String(value)]);
    };
    URLSearchParams.prototype.append = function(name, value) { this._params.push([name, String(value)]); };
    URLSearchParams.prototype.delete = function(name) { this._params = this._params.filter(p => p[0] !== name); };
    URLSearchParams.prototype.has = function(name) { return this._params.some(p => p[0] === name); };
    URLSearchParams.prototype.forEach = function(cb) { this._params.forEach(p => cb(p[1], p[0])); };
    URLSearchParams.prototype.entries = function() { return this._params[Symbol.iterator](); };
    URLSearchParams.prototype[Symbol.iterator] = function() { return this._params[Symbol.iterator](); };
    URLSearchParams.prototype.toString = function() {
        return this._params.map(p => encodeURIComponent(p[0]) + '=' + encodeURIComponent(p[1])).join('&');
    };

    globalThis.URL = function(url, base) {
        function resolve(u, b) {
            if (!b) return u;
            if (/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(u)) return u;
            const baseMatch = b.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:\/\/[^/]+)(\/.*)?$/);
            if (!baseMatch) return u;
            const origin = baseMatch[1];
            if (u.startsWith('/')) return origin + u;
            const basePath = (baseMatch[2] || '/').replace(/\/[^/]*$/, '/');
            return origin + basePath + u;
        }
        const full = resolve(url, base);
        const m = full.match(/^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/([^/?#]+)(\/[^?#]*)?(\?[^#]*)?(#.*)?$/);
        if (!m) throw new TypeError('Invalid URL: ' + full);

        this.protocol = m[1] + ':';
        const hostMatch = m[2].match(/^([^:]+)(:(\d+))?$/);
        this.hostname = hostMatch ? hostMatch[1] : m[2];
        this.port = hostMatch && hostMatch[3] ? hostMatch[3] : '';
        this.host = this.port ? this.hostname + ':' + this.port : this.hostname;
        this.pathname = m[3] || '/';
        this.hash = m[5] || '';
        this.origin = this.protocol + '//' + this.host;
        this.searchParams = new URLSearchParams(m[4] || '');

        const self = this;
        Object.defineProperty(this, 'search', { get: () => { const s = self.searchParams.toString(); return s ? '?' + s : ''; } });
        Object.defineProperty(this, 'href', { get: () => self.origin + self.pathname + self.search + self.hash });
    };
    URL.prototype.toString = function() { return this.href; };

    globalThis.fetch = async function(url, init) {
        const raw = await __nativeBridge.httpRequest(url, JSON.stringify(init || {}));
        const data = JSON.parse(raw);
        return {
            ok: data.ok,
            status: data.status,
            headers: { get: (n) => (data.headers[String(n).toLowerCase()] || null) },
            text: async () => data.bodyText,
            json: async () => JSON.parse(data.bodyText),
            arrayBuffer: async () => { throw new Error('arrayBuffer() not implemented in this runtime'); },
        };
    };

    globalThis.console = {
        log: (...a) => __nativeBridge.log(a.map(x => typeof x === 'string' ? x : JSON.stringify(x)).join(' ')),
        warn: (...a) => __nativeBridge.log('[warn] ' + a.map(String).join(' ')),
        error: (...a) => __nativeBridge.log('[error] ' + a.map(String).join(' ')),
    };

    globalThis.require = function(name) {
        switch (name) {
            case 'cheerio': return globalThis.__cheerioLib.cheerio;
            case 'htmlparser2': return globalThis.__cheerioLib.htmlparser2;
            case '@libs/fetch':
                return {
                    fetchApi: fetch,
                    fetchText: async (u, i) => (await fetch(u, i)).text(),
                    fetchFile: async () => { throw new Error('fetchFile not implemented in this runtime'); },
                };
            case '@libs/novelStatus':
                return { NovelStatus: globalThis.__NovelStatus };
            case '@libs/filterInputs':
                return {
                    FilterTypes: {
                        TextInput: 'Text',
                        Picker: 'Picker',
                        CheckboxGroup: 'Checkbox',
                        Switch: 'Switch',
                        ExcludableCheckboxGroup: 'XCheckbox',
                    },
                };
            case '@libs/defaultCover':
                return { defaultCover: 'https://github.com/LNReader/lnreader-plugins/blob/main/icons/src/coverNotAvailable.jpg?raw=true' };
            case '@libs/isAbsoluteUrl':
                return {
                    isUrlAbsolute: function(url) {
                        if (!url) return false;
                        if (url.indexOf('//') === 0) return true;
                        if (url.indexOf('://') === -1) return false;
                        if (url.indexOf('.') === -1) return false;
                        if (url.indexOf('/') === -1) return false;
                        if (url.indexOf(':') > url.indexOf('/')) return false;
                        if (url.indexOf('://') < url.indexOf('.')) return true;
                        return false;
                    },
                };
            case '@libs/storage': {
                function makeStorage() {
                    const db = {};
                    return {
                        set: function(key, value, expires) {
                            db[key] = {
                                created: new Date(),
                                value: value,
                                expires: expires instanceof Date ? expires.getTime() : expires,
                            };
                        },
                        get: function(key, raw) {
                            const item = db[key];
                            if (item && item.expires && Date.now() > item.expires) {
                                delete db[key];
                                return undefined;
                            }
                            return raw ? item : (item ? item.value : undefined);
                        },
                        getAllKeys: function() { return Object.keys(db); },
                        delete: function(key) { delete db[key]; },
                        clearAll: function() { for (const k of Object.keys(db)) delete db[k]; },
                    };
                }
                function makeWebStorage() {
                    const db = {};
                    return { get: function() { return db; } };
                }
                return {
                    storage: makeStorage(),
                    localStorage: makeWebStorage(),
                    sessionStorage: makeWebStorage(),
                };
            }
            default:
                throw new Error('Unmocked module in runtime: ' + name);
        }
    };

    globalThis.__loadPlugin = function(code) {
        const module = { exports: {} };
        const fn = new Function('module', 'exports', 'require', code);
        fn(module, module.exports, require);
        return module.exports.default;
    };
""".trimIndent()

class JsRuntime(dispatcher: CoroutineDispatcher) {
    private val bridge = NativeBridge()
    private val quickJs = QuickJs.create(jobDispatcher = dispatcher)

    suspend fun initialize() {
        quickJs.define("__nativeBridge") {
            asyncFunction("httpRequest") { args ->
                bridge.httpRequest(
                    url = args.getOrNull(0)?.toString() ?: error("httpRequest() requires a URL"),
                    initJson = args.getOrNull(1)?.toString() ?: "{}",
                )
            }
            function("log") { args ->
                bridge.log(args.joinToString(" ") { it?.toString() ?: "null" })
                null
            }
        }

        quickJs.evaluate<Any?>(JS_BOOTSTRAP)
        quickJs.evaluate<Any?>(
            """
                globalThis.__cheerioLib = globalThis.__cjsLoad(${PlatformJson.quoteString(PlatformIO.readResourceText("/cheerio-bundle.cjs"))});
            """.trimIndent()
        )
    }

    suspend fun loadPlugin(pluginCode: String) {
        quickJs.evaluate<Any?>(
            """
                globalThis.__plugin = globalThis.__loadPlugin(${PlatformJson.quoteString(pluginCode)});
            """.trimIndent()
        )
    }

    suspend fun popularNovels(page: Int): String =
        quickJs.evaluate(
            """
                JSON.stringify(
                    await globalThis.__plugin.popularNovels(
                        $page,
                        { showLatestNovels: false, filters: globalThis.__plugin.filters || {} }
                    ) ?? []
                )
            """.trimIndent()
        )

    suspend fun parseNovel(path: String): String =
        quickJs.evaluate(
            """
                JSON.stringify(await globalThis.__plugin.parseNovel(${PlatformJson.quoteString(path)}) ?? null)
            """.trimIndent()
        )

    suspend fun parseChapter(path: String): String =
        quickJs.evaluate(
            """
                String(await globalThis.__plugin.parseChapter(${PlatformJson.quoteString(path)}) ?? '')
            """.trimIndent()
        )

    fun close() {
        quickJs.close()
    }
}
