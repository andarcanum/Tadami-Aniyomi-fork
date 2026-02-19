var GLOBALS = {
	ac_areas: {
		ac_read: "войти", ac_trread: "читать переводы", ac_gen: "скачивать", ac_rate: "оценивать", ac_comment: "комментировать", ac_tr: "переводить",
		ac_blog_r: "читать блог", ac_blog_c: "комментировать в блоге", ac_blog_w: "писать посты в блоге",
		ac_announce: "создавать анонсы перевода", ac_membership: "управлять членством в группе перевода", ac_chap_edit: "редактировать оригинал", ac_book_edit: "редактировать описание перевода"
	},
	ac_areas_chap: {ac_read: "читать", ac_trread: "читать переводы", ac_gen: "скачивать", ac_rate: "оценивать", ac_comment: "комментировать", ac_tr: "переводить"},
	ac_roles: {a: "все", g: "группа", m: "модераторы", o: "никто"},
	ac_roles_title: {a: "все", g: "только члены группы перевода", m: "только модераторы", o: "только владелец"},
	translation_statuses: {0: "не определён", 1: "идёт перевод", 2: "перевод редактируется", 3: "перевод готов"}
};

var Global = {
	login: function() {
		$("#header-login form [name=User\\[login\\]]").focus();
		return false;
	},
	cururl: function() {
		return location.protocol + "//" + location.host + location.pathname + location.search;
	},
	rusEnding: function(n, t1, t2, t5) {
		if(n >= 11 && n <= 19) return t5;
		n = n % 10;
		if(n == 1) return t1;
		if(n >= 2 && n <= 4) return t2;
		return t5;
	}
};

/* Common */
function options(obj) {
	var html = "";
	for(var i in obj) html += "<option value='" + i + "'>" + obj[i] + "</option>";
	return html;
}


