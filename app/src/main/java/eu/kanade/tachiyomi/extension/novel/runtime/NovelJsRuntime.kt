package eu.kanade.tachiyomi.extension.novel.runtime

import android.util.Log
import com.eclipsesource.v8.JavaCallback
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.V8Value
import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class NovelJsRuntime(
    private val pluginId: String,
    private val nativeApi: NativeApi,
    private val moduleRegistry: NovelJsModuleRegistry = NovelJsModuleRegistry(),
) : Closeable {

    private val runtimeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NovelJsRuntime-$pluginId").apply { isDaemon = true }
    }

    private val v8: V8 = runOnRuntimeThread {
        V8.createV8Runtime().apply {
            Log.i(LOG_TAG, "Using J2V8 runtime for plugin id=$pluginId")
            bindNativeApi(this)
            executeVoidScript(bootstrapScript, "novel-js-runtime-bootstrap.js", 0)
            moduleRegistry.registerModules(this)
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
        runOnRuntimeThread {
            nativeApi.domReleaseAll()
            v8.release(true)
            Unit
        }
        runtimeExecutor.shutdown()
    }

    interface NativeApi {
        fun fetch(url: String, optionsJson: String?): String
        fun fetchProto(url: String, configJson: String, optionsJson: String?): String
        fun storageGet(key: String): String?
        fun storageSet(key: String, value: String)
        fun storageRemove(key: String)
        fun storageClear()
        fun storageKeys(): String
        fun resolveUrl(url: String, base: String?): String
        fun getPathname(url: String): String
        fun select(html: String, selector: String): String

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
        fun domText(handle: Int): String
        fun domAttr(handle: Int, name: String): String?
        fun domAttrs(handle: Int): String
        fun domHasClass(handle: Int, className: String): Boolean
        fun domData(handle: Int, key: String): String?
        fun domVal(handle: Int): String?
        fun domTagName(handle: Int): String
        fun domIsTextNode(handle: Int): Boolean
        fun domReplaceWith(handle: Int, html: String)
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

    private fun bindNativeApi(runtime: V8) {
        val nativeObject = V8Object(runtime)

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.fetch(
                    parameters.stringArg(0),
                    parameters.stringArgOrNull(1),
                )
            },
            "fetch",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.fetchProto(
                    parameters.stringArg(0),
                    parameters.stringArg(1),
                    parameters.stringArgOrNull(2),
                )
            },
            "fetchProto",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.storageGet(parameters.stringArg(0))
            },
            "storageGet",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.storageSet(parameters.stringArg(0), parameters.stringArg(1))
                null
            },
            "storageSet",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.storageRemove(parameters.stringArg(0))
                null
            },
            "storageRemove",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, _ ->
                nativeApi.storageClear()
                null
            },
            "storageClear",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, _ ->
                nativeApi.storageKeys()
            },
            "storageKeys",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.resolveUrl(
                    parameters.stringArg(0),
                    parameters.stringArgOrNull(1),
                )
            },
            "resolveUrl",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.getPathname(parameters.stringArg(0))
            },
            "getPathname",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.select(
                    parameters.stringArg(0),
                    parameters.stringArg(1),
                )
            },
            "select",
        )

        // DOM Store methods
        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domLoad(parameters.stringArg(0))
            },
            "domLoad",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domSelect(parameters.intArg(0), parameters.stringArg(1))
            },
            "domSelect",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domParent(parameters.intArg(0))
            },
            "domParent",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domChildren(parameters.intArg(0), parameters.stringArgOrNull(1))
            },
            "domChildren",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domNext(parameters.intArg(0), parameters.stringArgOrNull(1))
            },
            "domNext",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domPrev(parameters.intArg(0), parameters.stringArgOrNull(1))
            },
            "domPrev",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domNextAll(parameters.intArg(0), parameters.stringArgOrNull(1))
            },
            "domNextAll",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domPrevAll(parameters.intArg(0), parameters.stringArgOrNull(1))
            },
            "domPrevAll",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domSiblings(parameters.intArg(0), parameters.stringArgOrNull(1))
            },
            "domSiblings",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domClosest(parameters.intArg(0), parameters.stringArg(1))
            },
            "domClosest",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domContents(parameters.intArg(0))
            },
            "domContents",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domIs(parameters.intArg(0), parameters.stringArg(1))
            },
            "domIs",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domHas(parameters.intArg(0), parameters.stringArg(1))
            },
            "domHas",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domNot(parameters.intArg(0), parameters.stringArg(1))
            },
            "domNot",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domHtml(parameters.intArg(0))
            },
            "domHtml",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domOuterHtml(parameters.intArg(0))
            },
            "domOuterHtml",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domText(parameters.intArg(0))
            },
            "domText",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domAttr(parameters.intArg(0), parameters.stringArg(1))
            },
            "domAttr",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domAttrs(parameters.intArg(0))
            },
            "domAttrs",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domHasClass(parameters.intArg(0), parameters.stringArg(1))
            },
            "domHasClass",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domData(parameters.intArg(0), parameters.stringArg(1))
            },
            "domData",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domVal(parameters.intArg(0))
            },
            "domVal",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domTagName(parameters.intArg(0))
            },
            "domTagName",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domIsTextNode(parameters.intArg(0))
            },
            "domIsTextNode",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domReplaceWith(parameters.intArg(0), parameters.stringArg(1))
                null
            },
            "domReplaceWith",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domRemove(parameters.intArg(0))
                null
            },
            "domRemove",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domAddClass(parameters.intArg(0), parameters.stringArg(1))
                null
            },
            "domAddClass",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domRemoveClass(parameters.intArg(0), parameters.stringArg(1))
                null
            },
            "domRemoveClass",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.domRelease(parameters.intArg(0))
                null
            },
            "domRelease",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, _ ->
                nativeApi.domReleaseAll()
                null
            },
            "domReleaseAll",
        )

        // Console methods
        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.consoleLog(parameters.stringArg(0))
                null
            },
            "consoleLog",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.consoleError(parameters.stringArg(0))
                null
            },
            "consoleError",
        )

        nativeObject.registerJavaMethod(
            JavaCallback { _, parameters ->
                nativeApi.consoleWarn(parameters.stringArg(0))
                null
            },
            "consoleWarn",
        )

        runtime.add(NATIVE_OBJECT_NAME, nativeObject)
        nativeObject.release()
    }

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

    private fun V8Array.stringArg(index: Int): String {
        return stringArgOrNull(index).orEmpty()
    }

    private fun V8Array.stringArgOrNull(index: Int): String? {
        if (index >= length()) return null
        val value = runCatching { get(index) }.getOrNull()
        return when (value) {
            null -> null
            is String -> value
            is V8Value -> {
                value.release()
                null
            }
            else -> value.toString()
        }
    }

    private fun V8Array.intArg(index: Int): Int {
        if (index >= length()) return 0
        val value = runCatching { get(index) }.getOrNull()
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            is V8Value -> { value.release(); 0 }
            else -> 0
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
                  function URL(input, base) {
                    var resolved = __native.resolveUrl(String(input), base != null ? String(base) : null);
                    this.href = resolved;
                    this.pathname = __native.getPathname(resolved);
                  }
                  URL.prototype.toString = function() {
                    return this.href;
                  };
                  URL.prototype.valueOf = function() {
                    return this.href;
                  };
                  URL.prototype.toJSON = function() {
                    return this.href;
                  };
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
          function now() { return Date.now(); }
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
          var localStorage = { get: function() { return {}; } };
          var sessionStorage = { get: function() { return {}; } };
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
            return {
              ok: response.status >= 200 && response.status < 300,
              status: response.status,
              url: response.url || "",
              headers: response.headers || {},
              text: function() { return Promise.resolve(response.body || ""); },
              json: function() { return Promise.resolve(response.body ? JSON.parse(response.body) : null); },
              arrayBuffer: function() { return Promise.resolve(decodeBase64ToArrayBuffer(response.bodyBase64 || "")); }
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
            var payload = JSON.stringify(normalizeInit(input.options));
            var response = JSON.parse(__native.fetch(String(input.url), payload));
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
          function wrapHandles(handles) {
            if (!handles || !handles.length) handles = [];
            var api = {
              length: handles.length,
              _handles: handles,
              toArray: function() {
                var arr = [];
                for (var i = 0; i < handles.length; i++) {
                  arr.push({
                    tagName: __native.domTagName(handles[i]),
                    attribs: JSON.parse(__native.domAttrs(handles[i]))
                  });
                }
                return arr;
              },
              get: function(index) {
                if (index == null) return api.toArray();
                if (index < 0) index = handles.length + index;
                if (!handles[index] && handles[index] !== 0) return undefined;
                return {
                  tagName: __native.domTagName(handles[index]),
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
              attr: function(name) {
                if (!handles.length) return undefined;
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
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var found = JSON.parse(__native.domSelect(handles[i], String(selector)));
                  for (var j = 0; j < found.length; j++) result.push(found[j]);
                }
                return wrapHandles(result);
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
                return wrapHandles(result);
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
                return wrapHandles(result);
              },
              children: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var kids = JSON.parse(__native.domChildren(handles[i], selector || null));
                  for (var j = 0; j < kids.length; j++) result.push(kids[j]);
                }
                return wrapHandles(result);
              },
              contents: function() {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var c = JSON.parse(__native.domContents(handles[i]));
                  for (var j = 0; j < c.length; j++) result.push(c[j]);
                }
                return wrapHandles(result);
              },
              next: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var n = __native.domNext(handles[i], selector || null);
                  if (n >= 0) result.push(n);
                }
                return wrapHandles(result);
              },
              nextAll: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var arr = JSON.parse(__native.domNextAll(handles[i], selector || null));
                  for (var j = 0; j < arr.length; j++) result.push(arr[j]);
                }
                return wrapHandles(result);
              },
              prev: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var p = __native.domPrev(handles[i], selector || null);
                  if (p >= 0) result.push(p);
                }
                return wrapHandles(result);
              },
              prevAll: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var arr = JSON.parse(__native.domPrevAll(handles[i], selector || null));
                  for (var j = 0; j < arr.length; j++) result.push(arr[j]);
                }
                return wrapHandles(result);
              },
              siblings: function(selector) {
                var result = [];
                for (var i = 0; i < handles.length; i++) {
                  var arr = JSON.parse(__native.domSiblings(handles[i], selector || null));
                  for (var j = 0; j < arr.length; j++) result.push(arr[j]);
                }
                return wrapHandles(result);
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
                return wrapHandles(result);
              },
              has: function(selector) {
                var filtered = [];
                for (var i = 0; i < handles.length; i++) {
                  if (__native.domHas(handles[i], String(selector))) filtered.push(handles[i]);
                }
                return wrapHandles(filtered);
              },
              not: function(selector) {
                if (typeof selector === "function") {
                  var filtered = [];
                  for (var i = 0; i < handles.length; i++) {
                    if (!selector.call(null, i, wrapHandles([handles[i]]))) {
                      filtered.push(handles[i]);
                    }
                  }
                  return wrapHandles(filtered);
                }
                var filtered2 = [];
                for (var i = 0; i < handles.length; i++) {
                  if (!__native.domIs(handles[i], String(selector))) filtered2.push(handles[i]);
                }
                return wrapHandles(filtered2);
              },
              filter: function(predicate) {
                if (typeof predicate === "function") {
                  var filtered = [];
                  for (var i = 0; i < handles.length; i++) {
                    if (predicate.call(null, i, wrapHandles([handles[i]]))) {
                      filtered.push(handles[i]);
                    }
                  }
                  return wrapHandles(filtered);
                }
                if (typeof predicate === "string") {
                  var filtered2 = [];
                  for (var i = 0; i < handles.length; i++) {
                    if (__native.domIs(handles[i], predicate)) filtered2.push(handles[i]);
                  }
                  return wrapHandles(filtered2);
                }
                return api;
              },
              each: function(fn) {
                for (var i = 0; i < handles.length; i++) {
                  var ret = fn.call(null, i, wrapHandles([handles[i]]));
                  if (ret === false) break;
                }
                return api;
              },
              map: function(fn) {
                var mapped = [];
                for (var i = 0; i < handles.length; i++) {
                  mapped.push(fn.call(null, i, wrapHandles([handles[i]])));
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
              addClass: function(className) {
                for (var i = 0; i < handles.length; i++) {
                  __native.domAddClass(handles[i], String(className));
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
                return wrapHandles([]);
              }
            };
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
              return wrapHandles([]);
            }
            $.text = function(selector) {
              if (typeof selector === "string") return $(selector).text();
              return __native.domText(rootHandle);
            };
            $.html = function(selector) {
              if (typeof selector === "string") return $(selector).html();
              return __native.domHtml(rootHandle);
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
