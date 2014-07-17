var adminApp = angular.module('admin', ['ngRoute', 'AdminCtl']);

//Configure routes in the love-admin app
adminApp.config(function($routeProvider) {
    $routeProvider
        .when("/", {
            controller:     'HomeCtl',
            templateUrl:    'home.html'
        })
        .when("/creds", {
            controller:     'CredsCtl',
            templateUrl:    'creds.html'
        })
        .otherwise({
            redirectTo:     '/'
        });
});