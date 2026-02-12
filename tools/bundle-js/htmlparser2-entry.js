// Entry point for htmlparser2 bundle
// Produces a self-contained htmlparser2 with DomHandler and DomUtils
var htmlparser2 = require('htmlparser2');
var DomHandler = require('domhandler');
var DomUtils = require('domutils');
var DomSerializer = require('dom-serializer');

module.exports = {
  Parser: htmlparser2.Parser,
  DomHandler: DomHandler.DomHandler || DomHandler,
  DomUtils: DomUtils,
  parseDocument: htmlparser2.parseDocument,
  serialize: DomSerializer.default || DomSerializer
};
