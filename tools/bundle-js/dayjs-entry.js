// Entry point for dayjs bundle
// Produces a self-contained dayjs with customParseFormat plugin
var dayjs = require('dayjs');
var customParseFormat = require('dayjs/plugin/customParseFormat');
var localizedFormat = require('dayjs/plugin/localizedFormat');
dayjs.extend(customParseFormat);
dayjs.extend(localizedFormat);

// Export for our module system
module.exports = dayjs;
