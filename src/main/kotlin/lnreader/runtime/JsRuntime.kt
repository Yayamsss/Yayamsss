package lnreader.runtime

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Exposed to JS as `__nativeBridge`. Every method here is what the JS-side
 * fetch()/console shims actually call into.
 */
class NativeBridge(private val httpClient: OkHttpClient, private val gson: Gson) {
    @HostAccess.Export
    fun httpGet(url: String, initJson: String): String {
        val init = gson.fromJson(initJson, Map::class.java) as? Map<String, Any?> ?: emptyMap()
        val method = (init["method"] as? String) ?: "GET"
        val headersMap = (init["headers"] as? Map<String, Any?>) ?: emptyMap()
        val bodyStr = init["body"] as? String

        val builder = Request.Builder().url(url)
        headersMap.forEach { (k, v) -> if (v != null) builder.addHeader(k, v.toString()) }

        val body = bodyStr?.toRequestBody("text/plain".toMediaTypeOrNull())
        when (method.uppercase()) {
            "POST" -> builder.post(body ?: "".toRequestBody(null))
            "PUT" -> builder.put(body ?: "".toRequestBody(null))
            "DELETE" -> builder.delete(body)
            else -> builder.get()
        }

        return try {
            httpClient.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                val headersOut = resp.headers.names().associateWith { name ->
                    resp.headers[name]
                }
                gson.toJson(
                    mapOf(
                        "ok" to resp.isSuccessful,
                        "status" to resp.code,
                        "headers" to headersOut.mapKeys { it.key.lowercase() },
                        "bodyText" to text,
                    )
                )
            }
        } catch (e: Exception) {
            gson.toJson(
                mapOf(
                    "ok" to false,
                    "status" to 0,
                    "headers" to emptyMap<String, String>(),
                    "bodyText" to "",
                    "error" to (e.message ?: e.toString()),
                )
            )
        }
    }

    @HostAccess.Export
    fun log(msg: String) = println("[js] $msg")
}

/** Minimal JS-side runtime the compiled LNReader plugins expect to exist. */
val JS_BOOTSTRAP = """
    globalThis.__cjsLoad = function(source) {
        const module = { exports: {} };
        const fn = new Function('module', 'exports', 'require', source);
        fn(module, module.exports, function(name) {
            throw new Error('nested require not supported in POC: ' + name);
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
            if (bits >= 8) { bits -= 8; bytes.push((buf >> bits) & 0xff); }
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
}
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
        if (p[0] === name) { if (!found) { found = true; return true; } return false; }
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
}
URL.prototype.toString = function() { return this.href; };

    globalThis.fetch = async function(url, init) {
        const raw = __nativeBridge.httpGet(url, JSON.stringify(init || {}));
        const data = JSON.parse(raw);
        return {
            ok: data.ok,
            status: data.status,
            headers: { get: (n) => (data.headers[String(n).toLowerCase()] || null) },
            text: async () => data.bodyText,
            json: async () => JSON.parse(data.bodyText),
            arrayBuffer: async () => { throw new Error('arrayBuffer() not implemented in this POC'); },
        };
    };

    globalThis.console = {
        log:   (...a) => __nativeBridge.log(a.map(x => typeof x === 'string' ? x : JSON.stringify(x)).join(' ')),
        warn:  (...a) => __nativeBridge.log('[warn] ' + a.map(String).join(' ')),
        error: (...a) => __nativeBridge.log('[error] ' + a.map(String).join(' ')),
    };

    globalThis.require = function(name) {
        switch (name) {
            case 'cheerio':      return globalThis.__cheerioLib.cheerio;
            case 'htmlparser2':  return globalThis.__cheerioLib.htmlparser2;
            case '@libs/fetch':
                return {
                    fetchApi: fetch,
                    fetchText: async (u, i) => (await fetch(u, i)).text(),
                    fetchFile: async () => { throw new Error('fetchFile not implemented in this POC'); },
                };
            case '@libs/novelStatus':
                return { NovelStatus: globalThis.__NovelStatus };
            default:
                throw new Error('Unmocked module in POC: ' + name);
        }
    };

    globalThis.__loadPlugin = function(code) {
        const module = { exports: {} };
        const fn = new Function('module', 'exports', 'require', code);
        fn(module, module.exports, require);
        return module.exports.default;
    };
""".trimIndent()

fun awaitJsPromise(context: Context, promise: Value, timeoutMs: Long = 30_000): Value {
    val future = CompletableFuture<Value>()
    promise.invokeMember(
        "then",
        ProxyExecutable { args ->
            future.complete(args.getOrNull(0))
            null
        },
        ProxyExecutable { args ->
            future.completeExceptionally(
                RuntimeException(args.getOrNull(0)?.toString() ?: "unknown JS error")
            )
            null
        },
    )

    val deadline = System.currentTimeMillis() + timeoutMs
    while (!future.isDone && System.currentTimeMillis() < deadline) {
        context.eval("js", "1")
        Thread.sleep(10)
    }
    if (!future.isDone) {
        throw IllegalStateException("Timed out waiting for JavaScript Promise to resolve.")
    }
    return future.get(1, TimeUnit.SECONDS)
}

fun Value.memberString(name: String): String? =
    if (hasMembers() && hasMember(name) && !getMember(name).isNull) getMember(name).asString() else null

fun valueAsText(context: Context, value: Value): String =
    context.eval("js", "(function(value) { return String(value); })").execute(value).asString()

/**
 * Builds and wires a GraalJS [Context] the way compiled LNReader plugins
 * expect: a `fetch()` backed by OkHttp, real `cheerio`/`htmlparser2` (via the
 * pre-built esbuild bundle in resources), a `require()` shim, and a minimal
 * `console`. One [Context] should be reused for the lifetime of a loaded
 * plugin and only ever touched from a single thread.
 */
object JsRuntime {
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    fun createContext(): Context {
        val context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.EXPLICIT)
            .allowHostClassLookup { false }
            .option("js.ecmascript-version", "2022")
            .build()

        val bindings = context.getBindings("js")
        bindings.putMember("__nativeBridge", NativeBridge(httpClient, gson))
        context.eval("js", JS_BOOTSTRAP)

        // Load the cheerio+htmlparser2 bundle once as a CJS module, expose it as __cheerioLib
        val cheerioBundleSource = javaClass.getResourceAsStream("/cheerio-bundle.cjs")
            ?.bufferedReader()?.readText()
            ?: error("cheerio-bundle.cjs missing from resources")
        val cheerioLib: Value = bindings.getMember("__cjsLoad").execute(cheerioBundleSource)
        bindings.putMember("__cheerioLib", cheerioLib)

        return context
    }

    fun loadPlugin(context: Context, pluginCode: String): Value =
        context.getBindings("js").getMember("__loadPlugin").execute(pluginCode)
}