function htmlspecialchars(unsafe) {
	return unsafe.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

/* Расширяем jQuery */
(function($) {
	$.postJSON = function(url, data, callback) {
		$.ajax({
			url: url,
			type: 'POST',
			data: data,
			dataType: "json",
			success: callback,
			error: function(x, t, e) { alert("Ошибка связи. Попробуйте ещё раз.\n(" + t + ")"); }
		});
	}
    $.fn.outerHTML = function(s) {
        return s
            ? this.before(s).remove()
            : jQuery("<p>").append(this.eq(0).clone()).html();
    };
})(jQuery);

/*! Copyright (c) 2011 Brandon Aaron (http://brandonaaron.net)
 * Licensed under the MIT License (LICENSE.txt).
 *
 * Thanks to: http://adomas.org/javascript-mouse-wheel/ for some pointers.
 * Thanks to: Mathias Bank(http://www.mathias-bank.de) for a scope bug fix.
 * Thanks to: Seamus Leahy for adding deltaX and deltaY
 *
 * Version: 3.0.6
 *
 * Requires: 1.2.2+
 */
(function(a){function d(b){var c=b||window.event,d=[].slice.call(arguments,1),e=0,f=!0,g=0,h=0;return b=a.event.fix(c),b.type="mousewheel",c.wheelDelta&&(e=c.wheelDelta/120),c.detail&&(e=-c.detail/3),h=e,c.axis!==undefined&&c.axis===c.HORIZONTAL_AXIS&&(h=0,g=-1*e),c.wheelDeltaY!==undefined&&(h=c.wheelDeltaY/120),c.wheelDeltaX!==undefined&&(g=-1*c.wheelDeltaX/120),d.unshift(b,e,g,h),(a.event.dispatch||a.event.handle).apply(this,d)}var b=["DOMMouseScroll","mousewheel"];if(a.event.fixHooks)for(var c=b.length;c;)a.event.fixHooks[b[--c]]=a.event.mouseHooks;a.event.special.mousewheel={setup:function(){if(this.addEventListener)for(var a=b.length;a;)this.addEventListener(b[--a],d,!1);else this.onmousewheel=d},teardown:function(){if(this.removeEventListener)for(var a=b.length;a;)this.removeEventListener(b[--a],d,!1);else this.onmousewheel=null}},a.fn.extend({mousewheel:function(a){return a?this.bind("mousewheel",a):this.trigger("mousewheel")},unmousewheel:function(a){return this.unbind("mousewheel",a)}})})(jQuery);

/*
 * Copyright 2011 Andrey “A.I.” Sitnik <andrey@sitnik.ru>,
 * sponsored by Evil Martians.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
;(function (undefined) {
	"use strict";

	var defined = function (variable) {
		return (variable != undefined);
	};

	var self = window.Visibility = {
		onVisible: function (callback) {
			if ( !self.isSupported() || !self.hidden() ) {
				callback();
				return self.isSupported();
			}

			var listener = self.change(function (e, state) {
				if ( !self.hidden() ) {
					self.unbind(listener);
					callback();
				}
			});
			return listener;
		},
		change: function (callback) {
			if ( !self.isSupported() ) {
				return false;
			}
			self._lastCallback += 1;
			var number = self._lastCallback;
			self._callbacks[number] = callback;
			self._setListener();
			return number;
		},
		unbind: function (id) {
			delete self._callbacks[id];
		},
		afterPrerendering: function (callback) {
			if ( !self.isSupported() || 'prerender' != self.state() ) {
				callback();
				return self.isSupported();
			}

			var listener = self.change(function (e, state) {
				if ( 'prerender' != state ) {
					self.unbind(listener);
					callback();
				}
			});
			return listener;
		},
		hidden: function () {
			return self._prop('hidden', false);
		},
		state: function () {
			return self._prop('visibilityState', 'visible');
		},
		isSupported: function () {
			return defined(self._prefix());
		},
		_doc: window.document,
		_prefixes: ['webkit', 'moz'],
		_chechedPrefix: null,
		_listening: false,
		_lastCallback: -1,
		_callbacks: { },
		_hiddenBefore: false,
		_init: function () {
			self._hiddenBefore = self.hidden();
		},
		_prefix: function () {
			if ( null !== self._chechedPrefix ) {
				return self._chechedPrefix;
			}
			if ( defined(self._doc.visibilityState) ) {
				return self._chechedPrefix = '';
			}
			var name;
			for ( var i = 0; i < self._prefixes.length; i++ ) {
				name = self._prefixes[i] + 'VisibilityState';
				if ( defined(self._doc[name]) ) {
					return self._chechedPrefix = self._prefixes[i];
				}
			}
		},
		_name: function (name) {
			var prefix = self._prefix();
			if ( '' == prefix ) {
				return name;
			} else {
				return prefix +
					name.substr(0, 1).toUpperCase() + name.substr(1);
			}
		},
		_prop: function (name, unsupported) {
			if ( !self.isSupported() ) {
				return unsupported;
			}
			return self._doc[self._name(name)];
		},
		_onChange: function(event) {
			var state = self.state();

			for ( var i in self._callbacks ) {
				self._callbacks[i].call(self._doc, event, state);
			}

			self._hiddenBefore = self.hidden();
		},
		_setListener: function () {
			if ( self._listening ) {
				return;
			}
			var event = self._prefix() + 'visibilitychange';
			var listener = function () {
				self._onChange.apply(Visibility, arguments);
			};
			if ( self._doc.addEventListener ) {
				self._doc.addEventListener(event, listener, false);
			} else {
				self._doc.attachEvent(event, listener);
			}
			self._listening = true;
			self._hiddenBefore = self.hidden();
		}

	};
	self._init();
})();
;(function () {
	"use strict";

	var defined = function(variable) {
		return ('undefined' != typeof(variable));
	};

	var self = Visibility;

	var timers = {
		every: function (interval, hiddenInterval, callback) {
			self._initTimers();

			if ( !defined(callback) ) {
				callback = hiddenInterval;
				hiddenInterval = null;
			}
			self._lastTimer += 1;
			var number = self._lastTimer;
			self._timers[number] = ({
				interval:       interval,
				hiddenInterval: hiddenInterval,
				callback:       callback
			});
			self._runTimer(number, false);

			if ( self.isSupported() ) {
				self._setListener();
			}
			return number;
		},
		stop: function(id) {
			var timer = self._timers[id]
			if ( !defined(timer) ) {
				return false;
			}
			self._stopTimer(id);
			delete self._timers[id];
			return timer;
		},
		_lastTimer: -1,
		_timers: { },
		_timersInitialized: false,
		_initTimers: function () {
			if ( self._timersInitialized ) {
				return;
			}
			self._timersInitialized = true;

			if ( defined(window.jQuery) && defined(jQuery.every) ) {
				self._setInterval = self._chronoInterval;
			} else {
				self._setInterval = self._originalInterval;
			}
			self.change(function () {
				self._timersStopRun()
			});
		},
		_originalInterval: function (callback, interval) {
			return setInterval(callback, interval);
		},
		_chronoInterval: function (callback, internal) {
			return jQuery.every(internal, callback);
		},
		_setInterval: null,
		_runTimer: function (id, now) {
			var interval,
				timer = self._timers[id];
			if ( self.hidden() ) {
				if ( null === timer.hiddenInterval ) {
					return;
				}
				interval = timer.hiddenInterval;
			} else {
				interval = timer.interval;
			}
			if ( now ) {
				timer.callback.call(window);
			}
			timer.id = self._setInterval(timer.callback, interval);
		},
		_stopTimer: function (id) {
			var timer = self._timers[id];
			clearInterval(timer.id);
			delete timer.id;
		},
		_timersStopRun: function (event) {
			var isHidden = self.hidden(),
				hiddenBefore = self._hiddenBefore;

			if ( (isHidden && !hiddenBefore) || (!isHidden && hiddenBefore) ) {
				for ( var i in self._timers ) {
					self._stopTimer(i);
					self._runTimer(i, !isHidden);
				}
			}
		}

	};

	for ( var prop in timers ) {
		Visibility[prop] = timers[prop];
	}

})();


/* Классы */
function CUser(init) {
	for(var k in init) this[k] = init[k];

	this.notGuest = this.id != 0;

	this.ini = {
		"hot.img": 1,
		"hot.s_lang": 0,
		"hot.t_lang": 0,
		
		"ready.subscriptions": 0,

		"l.bgcolor": "#ffffff",
		"l.color": "#000000",
		"l.fontsize": 13,
		"l.lineheight": 18,
		"l.metascheme": 0,

		"t.iface": 1,
		"t.hlr": 1,
		"t.oe_hide": 1,
		"t.dict": 0,
		"t.textfontsize": 13,
		"t.copy": 0,
		"t.translator": 1,

		"c.sc": 0,

		"chat.h": 300,
		"chat.on": 0,

		"poll.done": 0,

        "blog.topics": "",
		
		"mainfilter.user_categories_na": 0,
		"mainfilter.user_categories": "",
        
        "lenta.subscription": 0,
        "lenta.copyright": 0
	};

	// parse ini cookie
	if($.cookie("ini")) {
		var a, pairs = $.cookie("ini").substr(2).split("\n");
		for(var i in pairs) {
			a = pairs[i].split("\t");
			if(a[0] in this.ini) this.ini[a[0]] = a[1];
		}
	}
}
CUser.prototype.ok = function() { return this.id != 0; }
CUser.prototype.url = function(area) {
	return "/users/" + this.id + (area ? "/" + area : "");
};
CUser.prototype.ahref = function(area) {
	return "<a href='" + this.url(area) + "' class='user'>" + this.login + "</a>";
};
CUser.prototype.ini_get = function(key) {
	return this.ini[key];
};
CUser.prototype.ini_set = function(key, value) {
	if(!(key in this.ini)) return;
	this.ini[key] = value;

	var t = "2@", expires = new Date("2037-08-08");
	for(var i in this.ini) {
		if(t != "2@") t += "\n";
		t += i + "\t" + this.ini[i];
	}
	$.cookie("ini", t, {expires: expires, path: "/"});
};

function CBook(init) {
	for(var k in init) this[k] = init[k];
}
CBook.prototype.url = function(area) {
	return "/book/" + this.id + (area ? "/" + area : "");
};



if(!window.console) console = {};
console.log = console.log || function(){};
console.warn = console.warn || function(){};
console.error = console.error || function(){};
console.info = console.info || function(){};
console.dir = console.dir || function(){};

$(document).ajaxError(function(e, xhr, settings, exception) {
	var txt;
	if(xhr.responseText.substr(0, 1) == "{") {
		txt = $.parseJSON(xhr.responseText).error;
	} else {
		txt = $(xhr.responseText).filter("p:first").text();
	}
	if(txt == "") {
		console.log("empty error message");
		console.dir(e);
		console.dir(xhr);
	} else {
		console.log(txt || xhr);
	}
});

$(function() {
	$(".upic.active").click(function() {
		var upic = $(this).attr("data-upic").split(".");
		var upic_url = "/i/upic/" + Math.floor(upic[0] / 1000) + "/" + upic[0] + "-" + upic[1] + "_big.jpg";

		var html = "<div id='userinfo_win' class='modal hide fade'><img src='" + upic_url + "' alt='' /></div>";
		$(document.body).append(html);
		$("#userinfo_win").modal().on("hidden", function() {$(this).remove()});
		return false;

	});

	// Залипание кнопок
	$("body").delegate("button.click-wait", "click", function(e) {
		$b = $(this);
		if($b.hasClass("disabled")) {
			e.preventDefault();
			return false;
		}
		$(this).text("...").addClass("disabled");
		return true;
	});

	$("[accesskey]").each(function() {
		var $this = $(this);
		var title = $this.attr("title"), text = ("Alt+") + $this.attr("accesskey").toUpperCase();
		if(title) title = title + " (" + text + ")";
		else title = text;

		$this.attr("title", title);
	});

//	$(window).keyup(function(e) {
//		if(e.altKey) {
//			console.log("%d, a=%d, c=%d, s=%d", e.which, e.altKey, e.ctrlKey, e.shiftKey);
//		}
//	});

	for(var ac in GLOBALS.ac_areas) {
		for(var role in GLOBALS.ac_roles) {
			$("i." + ac + "." + role).attr("title", GLOBALS.ac_areas[ac] + (role == "o" ? " может " : " могут ") + GLOBALS.ac_roles_title[role]);
		}
	}

});

function setCookie(name, value, options) {
	options = options || {};

	var expires = options.expires;

	if (typeof expires == "number" && expires) {
		var d = new Date();
		d.setTime(d.getTime() + expires * 1000);
		expires = options.expires = d;
	}
	if (expires && expires.toUTCString) {
		options.expires = expires.toUTCString();
	}

	value = encodeURIComponent(value);

	var updatedCookie = name + "=" + value;

	for (var propName in options) {
		updatedCookie += "; " + propName;
		var propValue = options[propName];
		if (propValue !== true) {
			updatedCookie += "=" + propValue;
		}
	}

	document.cookie = updatedCookie;
}

function getCookie(name) {
	var matches = document.cookie.match(new RegExp(
		"(?:^|; )" + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, '\\$1') + "=([^;]*)"
	));
	return matches ? decodeURIComponent(matches[1]) : undefined;
}

function like(book_id, chap_id) {
	var likeButtons = $('#scroll_chapter_' + chap_id + ' .like-buttons .btn');
	$.ajax({
		type: "POST",
		url: "/book/"+book_id+"/"+chap_id+"/like",
		beforeSend: function() {
			likeButtons.addClass('disabled');
		},
		success: function(data) {
			likeButtons.hide();
			$('#likes_cnt_'+chap_id).text(data);
		}
	});
}

function likePay(book_id, chap_id) {
	var likeButtons = $('#scroll_chapter_' + chap_id + ' .like-buttons .btn');
	$.ajax({
		type: "POST",
		url: "/book/"+book_id+"/"+chap_id+"/likePay",
		data: {
			csrf_token: $('meta[name="csrf-token"]').attr('content')
		},
		beforeSend: function() {
			likeButtons.addClass('disabled');
		},
		success: function(data) {
			likeButtons.hide();
			$('#likes_cnt_'+chap_id).text(data);
		}
	});
}

function strip_tags( str ){	// Strip HTML and PHP tags from a string
	//
	// +   original by: Kevin van Zonneveld (http://kevin.vanzonneveld.net)

	return str.replace(/<\/?[^>]+>/gi, '');
}
