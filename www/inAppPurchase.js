var exec = require('cordova/exec');

var InAppPurchase = {
    initialize: function(success, error) {
        exec(success, error, '_MYPURCHASE1', 'initialize', []);
    },
	
	purchase: function(productId, success, error, cancelled) {
        exec(success, function(err) {
            if (err === 'USER_CANCELED' && typeof cancelled === 'function') {
                cancelled();
            } else if (typeof error === 'function') {
                error(err);
            }
        }, '_MYPURCHASE1', 'purchase', [productId]);
    },
	
	close: function(success, error) {
        exec(success, error, '_MYPURCHASE1', 'close', []);
    },
};

module.exports = InAppPurchase;