package eu.kanade.tachiyomi.extension.novel.runtime

import android.util.Log
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Value
import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Structured logger for plugin compatibility issues.
 * Logs include plugin id, operation, capability, and failure category
 * to help debug compatibility issues with specific plugins.
 */
class CompatibilityLogger(
    private val pluginId: String,
) {
    fun logOperation(operation: String, capability: String, details: String? = null) {
        val message = buildString {
            append("plugin=$pluginId")
            append(" op=$operation")
            append(" cap=$capability")
            if (details != null) append(" details=$details")
        }
        Log.d(LOG_TAG, message)
    }

    fun logFailure(operation: String, capability: String, category: String, error: Throwable? = null) {
        val message = buildString {
            append("plugin=$pluginId")
            append(" op=$operation")
            append(" cap=$capability")
            append(" failure=$category")
            if (error != null) append(" error=${error.message}")
        }
        Log.e(LOG_TAG, message, error)
    }

    fun logCapabilityCheck(capability: String, supported: Boolean) {
        val message = buildString {
            append("plugin=$pluginId")
            append(" cap=$capability")
            append(" supported=$supported")
        }
        Log.i(LOG_TAG, message)
    }

    companion object {
        private const val LOG_TAG = "NovelCompatibility"
    }
}

// Runtime boundary: keep J2V8-specific details inside this class so callers only see the plugin API.
class NovelJsRuntime(
    private val pluginId: String,
    private val nativeApi: NativeApi,
    private val moduleRegistry: NovelJsModuleRegistry = NovelJsModuleRegistry(),
) : Closeable {

    private val compatibilityLogger = CompatibilityLogger(pluginId)

    @Volatile
    var released = false

    private val runtimeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NovelJsRuntime-$pluginId").apply { isDaemon = true }
    }

    private val v8: V8 = runOnRuntimeThread {
        V8.createV8Runtime().apply {
            Log.i(LOG_TAG, "Using J2V8 runtime for plugin id=$pluginId")
            compatibilityLogger.logOperation("init", "runtime", "J2V8")
            bindNativeApi(this, nativeApi, compatibilityLogger)
            executeVoidScript(bootstrapScript, "novel-js-runtime-bootstrap.js", 0)
            moduleRegistry.registerModules(this)
            compatibilityLogger.logOperation("init", "modules", "registered=${moduleRegistry.modules().size}")
        }
    }

    fun evaluate(
        script: String,
        fileName: String = "novel-plugin.js",
        timeoutMs: Long? = null,
    ): Any? {
        return runOnRuntimeThread(timeoutMs) {
            val value = v8.executeScript(script, fileName, 0)
            normalizeValue(value)
        }
    }

    override fun close() {
        released = true
        runOnRuntimeThread {
            nativeApi.domReleaseAll()
            v8.release(true)
            Unit
        }
        runtimeExecutor.shutdown()
    }

    interface NativeApi {
        fun fetch(url: String, optionsJson: String?): String
        fun fetchBinary(url: String, optionsJson: String?): String
        fun fetchProto(url: String, configJson: String, optionsJson: String?): String
        fun storageGet(key: String): String?
        fun storageSet(key: String, value: String)
        fun storageRemove(key: String)
        fun storageClear()
        fun storageKeys(): String
        fun localStorageGet(key: String): String?
        fun localStorageSet(key: String, value: String)
        fun localStorageRemove(key: String)
        fun localStorageClear()
        fun localStorageKeys(): String
        fun sessionStorageGet(key: String): String?
        fun sessionStorageSet(key: String, value: String)
        fun sessionStorageRemove(key: String)
        fun sessionStorageClear()
        fun sessionStorageKeys(): String
        fun resolveUrl(url: String, base: String?): String
        fun getPathname(url: String): String
        fun select(html: String, selector: String): String
        fun aesGcmDecrypt(keyB64: String, ivB64: String, cipherB64: String): String

        // DOM Store methods
        fun domLoad(html: String): Int
        fun domSelect(handle: Int, selector: String): String
        fun domParent(handle: Int): Int
        fun domChildren(handle: Int, selector: String?): String
        fun domNext(handle: Int, selector: String?): Int
        fun domPrev(handle: Int, selector: String?): Int
        fun domNextAll(handle: Int, selector: String?): String
        fun domPrevAll(handle: Int, selector: String?): String
        fun domSiblings(handle: Int, selector: String?): String
        fun domClosest(handle: Int, selector: String): Int
        fun domContents(handle: Int): String
        fun domIs(handle: Int, selector: String): Boolean
        fun domHas(handle: Int, selector: String): Boolean
        fun domNot(handle: Int, selector: String): String
        fun domHtml(handle: Int): String
        fun domOuterHtml(handle: Int): String
        fun domXml(handle: Int): String
        fun domText(handle: Int): String
        fun domAttr(handle: Int, name: String): String?
        fun domSetAttr(handle: Int, name: String, value: String)
        fun domRemoveAttr(handle: Int, name: String)
        fun domAttrs(handle: Int): String
        fun domHasClass(handle: Int, className: String): Boolean
        fun domData(handle: Int, key: String): String?
        fun domVal(handle: Int): String?
        fun domTagName(handle: Int): String
        fun domIsTextNode(handle: Int): Boolean
        fun domReplaceWith(handle: Int, html: String)
        fun domBefore(handle: Int, html: String)
        fun domAfter(handle: Int, html: String)
        fun domAppend(handle: Int, html: String)
        fun domPrepend(handle: Int, html: String)
        fun domEmpty(handle: Int)
        fun domRemove(handle: Int)
        fun domAddClass(handle: Int, className: String)
        fun domRemoveClass(handle: Int, className: String)
        fun domRelease(handle: Int)
        fun domReleaseAll()

        // Console
        fun consoleLog(message: String)
        fun consoleError(message: String)
        fun consoleWarn(message: String)
    }

    @Suppress("DEPRECATION")
    private fun normalizeValue(value: Any?): Any? {
        if (value !is V8Value) return value
        return try {
            null
        } finally {
            value.release()
        }
    }

    private fun <T> runOnRuntimeThread(timeoutMs: Long? = null, block: () -> T): T {
        val future = runtimeExecutor.submit<T> { block() }
        return try {
            if (timeoutMs != null) {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                future.get()
            }
        } catch (e: ExecutionException) {
            val cause = e.cause
            when (cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw RuntimeException(cause)
            }
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw RuntimeException(
                "J2V8 runtime call timed out after ${timeoutMs ?: 0L}ms for plugin id=$pluginId",
                e,
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("J2V8 runtime call interrupted for plugin id=$pluginId", e)
        }
    }

    companion object {
        private const val LOG_TAG = "NovelJsRuntime"
        private const val NATIVE_OBJECT_NAME = "__native"

        private val bootstrapScript = listOf(
            NovelJsPromiseShim.script,
            """
                (function(global) {
                  var __modules = {};
                  var __moduleFactories = {};
                  global.__defineModule = function(name, factory) {
                    __moduleFactories[name] = factory;
                  };
                  global.require = function(name) {
                    if (__modules[name]) return __modules[name].exports;
                    var factory = __moduleFactories[name];
                    if (!factory) throw new Error("Module not found: " + name);
                    var module = { exports: {} };
                    __modules[name] = module;
                    factory(module, module.exports);
                    return module.exports;
                  };
                  function FormData() { this.__formDataEntries = []; }
                  FormData.prototype.append = function(key, value) {
                    this.__formDataEntries.push({ key: String(key), value: String(value) });
                  };
                  global.FormData = FormData;
                  function URLSearchParams(init) {
                    this._entries = [];
                    if (typeof init === "string") {
                      var pairs = init.split("&");
                      for (var i = 0; i < pairs.length; i++) {
                        if (!pairs[i]) continue;
                        var parts = pairs[i].split("=");
                        var key = decodeURIComponent(parts[0] || "");
                        var value = decodeURIComponent(parts.slice(1).join("=") || "");
                        this._entries.push([key, value]);
                      }
                    } else if (init && typeof init === "object") {
                      for (var key in init) {
                        if (Object.prototype.hasOwnProperty.call(init, key)) {
                          this._entries.push([String(key), String(init[key])]);
                        }
                      }
                    }
                  }
                  URLSearchParams.prototype.append = function(key, value) {
                    this._entries.push([String(key), String(value)]);
                  };
                  URLSearchParams.prototype.set = function(key, value) {
                    var k = String(key);
                    var v = String(value);
                    var replaced = false;
                    var filtered = [];
                    for (var i = 0; i < this._entries.length; i++) {
                      var entry = this._entries[i];
                      if (entry[0] === k) {
                        if (!replaced) {
                          filtered.push([k, v]);
                          replaced = true;
                        }
                      } else {
                        filtered.push(entry);
                      }
                    }
                    if (!replaced) filtered.push([k, v]);
                    this._entries = filtered;
                  };
                  URLSearchParams.prototype.get = function(key) {
                    var k = String(key);
                    for (var i = 0; i < this._entries.length; i++) {
                      if (this._entries[i][0] === k) return this._entries[i][1];
                    }
                    return null;
                  };
                  URLSearchParams.prototype.has = function(key) {
                    var k = String(key);
                    for (var i = 0; i < this._entries.length; i++) {
                      if (this._entries[i][0] === k) return true;
                    }
                    return false;
                  };
                  URLSearchParams.prototype.delete = function(key) {
                    var k = String(key);
                    var filtered = [];
                    for (var i = 0; i < this._entries.length; i++) {
                      if (this._entries[i][0] !== k) filtered.push(this._entries[i]);
                    }
                    this._entries = filtered;
                  };
                  URLSearchParams.prototype.toString = function() {
                    return this._entries
                      .map(function(entry) {
                        return encodeURIComponent(entry[0]) + "=" + encodeURIComponent(entry[1]);
                      })
                      .join("&");
                  };
                  global.URLSearchParams = URLSearchParams;

                  function Headers(init) {
                    this._headers = {};
                    if (init) {
                      if (typeof Headers !== "undefined" && init instanceof Headers) {
                        init.forEach(function(value, key) {
                          this._headers[key.toLowerCase()] = String(value);
                        }, this);
                      } else if (Array.isArray(init)) {
                        for (var i = 0; i < init.length; i++) {
                          this.append(init[i][0], init[i][1]);
                        }
                      } else if (init && typeof init === "object") {
                        for (var key in init) {
                          if (Object.prototype.hasOwnProperty.call(init, key)) {
                            this.append(key, init[key]);
                          }
                        }
                      }
                    }
                  }
                  Headers.prototype.get = function(name) {
                    var key = String(name).toLowerCase();
                    return Object.prototype.hasOwnProperty.call(this._headers, key) ? this._headers[key] : null;
                  };
                  Headers.prototype.set = function(name, value) {
                    this._headers[String(name).toLowerCase()] = String(value);
                  };
                  Headers.prototype.append = function(name, value) {
                    var key = String(name).toLowerCase();
                    if (Object.prototype.hasOwnProperty.call(this._headers, key)) {
                      this._headers[key] += ", " + String(value);
                    } else {
                      this._headers[key] = String(value);
                    }
                  };
                  Headers.prototype.has = function(name) {
                    return Object.prototype.hasOwnProperty.call(this._headers, String(name).toLowerCase());
                  };
                  Headers.prototype.delete = function(name) {
                    delete this._headers[String(name).toLowerCase()];
                  };
                  Headers.prototype.forEach = function(callback, thisArg) {
                    for (var key in this._headers) {
                      if (Object.prototype.hasOwnProperty.call(this._headers, key)) {
                        callback.call(thisArg, this._headers[key], key, this);
                      }
                    }
                  };
                  Headers.prototype.keys = function() {
                    var result = [];
                    for (var key in this._headers) {
                      if (Object.prototype.hasOwnProperty.call(this._headers, key)) result.push(key);
                    }
                    return result;
                  };
                  Headers.prototype.toJSON = function() {
                    var result = {};
                    for (var key in this._headers) {
                      if (Object.prototype.hasOwnProperty.call(this._headers, key)) {
                        result[key] = this._headers[key];
                      }
                    }
                    return result;
                  };
                  global.Headers = Headers;

                  function URL(input, base) {
                    var resolved = __native.resolveUrl(String(input), base != null ? String(base) : null);
                    this.pathname = __native.getPathname(resolved);
                    // Split URL into base + query + fragment so toString() can
                    // rebuild including mutations to this.searchParams.
                    var qi = resolved.indexOf('?');
                    var hi = resolved.indexOf('#');
                    if (qi >= 0) {
                      this._baseUrl = resolved.substring(0, qi);
                      var qEnd = hi >= 0 ? hi : resolved.length;
                      this._fragment = hi >= 0 ? resolved.substring(hi) : '';
                      this.searchParams = new URLSearchParams(resolved.substring(qi + 1, qEnd));
                    } else if (hi >= 0) {
                      this._baseUrl = resolved.substring(0, hi);
                      this._fragment = resolved.substring(hi);
                      this.searchParams = new URLSearchParams('');
                    } else {
                      this._baseUrl = resolved;
                      this._fragment = '';
                      this.searchParams = new URLSearchParams('');
                    }
                  }
                  URL.prototype.toString = function() {
                    var qs = this.searchParams.toString();
                    return this._baseUrl + (qs ? '?' + qs : '') + this._fragment;
                  };
                  URL.prototype.valueOf = function() {
                    return this.toString();
                  };
                  URL.prototype.toJSON = function() {
                    return this.toString();
                  };
                  Object.defineProperty(URL.prototype, 'href', {
                    get: function() { return this.toString(); },
                    set: function(v) { this._baseUrl = v; },
                    configurable: true,
                    enumerable: true
                  });
                  global.URL = URL;

                  global.console = {
                    log: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleLog(args.join(' '));
                    },
                    error: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleError(args.join(' '));
                    },
                    warn: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleWarn(args.join(' '));
                    },
                    info: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleLog(args.join(' '));
                    },
                    debug: function() {}
                  };

                  if (!Array.prototype.flat) {
                    Array.prototype.flat = function(depth) {
                      depth = depth === undefined ? 1 : Math.floor(Number(depth));
                      if (depth < 1) return this.slice();
                      return this.reduce(function(acc, val) {
                        if (Array.isArray(val) && depth > 0) {
                          acc.push.apply(acc, val.flat(depth - 1));
                        } else {
                          acc.push(val);
                        }
                        return acc;
                      }, []);
                    };
                  }
                  if (!Array.prototype.flatMap) {
                    Array.prototype.flatMap = function(fn, thisArg) {
                      return this.map(fn, thisArg).flat(1);
                    };
                  }
                  if (!Array.prototype.includes) {
                    Array.prototype.includes = function(value, fromIndex) {
                      var start = fromIndex || 0;
                      for (var i = start; i < this.length; i++) {
                        if (this[i] === value || (this[i] !== this[i] && value !== value)) return true;
                      }
                      return false;
                    };
                  }
                  if (!Array.prototype.find) {
                    Array.prototype.find = function(fn, thisArg) {
                      for (var i = 0; i < this.length; i++) {
                        if (fn.call(thisArg, this[i], i, this)) return this[i];
                      }
                      return undefined;
                    };
                  }
                  if (!Array.prototype.findIndex) {
                    Array.prototype.findIndex = function(fn, thisArg) {
                      for (var i = 0; i < this.length; i++) {
                        if (fn.call(thisArg, this[i], i, this)) return i;
                      }
                      return -1;
                    };
                  }
                  if (!Array.from) {
                    Array.from = function(iterable, mapFn, thisArg) {
                      var arr = [];
                      if (iterable.length !== undefined) {
                        for (var i = 0; i < iterable.length; i++) {
                          arr.push(mapFn ? mapFn.call(thisArg, iterable[i], i) : iterable[i]);
                        }
                      }
                      return arr;
                    };
                  }
                  if (!Object.entries) {
                    Object.entries = function(obj) {
                      var entries = [];
                      for (var key in obj) {
                        if (Object.prototype.hasOwnProperty.call(obj, key)) {
                          entries.push([key, obj[key]]);
                        }
                      }
                      return entries;
                    };
                  }
                  if (!Object.values) {
                    Object.values = function(obj) {
                      var values = [];
                      for (var key in obj) {
                        if (Object.prototype.hasOwnProperty.call(obj, key)) {
                          values.push(obj[key]);
                        }
                      }
                      return values;
                    };
                  }
                  if (!Object.assign) {
                    Object.assign = function(target) {
                      for (var i = 1; i < arguments.length; i++) {
                        var source = arguments[i];
                        if (source != null) {
                          for (var key in source) {
                            if (Object.prototype.hasOwnProperty.call(source, key)) {
                              target[key] = source[key];
                            }
                          }
                        }
                      }
                      return target;
                    };
                  }
                  if (!String.prototype.includes) {
                    String.prototype.includes = function(search, start) {
                      return this.indexOf(search, start || 0) !== -1;
                    };
                  }
                  if (!String.prototype.startsWith) {
                    String.prototype.startsWith = function(search, pos) {
                      pos = pos || 0;
                      return this.substr(pos, search.length) === search;
                    };
                  }
                  if (!String.prototype.endsWith) {
                    String.prototype.endsWith = function(search, thisLen) {
                      if (thisLen === undefined) thisLen = this.length;
                      return this.substring(thisLen - search.length, thisLen) === search;
                    };
                  }
                  if (!String.prototype.trimStart) {
                    String.prototype.trimStart = function() { return this.replace(/^\s+/, ''); };
                  }
                  if (!String.prototype.trimEnd) {
                    String.prototype.trimEnd = function() { return this.replace(/\s+$/, ''); };
                  }
                  if (!String.prototype.padStart) {
                    String.prototype.padStart = function(len, fill) {
                      var s = String(this);
                      fill = fill || ' ';
                      while (s.length < len) s = fill + s;
                      return s.substring(s.length - len);
                    };
                  }
                  if (!String.prototype.padEnd) {
                    String.prototype.padEnd = function(len, fill) {
                      var s = String(this);
                      fill = fill || ' ';
                      while (s.length < len) s = s + fill;
                      return s.substring(0, len);
                    };
                  }
                  if (!String.prototype.replaceAll) {
                    String.prototype.replaceAll = function(search, replacement) {
                      return this.split(search).join(replacement);
                    };
                  }
                  if (!Number.isFinite) {
                    Number.isFinite = function(value) {
                      return typeof value === 'number' && isFinite(value);
                    };
                  }
                  if (!Number.isNaN) {
                    Number.isNaN = function(value) {
                      return typeof value === 'number' && value !== value;
                    };
                  }
                  if (typeof global.Intl === 'undefined') {
                    global.Intl = {};
                    global.Intl.DateTimeFormat = function(locale, options) {
                      return {
                        format: function(date) {
                          try { date = new Date(date); return date.toISOString().split('T')[0]; }
                          catch(e) { return String(date); }
                        },
                        resolvedOptions: function() { return {}; }
                      };
                    };
                    global.Intl.NumberFormat = function(locale, options) {
                      return {
                        format: function(n) { return String(n); },
                        resolvedOptions: function() { return {}; }
                      };
                    };
                    global.Intl.Collator = function(locale, options) {
                      return {
                        compare: function(a, b) {
                          a = String(a || ''); b = String(b || '');
                          return a < b ? -1 : a > b ? 1 : 0;
                        },
                        resolvedOptions: function() { return {}; }
                      };
                    };
                  }
                })(this);
            """.trimIndent(),
        ).joinToString("\n")
    }
}

