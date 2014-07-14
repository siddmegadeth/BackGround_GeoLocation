var exec = require("cordova/exec");
module.exports = {
    configure: function(success, failure, config) {
        var params              = JSON.stringify(config.params || {});
        var url                 = config.url        || 'BackgroundGeoLocation_url';
        var stationaryRadius    = (config.stationaryRadius >= 0) ? config.stationaryRadius : 50 ;   // meters
        var distanceFilter      = (config.distanceFilter >= 0) ? config.distanceFilter : 500 ;      // meters
        var locationTimeout     = (config.locationTimeout >= 0) ? config.locationTimeout : 60 ;     // seconds
        var desiredAccuracy     = (config.desiredAccuracy >= 0) ? config.desiredAccuracy : 100 ;    // meters
        var debug               = config.debug || false;
        var minAngleDif         = config.minAngleDif || 5;
        var minPeriodInterval   = config.minPeriodInterval || 10;
        var checkPeriodInterval = config.checkPeriodInterval || 5;

        exec(success || function() {},
             failure || function() {},
             'BackgroundGeoLocation',
             'configure',
             [params, url, stationaryRadius, distanceFilter, locationTimeout, desiredAccuracy, debug, minAngleDif, minPeriodInterval, checkPeriodInterval]);
    },
    start: function(success, failure, config) {
        exec(success || function() {},
             failure || function() {},
             'BackgroundGeoLocation',
             'start',
             []);
    },
    stop: function(success, failure, config) {
        exec(success || function() {},
            failure || function() {},
            'BackgroundGeoLocation',
            'stop',
            []);
    },
    finish: function(success, failure) {
        exec(success || function() {},
            failure || function() {},
            'BackgroundGeoLocation',
            'finish',
            []);  
    },
    changePace: function(isMoving, success, failure) {
        exec(success || function() {},
            failure || function() {},
            'BackgroundGeoLocation',
            'onPaceChange',
            [isMoving]);   
    },
    /**
    * @param {Integer} stationaryRadius
    * @param {Integer} desiredAccuracy
    * @param {Integer} distanceFilter
    * @param {Integer} timeout
    */
    setConfig: function(success, failure, config) {
        exec(success || function() {},
            failure || function() {},
            'BackgroundGeoLocation',
            'setConfig',
            [config]);
    },
   /**
    * Returns current stationaryLocation if available.  null if not
    */
   getStationaryLocation: function(success, failure) {
       exec(success || function() {},
            failure || function() {},
            'BackgroundGeoLocation',
            'getStationaryLocation',
            []);
       },
    getLastLocation: function(success, failure) {
       exec(success || function() {},
            failure || function() {},
            'BackgroundGeoLocation',
            'getLastLocation',
            []);
       },

};
