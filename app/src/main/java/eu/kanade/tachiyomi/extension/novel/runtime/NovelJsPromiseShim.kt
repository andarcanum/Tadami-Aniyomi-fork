package eu.kanade.tachiyomi.extension.novel.runtime

object NovelJsPromiseShim {
    val script = """
        (function(global) {
          function ImmediatePromise(executor) {
            var state = "pending";
            var value;
            var handlers = [];

            function fulfill(val) {
              if (state !== "pending") return;
              if (val && typeof val.then === "function") {
                return val.then(fulfill, reject);
              }
              state = "fulfilled";
              value = val;
              handlers.forEach(function(handler) { handler.onFulfilled(value); });
            }

            function reject(err) {
              if (state !== "pending") return;
              state = "rejected";
              value = err;
              handlers.forEach(function(handler) { handler.onRejected(value); });
            }

            this.then = function(onFulfilled, onRejected) {
              return new ImmediatePromise(function(resolve, reject) {
                function handleFulfilled(val) {
                  try {
                    resolve(onFulfilled ? onFulfilled(val) : val);
                  } catch (e) {
                    reject(e);
                  }
                }
                function handleRejected(err) {
                  if (!onRejected) {
                    reject(err);
                    return;
                  }
                  try {
                    resolve(onRejected(err));
                  } catch (e) {
                    reject(e);
                  }
                }
                if (state === "pending") {
                  handlers.push({ onFulfilled: handleFulfilled, onRejected: handleRejected });
                } else if (state === "fulfilled") {
                  handleFulfilled(value);
                } else {
                  handleRejected(value);
                }
              });
            };

            this.catch = function(onRejected) {
              return this.then(null, onRejected);
            };

            try {
              executor(fulfill, reject);
            } catch (e) {
              reject(e);
            }
          }

          ImmediatePromise.resolve = function(value) {
            return new ImmediatePromise(function(resolve) { resolve(value); });
          };

          ImmediatePromise.reject = function(error) {
            return new ImmediatePromise(function(_, reject) { reject(error); });
          };

          ImmediatePromise.all = function(values) {
            return new ImmediatePromise(function(resolve, reject) {
              var remaining = values.length;
              if (!remaining) return resolve([]);
              var results = new Array(values.length);
              values.forEach(function(value, index) {
                ImmediatePromise.resolve(value).then(function(resolved) {
                  results[index] = resolved;
                  remaining -= 1;
                  if (remaining === 0) resolve(results);
                }, reject);
              });
            });
          };

          global.__resolve = function(value) {
            if (value && typeof value.then === "function") {
              var result;
              var error;
              var done = false;
              value.then(
                function(v) { result = v; done = true; },
                function(e) { error = e; done = true; }
              );
              if (!done) throw new Error("Async result not supported");
              if (error) throw error;
              return result;
            }
            return value;
          };

          global.Promise = ImmediatePromise;
        })(this);
    """.trimIndent()
}