class NovelJsModuleRegistry(
    private val assetLoader: ((String) -> String)? = null,
) {
    fun registerModules(runtime: V8) {
        modules().forEach { module ->
            runtime.executeVoidScript(module.script, module.name, 0)
        }
    }

    fun modules(): List<NovelJsModule> {
        return listOf(
            NovelJsModule("novelStatus.js", novelStatusModule),
            NovelJsModule("storage.js", storageModule),
            NovelJsModule("filterInputs.js", filterInputsModule),
            NovelJsModule("defaultCover.js", defaultCoverModule),
            NovelJsModule("proseMirrorToHtml.js", proseMirrorToHtmlModule),
            NovelJsModule("fetch.js", fetchModule),
            NovelJsModule("isAbsoluteUrl.js", isAbsoluteUrlModule),
            NovelJsModule("typesConstants.js", typesConstantsModule),
            NovelJsModule("aes.js", aesModule),
            NovelJsModule("urlencode.js", urlEncodeModule),
            NovelJsModule("cheerio.js", cheerioModule),
            NovelJsModule("htmlparser2.js", htmlParserModule()),
            NovelJsModule("dayjs.js", dayjsModule()),
        )
    }

    data class NovelJsModule(
        val name: String,
        val script: String,
    )

    private val novelStatusModule = """
        __defineModule("@libs/novelStatus", function(module, exports) {
          module.exports = {
            NovelStatus: {
              Unknown: "Unknown",
              Ongoing: "Ongoing",
              Completed: "Completed",
              Licensed: "Licensed",
              PublishingFinished: "Publishing Finished",
              Cancelled: "Cancelled",
              OnHiatus: "On Hiatus"
            }
          };
        });
    """.trimIndent()

    private val storageModule = """
        __defineModule("@libs/storage", function(module, exports) {
          function parseValue(raw) {
            if (!raw) return undefined;
            try { return JSON.parse(raw); } catch (e) { return { value: raw }; }
          }
          function parseStorageValue(raw) {
            if (!raw) return undefined;
            try { return JSON.parse(raw); } catch (e) { return raw; }
          }
          function stringifyValue(value) {
            if (typeof value === "string") return value;
            try { return JSON.stringify(value); } catch (e) { return String(value); }
          }
          function now() { return Date.now(); }
          function createStorage(getNative, setNative, removeNative, clearNative, keysNative) {
            function readRaw(key) {
              return getNative(String(key));
            }
            function readParsed(key) {
              return parseStorageValue(readRaw(key));
            }
            function readAll() {
              var result = {};
              var keys = JSON.parse(keysNative());
              for (var index = 0; index < keys.length; index++) {
                var key = keys[index];
                var value = readRaw(key);
                if (typeof value !== "undefined" && value !== null) {
                  result[key] = value;
                }
              }
              return result;
            }
            function keyAt(index) {
              var keys = JSON.parse(keysNative());
              return typeof keys[index] === "undefined" ? null : keys[index];
            }
            return {
              get: function(key, raw) {
                if (typeof key === "undefined") return readAll();
                return raw ? readRaw(key) : readParsed(key);
              },
              getItem: function(key) { return readParsed(key); },
              set: function(key, value) { setNative(String(key), stringifyValue(value)); },
              setItem: function(key, value) { setNative(String(key), stringifyValue(value)); },
              delete: function(key) { removeNative(String(key)); },
              remove: function(key) { removeNative(String(key)); },
              removeItem: function(key) { removeNative(String(key)); },
              clear: function() { clearNative(); },
              clearAll: function() { clearNative(); },
              getAllKeys: function() { return JSON.parse(keysNative()); },
              keys: function() { return JSON.parse(keysNative()); },
              key: function(index) { return keyAt(index); },
              get length() { return JSON.parse(keysNative()).length; }
            };
          }
          var storage = {
            set: function(key, value, expires) {
              var expiry = null;
              if (expires instanceof Date) expiry = expires.getTime();
              else if (typeof expires === "number") expiry = expires;
              var payload = JSON.stringify({ value: value, expires: expiry, created: now() });
              __native.storageSet(String(key), payload);
            },
            get: function(key, raw) {
              var parsed = parseValue(__native.storageGet(String(key)));
              if (!parsed) return undefined;
              if (parsed.expires && now() > parsed.expires) {
                __native.storageRemove(String(key));
                return undefined;
              }
              return raw ? parsed : parsed.value;
            },
            getAllKeys: function() { return JSON.parse(__native.storageKeys()); },
            delete: function(key) { __native.storageRemove(String(key)); },
            clearAll: function() { __native.storageClear(); }
          };
          var localStorage = createStorage(
            __native.localStorageGet,
            __native.localStorageSet,
            __native.localStorageRemove,
            __native.localStorageClear,
            __native.localStorageKeys
          );
          var sessionStorage = createStorage(
            __native.sessionStorageGet,
            __native.sessionStorageSet,
            __native.sessionStorageRemove,
            __native.sessionStorageClear,
            __native.sessionStorageKeys
          );
          module.exports = { storage: storage, localStorage: localStorage, sessionStorage: sessionStorage };
        });
    """.trimIndent()

    private val filterInputsModule = """
        __defineModule("@libs/filterInputs", function(module, exports) {
          module.exports = {
            FilterTypes: {
              TextInput: "Text",
              Picker: "Picker",
              CheckboxGroup: "Checkbox",
              Switch: "Switch",
              ExcludableCheckboxGroup: "XCheckbox"
            }
          };
        });
    """.trimIndent()

    private val defaultCoverModule = """
        __defineModule("@libs/defaultCover", function(module, exports) {
          module.exports = {
            defaultCover: "https://github.com/LNReader/lnreader-plugins/blob/main/icons/src/coverNotAvailable.jpg?raw=true"
          };
        });
    """.trimIndent()

    private val proseMirrorToHtmlModule = """
        __defineModule("@libs/proseMirrorToHtml", function(module, exports) {
          function normalizeType(type) {
            if (typeof type !== "string") return "";
            return type.toLowerCase().replace(/[_-\s]/g, "");
          }
          function escapeHtml(value) {
            return String(value == null ? "" : value)
              .replace(/&/g, "&amp;")
              .replace(/</g, "&lt;")
              .replace(/>/g, "&gt;");
          }
          function escapeAttribute(value) {
            return escapeHtml(value).replace(/"/g, "&quot;").replace(/'/g, "&#39;");
          }
          function normalizeImageUrls(value, fallback) {
            var urls = Array.isArray(value) ? value : (value ? [value] : []);
            urls = urls.filter(function(item) { return !!item; });
            if (!urls.length && fallback) {
              urls.push(fallback);
            }
            return urls;
          }
          function applyMarks(text, marks) {
            if (!Array.isArray(marks) || !marks.length) return text;
            var rendered = text;
            for (var i = 0; i < marks.length; i++) {
              var mark = marks[i] || {};
              var type = normalizeType(mark.type);
              if (type === "bold" || type === "strong") rendered = "<b>" + rendered + "</b>";
              else if (type === "italic" || type === "em") rendered = "<i>" + rendered + "</i>";
              else if (type === "underline") rendered = "<u>" + rendered + "</u>";
              else if (type === "strike" || type === "s") rendered = "<s>" + rendered + "</s>";
              else if (type === "code") rendered = "<code>" + rendered + "</code>";
              else if (type === "link") {
                var href = mark.attrs && typeof mark.attrs.href === "string" ? mark.attrs.href : "";
                if (href) {
                  rendered = "<a href=\"" + escapeAttribute(href) + "\">" + rendered + "</a>";
                }
              }
            }
            return rendered;
          }
          function renderNodes(nodes, options) {
            if (!Array.isArray(nodes)) return "";
            var html = "";
            for (var i = 0; i < nodes.length; i++) {
              html += renderNode(nodes[i], options);
            }
            return html;
          }
          function renderNode(node, options) {
            if (!node || typeof node !== "object") return "";
            var type = normalizeType(node.type);
            var children = renderNodes(node.content, options);
            if (type === "doc") return children;
            if (type === "paragraph") return "<p>" + (children || "<br>") + "</p>";
            if (type === "bulletlist") return "<ul>" + (children || "<br>") + "</ul>";
            if (type === "orderedlist") return "<ol>" + (children || "<br>") + "</ol>";
            if (type === "listitem") return "<li>" + (children || "<br>") + "</li>";
            if (type === "blockquote") return "<blockquote>" + (children || "<br>") + "</blockquote>";
            if (type === "hardbreak") return "<br>";
            if (type === "horizontalrule" || type === "delimiter") return "<hr>";
            if (type === "heading") {
              var levelRaw = Number(node.attrs && node.attrs.level);
              var level = levelRaw >= 1 && levelRaw <= 6 ? levelRaw : 2;
              return "<h" + level + ">" + (children || "<br>") + "</h" + level + ">";
            }
            if (type === "image") {
              var resolved;
              if (options && typeof options.resolveImageUrls === "function") {
                try {
                  resolved = options.resolveImageUrls(node);
                } catch (_error) {
                  resolved = undefined;
                }
              }
              var fallback = node.attrs && typeof node.attrs.src === "string" ? node.attrs.src : undefined;
              var urls = normalizeImageUrls(resolved, fallback);
              if (!urls.length) return "";
              var alt = node.attrs && typeof node.attrs.alt === "string"
                ? " alt=\"" + escapeAttribute(node.attrs.alt) + "\""
                : "";
              var imageHtml = "";
              for (var j = 0; j < urls.length; j++) {
                imageHtml += "<img src=\"" + escapeAttribute(urls[j]) + "\"" + alt + ">";
              }
              return imageHtml;
            }
            if (type === "text") {
              return applyMarks(escapeHtml(node.text), node.marks);
            }
            return children;
          }
          function proseMirrorToHtml(input, options) {
            if (Array.isArray(input)) {
              return renderNodes(input, options || {});
            }
            if (input && typeof input === "object" && Array.isArray(input.content)) {
              return renderNodes(input.content, options || {});
            }
            return "";
          }
          module.exports = { proseMirrorToHtml: proseMirrorToHtml };
        });
    """.trimIndent()

    private val fetchModule = """
        __defineModule("@libs/fetch", function(module, exports) {
          function decodeBase64ToArrayBuffer(base64) {
            if (!base64) return new ArrayBuffer(0);
            var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
            var clean = String(base64).replace(/[^A-Za-z0-9+/=]/g, "");
            var padding = 0;
            if (clean.length >= 2 && clean.slice(clean.length - 2) === "==") padding = 2;
            else if (clean.length >= 1 && clean.slice(clean.length - 1) === "=") padding = 1;
            var outLength = Math.floor((clean.length * 3) / 4) - padding;
            if (outLength < 0) outLength = 0;
            var out = new Uint8Array(outLength);
            var outIndex = 0;
            for (var i = 0; i < clean.length; i += 4) {
              var c1 = chars.indexOf(clean.charAt(i));
              var c2 = chars.indexOf(clean.charAt(i + 1));
              var c3 = chars.indexOf(clean.charAt(i + 2));
              var c4 = chars.indexOf(clean.charAt(i + 3));
              if (c1 < 0 || c2 < 0) break;
              var triplet = (c1 << 18) | (c2 << 12) | ((c3 < 0 ? 0 : c3) << 6) | (c4 < 0 ? 0 : c4);
              if (outIndex < out.length) out[outIndex++] = (triplet >> 16) & 255;
              if (outIndex < out.length && clean.charAt(i + 2) !== "=") out[outIndex++] = (triplet >> 8) & 255;
              if (outIndex < out.length && clean.charAt(i + 3) !== "=") out[outIndex++] = triplet & 255;
            }
            return out.buffer;
          }
          function makeResponse(response) {
            var src = response.headers || {};
            var headers = {};
            for (var key in src) {
              if (Object.prototype.hasOwnProperty.call(src, key)) headers[key.toLowerCase()] = src[key];
            }
            headers.get = function(name) { return this[name] != null ? this[name] : this[String(name).toLowerCase()] || null; };
            headers.has = function(name) { return this[name] != null || String(name).toLowerCase() in this; };
            var cachedArrayBuffer = null;
            return {
              ok: response.status >= 200 && response.status < 300,
              status: response.status,
              url: response.url || "",
              headers: headers,
              text: function() { return Promise.resolve(response.body || ""); },
              json: function() { return Promise.resolve(response.body ? JSON.parse(response.body) : null); },
              arrayBuffer: function() {
                if (cachedArrayBuffer != null) return Promise.resolve(cachedArrayBuffer);
                var bodyBase64 = response.bodyBase64;
                if (!bodyBase64 && response.__requestUrl) {
                  bodyBase64 = __native.fetchBinary(response.__requestUrl, response.__requestOptionsPayload || null);
                }
                cachedArrayBuffer = decodeBase64ToArrayBuffer(bodyBase64 || "");
                return Promise.resolve(cachedArrayBuffer);
              }
            };
          }
          function normalizeInit(init) {
            if (!init) return { method: "GET", headers: {}, bodyType: "none", body: null };
            var bodyType = "none";
            var body = null;
            var formEntries = null;
            var referrer = null;
            var origin = null;
            if (init.body != null) {
              if (init.body && init.body.__formDataEntries) {
                bodyType = "form";
                formEntries = init.body.__formDataEntries;
              } else {
                bodyType = "text";
                body = String(init.body);
              }
            }
            if (init.referrer != null) referrer = init.referrer;
            else if (init.referer != null) referrer = init.referer;
            else if (init.Referer != null) referrer = init.Referer;
            else if (init.Referrer != null) referrer = init.Referrer;

            if (init.origin != null) origin = init.origin;
            else if (init.Origin != null) origin = init.Origin;

            return {
              method: init.method || "GET",
              headers: init.headers || {},
              bodyType: bodyType,
              body: body,
              formEntries: formEntries,
              referrer: referrer == null ? null : String(referrer),
              origin: origin == null ? null : String(origin)
            };
          }
          function normalizeFetchInput(url, options) {
            var resolvedUrl = url;
            var resolvedOptions = options;
            if (url && typeof url === "object") {
              if (url.url != null) {
                resolvedUrl = url.url;
              } else if (url.href != null) {
                resolvedUrl = url.href;
              }
              if (!resolvedOptions) {
                var derivedOptions = {};
                var hasDerivedOptions = false;
                if (url.method != null) {
                  derivedOptions.method = url.method;
                  hasDerivedOptions = true;
                }
                if (url.headers != null) {
                  derivedOptions.headers = url.headers;
                  hasDerivedOptions = true;
                }
                if (url.body != null) {
                  derivedOptions.body = url.body;
                  hasDerivedOptions = true;
                }
                if (url.referrer != null) {
                  derivedOptions.referrer = url.referrer;
                  hasDerivedOptions = true;
                } else if (url.referer != null) {
                  derivedOptions.referer = url.referer;
                  hasDerivedOptions = true;
                }
                if (url.origin != null) {
                  derivedOptions.origin = url.origin;
                  hasDerivedOptions = true;
                }
                if (hasDerivedOptions) {
                  resolvedOptions = derivedOptions;
                }
              }
            }
            if (resolvedUrl == null && resolvedOptions && typeof resolvedOptions.url === "string") {
              resolvedUrl = resolvedOptions.url;
            }
            return { url: resolvedUrl, options: resolvedOptions };
          }
          function fetchApi(url, options) {
            var input = normalizeFetchInput(url, options);
            if (input.url == null) throw new Error("fetchApi requires a URL");
            var requestUrl = String(input.url);
            var payload = JSON.stringify(normalizeInit(input.options));
            var raw = __native.fetch(requestUrl, payload);
            var response = JSON.parse(raw);
            response.__requestUrl = requestUrl;
            response.__requestOptionsPayload = payload;
            if (response.status === 0) {
              throw new Error("Network request failed: " + (response.body || "unknown error"));
            }
            return Promise.resolve(makeResponse(response));
          }
          function fetchText(url, options, encoding) {
            return fetchApi(url, options).then(function(res) { return res.text(); });
          }
          function fetchFile(url, options) {
            return fetchApi(url, options).then(function(res) { return res.text(); });
          }
          function fetchProto(config, url, options) {
            var input = normalizeFetchInput(url, options);
            if (input.url == null) throw new Error("fetchProto requires a URL");
            var configPayload = JSON.stringify(config || {});
            var optionsPayload = input.options == null ? null : JSON.stringify(normalizeInit(input.options));
            var response = __native.fetchProto(String(input.url), configPayload, optionsPayload);
            return Promise.resolve(response ? JSON.parse(response) : null);
          }
          module.exports = { fetchApi: fetchApi, fetchText: fetchText, fetchProto: fetchProto, fetchFile: fetchFile };
        });
    """.trimIndent()

    private val isAbsoluteUrlModule = """
        __defineModule("@libs/isAbsoluteUrl", function(module, exports) {
          function isUrlAbsolute(value) {
            if (value == null) return false;
            return /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(String(value));
          }
          module.exports = { isUrlAbsolute: isUrlAbsolute };
        });
    """.trimIndent()

    private val typesConstantsModule = """
        __defineModule("@/types/constants", function(module, exports) {
          var statusModule = require("@libs/novelStatus");
          var coverModule = require("@libs/defaultCover");
          module.exports = {
            NovelStatus: statusModule.NovelStatus,
            defaultCover: coverModule.defaultCover
          };
        });
    """.trimIndent()

    private val aesModule = """
        __defineModule("@libs/aes", function(module, exports) {
          function toB64(u8) {
            var binary = "";
            for (var i = 0; i < u8.length; i++) binary += String.fromCharCode(u8[i]);
            return btoa(binary);
          }
          function fromB64(b64) {
            var binary = atob(b64);
            var u8 = new Uint8Array(binary.length);
            for (var i = 0; i < binary.length; i++) u8[i] = binary.charCodeAt(i);
            return u8;
          }
          module.exports = {
            gcm: function(keyBytes, ivBytes) {
              return {
                decrypt: function(cipherBytes) {
                  var keyB64 = toB64(keyBytes);
                  var ivB64 = toB64(ivBytes);
                  var cipherB64 = toB64(cipherBytes);
                  var plainB64 = __native.__aesGcmDecrypt(keyB64, ivB64, cipherB64);
                  if (!plainB64) throw new Error("AES-GCM decrypt failed");
                  return fromB64(plainB64);
                }
              };
            }
          };
        });
    """.trimIndent()

    private val urlEncodeModule = """
        __defineModule("urlencode", function(module, exports) {
          module.exports = {
            encode: function(value) { return encodeURIComponent(String(value)); },
            decode: function(value) { return decodeURIComponent(String(value)); }
          };
        });
    """.trimIndent()

    private fun dayjsModule(): String {
        val bundleJs = assetLoader?.invoke("js/dayjs.bundle.js")
        if (bundleJs != null) {
            // IIFE bundle sets var __dayjs = ...; wrap it in __defineModule
            return """
                $bundleJs
                __defineModule("dayjs", function(module, exports) {
                  var d = typeof __dayjs !== "undefined" ? __dayjs : {};
                  var dayjs = d.default || d;
                  dayjs.default = dayjs;
                  module.exports = dayjs;
                });
            """.trimIndent()
        }
        // Fallback stub when no asset loader is available (e.g. unit tests)
        return """
            __defineModule("dayjs", function(module, exports) {
              function dayjs(input) {
                var value = input == null ? "" : String(input);
                return {
                  format: function() { return value; }
                };
              }
              dayjs.default = dayjs;
              module.exports = dayjs;
            });
        """.trimIndent()
    }

    private fun htmlParserModule(): String {
        val bundleJs = assetLoader?.invoke("js/htmlparser2.bundle.js")
        if (bundleJs != null) {
            // IIFE bundle sets var __htmlparser2 = ...; wrap it in __defineModule
            return """
                $bundleJs
                __defineModule("htmlparser2", function(module, exports) {
                  var hp2 = typeof __htmlparser2 !== "undefined" ? __htmlparser2 : {};
                  module.exports = hp2.default || hp2;
                });
            """.trimIndent()
        }
        // Fallback stub
        return """
            __defineModule("htmlparser2", function(module, exports) {
              function Parser(handler) {
                this._handler = handler || {};
                this._buffer = "";
              }
              Parser.prototype.write = function(chunk) {
                this._buffer += chunk || "";
              };
              Parser.prototype.end = function() {
                var html = this._buffer;
                this._buffer = "";
                if (!html) return;
                var handler = this._handler;
                var regex = /<!--[\s\S]*?-->|<\/?[a-zA-Z0-9:-]+(?:\s[^>]*?)?>/g;
                var lastIndex = 0;
                var match;
                while ((match = regex.exec(html)) !== null) {
                  var index = match.index;
                  if (index > lastIndex) {
                    var text = html.slice(lastIndex, index);
                    if (handler.ontext) handler.ontext(text);
                  }
                  var token = match[0];
                  if (token.startsWith("<!--")) {
                    lastIndex = regex.lastIndex;
                    continue;
                  }
                  var isClose = token[1] === "/";
                  var tagContent = token.substring(isClose ? 2 : 1, token.length - 1).trim();
                  var parts = tagContent.split(/\s+/);
                  var tagName = parts[0] || "";
                  if (isClose) {
                    if (handler.onclosetag) handler.onclosetag(tagName);
                    lastIndex = regex.lastIndex;
                    continue;
                  }
                  var attrs = {};
                  var attrRegex = /([^\s=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
                  var attrMatch;
                  while ((attrMatch = attrRegex.exec(tagContent)) !== null) {
                    var attrName = attrMatch[1];
                    if (attrName === tagName) continue;
                    var attrValue = attrMatch[2] || attrMatch[3] || attrMatch[4] || "";
                    attrs[attrName] = attrValue;
                  }
                  if (handler.onopentag) handler.onopentag(tagName, attrs);
                  lastIndex = regex.lastIndex;
                }
                if (lastIndex < html.length && handler.ontext) {
                  handler.ontext(html.slice(lastIndex));
                }
              };
              module.exports = { Parser: Parser };
            });
        """.trimIndent()
    }

    private val cheerioModule = """
        __defineModule("cheerio", function(module, exports) {
          function wrapHandles(handles, _prev) {
            if (!handles || !handles.length) handles = [];
            var api = {
              length: handles.length,
              _handles: handles,
              _prev: _prev || [],
              get attribs() {
                return handles.length ? JSON.parse(__native.domAttrs(handles[0])) : {};
              },
              get tagName() {
                return handles.length ? __native.domTagName(handles[0]) : '';
              },
              toArray: function() {
                var arr = [];
                for (var i = 0; i < handles.length; i++) {
                  var h = handles[i];
                  var isText = __native.domIsTextNode(h);
                  var tagName = isText ? '' : __native.domTagName(h);
                  // 'tag' for real elements, 'text' for text nodes,
                  // 'comment' for comment/other nodes (empty tagName, not text)
                  var type = isText ? 'text' : (tagName !== '' ? 'tag' : 'comment');
                  var obj = {
                    handle: h,
                    type: type,
                    tagName: tagName,
                    name: tagName,
                    attribs: isText ? {} : JSON.parse(__native.domAttrs(h))
                  };
                  if (isText) obj.data = __native.domText(h);
                  arr.push(obj);
                }
                return arr;
              },
              get: function(index) {
                if (index == null) return api.toArray();
                if (index < 0) index = handles.length + index;
                if (!handles[index] && handles[index] !== 0) return undefined;
                return {
                  tagName: __native.domTagName(handles[index]),
                  name: __native.domTagName(handles[index]),
                  attribs: JSON.parse(__native.domAttrs(handles[index]))
                };
              },
              first: function() { return wrapHandles(handles.length > 0 ? [handles[0]] : []); },
              last: function() { return wrapHandles(handles.length > 0 ? [handles[handles.length - 1]] : []); },
              eq: function(index) {
                if (index < 0) index = handles.length + index;
                return wrapHandles(handles[index] != null ? [handles[index]] : []);
              },
              slice: function(start, end) {
                return wrapHandles(handles.slice(start, end));
              },
              text: function() {
                var out = "";
                for (var i = 0; i < handles.length; i++) out += __native.domText(handles[i]);
                return out;
              },
              html: function() {
                if (!handles.length) return null;
                return __native.domHtml(handles[0]);
              },
              xml: function() {
                if (!handles.length) return null;
                return __native.domXml(handles[0]);
              },
              attr: function(name, value) {
                if (!handles.length) return value === undefined ? undefined : api;
                if (value !== undefined) {
                  for (var i = 0; i < handles.length; i++) {
                    __native.domSetAttr(handles[i], String(name), String(value));
                  }
                  return api;
                }
                var val = __native.domAttr(handles[0], String(name));
                return val == null ? undefined : val;
              },
              data: function(key) {
                if (!handles.length) return undefined;
                var val = __native.domData(handles[0], String(key));
                return val == null ? undefined : val;
              },
              val: function() {
                if (!handles.length) return undefined;
                return __native.domVal(handles[0]);
              },
              prop: function(name) {
                if (!handles.length) return undefined;
                if (name === "tagName" || name === "nodeName") return __native.domTagName(handles[0]);
                return api.attr(name);
              },
              hasClass: function(className) {
                if (!handles.length) return false;
                return __native.domHasClass(handles[0], String(className));
              },
              is: function(selector) {
                if (!handles.length) return false;
                return __native.domIs(handles[0], String(selector));
              },
              find: function(selector) {
                var s = String(selector);
                // Jsoup does not support relative CSS combinators like "> a".
                // Use children() which iterates direct child elements and
                // filters by tag/selector via Jsoup's Element.is().
                if (s.indexOf(">") === 0) {
                  var tag = s.replace(/^\s*>\s*/, "");
                  var result = [];
                  for (var i = 0; i < handles.length; i++) {
                    var kids = JSON.parse(__native.domChildren(handles[i], tag || ""));
                    for (var j = 0; j < kids.length; j++) result.push(kids[j]);
                  }
                  return wrapHandles(result, handles.slice());
                }
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var found = JSON.parse(__native.domSelect(handles[i], s));
                  for (var j = 0; j < found.length; j++) result.push(found[j]);
                }
                return wrapHandles(result, handles.slice());
              },
              parent: function(selector) {
                var result = [];
                var seen = {};
                for (var i = 0; i < handles.length; i++) {
                  var p = __native.domParent(handles[i]);
                  if (p >= 0 && !seen[p]) {
                    if (!selector || __native.domIs(p, String(selector))) {
                      result.push(p);
                      seen[p] = true;
                    }
                  }
                }
                return wrapHandles(result, handles.slice());
              },
              parents: function(selector) {
                var result = [];
                var seen = {};
                for (var i = 0; i < handles.length; i++) {
                  var p = __native.domParent(handles[i]);
                  while (p >= 0) {
                    if (!seen[p]) {
                      if (!selector || __native.domIs(p, String(selector))) {
                        result.push(p);
                      }
                      seen[p] = true;
                    }
                    p = __native.domParent(p);
                  }
                }
                return wrapHandles(result, handles.slice());
              },
              children: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var kids = JSON.parse(__native.domChildren(handles[i], selector || null));
                  for (var j = 0; j < kids.length; j++) result.push(kids[j]);
                }
                return wrapHandles(result, handles.slice());
              },
              contents: function() {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var c = JSON.parse(__native.domContents(handles[i]));
                  for (var j = 0; j < c.length; j++) result.push(c[j]);
                }
                return wrapHandles(result, handles.slice());
              },
              next: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var n = __native.domNext(handles[i], selector || null);
                  if (n >= 0) result.push(n);
                }
                return wrapHandles(result, handles.slice());
              },
              nextAll: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var arr = JSON.parse(__native.domNextAll(handles[i], selector || null));
                  for (var j = 0; j < arr.length; j++) result.push(arr[j]);
                }
                return wrapHandles(result, handles.slice());
              },
              prev: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var p = __native.domPrev(handles[i], selector || null);
                  if (p >= 0) result.push(p);
                }
                return wrapHandles(result, handles.slice());
              },
              prevAll: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var arr = JSON.parse(__native.domPrevAll(handles[i], selector || null));
                  for (var j = 0; j < arr.length; j++) result.push(arr[j]);
                }
                return wrapHandles(result, handles.slice());
              },
              siblings: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var arr = JSON.parse(__native.domSiblings(handles[i], selector || null));
                  for (var j = 0; j < arr.length; j++) result.push(arr[j]);
                }
                return wrapHandles(result, handles.slice());
              },
              closest: function(selector) {
                var result = [];
                var seen = {};
                for (var i = 0; i < handles.length; i++) {
                  var c = __native.domClosest(handles[i], String(selector));
                  if (c >= 0 && !seen[c]) {
                    result.push(c);
                    seen[c] = true;
                  }
                }
                return wrapHandles(result, handles.slice());
              },
              has: function(selector) {
                var filtered = [];
                for (var i = 0; i < handles.length; i++) {
                  if (__native.domHas(handles[i], String(selector))) filtered.push(handles[i]);
                }
                return wrapHandles(filtered, handles.slice());
              },
              not: function(selector) {
                if (typeof selector === "function") {
                  var filtered = [];
                  for (var i = 0; i < handles.length; i++) {
                    var wrapped = wrapHandles([handles[i]]);
                    if (!selector.call(wrapped, i, wrapped)) {
                      filtered.push(handles[i]);
                    }
                  }
                  return wrapHandles(filtered, handles.slice());
                }
                var filtered2 = [];
                for (var i = 0; i < handles.length; i++) {
                  if (!__native.domIs(handles[i], String(selector))) filtered2.push(handles[i]);
                }
                return wrapHandles(filtered2, handles.slice());
              },
              filter: function(predicate) {
                if (typeof predicate === "function") {
                  var filtered = [];
                  for (var i = 0; i < handles.length; i++) {
                    var wrapped = wrapHandles([handles[i]]);
                    if (predicate.call(wrapped, i, wrapped)) {
                      filtered.push(handles[i]);
                    }
                  }
                  return wrapHandles(filtered, handles.slice());
                }
                if (typeof predicate === "string") {
                  var filtered2 = [];
                  for (var i = 0; i < handles.length; i++) {
                    if (__native.domIs(handles[i], predicate)) filtered2.push(handles[i]);
                  }
                  return wrapHandles(filtered2, handles.slice());
                }
                return api;
              },
              each: function(fn) {
                for (var i = 0; i < handles.length; i++) {
                  var wrapped = wrapHandles([handles[i]]);
                  var ret = fn.call(wrapped, i, wrapped);
                  if (ret === false) break;
                }
                return api;
              },
              map: function(fn) {
                var mapped = [];
                for (var i = 0; i < handles.length; i++) {
                  var wrapped = wrapHandles([handles[i]]);
                  mapped.push(fn.call(wrapped, i, wrapped));
                }
                return {
                  get: function(index) {
                    if (index == null) return mapped.slice();
                    return mapped[index];
                  },
                  toArray: function() { return mapped.slice(); }
                };
              },
              remove: function() {
                for (var i = 0; i < handles.length; i++) {
                  __native.domRemove(handles[i]);
                }
                handles = [];
                api.length = 0;
                return api;
              },
              replaceWith: function(content) {
                var newHtml = typeof content === "string" ? content : "";
                for (var i = 0; i < handles.length; i++) {
                  __native.domReplaceWith(handles[i], newHtml);
                }
                return api;
              },
              before: function(content) {
                var newHtml = typeof content === "string" ? content : "";
                for (var i = 0; i < handles.length; i++) {
                  __native.domBefore(handles[i], newHtml);
                }
                return api;
              },
              after: function(content) {
                var newHtml = typeof content === "string" ? content : "";
                for (var i = 0; i < handles.length; i++) {
                  __native.domAfter(handles[i], newHtml);
                }
                return api;
              },
              append: function(content) {
                var newHtml = typeof content === "string" ? content : "";
                for (var i = 0; i < handles.length; i++) {
                  __native.domAppend(handles[i], newHtml);
                }
                return api;
              },
              prepend: function(content) {
                var newHtml = typeof content === "string" ? content : "";
                for (var i = 0; i < handles.length; i++) {
                  __native.domPrepend(handles[i], newHtml);
                }
                return api;
              },
              empty: function() {
                for (var i = 0; i < handles.length; i++) {
                  __native.domEmpty(handles[i]);
                }
                return api;
              },
              addClass: function(className) {
                for (var i = 0; i < handles.length; i++) {
                  __native.domAddClass(handles[i], String(className));
                }
                return api;
              },
              removeAttr: function(name) {
                for (var i = 0; i < handles.length; i++) {
                  __native.domRemoveAttr(handles[i], String(name));
                }
                return api;
              },
              removeClass: function(className) {
                for (var i = 0; i < handles.length; i++) {
                  __native.domRemoveClass(handles[i], String(className));
                }
                return api;
              },
              clone: function() {
                // Clone via outerHtml re-parse: returns new handles
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var outer = __native.domOuterHtml(handles[i]);
                  var docH = __native.domLoad(outer);
                  var kids = JSON.parse(__native.domChildren(docH, null));
                  for (var j = 0; j < kids.length; j++) result.push(kids[j]);
                }
                return wrapHandles(result);
              },
              index: function(el) {
                if (!arguments.length) {
                  // Index within parent
                  if (!handles.length) return -1;
                  var sibs = JSON.parse(__native.domSiblings(handles[0], null));
                  var p = __native.domParent(handles[0]);
                  if (p < 0) return -1;
                  var allKids = JSON.parse(__native.domChildren(p, null));
                  for (var i = 0; i < allKids.length; i++) {
                    if (allKids[i] === handles[0]) return i;
                  }
                  return -1;
                }
                return -1;
              },
              add: function(other) {
                var otherHandles = [];
                if (other && other._handles) otherHandles = other._handles;
                return wrapHandles(handles.concat(otherHandles));
              },
              end: function() {
                return _prev && _prev.length ? wrapHandles(_prev) : wrapHandles([]);
              },
              addBack: function() {
                return wrapHandles(handles.concat(_prev || []));
              },
              andSelf: function() {
                return wrapHandles(handles.concat(_prev || []));
              }
            };
            // Array-like index access (e.g., children()[0], siblings()[1])
            // Cheerio plugins expect [0] to return a DOM node with .attribs
            for (var i = 0; i < handles.length; i++) {
              (function(idx) {
                Object.defineProperty(api, String(idx), {
                  get: function() {
                    if (!handles[idx] && handles[idx] !== 0) return undefined;
                    return {
                      tagName: __native.domTagName(handles[idx]),
                      name: __native.domTagName(handles[idx]),
                      attribs: JSON.parse(__native.domAttrs(handles[idx]))
                    };
                  },
                  enumerable: true,
                  configurable: true
                });
              })(i);
            }
            return api;
          }

          function load(html) {
            var rootHandle = __native.domLoad(String(html || ""));
            function $(selector) {
              if (typeof selector === "string") {
                return wrapHandles(JSON.parse(__native.domSelect(rootHandle, selector)));
              }
              if (selector && typeof selector === "object" && selector._handles) {
                return selector;
              }
              if (typeof selector === "number") {
                return wrapHandles([selector]);
              }
              if (selector && typeof selector === "object" && typeof selector.handle === "number") {
                return wrapHandles([selector.handle]);
              }
              return wrapHandles([]);
            }
            $.text = function(selector) {
              if (typeof selector === "string") return $(selector).text();
              return __native.domText(rootHandle);
            };
            $.html = function(selector) {
              if (selector == null) return __native.domHtml(rootHandle);
              if (typeof selector === "string") return $(selector).html();
              // If given a wrapped cheerio object, return the outerHtml of its first element.
              // If given a raw node object (from toArray()), return its outerHtml.
              // This matches real cheerio's $.html(element) behaviour.
              if (selector && typeof selector === "object") {
                if (selector._handles && selector._handles.length) return __native.domOuterHtml(selector._handles[0]);
                if (typeof selector.handle === "number") return __native.domOuterHtml(selector.handle);
              }
              return __native.domHtml(rootHandle);
            };
            $.xml = function(selector) {
              if (selector == null) return __native.domXml(rootHandle);
              if (typeof selector === "string") return $(selector).xml();
              if (selector && typeof selector === "object") {
                if (selector._handles && selector._handles.length) return __native.domXml(selector._handles[0]);
                if (typeof selector.handle === "number") return __native.domXml(selector.handle);
              }
              return __native.domXml(rootHandle);
            };
            $.find = function(selector) { return $(selector); };
            $.root = function() { return wrapHandles([rootHandle]); };
            return $;
          }

          module.exports = { load: load };
        });
    """.trimIndent()

    private fun stubModule(name: String): String {
        return """
            __defineModule("$name", function(module, exports) {
              module.exports = function() { throw new Error("$name module is not bundled"); };
            });
        """.trimIndent()
    }
}
