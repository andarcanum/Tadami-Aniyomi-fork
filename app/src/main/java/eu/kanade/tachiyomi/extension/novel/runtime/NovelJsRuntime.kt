package eu.kanade.tachiyomi.extension.novel.runtime

import app.cash.quickjs.QuickJs
import java.io.Closeable

class NovelJsRuntime(
    private val pluginId: String,
    private val nativeApi: NativeApi,
    private val moduleRegistry: NovelJsModuleRegistry = NovelJsModuleRegistry(),
) : Closeable {

    private val quickJs: QuickJs = QuickJs.create().apply {
        set(NATIVE_OBJECT_NAME, NativeApi::class.java, nativeApi)
        evaluate(bootstrapScript, "novel-js-runtime-bootstrap.js")
        moduleRegistry.registerModules(this)
    }

    fun evaluate(script: String, fileName: String = "novel-plugin.js"): Any? {
        return quickJs.evaluate(script, fileName)
    }

    override fun close() {
        quickJs.close()
    }

    interface NativeApi {
        fun fetch(url: String, optionsJson: String?): String
        fun storageGet(key: String): String?
        fun storageSet(key: String, value: String)
        fun storageRemove(key: String)
        fun storageClear()
        fun storageKeys(): String
        fun resolveUrl(url: String, base: String?): String
        fun getPathname(url: String): String
        fun select(html: String, selector: String): String
    }

    companion object {
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
                  global.setTimeout = function(fn, delay) {
                    if (typeof fn === "function") fn();
                    return 0;
                  };
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
                  global.URL = URL;
                })(this);
            """.trimIndent(),
        ).joinToString("\n")
    }
}

class NovelJsModuleRegistry {
    fun registerModules(quickJs: QuickJs) {
        modules().forEach { module ->
            quickJs.evaluate(module.script, module.name)
        }
    }

    fun modules(): List<NovelJsModule> {
        return listOf(
            NovelJsModule("novelStatus.js", novelStatusModule),
            NovelJsModule("storage.js", storageModule),
            NovelJsModule("filterInputs.js", filterInputsModule),
            NovelJsModule("defaultCover.js", defaultCoverModule),
            NovelJsModule("fetch.js", fetchModule),
            NovelJsModule("urlencode.js", urlEncodeModule),
            NovelJsModule("cheerio.js", cheerioModule),
            NovelJsModule("htmlparser2.js", htmlParserModule),
            NovelJsModule("dayjs.js", dayjsModule),
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

    private val fetchModule = """
        __defineModule("@libs/fetch", function(module, exports) {
          function immediate(value) {
            return {
              then: function(onFulfilled, onRejected) {
                try {
                  if (onFulfilled) return immediate(onFulfilled(value));
                  return immediate(value);
                } catch (e) {
                  if (onRejected) return immediate(onRejected(e));
                  throw e;
                }
              },
              catch: function(onRejected) { return this.then(null, onRejected); }
            };
          }
          function makeResponse(response) {
            return {
              ok: response.status >= 200 && response.status < 300,
              status: response.status,
              url: response.url || "",
              headers: response.headers || {},
              text: function() { return immediate(response.body || ""); },
              json: function() { return immediate(response.body ? JSON.parse(response.body) : null); }
            };
          }
          function normalizeInit(init) {
            if (!init) return { method: "GET", headers: {}, bodyType: "none", body: null };
            var bodyType = "none";
            var body = null;
            var formEntries = null;
            if (init.body != null) {
              if (init.body && init.body.__formDataEntries) {
                bodyType = "form";
                formEntries = init.body.__formDataEntries;
              } else {
                bodyType = "text";
                body = String(init.body);
              }
            }
            return {
              method: init.method || "GET",
              headers: init.headers || {},
              bodyType: bodyType,
              body: body,
              formEntries: formEntries
            };
          }
          function fetchApi(url, options) {
            var payload = JSON.stringify(normalizeInit(options));
            var response = JSON.parse(__native.fetch(String(url), payload));
            return immediate(makeResponse(response));
          }
          function fetchText(url, options, encoding) {
            return fetchApi(url, options).then(function(res) { return res.text(); });
          }
          function fetchFile(url, options) {
            return fetchApi(url, options).then(function(res) { return res.text(); });
          }
          function fetchProto() {
            throw new Error("fetchProto is not supported");
          }
          module.exports = { fetchApi: fetchApi, fetchText: fetchText, fetchProto: fetchProto, fetchFile: fetchFile };
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

    private val dayjsModule = """
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

    private val htmlParserModule = """
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

    private val cheerioModule = """
        __defineModule("cheerio", function(module, exports) {
          function clone(node) {
            return {
              html: node && node.html ? String(node.html) : "",
              text: node && node.text ? String(node.text) : "",
              attrs: node && node.attrs ? node.attrs : {}
            };
          }

          function wrap(nodes) {
            var list = (nodes || []).map(clone);
            var api = function(selector) {
              if (typeof selector === "string") {
                var selected = [];
                for (var i = 0; i < list.length; i++) {
                  selected = selected.concat(JSON.parse(__native.select(list[i].html || "", selector)));
                }
                return wrap(selected);
              }
              if (selector && selector.html) return wrap([selector]);
              if (Array.isArray(selector)) return wrap(selector);
              return wrap([]);
            };

            api.length = list.length;
            api.toArray = function() { return list.map(clone); };
            api.get = function(index) {
              if (index == null) return list.map(clone);
              return list[index] ? clone(list[index]) : undefined;
            };
            api.first = function() { return wrap(list.length > 0 ? [list[0]] : []); };
            api.eq = function(index) { return wrap(list[index] ? [list[index]] : []); };
            api.text = function() {
              var out = "";
              for (var i = 0; i < list.length; i++) out += (list[i].text || "");
              return out;
            };
            api.html = function() { return list[0] ? (list[0].html || "") : null; };
            api.attr = function(name) {
              if (!list[0] || !list[0].attrs) return undefined;
              return list[0].attrs[name];
            };
            api.find = function(selector) {
              var selected = [];
              for (var i = 0; i < list.length; i++) {
                selected = selected.concat(JSON.parse(__native.select(list[i].html || "", selector)));
              }
              return wrap(selected);
            };
            api.each = function(fn) {
              for (var i = 0; i < list.length; i++) {
                fn.call(list[i], i, clone(list[i]));
              }
              return api;
            };
            api.map = function(fn) {
              var mapped = [];
              for (var i = 0; i < list.length; i++) {
                mapped.push(fn.call(list[i], i, clone(list[i])));
              }
              return {
                get: function() { return mapped; }
              };
            };
            return api;
          }

          function load(html) {
            var rootHtml = String(html || "");
            function $(selector) {
              if (typeof selector === "string") {
                return wrap(JSON.parse(__native.select(rootHtml, selector)));
              }
              if (selector && selector.html) return wrap([selector]);
              if (Array.isArray(selector)) return wrap(selector);
              return wrap([]);
            }
            $.root = function() { return wrap([{ html: rootHtml, text: "", attrs: {} }]); };
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
