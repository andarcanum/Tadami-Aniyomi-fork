package eu.kanade.tachiyomi.extension.novel.runtime

object NovelJsPromiseShim {
    val script = """
        (function(global) {
          // QuickJS doesn't provide an event loop. LNReader-style plugins rely on TS __awaiter which
          // expects async Promise chaining. If we execute `.then()` handlers synchronously, deep
          // await chains recurse and can overflow the stack. We simulate microtasks with a job queue
          // and drain it iteratively from __resolve().
          var __jobQueue = [];

          function __queueJob(fn) {
            if (typeof fn !== "function") return;
            __jobQueue.push(fn);
          }

          function __drainJobs(maxJobs) {
            var limit = typeof maxJobs === "number" ? maxJobs : 1000;
            var count = 0;
            while (__jobQueue.length > 0 && count < limit) {
              var job = __jobQueue.shift();
              try { job(); } catch (e) { throw e; }
              count += 1;
            }
            return count;
          }

          global.__queueJob = __queueJob;

          // Basic setTimeout shim used by plugins for sleep(). Delay is ignored but scheduling is async.
          global.setTimeout = function(fn, delay) {
            __queueJob(fn);
            return 0;
          };

          function ImmediatePromise(executor) {
            var state = "pending";
            var value;
            var handlers = [];

            function callHandler(handler) {
              __queueJob(function() {
                if (state === "fulfilled") handler.onFulfilled(value);
                else if (state === "rejected") handler.onRejected(value);
              });
            }

            function fulfill(val) {
              if (state !== "pending") return;
              if (val && typeof val.then === "function") {
                // Assimilate thenables/promises without recursing on the JS stack.
                return val.then(fulfill, reject);
              }
              state = "fulfilled";
              value = val;
              for (var i = 0; i < handlers.length; i++) callHandler(handlers[i]);
            }

            function reject(err) {
              if (state !== "pending") return;
              state = "rejected";
              value = err;
              for (var i = 0; i < handlers.length; i++) callHandler(handlers[i]);
            }

            this.then = function(onFulfilled, onRejected) {
              return new ImmediatePromise(function(resolve, reject) {
                function handleFulfilled(val) {
                  try { resolve(onFulfilled ? onFulfilled(val) : val); } catch (e) { reject(e); }
                }
                function handleRejected(err) {
                  if (!onRejected) { reject(err); return; }
                  try { resolve(onRejected(err)); } catch (e) { reject(e); }
                }
                var handler = { onFulfilled: handleFulfilled, onRejected: handleRejected };
                if (state === "pending") {
                  handlers.push(handler);
                } else {
                  callHandler(handler);
                }
              });
            };

            this.catch = function(onRejected) {
              return this.then(null, onRejected);
            };

            try { executor(fulfill, reject); } catch (e) { reject(e); }
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
            if (!value || typeof value.then !== "function") return value;

            var result;
            var error;
            var done = false;
            value.then(
              function(v) { result = v; done = true; },
              function(e) { error = e; done = true; }
            );

            // Drain queued microtasks until the Promise settles (or we hit a safety limit).
            var safety = 200000; // max jobs to run in one __resolve()
            while (!done && __jobQueue.length > 0 && safety > 0) {
              var ran = __drainJobs(1000);
              safety -= ran;
              if (ran === 0) break;
            }

            if (!done) throw new Error("Async result not supported");
            if (error) throw error;
            return result;
          };

          global.Promise = ImmediatePromise;
        })(this);
    """.trimIndent()
}
